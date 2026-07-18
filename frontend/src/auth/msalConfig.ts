import type { Configuration } from '@azure/msal-browser'
import { LogLevel, PublicClientApplication } from '@azure/msal-browser'

const tenantId = import.meta.env.VITE_AZURE_TENANT_ID as string
const clientId = import.meta.env.VITE_AZURE_CLIENT_ID as string
const apiScope = import.meta.env.VITE_AZURE_API_SCOPE as string

export const apiScopes = [apiScope].filter(Boolean)

export const msalConfig: Configuration = {
  auth: {
    clientId,
    authority: `https://login.microsoftonline.com/${tenantId}`,
    redirectUri: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
  },
  cache: {
    cacheLocation: 'sessionStorage',
  },
  system: {
    loggerOptions: {
      logLevel: LogLevel.Warning,
    },
  },
}

export const loginRequest = {
  scopes: apiScopes,
}

export const msalInstance = new PublicClientApplication(msalConfig)
