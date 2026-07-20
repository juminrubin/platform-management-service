import type { FormEvent } from 'react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteServiceOffering, listServiceOfferings } from '../../api/client'
import type { ServiceOffering } from '../../api/types'
import { useAuthorization } from '../../auth/AuthorizationContext'
import { EmptyState, ErrorBox, Field, FilterBar, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function ServiceOfferingListPage() {
  const { canMaintain } = useAuthorization()
  const [items, setItems] = useState<ServiceOffering[]>([])
  const [activeOnly, setActiveOnly] = useState(false)
  const [category, setCategory] = useState('')
  const [q, setQ] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listServiceOfferings({
        activeOnly: activeOnly || undefined,
        category: category || undefined,
      })
      setItems(data)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [activeOnly, category])

  useEffect(() => {
    void load()
  }, [load])

  const filtered = items.filter((o) => {
    if (!q.trim()) return true
    const s = q.toLowerCase()
    return (
      o.id.toLowerCase().includes(s) ||
      o.name.toLowerCase().includes(s) ||
      (o.description ?? '').toLowerCase().includes(s) ||
      o.category.toLowerCase().includes(s)
    )
  })

  async function onDelete(rowId: string) {
    if (!confirm(`Delete service offering ${rowId}?`)) return
    try {
      await deleteServiceOffering(rowId)
      await load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Service offerings"
        subtitle="Catalog of services that can be entitled to participants."
        actions={
          canMaintain ? (
            <Link className="button primary" to="/service-offerings/new">
              New offering
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
          setActiveOnly(false)
          setCategory('')
          setQ('')
        }}
      >
        <Field label="Category (API)">
          <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="LLM, SPEECH…" />
        </Field>
        <Field label="Active only (API)">
          <select
            value={activeOnly ? 'true' : 'false'}
            onChange={(e) => setActiveOnly(e.target.value === 'true')}
          >
            <option value="false">No</option>
            <option value="true">Yes</option>
          </select>
        </Field>
        <Field label="Search id / name / description">
          <input value={q} onChange={(e) => setQ(e.target.value)} />
        </Field>
      </FilterBar>

      <ErrorBox error={error} />
      {loading && <Loading />}
      {!loading && filtered.length === 0 && <EmptyState message="No service offerings match." />}
      {!loading && filtered.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Category</th>
              <th>Active</th>
              <th>Updated</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((o) => (
              <tr key={o.id}>
                <td>
                  <Link to={`/service-offerings/${encodeURIComponent(o.id)}`}>
                    <code>{o.id}</code>
                  </Link>
                </td>
                <td>{o.name}</td>
                <td>{o.category}</td>
                <td>{o.active ? 'Yes' : 'No'}</td>
                <td className="nowrap">{formatDateTime(o.updatedAt)}</td>
                <td className="row gap">
                  <Link className="button" to={`/service-offerings/${encodeURIComponent(o.id)}`}>
                    View
                  </Link>
                  {canMaintain && (
                    <>
                      <Link className="button" to={`/service-offerings/${encodeURIComponent(o.id)}/edit`}>
                        Edit
                      </Link>
                      <button type="button" onClick={() => void onDelete(o.id)}>
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
