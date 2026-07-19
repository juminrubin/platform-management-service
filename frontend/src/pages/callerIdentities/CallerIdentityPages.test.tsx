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

  it('deletes from detail after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteCallerIdentity).mockResolvedValue(undefined)
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/:id" element={<CallerIdentityDetailPage />} />
        <Route path="/caller-identities" element={<div>ci list</div>} />
      </Routes>,
      { route: `/caller-identities/${callerIdentityActive.id}` },
    )
    await screen.findByText('alice@acme.example')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteCallerIdentity).toHaveBeenCalledWith(callerIdentityActive.id)
    })
    expect(await screen.findByText('ci list')).toBeInTheDocument()
  })

  it('shows detail load error', async () => {
    vi.mocked(api.getCallerIdentity).mockRejectedValue(new Error('404 gone'))
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/:id" element={<CallerIdentityDetailPage />} />
      </Routes>,
      { route: `/caller-identities/${callerIdentityActive.id}` },
    )
    expect(await screen.findByText(/404 gone/)).toBeInTheDocument()
  })

  it('shows delete error on detail', async () => {
    vi.mocked(api.deleteCallerIdentity).mockRejectedValue(new Error('delete failed'))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/:id" element={<CallerIdentityDetailPage />} />
      </Routes>,
      { route: `/caller-identities/${callerIdentityActive.id}` },
    )
    await screen.findByText('alice@acme.example')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(await screen.findByText(/delete failed/)).toBeInTheDocument()
  })

  it('filters client-side on list', async () => {
    const user = userEvent.setup()
    renderWithRouter(<CallerIdentityListPage />)
    await screen.findByText('alice@acme.example')
    await user.type(screen.getByLabelText(/Search identity/i), 'zzz-no-match')
    expect(screen.queryByText('alice@acme.example')).not.toBeInTheDocument()
    expect(screen.getByText(/No caller identities match/i)).toBeInTheDocument()
  })

  it('deletes from list', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteCallerIdentity).mockResolvedValue(undefined)
    renderWithRouter(<CallerIdentityListPage />)
    await screen.findByText('alice@acme.example')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteCallerIdentity).toHaveBeenCalledWith(callerIdentityActive.id)
    })
  })

  it('shows list load error', async () => {
    vi.mocked(api.listCallerIdentities).mockRejectedValue(new Error('list boom'))
    renderWithRouter(<CallerIdentityListPage />)
    expect(await screen.findByText(/list boom/)).toBeInTheDocument()
  })

  it('resets list filters', async () => {
    const user = userEvent.setup()
    renderWithRouter(<CallerIdentityListPage />)
    await screen.findByText('alice@acme.example')
    await user.type(screen.getByPlaceholderText('acme-corp'), 'acme-corp')
    await user.click(screen.getByRole('button', { name: /reset/i }))
    expect(screen.getByPlaceholderText('acme-corp')).toHaveValue('')
  })

  it('shows form create error', async () => {
    const user = userEvent.setup()
    vi.mocked(api.createCallerIdentity).mockRejectedValue(new Error('create failed'))
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/new" element={<CallerIdentityFormPage />} />
      </Routes>,
      { route: '/caller-identities/new' },
    )
    await screen.findByRole('option', { name: /Acme Corporation/ })
    await user.selectOptions(screen.getByLabelText(/^Participant/), 'acme-corp')
    await user.type(screen.getByLabelText(/^Caller identity/), 'bob@acme.example')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))
    expect(await screen.findByText(/create failed/)).toBeInTheDocument()
  })

  it('shows form load error for participants list', async () => {
    vi.mocked(api.listParticipants).mockRejectedValue(new Error('participants failed'))
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/new" element={<CallerIdentityFormPage />} />
      </Routes>,
      { route: '/caller-identities/new' },
    )
    expect(await screen.findByText(/participants failed/)).toBeInTheDocument()
  })

  it('shows form load error on edit', async () => {
    vi.mocked(api.getCallerIdentity).mockRejectedValue(new Error('ci missing'))
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/:id/edit" element={<CallerIdentityFormPage />} />
      </Routes>,
      { route: `/caller-identities/${callerIdentityActive.id}/edit` },
    )
    expect(await screen.findByText(/ci missing/)).toBeInTheDocument()
  })

  it('cancels detail delete when confirm is false', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderWithRouter(
      <Routes>
        <Route path="/caller-identities/:id" element={<CallerIdentityDetailPage />} />
      </Routes>,
      { route: `/caller-identities/${callerIdentityActive.id}` },
    )
    await screen.findByText('alice@acme.example')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(api.deleteCallerIdentity).not.toHaveBeenCalled()
  })

  it('shows list delete error', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteCallerIdentity).mockRejectedValue(new Error('list delete failed'))
    renderWithRouter(<CallerIdentityListPage />)
    await screen.findByText('alice@acme.example')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(await screen.findByText(/list delete failed/)).toBeInTheDocument()
  })
})
