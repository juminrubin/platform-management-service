import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import http from 'node:http'
import { afterEach, describe, expect, it } from 'vitest'
import { createServer, renderConfigJs, resolveListenPort } from '../server.mjs'

function listen(server: http.Server): Promise<number> {
  return new Promise((resolve, reject) => {
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address()
      if (typeof addr === 'object' && addr) {
        resolve(addr.port)
        return
      }
      reject(new Error('server did not bind a port'))
    })
  })
}

describe('production static server', () => {
  const servers: http.Server[] = []

  afterEach(async () => {
    await Promise.all(
      servers.splice(0).map(
        (server) =>
          new Promise<void>((resolve, reject) => {
            server.close((err) => (err ? reject(err) : resolve()))
          }),
      ),
    )
  })

  it('listens on PORT / 3000 and never on APP_API_BASE_URL', () => {
    expect(resolveListenPort({})).toBe(3000)
    expect(resolveListenPort({ APP_API_BASE_URL: 'http://backend.example:8080' })).toBe(3000)
    expect(resolveListenPort({ PORT: '3000', APP_API_BASE_URL: 'https://api.example.com:8080' })).toBe(3000)
    expect(resolveListenPort({ PORT: '8080' })).toBe(8080)
    expect(resolveListenPort({ WEBSITES_PORT: '8080' })).toBe(8080)
  })

  it('serializes APP_* system environment variables into config.js', () => {
    const js = renderConfigJs({
      APP_AZURE_TENANT_ID: 'tenant',
      APP_CLIENT_ID: 'spa',
      APP_API_SCOPE: 'api://x/access_as_user',
      APP_API_BASE_URL: 'https://api.example.com',
    })
    expect(js).toBe(
      'window.__APP_CONFIG__={"APP_AZURE_TENANT_ID":"tenant","APP_CLIENT_ID":"spa","APP_API_SCOPE":"api://x/access_as_user","APP_API_BASE_URL":"https://api.example.com"};',
    )
  })

  it('escapes values so they cannot break out of the script tag', () => {
    const js = renderConfigJs({ APP_CLIENT_ID: 'a"</script>' })
    expect(js).toContain('\\"')
    expect(js).not.toContain('</script>')
    expect(js).toContain('\\u003c/script>')
  })

  it('serves healthz, runtime config, static files, and SPA fallback', async () => {
    const root = mkdtempSync(join(tmpdir(), 'pms-ui-'))
    writeFileSync(join(root, 'index.html'), '<html>spa</html>')
    writeFileSync(join(root, 'favicon.svg'), '<svg></svg>')
    writeFileSync(join(root, 'server.mjs'), 'should-not-be-served')

    const server = createServer({
      staticRoot: root,
      env: { APP_CLIENT_ID: 'runtime-spa' },
    })
    servers.push(server)
    const port = await listen(server)
    const base = `http://127.0.0.1:${port}`

    const health = await fetch(`${base}/healthz`)
    expect(health.status).toBe(200)
    expect(await health.text()).toBe('ok')

    const config = await fetch(`${base}/config.js`)
    expect(config.status).toBe(200)
    expect(config.headers.get('cache-control')).toBe('no-store')
    expect(await config.text()).toContain('"APP_CLIENT_ID":"runtime-spa"')

    const asset = await fetch(`${base}/favicon.svg`)
    expect(asset.status).toBe(200)
    expect(await asset.text()).toBe('<svg></svg>')

    const spa = await fetch(`${base}/participants/acme`)
    expect(spa.status).toBe(200)
    expect(await spa.text()).toBe('<html>spa</html>')

    const hidden = await fetch(`${base}/server.mjs`)
    expect(hidden.status).toBe(404)
  })

  it('keeps the browser on the UI origin and proxies /api to APP_API_BASE_URL', async () => {
    const upstream = http.createServer((req, res) => {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ path: req.url, host: req.headers.host }))
    })
    servers.push(upstream)
    const upPort = await listen(upstream)

    const root = mkdtempSync(join(tmpdir(), 'pms-ui-'))
    writeFileSync(join(root, 'index.html'), '<html>spa</html>')
    const server = createServer({
      staticRoot: root,
      env: {
        APP_CLIENT_ID: 'spa',
        APP_API_BASE_URL: `http://127.0.0.1:${upPort}`,
      },
    })
    servers.push(server)
    const port = await listen(server)
    const base = `http://127.0.0.1:${port}`

    const config = await fetch(`${base}/config.js`)
    expect(await config.text()).toContain('"APP_API_BASE_URL":""')

    const api = await fetch(`${base}/api/v1/auth/me`)
    expect(api.status).toBe(200)
    expect(await api.json()).toEqual({
      path: '/api/v1/auth/me',
      host: `127.0.0.1:${upPort}`,
    })

    const spa = await fetch(`${base}/participants`)
    expect(await spa.text()).toBe('<html>spa</html>')
  })
})
