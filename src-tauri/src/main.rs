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

#[cfg(not(target_os = "windows"))]
fn download_jre() -> bool { true } // Bundled in .app on macOS

#[cfg(target_os = "windows")]
fn download_jre() -> bool {
    let res = resource_dir();
    let url = "https://github.com/PrettyZoo/PrettyZk/releases/download/v3.0.0/jre.zip";
    let zip_path = res.join("jre.zip");

    eprintln!("Downloading JRE from {}...", url);

    // Download with PowerShell
    let dl = format!(
        "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; \
        Invoke-WebRequest -Uri '{}' -OutFile '{}' -UseBasicParsing",
        url, zip_path.to_str().unwrap()
    );

    let status = Command::new("powershell")
        .args(["-NoProfile", "-Command", &dl])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .status()
        .unwrap_or_default();

    if !status.success() {
        eprintln!("Download failed (exit: {:?})", status.code());
        return false;
    }

    if !zip_path.exists() {
        eprintln!("Downloaded file not found");
        return false;
    }

    // Extract
    let extract = format!(
        "Expand-Archive -Path '{}' -DestinationPath '{}' -Force",
        zip_path.to_str().unwrap(),
        res.to_str().unwrap()
    );

    let status2 = Command::new("powershell")
        .args(["-NoProfile", "-Command", &extract])
        .status()
        .unwrap_or_default();

    let _ = std::fs::remove_file(&zip_path);

    if !status2.success() {
        eprintln!("Extract failed (exit: {:?})", status2.code());
        return false;
    }

    let java = res.join("jre").join("runtime").join("bin").join("java.exe");
    if java.exists() {
        // Move jre/jre/* to jre/* (handle nested dir)
        let inner = res.join("jre").join("runtime");
        if inner.exists() {
            let _ = std::fs::rename(&inner, &res.join("runtime"));
            let _ = std::fs::remove_dir_all(res.join("jre"));
        }
        eprintln!("JRE installed successfully");
        true
    } else {
        eprintln!("JRE binary not found after extraction");
        false
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

    eprintln!("ERROR: Java not found. Please install Java 17+ from https://adoptium.net/");
    eprintln!("Or make sure PrettyZk-jre.zip is available in the release assets.");
    std::process::exit(1);
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
