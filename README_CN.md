<p align="center">
    <img src="release/img/icon.png" width="200">
</p>

<h1 align="center">PrettyZk</h1>

<p align="center">
    <b>PrettyZk</b> 是一个基于 Web 的 ZooKeeper 图形化管理工具，源自 <a href="https://github.com/PrettyZoo/PrettyZk">PrettyZoo</a>。
</p>

<p align="center">
    <img src="https://img.shields.io/badge/language-Java%2017%20%2B%20Vue%203-blue" alt="language">
    <img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="license">
</p>

---

## ✨ 功能特性

- **Web 页面** — 原生 HTML + CSS + JS（Vue 3），无 JavaFX 依赖
- **桌面安装** — Tauri 壳提供原生窗口（macOS/Windows/Linux）
- **网页部署** — 支持 `--web` 模式部署为 Web 服务
- **实时推送** — ZK 节点变更通过 WebSocket 实时推送到前端
- **暗色/亮色主题** — CSS 变量一键切换
- **中英文切换** — 内置国际化，即时切换
- **语法高亮** — CodeMirror 6 编辑器，支持 JSON/XML 格式化
- **终端** — xterm.js 交互式 ZK 终端
- **ZK 版本兼容** — 支持 ZK 3.4+，旧版本通过原生连接兼容

## 🚀 快速开始

### 环境要求

- Java 17+
- Node.js 18+（前端开发）

### 开发模式

```bash
# 构建前端
cd webapp && npm install && npm run build

# 启动后端
cd .. && ./gradlew run
# 浏览器打开 http://127.0.0.1:{port}
```

### 桌面安装包（Tauri）

```bash
# 安装 Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 构建并运行
cd src-tauri
cargo build --release
./target/release/prettyzk

# 打包安装包
cargo tauri build
```

### Web 部署

```bash
./gradlew jar
java -jar app/build/libs/app-*.jar --web --port 8080
```

## 🔄 与 PrettyZoo 的关系

PrettyZk 是对 [PrettyZoo](https://github.com/PrettyZoo/PrettyZk)（vran-dev）的完整重写，原项目已归档停止维护。

| | PrettyZoo（原版） | PrettyZk |
|---|---|---|
| UI | JavaFX + FXML + JFoenix | Vue 3 + Vite + CSS |
| 桌面壳 | JavaFX Stage | Tauri WebView |
| Web 部署 | ❌ | ✅（--web 参数）|
| 代码编辑器 | RichTextFX | CodeMirror 6 |
| 终端 | JavaFX TextArea | xterm.js |
| 主题 | CSS 文件 | CSS 变量 |
| 国际化 | Java ResourceBundle | Vue 响应式 i18n |

## 📄 开源协议

Apache License 2.0
