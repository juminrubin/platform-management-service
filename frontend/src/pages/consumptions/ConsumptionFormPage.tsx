import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createConsumption, listCallerIdentities, listServiceOfferings } from '../../api/client'
import type { CallerIdentity, ServiceOffering } from '../../api/types'
import { ErrorBox, Field, Loading, PageHeader } from '../../components/ui'

export function ConsumptionFormPage() {
  const navigate = useNavigate()
  const [callers, setCallers] = useState<CallerIdentity[]>([])
  const [offerings, setOfferings] = useState<ServiceOffering[]>([])
  const [participantCallerIdentityId, setParticipantCallerIdentityId] = useState('')
  const [serviceOfferingId, setServiceOfferingId] = useState('')
  const [consumptionData, setConsumptionData] = useState(
    '{\n  "endpoint_url": "",\n  "input_token": 0,\n  "output_token": 0,\n  "cache_token": 0\n}',
  )
  const [consumedAt, setConsumedAt] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([listCallerIdentities(), listServiceOfferings()])
      .then(([c, o]) => {
        setCallers(c)
        setOfferings(o)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      JSON.parse(consumptionData || '{}')
    } catch {
      setError('consumptionData must be valid JSON')
      return
    }
    try {
      let consumedAtIso: string | null = null
      if (consumedAt) {
        consumedAtIso = new Date(consumedAt).toISOString()
      }
      const created = await createConsumption({
        participantCallerIdentityId,
        serviceOfferingId,
        consumptionData: consumptionData.trim() || '{}',
        consumedAt: consumedAtIso,
      })
      navigate(`/consumptions/${created.id}`)
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
        title="Record consumption"
        subtitle="Requires System.Maintainer or Consumption.Registrator. No update API — create only."
        actions={
          <Link className="button" to="/consumptions">
            Cancel
          </Link>
        }
      />
      <ErrorBox error={error} />
      <form className="form" onSubmit={(e) => void onSubmit(e)}>
        <Field label="Caller identity">
          <select
            required
            value={participantCallerIdentityId}
            onChange={(e) => setParticipantCallerIdentityId(e.target.value)}
          >
            <option value="">Select…</option>
            {callers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.callerIdentity} — {c.participantName} ({c.participantId})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Service offering">
          <select required value={serviceOfferingId} onChange={(e) => setServiceOfferingId(e.target.value)}>
            <option value="">Select…</option>
            {offerings.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name} ({o.id})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Event time (optional)" hint="Defaults to now if empty.">
          <input type="datetime-local" value={consumedAt} onChange={(e) => setConsumedAt(e.target.value)} />
        </Field>
        <Field label="Consumption data (JSON)">
          <textarea
            className="mono"
            rows={10}
            required
            value={consumptionData}
            onChange={(e) => setConsumptionData(e.target.value)}
          />
        </Field>
        <button type="submit" className="primary">
          Create
        </button>
      </form>
    </section>
  )
}
