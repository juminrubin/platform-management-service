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

  it('hides create/edit/delete actions for System.Reader', async () => {
    renderWithRouter(<ParticipantListPage />, {
      auth: {
        canMaintain: false,
        canRead: true,
        canCheckEntitlement: true,
        canRegisterConsumption: false,
        roles: ['System.Reader'],
      },
    })
    expect(await screen.findByText('Acme Corporation')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /new participant/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^edit$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /^view$/i }).length).toBeGreaterThan(0)
  })

  it('shows create/edit/delete for System.Maintainer', async () => {
    renderWithRouter(<ParticipantListPage />)
    expect(await screen.findByText('Acme Corporation')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /new participant/i })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /^edit$/i }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: /delete/i }).length).toBeGreaterThan(0)
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

  it('skips delete when confirm is cancelled', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderWithRouter(<ParticipantListPage />)
    await screen.findByText('Acme Corporation')
    const row = screen.getByText('Acme Corporation').closest('tr')!
    await user.click(within(row).getByRole('button', { name: /delete/i }))
    expect(api.deleteParticipant).not.toHaveBeenCalled()
  })

  it('shows list load error', async () => {
    vi.mocked(api.listParticipants).mockRejectedValue(new Error('500 Server Error'))
    renderWithRouter(<ParticipantListPage />)
    expect(await screen.findByText(/500 Server Error/)).toBeInTheDocument()
  })

  it('shows empty state', async () => {
    vi.mocked(api.listParticipants).mockResolvedValue([])
    renderWithRouter(<ParticipantListPage />)
    expect(await screen.findByText(/No participants match filters/i)).toBeInTheDocument()
  })

  it('resets filters', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ParticipantListPage />)
    await screen.findByText('Acme Corporation')
    await user.selectOptions(screen.getByLabelText(/status \(api\)/i), 'ACTIVE')
    await user.type(screen.getByPlaceholderText(/filter current results/i), 'acme')
    await user.click(screen.getByRole('button', { name: /reset/i }))
    await waitFor(() => {
      expect(api.listParticipants).toHaveBeenCalledWith()
    })
  })

  it('shows delete API error', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteParticipant).mockRejectedValue(new Error('403 Forbidden'))
    renderWithRouter(<ParticipantListPage />)
    await screen.findByText('Acme Corporation')
    const row = screen.getByText('Acme Corporation').closest('tr')!
    await user.click(within(row).getByRole('button', { name: /delete/i }))
    expect(await screen.findByText(/403 Forbidden/)).toBeInTheDocument()
  })
})
