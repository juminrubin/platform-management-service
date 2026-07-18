import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CodeBlock, DetailGrid, EmptyState, ErrorBox, formatDateTime } from './ui'

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

  it('pretty-prints JSON in CodeBlock', () => {
    render(<CodeBlock value='{"a":1}' />)
    expect(screen.getByText(/"a": 1/)).toBeInTheDocument()
  })

  it('formats ISO datetimes', () => {
    expect(formatDateTime('2024-06-01T12:00:00.000Z')).toContain('2024-06-01')
    expect(formatDateTime(null)).toBe('—')
  })
})
