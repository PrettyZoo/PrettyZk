#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::sync::Mutex;
use std::time::Duration;
use tauri::Manager;

struct JavaProcess(Mutex<Option<Child>>);

fn resource_dir() -> PathBuf {
    let exe = std::env::current_exe().unwrap();
    // macOS: PrettyZk.app/Contents/Resources/
    let mac = exe.parent().unwrap().parent().unwrap().join("Resources");
    if mac.join("runtime").exists() { return mac; }
    // Windows/Linux: same dir as exe
    let win = exe.parent().unwrap().join("resources");
    if win.join("runtime").exists() { return win; }
    // Dev mode
    let dev = exe.parent().unwrap().parent().unwrap().join("resources");
    if dev.join("runtime").exists() { return dev; }
    // Fallback: try current_exe parent
    exe.parent().unwrap().join("resources")
}

fn ensure_jre(res_dir: &PathBuf) -> bool {
    let java = res_dir.join("runtime").join("bin").join("java");
    if cfg!(windows) { return java.with_extension("exe").exists(); }
    java.exists()
}

#[cfg(not(target_os = "windows"))]
fn download_jre() -> bool { true } // Bundled in .app on macOS

#[cfg(target_os = "windows")]
fn download_jre() -> bool {
    let res = resource_dir();
    let version = "3.0.16";
    let url = format!(
        "https://github.com/PrettyZoo/PrettyZk/releases/download/v{}/PrettyZk-jre-v{}.tar.gz",
        version, version
    );

    eprintln!("JRE not found. Downloading from {}...", url);

    // Download with PowerShell (built into Windows)
    let dl_script = format!(
        "$p='{}'; $u='{}'; \
        [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; \
        Invoke-WebRequest -Uri $u -OutFile $p\\jre.tar.gz -UseBasicParsing; \
        tar -xzf $p\\jre.tar.gz -C $p; \
        Remove-Item $p\\jre.tar.gz",
        res.to_str().unwrap_or(".")
    );

    let status = Command::new("powershell")
        .args(["-NoProfile", "-Command", &dl_script])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();

    match status {
        Ok(s) if s.success() => {
            eprintln!("JRE downloaded successfully");
            true
        }
        _ => {
            eprintln!("Failed to download JRE. Please install Java 17+ manually.");
            false
        }
    }
}

fn find_java() -> String {
    let res = resource_dir();
    let bundled = res.join("runtime").join("bin").join("java");
    let bundled_exe = res.join("runtime").join("bin").join("java.exe");

    if bundled.exists() { return bundled.to_str().unwrap().to_string(); }
    if bundled_exe.exists() { return bundled_exe.to_str().unwrap().to_string(); }

    // Download JRE on Windows if not bundled
    if cfg!(windows) && download_jre() {
        if bundled_exe.exists() { return bundled_exe.to_str().unwrap().to_string(); }
    }

    "java".to_string()
}

fn check_server(port: u16) -> bool {
    if let Ok(mut stream) = TcpStream::connect_timeout(
        &format!("127.0.0.1:{}", port).parse().unwrap(), Duration::from_secs(2),
    ) {
        let req = format!("GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:{}\r\nConnection: close\r\n\r\n", port);
        let _ = stream.write_all(req.as_bytes());
        let mut resp = String::new();
        if BufReader::new(&stream).read_line(&mut resp).is_ok() { return resp.contains("200 OK"); }
    }
    false
}

fn start_backend(java: &str, port: u16) -> Child {
    let res = resource_dir();

    // Collect all jar files
    let lib_dir = res.join("app").join("lib");
    let mut cp = String::new();

    if lib_dir.exists() {
        if let Ok(entries) = std::fs::read_dir(&lib_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().map_or(false, |e| e == "jar") {
                    if !cp.is_empty() { cp.push(':'); }
                    cp.push_str(path.to_str().unwrap());
                }
            }
        }
    }

    if !cp.is_empty() {
        return Command::new(java)
            .args(["-cp", &cp, "-Dfile.encoding=utf-8", "cc.cc1234.Application", "--port", &port.to_string()])
            .stdout(Stdio::piped()).stderr(Stdio::piped())
            .spawn().expect("Failed to start backend");
    }

    // Dev: use installDist script
    let mut dir = std::env::current_exe().unwrap();
    for _ in 0..4 { dir.pop(); }
    let dev = dir.join("app").join("build").join("install").join("app").join("bin").join("app");
    if dev.exists() {
        return Command::new(&dev)
            .args(["--port", &port.to_string()])
            .stdout(Stdio::piped()).stderr(Stdio::piped())
            .spawn().expect("Failed to start dev backend");
    }

    panic!("Backend not found. Run: gradlew :app:installDist");
}

fn main() {
    let java = find_java();
    let port = 0u16;
    let mut child = start_backend(&java, port);

    let stdout = child.stdout.take().unwrap();
    let stderr = child.stderr.take().unwrap();
    let (port_tx, port_rx) = mpsc::channel();

    std::thread::spawn(move || {
        for line in BufReader::new(stdout).lines().flatten() {
            println!("[backend] {}", line);
            if let Some(p) = line.split("127.0.0.1:").nth(1) {
                if let Ok(pn) = p.trim_end_matches('/').trim().parse::<u16>() { let _ = port_tx.send(pn); }
            }
        }
    });

    std::thread::spawn(move || {
        for line in BufReader::new(stderr).lines().flatten() { eprintln!("[backend] {}", line); }
    });

    let bp = port_rx.recv_timeout(Duration::from_secs(120))
        .unwrap_or_else(|e| { eprintln!("Backend timeout: {:?}", e); std::process::exit(1); });

    for _ in 0..30 { if check_server(bp) { break; } std::thread::sleep(Duration::from_secs(1)); }

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(JavaProcess(Mutex::new(Some(child))))
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                if let Some(state) = window.try_state::<JavaProcess>() {
                    if let Ok(mut guard) = state.0.lock() {
                        if let Some(mut c) = guard.take() { let _ = c.kill(); let _ = c.wait(); }
                    }
                }
            }
        })
        .setup(move |app| {
            let window = app.get_webview_window("main").unwrap();
            let _ = window.eval(&format!("window.location.href = 'http://127.0.0.1:{}'", bp));
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
