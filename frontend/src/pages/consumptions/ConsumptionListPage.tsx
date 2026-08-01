import type { FormEvent } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  deleteConsumption,
  listCallerRegistrations,
  listConsumptions,
  listServiceOfferings,
} from '../../api/client'
import type { CallerRegistration, Consumption, ServiceOffering } from '../../api/types'
import { useAuthorization } from '../../auth/AuthorizationContext'
import { EmptyState, ErrorBox, Field, FilterBar, Loading, PageHeader, formatDateTime } from '../../components/ui'

function previewJson(raw: string): string {
  try {
    const o = JSON.parse(raw) as Record<string, unknown>
    const parts: string[] = []
    for (const k of ['input_token', 'output_token', 'cache_token', 'endpoint_url']) {
      if (o[k] !== undefined) parts.push(`${k}=${String(o[k])}`)
    }
    return parts.length ? parts.join(', ') : raw.slice(0, 80)
  } catch {
    return raw.slice(0, 80)
  }
}

export function ConsumptionListPage() {
  const { canMaintain, canRegisterConsumption } = useAuthorization()
  const [items, setItems] = useState<Consumption[]>([])
  const [callerRegistrations, setCallerRegistrations] = useState<CallerRegistration[]>([])
  const [offerings, setOfferings] = useState<ServiceOffering[]>([])

  const [callerId, setCallerId] = useState('')
  const [serviceOfferingId, setServiceOfferingId] = useState('')
  const [participantId, setParticipantId] = useState('')
  const [callerText, setCallerText] = useState('')
  const [fromAt, setFromAt] = useState('')
  const [toAt, setToAt] = useState('')
  const [jsonQuery, setJsonQuery] = useState('')

  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listConsumptions({
        callerId: callerId || undefined,
        serviceOfferingId: serviceOfferingId || undefined,
      })
      setItems(data)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [callerId, serviceOfferingId])

  useEffect(() => {
    void load()
    listCallerRegistrations()
      .then(setCallerRegistrations)
      .catch(() => undefined)
    listServiceOfferings()
      .then(setOfferings)
      .catch(() => undefined)
  }, [load])

  const filtered = useMemo(() => {
    return items.filter((c) => {
      if (participantId && c.participantId !== participantId) return false
      if (callerText.trim()) {
        const s = callerText.toLowerCase()
        if (!c.callerId.toLowerCase().includes(s)) {
          return false
        }
      }
      if (fromAt) {
        const from = new Date(fromAt).getTime()
        if (new Date(c.capturedAt).getTime() < from) return false
      }
      if (toAt) {
        const to = new Date(toAt).getTime()
        if (new Date(c.capturedAt).getTime() > to) return false
      }
      if (jsonQuery.trim()) {
        const s = jsonQuery.toLowerCase()
        if (
          !c.consumptionData.toLowerCase().includes(s) &&
          !c.serviceOfferingId.toLowerCase().includes(s) &&
          !c.participantName.toLowerCase().includes(s) &&
          !(c.sourceRefId?.toLowerCase().includes(s) ?? false)
        ) {
          return false
        }
      }
      return true
    })
  }, [items, participantId, callerText, fromAt, toAt, jsonQuery])

  const participantOptions = useMemo(() => {
    const map = new Map<string, string>()
    items.forEach((c) => map.set(c.participantId, c.participantName))
    callerRegistrations.forEach((c) => map.set(c.participantId, c.participantName))
    return [...map.entries()].sort((a, b) => a[1].localeCompare(b[1]))
  }, [items, callerRegistrations])

  async function onDelete(rowId: string) {
    if (!confirm('Delete this consumption record?')) return
    try {
      await deleteConsumption(rowId)
      await load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <section className="card">
      <PageHeader
        title="Call consumptions"
        subtitle="Token / usage events for a registered caller against a service offering."
        actions={
          canRegisterConsumption ? (
            <Link className="button primary" to="/consumptions/new">
              Record consumption
            </Link>
          ) : undefined
        }
      />

      <FilterBar
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          void load()
        }}
        onReset={() => {
          setCallerId('')
          setServiceOfferingId('')
          setParticipantId('')
          setCallerText('')
          setFromAt('')
          setToAt('')
          setJsonQuery('')
          void listConsumptions().then(setItems)
        }}
      >
        <Field label="Caller ID (API)" hint="Unique principal of the registration">
          <select value={callerId} onChange={(e) => setCallerId(e.target.value)}>
            <option value="">All</option>
            {callerRegistrations.map((c) => (
              <option key={c.callerId} value={c.callerId}>
                {c.callerId} — {c.participantId}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Service offering (API)">
          <select value={serviceOfferingId} onChange={(e) => setServiceOfferingId(e.target.value)}>
            <option value="">All</option>
            {offerings.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name} ({o.id})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Participant (client)">
          <select value={participantId} onChange={(e) => setParticipantId(e.target.value)}>
            <option value="">All</option>
            {participantOptions.map(([pid, pname]) => (
              <option key={pid} value={pid}>
                {pname} ({pid})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Caller text (client)">
          <input
            value={callerText}
            onChange={(e) => setCallerText(e.target.value)}
            placeholder="email / client id substring"
          />
        </Field>
        <Field label="From (client, captured at)">
          <input type="datetime-local" value={fromAt} onChange={(e) => setFromAt(e.target.value)} />
        </Field>
        <Field label="To (client, captured at)">
          <input type="datetime-local" value={toAt} onChange={(e) => setToAt(e.target.value)} />
        </Field>
        <Field label="Search JSON / names (client)">
          <input
            value={jsonQuery}
            onChange={(e) => setJsonQuery(e.target.value)}
            placeholder="input_token, endpoint, participant…"
          />
        </Field>
      </FilterBar>

      <p className="muted">
        Showing <strong>{filtered.length}</strong> of <strong>{items.length}</strong> loaded records (API
        filters re-fetch; client filters refine the result set).
      </p>

      <ErrorBox error={error} />
      {loading && <Loading />}
      {!loading && filtered.length === 0 && <EmptyState message="No consumption records match." />}
      {!loading && filtered.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Captured at (UTC)</th>
              <th>Source ref</th>
              <th>Caller ID</th>
              <th>Participant</th>
              <th>Service</th>
              <th>Data preview</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((c) => (
              <tr key={c.id}>
                <td className="nowrap">{formatDateTime(c.capturedAt)}</td>
                <td>
                  <code>{c.sourceRefId ?? '—'}</code>
                </td>
                <td>
                  <code>{c.callerId}</code>
                </td>
                <td>
                  {c.participantName}
                  <br />
                  <code>{c.participantId}</code>
                </td>
                <td>
                  <code>{c.serviceOfferingId}</code>
                </td>
                <td className="preview">{previewJson(c.consumptionData)}</td>
                <td className="row gap">
                  <Link className="button" to={`/consumptions/${c.id}`}>
                    View
                  </Link>
                  {canMaintain && (
                    <button type="button" onClick={() => void onDelete(c.id)}>
                      Delete
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
