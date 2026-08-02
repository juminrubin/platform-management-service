import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listConnectors, startConnector, stopConnector } from '../../api/client'
import type { ConnectorSummary } from '../../api/types'
import { EmptyState, ErrorBox, Loading, PageHeader, formatDateTime } from '../../components/ui'
import { connectorDescription, connectorTitle, statusBadgeClass } from './connectorMeta'

export function ConnectorListPage() {
  const [items, setItems] = useState<ConnectorSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listConnectors()
      setItems(data.connectors)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function onStart(id: string) {
    setBusyId(id)
    setError(null)
    try {
      await startConnector(id)
      await load()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusyId(null)
    }
  }

  async function onStop(id: string) {
    setBusyId(id)
    setError(null)
    try {
      await stopConnector(id)
      await load()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Connectors"
        subtitle="Backend integration processes (System.Maintainer). View runtime status, edit config, start and stop."
        actions={
          <button type="button" onClick={() => void load()} disabled={loading}>
            Refresh
          </button>
        }
      />

      <ErrorBox error={error} />
      {loading && <Loading />}
      {!loading && items.length === 0 && <EmptyState message="No connectors registered." />}
      {!loading && items.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Connector</th>
              <th>Status</th>
              <th>Enabled</th>
              <th>Running</th>
              <th>Detail</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {items.map((c) => {
              const busy = busyId === c.id
              return (
                <tr key={c.id}>
                  <td>
                    <Link to={`/connectors/${encodeURIComponent(c.id)}`}>
                      <strong>{connectorTitle(c.id)}</strong>
                    </Link>
                    <div className="muted" style={{ fontSize: '0.85rem' }}>
                      <code>{c.id}</code>
                    </div>
                    <div className="muted" style={{ fontSize: '0.82rem', maxWidth: 320 }}>
                      {connectorDescription(c.id)}
                    </div>
                  </td>
                  <td>
                    <span className={`badge ${statusBadgeClass(c.status)}`}>{c.status}</span>
                  </td>
                  <td>{c.enabled ? 'yes' : 'no'}</td>
                  <td>{c.running ? 'yes' : 'no'}</td>
                  <td className="muted" style={{ maxWidth: 220 }}>
                    {c.detail ?? '—'}
                    {c.attributes?.lastLoadedAt && (
                      <div style={{ fontSize: '0.8rem' }}>
                        loaded {formatDateTime(c.attributes.lastLoadedAt)}
                      </div>
                    )}
                  </td>
                  <td className="row gap">
                    <Link className="button" to={`/connectors/${encodeURIComponent(c.id)}`}>
                      Open
                    </Link>
                    <button
                      type="button"
                      className="primary"
                      disabled={busy || !c.enabled || c.running}
                      onClick={() => void onStart(c.id)}
                      title={c.running ? 'Already running' : 'Start connector'}
                    >
                      Start
                    </button>
                    <button
                      type="button"
                      disabled={busy || !c.running}
                      onClick={() => void onStop(c.id)}
                      title={!c.running ? 'Not running' : 'Stop connector'}
                    >
                      Stop
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </section>
  )
}
