# PrettyZk 重构设计文档

> 版本：v1.0（2026-11-15）
> 范围：多平台安装包架构、Windows 启动修复、包结构治理、前端视觉重构
> 原则：Java 后端保留；前端保留 Vue 3；运行时完全自包含（Windows/macOS）；不留历史包袱

---

## 1. 背景与目标

PrettyZk 3.0 由三部分组成：

- **Java 后端**：Javalin + Curator，Gradle 多模块 `app / core / model / client`
- **前端**：Vue 3 + Vite + TypeScript
- **桌面壳**：Tauri 2 + Rust，负责启动 Java 后端并加载本地 Web UI

当前存在四个问题：

| # | 问题 | 影响 |
|---|---|---|
| 1 | Windows 安装后启动失败/白屏 | 用户无法使用 |
| 2 | 页面观感差、图标风格混乱、存在可读性 bug | 产品不专业 |
| 3 | 包结构/模块命名混乱，存在失效测试和 JavaFX 残留 | 维护成本高、构建不可信 |
| 4 | 打包逻辑散落在 3 个 workflow + 2 套手写安装器 | 无法一次产出多平台安装包，且容易漏拷资源 |

**目标：**

1. 一次 tag 触发 CI，产出 macOS arm64、macOS x64、Windows x64、Linux x64 安装包，汇总到同一个 Release。
2. Windows/macOS 安装包完全自包含：自带 JRE、WebView2、VC++ 运行库，用户无需安装 Java、无需联网。
3. 修复 Windows 启动白屏，并提供可见的错误诊断。
4. 前端改为紧凑、专业的桌面工具风（Vue 3 + Naive UI + Lucide）。
5. 清理所有历史包袱：JavaFX 残留、失效测试、JPMS、vendored jar、旧安装器、空壳组件。

**非目标（本期不做）：**

- 不重写 Java 后端，不改变 REST/WS 协议。
- 不把前端迁移到 React。
- 不实现自动更新（updater）。
- 不为 Linux 做完全自包含（WebKitGTK/glibc 仍依赖系统），详见 §5。

---

## 2. 现状问题与根因

### 2.1 Windows 白屏（按证据排序）

#### 根因 0：后端没有打印端口（最高置信，已核实）

`core/src/main/resources/log4j2.xml` 使用 Log4j **1.x** 的 `<log4j:configuration>` 根节点。Log4j 2.24 的 `log4j-core` 中不包含 `Log4j1ConfigurationFactory`，项目也没有引入 `log4j-1.2-api` 桥接包，因此该配置解析失败，回退到默认配置（只输出 ERROR，且不写文件）。

`Application.java` 通过 `LOG.info(...)` 公告端口：

```java
LOG.info("PrettyZk desktop app started on http://127.0.0.1:{}", port);
```

INFO 被吞掉后，`src-tauri/src/main.rs` 的 stdout 扫描永远收不到端口，`recv_timeout(120s)` 超时，kill Java 并 `process::exit(1)`。Windows release 构建带 `windows_subsystem = "windows"`，没有控制台，用户看到的就是"双击没反应/白屏"。

**修复：**

1. `log4j2.xml` 改写为 Log4j 2 `<Configuration>` 格式（Console + RollingFile）。
2. 后端启动后直接打印不依赖日志系统的 marker：

```java
System.out.println("PRETTYZK_READY " + port);
System.out.flush();
```

Rust 只解析该 marker。

#### 根因 1：Tauri 内嵌页资源必然 404

`webapp/vite.config.js` 中 `base: '/web/'`，构建产物引用 `/web/assets/*`。Tauri 的资源协议把前端挂在根路径，不存在 `/web/` 前缀，内嵌页的 JS/CSS 100% 404，窗口天然是白的。

唯一让 UI 出现的是 `main.rs` 中的：

```rust
window.eval("window.location.href = 'http://127.0.0.1:<port>'")
```

`eval` 作用于当前文档，会与初始导航竞态；Windows WebView2 与 macOS WKWebView 的就绪时机不同，eval 可能被随后的初始导航覆盖，停留在白页。

**修复：** 本地 splash 页 → 后端健康后由 Rust 调用 `WebviewWindow::navigate`。

#### 根因 2：WebView2 不可用

旧 `scripts/installer.nsi` 静默运行 WebView2 bootstrapper，失败只弹提示继续安装。离线/企业环境/被杀软拦截时 WebView2 缺失，直接白屏。

