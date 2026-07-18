import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { MePage } from './MePage'
import { renderWithRouter } from '../test/render'
import * as api from '../api/client'

vi.mock('../api/client', () => ({
  getMe: vi.fn(),
}))

describe('MePage', () => {
  beforeEach(() => {
    vi.mocked(api.getMe).mockResolvedValue({
      subject: 'user-1',
      preferredUsername: 'alice@contoso.com',
      name: 'Alice',
      clientId: 'spa-client',
      tenantId: 'tenant',
      audience: ['api'],
      authorities: ['ROLE_System.Reader'],
      scopes: ['access_as_user'],
      roles: ['System.Reader'],
    })
  })

  it('loads and displays token claims', async () => {
    renderWithRouter(<MePage />)
    await waitFor(() => {
      expect(screen.getByText(/alice@contoso.com/)).toBeInTheDocument()
    })
    expect(screen.getByText(/System.Reader/)).toBeInTheDocument()
    expect(api.getMe).toHaveBeenCalled()
  })

  it('shows error from API', async () => {
    vi.mocked(api.getMe).mockRejectedValue(new Error('401 Unauthorized'))
    renderWithRouter(<MePage />)
    expect(await screen.findByText(/401 Unauthorized/)).toBeInTheDocument()
  })
})
