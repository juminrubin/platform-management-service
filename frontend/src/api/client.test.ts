import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BrowserAuthError, InteractionRequiredAuthError } from '@azure/msal-browser'
import {
  apiFetch,
  createCallerRegistration,
  createConsumption,
  createEntitlement,
  createParticipant,
  createServiceOffering,
  deleteCallerRegistration,
  deleteConsumption,
  deleteEntitlement,
  deleteParticipant,
  deleteServiceOffering,
  getCallerRegistration,
  getConnector,
  getConnectorConfig,
  getConsumption,
  getEntitlement,
  getMe,
  getParticipant,
  getServiceOffering,
  listCallerRegistrations,
  listConnectors,
  listConsumptions,
  listEntitlements,
  listParticipants,
  listServiceOfferings,
  startConnector,
  stopConnector,
  updateCallerRegistration,
  updateConnectorConfig,
  updateEntitlement,
  updateParticipant,
  updateServiceOffering,
} from './client'

const acquireTokenSilent = vi.fn()
const acquireTokenPopup = vi.fn()
const getAllAccounts = vi.fn()
const getActiveAccount = vi.fn()
const setActiveAccount = vi.fn()
const account = { username: 'alice@contoso.com', homeAccountId: 'a.b' }

vi.mock('../auth/msalConfig', () => ({
  msalInstance: {
    getAllAccounts: (...args: unknown[]) => getAllAccounts(...args),
    getActiveAccount: (...args: unknown[]) => getActiveAccount(...args),
    setActiveAccount: (...args: unknown[]) => setActiveAccount(...args),
    acquireTokenSilent: (...args: unknown[]) => acquireTokenSilent(...args),
    acquireTokenPopup: (...args: unknown[]) => acquireTokenPopup(...args),
  },
  apiScopes: ['api://test/access_as_user'],
  tokenRequest: { scopes: ['api://test/access_as_user'] },
  getAccount: () => getActiveAccount() ?? getAllAccounts()[0] ?? null,
  setActiveAccountFromResult: vi.fn((result?: { account?: unknown } | null) => {
    if (result?.account) setActiveAccount(result.account)
  }),
}))

