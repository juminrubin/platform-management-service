import type { FormEvent } from 'react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteCallerRegistration, listCallerRegistrations } from '../../api/client'
import type { CallerRegistration } from '../../api/types'
import { EmptyState, ErrorBox, Field, FilterBar, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function CallerRegistrationListPage() {
  const [items, setItems] = useState<CallerRegistration[]>([])
  const [participantId, setParticipantId] = useState('')
  const [status, setStatus] = useState('')
  const [q, setQ] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listCallerRegistrations({
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
      c.callerId.toLowerCase().includes(s) ||
      c.participantId.toLowerCase().includes(s) ||
      c.participantName.toLowerCase().includes(s)
    )
  })

  async function onDelete(callerId: string) {
    if (!confirm('Delete this caller registration?')) return
    try {
      await deleteCallerRegistration(callerId)
      await load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Caller registrations"
        subtitle="Unique caller principals (email / Entra client id / MI) grouped under a participant for billing."
        actions={
          <Link className="button primary" to="/caller-registrations/new">
            Register caller
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
        <Field label="Search caller / participant">
          <input value={q} onChange={(e) => setQ(e.target.value)} />
        </Field>
      </FilterBar>

      <ErrorBox error={error} />
      {loading && <Loading />}
      {!loading && filtered.length === 0 && <EmptyState message="No caller registrations match." />}
      {!loading && filtered.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Caller ID</th>
              <th>Participant</th>
              <th>Status</th>
              <th>Updated</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((c) => (
              <tr key={c.callerId}>
                <td>
                  <Link to={`/caller-registrations/${encodeURIComponent(c.callerId)}`}>
                    <code>{c.callerId}</code>
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
                  <Link className="button" to={`/caller-registrations/${encodeURIComponent(c.callerId)}`}>
                    View
                  </Link>
                  <Link className="button" to={`/caller-registrations/${encodeURIComponent(c.callerId)}/edit`}>
                    Edit
                  </Link>
                  <button type="button" onClick={() => void onDelete(c.callerId)}>
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
