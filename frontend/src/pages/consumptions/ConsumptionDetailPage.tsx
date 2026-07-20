import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteConsumption, getConsumption } from '../../api/client'
import type { Consumption } from '../../api/types'
import { CodeBlock, DetailGrid, ErrorBox, Loading, PageHeader, formatDateTime } from '../../components/ui'

export function ConsumptionDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [item, setItem] = useState<Consumption | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getConsumption(id)
      .then(setItem)
      .catch((e: Error) => setError(e.message))
  }, [id])

  async function onDelete() {
    if (!confirm('Delete this consumption record?')) return
    try {
      await deleteConsumption(id)
      navigate('/consumptions')
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Consumption record"
        subtitle="API has no update endpoint — create a new record to correct data."
        actions={
          <div className="row gap">
            <Link className="button" to="/consumptions">
              Back
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
        <>
          <DetailGrid
            entries={[
              { label: 'ID', value: <code>{item.id}</code> },
              { label: 'Event time', value: formatDateTime(item.createdAt) },
              {
                label: 'Caller ID',
                value: (
                  <Link to={`/caller-registrations/${encodeURIComponent(item.callerId)}`}>
                    <code>{item.callerId}</code>
                  </Link>
                ),
              },
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
            ]}
          />
          <h3>Consumption data (JSON)</h3>
          <CodeBlock value={item.consumptionData} />
        </>
      )}
    </section>
  )
}
