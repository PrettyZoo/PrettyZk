<template>
  <div ref="terminalContainer" class="terminal-wrapper"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Terminal } from 'xterm'
import { FitAddon } from '@xterm/addon-fit'
import 'xterm/css/xterm.css'

const props = defineProps({ serverId: { type: String, required: true } })

const terminalContainer = ref(null)
let term = null
let fitAddon = null
let ws = null
let buffer = ''
let reconnectTimer = null
let reconnectAttempts = 0
const MAX_RECONNECT = 5
const RECONNECT_DELAY = 2000

onMounted(() => {
  term = new Terminal({
    cursorBlink: true,
    fontSize: 13,
    fontFamily: "'SF Mono', Monaco, Menlo, Consolas, monospace",
    theme: {
      background: '#1a1a1a',
      foreground: '#e0e0e0',
      cursor: '#e0e0e0',
    },
  })

  fitAddon = new FitAddon()
  term.loadAddon(fitAddon)
  term.open(terminalContainer.value)
  fitAddon.fit()

  term.write('PrettyZk Terminal\r\n')
  writePrompt()

  term.onKey(({ key, domEvent }) => {
    if (domEvent.key === 'Enter') {
      term.write('\r\n')
      if (buffer.trim()) {
        sendCommand(buffer.trim())
      } else {
        writePrompt()
      }
      buffer = ''
    } else if (domEvent.key === 'Backspace') {
      if (buffer.length > 0) {
        buffer = buffer.slice(0, -1)
        term.write('\b \b')
      }
    } else if (domEvent.ctrlKey && !domEvent.altKey && !domEvent.metaKey) {
      // Ctrl+C (SIGINT), Ctrl+D (EOF), etc. — send immediately, don't buffer
      sendCommand(key)
    } else if (!domEvent.altKey && !domEvent.metaKey) {
      if (domEvent.key === 'Tab') {
        buffer += '\t'
        term.write('\t')
      } else if (key.length === 1) {
        buffer += key
        term.write(key)
      }
    }
  })

  connectWs()
})

// Reconnect when serverId changes (e.g., user switches servers in parent)
watch(() => props.serverId, (newId) => {
  term.clear()
  term.write('PrettyZk Terminal\r\n')
  buffer = ''
  reconnectAttempts = 0
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  if (ws) { ws.onclose = null; ws.close() }
  connectWs()
})

onUnmounted(() => {
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (ws) { ws.onclose = null; ws.close() }
  if (term) term.dispose()
})

function connectWs() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${window.location.host}/ws/terminal/${encodeURIComponent(props.serverId)}`)

  ws.onopen = () => term.write('\x1b[32mConnected\x1b[0m\r\n')

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'output') {
        term.write(msg.data.replace(/\n/g, '\r\n'))
      } else if (msg.type === 'clear') {
        term.clear()
      } else if (msg.type === 'error') {
        term.write(`\x1b[31m${msg.data}\x1b[0m\r\n`)
      } else if (msg.type === 'info') {
        term.write(`\x1b[36m${msg.data}\x1b[0m\r\n`)
      }
    } catch (e) {
      term.write(event.data.replace(/\n/g, '\r\n'))
    }
    writePrompt()
  }

  ws.onclose = () => {
    term.write('\r\n\x1b[31mDisconnected\x1b[0m\r\n')
    if (reconnectAttempts < MAX_RECONNECT) {
      const delay = Math.min(RECONNECT_DELAY * (reconnectAttempts + 1), 10000)
      term.write(`\x1b[33mReconnecting in ${delay / 1000}s...\x1b[0m\r\n`)
      reconnectTimer = setTimeout(() => {
        reconnectAttempts++
        reconnectTimer = null
        connectWs()
      }, delay)
    } else {
      term.write('\r\n\x1b[31mMax reconnect attempts reached. Switch tab to retry.\x1b[0m\r\n')
    }
  }

  ws.onerror = () => term.write('\r\n\x1b[31mWebSocket error\x1b[0m\r\n')
}

function sendCommand(command) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ command }))
  } else {
    term.write('\r\n\x1b[31mNot connected\x1b[0m\r\n')
    writePrompt()
  }
}

function writePrompt() {
  term.write('\r\n\x1b[36mzk:/>\x1b[0m ')
}
</script>

<style scoped>
.terminal-wrapper {
  height: 100%;
  background: #1a1a1a;
}
</style>