**修复：** Tauri 官方 NSIS + `webviewInstallMode: offlineInstaller`。

#### 根因 3：后端失败无诊断

release 构建无控制台，Java 子进程的 stdout/stderr 只 `println!` 到不可见控制台；后端起不来时等待 120 秒后静默退出。

**修复：** splash 错误页 + `app_log_dir()/desktop.log` + CI 冒烟测试。

#### 根因 4：手写资源布局脆弱

`resource_dir()` 有 macOS / `exe/resources` / NSIS `exe/runtime` / dev 四个分支；JRE 缺失时还会尝试往 Program Files 下载 JRE，非管理员必失败。

**修复：** Tauri `bundle.resources` 统一布局 + `app.path().resource_dir()` 单一代码路径。

### 2.2 页面观感差

- emoji 图标（🦓📁💻🔧⚡▶✎✕⟳）与内联 SVG、Unicode 符号混用。
- 浅色主题下选中服务器名 `color:#fff` 配浅灰底 `--sidebar-hover:#e4e6eb`，几乎不可见。
- `Toast.vue` 的 `onUnmounted` 未导入、`timers` 未定义，卸载时必报错。
- `App.vue` 与 `Sidebar.vue` 重复设置字号。
- `Sidebar.vue` 的 `showContextMenu` 是空函数。
- `WelcomeScreen.vue` 的"Recent Servers"卡片没有点击行为。
- 12 个组件平铺在 `webapp/src/components/`。

**结论：** 视觉问题不是 Vue 造成的，而是缺少设计系统。保留 Vue 3，接入 Naive UI + Lucide + 设计 token。

### 2.3 包结构与历史包袱

- `specification-impl` 的包没有统一前缀：`com.dujie.client.client.curator`、`com.dujie.client.config`、`com.dujie.client.util`、`com.dujie.client.zknative`、`com.dujie.client.zookeeper`。
- `app` 主类在 `com.dujie.app.Application`，测试却在 `com.dujie.app.util`。
- `FillsTest`、`PathTrieTest` 引用已不存在的类，`./gradlew test` 无法编译（CI 一直用 `-x test` 绕过）。
- `grammars/*.g4`、`assets/icon/*` 是 JavaFX 残留。
- `app/src/main/resources/web/` 只提交了生成后的 `index.html`，`assets/` 被 gitignore，单独构建会打出坏包。
- 四个 `module-info.java` + 三处 `--module-path` 编译 hack + `merged-zookeeper.jar` 都是 JavaFX 模块化时代的产物；运行时实际使用 `java -cp`。
- `VersionChecker`、`UiConfiguration`、`PathIterator`、`tauri-plugin-shell` 等无引用代码。

---

## 3. 总体架构

### 3.1 模块划分（重构后）

```
PrettyZk/
├── model/          # 领域契约与模型（原 specification）
├── client/         # Curator / 原生 ZK / JSON 配置 / 4LC 实现（原 specification-impl）
├── core/            # 领域服务与实体
├── app/             # Javalin / REST / WS / 进程入口
├── webapp/          # Vue 3 UI
├── src-tauri/       # Tauri 2 桌面壳
├── scripts/         # prepare / smoke / set-version
└── docs/            # 设计文档
```

依赖方向（单向、无循环）：

```
app ──▶ core ──▶ client ──▶ model
   │          │
   └──────────┴────▶ model
```

| 模块 | 职责 |
|---|---|
| `model` | `ZkNode`、连接/监听器接口、配置模型 |
| `client` | Curator 连接、原生 ZK 连接、JSON 配置读写、4LC 终端 |
| `core` | `ConfigurationDomainService`、`ZookeeperDomainService` |
| `app` | Javalin、REST API、静态资源、`Application.main` |
| `webapp` | UI，通过 HTTP/WS 与 `app` 通信 |

### 3.2 Java 包结构

```
com.dujie
├── app
│   ├── Application
│   ├── web/                 # Javalin server + WS manager
│   │   └── api/             # REST handlers
│   └── version/             # Version
├── core
│   ├── configuration/{entity,value,service,factory}
│   └── zookeeper/{entity,service,factory}
├── model
│   ├── config/{model}
│   ├── connection  listener  node  util
└── client
    ├── client/curator
    ├── config
    ├── util
    ├── zknative
    └── zookeeper
```

