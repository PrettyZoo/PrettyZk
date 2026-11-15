#!/usr/bin/env node
/**
 * Keep the application version in sync across the JVM and Tauri manifests.
 *
 *   node scripts/set-version.mjs 3.0.1
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const raw = process.argv[2]
if (!raw) {
  console.error('usage: node scripts/set-version.mjs <x.y.z>')
  process.exit(1)
}
const version = raw.replace(/^v/, '')
if (!/^\d+\.\d+\.\d+([.-][0-9A-Za-z.-]+)?$/.test(version)) {
  console.error(`invalid version: ${raw}`)
  process.exit(1)
}

function update(relativePath, transform) {
  const file = path.join(root, relativePath)
  const before = readFileSync(file, 'utf8')
  const after = transform(before)
  if (after === before) {
    console.warn(`[version] no change: ${relativePath}`)
    return
  }
  writeFileSync(file, after)
  console.log(`[version] ${relativePath}`)
}

update('build.gradle', (text) => text.replace(/version\s+'[^']+'/, `version '${version}'`))

update(
  'app/src/main/java/com/dujie/app/version/Version.java',
  (text) => text.replace(/VERSION\s*=\s*"[^"]+"/, `VERSION = "${version}"`),
)

update('src-tauri/tauri.conf.json', (text) =>
  text.replace(/("version"\s*:\s*)"[^"]+"/, `$1"${version}"`),
)

update('src-tauri/Cargo.toml', (text) => {
  const packageIndex = text.indexOf('[package]')
  if (packageIndex < 0) return text
  const head = text.slice(0, packageIndex)
  const tail = text.slice(packageIndex)
  return head + tail.replace(/version\s*=\s*"[^"]+"/, `version = "${version}"`)
})

console.log(`[version] set to ${version}`)
