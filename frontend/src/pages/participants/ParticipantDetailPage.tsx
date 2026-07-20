import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteParticipant, getParticipant } from '../../api/client'
import type { Participant } from '../../api/types'
import { useAuthorization } from '../../auth/AuthorizationContext'
import { DetailGrid, ErrorBox, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function ParticipantDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { canMaintain } = useAuthorization()
  const [item, setItem] = useState<Participant | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getParticipant(id)
      .then(setItem)
      .catch((e: Error) => setError(e.message))
  }, [id])

  async function onDelete() {
    if (!confirm(`Delete participant ${id}?`)) return
    try {
      await deleteParticipant(id)
      navigate('/participants')
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Participant"
        subtitle={id}
        actions={
          <div className="row gap">
            <Link className="button" to="/participants">
              Back to list
            </Link>
            {canMaintain && (
              <>
                <Link className="button primary" to={`/participants/${encodeURIComponent(id)}/edit`}>
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
        <DetailGrid
          entries={[
            { label: 'ID', value: <code>{item.id}</code> },
            { label: 'Name', value: item.name },
            { label: 'Contact', value: item.contact },
            { label: 'Status', value: <span className={`badge status-${item.status}`}>{item.status}</span> },
            { label: 'Created', value: formatDateTime(item.createdAt) },
            { label: 'Updated', value: formatDateTime(item.updatedAt) },
          ]}
        />
      )}
    </section>
  )
}