### 3.3 运行时资源布局

```
安装目录 / App Bundle
├── PrettyZk(.exe)          # Tauri shell
└── runtime/                # jlink 生成的 JRE（本平台、本架构）
    ├── bin/java[.exe]
    └── ...
└── app/lib/*.jar           # app + 依赖 + 前端静态资源
```

Tauri `bundle.resources` 使用目录源 + 空目标，把 `build/desktop/` 的内容直接铺到 `$RESOURCE/`：

```json
"bundle": {
  "resources": { "../build/desktop/": "" }
}
```

`$RESOURCE` 的平台解析由 Tauri 负责：

| 平台 | `$RESOURCE` |
|---|---|
| macOS | `PrettyZk.app/Contents/Resources` |
| Windows | exe 所在目录 |
| Linux deb | `/usr/lib/<product>` |
| Linux AppImage | `${APPDIR}/usr/lib/<product>` |

---

## 4. 多平台打包设计

### 4.1 为什么用 CI 矩阵而不是本机交叉编译

- Tauri 依赖各平台原生 WebView，官方只完全支持在目标平台构建。
- Windows MSI（WiX）不支持从 macOS 交叉编译；NSIS 跨编译仅实验性。
- macOS x64 的 DMG 必须由 x64 JDK/jlink 生成，不能在 arm64 机器上复用。
- GitHub Actions 提供 macOS arm64、macOS Intel、Windows、Linux 原生 runner。

**结论：** "一次生成全平台" = 一次 tag 触发 CI 矩阵，不是在本机一个命令产出全部。

### 4.2 构建流水线

```
npm run prepare
  ├─ webapp: npm ci && vite build            → webapp/dist/
  ├─ gradle :app:installDist              → app/build/install/app/lib/
  ├─ gradle :app:jlinkRuntime             → 最小 JRE（本平台架构）
  ├─ gradle :app:stageDesktop             → build/desktop/{runtime,app/lib}
  └─ set-version.mjs                         → 同步 4 处版本号

npx tauri build --target <target> --bundles <bundles>
  ├─ 嵌入 frontendDist（splash + UI）
  ├─ 编译 Rust
  ├─ 复制 bundle.resources
  └─ 生成 dmg / nsis / appimage / deb
```

### 4.3 CI 矩阵

| OS runner | Rust target | 产物 |
|---|---|---|
| `macos-15` | `aarch64-apple-darwin` | `PrettyZk_<ver>_aarch64.dmg` |
| `macos-15-intel` | `x86_64-apple-darwin` | `PrettyZk_<ver>_x64.dmg` |
| `windows-latest` | `x86_64-pc-windows-msvc` | `PrettyZk_<ver>_x64-setup.exe` |
| `ubuntu-22.04` | `x86_64-unknown-linux-gnu` | `.AppImage` / `.deb` |

流程：

```
build (matrix, 4 jobs)
  ├─ 安装 JDK 21 / Node 22 / Rust stable（Gradle 8.10）
  ├─ prepare-desktop
  ├─ smoke-backend（JRE + jar + /api/health）
  ├─ tauri build
  ├─ 断言产物架构与资源
  └─ upload-artifact

release (needs: build, 仅 tag)
  ├─ download-artifact (merge-multiple)
  └─ softprops/action-gh-release (draft: true)
```

- 所有平台构建 + 冒烟测试通过后才创建 draft release。
- `workflow_dispatch` 只构建并上传 artifact，不创建 Release。

### 4.4 版本同步

当前版本硬编码在 4 处：

- 根 `build.gradle` 的 `version '3.0.0'`
- `app/.../version/Version.java`
- `src-tauri/tauri.conf.json`
- `src-tauri/Cargo.toml`

`scripts/set-version.mjs <x.y.z>` 统一更新；CI 从 tag 推导 `VERSION`，本地用 `git describe --tags`。

---

## 5. 自包含与兼容性设计

### 5.1 自包含矩阵

