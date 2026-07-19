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
    expect(screen.getByText(/alice@contoso.com/)).toBeInTheDocument()
  })

  it('prompts sign-in when unauthenticated', async () => {
    isAuthenticated = false
    const user = userEvent.setup()
    renderWithRouter(<HomePage />)
    expect(screen.queryByText('Participants')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /sign in with microsoft/i }))
    expect(signInWithPopup).toHaveBeenCalled()
  })
})
