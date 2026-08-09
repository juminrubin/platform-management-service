/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly APP_AZURE_TENANT_ID?: string
  readonly APP_AZURE_CLIENT_ID?: string
  readonly APP_AZURE_API_SCOPE?: string
  readonly APP_API_BASE_URL?: string
  /** package.json version (or APP_BUILD_VERSION) stamped at Vite build time */
  readonly APP_BUILD_VERSION?: string
  /** ISO-8601 build timestamp stamped at Vite build time */
  readonly APP_BUILD_TIMESTAMP?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