| 依赖 | 处理方式 | 随包 |
|---|---|---|
| Java 运行时 | jlink 最小 JRE，`$RESOURCE/runtime`；生产只认 bundled runtime | ✅ |
| Java 第三方库 | `installDist` 全部 jar，`$RESOURCE/app/lib` | ✅ |
| 前端资源 | Vite 构建进 `app` jar；无 CDN/外部字体 | ✅ |
| Windows WebView2 | `offlineInstaller`（约 127MB，离线）；可选 `fixedRuntime`（约 180MB） | ✅ |
| Windows VC++ | 包内 `vc_redist.x64.exe`，NSIS hook 检测后静默安装 | ✅ |
| macOS WebView | WKWebView（OS 自带） | ✅ |
| 运行时联网 | 删除 `VersionChecker`，无遥测/更新检查 | ✅ |
| Linux WebKitGTK/glibc | 仍依赖系统库 | ⚠️ |

### 5.2 架构/平台矩阵

| 平台 | JRE | 最低系统 | 关键约束 |
|---|---|---|---|
| macOS arm64 | jlink 21 arm64 | macOS 12 | 独立 DMG；ad-hoc 签名 |
| macOS x64 | jlink 21 x64 | macOS 12 | 独立 DMG；不得复用 arm64 JRE |
| Windows x64 | jlink 21 x64 | Windows 10 1809+ | 离线 WebView2 + VC++ |
| Linux x64 | jlink 21 x64 | glibc ≥ 2.35 | AppImage/deb；WebKitGTK 4.1 |

### 5.3 jlink 模块集

```
java.base,java.compiler,java.desktop,java.instrument,java.logging,
java.management,java.naming,java.prefs,java.rmi,java.scripting,
java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,
java.xml,jdk.crypto.ec,jdk.unsupported,jdk.zipfs
```

删除 `VersionChecker` 后去掉 `java.net.http`。用 `jdeps --print-module-deps --ignore-missing-deps` 对 `app/build/install/app/lib/*.jar` 复核。

### 5.4 兼容性红线

1. 每个包只含本平台、本架构的二进制；绝不复用其他系统构建的 JRE/可执行文件。
2. 不使用系统 Java（生产构建）。
3. 路径用 `PathBuf`/`resource_dir()`，不手拼 `/`。
4. classpath 分隔符由 `cfg!(windows)` 决定；Java 固定 `-Dfile.encoding=utf-8`。
5. 图标文件名大小写必须与配置一致（Linux 大小写敏感）。
6. 每个平台打包前跑后端冒烟测试；产物做架构断言。

---

## 6. 包结构治理

### 6.1 移除 JPMS 与 vendored jar

运行时一直使用 `java -cp`，`module-info.java`、`--module-path` 编译 hack、`merged-zookeeper.jar` 都是 JavaFX 模块化时代的遗留：

- 删除 4 个 `module-info.java`。
- 删除 `core`、`model`、`client` 三处 `compileJava { ... --module-path ... }`。
- 删除 `model/libs/merged-zookeeper.jar`、`unzipZookeeper`、`buildMergedZookeeperJar`。
- `model` 改用普通 Maven 依赖：

```groovy
api('org.apache.zookeeper:zookeeper') {
    exclude group: 'junit', module: 'junit'
    exclude group: 'log4j', module: 'log4j'
    exclude group: 'org.slf4j', module: 'slf4j-log4j12'
}
```

### 6.2 死代码清理

| 项 | 原因 |
|---|---|
| `VersionChecker` | 无引用；删除运行时 GitHub API 请求 |
| `saveMainSplitPaneDividerPosition` / `saveNodeViewSplitPaneDividerPosition` | 无调用；JavaFX 分栏已移除 |
| `Configuration.UiConfiguration` / `ConfigData.UiConfig` | 仅被上述方法使用 |
| `specification/util/PathIterator.java` | 无引用 |
| `FillsTest` / `PathTrieTest` | 引用不存在的类，测试无法编译 |
| `grammars/*.g4`、`assets/icon/*` | 无引用的 JavaFX 资源 |
| `tauri-plugin-shell` + `shell:allow-open` | Rust 未使用 |
| `bin/prettyzk`、`installer.nsi`、`build-installer.sh`、`tauri.win.conf.json`、`Info.plist` | 旧打包方案 |
| `Toast.vue`、`Dialog.vue`、`WelcomeScreen.vue` | 由 Naive UI + EmptyState 替代 |

### 6.3 Web 产物与源码分离

