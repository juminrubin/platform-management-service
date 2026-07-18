import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteCallerIdentity, getCallerIdentity } from '../../api/client'
import type { CallerIdentity } from '../../api/types'
import { DetailGrid, ErrorBox, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function CallerIdentityDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [item, setItem] = useState<CallerIdentity | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getCallerIdentity(id)
      .then(setItem)
      .catch((e: Error) => setError(e.message))
  }, [id])

  async function onDelete() {
    if (!confirm('Delete this caller identity?')) return
    try {
      await deleteCallerIdentity(id)
      navigate('/caller-identities')
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Caller identity"
        actions={
          <div className="row gap">
            <Link className="button" to="/caller-identities">
              Back
            </Link>
            <Link className="button primary" to={`/caller-identities/${id}/edit`}>
              Edit status
            </Link>
            <button type="button" onClick={() => void onDelete()}>
              Delete
            </button>
          </div>
        }
      />
      <ErrorBox error={error} />
      {!item && !error && <Loading />}
      {item && (
        <DetailGrid
          entries={[
            { label: 'ID', value: <code>{item.id}</code> },
            { label: 'Caller identity', value: <code>{item.callerIdentity}</code> },
            {
              label: 'Participant',
              value: (
                <Link to={`/participants/${encodeURIComponent(item.participantId)}`}>
                  {item.participantName} ({item.participantId})
                </Link>
              ),
            },
            { label: 'Status', value: <span className={`badge status-${item.status}`}>{item.status}</span> },
            { label: 'Created', value: formatDateTime(item.createdAt) },
            { label: 'Updated', value: formatDateTime(item.updatedAt) },
          ]}
        />
      )}
    </section>
  )
}
