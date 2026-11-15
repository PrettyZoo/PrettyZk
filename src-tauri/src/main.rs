#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::fs::OpenOptions;
use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::sync::Mutex;
use std::time::Duration;
use tauri::{Manager, RunEvent, WebviewUrl, WebviewWindow, WebviewWindowBuilder};

const BACKEND_MAIN_CLASS: &str = "com.dujie.app.Application";
const READY_MARKER: &str = "PRETTYZK_READY ";

struct BackendState(Mutex<Option<Child>>);

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
    if let Some(java) = java_binary(base) {
        return Ok(java);
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

    let mut command = Command::new(java);
    command
        .args([
            "-cp",
            &classpath,
            "-Dfile.encoding=utf-8",
            BACKEND_MAIN_CLASS,
            "--port",
            &port.to_string(),
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
        if let Ok(mut guard) = state.0.lock() {
            if let Some(mut child) = guard.take() {
                let _ = child.kill();
                let _ = child.wait();
            }
        }
    }
}

fn show_error(window: &WebviewWindow, message: &str, log: &Path) {
    let text = format!("{}\n\n日志文件：{}", message, log.display());
    let payload = serde_json::to_string(&text).unwrap_or_else(|_| "\"startup failed\"".into());
    let script = format!("window.showStartupError({});", payload);
    let _ = window.eval(&script);
}

fn run_backend(
    app: &tauri::AppHandle,
    window: &WebviewWindow,
    log: &Path,
) -> Result<(), String> {
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
        if let Ok(mut guard) = state.0.lock() {
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
            return Err("后端在 120 秒内没有报告端口，请查看日志".to_string());
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
        return Err(format!("后端健康检查失败（端口 {}）", port));
    }

    let url = tauri::Url::parse(&format!("http://127.0.0.1:{}", port))
        .map_err(|e| format!("无效的后端地址: {e}"))?;
    let window = window.clone();
    app.run_on_main_thread(move || {
        let _ = window.navigate(url);
    })
    .map_err(|e| format!("无法切换窗口地址: {e}"))?;
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
        .manage(BackendState(Mutex::new(None)))
        .setup(|app| {
            let window = WebviewWindowBuilder::new(
                app,
                "main",
                WebviewUrl::App("splash.html".into()),
            )
            .title("PrettyZk")
            .inner_size(1050.0, 650.0)
            .min_inner_size(900.0, 500.0)
            .center()
            .on_navigation(|url| {
                url.scheme() == "tauri"
                    || url.scheme() == "about"
                    || url.host_str() == Some("tauri.localhost")
                    || (url.scheme() == "http" && url.host_str() == Some("127.0.0.1"))
            })
            .build()?;

            let handle = app.handle().clone();
            let log = log_path(&handle);
            std::thread::spawn(move || {
                if let Err(error) = run_backend(&handle, &window, &log) {
                    show_error(&window, &error, &log);
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
