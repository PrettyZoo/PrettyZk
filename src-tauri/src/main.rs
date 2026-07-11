#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::sync::Mutex;
use std::time::Duration;
use tauri::Manager;

struct JavaProcess(Mutex<Option<Child>>);

/// Convert a path to a string for display, falling back to a placeholder on non-UTF8 paths.
fn path_str(p: &Path) -> &str {
    p.to_str().unwrap_or("<non-utf8-path>")
}

fn resource_dir() -> PathBuf {
    let exe = std::env::current_exe().unwrap_or_else(|_| PathBuf::from("."));
    let exe_parent = exe.parent().unwrap_or_else(|| Path::new("."));

    // macOS: PrettyZk.app/Contents/Resources/
    if let Some(grandparent) = exe_parent.parent() {
        let mac = grandparent.join("Resources");
        if mac.join("runtime").exists() {
            return mac;
        }
    }

    // Windows/Linux: resources/ alongside exe
    let win = exe_parent.join("resources");
    if win.join("runtime").exists() {
        return win;
    }

    // Windows/Linux (NSIS installer): runtime directly alongside exe, no "resources" prefix
    if exe_parent.join("runtime").exists() {
        return exe_parent.to_path_buf();
    }

    // Dev mode: exe in target/debug/ or target/release/, resources in ../../resources/
    if let Some(grandparent) = exe_parent.parent() {
        let dev = grandparent.join("resources");
        if dev.join("runtime").exists() {
            return dev;
        }
    }

    // Fallback: try current_exe parent with "resources" suffix
    exe_parent.join("resources")
}

#[cfg(target_os = "windows")]
fn download_jre() -> bool {
    let res = resource_dir();
    let jre_dir = res.join("runtime");
    let tmp = res.join("__jre_tmp");
    let _ = std::fs::create_dir_all(&tmp);

    // Download from Adoptium API (official Eclipse Temurin builds)
    let url = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse";
    let java_exe = jre_dir.join("bin").join("java.exe");

    // Skip if already downloaded
    if java_exe.exists() {
        return true;
    }

    eprintln!("Downloading Java 17 JRE (Eclipse Temurin)...");

    let script = format!(
        "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; \
        $ErrorActionPreference='Stop'; \
        $p='{0}'; mkdir $p -Force; \
        Invoke-WebRequest '{1}' -OutFile \"$p\\jre.zip\" -UseBasicParsing; \
        Expand-Archive \"$p\\jre.zip\" \"$p\\extracted\" -Force; \
        $d=Get-ChildItem \"$p\\extracted\" -Directory | Select-Object -First 1; \
        if($d){{Move-Item $d.FullName '{2}' -Force}}",
        path_str(&tmp), url, path_str(&jre_dir)
    );

    let status = Command::new("powershell")
        .args(["-NoProfile", "-Command", &script])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .unwrap_or_default();

    let _ = std::fs::remove_dir_all(&tmp);

    if java_exe.exists() {
        eprintln!("JRE installed: {}", jre_dir.display());
        true
    } else {
        eprintln!("JRE download failed. Install Java 17 from https://adoptium.net/");
        false
    }
}

fn check_java(cmd: &str) -> bool {
    Command::new(cmd)
        .arg("-version")
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map_or(false, |s| s.success())
}

fn find_java() -> String {
    let res = resource_dir();
    let bundled = res.join("runtime").join("bin").join("java");
    let bundled_exe = res.join("runtime").join("bin").join("java.exe");

    // 1. Check bundled JRE
    if bundled.exists() {
        return path_str(&bundled).to_string();
    }
    if bundled_exe.exists() {
        return path_str(&bundled_exe).to_string();
    }

    // 2. Check system Java 17+
    if check_java("java") {
        return "java".to_string();
    }
    let java_home = std::env::var("JAVA_HOME").unwrap_or_default();
    if !java_home.is_empty() {
        let jh_java = Path::new(&java_home).join("bin").join("java");
        let jh_java_exe = Path::new(&java_home).join("bin").join("java.exe");
        if jh_java.exists() || jh_java_exe.exists() {
            return path_str(&jh_java).to_string();
        }
    }

    // 3. Download JRE on Windows
    #[cfg(target_os = "windows")]
    {
        if download_jre() && bundled_exe.exists() {
            return path_str(&bundled_exe).to_string();
        }
    }

    eprintln!("ERROR: Java not found. Please install Java 17+ from https://adoptium.net/");
    eprintln!("Or use PrettyZk-Setup.exe which includes Java automatically.");
    std::process::exit(1);
}

