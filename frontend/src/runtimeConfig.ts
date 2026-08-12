export const RUNTIME_CONFIG_KEYS = [
  'APP_AZURE_TENANT_ID',
  'APP_CLIENT_ID',
  'APP_API_SCOPE',
  'APP_API_BASE_URL',
] as const

export type RuntimeConfigKey = (typeof RUNTIME_CONFIG_KEYS)[number]

export type RuntimeConfig = Record<RuntimeConfigKey, string>

function readKey(raw: Partial<RuntimeConfig> | undefined, key: RuntimeConfigKey): string {
  const value = raw?.[key]
  return value == null ? '' : String(value).trim()
}

/** Public SPA settings injected at process start (see `/config.js`). */
export function getRuntimeConfig(): RuntimeConfig {
  const raw = typeof window !== 'undefined' ? window.__APP_CONFIG__ : undefined
  return {
    APP_AZURE_TENANT_ID: readKey(raw, 'APP_AZURE_TENANT_ID'),
    APP_CLIENT_ID: readKey(raw, 'APP_CLIENT_ID'),
    APP_API_SCOPE: readKey(raw, 'APP_API_SCOPE'),
    APP_API_BASE_URL: readKey(raw, 'APP_API_BASE_URL'),
  }
}
