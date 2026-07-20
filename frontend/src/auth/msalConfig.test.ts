import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AccountInfo, AuthenticationResult } from '@azure/msal-browser'
import { LogLevel } from '@azure/msal-browser'
import {
  apiScopes,
  getAccount,
  initializeMsal,
  loginRequest,
  logoutPopupRequest,
  msalConfig,
  msalInstance,
  popupRedirectUri,
  setActiveAccountFromResult,
  signInWithPopup,
  signOutWithPopup,
} from './msalConfig'

const account = {
  username: 'alice@contoso.com',
  homeAccountId: 'a.b',
  localAccountId: 'local',
  environment: 'login.windows.net',
  tenantId: 'tenant',
  idTokenClaims: { login_hint: 'alice@contoso.com' },
} as AccountInfo

describe('msalConfig', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('uses blank redirect page for popup flows', () => {
    expect(popupRedirectUri).toMatch(/\/auth-redirect\.html$/)
    expect(msalConfig.auth.redirectUri).toBe(popupRedirectUri)
    expect(msalConfig.auth.postLogoutRedirectUri).toBe(popupRedirectUri)
    expect(loginRequest.redirectUri).toBe(popupRedirectUri)
  })

  it('configures API scopes from env', () => {
    expect(apiScopes.length).toBe(1)
    expect(apiScopes[0]).toContain('access_as_user')
  })

  it('uses sessionStorage and disables platform broker', () => {
    expect(msalConfig.cache?.cacheLocation).toBe('sessionStorage')
    expect(msalConfig.system?.allowPlatformBroker).toBe(false)
  })

  it('logs errors via loggerCallback and skips PII', () => {
    const cb = msalConfig.system?.loggerOptions?.loggerCallback
    expect(cb).toBeTypeOf('function')
    const err = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    cb?.(LogLevel.Error, 'boom', false)
    cb?.(LogLevel.Error, 'secret', true)
    cb?.(LogLevel.Info, 'info', false)
    expect(err).toHaveBeenCalledWith('boom')
    expect(err).toHaveBeenCalledTimes(1)
  })

  it('logoutPopupRequest includes account and login_hint when present', () => {
    const req = logoutPopupRequest(account)
    expect(req.postLogoutRedirectUri).toBe(popupRedirectUri)
    expect(req.account).toBe(account)
    expect(req.logoutHint).toBe('alice@contoso.com')
  })

  it('logoutPopupRequest works without account', () => {
    const req = logoutPopupRequest(null)
    expect(req.account).toBeUndefined()
    expect(req.logoutHint).toBeUndefined()
  })

  it('getAccount prefers active then first cached account', () => {
    vi.spyOn(msalInstance, 'getActiveAccount').mockReturnValue(null)
    vi.spyOn(msalInstance, 'getAllAccounts').mockReturnValue([account])
    expect(getAccount()).toBe(account)

    vi.spyOn(msalInstance, 'getActiveAccount').mockReturnValue(account)
    expect(getAccount()).toBe(account)

    vi.spyOn(msalInstance, 'getActiveAccount').mockReturnValue(null)
    vi.spyOn(msalInstance, 'getAllAccounts').mockReturnValue([])
    expect(getAccount()).toBeNull()
  })

  it('setActiveAccountFromResult uses result account or first cached', () => {
    const setActive = vi.spyOn(msalInstance, 'setActiveAccount').mockImplementation(() => undefined)
    setActiveAccountFromResult({ account } as AuthenticationResult)
    expect(setActive).toHaveBeenCalledWith(account)

    setActive.mockClear()
    vi.spyOn(msalInstance, 'getAllAccounts').mockReturnValue([account])
    vi.spyOn(msalInstance, 'getActiveAccount').mockReturnValue(null)
    setActiveAccountFromResult(null)
    expect(setActive).toHaveBeenCalledWith(account)
  })

  it('initializeMsal handles redirect on main window', async () => {
    vi.spyOn(msalInstance, 'initialize').mockResolvedValue(undefined)
    vi.spyOn(msalInstance, 'handleRedirectPromise').mockResolvedValue({
      account,
    } as AuthenticationResult)
    const replace = vi.spyOn(window.history, 'replaceState').mockImplementation(() => undefined)
    const setActive = vi.spyOn(msalInstance, 'setActiveAccount').mockImplementation(() => undefined)

    await initializeMsal()
    expect(msalInstance.initialize).toHaveBeenCalled()
    expect(setActive).toHaveBeenCalledWith(account)
    expect(replace).toHaveBeenCalled()
  })

  it('initializeMsal skips redirect handling in popup or blank page', async () => {
    vi.spyOn(msalInstance, 'initialize').mockResolvedValue(undefined)
    const handle = vi.spyOn(msalInstance, 'handleRedirectPromise')
    Object.defineProperty(window, 'opener', { value: {}, configurable: true })
    await initializeMsal()
    expect(handle).not.toHaveBeenCalled()
    Object.defineProperty(window, 'opener', { value: null, configurable: true })
  })

  it('initializeMsal clears hash after redirect failure', async () => {
    vi.spyOn(msalInstance, 'initialize').mockResolvedValue(undefined)
    vi.spyOn(msalInstance, 'handleRedirectPromise').mockRejectedValue(new Error('bad hash'))
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const replace = vi.spyOn(window.history, 'replaceState').mockImplementation(() => undefined)
    window.location.hash = '#error=access_denied'
    await initializeMsal()
    expect(replace).toHaveBeenCalled()
    window.location.hash = ''
  })

  it('signInWithPopup sets active account', async () => {
    vi.spyOn(msalInstance, 'loginPopup').mockResolvedValue({ account } as AuthenticationResult)
    const setActive = vi.spyOn(msalInstance, 'setActiveAccount').mockImplementation(() => undefined)
    const result = await signInWithPopup()
    expect(result.account).toBe(account)
    expect(setActive).toHaveBeenCalledWith(account)
  })

  it('signOutWithPopup clears active account on success', async () => {
    vi.spyOn(msalInstance, 'getActiveAccount').mockReturnValue(account)
    vi.spyOn(msalInstance, 'getAllAccounts').mockReturnValue([account])
    vi.spyOn(msalInstance, 'logoutPopup').mockResolvedValue(undefined)
    const setActive = vi.spyOn(msalInstance, 'setActiveAccount').mockImplementation(() => undefined)
    await signOutWithPopup()
    expect(msalInstance.logoutPopup).toHaveBeenCalled()
    expect(setActive).toHaveBeenCalledWith(null)
  })

  it('signOutWithPopup clears cache when logoutPopup fails', async () => {
    vi.spyOn(msalInstance, 'getActiveAccount').mockReturnValue(account)
    vi.spyOn(msalInstance, 'getAllAccounts').mockReturnValue([account])
    vi.spyOn(msalInstance, 'logoutPopup').mockRejectedValue(new Error('popup closed'))
    vi.spyOn(msalInstance, 'clearCache').mockResolvedValue(undefined)
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const setActive = vi.spyOn(msalInstance, 'setActiveAccount').mockImplementation(() => undefined)
    await signOutWithPopup()
    expect(msalInstance.clearCache).toHaveBeenCalled()
    expect(setActive).toHaveBeenCalledWith(null)
  })
})
