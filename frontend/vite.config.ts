/// <reference types="vitest/config" />
import { copyFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

const rootDir = dirname(fileURLToPath(import.meta.url))

const packageJson = JSON.parse(readFileSync(resolve(rootDir, 'package.json'), 'utf-8')) as {
  version: string
}
/** Stamp version (package.json) and ISO-8601 build time into the SPA bundle. */
const buildVersion = process.env.APP_BUILD_VERSION || packageJson.version
const buildTimestamp = process.env.APP_BUILD_TIMESTAMP || new Date().toISOString()

/** Keep public/msal-redirect-bridge.min.js in sync with the installed @azure/msal-browser package. */
function copyMsalRedirectBridge(): Plugin {
  const copy = () => {
    const src = resolve(
      rootDir,
      'node_modules/@azure/msal-browser/lib/redirect-bridge/msal-redirect-bridge.min.js',
    )
    const dest = resolve(rootDir, 'public/msal-redirect-bridge.min.js')
    if (!existsSync(src)) {
      console.warn('[vite] msal-redirect-bridge source not found; run npm install')
      return
    }
    mkdirSync(dirname(dest), { recursive: true })
    copyFileSync(src, dest)
  }
  return {
    name: 'copy-msal-redirect-bridge',
    buildStart: copy,
    configureServer: copy,
  }
}

const RUNTIME_CONFIG_KEYS = [
  'APP_AZURE_TENANT_ID',
  'APP_CLIENT_ID',
  'APP_API_SCOPE',
  'APP_API_BASE_URL',
] as const

function renderConfigJs(env: NodeJS.ProcessEnv): string {
  const cfg: Record<string, string> = {}
  for (const key of RUNTIME_CONFIG_KEYS) {
    cfg[key] = env[key] ?? ''
  }
  return `window.__APP_CONFIG__=${JSON.stringify(cfg).replace(/</g, '\\u003c')};`
}

/** Serve /config.js from process env in dev/preview; emit a portable Azure zip in production. */
function runtimeConfigAndDeploy(): Plugin {
  const serveConfig = (
    req: { url?: string },
    res: { setHeader: (name: string, value: string) => void; end: (body: string) => void },
    next: () => void,
  ) => {
    const pathOnly = req.url?.split('?')[0]
    if (pathOnly !== '/config.js') {
      next()
      return
    }
    res.setHeader('Content-Type', 'application/javascript; charset=utf-8')
    res.setHeader('Cache-Control', 'no-store')
    res.end(renderConfigJs(process.env))
  }

  return {
    name: 'runtime-config-and-deploy',
    configureServer(server) {
      server.middlewares.use(serveConfig)
    },
    configurePreviewServer(server) {
      server.middlewares.use(serveConfig)
    },
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'config.js',
        source: 'window.__APP_CONFIG__=window.__APP_CONFIG__||{};\n',
      })
      this.emitFile({
        type: 'asset',
        fileName: 'server.mjs',
        source: readFileSync(resolve(rootDir, 'server.mjs'), 'utf-8'),
      })
      this.emitFile({
        type: 'asset',
        fileName: 'package.json',
        source:
          JSON.stringify(
            {
              name: 'platform-management-service-ui',
              private: true,
              version: packageJson.version,
              license: 'Apache-2.0',
              type: 'module',
              scripts: { start: 'node server.mjs' },
              engines: { node: '>=20' },
            },
            null,
            2,
          ) + '\n',
      })
      this.emitFile({
        type: 'asset',
        fileName: '.deployment',
        source: '[config]\nSCM_DO_BUILD_DURING_DEPLOYMENT=false\n',
      })
      this.emitFile({
        type: 'asset',
        fileName: 'web.config',
        source: `<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <system.webServer>
    <handlers>
      <add name="httpPlatformHandler" path="*" verb="*" modules="httpPlatformHandler" resourceType="Unspecified" />
    </handlers>
    <httpPlatform processPath="node" arguments="server.mjs" stdoutLogEnabled="false" startupTimeLimit="60">
      <environmentVariables>
        <environmentVariable name="PORT" value="%HTTP_PLATFORM_PORT%" />
      </environmentVariables>
    </httpPlatform>
  </system.webServer>
</configuration>
`,
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const fileEnv = loadEnv(mode, rootDir, ['APP_', 'VITE_'])
  for (const [key, value] of Object.entries(fileEnv)) {
    if (process.env[key] === undefined) {
      process.env[key] = value
    }
  }

  return {
    plugins: [react(), copyMsalRedirectBridge(), runtimeConfigAndDeploy()],
    // Expose APP_BUILD_* from .env to import.meta.env (runtime APP_* is served via /config.js)
    envPrefix: ['APP_', 'VITE_'],
    define: {
      'import.meta.env.APP_BUILD_VERSION': JSON.stringify(buildVersion),
      'import.meta.env.APP_BUILD_TIMESTAMP': JSON.stringify(buildTimestamp),
    },
    server: {
      port: 3000,
      strictPort: true,
      proxy: {
        '/api': {
          target: process.env.APP_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
        '/actuator': {
          target: process.env.APP_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: true,
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'text-summary', 'html', 'lcov'],
        // App source only — entrypoints / pure types / test helpers excluded
        include: ['src/**/*.{ts,tsx}'],
        exclude: [
          'src/**/*.{test,spec}.{ts,tsx}',
          'src/test/**',
          'src/main.tsx',
          'src/App.tsx',
          'src/api/types.ts',
          'src/**/*.d.ts',
        ],
        // Industry-common gate (aligns with backend JaCoCo ≥80% lines)
        thresholds: {
          lines: 80,
          statements: 80,
          functions: 80,
          branches: 75,
        },
      },
    },
  }
})
