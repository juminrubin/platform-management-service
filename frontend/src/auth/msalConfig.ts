import type {
  AccountInfo,
  AuthenticationResult,
  Configuration,
  EndSessionPopupRequest,
  PopupRequest,
  RedirectRequest,
  SilentRequest,
} from '@azure/msal-browser'
import { LogLevel, PublicClientApplication } from '@azure/msal-browser'

const tenantId = String(import.meta.env.APP_AZURE_TENANT_ID ?? '').trim()
const clientId = String(import.meta.env.APP_AZURE_CLIENT_ID ?? '').trim()
const apiScope = String(import.meta.env.APP_AZURE_API_SCOPE ?? '').trim()

function requireConfig(name: string, value: string): string {
  if (!value) {
    throw new Error(
      `Missing ${name}. Copy frontend/.env.example to frontend/.env and set Entra values. ` +
        `Vite must expose APP_* vars (see envPrefix in vite.config.ts).`,
    )
  }
  return value
}

const resolvedTenantId = requireConfig('APP_AZURE_TENANT_ID', tenantId)
const resolvedClientId = requireConfig('APP_AZURE_CLIENT_ID', clientId)
const resolvedApiScope = requireConfig('APP_AZURE_API_SCOPE', apiScope)

export const apiScopes = [resolvedApiScope]

/**
 * Static blank page used as redirectUri for popup / silent token flows.
 * Must be registered as an SPA redirect URI in Entra (in addition to the app origin).
 * Loading the full SPA in the popup is a common cause of “popup never closes / doesn’t return”.
 */
export const popupRedirectUri = `${window.location.origin}/auth-redirect.html`

/** Main app origin — used after logout and for full-page redirect login. */
export const appOrigin = window.location.origin

export const msalConfig: Configuration = {
  auth: {
    clientId: resolvedClientId,
    authority: `https://login.microsoftonline.com/${resolvedTenantId}`,
    // Default redirect for popup/silent. Must be the blank bridge page — never the SPA.
    redirectUri: popupRedirectUri,
    // Logout popup also lands on the blank page (MSAL opens postLogoutRedirectUri in the popup).
    postLogoutRedirectUri: popupRedirectUri,
  },
  cache: {
    cacheLocation: 'sessionStorage',
  },
  system: {
    loggerOptions: {
      logLevel: LogLevel.Warning,
      loggerCallback: (level, message, containsPii) => {
        if (containsPii) return
        if (level === LogLevel.Error) {
          console.error(message)
        }
      },
    },
    // Disable WAM / native broker; SPA uses browser popups only (msal-browser v5+ name).
    allowPlatformBroker: false,
    // Give the popup enough time to round-trip through Entra on slow networks
    popupBridgeTimeout: 120_000,
    iframeBridgeTimeout: 10_000,
  },
}

/** Interactive login (popup). Always pin redirectUri to the blank bridge page. */
export const loginRequest: PopupRequest = {
  scopes: apiScopes,
  redirectUri: popupRedirectUri,
  // Avoid reusing a stale interactive session in a leftover popup window.
  prompt: 'select_account',
}

/** Full-page redirect login (optional alternative to popup). */
export const loginRedirectRequest: RedirectRequest = {
  scopes: apiScopes,
  redirectUri: appOrigin,
  prompt: 'select_account',
}

/** Silent / popup token acquisition against the API. */
export const tokenRequest: PopupRequest & SilentRequest = {
  scopes: apiScopes,
  redirectUri: popupRedirectUri,
}

export const logoutPopupRequest = (account?: AccountInfo | null): EndSessionPopupRequest => {
  const req: EndSessionPopupRequest = {
    // postLogoutRedirectUri MUST be the blank page: MSAL opens it inside the popup.
    // Using the SPA origin leaves a full signed-out (or later signed-in) app inside the dialog.
    postLogoutRedirectUri: popupRedirectUri,
  }
  if (account) {
    req.account = account
    // Prefer promptless server logout when login_hint is available.
    const loginHint = (account.idTokenClaims as { login_hint?: string } | undefined)?.login_hint
    if (loginHint) {
      req.logoutHint = loginHint
    }
  }
  // Do not set mainWindowRedirectUri — it reloads the main tab mid-logout and
  // confuses users; local cache clear + React state is enough for SPA sign-out.
  return req
}

export const msalInstance = new PublicClientApplication(msalConfig)

/** Prefer the active account; fall back to the first cached account. */
export function getAccount(): AccountInfo | null {
  return msalInstance.getActiveAccount() ?? msalInstance.getAllAccounts()[0] ?? null
}

export function setActiveAccountFromResult(result: AuthenticationResult | null | undefined): void {
  if (result?.account) {
    msalInstance.setActiveAccount(result.account)
    return
  }
  const existing = msalInstance.getAllAccounts()
  if (existing.length > 0 && !msalInstance.getActiveAccount()) {
    msalInstance.setActiveAccount(existing[0])
  }
}

/**
 * Initialize MSAL and consume any full-page redirect response on the main app origin.
 * Stale redirect hashes are cleared so the SPA still boots.
 */
export async function initializeMsal(): Promise<void> {
  await msalInstance.initialize()

  // Popup completion is handled by the opener; only process redirect on the main window.
  if (window.opener || isAuthRedirectBlankPage()) {
    return
  }

  try {
    const result = await msalInstance.handleRedirectPromise()
    setActiveAccountFromResult(result)
    if (result) {
      // Clean URL if Entra left a hash/query on the app origin after loginRedirect.
      window.history.replaceState(null, document.title, window.location.pathname + window.location.search)
    }
  } catch (err) {
    console.warn('MSAL handleRedirectPromise failed; clearing auth hash and continuing', err)
    if (window.location.hash.includes('code=') || window.location.hash.includes('error=')) {
      window.history.replaceState(null, document.title, window.location.pathname + window.location.search)
    }
    setActiveAccountFromResult(null)
  }
}

function isAuthRedirectBlankPage(): boolean {
  return window.location.pathname.endsWith('/auth-redirect.html')
}

/**
 * Sign in via popup. Prefer this for SPA UX; popup uses the blank redirect page so
 * Entra can hand control back to the opener and close the dialog.
 */
export async function signInWithPopup(): Promise<AuthenticationResult> {
  const result = await msalInstance.loginPopup(loginRequest)
  setActiveAccountFromResult(result)
  try {
    window.focus()
  } catch {
    // ignore — some browsers block focus without user gesture timing
  }
  return result
}

export async function signOutWithPopup(): Promise<void> {
  const account = getAccount()
  try {
    await msalInstance.logoutPopup(logoutPopupRequest(account))
  } catch (err) {
    // Server logout is best-effort; always clear local session so the UI recovers.
    console.warn('logoutPopup failed; clearing local MSAL session', err)
    // Clear remaining accounts from the local cache when popup logout fails.
    const accounts = msalInstance.getAllAccounts()
    for (const a of accounts) {
      try {
        await msalInstance.clearCache({ account: a })
      } catch {
        /* ignore */
      }
    }
  } finally {
    msalInstance.setActiveAccount(null)
  }
  try {
    window.focus()
  } catch {
    /* ignore */
  }
}
