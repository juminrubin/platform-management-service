import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { CallerIdentityListPage } from './CallerIdentityListPage'
import { CallerIdentityDetailPage } from './CallerIdentityDetailPage'
import { CallerIdentityFormPage } from './CallerIdentityFormPage'
import { renderWithRouter } from '../../test/render'
import { callerIdentityActive, participantActive } from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  listCallerIdentities: vi.fn(),
  getCallerIdentity: vi.fn(),
  createCallerIdentity: vi.fn(),
  updateCallerIdentity: vi.fn(),
  deleteCallerIdentity: vi.fn(),
  listParticipants: vi.fn(),
}))

describe('Caller identity pages', () => {
  beforeEach(() => {
    vi.mocked(api.listCallerIdentities).mockResolvedValue([callerIdentityActive])
    vi.mocked(api.getCallerIdentity).mockResolvedValue(callerIdentityActive)
    vi.mocked(api.listParticipants).mockResolvedValue([participantActive])
    vi.mocked(api.createCallerIdentity).mockResolvedValue(callerIdentityActive)
    vi.mocked(api.updateCallerIdentity).mockResolvedValue({
      ...callerIdentityActive,
      status: 'INACTIVE',
    })
  })

  it('lists caller identities', async () => {
    renderWithRouter(<CallerIdentityListPage />)
    expect(await screen.findByText('alice@acme.example')).toBeInTheDocument()
    expect(screen.getByText(/Acme Corporation/)).toBeInTheDocument()
  })

  it('filters list by API participant id', async () => {
    const user = userEvent.setup()
    renderWithRouter(<CallerIdentityListPage />)
    await screen.findByText('alice@acme.example')
    await user.type(screen.getByPlaceholderText('acme-corp'), 'acme-corp')
    await user.click(screen.getByRole('button', { name: /apply filters/i }))
    await waitFor(() => {
      expect(api.listCallerIdentities).toHaveBeenLastCalledWith(
        expect.objectContaining({ participantId: 'acme-corp' }),
      )
    })
  })

  it('shows detail', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/:id" element={<CallerIdentityDetailPage />} />
      </Routes>,
      { route: `/caller-identities/${callerIdentityActive.id}` },
    )
    expect(await screen.findByText('alice@acme.example')).toBeInTheDocument()
    expect(api.getCallerIdentity).toHaveBeenCalledWith(callerIdentityActive.id)
  })

  it('creates a caller identity', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/new" element={<CallerIdentityFormPage />} />
        <Route path="/caller-identities/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/caller-identities/new' },
    )

    await screen.findByRole('option', { name: /Acme Corporation/ })
    await user.selectOptions(screen.getByLabelText(/^Participant/), 'acme-corp')
    await user.type(screen.getByLabelText(/^Caller identity/), 'bob@acme.example')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))

    await waitFor(() => {
      expect(api.createCallerIdentity).toHaveBeenCalledWith(
        expect.objectContaining({
          participantId: 'acme-corp',
          callerIdentity: 'bob@acme.example',
        }),
      )
    })
  })

  it('updates status on edit', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/:id/edit" element={<CallerIdentityFormPage />} />
        <Route path="/caller-identities/:id" element={<div>detail</div>} />
      </Routes>,
      { route: `/caller-identities/${callerIdentityActive.id}/edit` },
    )

    expect(await screen.findByDisplayValue('alice@acme.example')).toBeDisabled()
    await user.selectOptions(screen.getByLabelText(/^Status/), 'INACTIVE')
    await user.click(screen.getByRole('button', { name: /save status/i }))

    await waitFor(() => {
      expect(api.updateCallerIdentity).toHaveBeenCalledWith(callerIdentityActive.id, {
        status: 'INACTIVE',
      })
    })
  })
})
