#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::sync::Mutex;
use std::time::Duration;
use tauri::Manager;

struct JavaProcess(Mutex<Option<Child>>);

fn check_server(port: u16) -> bool {
    if let Ok(mut stream) = TcpStream::connect_timeout(
        &format!("127.0.0.1:{}", port).parse().unwrap(),
        Duration::from_secs(2),
    ) {
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

fn main() {
    let mut dir = std::env::current_exe().unwrap();
    for _ in 0..4 { dir.pop(); } // from target/debug/prettyzk to project root

    let backend = dir.join("app").join("build").join("install").join("app").join("bin").join("app");
    let mut child = Command::new(&backend)
        .current_dir(&dir)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap_or_else(|_| {
            // Fallback: try gradlew run
            Command::new("./gradlew")
                .args(["run"])
                .current_dir(&dir)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn()
                .expect("Failed to start backend. Run 'gradlew :app:installDist' first.")
        });

    let stdout = child.stdout.take().unwrap();
    let stderr = child.stderr.take().unwrap();
    let (port_tx, port_rx) = mpsc::channel();

    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        for line in reader.lines().flatten() {
            println!("[backend] {}", line);
            if let Some(p) = line.split("127.0.0.1:").nth(1) {
                if let Ok(pn) = p.trim_end_matches('/').parse::<u16>() {
                    let _ = port_tx.send(pn);
                }
            }
        }
    });

    std::thread::spawn(move || {
        for line in BufReader::new(stderr).lines().flatten() {
            eprintln!("[backend] {}", line);
        }
    });

    let port = match port_rx.recv_timeout(Duration::from_secs(120)) {
        Ok(p) => p,
        Err(e) => { eprintln!("Failed to get port: {:?}", e); std::process::exit(1); }
    };

    let mut ready = false;
    for _ in 0..30 {
        if check_server(port) { ready = true; break; }
        std::thread::sleep(Duration::from_secs(1));
    }
    if !ready { eprintln!("Backend health check failed"); std::process::exit(1); }

    println!("Backend ready on http://127.0.0.1:{}", port);

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(JavaProcess(Mutex::new(Some(child))))
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                if let Some(state) = window.try_state::<JavaProcess>() {
                    if let Ok(mut guard) = state.0.lock() {
                        if let Some(mut c) = guard.take() {
                            let _ = c.kill(); let _ = c.wait();
                        }
                    }
                }
            }
        })
        .setup(move |app| {
            let window = app.get_webview_window("main").unwrap();
            let _ = window.eval(&format!("window.location.href = 'http://127.0.0.1:{}'", port));
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
