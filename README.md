<p align="center">
    <img src="release/img/icon.png" width="200">
</p>

<h1 align="center">PrettyZk</h1>

<p align="center">
    <b>PrettyZk</b> is a modern desktop ZooKeeper GUI client, evolved from <a href="https://github.com/PrettyZoo/PrettyZk">PrettyZoo</a>.
</p>

<p align="center">
    <img src="https://img.shields.io/badge/language-Java%2017%20%2B%20Vue%203-blue" alt="language">
    <img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="license">
</p>

---

## ✨ Features

- **Desktop native** — Tauri shell provides a native window (macOS/Windows/Linux)
- **Modern UI** — Vue 3 + Vite, no JavaFX dependency
- **Real-time updates** — ZK node changes pushed via WebSocket
- **Dark/Light themes** — CSS variable based, toggle instantly
- **i18n** — English and Chinese UI, switch anytime
- **Syntax highlighting** — CodeMirror 6 editor with JSON/XML formatting
- **Interactive terminal** — xterm.js based ZK command line
- **ZK 3.4+ compatible** — Supports legacy ZK servers via native connection
- **Multiple connections** — Manage multiple ZK clusters simultaneously
- **ACL & SSH Tunnel** — Full ACL and SSH tunnel support

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Rust (for Tauri build)

### Build & Run

```bash
# 1. Build backend distribution
./gradlew :app:installDist

# 2. Build desktop app
cd src-tauri
cargo build --release

# 3. Run
./target/release/prettyzk
```

### Development (without Tauri)

```bash
# Build frontend
cd webapp && npm install && npm run build

# Start backend
cd .. && ./bin/prettyzk
# Open http://127.0.0.1:{port} in browser for dev
```

### Package Installer

```bash
cd src-tauri
cargo tauri build
# Output: src-tauri/target/release/bundle/
```

## 📦 Tech Stack

| Layer | Technology |
|---|---|
| Desktop Shell | Tauri 2 (Rust) |
| Frontend | Vue 3 + TypeScript + Vite |
| HTTP Server | Javalin 6 (embedded Jetty) |
| REST API | Javalin + Jackson |
| Editor | CodeMirror 6 |
| Terminal | xterm.js |
| ZK Client | Apache Curator 5.x |
| Build | Gradle + jlink |

## 🔄 Relationship to PrettyZoo

PrettyZk is a complete rewrite of [PrettyZoo](https://github.com/PrettyZoo/PrettyZk) (by vran-dev), which is now archived.

| | PrettyZoo (original) | PrettyZk |
|---|---|---|
| UI | JavaFX + FXML + JFoenix | Vue 3 + TypeScript + CSS |
| Desktop | JavaFX Stage | Tauri WebView |
| Code Editor | RichTextFX | CodeMirror 6 |
| Terminal | JavaFX TextArea | xterm.js |
| Theme | Separate CSS files | CSS variables |
| i18n | Java ResourceBundle | Vue reactive i18n |

## 📄 License

Apache License 2.0. See [LICENSE](LICENSE) for details.
