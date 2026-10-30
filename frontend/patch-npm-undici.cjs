// Compatible security maintenance for the undici bundled by npm 12.2.1.
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const crypto = require('node:crypto')
const { execFileSync } = require('node:child_process')
// Executáveis por caminho fixo, nunca resolvidos pelo PATH: o npm vive ao lado do node em uso
// e o tar do sistema fica em um diretório não gravável.
const npm = path.join(path.dirname(process.execPath), 'npm')
const tar = ['/usr/bin/tar', '/bin/tar'].find((candidate) => fs.existsSync(candidate))
if (!tar) throw new Error('tar not found in a fixed system directory')
const root = path.join(execFileSync(npm, ['root', '-g'], { encoding: 'utf8' }).trim(), 'npm')
if (require(path.join(root, 'package.json')).version !== '12.2.1') throw new Error('Expected npm 12.2.1')
const range = require(path.join(root, 'node_modules/node-gyp/package.json')).dependencies.undici
const semver = require(path.join(root, 'node_modules/semver'))
if (!semver.satisfies('6.29.1', range)) throw new Error('Undici patch is outside the supported dependency range')
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'npm-undici-patch-'))
try {
  execFileSync(npm, ['pack', 'undici@6.29.1', '--ignore-scripts', '--silent', '--pack-destination', temp], { stdio: 'pipe' })
  const archive = path.join(temp, 'undici-6.29.1.tgz')
  const integrity = crypto.createHash('sha512').update(fs.readFileSync(archive)).digest('base64')
  if (integrity !== 'EvRlUt/QqAEvXdQeRMYUfwOQsfzpDztykrG2owGEE/rBOBoYv9uF39ZzsNDh4lTFHhTr2XOZjKkYgueD12ZmGA==') throw new Error('Undici integrity mismatch')
  execFileSync(tar, ['-xzf', archive, '-C', temp])
  const extracted = path.join(temp, 'package')
  if (JSON.parse(fs.readFileSync(path.join(extracted, 'package.json'), 'utf8')).version !== '6.29.1') throw new Error('Unexpected package version')
  const installed = path.join(root, 'node_modules/undici')
  fs.rmSync(installed, { recursive: true, force: true })
  fs.cpSync(extracted, installed, { recursive: true })
  process.stdout.write('Verified compatible undici 6.29.1 maintenance installed\n')
} finally {
  fs.rmSync(temp, { recursive: true, force: true })
}
