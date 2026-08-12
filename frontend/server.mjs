import http from 'node:http'
import https from 'node:https'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export const CONFIG_KEYS = [
  'APP_AZURE_TENANT_ID',
  'APP_CLIENT_ID',
  'APP_API_SCOPE',
  'APP_API_BASE_URL',
]

const MIME = {
  '.css': 'text/css; charset=utf-8',
  '.gif': 'image/gif',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

const HIDDEN_FILES = new Set(['.deployment', 'package.json', 'server.mjs', 'web.config'])
const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailers',
  'transfer-encoding',
  'upgrade',
])

function resolveStaticRoot() {
  const distDir = path.join(__dirname, 'dist')
  if (fs.existsSync(path.join(distDir, 'index.html'))) {
    return distDir
  }
  return __dirname
}

/** UI listen port. Never derived from APP_API_BASE_URL (that is the API upstream only). */
export function resolveListenPort(env = process.env) {
  const raw = env.PORT || env.WEBSITES_PORT
  if (raw != null && String(raw).trim() !== '') {
    const port = Number(raw)
    if (Number.isFinite(port) && port > 0) {
      return port
    }
  }
  return 3000
}

export function renderConfigJs(env) {
  const cfg = {}
  for (const key of CONFIG_KEYS) {
    cfg[key] = env[key] ?? ''
  }
  return `window.__APP_CONFIG__=${JSON.stringify(cfg).replace(/</g, '\\u003c')};`
}

function isInsideRoot(root, candidate) {
  const resolvedRoot = path.resolve(root)
  const resolved = path.resolve(candidate)
  return resolved === resolvedRoot || resolved.startsWith(resolvedRoot + path.sep)
}

function isApiPath(urlPath) {
  return urlPath === '/api' || urlPath.startsWith('/api/') || urlPath === '/actuator' || urlPath.startsWith('/actuator/')
}

function copyHeaders(source, extra = {}) {
  const headers = {}
  for (const [name, value] of Object.entries(source)) {
    if (value == null || HOP_BY_HOP.has(name.toLowerCase())) {
      continue
    }
    headers[name] = value
  }
  return { ...headers, ...extra }
}

function proxyApi(req, res, apiBaseUrl) {
  let target
  try {
    target = new URL(req.url || '/', apiBaseUrl.endsWith('/') ? apiBaseUrl : `${apiBaseUrl}/`)
  } catch {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Invalid APP_API_BASE_URL')
    return
  }

  const lib = target.protocol === 'https:' ? https : http
  const preq = lib.request(
    {
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port || (target.protocol === 'https:' ? 443 : 80),
      path: `${target.pathname}${target.search}`,
      method: req.method,
      headers: copyHeaders(req.headers, { host: target.host }),
    },
    (pres) => {
      res.writeHead(pres.statusCode || 502, copyHeaders(pres.headers))
      pres.pipe(res)
    },
  )
  preq.on('error', (err) => {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    }
    res.end(`API proxy error: ${err.message}`)
  })
  req.pipe(preq)
}

export function createServer(options = {}) {
  const staticRoot = path.resolve(options.staticRoot || resolveStaticRoot())
  const env = options.env || process.env
  const apiBaseUrl = String(env.APP_API_BASE_URL ?? '').trim()

  return http.createServer((req, res) => {
    const urlPath = decodeURIComponent((req.url || '/').split('?')[0] || '/')

    if (urlPath === '/healthz') {
      res.writeHead(200, { 'Content-Type': 'text/plain', 'Cache-Control': 'no-store' })
      res.end('ok')
      return
    }

    if (urlPath === '/config.js') {
      // Browser must call this UI origin. APP_API_BASE_URL is the server-side proxy target only.
      res.writeHead(200, {
        'Content-Type': 'application/javascript; charset=utf-8',
        'Cache-Control': 'no-store',
      })
      res.end(renderConfigJs({ ...env, APP_API_BASE_URL: '' }))
      return
    }

    if (isApiPath(urlPath)) {
      if (!apiBaseUrl) {
        res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
        res.end('APP_API_BASE_URL is not set')
        return
      }
      proxyApi(req, res, apiBaseUrl)
      return
    }

    const relative = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '')
    const requested = path.resolve(staticRoot, relative)
    if (!isInsideRoot(staticRoot, requested) || HIDDEN_FILES.has(path.basename(requested))) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('Not found')
      return
    }

    const sendFile = (filePath, fallbackToIndex) => {
      fs.stat(filePath, (statErr, stat) => {
        if (statErr || !stat.isFile()) {
          if (fallbackToIndex) {
            sendFile(path.join(staticRoot, 'index.html'), false)
            return
          }
          res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
          res.end('Not found')
          return
        }
        fs.readFile(filePath, (readErr, data) => {
          if (readErr) {
            res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' })
            res.end('Internal server error')
            return
          }
          const ext = path.extname(filePath).toLowerCase()
          const headers = { 'Content-Type': MIME[ext] || 'application/octet-stream' }
          if (path.basename(filePath) === 'index.html') {
            headers['Cache-Control'] = 'no-cache'
          }
          res.writeHead(200, headers)
          res.end(data)
        })
      })
    }

    sendFile(requested, true)
  })
}

function main() {
  const port = resolveListenPort()
  const host = process.env.HOST || '0.0.0.0'
  const server = createServer()
  server.listen(port, host, () => {
    const api = String(process.env.APP_API_BASE_URL ?? '').trim()
    const proxyNote = api ? ` (proxy /api → ${api})` : ''
    console.log(`UI listening on http://${host}:${port}${proxyNote}`)
  })
}

const invokedPath = process.argv[1] && path.resolve(process.argv[1])
if (invokedPath && fileURLToPath(import.meta.url) === invokedPath) {
  main()
}
