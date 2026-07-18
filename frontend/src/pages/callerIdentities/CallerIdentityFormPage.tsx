import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  createCallerIdentity,
  getCallerIdentity,
  listParticipants,
  updateCallerIdentity,
} from '../../api/client'
import type { CallerIdentityStatus, Participant } from '../../api/types'
import { ErrorBox, Field, Loading, PageHeader } from '../../components/ui'

export function CallerIdentityFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const [participants, setParticipants] = useState<Participant[]>([])
  const [participantId, setParticipantId] = useState('')
  const [callerIdentity, setCallerIdentity] = useState('')
  const [status, setStatus] = useState<CallerIdentityStatus>('ACTIVE')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listParticipants()
      .then(setParticipants)
      .catch((e: Error) => setError(e.message))

    if (!id) {
      setLoading(false)
      return
    }
    getCallerIdentity(id)
      .then((c) => {
        setParticipantId(c.participantId)
        setCallerIdentity(c.callerIdentity)
        setStatus(c.status)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      if (isEdit && id) {
        await updateCallerIdentity(id, { status })
        navigate(`/caller-identities/${id}`)
      } else {
        const created = await createCallerIdentity({
          participantId: participantId.trim(),
          callerIdentity: callerIdentity.trim(),
          status,
        })
        navigate(`/caller-identities/${created.id}`)
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
        title={isEdit ? 'Edit caller identity' : 'Register caller identity'}
        subtitle={isEdit ? 'Only status can be updated via the API.' : undefined}
        actions={
          <Link className="button" to={isEdit && id ? `/caller-identities/${id}` : '/caller-identities'}>
            Cancel
          </Link>
        }
      />
      <ErrorBox error={error} />
      <form className="form" onSubmit={(e) => void onSubmit(e)}>
        <Field label="Participant">
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
        <Field label="Caller identity" hint="Email, Entra client id, or managed identity object id">
          <input
            required
            maxLength={255}
            disabled={isEdit}
            value={callerIdentity}
            onChange={(e) => setCallerIdentity(e.target.value)}
          />
        </Field>
        <Field label="Status">
          <select value={status} onChange={(e) => setStatus(e.target.value as CallerIdentityStatus)}>
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