/** Real Fetch Response so `tsc` and runtime both accept the mock. */
function mockJsonResponse(body: unknown, init: { status?: number; statusText?: string } = {}): Response {
  const status = init.status ?? 200
  const statusText = init.statusText ?? (status === 204 ? 'No Content' : 'OK')
  if (status === 204) {
    return new Response(null, { status, statusText })
  }
  const payload = typeof body === 'string' ? body : JSON.stringify(body ?? null)
  return new Response(payload, {
    status,
    statusText,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Always return a fresh Response (body streams are single-use). */
function stubFetchJson(body: unknown, init: { status?: number; statusText?: string } = {}) {
  vi.mocked(fetch).mockImplementation(async () => mockJsonResponse(body, init))
}

describe('api client', () => {
  beforeEach(() => {
    getAllAccounts.mockReturnValue([account])
    getActiveAccount.mockReturnValue(account)
    acquireTokenSilent.mockResolvedValue({ accessToken: 'silent-token', account })
    acquireTokenPopup.mockResolvedValue({ accessToken: 'popup-token', account })
    vi.stubGlobal('fetch', vi.fn())
  })

  describe('apiFetch / auth', () => {
    it('throws when no MSAL accounts are present', async () => {
      getActiveAccount.mockReturnValue(null)
      getAllAccounts.mockReturnValue([])
      await expect(apiFetch('/api/v1/auth/me')).rejects.toThrow('Not signed in')
      expect(fetch).not.toHaveBeenCalled()
    })

    it('attaches bearer token and parses JSON success', async () => {
      stubFetchJson({ subject: 'u1' })
      await expect(getMe()).resolves.toEqual({ subject: 'u1' })
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/auth/me',
        expect.objectContaining({
          headers: expect.any(Headers),
        }),
      )
      const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit
      expect((init.headers as Headers).get('Authorization')).toBe('Bearer silent-token')
    })

    it('falls back to popup when silent acquisition requires interaction', async () => {
      acquireTokenSilent.mockRejectedValue(new InteractionRequiredAuthError('interaction_required', 'need login'))
      stubFetchJson([])
      await listParticipants()
      expect(acquireTokenPopup).toHaveBeenCalled()
      const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit
      expect((init.headers as Headers).get('Authorization')).toBe('Bearer popup-token')
    })

    it('re-throws non-interaction silent token errors', async () => {
      acquireTokenSilent.mockRejectedValue(new Error('network down'))
      await expect(getMe()).rejects.toThrow('network down')
    })

    it('treats BrowserAuthError cache codes as interaction-required', async () => {
      acquireTokenSilent.mockRejectedValue(new BrowserAuthError('no_token_request_cache_error', 'cache'))
      stubFetchJson({ ok: true })
      await getMe()
      expect(acquireTokenPopup).toHaveBeenCalled()
    })

    it('fails fast when another interactive login is already in progress', async () => {
      acquireTokenSilent.mockRejectedValue(new BrowserAuthError('interaction_in_progress', 'busy'))
      await expect(getMe()).rejects.toThrow(/Sign-in already in progress/)
      expect(acquireTokenPopup).not.toHaveBeenCalled()
    })

    it('sets Content-Type for JSON bodies and throws on HTTP error', async () => {
      stubFetchJson('bad request', { status: 400, statusText: 'Bad Request' })
      await expect(createParticipant({ id: 'x', name: 'X' })).rejects.toThrow('400 Bad Request: bad request')
      const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit
      expect((init.headers as Headers).get('Content-Type')).toBe('application/json')
      expect(init.method).toBe('POST')
    })

    it('returns undefined for 204 No Content', async () => {
      stubFetchJson(undefined, { status: 204 })
      await expect(deleteParticipant('acme-corp')).resolves.toBeUndefined()
    })
  })

  describe('resource helpers and query strings', () => {
    beforeEach(() => {
      stubFetchJson([])
    })

    it('lists participants with optional status', async () => {
      await listParticipants()
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/participants', expect.anything())
      await listParticipants('ACTIVE')
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/participants?status=ACTIVE', expect.anything())
    })

    it('gets / updates / deletes a participant', async () => {
      stubFetchJson({ id: 'acme-corp' })
      await getParticipant('acme/corp')
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/participants/acme%2Fcorp', expect.anything())

      await updateParticipant('acme-corp', { name: 'Acme', status: 'ACTIVE' })
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/participants/acme-corp',
        expect.objectContaining({ method: 'PUT' }),
      )

      stubFetchJson(undefined, { status: 204 })
      await deleteParticipant('acme-corp')
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/participants/acme-corp',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })

    it('lists caller registrations with filters and supports CRUD', async () => {
      await listCallerRegistrations({ participantId: 'acme-corp', status: 'ACTIVE' })
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/caller-registrations?participantId=acme-corp&status=ACTIVE',
        expect.anything(),
      )
      await listCallerRegistrations()
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/caller-registrations', expect.anything())

      stubFetchJson({ callerId: 'a@b.com' })
      await getCallerRegistration('a@b.com')
      await createCallerRegistration({ participantId: 'acme-corp', callerId: 'a@b.com' })
      await updateCallerRegistration('a@b.com', { status: 'INACTIVE' })
      stubFetchJson(undefined, { status: 204 })
      await deleteCallerRegistration('a@b.com')
      expect(vi.mocked(fetch).mock.calls.map((c) => [c[0], (c[1] as RequestInit)?.method])).toEqual(
        expect.arrayContaining([
          ['/api/v1/caller-registrations/a%40b.com', undefined],
          ['/api/v1/caller-registrations', 'POST'],
          ['/api/v1/caller-registrations/a%40b.com', 'PUT'],
          ['/api/v1/caller-registrations/a%40b.com', 'DELETE'],
        ]),
      )
    })

    it('lists service offerings with filters and supports CRUD', async () => {
      await listServiceOfferings({ activeOnly: true, category: 'LLM' })
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/service-offerings?activeOnly=true&category=LLM',
        expect.anything(),
      )
      await listServiceOfferings({})
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/service-offerings', expect.anything())

      stubFetchJson({ id: 'gpt-5.1' })
      await getServiceOffering('gpt-5.1')
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/service-offerings/gpt-5.1', expect.anything())
      await getServiceOffering('Group1/Service1a')
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/service-offerings/Group1%2FService1a',
        expect.anything(),
      )
      await createServiceOffering({ id: 'm', name: 'M', category: 'LLM' })
      await updateServiceOffering('m', {
        name: 'M',
        category: 'LLM',
        provider: 'SYSTEM',
        config: '{}',
        active: true,
      })
      stubFetchJson(undefined, { status: 204 })
      await deleteServiceOffering('m')
    })

    it('lists entitlements with filters and supports CRUD', async () => {
      await listEntitlements({
        participantId: 'acme-corp',
        serviceOfferingId: 'gpt-5.1',
        status: 'ACTIVE',
      })
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/entitlements?participantId=acme-corp&serviceOfferingId=gpt-5.1&status=ACTIVE',
        expect.anything(),
      )

      stubFetchJson({ id: 'e1' })
      await getEntitlement('e1')
      await createEntitlement({
        participantId: 'acme-corp',
        serviceOfferingId: 'gpt-5.1',
        validFrom: '2024-01-01',
      })
      await updateEntitlement('e1', {
        status: 'ACTIVE',
        validFrom: '2024-01-01',
        config: '{}',
      })
      stubFetchJson(undefined, { status: 204 })
      await deleteEntitlement('e1')
    })

    it('lists consumptions with filters and supports create/get/delete', async () => {
      await listConsumptions({
        callerId: 'alice@acme.example',
        serviceOfferingId: 'gpt-5.1',
      })
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/consumptions?callerId=alice%40acme.example&serviceOfferingId=gpt-5.1',
        expect.anything(),
      )
      await listConsumptions()
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/consumptions', expect.anything())

      stubFetchJson({ id: 'd1' })
      await getConsumption('d1')
      await createConsumption({
        callerId: 'alice@acme.example',
        serviceOfferingId: 'gpt-5.1',
        consumptionData: '{}',
      })
      stubFetchJson(undefined, { status: 204 })
      await deleteConsumption('d1')
    })

    it('supports connector control plane helpers', async () => {
      stubFetchJson({ connectors: [] })
      await listConnectors()
      expect(fetch).toHaveBeenLastCalledWith('/api/v1/connectors', expect.anything())

      stubFetchJson({ id: 'entra-directory', running: false })
      await getConnector('entra-directory')
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/connectors/entra-directory',
        expect.anything(),
      )

      stubFetchJson({ id: 'entra-directory', configuration: {} })
      await getConnectorConfig('entra-directory')
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/connectors/entra-directory/config',
        expect.anything(),
      )

      await updateConnectorConfig('entra-directory', {
        configuration: { refreshIntervalMs: 60_000 },
      })
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/connectors/entra-directory/config',
        expect.objectContaining({ method: 'PUT' }),
      )

      stubFetchJson({ id: 'entra-directory', running: true })
      await startConnector('entra-directory')
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/connectors/entra-directory/start',
        expect.objectContaining({ method: 'POST' }),
      )

      stubFetchJson({ id: 'entra-directory', running: false })
      await stopConnector('entra-directory')
      expect(fetch).toHaveBeenLastCalledWith(
        '/api/v1/connectors/entra-directory/stop',
        expect.objectContaining({ method: 'POST' }),
      )
    })
  })
})