- Vite `outDir` → `webapp/dist`；`base` → `./`（同时适配 Javalin `/web/index.html` 与 Tauri embedded root）。
- `app/build.gradle` 的 `processResources` 把 `webapp/dist` 打进 jar 的 `web/`。
- 删除已提交的 `app/src/main/resources/web/`，`webapp/dist/` 加入 `.gitignore`。
- `webapp/public/splash.html` 作为 Tauri splash，Vite 会自动复制到 dist 根。

### 6.4 根目录

删除：

```
bin/prettyzk
scripts/installer.nsi
scripts/build-installer.sh
src-tauri/tauri.win.conf.json
src-tauri/Info.plist
app/src/main/resources/grammars/
app/src/main/resources/assets/icon/
app/src/test/java/com/dujie/app/util/
specification/libs/
```

新增：

```
scripts/prepare-desktop.mjs
scripts/smoke-backend.mjs
scripts/set-version.mjs
src-tauri/windows/hooks.nsh
webapp/public/splash.html
docs/refactor-design.md
```

---

## 7. 前端设计

### 7.1 技术栈

| 层 | 选型 |
|---|---|
| 框架 | Vue 3 + Vite + TypeScript（保留） |
| 组件库 | Naive UI |
| 图标 | Lucide Vue Next |
| 编辑器 | CodeMirror 6（保留） |
| 终端 | xterm.js（保留） |
| 主题 | Naive UI `darkTheme` + `themeOverrides` |
| 全局反馈 | `createDiscreteApi(['message','dialog'])` |

**为什么不是 React：** 视觉上限由组件库、设计 token、图标系统决定，与框架关系不大。换 React 需要重写 12 个组件，风险和工期都不划算。

### 7.2 设计 token

| Token | Light | Dark |
|---|---|---|
| Accent | `#0F9D8A` | `#4FD1C5` |
| Background | `#FFFFFF` / `#F6F7F9` | `#15171C` / `#1C1F26` |
| Border | `#E3E5EA` | `#2A2E37` |
| Text | `#1B1D23` / `#6B7280` | `#E6E8EE` / `#9AA1AE` |
| Radius | 6px | 6px |
| Base font | 13px | 13px |
| Code font | `ui-monospace, SFMono-Regular, Menlo, Consolas` | 同左 |

字号设置绑定到 `themeOverrides.common.fontSize` 及派生尺寸，不再改 `document.documentElement.style.fontSize`。

### 7.3 页面布局

```
┌──────────────────┬──────────────────────────────────────────┐
│ PrettyZk         │  Browse   Terminal   4LC                  │
│ [搜索连接…]      │  /  apps  /  config                      │
│ ┌──────────────┐ │ ┌──────────────┬───────────────────────┐ │
│ │ ● local      │ │ │ ▸ /          │ dataVersion   12      │ │
│ │ ○ prod-zk    │ │ │   ▸ apps     │ dataLength    48 B    │ │
│ │ ○ staging    │ │ │     ▸ config │ children      3       │ │
│ └──────────────┘ │ │              │ [RAW][JSON][XML]      │ │
│ + 新建连接       │ │              │ { ... }               │ │
│──────────────────│ └──────────────┴───────────────────────┘ │
│ 日志  中/EN  字号 │ ● Connected · 3 nodes · v3.0.0           │
└──────────────────┴──────────────────────────────────────────┘
```

### 7.4 组件目录

```
webapp/src/
├── App.vue  main.ts  api.ts  i18n.ts
├── theme.ts
├── styles/{tokens.css,global.css}
├── composables/feedback.ts
└── components/
    ├── layout/{AppSidebar,StatusBar,EmptyState}.vue
    ├── server/{ServerList,ServerForm}.vue
    ├── node/{NodeBrowser,NodeTree,NodeDetail}.vue
    ├── tools/{TerminalPanel,LogViewer}.vue
    └── common/{ConfirmDialog,FormDialog}.vue
```

### 7.5 关键交互

- 侧栏连接项：状态点、host:port、hover 操作；右键菜单 Connect / Disconnect / Edit / Delete。
- 节点树：Lucide `ChevronRight / Folder / FolderOpen / Zap`；方向键导航；选中态 accent 左边线 + 淡底。
- 分区可拖拽（树 / 编辑器）。
- 空状态：品牌图标 + 说明 + 新建连接 CTA。
- 弹窗/提示统一走 Naive UI，删除 `window.__toast` / `window.__dialog`。
- 键盘：Tab 焦点、树方向键/回车、Esc 关闭弹窗。

