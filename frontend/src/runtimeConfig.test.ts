import { afterEach, describe, expect, it } from 'vitest'
import { getRuntimeConfig } from './runtimeConfig'

describe('getRuntimeConfig', () => {
  const previous = window.__APP_CONFIG__

  afterEach(() => {
    window.__APP_CONFIG__ = previous
  })

  it('returns trimmed values from window.__APP_CONFIG__', () => {
    window.__APP_CONFIG__ = {
      APP_AZURE_TENANT_ID: '  tenant-1  ',
      APP_CLIENT_ID: 'spa-client',
      APP_API_SCOPE: 'api://api/access_as_user',
      APP_API_BASE_URL: 'https://api.example.com',
    }

    expect(getRuntimeConfig()).toEqual({
      APP_AZURE_TENANT_ID: 'tenant-1',
      APP_CLIENT_ID: 'spa-client',
      APP_API_SCOPE: 'api://api/access_as_user',
      APP_API_BASE_URL: 'https://api.example.com',
    })
  })

  it('defaults missing or empty keys to empty strings', () => {
    window.__APP_CONFIG__ = { APP_AZURE_TENANT_ID: '' }
    expect(getRuntimeConfig()).toEqual({
      APP_AZURE_TENANT_ID: '',
      APP_CLIENT_ID: '',
      APP_API_SCOPE: '',
      APP_API_BASE_URL: '',
    })
  })
})
