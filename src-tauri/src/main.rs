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
    use std::net::TcpStream;
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
    for _ in 0..4 { dir.pop(); }

    let mut child = Command::new("./gradlew")
        .args(["run"])
        .current_dir(&dir)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .expect("Failed to start Gradle");

    let stdout = child.stdout.take().unwrap();
    let stderr = child.stderr.take().unwrap();

    // Channel to communicate the port from the stdout reader thread
    let (port_tx, port_rx) = mpsc::channel();

    // Forward stdout and find the port
    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        for line in reader.lines() {
            if let Ok(l) = &line {
                println!("[gradle] {}", l);
                if let Some(p) = l.split("127.0.0.1:").nth(1) {
                    if let Ok(pn) = p.trim_end_matches('/').parse::<u16>() {
                        let _ = port_tx.send(pn);
                    }
                }
            }
        }
    });

    // Forward stderr
    std::thread::spawn(move || {
        let reader = BufReader::new(stderr);
        for line in reader.lines().flatten() {
            eprintln!("[gradle] {}", line);
        }
    });

    // Wait for the port
    let port = match port_rx.recv_timeout(Duration::from_secs(120)) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("Failed to get server port: {:?}", e);
            std::process::exit(1);
        }
    };

    // Wait for server health check
    let mut ready = false;
    for _ in 0..30 {
        if check_server(port) {
            ready = true;
            break;
        }
        std::thread::sleep(Duration::from_secs(1));
    }
    if !ready {
        eprintln!("Server failed health check");
        std::process::exit(1);
    }

    println!("Server ready on http://127.0.0.1:{}", port);

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(JavaProcess(Mutex::new(Some(child))))
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                if let Some(state) = window.try_state::<JavaProcess>() {
                    if let Ok(mut guard) = state.0.lock() {
                        if let Some(mut c) = guard.take() {
                            let _ = c.kill();
                            let _ = c.wait();
                        }
                    }
                }
            }
        })
        .setup(move |app| {
            let window = app.get_webview_window("main").unwrap();
            let url = format!("http://127.0.0.1:{}", port);
            let _ = window.eval(&format!("window.location.href = '{}'", url));
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
