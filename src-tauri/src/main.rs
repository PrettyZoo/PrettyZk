#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::Duration;
use tauri::Manager;

struct JavaProcess(Mutex<Option<Child>>);

fn find_free_port() -> u16 {
    std::net::TcpListener::bind("127.0.0.1:0")
        .map(|l| l.local_addr().unwrap().port())
        .unwrap_or(8080)
}

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

fn wait_for_server(port: u16, max_retries: u32) -> Result<(), String> {
    for _ in 0..max_retries {
        if check_server(port) {
            return Ok(());
        }
        std::thread::sleep(Duration::from_secs(1));
    }
    Err("Server did not start in time".to_string())
}

fn start_java_backend() -> (Child, u16) {
    let port = find_free_port();

    let child = Command::new("java")
        .args([
            "-cp",
            &get_classpath(),
            "-Dfile.encoding=utf-8",
            "cc.cc1234.Application",
            "--port",
            &port.to_string(),
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .expect("Failed to start Java backend");

    (child, port)
}

fn get_classpath() -> String {
    // In development, use gradle build output
    let mut dir = std::env::current_exe().unwrap();
    for _ in 0..4 { dir.pop(); } // navigate from target/release/prettyzoo to project root

    let base = dir.to_str().unwrap_or(".").to_string();
    let res_dir = format!("{}/app/build/resources/main", base);
    let mut jars = vec![res_dir];

    // Collect all jars from build/libs
    let app_libs = format!("{}/app/build/libs", base);
    let core_libs = format!("{}/core/build/libs", base);
    let spec_libs = format!("{}/specification/build/libs", base);
    let spec_impl_libs = format!("{}/specification-impl/build/libs", base);

    for lib_dir in [&app_libs, &core_libs, &spec_libs, &spec_impl_libs] {
        if let Ok(entries) = std::fs::read_dir(lib_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().map_or(false, |e| e == "jar") {
                    jars.push(path.to_str().unwrap().to_string());
                }
            }
        }
    }

    // Add merged-zookeeper jar
    jars.push(format!("{}/specification/libs/merged-zookeeper.jar", base));

    // Add gradle cache jars (runtime dependencies)
    let gradle_cache = format!("{}/.gradle/caches", std::env::var("HOME").unwrap_or_default());
    let deps = [
        "javalin-6.4.0.jar", "jetty-server-11", "jetty-http-11", "jetty-io-11",
        "jetty-util-11", "jetty-servlet-11", "jetty-webapp-11", "jetty-security-11",
        "jetty-xml-11", "websocket-jetty-server-11", "websocket-servlet-11",
        "websocket-core-server-11", "websocket-jetty-common-11", "websocket-core-common-11",
        "slf4j-api-2", "log4j-core-2", "log4j-api-2", "log4j-slf4j2",
        "jackson-databind", "jackson-core", "jackson-annotations", "jackson-datatype-jdk8",
        "commons-io-2", "guava-31", "curator-framework-5", "curator-recipes-5",
        "curator-client-5", "kotlin-stdlib", "kotlin-stdlib-jdk8",
        "sshj-0", "bcpkix-jdk15on", "bcutil-jdk15on", "bcprov-jdk15on",
        "eddsa-0", "jzlib-1.1", "jetty-jakarta-servlet-api-5",
    ];
    for dep in &deps {
        if let Ok(files) = find_files(&gradle_cache, dep) {
            for f in files {
                jars.push(f);
            }
        }
    }

    jars.join(":")
}

fn find_files(root: &str, pattern: &str) -> Result<Vec<String>, std::io::Error> {
    let mut result = Vec::new();
    if let Ok(entries) = std::fs::read_dir(root) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                if let Ok(mut sub) = find_files(path.to_str().unwrap(), pattern) {
                    result.append(&mut sub);
                }
            } else if path.to_str().map_or(false, |s| s.contains(pattern) && s.ends_with(".jar")) {
                result.push(path.to_str().unwrap().to_string());
            }
        }
    }
    Ok(result)
}

fn main() {
    let (java_child, port) = start_java_backend();

    // Forward Java stdout/stderr
    let stdout = java_child.stdout.expect("Failed to capture stdout");
    let stderr = java_child.stderr.expect("Failed to capture stderr");
    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        for line in reader.lines().flatten() {
            println!("[java] {}", line);
        }
    });
    std::thread::spawn(move || {
        let reader = BufReader::new(stderr);
        for line in reader.lines().flatten() {
            eprintln!("[java] {}", line);
        }
    });

    if let Err(e) = wait_for_server(port, 60) {
        eprintln!("Failed to start backend: {}", e);
        std::process::exit(1);
    }

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(JavaProcess(Mutex::new(Some(java_child))))
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                if let Some(state) = window.try_state::<JavaProcess>() {
                    if let Ok(mut guard) = state.0.lock() {
                        if let Some(mut child) = guard.take() {
                            let _ = child.kill();
                            let _ = child.wait();
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
