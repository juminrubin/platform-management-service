import type { ReactElement, ReactNode } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import {
  AuthorizationProvider,
  type AuthorizationState,
} from '../auth/AuthorizationContext'
import { maintainerCapabilities } from '../auth/capabilities'

type Options = Omit<RenderOptions, 'wrapper'> & {
  route?: string
  initialEntries?: string[]
  /** Override authorization capabilities (defaults to System.Maintainer). */
  auth?: Partial<AuthorizationState>
}

export function renderWithRouter(
  ui: ReactElement,
  { route = '/', initialEntries, auth, ...options }: Options = {},
) {
  const entries = initialEntries ?? [route]
  const authValue: Partial<AuthorizationState> = {
    ...maintainerCapabilities,
    loading: false,
    roles: ['System.Maintainer'],
    ...auth,
  }

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={entries}>
        <AuthorizationProvider value={authValue}>{children}</AuthorizationProvider>
      </MemoryRouter>
    )
  }
  return render(ui, { wrapper: Wrapper, ...options })
}
