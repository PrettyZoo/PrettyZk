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
    exe.parent().unwrap().parent().unwrap().join("Resources")
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

fn find_java() -> String {
    // Try bundled JRE first
    let bundled = resource_dir().join("runtime").join("bin").join("java");
    if bundled.exists() { return bundled.to_str().unwrap().to_string(); }
    // Fall back to system java
    "java".to_string()
}

fn start_backend(java: &str, port: u16) -> Child {
    // Try bundled app distribution
    let app_dir = resource_dir().join("app");
    let bundled_jar = app_dir.join("lib").join("app-3.0.0.jar");
    if bundled_jar.exists() {
        let cp = app_dir.join("lib");
        let classpath = format!("{}", cp.display());
        return Command::new(java)
            .args(["-cp", &classpath, "-Dfile.encoding=utf-8", "cc.cc1234.Application", "--port", &port.to_string()])
            .stdout(Stdio::piped()).stderr(Stdio::piped())
            .spawn().expect("Failed to start bundled backend");
    }

    // Dev mode: project build directory
    let mut dir = std::env::current_exe().unwrap();
    for _ in 0..4 { dir.pop(); }
    let dev_bin = dir.join("app").join("build").join("install").join("app").join("bin").join("app");
    if dev_bin.exists() {
        return Command::new(&dev_bin)
            .args(["--port", &port.to_string()])
            .stdout(Stdio::piped()).stderr(Stdio::piped())
            .spawn().expect("Failed to start dev backend");
    }

    panic!("Backend not found. Build: gradlew :app:installDist");
}

fn main() {
    let port = 0u16;
    let java = find_java();
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
