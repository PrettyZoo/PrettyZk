<p align="center">
    <img src="release/img/icon.png" width="200">
</p>

<h1 align="center">PrettyZk</h1>

<p align="center">
    <b>PrettyZk</b> is a modern desktop ZooKeeper GUI client, evolved from <a href="https://github.com/PrettyZoo/PrettyZk">PrettyZoo</a>.
</p>

<p align="center">
    <img src="https://img.shields.io/badge/language-Java%2021%20%2B%20Vue%203-blue" alt="language">
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

- Java 21+
- Rust (for Tauri build)
- WebView2 (Windows only, auto-installed by MSI)

### Build & Run

```bash
# 1. Build frontend
cd webapp && npm install && npm run build

# 2. Build backend distribution
./gradlew :app:installDist

# 3. Build and run desktop app
cd src-tauri
cargo build --release
./target/release/prettyzoo
```

### Development (without Tauri)

```bash
# Build frontend
cd webapp && npm install && npm run build

# Start backend
cd .. && app/build/install/app/bin/app
# Open http://127.0.0.1:{port} in browser for dev
```

### Package Installer

```bash
# macOS
./scripts/build-installer.sh

# Windows (requires NSIS and WiX Toolset)
cd src-tauri && npx tauri build
# Output: src-tauri/target/release/bundle/msi/
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

PrettyZk is a UI modernization of [PrettyZoo](https://github.com/PrettyZoo/PrettyZk) (by vran-dev), replacing the JavaFX frontend with Vue 3 + Tauri while keeping the core ZK domain logic intact.

| | PrettyZoo (original) | PrettyZk |
|---|---|---|
| Frontend | JavaFX + FXML + JFoenix | Vue 3 + TypeScript + Vite |
| Desktop Shell | JavaFX Stage | Tauri WebView |
| Code Editor | RichTextFX | CodeMirror 6 |
| Terminal | JavaFX TextArea | xterm.js |
| Theme | CSS file swap | CSS variables |
| i18n | Java ResourceBundle | Vue reactive i18n |
| Backend (core/spec) | Same ✅ | Same ✅ |

## 📄 License

Apache License 2.0. See [LICENSE](LICENSE) for details.
