import type { FormEvent } from 'react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteEntitlement, listEntitlements } from '../../api/client'
import type { Entitlement } from '../../api/types'
import { useAuthorization } from '../../auth/AuthorizationContext'
import { EmptyState, ErrorBox, Field, FilterBar, Loading, PageHeader } from '../../components/ui'

export function EntitlementListPage() {
  const { canMaintain } = useAuthorization()
  const [items, setItems] = useState<Entitlement[]>([])
  const [participantId, setParticipantId] = useState('')
  const [serviceOfferingId, setServiceOfferingId] = useState('')
  const [status, setStatus] = useState('')
  const [q, setQ] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listEntitlements({
        participantId: participantId || undefined,
        serviceOfferingId: serviceOfferingId || undefined,
        status: status || undefined,
      })
      setItems(data)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [participantId, serviceOfferingId, status])

  useEffect(() => {
    void load()
  }, [load])

  const filtered = items.filter((row) => {
    if (!q.trim()) return true
    const s = q.toLowerCase()
    return (
      row.participantId.toLowerCase().includes(s) ||
      row.participantName.toLowerCase().includes(s) ||
      row.serviceOfferingId.toLowerCase().includes(s) ||
      row.serviceOfferingName.toLowerCase().includes(s) ||
      (row.notes ?? '').toLowerCase().includes(s)
    )
  })

  async function onDelete(rowId: string) {
    if (!confirm('Delete this entitlement?')) return
    try {
      await deleteEntitlement(rowId)
      await load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Entitlements"
        subtitle="Participant access rights to a service offering."
        actions={
          canMaintain ? (
            <Link className="button primary" to="/entitlements/new">
              New entitlement
            </Link>
          ) : undefined
        }
      />

      <FilterBar
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          void load()
        }}
        onReset={() => {
          setParticipantId('')
          setServiceOfferingId('')
          setStatus('')
          setQ('')
        }}
      >
        <Field label="Participant ID (API)">
          <input value={participantId} onChange={(e) => setParticipantId(e.target.value)} placeholder="acme-corp" />
        </Field>
        <Field label="Service offering ID (API)">
          <input
            value={serviceOfferingId}
            onChange={(e) => setServiceOfferingId(e.target.value)}
            placeholder="gpt-5.1"
          />
        </Field>
        <Field label="Status (API)">
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="PENDING">PENDING</option>
            <option value="EXPIRED">EXPIRED</option>
            <option value="REVOKED">REVOKED</option>
          </select>
        </Field>
        <Field label="Search names / notes">
          <input value={q} onChange={(e) => setQ(e.target.value)} />
        </Field>
      </FilterBar>

      <ErrorBox error={error} />
      {loading && <Loading />}
      {!loading && filtered.length === 0 && <EmptyState message="No entitlements match." />}
      {!loading && filtered.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Participant</th>
              <th>Service</th>
              <th>Status</th>
              <th>Valid</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((row) => (
              <tr key={row.id}>
                <td>
                  {row.participantName}
                  <br />
                  <code>{row.participantId}</code>
                </td>
                <td>
                  {row.serviceOfferingName}
                  <br />
                  <code>{row.serviceOfferingId}</code>
                </td>
                <td>
                  <span className={`badge status-${row.status}`}>{row.status}</span>
                </td>
                <td className="nowrap">
                  {row.validFrom} → {row.validTo ?? '∞'}
                </td>
                <td className="row gap">
                  <Link className="button" to={`/entitlements/${row.id}`}>
                    View
                  </Link>
                  {canMaintain && (
                    <>
                      <Link className="button" to={`/entitlements/${row.id}/edit`}>
                        Edit
                      </Link>
                      <button type="button" onClick={() => void onDelete(row.id)}>
                        Delete
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
