import { Link } from 'react-router-dom'
import type { FormEvent, ReactNode } from 'react'

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string
  subtitle?: string
  actions?: ReactNode
}) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="row gap">{actions}</div>}
    </div>
  )
}

export function ErrorBox({ error }: { error: string | null }) {
  if (!error) return null
  return <pre className="error">{error}</pre>
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <p className="muted">{label}</p>
}

export function FilterBar({
  onSubmit,
  onReset,
  children,
}: {
  onSubmit: (e: FormEvent) => void
  onReset?: () => void
  children: ReactNode
}) {
  return (
    <form className="filter-bar" onSubmit={onSubmit}>
      <div className="filter-fields">{children}</div>
      <div className="row gap">
        <button type="submit" className="primary">
          Apply filters
        </button>
        {onReset && (
          <button type="button" onClick={onReset}>
            Reset
          </button>
        )}
      </div>
    </form>
  )
}

export function Field({
  label,
  children,
  hint,
}: {
  label: string
  children: ReactNode
  hint?: string
}) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

export function DetailGrid({
  entries,
}: {
  entries: Array<{ label: string; value: ReactNode }>
}) {
  return (
    <dl className="detail-grid">
      {entries.map((e) => (
        <div key={e.label} className="detail-row">
          <dt>{e.label}</dt>
          <dd>{e.value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  )
}

export function NavLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link className="nav-link" to={to}>
      {children}
    </Link>
  )
}

export function EmptyState({ message }: { message: string }) {
  return <p className="muted empty">{message}</p>
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Date(iso).toISOString().replace('T', ' ').replace(/\.\d{3}Z$/, ' Z')
  } catch {
    return iso
  }
}

export function CodeBlock({ value }: { value: string }) {
  let pretty = value
  try {
    pretty = JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    // keep raw
  }
  return <pre className="code">{pretty}</pre>
}
