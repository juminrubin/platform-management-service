import type { FormEvent } from 'react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteCallerIdentity, listCallerIdentities } from '../../api/client'
import type { CallerIdentity } from '../../api/types'
import { EmptyState, ErrorBox, Field, FilterBar, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function CallerIdentityListPage() {
  const [items, setItems] = useState<CallerIdentity[]>([])
  const [participantId, setParticipantId] = useState('')
  const [status, setStatus] = useState('')
  const [q, setQ] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listCallerIdentities({
        participantId: participantId || undefined,
        status: status || undefined,
      })
      setItems(data)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [participantId, status])

  useEffect(() => {
    void load()
  }, [load])

  const filtered = items.filter((c) => {
    if (!q.trim()) return true
    const s = q.toLowerCase()
    return (
      c.callerIdentity.toLowerCase().includes(s) ||
      c.participantId.toLowerCase().includes(s) ||
      c.participantName.toLowerCase().includes(s) ||
      c.id.toLowerCase().includes(s)
    )
  })

  async function onDelete(rowId: string) {
    if (!confirm('Delete this caller identity?')) return
    try {
      await deleteCallerIdentity(rowId)
      await load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Caller identities"
        subtitle="Email / Entra client id / managed identity linked to a participant."
        actions={
          <Link className="button primary" to="/caller-identities/new">
            Register identity
          </Link>
        }
      />

      <FilterBar
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          void load()
        }}
        onReset={() => {
          setParticipantId('')
          setStatus('')
          setQ('')
        }}
      >
        <Field label="Participant ID (API)">
          <input value={participantId} onChange={(e) => setParticipantId(e.target.value)} placeholder="acme-corp" />
        </Field>
        <Field label="Status (API)">
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="REVOKED">REVOKED</option>
          </select>
        </Field>
        <Field label="Search identity / participant">
          <input value={q} onChange={(e) => setQ(e.target.value)} />
        </Field>
      </FilterBar>

      <ErrorBox error={error} />
      {loading && <Loading />}
      {!loading && filtered.length === 0 && <EmptyState message="No caller identities match." />}
      {!loading && filtered.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Caller identity</th>
              <th>Participant</th>
              <th>Status</th>
              <th>Updated</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((c) => (
              <tr key={c.id}>
                <td>
                  <Link to={`/caller-identities/${c.id}`}>
                    <code>{c.callerIdentity}</code>
                  </Link>
                </td>
                <td>
                  {c.participantName} (<code>{c.participantId}</code>)
                </td>
                <td>
                  <span className={`badge status-${c.status}`}>{c.status}</span>
                </td>
                <td className="nowrap">{formatDateTime(c.updatedAt)}</td>
                <td className="row gap">
                  <Link className="button" to={`/caller-identities/${c.id}`}>
                    View
                  </Link>
                  <Link className="button" to={`/caller-identities/${c.id}/edit`}>
                    Edit
                  </Link>
                  <button type="button" onClick={() => void onDelete(c.id)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
