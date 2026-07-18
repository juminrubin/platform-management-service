import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { HomePage } from './HomePage'
import { renderWithRouter } from '../test/render'

const loginPopup = vi.fn()

vi.mock('@azure/msal-react', () => ({
  useMsal: () => ({
    instance: { loginPopup, logoutPopup: vi.fn() },
    accounts: [{ username: 'alice@contoso.com', name: 'Alice' }],
  }),
  useIsAuthenticated: () => true,
}))

vi.mock('../auth/msalConfig', () => ({
  loginRequest: { scopes: ['api://x/access_as_user'] },
}))

describe('HomePage', () => {
  it('shows module links when authenticated', () => {
    renderWithRouter(<HomePage />)
    expect(screen.getByText('Platform Management Service')).toBeInTheDocument()
    expect(screen.getByText('Participants')).toBeInTheDocument()
    expect(screen.getByText('Consumptions')).toBeInTheDocument()
    expect(screen.getByText(/alice@contoso.com/)).toBeInTheDocument()
  })
})
