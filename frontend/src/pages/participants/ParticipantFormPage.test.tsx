import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { ParticipantFormPage } from './ParticipantFormPage'
import { renderWithRouter } from '../../test/render'
import { participantActive } from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  getParticipant: vi.fn(),
  createParticipant: vi.fn(),
  updateParticipant: vi.fn(),
}))

describe('ParticipantFormPage', () => {
  beforeEach(() => {
    vi.mocked(api.getParticipant).mockResolvedValue(participantActive)
    vi.mocked(api.createParticipant).mockResolvedValue(participantActive)
    vi.mocked(api.updateParticipant).mockResolvedValue(participantActive)
  })

  it('creates a participant', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/participants/new" element={<ParticipantFormPage />} />
        <Route path="/participants/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/participants/new' },
    )

    await user.type(screen.getByLabelText(/^ID/), 'new-co')
    await user.type(screen.getByLabelText(/^Name/), 'New Co')
    await user.click(screen.getByRole('button', { name: /create/i }))

    await waitFor(() => {
      expect(api.createParticipant).toHaveBeenCalledWith(
        expect.objectContaining({ id: 'new-co', name: 'New Co', status: 'ACTIVE' }),
      )
    })
  })

  it('loads and updates an existing participant', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/participants/:id/edit" element={<ParticipantFormPage />} />
        <Route path="/participants/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/participants/acme-corp/edit' },
    )

    expect(await screen.findByDisplayValue('Acme Corporation')).toBeInTheDocument()
    const name = screen.getByLabelText(/^Name/)
    await user.clear(name)
    await user.type(name, 'Acme Renamed')
    await user.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => {
      expect(api.updateParticipant).toHaveBeenCalledWith(
        'acme-corp',
        expect.objectContaining({ name: 'Acme Renamed' }),
      )
    })
  })

  it('shows load error on edit', async () => {
    vi.mocked(api.getParticipant).mockRejectedValue(new Error('404 missing'))
    renderWithRouter(
      <Routes>
        <Route path="/participants/:id/edit" element={<ParticipantFormPage />} />
      </Routes>,
      { route: '/participants/missing/edit' },
    )
    expect(await screen.findByText(/404 missing/)).toBeInTheDocument()
  })

  it('shows create API error', async () => {
    const user = userEvent.setup()
    vi.mocked(api.createParticipant).mockRejectedValue(new Error('409 exists'))
    renderWithRouter(
      <Routes>
        <Route path="/participants/new" element={<ParticipantFormPage />} />
      </Routes>,
      { route: '/participants/new' },
    )
    await user.type(screen.getByLabelText(/^ID/), 'dup')
    await user.type(screen.getByLabelText(/^Name/), 'Dup')
    await user.click(screen.getByRole('button', { name: /create/i }))
    expect(await screen.findByText(/409 exists/)).toBeInTheDocument()
  })

  it('allows editing contact and status fields', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/participants/:id/edit" element={<ParticipantFormPage />} />
        <Route path="/participants/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/participants/acme-corp/edit' },
    )
    expect(await screen.findByDisplayValue('ops@acme.example')).toBeInTheDocument()
    const contact = screen.getByLabelText(/^Contact/)
    await user.clear(contact)
    await user.type(contact, 'new@acme.example')
    await user.selectOptions(screen.getByLabelText(/^Status/), 'SUSPENDED')
    await user.click(screen.getByRole('button', { name: /save changes/i }))
    await waitFor(() => {
      expect(api.updateParticipant).toHaveBeenCalledWith(
        'acme-corp',
        expect.objectContaining({ contact: 'new@acme.example', status: 'SUSPENDED' }),
      )
    })
  })
})
