#!/usr/bin/env node
/**
 * Build the self-contained backend payload consumed by the Tauri bundler.
 *
 *   1. webapp: install + vite build -> webapp/dist
 *   2. Gradle: installDist + jlink + stage -> build/desktop
 *   3. verify JRE, jars, splash and UI assets
 *   4. optionally sync the version from the VERSION env var / git tag
 *
 * The script resolves the repository root from its own location, so it works
 * no matter which directory the harness invokes it from.
 */
import { execFileSync, spawnSync } from 'node:child_process'
import { existsSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const isWindows = process.platform === 'win32'
const npm = isWindows ? 'npm.cmd' : 'npm'
const gradlew = isWindows ? path.join(root, 'gradlew.bat') : path.join(root, 'gradlew')

function run(command, args, options = {}) {
  console.log(`\n$ ${command} ${args.join(' ')}`)
  const result = spawnSync(command, args, {
    cwd: options.cwd ?? root,
    stdio: 'inherit',
    shell: isWindows,
    env: process.env,
  })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

function fail(message) {
  console.error(`\n[prepare-desktop] ${message}`)
  process.exit(1)
}

function currentVersion() {
  if (process.env.VERSION) return process.env.VERSION.replace(/^v/, '')
  try {
    return execFileSync('git', ['describe', '--tags', '--abbrev=0'], {
      cwd: root,
      encoding: 'utf8',
    }).trim().replace(/^v/, '')
  } catch {
    return null
  }
}

const version = currentVersion()
if (version) {
  run(process.execPath, [path.join(root, 'scripts', 'set-version.mjs'), version])
}

run(npm, ['--prefix', 'webapp', 'ci', '--no-audit', '--no-fund'], { cwd: root })
run(npm, ['--prefix', 'webapp', 'run', 'build'], { cwd: root })
run(gradlew, ['clean', 'build', ':app:stageDesktop', '--no-daemon'], { cwd: root })

const javaName = isWindows ? 'java.exe' : 'java'
const java = path.join(root, 'build', 'desktop', 'runtime', 'bin', javaName)
const libDir = path.join(root, 'build', 'desktop', 'app', 'lib')
const distDir = path.join(root, 'webapp', 'dist')

if (!existsSync(java)) fail(`missing JRE: ${java}`)
if (!existsSync(libDir) || readdirSync(libDir).filter((f) => f.endsWith('.jar')).length === 0) {
  fail(`missing backend jars: ${libDir}`)
}
if (!existsSync(path.join(distDir, 'index.html'))) fail(`missing UI build: ${distDir}/index.html`)
if (!existsSync(path.join(distDir, 'splash.html'))) fail(`missing splash page: ${distDir}/splash.html`)

console.log(`\n[prepare-desktop] ready: ${version ? `v${version}` : 'unknown version'}`)
console.log(`  JRE:     ${java}`)
console.log(`  Jars:    ${libDir}`)
console.log(`  UI:      ${distDir}`)
