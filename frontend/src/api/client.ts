import { InteractionRequiredAuthError } from '@azure/msal-browser'
import { msalInstance, apiScopes } from '../auth/msalConfig'
import type {
  AuthenticatedUser,
  CallerIdentity,
  Consumption,
  CreateCallerIdentity,
  CreateConsumption,
  CreateEntitlement,
  CreateParticipant,
  CreateServiceOffering,
  Entitlement,
  Participant,
  ServiceOffering,
  UpdateCallerIdentity,
  UpdateEntitlement,
  UpdateParticipant,
  UpdateServiceOffering,
} from './types'

const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) || ''

async function getAccessToken(): Promise<string> {
  const accounts = msalInstance.getAllAccounts()
  if (accounts.length === 0) {
    throw new Error('Not signed in')
  }
  const account = accounts[0]
  try {
    const result = await msalInstance.acquireTokenSilent({
      account,
      scopes: apiScopes,
    })
    return result.accessToken
  } catch (e) {
    if (e instanceof InteractionRequiredAuthError) {
      const result = await msalInstance.acquireTokenPopup({
        account,
        scopes: apiScopes,
      })
      return result.accessToken
    }
    throw e
  }
}

function toQuery(params: Record<string, string | boolean | undefined | null>): string {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return
    q.set(k, String(v))
  })
  const s = q.toString()
  return s ? `?${s}` : ''
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = await getAccessToken()
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const res = await fetch(`${baseUrl}${path}`, { ...init, headers })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`${res.status} ${res.statusText}: ${text}`)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}

// —— Auth ——
export const getMe = () => apiFetch<AuthenticatedUser>('/api/v1/auth/me')

// —— Participants ——
export const listParticipants = (status?: string) =>
  apiFetch<Participant[]>(`/api/v1/participants${toQuery({ status })}`)

export const getParticipant = (id: string) =>
  apiFetch<Participant>(`/api/v1/participants/${encodeURIComponent(id)}`)

export const createParticipant = (body: CreateParticipant) =>
  apiFetch<Participant>('/api/v1/participants', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const updateParticipant = (id: string, body: UpdateParticipant) =>
  apiFetch<Participant>(`/api/v1/participants/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })

export const deleteParticipant = (id: string) =>
  apiFetch<void>(`/api/v1/participants/${encodeURIComponent(id)}`, { method: 'DELETE' })

// —— Caller identities ——
export const listCallerIdentities = (filters?: {
  participantId?: string
  status?: string
}) =>
  apiFetch<CallerIdentity[]>(
    `/api/v1/caller-identities${toQuery({
      participantId: filters?.participantId,
      status: filters?.status,
    })}`,
  )

export const getCallerIdentity = (id: string) =>
  apiFetch<CallerIdentity>(`/api/v1/caller-identities/${encodeURIComponent(id)}`)

export const createCallerIdentity = (body: CreateCallerIdentity) =>
  apiFetch<CallerIdentity>('/api/v1/caller-identities', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const updateCallerIdentity = (id: string, body: UpdateCallerIdentity) =>
  apiFetch<CallerIdentity>(`/api/v1/caller-identities/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })

export const deleteCallerIdentity = (id: string) =>
  apiFetch<void>(`/api/v1/caller-identities/${encodeURIComponent(id)}`, { method: 'DELETE' })

// —— Service offerings ——
export const listServiceOfferings = (filters?: { activeOnly?: boolean; category?: string }) =>
  apiFetch<ServiceOffering[]>(
    `/api/v1/service-offerings${toQuery({
      activeOnly: filters?.activeOnly,
      category: filters?.category,
    })}`,
  )

export const getServiceOffering = (id: string) =>
  apiFetch<ServiceOffering>(`/api/v1/service-offerings/${encodeURIComponent(id)}`)

export const createServiceOffering = (body: CreateServiceOffering) =>
  apiFetch<ServiceOffering>('/api/v1/service-offerings', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const updateServiceOffering = (id: string, body: UpdateServiceOffering) =>
  apiFetch<ServiceOffering>(`/api/v1/service-offerings/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })

export const deleteServiceOffering = (id: string) =>
  apiFetch<void>(`/api/v1/service-offerings/${encodeURIComponent(id)}`, { method: 'DELETE' })

// —— Entitlements ——
export const listEntitlements = (filters?: {
  participantId?: string
  serviceOfferingId?: string
  status?: string
}) =>
  apiFetch<Entitlement[]>(
    `/api/v1/entitlements${toQuery({
      participantId: filters?.participantId,
      serviceOfferingId: filters?.serviceOfferingId,
      status: filters?.status,
    })}`,
  )

export const getEntitlement = (id: string) =>
  apiFetch<Entitlement>(`/api/v1/entitlements/${encodeURIComponent(id)}`)

export const createEntitlement = (body: CreateEntitlement) =>
  apiFetch<Entitlement>('/api/v1/entitlements', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const updateEntitlement = (id: string, body: UpdateEntitlement) =>
  apiFetch<Entitlement>(`/api/v1/entitlements/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })

export const deleteEntitlement = (id: string) =>
  apiFetch<void>(`/api/v1/entitlements/${encodeURIComponent(id)}`, { method: 'DELETE' })

// —— Consumptions ——
export const listConsumptions = (filters?: {
  participantCallerIdentityId?: string
  serviceOfferingId?: string
}) =>
  apiFetch<Consumption[]>(
    `/api/v1/consumptions${toQuery({
      participantCallerIdentityId: filters?.participantCallerIdentityId,
      serviceOfferingId: filters?.serviceOfferingId,
    })}`,
  )

export const getConsumption = (id: string) =>
  apiFetch<Consumption>(`/api/v1/consumptions/${encodeURIComponent(id)}`)

export const createConsumption = (body: CreateConsumption) =>
  apiFetch<Consumption>('/api/v1/consumptions', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const deleteConsumption = (id: string) =>
  apiFetch<void>(`/api/v1/consumptions/${encodeURIComponent(id)}`, { method: 'DELETE' })
