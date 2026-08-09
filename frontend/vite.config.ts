/// <reference types="vitest/config" />
import { copyFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, type Plugin } from 'vite'
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

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), copyMsalRedirectBridge()],
  // Expose APP_* from .env to import.meta.env (default Vite prefix is only VITE_)
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
})
