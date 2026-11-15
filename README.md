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

- JDK 21 (LTS)
- Node.js 22
- Rust stable (for the Tauri shell)

### Build & Install

```bash
npm install
npm run prepare          # UI + backend jars + minimal JRE -> build/desktop
npm run smoke            # starts the bundled JRE and checks /api/health
npx tauri build --bundles dmg   # macOS; use --bundles nsis on Windows
```

The installer is self-contained: it ships the JDK runtime, all Java jars,
WebView2 (Windows, offline installer) and the VC++ runtime. End users do not
need Java, Node, Rust, or an internet connection.

### Development

```bash
# Option A: full desktop shell (splash + embedded backend)
npm run prepare
npm run dev

# Option B: browser UI + backend only
app/build/install/app/bin/app --port 8080
npm run web:dev          # http://localhost:5173, proxies /api and /ws to 8080
```

### Release (all platforms at once)

```bash
git tag v3.0.1
git push origin v3.0.1
```

A single GitHub Actions matrix builds macOS arm64/x64, Windows x64 and
Linux x64, then attaches every installer to one draft release. Native
installers cannot be reliably cross-built from macOS, so CI is the supported
way to produce all platforms in one run.

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
| Build | Gradle 8.10 + jlink 21 |

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
| Backend (model/client/core/app) | Same ✅ | Same ✅ |

## 📄 License

Apache License 2.0. See [LICENSE](LICENSE) for details.
