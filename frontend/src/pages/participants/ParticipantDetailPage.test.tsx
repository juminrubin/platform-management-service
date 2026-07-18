import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { ParticipantDetailPage } from './ParticipantDetailPage'
import { renderWithRouter } from '../../test/render'
import { participantActive } from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  getParticipant: vi.fn(),
  deleteParticipant: vi.fn(),
}))

describe('ParticipantDetailPage', () => {
  beforeEach(() => {
    vi.mocked(api.getParticipant).mockResolvedValue(participantActive)
  })

  it('loads and shows participant details', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/participants/:id" element={<ParticipantDetailPage />} />
      </Routes>,
      { route: '/participants/acme-corp' },
    )

    expect(await screen.findByText('Acme Corporation')).toBeInTheDocument()
    expect(screen.getByText('ops@acme.example')).toBeInTheDocument()
    expect(api.getParticipant).toHaveBeenCalledWith('acme-corp')
  })

  it('shows API error', async () => {
    vi.mocked(api.getParticipant).mockRejectedValue(new Error('404 Not Found'))
    renderWithRouter(
      <Routes>
        <Route path="/participants/:id" element={<ParticipantDetailPage />} />
      </Routes>,
      { route: '/participants/missing' },
    )
    await waitFor(() => {
      expect(screen.getByText(/404 Not Found/)).toBeInTheDocument()
    })
  })
})
