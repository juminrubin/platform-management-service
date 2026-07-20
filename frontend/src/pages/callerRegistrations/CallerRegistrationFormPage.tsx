import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  createCallerRegistration,
  getCallerRegistration,
  listParticipants,
  updateCallerRegistration,
} from '../../api/client'
import type { CallerRegistrationStatus, Participant } from '../../api/types'
import { ErrorBox, Field, Loading, PageHeader } from '../../components/ui'

export function CallerRegistrationFormPage() {
  const { callerId } = useParams()
  const isEdit = Boolean(callerId)
  const navigate = useNavigate()

  const [participants, setParticipants] = useState<Participant[]>([])
  const [participantId, setParticipantId] = useState('')
  const [callerIdValue, setCallerIdValue] = useState('')
  const [status, setStatus] = useState<CallerRegistrationStatus>('ACTIVE')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listParticipants()
      .then(setParticipants)
      .catch((e: Error) => setError(e.message))

    if (!callerId) {
      setLoading(false)
      return
    }
    getCallerRegistration(callerId)
      .then((c) => {
        setParticipantId(c.participantId)
        setCallerIdValue(c.callerId)
        setStatus(c.status)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [callerId])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      if (isEdit && callerId) {
        await updateCallerRegistration(callerId, { status })
        navigate(`/caller-registrations/${encodeURIComponent(callerId)}`)
      } else {
        const created = await createCallerRegistration({
          participantId: participantId.trim(),
          callerId: callerIdValue.trim(),
          status,
        })
        navigate(`/caller-registrations/${encodeURIComponent(created.callerId)}`)
      }
    } catch (err) {
      setError((err as Error).message)
    }
  }

  if (loading) {
    return (
      <section className="card">
        <Loading />
      </section>
    )
  }

  return (
    <section className="card">
      <PageHeader
        title={isEdit ? 'Edit caller registration' : 'Register caller'}
        subtitle={isEdit ? 'Only status can be updated via the API.' : undefined}
        actions={
          <Link
            className="button"
            to={
              isEdit && callerId
                ? `/caller-registrations/${encodeURIComponent(callerId)}`
                : '/caller-registrations'
            }
          >
            Cancel
          </Link>
        }
      />
      <ErrorBox error={error} />
      <form className="form" onSubmit={(e) => void onSubmit(e)}>
        <Field label="Participant" hint="Billing group for this caller">
          <select
            required
            disabled={isEdit}
            value={participantId}
            onChange={(e) => setParticipantId(e.target.value)}
          >
            <option value="">Select…</option>
            {participants.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.id})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Caller ID" hint="Unique principal: email, Entra client id, or managed identity object id">
          <input
            required
            maxLength={255}
            disabled={isEdit}
            value={callerIdValue}
            onChange={(e) => setCallerIdValue(e.target.value)}
          />
        </Field>
        <Field label="Status">
          <select value={status} onChange={(e) => setStatus(e.target.value as CallerRegistrationStatus)}>
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="REVOKED">REVOKED</option>
          </select>
        </Field>
        <button type="submit" className="primary">
          {isEdit ? 'Save status' : 'Create'}
        </button>
      </form>
    </section>
  )
}
