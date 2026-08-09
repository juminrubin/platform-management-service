/**
 * Frontend build stamp injected by Vite (`define` in vite.config.ts).
 * Override at build/CI with APP_BUILD_VERSION / APP_BUILD_TIMESTAMP env vars.
 */
export const buildInfo = {
  version: String(import.meta.env.APP_BUILD_VERSION ?? '0.0.0-dev'),
  timestamp: String(import.meta.env.APP_BUILD_TIMESTAMP ?? 'unknown'),
} as const
