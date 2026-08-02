export type ParticipantStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'
export type CallerRegistrationStatus = 'ACTIVE' | 'INACTIVE' | 'REVOKED'
export type EntitlementStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'PENDING'

export type Participant = {
  id: string
  name: string
  contact: string | null
  status: ParticipantStatus
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

export type CreateParticipant = {
  id: string
  name: string
  contact?: string | null
  status?: ParticipantStatus
}

export type UpdateParticipant = {
  name: string
  contact?: string | null
  status: ParticipantStatus
}

/** Participant caller registration; callerId is the unique key. */
export type CallerRegistration = {
  callerId: string
  participantId: string
  participantName: string
  status: CallerRegistrationStatus
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

export type CreateCallerRegistration = {
  participantId: string
  callerId: string
  status?: CallerRegistrationStatus
}

export type UpdateCallerRegistration = {
  status: CallerRegistrationStatus
}

export type ServiceOffering = {
  id: string
  name: string
  description: string | null
  category: string
  /** Provider of the offering; defaults to SYSTEM. */
  provider: string
  config: string
  active: boolean
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

export type CreateServiceOffering = {
  id: string
  name: string
  description?: string | null
  category: string
  /** Defaults to SYSTEM when omitted. */
  provider?: string
  config?: string
  active?: boolean
}

export type UpdateServiceOffering = {
  name: string
  description?: string | null
  category: string
  provider: string
  config: string
  active: boolean
}

export type Entitlement = {
  id: string
  participantId: string
  participantName: string
  serviceOfferingId: string
  serviceOfferingName: string
  status: EntitlementStatus
  validFrom: string
  validTo: string | null
  config: string
  notes: string | null
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

export type CreateEntitlement = {
  participantId: string
  serviceOfferingId: string
  status?: EntitlementStatus
  validFrom: string
  validTo?: string | null
  config?: string
  notes?: string | null
}

export type UpdateEntitlement = {
  status: EntitlementStatus
  validFrom: string
  validTo?: string | null
  config: string
  notes?: string | null
}

export type Consumption = {
  id: string
  callerId: string
  participantId: string
  participantName: string
  serviceOfferingId: string
  serviceOfferingName: string
  /** Source Reference Identification from the consumption reporter (e.g. request UUID). */
  sourceRefId: string | null
  consumptionData: string
  /** When the consumption was captured at runtime (UTC business event time). */
  capturedAt: string
  /** When this row was stored in the platform (UTC audit time). */
  createdAt: string
}

export type CreateConsumption = {
  callerId: string
  serviceOfferingId: string
  /** Optional unique Source Reference Identification (e.g. request UUID). */
  sourceRefId?: string | null
  consumptionData?: string
  /** Optional runtime capture time (UTC ISO-8601). Defaults to now. */
  capturedAt?: string | null
}

export type AuthenticatedUser = {
  subject: string
  preferredUsername: string | null
  name: string | null
  clientId: string | null
  tenantId: string | null
  audience: string[]
  authorities: string[]
  scopes: string[]
  roles: string[]
  /** Entra security group object IDs from the JWT `groups` claim. */
  groups?: string[]
  /** Platform-System-* group display names resolved for this principal. */
  platformGroups?: string[]
  /** OAuth scopes configured for matching Application Registration IDs. */
  expectedScopes?: string[]
  /** Configured Application Registration client IDs that matched this token. */
  matchedApplicationRegistrationIds?: string[]
}

// —— Connectors (Maintainer control plane) ——

export type ConnectorIdPath =
  | 'entra-directory'
  | 'consumption-storage'
  | 'consumption-eventhub'
  | string

/** List row from GET /api/v1/connectors */
export type ConnectorSummary = {
  id: ConnectorIdPath
  enabled: boolean
  configured: boolean
  running: boolean
  status: string
  detail: string | null
  attributes: Record<string, string>
}

export type ConnectorListResponse = {
  connectors: ConnectorSummary[]
}

export type ConnectorLogSnapshot = {
  maxBytes: number
  bytes: number
  lineCount: number
  /** Newest first (index 0). */
  lines: string[]
}

/** Full process view from GET /api/v1/connectors/{id} */
export type ConnectorInfo = {
  id: ConnectorIdPath
  enabled: boolean
  configured: boolean
  running: boolean
  status: string
  detail: string | null
  lastStartedBy: string | null
  lastStartedAt: string | null
  lastStoppedBy: string | null
  lastStoppedAt: string | null
  lastError: string | null
  attributes: Record<string, string>
  configuration: Record<string, unknown>
  logSnapshot: ConnectorLogSnapshot
}

export type ConnectorConfigResponse = {
  id: ConnectorIdPath
  configuration: Record<string, unknown>
}

export type ConnectorConfigureRequest = {
  configuration: Record<string, unknown>
}
