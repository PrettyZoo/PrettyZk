#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::fs::OpenOptions;
use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::sync::Mutex;
use std::time::Duration;
use tauri::webview::PageLoadEvent;
use tauri::{Manager, RunEvent, WebviewUrl, WebviewWindow, WebviewWindowBuilder};

const BACKEND_MAIN_CLASS: &str = "com.dujie.app.Application";
const READY_MARKER: &str = "PRETTYZK_READY ";

const BACKEND_LOST_SCRIPT: &str = r#"
(function () {
  if (document.getElementById('prettyzk-backend-lost')) return;
  var el = document.createElement('div');
  el.id = 'prettyzk-backend-lost';
  el.textContent = 'PrettyZk backend stopped. Please restart the application.';
  el.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:2147483647;' +
    'padding:8px 12px;background:#b42318;color:#fff;text-align:center;' +
    'font:12px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;';
  document.body.appendChild(el);
})();
"#;

#[derive(Clone)]
enum BackendStatus {
    Starting,
    Ready(u16),
    Failed(String),
}

struct BackendState {
    child: Mutex<Option<Child>>,
    status: Mutex<BackendStatus>,
}

fn path_str(p: &Path) -> &str {
    p.to_str().unwrap_or("<non-utf8-path>")
}

fn java_binary(base: &Path) -> Option<PathBuf> {
    let name = if cfg!(windows) { "java.exe" } else { "java" };
    let java = base.join("runtime").join("bin").join(name);
    if java.exists() {
        Some(java)
    } else {
        None
    }
}

/// Resolve the directory containing `runtime/` and `app/lib/`.
///
/// Production: Tauri's resource dir, populated by `bundle.resources`.
/// Development: `<repo>/build/desktop` (from `npm run prepare`), or the
/// Gradle installDist output as a last resort.
fn backend_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let mut candidates: Vec<PathBuf> = Vec::new();

    // Preferred: Tauri's resource resolver.
    if let Ok(dir) = app.path().resource_dir() {
        candidates.push(dir);
    }

    // Fallback: derive the resource directory from the executable. This keeps
    // the app working when Tauri cannot resolve the bundle path (some macOS
    // launch modes return `unknown path`).
    if let Ok(exe) = std::env::current_exe() {
        if let Some(exe_dir) = exe.parent() {
            candidates.push(exe_dir.join("resources"));
            candidates.push(exe_dir.to_path_buf());
            if let Some(contents_dir) = exe_dir.parent() {
                candidates.push(contents_dir.join("Resources"));
                candidates.push(contents_dir.to_path_buf());
            }
        }
    }

    if let Some(found) = candidates
        .iter()
        .find(|dir| dir.join("runtime").exists() || dir.join("app").join("lib").exists())
    {
        return Ok(found.clone());
    }

    let repo_root = Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .unwrap_or_else(|| Path::new("."));
    let staged = repo_root.join("build").join("desktop");
    if staged.join("runtime").exists() {
        return Ok(staged);
    }

    let install_dist = repo_root
        .join("app")
        .join("build")
        .join("install")
        .join("app");
    if install_dist.join("lib").exists() {
        return Ok(install_dist);
    }

    let looked_in = candidates
        .iter()
        .map(|dir| dir.display().to_string())
        .collect::<Vec<_>>()
        .join(", ");
    Err(format!(
        "backend runtime not found. Looked in: {} and {}",
        looked_in,
        staged.display()
    ))
}

fn find_java(base: &Path) -> Result<PathBuf, String> {
    // A launch that races the installer may briefly see the Resources
    // directory before java[.exe] is copied. Retry for a short window.
    for attempt in 0..5 {
        if let Some(java) = java_binary(base) {
            return Ok(java);
        }
        if attempt < 4 {
            std::thread::sleep(Duration::from_millis(300));
        }
    }

    // Only development builds may fall back to a system Java installation.
    #[cfg(debug_assertions)]
    {
        let system_java = Command::new("java")
            .arg("-version")
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map(|status| status.success())
            .unwrap_or(false);
        if system_java {
            return Ok(PathBuf::from("java"));
        }
    }

    Err(format!(
        "bundled Java runtime not found at {}",
        base.join("runtime").display()
    ))
}

