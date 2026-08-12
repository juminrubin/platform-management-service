/// <reference types="vite/client" />

import type { RuntimeConfig } from './runtimeConfig'

declare global {
  interface ImportMetaEnv {
    /** package.json version (or APP_BUILD_VERSION) stamped at Vite build time */
    readonly APP_BUILD_VERSION?: string
    /** ISO-8601 build timestamp stamped at Vite build time */
    readonly APP_BUILD_TIMESTAMP?: string
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv
  }

  interface Window {
    /** Injected by /config.js from process environment at server start. */
    __APP_CONFIG__?: Partial<RuntimeConfig>
  }
}

export {}
