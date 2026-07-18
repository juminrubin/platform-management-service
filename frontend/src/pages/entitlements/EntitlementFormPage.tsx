import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  createEntitlement,
  getEntitlement,
  listParticipants,
  listServiceOfferings,
  updateEntitlement,
} from '../../api/client'
import type { EntitlementStatus, Participant, ServiceOffering } from '../../api/types'
import { ErrorBox, Field, Loading, PageHeader } from '../../components/ui'

export function EntitlementFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const [participants, setParticipants] = useState<Participant[]>([])
  const [offerings, setOfferings] = useState<ServiceOffering[]>([])
  const [participantId, setParticipantId] = useState('')
  const [serviceOfferingId, setServiceOfferingId] = useState('')
  const [status, setStatus] = useState<EntitlementStatus>('PENDING')
  const [validFrom, setValidFrom] = useState('')
  const [validTo, setValidTo] = useState('')
  const [config, setConfig] = useState('{}')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([listParticipants(), listServiceOfferings()])
      .then(([p, o]) => {
        setParticipants(p)
        setOfferings(o)
      })
      .catch((e: Error) => setError(e.message))

    if (!id) {
      setValidFrom(new Date().toISOString().slice(0, 10))
      setLoading(false)
      return
    }
    getEntitlement(id)
      .then((row) => {
        setParticipantId(row.participantId)
        setServiceOfferingId(row.serviceOfferingId)
        setStatus(row.status)
        setValidFrom(row.validFrom)
        setValidTo(row.validTo ?? '')
        setConfig(row.config)
        setNotes(row.notes ?? '')
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      JSON.parse(config || '{}')
    } catch {
      setError('Config must be valid JSON')
      return
    }
    try {
      if (isEdit && id) {
        await updateEntitlement(id, {
          status,
          validFrom,
          validTo: validTo || null,
          config: config.trim() || '{}',
          notes: notes.trim() || null,
        })
        navigate(`/entitlements/${id}`)
      } else {
        const created = await createEntitlement({
          participantId,
          serviceOfferingId,
          status,
          validFrom,
          validTo: validTo || null,
          config: config.trim() || '{}',
          notes: notes.trim() || null,
        })
        navigate(`/entitlements/${created.id}`)
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
        title={isEdit ? 'Edit entitlement' : 'New entitlement'}
        actions={
          <Link className="button" to={isEdit && id ? `/entitlements/${id}` : '/entitlements'}>
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
        <Field label="Service offering">
          <select
            required
            disabled={isEdit}
            value={serviceOfferingId}
            onChange={(e) => setServiceOfferingId(e.target.value)}
          >
            <option value="">Select…</option>
            {offerings.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name} ({o.id})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Status">
          <select value={status} onChange={(e) => setStatus(e.target.value as EntitlementStatus)}>
            <option value="PENDING">PENDING</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="EXPIRED">EXPIRED</option>
            <option value="REVOKED">REVOKED</option>
          </select>
        </Field>
        <div className="form-row-2">
          <Field label="Valid from">
            <input type="date" required value={validFrom} onChange={(e) => setValidFrom(e.target.value)} />
          </Field>
          <Field label="Valid to" hint="Leave empty for open-ended">
            <input type="date" value={validTo} onChange={(e) => setValidTo(e.target.value)} />
          </Field>
        </div>
        <Field label="Config (JSON)">
          <textarea className="mono" rows={6} required value={config} onChange={(e) => setConfig(e.target.value)} />
        </Field>
        <Field label="Notes">
          <textarea rows={2} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <button type="submit" className="primary">
          {isEdit ? 'Save changes' : 'Create'}
        </button>
      </form>
    </section>
  )
}
