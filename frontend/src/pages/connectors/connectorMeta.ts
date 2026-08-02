import type { ConnectorIdPath } from '../../api/types'

/** Human labels and help for known connector path ids. */
export const CONNECTOR_LABELS: Record<string, { title: string; description: string }> = {
  'entra-directory': {
    title: 'Entra directory',
    description:
      'Loads Platform-System-* groups and members from Microsoft Graph. Data: /api/v1/entra/groups.',
  },
  'consumption-storage': {
    title: 'Consumption blob storage',
    description:
      'Imports consumption Avro files from Azure Blob for a configured date range. Data: /api/v1/consumption/blob.',
  },
  'consumption-eventhub': {
    title: 'Consumption Event Hub',
    description: 'Live Event Hub consumer for consumption events. Data: /api/v1/consumptions.',
  },
}

export function connectorTitle(id: ConnectorIdPath): string {
  return CONNECTOR_LABELS[id]?.title ?? id
}

export function connectorDescription(id: ConnectorIdPath): string {
  return CONNECTOR_LABELS[id]?.description ?? 'Integration connector process.'
}

/** Runtime-editable configuration keys accepted by the backend configure API. */
export function editableConfigKeys(id: ConnectorIdPath): string[] {
  switch (id) {
    case 'entra-directory':
      return ['refreshIntervalMs']
    case 'consumption-storage':
      return ['startDate', 'endDate', 'dryRun', 'blobPrefixes']
    case 'consumption-eventhub':
      return ['requireSourceRefId']
    default:
      return []
  }
}

export function statusBadgeClass(status: string): string {
  const s = status.toUpperCase()
  if (s === 'RUNNING' || s === 'UP') return 'status-ACTIVE'
  if (s === 'STOPPED' || s === 'DISABLED') return 'status-INACTIVE'
  if (s === 'DEGRADED' || s === 'DOWN') return 'status-SUSPENDED'
  return 'status-PENDING'
}
