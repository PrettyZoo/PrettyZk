<p align="center">
    <img src="release/img/icon.png" width="200">
</p>

<h1 align="center">PrettyZk</h1>

<p align="center">
    <b>PrettyZk</b> is a modern web-based ZooKeeper GUI client, evolved from <a href="https://github.com/vran-dev/PrettyZoo">PrettyZoo</a>.
</p>

<p align="center">
    <img src="https://img.shields.io/badge/language-Java%2017%20%2B%20Vue%203-blue" alt="language">
    <img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="license">
</p>

---

## ✨ Features

- **Web-based UI** — Native HTML + CSS + JS (Vue 3), no JavaFX dependency
- **Desktop app** — Tauri shell provides a native window (macOS/Windows/Linux)
- **Web deployment** — Can also be deployed as a standalone web service
- **Real-time updates** — ZK node changes pushed via WebSocket
- **Dark/Light themes** — CSS variable based, toggle instantly
- **i18n** — English and Chinese UI, switch anytime
- **Syntax highlighting** — CodeMirror 6 editor with JSON/XML formatting
- **Terminal** — xterm.js based interactive ZK terminal
- **ZK 3.4+ compatible** — Supports legacy ZK servers via native connection

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Node.js 18+ (for frontend development)

### Development

```bash
# Build frontend
cd webapp && npm install && npm run build

# Start backend
cd .. && ./gradlew run
# Open http://127.0.0.1:{port}
```

### Desktop Build (Tauri)

```bash
# Install Rust (if not installed)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Build and run
cd src-tauri
cargo build --release
./target/release/prettyzk

# Package installer
cargo tauri build
```

### Docker / Web Deployment

```bash
# Build the jar
./gradlew jar

# Run as web service
java -jar app/build/libs/app-*.jar --web --port 8080
```

## 📦 Tech Stack

| Layer | Technology |
|---|---|
| HTTP Server | Javalin 6 (embedded Jetty) |
| REST API | Javalin + Jackson |
| Frontend | Vue 3 + Vite + vanilla CSS |
| Editor | CodeMirror 6 |
| Terminal | xterm.js |
| Desktop Shell | Tauri 2 (Rust) |
| ZK Client | Apache Curator 5.x |
| Build | Gradle + jlink + jpackage |

## 🔄 Relationship to PrettyZoo

PrettyZk is a complete rewrite of [PrettyZoo](https://github.com/vran-dev/PrettyZoo) (by vran-dev), which is now archived. Key differences:

| | PrettyZoo (original) | PrettyZk |
|---|---|---|
| UI | JavaFX + FXML + JFoenix | Vue 3 + Vite + CSS |
| Desktop | JavaFX Stage | Tauri WebView |
| Web Deployment | ❌ | ✅ (--web flag) |
| Code Editor | RichTextFX | CodeMirror 6 |
| Terminal | JavaFX TextArea | xterm.js |
| Theme | CSS files | CSS variables |
| i18n | Java ResourceBundle | Vue reactive i18n |

## 📄 License

Apache License 2.0. See [LICENSE](LICENSE) for details.
