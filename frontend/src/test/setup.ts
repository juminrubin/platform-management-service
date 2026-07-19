import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

// Defaults for modules that read import.meta.env.APP_* at load time
vi.stubEnv('APP_AZURE_TENANT_ID', 'test-tenant')
vi.stubEnv('APP_AZURE_CLIENT_ID', 'test-spa-client')
vi.stubEnv('APP_AZURE_API_SCOPE', 'api://test-api/access_as_user')
vi.stubEnv('APP_API_BASE_URL', '')

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})
