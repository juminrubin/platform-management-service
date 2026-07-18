import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ParticipantListPage } from './ParticipantListPage'
import { renderWithRouter } from '../../test/render'
import { participantActive, participantInactive } from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  listParticipants: vi.fn(),
  deleteParticipant: vi.fn(),
}))

describe('ParticipantListPage', () => {
  beforeEach(() => {
    vi.mocked(api.listParticipants).mockResolvedValue([participantActive, participantInactive])
    vi.mocked(api.deleteParticipant).mockResolvedValue(undefined)
  })

  it('loads and renders participants', async () => {
    renderWithRouter(<ParticipantListPage />)
    expect(await screen.findByText('Acme Corporation')).toBeInTheDocument()
    expect(screen.getByText('Gamma Partners')).toBeInTheDocument()
    expect(api.listParticipants).toHaveBeenCalled()
  })

  it('filters client-side by search text', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ParticipantListPage />)
    await screen.findByText('Acme Corporation')

    await user.type(screen.getByPlaceholderText(/filter current results/i), 'gamma')
    expect(screen.queryByText('Acme Corporation')).not.toBeInTheDocument()
    expect(screen.getByText('Gamma Partners')).toBeInTheDocument()
  })

  it('applies API status filter on submit', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ParticipantListPage />)
    await screen.findByText('Acme Corporation')

    await user.selectOptions(screen.getByLabelText(/status \(api\)/i), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: /apply filters/i }))

    await waitFor(() => {
      expect(api.listParticipants).toHaveBeenLastCalledWith('ACTIVE')
    })
  })

  it('deletes a participant after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderWithRouter(<ParticipantListPage />)
    await screen.findByText('Acme Corporation')

    const row = screen.getByText('Acme Corporation').closest('tr')
    expect(row).toBeTruthy()
    await user.click(within(row as HTMLElement).getByRole('button', { name: /delete/i }))

    await waitFor(() => {
      expect(api.deleteParticipant).toHaveBeenCalledWith('acme-corp')
    })
  })
})
