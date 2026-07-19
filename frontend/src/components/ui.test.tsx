import type { FormEvent } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import {
  CodeBlock,
  DetailGrid,
  EmptyState,
  ErrorBox,
  Field,
  FilterBar,
  Loading,
  NavLink,
  PageHeader,
  formatDateTime,
} from './ui'

describe('shared UI helpers', () => {
  it('renders ErrorBox only when error is set', () => {
    const { rerender } = render(<ErrorBox error={null} />)
    expect(screen.queryByText(/boom/i)).not.toBeInTheDocument()
    rerender(<ErrorBox error="boom" />)
    expect(screen.getByText('boom')).toBeInTheDocument()
  })

  it('renders EmptyState message', () => {
    render(<EmptyState message="Nothing here" />)
    expect(screen.getByText('Nothing here')).toBeInTheDocument()
  })

  it('renders Loading with default and custom labels', () => {
    const { rerender } = render(<Loading />)
    expect(screen.getByText('Loading…')).toBeInTheDocument()
    rerender(<Loading label="Fetching…" />)
    expect(screen.getByText('Fetching…')).toBeInTheDocument()
  })

  it('renders PageHeader with subtitle and actions', () => {
    render(<PageHeader title="Title" subtitle="Sub" actions={<button type="button">Act</button>} />)
    expect(screen.getByRole('heading', { name: 'Title' })).toBeInTheDocument()
    expect(screen.getByText('Sub')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Act' })).toBeInTheDocument()
  })

  it('renders Field with optional hint', () => {
    render(
      <Field label="Name" hint="Required">
        <input />
      </Field>,
    )
    expect(screen.getByText('Name')).toBeInTheDocument()
    expect(screen.getByText('Required')).toBeInTheDocument()
  })

  it('FilterBar submits and resets', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn((e: FormEvent) => e.preventDefault())
    const onReset = vi.fn()
    render(
      <FilterBar onSubmit={onSubmit} onReset={onReset}>
        <input aria-label="q" />
      </FilterBar>,
    )
    await user.click(screen.getByRole('button', { name: /apply filters/i }))
    expect(onSubmit).toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: /reset/i }))
    expect(onReset).toHaveBeenCalled()
  })

  it('renders DetailGrid labels and values', () => {
    render(
      <DetailGrid
        entries={[
          { label: 'Name', value: 'Acme' },
          { label: 'Empty', value: null },
        ]}
      />,
    )
    expect(screen.getByText('Name')).toBeInTheDocument()
    expect(screen.getByText('Acme')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('renders NavLink', () => {
    render(
      <MemoryRouter>
        <NavLink to="/participants">Participants</NavLink>
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: 'Participants' })).toHaveAttribute('href', '/participants')
  })

  it('pretty-prints JSON in CodeBlock and keeps invalid JSON raw', () => {
    const { rerender } = render(<CodeBlock value='{"a":1}' />)
    expect(screen.getByText(/"a": 1/)).toBeInTheDocument()
    rerender(<CodeBlock value="not-json" />)
    expect(screen.getByText('not-json')).toBeInTheDocument()
  })

  it('formats ISO datetimes and falls back for invalid input', () => {
    expect(formatDateTime('2024-06-01T12:00:00.000Z')).toContain('2024-06-01')
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
    // Invalid Date still produces a string from toISOString path or the catch returns input.
    // Force catch by patching Date temporarily.
    const OriginalDate = globalThis.Date
    class ThrowingDate {
      constructor() {
        throw new Error('bad date')
      }
      static now() {
        return OriginalDate.now()
      }
    }
    // @ts-expect-error intentional override for catch branch
    globalThis.Date = ThrowingDate
    try {
      expect(formatDateTime('garbage')).toBe('garbage')
    } finally {
      globalThis.Date = OriginalDate
    }
  })
})
