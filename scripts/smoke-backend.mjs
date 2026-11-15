#!/usr/bin/env node
/**
 * Start the staged JRE + backend jars and assert that the HTTP API is healthy.
 * This catches missing jlink modules, classpath/resource mistakes and broken
 * logging before an installer is produced.
 */
import { spawn } from 'node:child_process'
import { existsSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const isWindows = process.platform === 'win32'
const java = path.join(root, 'build', 'desktop', 'runtime', 'bin', isWindows ? 'java.exe' : 'java')
const libDir = path.join(root, 'build', 'desktop', 'app', 'lib')
const TIMEOUT_MS = 60_000

if (!existsSync(java)) {
  console.error(`[smoke] missing JRE: ${java}`)
  process.exit(1)
}

const jars = readdirSync(libDir).filter((f) => f.endsWith('.jar'))
if (jars.length === 0) {
  console.error(`[smoke] no jars in ${libDir}`)
  process.exit(1)
}

const classpath = jars.map((jar) => path.join(libDir, jar)).join(path.delimiter)
const child = spawn(
  java,
  ['-cp', classpath, '-Dfile.encoding=utf-8', 'com.dujie.app.Application', '--port', '0'],
  { cwd: path.join(root, 'build', 'desktop'), windowsHide: true },
)

let output = ''
let port = null
let settled = false

function finish(code, message) {
  if (settled) return
  settled = true
  if (message) console.error(`[smoke] ${message}`)
  if (output.trim()) console.error(output.trim())
  if (!child.killed) child.kill()
  process.exit(code)
}

const timer = setTimeout(() => finish(1, `backend did not become healthy within ${TIMEOUT_MS / 1000}s`), TIMEOUT_MS)

function onLine(line) {
  output += line + '\n'
  const match = line.match(/PRETTYZK_READY (\d+)/)
  if (match && !port) {
    port = Number(match[1])
    checkHealth(port)
  }
}

function pipe(stream) {
  let buffer = ''
  stream.setEncoding('utf8')
  stream.on('data', (chunk) => {
    buffer += chunk
    let index
    while ((index = buffer.indexOf('\n')) >= 0) {
      onLine(buffer.slice(0, index).replace(/\r$/, ''))
      buffer = buffer.slice(index + 1)
    }
  })
}

pipe(child.stdout)
pipe(child.stderr)

async function checkHealth(p) {
  const url = `http://127.0.0.1:${p}/api/health`
  for (let attempt = 0; attempt < 30; attempt++) {
    try {
      const response = await fetch(url)
      const body = await response.text()
      if (response.ok && body.includes('"status":"ok"')) {
        clearTimeout(timer)
        settled = true
        console.log(`[smoke] ok: ${url} -> ${body}`)
        child.kill()
        process.exit(0)
      }
    } catch {
      // not ready yet
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  finish(1, `health check failed: ${url}`)
}

child.on('error', (error) => finish(1, `cannot start backend: ${error.message}`))
child.on('exit', (code) => {
  if (!settled) finish(1, `backend exited early with code ${code}`)
})
