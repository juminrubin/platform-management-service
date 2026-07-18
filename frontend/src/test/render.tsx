import type { ReactElement, ReactNode } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

type Options = Omit<RenderOptions, 'wrapper'> & {
  route?: string
  initialEntries?: string[]
}

export function renderWithRouter(
  ui: ReactElement,
  { route = '/', initialEntries, ...options }: Options = {},
) {
  const entries = initialEntries ?? [route]
  function Wrapper({ children }: { children: ReactNode }) {
    return <MemoryRouter initialEntries={entries}>{children}</MemoryRouter>
  }
  return render(ui, { wrapper: Wrapper, ...options })
}
