import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createParticipant, getParticipant, updateParticipant } from '../../api/client'
import type { ParticipantStatus } from '../../api/types'
import { ErrorBox, Field, Loading, PageHeader } from '../../components/ui'

export function ParticipantFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const [formId, setFormId] = useState('')
  const [name, setName] = useState('')
  const [contact, setContact] = useState('')
  const [status, setStatus] = useState<ParticipantStatus>('ACTIVE')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(isEdit)

  useEffect(() => {
    if (!id) return
    getParticipant(id)
      .then((p) => {
        setFormId(p.id)
        setName(p.name)
        setContact(p.contact ?? '')
        setStatus(p.status)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      if (isEdit && id) {
        await updateParticipant(id, {
          name: name.trim(),
          contact: contact.trim() || null,
          status,
        })
        navigate(`/participants/${encodeURIComponent(id)}`)
      } else {
        const created = await createParticipant({
          id: formId.trim(),
          name: name.trim(),
          contact: contact.trim() || null,
          status,
        })
        navigate(`/participants/${encodeURIComponent(created.id)}`)
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
        title={isEdit ? 'Edit participant' : 'New participant'}
        actions={
          <Link className="button" to={isEdit && id ? `/participants/${encodeURIComponent(id)}` : '/participants'}>
            Cancel
          </Link>
        }
      />
      <ErrorBox error={error} />
      <form className="form" onSubmit={(e) => void onSubmit(e)}>
        <Field label="ID" hint={isEdit ? 'Immutable business key' : 'Max 40 chars, unique'}>
          <input
            required
            maxLength={40}
            value={formId}
            disabled={isEdit}
            onChange={(e) => setFormId(e.target.value)}
          />
        </Field>
        <Field label="Name">
          <input required maxLength={255} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Contact">
          <input maxLength={255} value={contact} onChange={(e) => setContact(e.target.value)} />
        </Field>
        <Field label="Status">
          <select value={status} onChange={(e) => setStatus(e.target.value as ParticipantStatus)}>
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
          </select>
        </Field>
        <div className="row gap">
          <button type="submit" className="primary">
            {isEdit ? 'Save changes' : 'Create'}
          </button>
        </div>
      </form>
    </section>
  )
}
