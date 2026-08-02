import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { Layout } from './Layout'
import { renderWithRouter } from '../test/render'

const signInWithPopup = vi.fn().mockResolvedValue({ account: { username: 'alice@contoso.com' } })
const signOutWithPopup = vi.fn().mockResolvedValue(undefined)
let isAuthenticated = false

vi.mock('@azure/msal-react', () => ({
  useMsal: () => ({
    instance: { loginPopup: vi.fn(), logoutPopup: vi.fn(), getActiveAccount: vi.fn() },
    accounts: [{ username: 'alice@contoso.com', name: 'Alice' }],
  }),
  useIsAuthenticated: () => isAuthenticated,
}))

vi.mock('../auth/msalConfig', () => ({
  signInWithPopup: (...args: unknown[]) => signInWithPopup(...args),
  signOutWithPopup: (...args: unknown[]) => signOutWithPopup(...args),
}))

function renderLayout() {
  return renderWithRouter(
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<div>Home content</div>} />
        <Route path="/participants" element={<div>Participants content</div>} />
      </Route>
    </Routes>,
    { route: '/' },
  )
}

describe('Layout', () => {
  it('shows sign-in when unauthenticated and hides module nav', async () => {
    isAuthenticated = false
    const user = userEvent.setup()
    renderLayout()

    expect(screen.getByText('Platform Management Service')).toBeInTheDocument()
    expect(screen.getByText('Home content')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Participants' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(signInWithPopup).toHaveBeenCalled()
  })

  it('shows nav links and sign-out when authenticated', async () => {
    isAuthenticated = true
    const user = userEvent.setup()
    renderLayout()

    expect(screen.getByRole('link', { name: 'Participants' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Consumptions' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Connectors' })).toBeInTheDocument()
    expect(screen.getByText('alice@contoso.com')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /sign out/i }))
    expect(signOutWithPopup).toHaveBeenCalled()
  })

  it('hides connectors nav for non-maintainers', () => {
    isAuthenticated = true
    renderWithRouter(
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<div>Home content</div>} />
        </Route>
      </Routes>,
      {
        route: '/',
        auth: {
          canMaintain: false,
          canRead: true,
          canCheckEntitlement: true,
          canRegisterConsumption: false,
          roles: ['System.Reader'],
        },
      },
    )
    expect(screen.getByRole('link', { name: 'Participants' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Connectors' })).not.toBeInTheDocument()
  })

  it('logs sign-in failures without crashing', async () => {
    isAuthenticated = false
    const user = userEvent.setup()
    const err = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    signInWithPopup.mockRejectedValueOnce(new Error('popup blocked'))
    renderLayout()
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(err).toHaveBeenCalled()
  })

  it('logs sign-out failures without crashing', async () => {
    isAuthenticated = true
    const user = userEvent.setup()
    const err = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    signOutWithPopup.mockRejectedValueOnce(new Error('logout failed'))
    renderLayout()
    await user.click(screen.getByRole('button', { name: /sign out/i }))
    expect(err).toHaveBeenCalled()
  })
})
