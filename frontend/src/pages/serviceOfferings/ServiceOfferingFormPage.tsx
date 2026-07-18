import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createServiceOffering, getServiceOffering, updateServiceOffering } from '../../api/client'
import { ErrorBox, Field, Loading, PageHeader } from '../../components/ui'

export function ServiceOfferingFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const [formId, setFormId] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('LLM')
  const [config, setConfig] = useState('{}')
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(isEdit)

  useEffect(() => {
    if (!id) return
    getServiceOffering(id)
      .then((o) => {
        setFormId(o.id)
        setName(o.name)
        setDescription(o.description ?? '')
        setCategory(o.category)
        setConfig(o.config)
        setActive(o.active)
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
        await updateServiceOffering(id, {
          name: name.trim(),
          description: description.trim() || null,
          category: category.trim(),
          config: config.trim() || '{}',
          active,
        })
        navigate(`/service-offerings/${encodeURIComponent(id)}`)
      } else {
        const created = await createServiceOffering({
          id: formId.trim(),
          name: name.trim(),
          description: description.trim() || null,
          category: category.trim(),
          config: config.trim() || '{}',
          active,
        })
        navigate(`/service-offerings/${encodeURIComponent(created.id)}`)
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
        title={isEdit ? 'Edit service offering' : 'New service offering'}
        actions={
          <Link
            className="button"
            to={isEdit && id ? `/service-offerings/${encodeURIComponent(id)}` : '/service-offerings'}
          >
            Cancel
          </Link>
        }
      />
      <ErrorBox error={error} />
      <form className="form" onSubmit={(e) => void onSubmit(e)}>
        <Field label="ID" hint="Business key, e.g. gpt-5.1">
          <input
            required
            maxLength={100}
            disabled={isEdit}
            value={formId}
            onChange={(e) => setFormId(e.target.value)}
          />
        </Field>
        <Field label="Name">
          <input required maxLength={255} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Description">
          <textarea rows={3} maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <Field label="Category">
          <input required maxLength={64} value={category} onChange={(e) => setCategory(e.target.value)} />
        </Field>
        <Field label="Config (JSON)">
          <textarea
            className="mono"
            rows={8}
            required
            value={config}
            onChange={(e) => setConfig(e.target.value)}
          />
        </Field>
        <Field label="Active">
          <select value={active ? 'true' : 'false'} onChange={(e) => setActive(e.target.value === 'true')}>
            <option value="true">Yes</option>
            <option value="false">No</option>
          </select>
        </Field>
        <button type="submit" className="primary">
          {isEdit ? 'Save changes' : 'Create'}
        </button>
      </form>
    </section>
  )
}
