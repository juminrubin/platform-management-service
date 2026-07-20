import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteServiceOffering, getServiceOffering } from '../../api/client'
import type { ServiceOffering } from '../../api/types'
import { useAuthorization } from '../../auth/AuthorizationContext'
import { CodeBlock, DetailGrid, ErrorBox, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function ServiceOfferingDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { canMaintain } = useAuthorization()
  const [item, setItem] = useState<ServiceOffering | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getServiceOffering(id)
      .then(setItem)
      .catch((e: Error) => setError(e.message))
  }, [id])

  async function onDelete() {
    if (!confirm(`Delete service offering ${id}?`)) return
    try {
      await deleteServiceOffering(id)
      navigate('/service-offerings')
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Service offering"
        subtitle={id}
        actions={
          <div className="row gap">
            <Link className="button" to="/service-offerings">
              Back
            </Link>
            {canMaintain && (
              <>
                <Link className="button primary" to={`/service-offerings/${encodeURIComponent(id)}/edit`}>
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
              { label: 'Name', value: item.name },
              { label: 'Description', value: item.description },
              { label: 'Category', value: item.category },
              { label: 'Active', value: item.active ? 'Yes' : 'No' },
              { label: 'Created', value: formatDateTime(item.createdAt) },
              { label: 'Updated', value: formatDateTime(item.updatedAt) },
            ]}
          />
          <h3>Config (JSON)</h3>
          <CodeBlock value={item.config} />
        </>
      )}
    </section>
  )
}
