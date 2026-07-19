import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { ParticipantDetailPage } from './ParticipantDetailPage'
import { renderWithRouter } from '../../test/render'
import { participantActive } from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  getParticipant: vi.fn(),
  deleteParticipant: vi.fn(),
}))

function renderDetail(route = '/participants/acme-corp') {
  return renderWithRouter(
    <Routes>
      <Route path="/participants/:id" element={<ParticipantDetailPage />} />
      <Route path="/participants" element={<div>list page</div>} />
    </Routes>,
    { route },
  )
}

describe('ParticipantDetailPage', () => {
  beforeEach(() => {
    vi.mocked(api.getParticipant).mockResolvedValue(participantActive)
    vi.mocked(api.deleteParticipant).mockResolvedValue(undefined)
  })

  it('loads and shows participant details', async () => {
    renderDetail()
    expect(await screen.findByText('Acme Corporation')).toBeInTheDocument()
    expect(screen.getByText('ops@acme.example')).toBeInTheDocument()
    expect(api.getParticipant).toHaveBeenCalledWith('acme-corp')
  })

  it('shows API error', async () => {
    vi.mocked(api.getParticipant).mockRejectedValue(new Error('404 Not Found'))
    renderDetail('/participants/missing')
    await waitFor(() => {
      expect(screen.getByText(/404 Not Found/)).toBeInTheDocument()
    })
  })

  it('deletes after confirm and navigates to list', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderDetail()
    await screen.findByText('Acme Corporation')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteParticipant).toHaveBeenCalledWith('acme-corp')
    })
    expect(await screen.findByText('list page')).toBeInTheDocument()
  })

  it('does not delete when confirm is cancelled', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderDetail()
    await screen.findByText('Acme Corporation')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(api.deleteParticipant).not.toHaveBeenCalled()
  })

  it('shows delete API error', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteParticipant).mockRejectedValue(new Error('409 Conflict'))
    renderDetail()
    await screen.findByText('Acme Corporation')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(await screen.findByText(/409 Conflict/)).toBeInTheDocument()
  })
})