fn check_server(port: u16) -> bool {
    let addr: std::net::SocketAddr = (std::net::Ipv4Addr::LOCALHOST, port).into();
    if let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_secs(2)) {
        let req = format!(
            "GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:{}\r\nConnection: close\r\n\r\n",
            port
        );
        let _ = stream.write_all(req.as_bytes());
        let mut resp = String::new();
        if BufReader::new(&stream).read_line(&mut resp).is_ok() {
            return resp.contains("200 OK");
        }
    }
    false
}

fn start_backend(java: &str, port: u16) -> Child {
    let res = resource_dir();

    // Collect all jar files
    let lib_dir = res.join("app").join("lib");
    let mut jars: Vec<PathBuf> = Vec::new();

    if lib_dir.exists() {
        if let Ok(entries) = std::fs::read_dir(&lib_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().map_or(false, |e| e == "jar") {
                    jars.push(path);
                }
            }
        }
    }

    if !jars.is_empty() {
        // Java classpath separator: ';' on Windows, ':' on Unix
        let sep = if cfg!(target_os = "windows") { ';' } else { ':' };
        let mut cp = String::new();
        for jar in &jars {
            if !cp.is_empty() {
                cp.push(sep);
            }
            cp.push_str(path_str(jar));
        }

        return Command::new(java)
            .args([
                "-cp", &cp,
                "-Dfile.encoding=utf-8",
                "cc.cc1234.Application",
                "--port", &port.to_string(),
            ])
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .expect("Failed to start backend");
    }

    // Dev: use installDist script
    let mut dir = std::env::current_exe().unwrap_or_else(|_| PathBuf::from("."));
    for _ in 0..4 {
        dir.pop();
    }
    let dev = dir
        .join("app")
        .join("build")
        .join("install")
        .join("app")
        .join("bin")
        .join("app");
    if dev.exists() {
        return Command::new(&dev)
            .args(["--port", &port.to_string()])
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .expect("Failed to start dev backend");
    }

    panic!("Backend not found. Run: gradlew :app:installDist");
}

fn main() {
    let java = find_java();
    let mut child = start_backend(&java, 0);

    let stdout = child.stdout.take().expect("stdout should be piped");
    let stderr = child.stderr.take().expect("stderr should be piped");
    let (port_tx, port_rx) = mpsc::channel();

    // Parse the backend's port announcement from stdout.
    // The backend prints: "PrettyZk desktop app started on http://127.0.0.1:<PORT>"
    std::thread::spawn(move || {
        for line in BufReader::new(stdout).lines().flatten() {
            println!("[backend] {}", line);
            // Only parse lines that explicitly claim to be the startup announcement
            if line.contains("started on http://127.0.0.1:") {
                if let Some(p) = line.split("127.0.0.1:").nth(1) {
                    if let Ok(pn) = p.trim_end_matches('/').trim().parse::<u16>() {
                        let _ = port_tx.send(pn);
                    }
                }
            }
        }
    });

    std::thread::spawn(move || {
        for line in BufReader::new(stderr).lines().flatten() {
            eprintln!("[backend] {}", line);
        }
    });

    let bp = port_rx.recv_timeout(Duration::from_secs(120)).unwrap_or_else(|e| {
        eprintln!("Backend timeout: {:?}", e);
        // Kill the child process before exiting to avoid orphans
        let _ = child.kill();
        let _ = child.wait();
        std::process::exit(1);
    });

    // Wait for backend health check (up to 30 seconds)
    let mut healthy = false;
    for _ in 0..30 {
        if check_server(bp) {
            healthy = true;
            break;
        }
        std::thread::sleep(Duration::from_secs(1));
    }
    if !healthy {
        eprintln!("Backend health check failed on port {}", bp);
        let _ = child.kill();
        let _ = child.wait();
        std::process::exit(1);
    }

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
            let window = app
                .get_webview_window("main")
                .expect("main window should exist");
            let _ = window.eval(&format!(
                "window.location.href = 'http://127.0.0.1:{}'",
                bp
            ));
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
