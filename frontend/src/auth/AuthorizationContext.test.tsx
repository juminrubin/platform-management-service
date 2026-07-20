import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  AuthorizationProvider,
  useAuthorization,
} from './AuthorizationContext'
import * as api from '../api/client'

let isAuthenticated = true

vi.mock('@azure/msal-react', () => ({
  useIsAuthenticated: () => isAuthenticated,
}))

vi.mock('../api/client', () => ({
  getMe: vi.fn(),
}))

function Probe() {
  const auth = useAuthorization()
  return (
    <div>
      <span data-testid="loading">{String(auth.loading)}</span>
      <span data-testid="canMaintain">{String(auth.canMaintain)}</span>
      <span data-testid="canRead">{String(auth.canRead)}</span>
      <span data-testid="roles">{auth.roles.join(',')}</span>
      <span data-testid="error">{auth.error ?? ''}</span>
      <span data-testid="user">{auth.user?.preferredUsername ?? ''}</span>
      <button type="button" onClick={() => void auth.refresh()}>
        refresh
      </button>
    </div>
  )
}

describe('AuthorizationProvider (live)', () => {
  beforeEach(() => {
    isAuthenticated = true
    vi.mocked(api.getMe).mockReset()
  })

  it('loads roles from /auth/me when authenticated', async () => {
    vi.mocked(api.getMe).mockResolvedValue({
      subject: 'sub',
      preferredUsername: 'alice@contoso.com',
      name: 'Alice',
      clientId: null,
      tenantId: 't',
      audience: [],
      authorities: ['ROLE_System.Maintainer'],
      scopes: [],
      roles: ['System.Maintainer'],
    })

    render(
      <AuthorizationProvider>
        <Probe />
      </AuthorizationProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('canMaintain')).toHaveTextContent('true')
    })
    expect(screen.getByTestId('canRead')).toHaveTextContent('true')
    expect(screen.getByTestId('roles')).toHaveTextContent('System.Maintainer')
    expect(screen.getByTestId('user')).toHaveTextContent('alice@contoso.com')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
    expect(api.getMe).toHaveBeenCalled()
  })

  it('clears capabilities when not authenticated', async () => {
    isAuthenticated = false
    render(
      <AuthorizationProvider>
        <Probe />
      </AuthorizationProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('canMaintain')).toHaveTextContent('false')
    })
    expect(api.getMe).not.toHaveBeenCalled()
    expect(screen.getByTestId('roles')).toHaveTextContent('')
  })

  it('records error and clears roles when getMe fails', async () => {
    vi.mocked(api.getMe).mockRejectedValue(new Error('401 Unauthorized'))
    render(
      <AuthorizationProvider>
        <Probe />
      </AuthorizationProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent('401 Unauthorized')
    })
    expect(screen.getByTestId('canMaintain')).toHaveTextContent('false')
    expect(screen.getByTestId('user')).toHaveTextContent('')
  })

  it('refresh reloads capabilities', async () => {
    vi.mocked(api.getMe)
      .mockResolvedValueOnce({
        subject: 's',
        preferredUsername: 'r@x.com',
        name: null,
        clientId: null,
        tenantId: null,
        audience: [],
        authorities: ['ROLE_System.Reader'],
        scopes: [],
        roles: ['System.Reader'],
      })
      .mockResolvedValueOnce({
        subject: 's',
        preferredUsername: 'r@x.com',
        name: null,
        clientId: null,
        tenantId: null,
        audience: [],
        authorities: ['ROLE_System.Maintainer'],
        scopes: [],
        roles: ['System.Maintainer'],
      })

    const user = userEvent.setup()
    render(
      <AuthorizationProvider>
        <Probe />
      </AuthorizationProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('canMaintain')).toHaveTextContent('false')
    })
    await user.click(screen.getByRole('button', { name: /refresh/i }))
    await waitFor(() => {
      expect(screen.getByTestId('canMaintain')).toHaveTextContent('true')
    })
  })

  it('static override skips getMe', () => {
    render(
      <AuthorizationProvider value={{ canMaintain: true, canRead: true, roles: ['System.Maintainer'] }}>
        <Probe />
      </AuthorizationProvider>,
    )
    expect(screen.getByTestId('canMaintain')).toHaveTextContent('true')
    expect(api.getMe).not.toHaveBeenCalled()
  })
})

describe('useAuthorization', () => {
  it('throws outside provider', () => {
    expect(() => render(<Probe />)).toThrow(/AuthorizationProvider/)
  })
})
