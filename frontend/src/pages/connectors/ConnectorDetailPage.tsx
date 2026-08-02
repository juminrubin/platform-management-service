import type { FormEvent } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getConnector,
  startConnector,
  stopConnector,
  updateConnectorConfig,
} from '../../api/client'
import type { ConnectorInfo } from '../../api/types'
import {
  CodeBlock,
  DetailGrid,
  ErrorBox,
  Field,
  Loading,
  PageHeader,
  formatDateTime,
} from '../../components/ui'
import {
  connectorDescription,
  connectorTitle,
  editableConfigKeys,
  statusBadgeClass,
} from './connectorMeta'

function stringifyConfigValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'boolean' || typeof value === 'number') return String(value)
  return JSON.stringify(value)
}

function parseConfigField(key: string, raw: string): unknown {
  const trimmed = raw.trim()
  if (key === 'dryRun' || key === 'requireSourceRefId') {
    if (trimmed === '') return null
    if (trimmed === 'true' || trimmed === '1') return true
    if (trimmed === 'false' || trimmed === '0') return false
    return trimmed
  }
  if (key === 'refreshIntervalMs') {
    if (trimmed === '') return null
    const n = Number(trimmed)
    if (Number.isNaN(n)) throw new Error(`${key} must be a number`)
    return n
  }
  if (key === 'blobPrefixes') {
    if (trimmed === '') return []
    if (trimmed.startsWith('[')) return JSON.parse(trimmed) as string[]
    return trimmed.split(',').map((s) => s.trim())
  }
  return trimmed === '' ? null : trimmed
}

export function ConnectorDetailPage() {
  const { id = '' } = useParams()
  const [info, setInfo] = useState<ConnectorInfo | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [editValues, setEditValues] = useState<Record<string, string>>({})

  const editableKeys = useMemo(() => editableConfigKeys(id), [id])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getConnector(id)
      setInfo(data)
      const next: Record<string, string> = {}
      for (const key of editableConfigKeys(data.id)) {
        next[key] = stringifyConfigValue(data.configuration?.[key])
      }
      setEditValues(next)
    } catch (e) {
      setError((e as Error).message)
      setInfo(null)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  async function onStart() {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const data = await startConnector(id)
      setInfo(data)
      setMessage('Connector started.')
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function onStop() {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const data = await stopConnector(id)
      setInfo(data)
      setMessage('Connector stop requested (in-flight work is not hard-cancelled).')
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function onSaveConfig(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const configuration: Record<string, unknown> = {}
      for (const key of editableKeys) {
        configuration[key] = parseConfigField(key, editValues[key] ?? '')
      }
      await updateConnectorConfig(id, { configuration })
      await load()
      setMessage('Configuration saved.')
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const configEntries = info
    ? Object.entries(info.configuration ?? {}).map(([label, value]) => ({
        label,
        value:
          value === null || value === undefined ? (
            '—'
          ) : typeof value === 'object' ? (
            <code>{JSON.stringify(value)}</code>
          ) : (
            <code>{String(value)}</code>
          ),
      }))
    : []

  const attributeEntries = info
    ? Object.entries(info.attributes ?? {}).map(([label, value]) => ({
        label,
        value: <code>{value}</code>,
      }))
    : []

  return (
    <section className="card">
      <PageHeader
        title={connectorTitle(id)}
        subtitle={connectorDescription(id)}
        actions={
          <div className="row gap">
            <Link className="button" to="/connectors">
              Back to list
            </Link>
            <button type="button" onClick={() => void load()} disabled={loading || busy}>
              Refresh
            </button>
            <button
              type="button"
              className="primary"
              disabled={busy || !info?.enabled || !!info?.running}
              onClick={() => void onStart()}
            >
              Start
            </button>
            <button type="button" disabled={busy || !info?.running} onClick={() => void onStop()}>
              Stop
            </button>
          </div>
        }
      />

      <ErrorBox error={error} />
      {message && <p className="ok-message">{message}</p>}
      {loading && !info && <Loading />}

      {info && (
        <div className="stack">
          <DetailGrid
            entries={[
              { label: 'ID', value: <code>{info.id}</code> },
              {
                label: 'Status',
                value: <span className={`badge ${statusBadgeClass(info.status)}`}>{info.status}</span>,
              },
              { label: 'Detail', value: info.detail },
              { label: 'Enabled', value: info.enabled ? 'yes' : 'no' },
              { label: 'Configured', value: info.configured ? 'yes' : 'no' },
              { label: 'Running', value: info.running ? 'yes' : 'no' },
              { label: 'Last error', value: info.lastError },
              { label: 'Last started by', value: info.lastStartedBy },
              { label: 'Last started at', value: formatDateTime(info.lastStartedAt) },
              { label: 'Last stopped by', value: info.lastStoppedBy },
              { label: 'Last stopped at', value: formatDateTime(info.lastStoppedAt) },
            ]}
          />

          <h2 className="section-title">Runtime attributes</h2>
          {attributeEntries.length === 0 ? (
            <p className="muted">No attributes.</p>
          ) : (
            <DetailGrid entries={attributeEntries} />
          )}

          <h2 className="section-title">Configuration (public)</h2>
          {configEntries.length === 0 ? (
            <p className="muted">No configuration exposed.</p>
          ) : (
            <DetailGrid entries={configEntries} />
          )}

          {editableKeys.length > 0 && (
            <>
              <h2 className="section-title">Edit runtime configuration</h2>
              <p className="muted">
                Only runtime-editable keys are sent to the API. Deploy-time secrets and IDs stay
                read-only above.
              </p>
              <form className="stack" onSubmit={(e) => void onSaveConfig(e)}>
                <div className="filter-fields">
                  {editableKeys.map((key) => (
                    <Field
                      key={key}
                      label={key}
                      hint={
                        key === 'blobPrefixes'
                          ? 'Comma-separated prefixes, or JSON array. Empty uses all configured prefixes.'
                          : key === 'refreshIntervalMs'
                            ? 'Milliseconds between Graph refreshes while running (0 disables schedule).'
                            : key === 'dryRun' || key === 'requireSourceRefId'
                              ? 'true or false'
                              : key.includes('Date')
                                ? 'YYYY-MM-DD'
                                : undefined
                      }
                    >
                      <input
                        value={editValues[key] ?? ''}
                        onChange={(e) =>
                          setEditValues((prev) => ({ ...prev, [key]: e.target.value }))
                        }
                        disabled={busy}
                      />
                    </Field>
                  ))}
                </div>
                <div className="row gap">
                  <button type="submit" className="primary" disabled={busy}>
                    Save configuration
                  </button>
                </div>
              </form>
            </>
          )}

          <h2 className="section-title">
            Process log snapshot{' '}
            <span className="muted" style={{ fontWeight: 400, fontSize: '0.9rem' }}>
              ({info.logSnapshot.lineCount} lines, {info.logSnapshot.bytes} /{' '}
              {info.logSnapshot.maxBytes} bytes; newest first)
            </span>
          </h2>
          {info.logSnapshot.lines.length === 0 ? (
            <p className="muted">No log lines yet.</p>
          ) : (
            <CodeBlock value={info.logSnapshot.lines.join('\n')} />
          )}
        </div>
      )}
    </section>
  )
}
