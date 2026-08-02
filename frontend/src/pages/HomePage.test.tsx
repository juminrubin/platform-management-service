import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HomePage } from './HomePage'
import { renderWithRouter } from '../test/render'

const signInWithPopup = vi.fn().mockResolvedValue({ account: { username: 'alice@contoso.com' } })
let isAuthenticated = true

vi.mock('@azure/msal-react', () => ({
  useMsal: () => ({
    instance: { loginPopup: vi.fn(), logoutPopup: vi.fn() },
    accounts: [{ username: 'alice@contoso.com', name: 'Alice' }],
  }),
  useIsAuthenticated: () => isAuthenticated,
}))

vi.mock('../auth/msalConfig', () => ({
  signInWithPopup: (...args: unknown[]) => signInWithPopup(...args),
}))

describe('HomePage', () => {
  it('shows module links when authenticated', () => {
    isAuthenticated = true
    renderWithRouter(<HomePage />)
    expect(screen.getByText('Platform Management Service')).toBeInTheDocument()
    expect(screen.getByText('Participants')).toBeInTheDocument()
    expect(screen.getByText('Consumptions')).toBeInTheDocument()
    expect(screen.getByText('Connectors')).toBeInTheDocument()
    expect(screen.getByText(/alice@contoso.com/)).toBeInTheDocument()
  })

  it('hides connectors module for non-maintainers', () => {
    isAuthenticated = true
    renderWithRouter(<HomePage />, {
      auth: {
        canMaintain: false,
        canRead: true,
        canCheckEntitlement: true,
        canRegisterConsumption: false,
        roles: ['System.Reader'],
      },
    })
    expect(screen.getByText('Participants')).toBeInTheDocument()
    expect(screen.queryByText('Connectors')).not.toBeInTheDocument()
  })

  it('prompts sign-in when unauthenticated', async () => {
    isAuthenticated = false
    const user = userEvent.setup()
    renderWithRouter(<HomePage />)
    expect(screen.queryByText('Participants')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /sign in with microsoft/i }))
    expect(signInWithPopup).toHaveBeenCalled()
  })

  it('logs failed home sign-in', async () => {
    isAuthenticated = false
    const user = userEvent.setup()
    const err = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    signInWithPopup.mockRejectedValueOnce(new Error('cancelled'))
    renderWithRouter(<HomePage />)
    await user.click(screen.getByRole('button', { name: /sign in with microsoft/i }))
    expect(err).toHaveBeenCalled()
  })
})
