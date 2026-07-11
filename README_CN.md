<p align="center">
    <img src="release/img/icon.png" width="200">
</p>

<h1 align="center">PrettyZk</h1>

<p align="center">
    <b>PrettyZk</b> 是一款现代化的 ZooKeeper 桌面客户端，源自 <a href="https://github.com/PrettyZoo/PrettyZk">PrettyZoo</a>。
</p>

<p align="center">
    <img src="https://img.shields.io/badge/language-Java%2017%20%2B%20Vue%203-blue" alt="language">
    <img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="license">
</p>

---

## ✨ 功能特性

- **桌面原生体验** — Tauri 壳提供原生窗口（macOS/Windows/Linux）
- **现代化 UI** — Vue 3 + TypeScript，无 JavaFX 依赖
- **实时推送** — ZK 节点变更通过 WebSocket 实时更新
- **暗色/亮色主题** — CSS 变量一键切换
- **中英文切换** — 内置国际化，即时切换
- **语法高亮** — CodeMirror 6 编辑器，支持 JSON/XML 格式化
- **交互式终端** — xterm.js 命令行操作
- **ZK 版本兼容** — 支持 ZK 3.4+，旧版本通过原生连接兼容
- **多连接管理** — 同时管理多个 ZK 集群
- **ACL & SSH 隧道** — 完整的 ACL 和 SSH 隧道支持

## 🚀 快速开始

### 环境要求

- Java 17+
- Rust（构建 Tauri 桌面壳需要）

### 构建并运行

```bash
# 1. 构建后端分发
./gradlew :app:installDist

# 2. 构建桌面应用
cd src-tauri
cargo build --release

# 3. 启动
./target/release/prettyzk
```

### 开发模式（跳过 Tauri）

```bash
# 构建前端
cd webapp && npm install && npm run build

# 启动后端
cd .. && ./bin/prettyzk
# 浏览器打开 http://127.0.0.1:{port} 调试
```

### 打包安装程序

```bash
cd src-tauri
cargo tauri build
# 安装包在 src-tauri/target/release/bundle/
```

## 📦 技术栈

| 层 | 技术 |
|---|---|
| 桌面壳 | Tauri 2 (Rust) |
| 前端 | Vue 3 + TypeScript + Vite |
| HTTP 服务 | Javalin 6 (内嵌 Jetty) |
| REST API | Javalin + Jackson |
| 编辑器 | CodeMirror 6 |
| 终端 | xterm.js |
| ZK 客户端 | Apache Curator 5.x |
| 构建 | Gradle + jlink |

## 🔄 与 PrettyZoo 的关系

PrettyZk 是基于 [PrettyZoo](https://github.com/PrettyZoo/PrettyZk)（vran-dev）的 UI 现代化改造，将 JavaFX 前端替换为 Vue 3 + Tauri，核心 ZK 领域逻辑保持不变。

| | PrettyZoo（原版） | PrettyZk |
|---|---|---|
| 前端 | JavaFX + FXML + JFoenix | Vue 3 + TypeScript + Vite |
| 桌面壳 | JavaFX Stage | Tauri WebView |
| 代码编辑器 | RichTextFX | CodeMirror 6 |
| 终端 | JavaFX TextArea | xterm.js |
| 主题 | CSS 文件切换 | CSS 变量 |
| 国际化 | Java ResourceBundle | Vue 响应式 i18n |
| 后端 (core/spec) | 相同 ✅ | 相同 ✅ |

## 📄 开源协议

Apache License 2.0
