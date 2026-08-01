import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteEntitlement, getEntitlement } from '../../api/client'
import type { Entitlement } from '../../api/types'
import { useAuthorization } from '../../auth/AuthorizationContext'
import { CodeBlock, DetailGrid, ErrorBox, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function EntitlementDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { canMaintain } = useAuthorization()
  const [item, setItem] = useState<Entitlement | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getEntitlement(id)
      .then(setItem)
      .catch((e: Error) => setError(e.message))
  }, [id])

  async function onDelete() {
    if (!confirm('Delete this entitlement?')) return
    try {
      await deleteEntitlement(id)
      navigate('/entitlements')
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Entitlement"
        actions={
          <div className="row gap">
            <Link className="button" to="/entitlements">
              Back
            </Link>
            {canMaintain && (
              <>
                <Link className="button primary" to={`/entitlements/${id}/edit`}>
                  Edit
                </Link>
                <button type="button" onClick={() => void onDelete()}>
                  Delete
                </button>
              </>
            )}
          </div>
        }
      />
      <ErrorBox error={error} />
      {!item && !error && <Loading />}
      {item && (
        <>
          <DetailGrid
            entries={[
              { label: 'ID', value: <code>{item.id}</code> },
              {
                label: 'Participant',
                value: (
                  <Link to={`/participants/${encodeURIComponent(item.participantId)}`}>
                    {item.participantName} ({item.participantId})
                  </Link>
                ),
              },
              {
                label: 'Service offering',
                value: (
                  <Link to={`/service-offerings/${encodeURIComponent(item.serviceOfferingId)}`}>
                    {item.serviceOfferingName} ({item.serviceOfferingId})
                  </Link>
                ),
              },
              { label: 'Status', value: <span className={`badge status-${item.status}`}>{item.status}</span> },
              { label: 'Valid from', value: item.validFrom },
              { label: 'Valid to', value: item.validTo ?? '∞' },
              { label: 'Notes', value: item.notes },
              { label: 'Created', value: formatDateTime(item.createdAt) },
              { label: 'Created by', value: item.createdBy },
              { label: 'Updated', value: formatDateTime(item.updatedAt) },
              { label: 'Updated by', value: item.updatedBy },
            ]}
          />
          <h3>Config (JSON)</h3>
          <CodeBlock value={item.config} />
        </>
      )}
    </section>
  )
}
