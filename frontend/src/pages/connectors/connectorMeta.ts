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
      'Reads Avro from the input container, filters object_type, and writes Parquet to the output container. Data: /api/v1/consumption/blob.',
  },
  'consumption-eventhub': {
    title: 'Consumption Event Hub',
    description: 'Live Event Hub consumer for consumption events. Data: /api/v1/consumptions.',
  },
  'daily-consumption-aggregate': {
    title: 'Daily consumption aggregate',
    description:
      'Compacts yesterday UTC 5-minute Parquet files into one daily file under the configured daily prefix.',
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
      return ['startDate', 'endDate', 'dryRun', 'force', 'inputBlobPrefixes']
    case 'consumption-eventhub':
      return ['requireSourceRefId']
    case 'daily-consumption-aggregate':
      return ['targetDate', 'force', 'runHourUtc']
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
