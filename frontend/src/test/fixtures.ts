import type {
  CallerRegistration,
  Consumption,
  Entitlement,
  Participant,
  ServiceOffering,
} from '../api/types'

export const participantActive: Participant = {
  id: 'acme-corp',
  name: 'Acme Corporation',
  contact: 'ops@acme.example',
  status: 'ACTIVE',
  createdAt: '2024-01-15T10:00:00Z',
  updatedAt: '2024-01-15T10:00:00Z',
}

export const participantInactive: Participant = {
  id: 'gamma-partners',
  name: 'Gamma Partners',
  contact: 'contact@gamma.example',
  status: 'INACTIVE',
  createdAt: '2024-03-10T14:00:00Z',
  updatedAt: '2024-06-01T08:00:00Z',
}

export const callerRegistrationActive: CallerRegistration = {
  callerId: 'alice@acme.example',
  participantId: 'acme-corp',
  participantName: 'Acme Corporation',
  status: 'ACTIVE',
  createdAt: '2024-01-16T08:00:00Z',
  updatedAt: '2024-01-16T08:00:00Z',
}

export const serviceOfferingGpt: ServiceOffering = {
  id: 'gpt-5.1',
  name: 'GPT 5.1',
  description: 'Flagship chat completion model',
  category: 'LLM',
  config: '{"default_max_tpm":120000}',
  active: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
}

export const serviceOfferingLegacy: ServiceOffering = {
  id: 'legacy-batch',
  name: 'Legacy Batch',
  description: 'Deprecated',
  category: 'PLATFORM',
  config: '{}',
  active: false,
  createdAt: '2023-06-01T00:00:00Z',
  updatedAt: '2024-05-01T00:00:00Z',
}

export const entitlementActive: Entitlement = {
  id: 'e1111111-1111-1111-1111-111111111111',
  participantId: 'acme-corp',
  participantName: 'Acme Corporation',
  serviceOfferingId: 'gpt-5.1',
  serviceOfferingName: 'GPT 5.1',
  status: 'ACTIVE',
  validFrom: '2024-01-15',
  validTo: '2025-12-31',
  config: '{"max_tpm":100000}',
  notes: 'Enterprise tier',
  createdAt: '2024-01-15T10:05:00Z',
  updatedAt: '2024-01-15T10:05:00Z',
}

export const consumptionSample: Consumption = {
  id: 'd1111111-1111-1111-1111-111111111111',
  callerId: 'alice@acme.example',
  participantId: 'acme-corp',
  participantName: 'Acme Corporation',
  serviceOfferingId: 'gpt-5.1',
  serviceOfferingName: 'GPT 5.1',
  sourceRefId: 'req-d1111111-1111-1111-1111-111111111111',
  consumptionData: '{"input_token":1200,"output_token":340}',
  createdAt: '2024-06-01T12:00:00Z',
}

export const consumptionLater: Consumption = {
  id: 'd2222222-2222-2222-2222-222222222222',
  callerId: 'alice@acme.example',
  participantId: 'acme-corp',
  participantName: 'Acme Corporation',
  serviceOfferingId: 'gpt-5.1',
  serviceOfferingName: 'GPT 5.1',
  sourceRefId: 'req-d2222222-2222-2222-2222-222222222222',
  consumptionData: '{"input_token":50,"output_token":10}',
  createdAt: '2024-07-15T09:30:00Z',
}
