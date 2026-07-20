import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CallerRegistrationListPage } from './CallerRegistrationListPage'
import { CallerRegistrationDetailPage } from './CallerRegistrationDetailPage'
import { CallerRegistrationFormPage } from './CallerRegistrationFormPage'
import * as api from '../../api/client'
import { callerRegistrationActive, participantActive } from '../../test/fixtures'
import { renderWithRouter } from '../../test/render'

vi.mock('../../api/client', () => ({
  listCallerRegistrations: vi.fn(),
  getCallerRegistration: vi.fn(),
  createCallerRegistration: vi.fn(),
  updateCallerRegistration: vi.fn(),
  deleteCallerRegistration: vi.fn(),
  listParticipants: vi.fn(),
}))

describe('Caller registration pages', () => {
  beforeEach(() => {
    vi.mocked(api.listCallerRegistrations).mockResolvedValue([callerRegistrationActive])
    vi.mocked(api.getCallerRegistration).mockResolvedValue(callerRegistrationActive)
    vi.mocked(api.listParticipants).mockResolvedValue([participantActive])
    vi.mocked(api.createCallerRegistration).mockResolvedValue(callerRegistrationActive)
    vi.mocked(api.updateCallerRegistration).mockResolvedValue({
      ...callerRegistrationActive,
      status: 'INACTIVE',
    })
  })

  it('lists caller registrations', async () => {
    renderWithRouter(<CallerRegistrationListPage />)
    expect(await screen.findByText('alice@acme.example')).toBeInTheDocument()
    expect(screen.getByText(/Acme Corporation/)).toBeInTheDocument()
  })

  it('filters by participant and status via API', async () => {
    const user = userEvent.setup()
    renderWithRouter(<CallerRegistrationListPage />)
    await screen.findByText('alice@acme.example')

    await user.type(screen.getByLabelText(/Participant ID/), 'acme-corp')
    await user.selectOptions(screen.getByLabelText(/Status \(API\)/), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: /apply/i }))

    await waitFor(() => {
      expect(api.listCallerRegistrations).toHaveBeenLastCalledWith(
        expect.objectContaining({ participantId: 'acme-corp', status: 'ACTIVE' }),
      )
    })
  })

  it('shows detail', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/caller-registrations/:callerId" element={<CallerRegistrationDetailPage />} />
      </Routes>,
      { route: `/caller-registrations/${encodeURIComponent(callerRegistrationActive.callerId)}` },
    )
    expect(await screen.findByText('alice@acme.example')).toBeInTheDocument()
    expect(screen.getByText(/Acme Corporation/)).toBeInTheDocument()
  })

  it('creates a registration', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/caller-registrations/new" element={<CallerRegistrationFormPage />} />
      </Routes>,
      { route: '/caller-registrations/new' },
    )
    await screen.findByLabelText(/^Participant/)
    await user.selectOptions(screen.getByLabelText(/^Participant/), participantActive.id)
    await user.type(screen.getByLabelText(/^Caller ID/), 'new@acme.example')
    await user.click(screen.getByRole('button', { name: /create/i }))

    await waitFor(() => {
      expect(api.createCallerRegistration).toHaveBeenCalledWith(
        expect.objectContaining({
          participantId: 'acme-corp',
          callerId: 'new@acme.example',
        }),
      )
    })
  })

  it('updates status on edit', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/caller-registrations/:callerId/edit" element={<CallerRegistrationFormPage />} />
      </Routes>,
      {
        route: `/caller-registrations/${encodeURIComponent(callerRegistrationActive.callerId)}/edit`,
      },
    )
    await screen.findByDisplayValue('alice@acme.example')
    await user.selectOptions(screen.getByLabelText(/^Status/), 'INACTIVE')
    await user.click(screen.getByRole('button', { name: /save status/i }))

    await waitFor(() => {
      expect(api.updateCallerRegistration).toHaveBeenCalledWith(
        callerRegistrationActive.callerId,
        { status: 'INACTIVE' },
      )
    })
  })

  it('filters client-side search and supports reset', async () => {
    const user = userEvent.setup()
    renderWithRouter(<CallerRegistrationListPage />)
    await screen.findByText('alice@acme.example')
    await user.type(screen.getByLabelText(/Search caller/), 'zzz-missing')
    expect(screen.queryByText('alice@acme.example')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /reset/i }))
    expect(await screen.findByText('alice@acme.example')).toBeInTheDocument()
  })

  it('deletes from list after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteCallerRegistration).mockResolvedValue(undefined)
    renderWithRouter(<CallerRegistrationListPage />)
    await screen.findByText('alice@acme.example')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteCallerRegistration).toHaveBeenCalledWith(callerRegistrationActive.callerId)
    })
  })

  it('hides write actions for System.Reader', async () => {
    renderWithRouter(<CallerRegistrationListPage />, {
      auth: {
        canMaintain: false,
        canRead: true,
        canCheckEntitlement: true,
        canRegisterConsumption: false,
        roles: ['System.Reader'],
      },
    })
    await screen.findByText('alice@acme.example')
    expect(screen.queryByRole('link', { name: /register caller/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^view$/i })).toBeInTheDocument()
  })

  it('deletes from detail after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteCallerRegistration).mockResolvedValue(undefined)
    renderWithRouter(
      <Routes>
        <Route path="/caller-registrations/:callerId" element={<CallerRegistrationDetailPage />} />
        <Route path="/caller-registrations" element={<div>list page</div>} />
      </Routes>,
      { route: `/caller-registrations/${encodeURIComponent(callerRegistrationActive.callerId)}` },
    )
    await screen.findByText('alice@acme.example')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteCallerRegistration).toHaveBeenCalledWith(callerRegistrationActive.callerId)
    })
    expect(await screen.findByText('list page')).toBeInTheDocument()
  })

  it('shows detail load error', async () => {
    vi.mocked(api.getCallerRegistration).mockRejectedValue(new Error('404 missing'))
    renderWithRouter(
      <Routes>
        <Route path="/caller-registrations/:callerId" element={<CallerRegistrationDetailPage />} />
      </Routes>,
      { route: `/caller-registrations/${encodeURIComponent(callerRegistrationActive.callerId)}` },
    )
    expect(await screen.findByText(/404 missing/)).toBeInTheDocument()
  })

  it('shows list load error', async () => {
    vi.mocked(api.listCallerRegistrations).mockRejectedValue(new Error('list failed'))
    renderWithRouter(<CallerRegistrationListPage />)
    expect(await screen.findByText(/list failed/)).toBeInTheDocument()
  })
})