fn start_backend(java: &Path, base: &Path, port: u16) -> Result<Child, String> {
    let lib_dir = base.join("app").join("lib");
    let mut jars: Vec<PathBuf> = Vec::new();
    if let Ok(entries) = std::fs::read_dir(&lib_dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().map_or(false, |ext| ext == "jar") {
                jars.push(path);
            }
        }
    }
    jars.sort();
    if jars.is_empty() {
        return Err(format!("no backend jars found in {}", lib_dir.display()));
    }

    let separator = if cfg!(windows) { ';' } else { ':' };
    let mut classpath = String::new();
    for jar in &jars {
        if !classpath.is_empty() {
            classpath.push(separator);
        }
        classpath.push_str(path_str(jar));
    }

    let parent_pid = std::process::id().to_string();
    let mut command = Command::new(java);
    command
        .args([
            "-cp",
            &classpath,
            "-Dfile.encoding=utf-8",
            BACKEND_MAIN_CLASS,
            "--port",
            &port.to_string(),
            "--parent-pid",
            &parent_pid,
        ])
        .current_dir(base)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        // Do not flash a console window when starting java.exe.
        command.creation_flags(0x08000000);
    }

    #[cfg(target_os = "linux")]
    {
        use std::os::unix::process::CommandExt;
        // Ask the kernel to terminate Java if the desktop shell dies unexpectedly.
        unsafe {
            command.pre_exec(|| {
                libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGTERM);
                Ok(())
            });
        }
    }

    command
        .spawn()
        .map_err(|e| format!("cannot start {}: {e}", java.display()))
}

fn check_server(port: u16) -> bool {
    let addr: std::net::SocketAddr = (std::net::Ipv4Addr::LOCALHOST, port).into();
    if let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_secs(2)) {
        let _ = stream.set_read_timeout(Some(Duration::from_secs(2)));
        let _ = stream.set_write_timeout(Some(Duration::from_secs(2)));
        let request = format!(
            "GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:{}\r\nConnection: close\r\n\r\n",
            port
        );
        let _ = stream.write_all(request.as_bytes());
        let mut response = String::new();
        if BufReader::new(&stream).read_line(&mut response).is_ok() {
            return response.contains("200 OK");
        }
    }
    false
}

fn log_path(app: &tauri::AppHandle) -> PathBuf {
    app.path()
        .app_log_dir()
        .map(|dir| dir.join("desktop.log"))
        .unwrap_or_else(|_| std::env::temp_dir().join("prettyzk-desktop.log"))
}

fn rotate_log(path: &Path) {
    if let Ok(metadata) = std::fs::metadata(path) {
        if metadata.len() > 5 * 1024 * 1024 {
            let backup = path.with_file_name("desktop.log.1");
            let _ = std::fs::rename(path, backup);
        }
    }
}

fn append_log(path: &Path, line: &str) {
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
        let _ = writeln!(file, "{}", line);
    }
}

fn stop_backend(app: &tauri::AppHandle) {
    if let Some(state) = app.try_state::<BackendState>() {
        if let Ok(mut guard) = state.child.lock() {
            if let Some(mut child) = guard.take() {
                let _ = child.kill();
                let _ = child.wait();
            }
        }
    }
}

fn backend_ready_script(port: u16) -> String {
    format!(
        "window.__PRETTYZK_BACKEND__ = 'http://127.0.0.1:{port}';\
         window.dispatchEvent(new Event('prettyzk-backend-ready'));"
    )
}

fn backend_error_script(message: &str) -> String {
    let payload = serde_json::to_string(message).unwrap_or_else(|_| "\"startup failed\"".into());
    format!(
        "window.__PRETTYZK_BACKEND_ERROR__ = {payload};\
         window.dispatchEvent(new CustomEvent('prettyzk-backend-error', {{ detail: {payload} }}));\
         if (!window.__PRETTYZK_FRONTEND_READY__) {{\
           (function () {{\
             var id = 'prettyzk-backend-error-banner';\
             if (document.getElementById(id)) return;\
             var el = document.createElement('div');\
             el.id = id;\
             el.textContent = {payload};\
             el.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:2147483647;padding:8px 12px;background:#b42318;color:#fff;text-align:center;font:12px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;white-space:pre-wrap;';\
             if (document.body) document.body.appendChild(el);\
             else document.addEventListener('DOMContentLoaded', function () {{ document.body.appendChild(el); }});\
           }})();\
         }}"
    )
}

fn inject_backend_state(app: &tauri::AppHandle, window: &WebviewWindow) {
    let Some(state) = app.try_state::<BackendState>() else {
        return;
    };
    let status = state.status.lock().ok().map(|guard| (*guard).clone());
    match status {
        Some(BackendStatus::Ready(port)) => {
            let _ = window.eval(&backend_ready_script(port));
        }
        Some(BackendStatus::Failed(error)) => {
            let _ = window.eval(&backend_error_script(&error));
        }
        Some(BackendStatus::Starting) | None => {}
    }
}

fn show_error(window: &WebviewWindow, message: &str) {
    // If the webview is still loading, on_page_load replays the stored error.
    let _ = window.eval(&backend_error_script(message));
}

