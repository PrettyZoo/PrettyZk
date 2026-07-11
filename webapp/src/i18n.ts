// PrettyZk Web UI - Internationalization

import { ref } from 'vue'

const messages: Record<string, Record<string, unknown>> = {
  en: {
    app: { name: 'PrettyZk' },
    sidebar: {
      brand: 'PrettyZk',
      newConnection: 'New Connection',
      servers: 'Servers',
      noServers: 'No servers yet. Click "+" to add one.',
      logs: 'Logs',
      exp: 'Exp',
      imp: 'Imp',
      exportTooltip: 'Export Configuration',
      importTooltip: 'Import Configuration',
      logsTooltip: 'Application Logs',
      langTooltip: 'Switch Language',
      fontTooltip: 'Font Size',
      themeLight: 'Switch to Light',
      themeDark: 'Switch to Dark',
      importSuccess: 'Config imported, please restart',
      importFailed: 'Import failed: {msg}',
    },
    welcome: {
      title: 'Welcome to PrettyZk',
      subtitle: 'A beautiful Zookeeper GUI client',
      newConn: 'New Connection',
      newConnDesc: 'Add a ZooKeeper server to get started',
      recent: 'Recent Servers',
      recentDesc: 'Click a server from the sidebar to connect',
    },
    server: {
      newServer: 'New Server',
      editServer: 'Edit Server',
      host: 'Host',
      port: 'Port',
      alias: 'Alias',
      acl: 'ACL',
      sshTunnel: 'SSH Tunnel',
      sshHost: 'SSH Host',
      sshPort: 'SSH Port',
      sshUser: 'SSH Username',
      sshPass: 'SSH Password',
      remoteHost: 'Remote Host',
      remotePort: 'Remote Port',
      advConfig: 'Advanced',
      connTimeout: 'Connection Timeout (ms)',
      sessionTimeout: 'Session Timeout (ms)',
      maxRetries: 'Max Retries',
      retryInterval: 'Retry Interval (ms)',
      zkVersion: 'ZK Version',
      zkVersionAuto: 'Auto (Curator 5.x)',
      zkVersion34: '3.4.x (Native)',
      zkVersion35: '3.5.x (Backward)',
      zkVersion36: '3.6+ (Default)',
      save: 'Save',
      cancel: 'Cancel',
      saved: 'Server saved',
      saveFailed: 'Failed to save: {msg}',
      delete: 'Delete',
      deleteConfirm: 'Are you sure you want to delete this server?',
      deleteTitle: 'Delete Server',
      deleted: 'Server deleted',
      deleteFailed: 'Failed to delete: {msg}',
      loadFailed: 'Failed to load server: {msg}',
      connect: 'Connect',
      disconnect: 'Disconnect',
      connected: 'Connected',
      disconnected: 'Disconnected',
    },
    node: {
      browse: 'Browse',
      terminal: 'Terminal',
      fourLetterCmd: '4-Letter Cmd',
      sync: 'Sync',
      add: 'Add',
      back: 'Back',
      search: 'Search nodes...',
      root: '/ (root)',
      selectNode: 'Select a node to view details',
      loading: 'Loading...',
      connecting: 'Connecting...',
      addNode: 'Add Node',
      parentPath: 'Parent Path',
      nodeName: 'Node Name',
      data: 'Data',
      nodeMode: 'Node Mode',
      save: 'Save',
      dataSaved: 'Data saved',
      saveFailed: 'Failed to save: {msg}',
      deleteNode: 'Delete Node',
      deleteConfirm: 'Delete {path}?',
      deleted: 'Node deleted',
      deleteFailed: 'Failed: {msg}',
      created: 'Node created',
      loadFailed: 'Failed to load: {msg}',
      synced: 'Synced',
      enterCmd: 'Enter 4-letter command',
      error: 'Error: {msg}',
      path: 'Path',
      dataLength: 'Data Length',
      children: 'Children',
      dataVersion: 'Data Version',
      ephemeral: 'Ephemeral',
      createdTime: 'Created',
      modifiedTime: 'Modified',
      nodeData: 'Node Data',
      cmd: 'Command',
      execute: 'Execute',
      empty: '(empty)',
      addNodeTitle: 'Add Node',
      modePersistent: 'Persistent',
      modePersistentDesc: 'Persistent - survives session disconnects',
      modeEphemeral: 'Ephemeral',
      modeEphemeralDesc: 'Ephemeral - deleted when session ends',
      modePersistentSeq: 'Persistent Sequential',
      modePersistentSeqDesc: 'Persistent with auto-incrementing suffix',
      modeEphemeralSeq: 'Ephemeral Sequential',
      modeEphemeralSeqDesc: 'Ephemeral with auto-incrementing suffix',
    },
    log: {
      title: 'Application Logs',
      clear: 'Clear',
      streaming: 'Streaming logs...',
      connecting: 'Connecting...',
      connectFailed: 'Failed to connect: {msg}',
    },
    config: {
      exportSuccess: 'Config exported',
      exportFailed: 'Export failed: {msg}',
    },
    common: {
      confirm: 'Confirm',
      cancel: 'Cancel',
      ok: 'OK',
      error: 'Error',
      success: 'Success',
      info: 'Info',
    },
  },

  zh: {
    app: { name: 'PrettyZk' },
    sidebar: {
      brand: 'PrettyZk',
      newConnection: '新建连接',
      servers: '服务器列表',
      noServers: '暂无服务器，点击"+"添加',
      logs: '日志',
      exp: '导出',
      imp: '导入',
      exportTooltip: '导出配置',
      importTooltip: '导入配置',
      logsTooltip: '应用日志',
      langTooltip: '切换语言',
      fontTooltip: '字体大小',
      themeLight: '切换亮色主题',
      themeDark: '切换暗色主题',
      importSuccess: '配置已导入，请重启应用',
      importFailed: '导入失败: {msg}',
    },
    welcome: {
      title: '欢迎使用 PrettyZk',
      subtitle: '优雅的 ZooKeeper 图形化管理工具',
      newConn: '新建连接',
      newConnDesc: '添加 ZooKeeper 服务器开始使用',
      recent: '最近使用的服务器',
      recentDesc: '从左侧边栏点击服务器进行连接',
    },
    server: {
      newServer: '新建服务器',
      editServer: '编辑服务器',
      host: '主机地址',
      port: '端口',
      alias: '别名',
      acl: 'ACL',
      sshTunnel: 'SSH 隧道',
      sshHost: 'SSH 主机',
      sshPort: 'SSH 端口',
      sshUser: 'SSH 用户名',
      sshPass: 'SSH 密码',
      remoteHost: '远程主机',
      remotePort: '远程端口',
      advConfig: '高级配置',
      connTimeout: '连接超时(毫秒)',
      sessionTimeout: '会话超时(毫秒)',
      maxRetries: '最大重试次数',
      retryInterval: '重试间隔(毫秒)',
      zkVersion: 'ZK 版本',
      zkVersionAuto: '自动 (Curator 5.x)',
      zkVersion34: '3.4.x (原生模式)',
      zkVersion35: '3.5.x (向后兼容)',
      zkVersion36: '3.6+ (默认)',
      save: '保存',
      cancel: '取消',
      saved: '服务器已保存',
      saveFailed: '保存失败: {msg}',
      delete: '删除',
      deleteConfirm: '确定要删除此服务器吗？',
      deleteTitle: '删除服务器',
      deleted: '服务器已删除',
      deleteFailed: '删除失败: {msg}',
      loadFailed: '加载服务器失败: {msg}',
      connect: '连接',
      disconnect: '断开',
      connected: '已连接',
      disconnected: '已断开',
    },
    node: {
      browse: '浏览',
      terminal: '终端',
      fourLetterCmd: 'ZK 管理命令',
      sync: '同步',
      add: '添加',
      back: '返回',
      search: '搜索节点...',
      root: '/ (根节点)',
      selectNode: '选择一个节点查看详情',
      loading: '加载中...',
      connecting: '连接中...',
      addNode: '添加节点',
      parentPath: '父路径',
      nodeName: '节点名称',
      data: '数据',
      nodeMode: '节点模式',
      save: '保存',
      dataSaved: '数据已保存',
      saveFailed: '保存失败: {msg}',
      deleteNode: '删除节点',
      deleteConfirm: '确定删除 {path} 吗？',
      deleted: '节点已删除',
      deleteFailed: '删除失败: {msg}',
      created: '节点已创建',
      loadFailed: '加载失败: {msg}',
      synced: '已同步',
      enterCmd: '请输入4位管理命令',
      error: '错误: {msg}',
      path: '路径',
      dataLength: '数据长度',
      children: '子节点',
      dataVersion: '数据版本',
      ephemeral: '临时节点',
      createdTime: '创建时间',
      modifiedTime: '修改时间',
      nodeData: '节点数据',
      cmd: '命令',
      execute: '执行',
      empty: '(空)',
      addNodeTitle: '添加节点',
      modePersistent: '持久节点',
      modePersistentDesc: '持久节点 - 断开连接后仍存在',
      modeEphemeral: '临时节点',
      modeEphemeralDesc: '临时节点 - 会话结束自动删除',
      modePersistentSeq: '持久顺序节点',
      modePersistentSeqDesc: '持久节点，带自动递增序号',
      modeEphemeralSeq: '临时顺序节点',
      modeEphemeralSeqDesc: '临时节点，带自动递增序号',
    },
    log: {
      title: '应用日志',
      clear: '清空',
      streaming: '日志流...',
      connecting: '连接中...',
      connectFailed: '连接失败: {msg}',
    },
    config: {
      exportSuccess: '配置已导出',
      exportFailed: '导出失败: {msg}',
    },
    common: {
      confirm: '确认',
      cancel: '取消',
      ok: '确定',
      error: '错误',
      success: '成功',
      info: '信息',
    },
  },
}

const LOCALE_STORAGE_KEY = 'prettyzk-locale'
const fallbackLocale = 'en'

function detectLocale(): string {
  // Check localStorage first
  const saved = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (saved && messages[saved]) return saved
  // Fall back to browser language
  return navigator.language?.startsWith('zh') ? 'zh' : 'en'
}

const localeState = ref(detectLocale())

export function t(key: string, params: Record<string, string> = {}) {
  const currentLocale = localeState.value
  const keys = key.split('.')
  let msg: unknown = messages[currentLocale]
  for (const k of keys) {
    msg = (msg as Record<string, unknown>)?.[k]
    if (msg === undefined) break
  }
  if (msg === undefined) {
    msg = messages[fallbackLocale]
    for (const k of keys) {
      msg = (msg as Record<string, unknown>)?.[k]
      if (msg === undefined) break
    }
  }
  if (typeof msg === 'string') {
    return msg.replace(/\{(\w+)\}/g, (_, k) => params[k] ?? `{${k}}`)
  }
  return key
}

export function setLocale(locale: string) {
  if (messages[locale]) {
    localeState.value = locale
    localStorage.setItem(LOCALE_STORAGE_KEY, locale)
    return true
  }
  return false
}

export function getLocale() {
  return localeState.value
}
