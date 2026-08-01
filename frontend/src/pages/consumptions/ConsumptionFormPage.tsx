import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createConsumption, listCallerRegistrations, listServiceOfferings } from '../../api/client'
import type { CallerRegistration, ServiceOffering } from '../../api/types'
import { ErrorBox, Field, Loading, PageHeader } from '../../components/ui'

export function ConsumptionFormPage() {
  const navigate = useNavigate()
  const [callers, setCallers] = useState<CallerRegistration[]>([])
  const [offerings, setOfferings] = useState<ServiceOffering[]>([])
  const [callerId, setCallerId] = useState('')
  const [serviceOfferingId, setServiceOfferingId] = useState('')
  const [sourceRefId, setSourceRefId] = useState<string>(() => crypto.randomUUID())
  const [consumptionData, setConsumptionData] = useState(
    '{\n  "endpoint_url": "",\n  "input_token": 0,\n  "output_token": 0,\n  "cache_token": 0\n}',
  )
  const [capturedAt, setCapturedAt] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([listCallerRegistrations(), listServiceOfferings()])
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
      let capturedAtIso: string | null = null
      if (capturedAt) {
        capturedAtIso = new Date(capturedAt).toISOString()
      }
      const created = await createConsumption({
        callerId,
        serviceOfferingId,
        sourceRefId: sourceRefId.trim() || null,
        consumptionData: consumptionData.trim() || '{}',
        capturedAt: capturedAtIso,
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
        <Field label="Caller ID">
          <select required value={callerId} onChange={(e) => setCallerId(e.target.value)}>
            <option value="">Select…</option>
            {callers.map((c) => (
              <option key={c.callerId} value={c.callerId}>
                {c.callerId} — {c.participantName} ({c.participantId})
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
        <Field
          label="Source reference ID"
          hint="Unique Source Reference Identification from the reporter (e.g. request UUID). Used for idempotent registration."
        >
          <input
            className="mono"
            value={sourceRefId}
            onChange={(e) => setSourceRefId(e.target.value)}
            placeholder="e.g. request UUID"
            maxLength={255}
          />
        </Field>
        <Field
          label="Captured at (optional)"
          hint="When the consumption was captured at runtime. Defaults to now if empty."
        >
          <input type="datetime-local" value={capturedAt} onChange={(e) => setCapturedAt(e.target.value)} />
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