fn run_backend(
    app: &tauri::AppHandle,
    window: &WebviewWindow,
    log: &Path,
) -> Result<(), String> {
    rotate_log(log);
    let base = backend_dir(app)?;
    let java = find_java(&base)?;
    let mut child = start_backend(&java, &base, 0)?;

    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| "backend stdout is not available".to_string())?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| "backend stderr is not available".to_string())?;

    if let Some(state) = app.try_state::<BackendState>() {
        if let Ok(mut guard) = state.child.lock() {
            *guard = Some(child);
        }
    }

    let (port_tx, port_rx) = mpsc::channel();
    let stdout_log = log.to_path_buf();
    std::thread::spawn(move || {
        for line in BufReader::new(stdout).lines().flatten() {
            append_log(&stdout_log, &line);
            if let Some(port) = line.split(READY_MARKER).nth(1) {
                if let Ok(port) = port.trim().parse::<u16>() {
                    let _ = port_tx.send(port);
                }
            }
            // Compatibility with older backends.
            if line.contains("desktop app started on http://127.0.0.1:") {
                if let Some(port) = line.split("127.0.0.1:").nth(1) {
                    if let Ok(port) = port.trim_end_matches('/').trim().parse::<u16>() {
                        let _ = port_tx.send(port);
                    }
                }
            }
        }
    });

    let stderr_log = log.to_path_buf();
    std::thread::spawn(move || {
        for line in BufReader::new(stderr).lines().flatten() {
            append_log(&stderr_log, &line);
        }
    });

    let port = match port_rx.recv_timeout(Duration::from_secs(120)) {
        Ok(port) => port,
        Err(_) => {
            stop_backend(app);
            return Err("backend did not report a port within 120 seconds".to_string());
        }
    };

    let mut healthy = false;
    for _ in 0..30 {
        if check_server(port) {
            healthy = true;
            break;
        }
        std::thread::sleep(Duration::from_secs(1));
    }
    if !healthy {
        stop_backend(app);
        return Err(format!("backend health check failed on port {port}"));
    }

    if let Some(state) = app.try_state::<BackendState>() {
        if let Ok(mut guard) = state.status.lock() {
            *guard = BackendStatus::Ready(port);
        }
    }

    // The window already shows the app shell; hand it the backend port.
    // If the webview is still loading, on_page_load injects the stored status.
    let notify_app = app.clone();
    let notify_window = window.clone();
    let _ = app.run_on_main_thread(move || {
        inject_backend_state(&notify_app, &notify_window);
    });

    // If the backend dies after startup, surface a banner instead of leaving
    // a UI where every action silently fails.
    let monitor_app = app.clone();
    let monitor_window = window.clone();
    std::thread::spawn(move || {
        let mut failures = 0;
        loop {
            std::thread::sleep(Duration::from_secs(5));
            if check_server(port) {
                failures = 0;
                continue;
            }
            failures += 1;
            if failures >= 2 {
                let handle = monitor_app.clone();
                let win = monitor_window.clone();
                let _ = handle.run_on_main_thread(move || {
                    let _ = win.eval(BACKEND_LOST_SCRIPT);
                });
                break;
            }
        }
    });
    Ok(())
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .manage(BackendState {
            child: Mutex::new(None),
            status: Mutex::new(BackendStatus::Starting),
        })
        .setup(|app| {
            let page_app = app.handle().clone();
            let window = WebviewWindowBuilder::new(
                app,
                "main",
                WebviewUrl::App("index.html".into()),
            )
            .title("PrettyZk")
            .inner_size(1050.0, 650.0)
            .min_inner_size(900.0, 500.0)
            .center()
            .on_navigation(|url| {
                url.scheme() == "tauri"
                    || url.scheme() == "about"
                    || url.host_str() == Some("tauri.localhost")
                    || (url.scheme() == "http"
                        && matches!(url.host_str(), Some("127.0.0.1") | Some("localhost")))
            })
            .on_page_load(move |window, payload| {
                if matches!(payload.event(), PageLoadEvent::Finished) {
                    inject_backend_state(&page_app, &window);
                }
            })
            .build()?;

            let handle = app.handle().clone();
            let error_handle = handle.clone();
            let log = log_path(&handle);
            std::thread::spawn(move || {
                if let Err(error) = run_backend(&handle, &window, &log) {
                    let text = format!("{}\n\nLog file: {}", error, log.display());
                    if let Some(state) = error_handle.try_state::<BackendState>() {
                        if let Ok(mut guard) = state.status.lock() {
                            *guard = BackendStatus::Failed(text.clone());
                        }
                    }
                    show_error(&window, &text);
                }
            });
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app, event| {
            if matches!(event, RunEvent::Exit | RunEvent::ExitRequested { .. }) {
                stop_backend(app);
            }
        });
}
