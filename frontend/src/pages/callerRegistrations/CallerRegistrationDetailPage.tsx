import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteCallerRegistration, getCallerRegistration } from '../../api/client'
import type { CallerRegistration } from '../../api/types'
import { useAuthorization } from '../../auth/AuthorizationContext'
import { DetailGrid, ErrorBox, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function CallerRegistrationDetailPage() {
  const { callerId = '' } = useParams()
  const navigate = useNavigate()
  const { canMaintain } = useAuthorization()
  const [item, setItem] = useState<CallerRegistration | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getCallerRegistration(callerId)
      .then(setItem)
      .catch((e: Error) => setError(e.message))
  }, [callerId])

  async function onDelete() {
    if (!confirm('Delete this caller registration?')) return
    try {
      await deleteCallerRegistration(callerId)
      navigate('/caller-registrations')
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Caller registration"
        actions={
          <div className="row gap">
            <Link className="button" to="/caller-registrations">
              Back
            </Link>
            {canMaintain && (
              <>
                <Link
                  className="button primary"
                  to={`/caller-registrations/${encodeURIComponent(callerId)}/edit`}
                >
                  Edit status
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
            { label: 'Caller ID', value: <code>{item.callerId}</code> },
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