---

## 8. 实施计划

| 阶段 | 内容 | 验收 |
|---|---|---|
| 0 | 快速止血：Log4j2 配置 + `PRETTYZK_READY` marker + Rust `navigate` | Windows/macOS 能启动，marker 可见 |
| 1a | 删死代码、失效测试、JavaFX 资源、旧安装器 | `./gradlew clean build` 通过 |
| 1b | 模块/包重命名（`model / client / core / app`），删除 JPMS/merged jar | 编译、测试、冒烟通过 |
| 1c | web 产物分离、前端目录分组 | jar 内 `web/` 完整 |
| 2 | Gradle jlink/stage、prepare 脚本、Tauri resources、Rust splash/诊断 | 本机 DMG 可安装启动 |
| 3 | 单 matrix CI、版本同步、产物断言 | tag 产出 4 平台 draft release |
| 4 | Windows 干净机离线验证 | 无 Java/无 WebView2 环境可启动 |
| 5 | 前端 Naive UI 重构 | 亮/暗主题、900×500、全功能回归 |

---

## 9. 验证与验收

### 9.1 后端/打包

```bash
./gradlew clean build
npm run prepare
node scripts/smoke-backend.mjs
npx tauri build --bundles dmg
```

- `smoke-backend.mjs` 等待 `PRETTYZK_READY <port>`，请求 `/api/health`。
- 安装包内断言：`runtime/bin/java[.exe]`、`app/lib/*.jar`、前端 `web/index.html`。
- 架构断言：`lipo -info`（macOS）、PE 头（Windows）、`file`（Linux）。

### 9.2 UI

- `npm run build` 无错误、无 Vue 警告。
- 明暗主题、中英切换、字号 10–24、900×500 / 1440×900。
- 服务器增删改查、连接、树展开/搜索、节点增删改、RAW/JSON/XML、终端、4LC、日志。
- 键盘可达性。

### 9.3 安装矩阵

| 平台 | 场景 |
|---|---|
| Windows 10 / 11 | 干净机、断网、无系统 Java、无 WebView2 |
| macOS 12+ arm64/x64 | 下载 DMG、拖入 Applications、启动 |
| Ubuntu 22.04 / 24.04 | deb / AppImage 安装与启动 |

---

## 10. 风险与应对

| 风险 | 应对 |
|---|---|
| Windows 仍被 SmartScreen 拦截 | 未签名无法彻底消除；文档说明；后续购买代码签名证书 |
| macOS Gatekeeper 拦截未签名 App | ad-hoc 签名 + 文档说明 `xattr -cr`；后续 Developer ID |
| WebView2 离线安装包体积大 | 默认 `offlineInstaller`；提供 `fixedRuntime` 备选 |
| Linux WebKitGTK 依赖 | 文档明确；如需完全自包含，后续评估 Flatpak |
| 包名/模块重命名引入回归 | 按 1a/1b/1c 分步提交，每步 `clean build` + 冒烟 |
| Tauri bundler 资源路径差异 | 用官方目录源 + 空目标；CI 逐平台断言 |
| jlink 模块缺失 | `jdeps` 复核 + 每平台冒烟测试 |

---

## 11. 附录：关键命令

```bash
# 本地准备后端 payload
npm run prepare

# 后端冒烟（不启动 GUI）
node scripts/smoke-backend.mjs

# 本机打包（macOS 只能出 macOS 包）
npx tauri build --bundles dmg

# 发布：一次 tag 触发全平台 CI
git tag v3.0.1
git push origin v3.0.1

# 手动触发 CI（只出 artifact）
# GitHub Actions -> Release -> Run workflow
```

---

## 12. 结论

- **Windows 白屏不是系统兼容性问题**，而是 Log4j 配置 + 端口 marker + eval 导航 + 非自包含运行时叠加导致的。
- **多平台安装包靠 CI 矩阵**，不是本机交叉编译；每个包只含本平台二进制。
- **完全自包含在 Windows/macOS 可实现**（JRE + 离线 WebView2 + VC++），Linux 的 WebKitGTK/glibc 仍依赖系统。
- **前端不换语言**，用 Naive UI + 设计 token + Lucide 达到专业桌面工具观感。
- **历史包袱一次清掉**：JPMS、merged jar、JavaFX 残留、失效测试、旧安装器、空壳组件。
