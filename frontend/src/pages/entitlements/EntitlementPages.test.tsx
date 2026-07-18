import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { EntitlementListPage } from './EntitlementListPage'
import { EntitlementDetailPage } from './EntitlementDetailPage'
import { EntitlementFormPage } from './EntitlementFormPage'
import { renderWithRouter } from '../../test/render'
import {
  entitlementActive,
  participantActive,
  serviceOfferingGpt,
} from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  listEntitlements: vi.fn(),
  getEntitlement: vi.fn(),
  createEntitlement: vi.fn(),
  updateEntitlement: vi.fn(),
  deleteEntitlement: vi.fn(),
  listParticipants: vi.fn(),
  listServiceOfferings: vi.fn(),
}))

describe('Entitlement pages', () => {
  beforeEach(() => {
    vi.mocked(api.listEntitlements).mockResolvedValue([entitlementActive])
    vi.mocked(api.getEntitlement).mockResolvedValue(entitlementActive)
    vi.mocked(api.listParticipants).mockResolvedValue([participantActive])
    vi.mocked(api.listServiceOfferings).mockResolvedValue([serviceOfferingGpt])
    vi.mocked(api.createEntitlement).mockResolvedValue(entitlementActive)
    vi.mocked(api.updateEntitlement).mockResolvedValue(entitlementActive)
  })

  it('lists entitlements', async () => {
    renderWithRouter(<EntitlementListPage />)
    expect(await screen.findByText('Acme Corporation')).toBeInTheDocument()
    expect(screen.getByText('GPT 5.1')).toBeInTheDocument()
    expect(screen.getAllByText('ACTIVE').length).toBeGreaterThan(0)
  })

  it('filters by participant and status via API', async () => {
    const user = userEvent.setup()
    renderWithRouter(<EntitlementListPage />)
    await screen.findByText('Acme Corporation')
    await user.type(screen.getByPlaceholderText('acme-corp'), 'acme-corp')
    await user.selectOptions(screen.getByLabelText(/^Status \(API\)/), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: /apply filters/i }))
    await waitFor(() => {
      expect(api.listEntitlements).toHaveBeenLastCalledWith(
        expect.objectContaining({ participantId: 'acme-corp', status: 'ACTIVE' }),
      )
    })
  })

  it('shows detail', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/:id" element={<EntitlementDetailPage />} />
      </Routes>,
      { route: `/entitlements/${entitlementActive.id}` },
    )
    expect(await screen.findByText('Enterprise tier')).toBeInTheDocument()
    expect(screen.getByText(/max_tpm/)).toBeInTheDocument()
  })

  it('creates an entitlement', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/new" element={<EntitlementFormPage />} />
        <Route path="/entitlements/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/entitlements/new' },
    )

    await screen.findByRole('option', { name: /Acme Corporation/ })
    await user.selectOptions(screen.getByLabelText(/^Participant/), 'acme-corp')
    await user.selectOptions(screen.getByLabelText(/^Service offering/), 'gpt-5.1')
    await user.selectOptions(screen.getByLabelText(/^Status/), 'ACTIVE')
    await user.clear(screen.getByLabelText(/Valid from/i))
    await user.type(screen.getByLabelText(/Valid from/i), '2024-01-01')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))

    await waitFor(() => {
      expect(api.createEntitlement).toHaveBeenCalledWith(
        expect.objectContaining({
          participantId: 'acme-corp',
          serviceOfferingId: 'gpt-5.1',
          status: 'ACTIVE',
          validFrom: '2024-01-01',
        }),
      )
    })
  })
})
