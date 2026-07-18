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
})
