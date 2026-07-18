import type { FormEvent } from 'react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteParticipant, listParticipants } from '../../api/client'
import type { Participant, ParticipantStatus } from '../../api/types'
import { EmptyState, ErrorBox, FilterBar, Field, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function ParticipantListPage() {
  const [items, setItems] = useState<Participant[]>([])
  const [status, setStatus] = useState('')
  const [q, setQ] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listParticipants(status || undefined)
      setItems(data)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    void load()
  }, [load])

  const filtered = items.filter((p) => {
    if (!q.trim()) return true
    const s = q.toLowerCase()
    return (
      p.id.toLowerCase().includes(s) ||
      p.name.toLowerCase().includes(s) ||
      (p.contact ?? '').toLowerCase().includes(s)
    )
  })

  async function onDelete(rowId: string) {
    if (!confirm(`Delete participant ${rowId}?`)) return
    try {
      await deleteParticipant(rowId)
      await load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  function onFilter(e: FormEvent) {
    e.preventDefault()
    void load()
  }

  return (
    <section className="card">
      <PageHeader
        title="Participants"
        subtitle="Organizations that consume platform services."
        actions={
          <Link className="button primary" to="/participants/new">
            New participant
          </Link>
        }
      />

      <FilterBar
        onSubmit={onFilter}
        onReset={() => {
          setStatus('')
          setQ('')
          void listParticipants().then(setItems)
        }}
      >
        <Field label="Status (API)">
          <select value={status} onChange={(e) => setStatus(e.target.value as ParticipantStatus | '')}>
            <option value="">All</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
          </select>
        </Field>
        <Field label="Search id / name / contact">
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Filter current results" />
        </Field>
      </FilterBar>

      <ErrorBox error={error} />
      {loading && <Loading />}
      {!loading && filtered.length === 0 && <EmptyState message="No participants match filters." />}
      {!loading && filtered.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Contact</th>
              <th>Status</th>
              <th>Updated</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((p) => (
              <tr key={p.id}>
                <td>
                  <Link to={`/participants/${encodeURIComponent(p.id)}`}>
                    <code>{p.id}</code>
                  </Link>
                </td>
                <td>{p.name}</td>
                <td>{p.contact ?? '—'}</td>
                <td>
                  <span className={`badge status-${p.status}`}>{p.status}</span>
                </td>
                <td className="nowrap">{formatDateTime(p.updatedAt)}</td>
                <td className="row gap">
                  <Link className="button" to={`/participants/${encodeURIComponent(p.id)}`}>
                    View
                  </Link>
                  <Link className="button" to={`/participants/${encodeURIComponent(p.id)}/edit`}>
                    Edit
                  </Link>
                  <button type="button" onClick={() => void onDelete(p.id)}>
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
