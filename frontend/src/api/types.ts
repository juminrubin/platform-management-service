export type ParticipantStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'
export type CallerIdentityStatus = 'ACTIVE' | 'INACTIVE' | 'REVOKED'
export type EntitlementStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'PENDING'

export type Participant = {
  id: string
  name: string
  contact: string | null
  status: ParticipantStatus
  createdAt: string
  updatedAt: string
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

export type CallerIdentity = {
  id: string
  participantId: string
  participantName: string
  callerIdentity: string
  status: CallerIdentityStatus
  createdAt: string
  updatedAt: string
}

export type CreateCallerIdentity = {
  participantId: string
  callerIdentity: string
  status?: CallerIdentityStatus
}

export type UpdateCallerIdentity = {
  status: CallerIdentityStatus
}

export type ServiceOffering = {
  id: string
  name: string
  description: string | null
  category: string
  config: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export type CreateServiceOffering = {
  id: string
  name: string
  description?: string | null
  category: string
  config?: string
  active?: boolean
}

export type UpdateServiceOffering = {
  name: string
  description?: string | null
  category: string
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
  updatedAt: string
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
  participantCallerIdentityId: string
  callerIdentity: string
  participantId: string
  participantName: string
  serviceOfferingId: string
  serviceOfferingName: string
  consumptionData: string
  createdAt: string
}

export type CreateConsumption = {
  participantCallerIdentityId: string
  serviceOfferingId: string
  consumptionData?: string
  consumedAt?: string | null
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
}
