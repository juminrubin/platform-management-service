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

  it('loads and updates an entitlement', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/:id/edit" element={<EntitlementFormPage />} />
        <Route path="/entitlements/:id" element={<div>detail</div>} />
      </Routes>,
      { route: `/entitlements/${entitlementActive.id}/edit` },
    )
    expect(await screen.findByDisplayValue('Enterprise tier')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText(/^Status/), 'REVOKED')
    await user.click(screen.getByRole('button', { name: /save changes/i }))
    await waitFor(() => {
      expect(api.updateEntitlement).toHaveBeenCalledWith(
        entitlementActive.id,
        expect.objectContaining({ status: 'REVOKED' }),
      )
    })
  })

  it('rejects invalid config JSON', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/new" element={<EntitlementFormPage />} />
      </Routes>,
      { route: '/entitlements/new' },
    )
    await screen.findByRole('option', { name: /Acme Corporation/ })
    await user.selectOptions(screen.getByLabelText(/^Participant/), 'acme-corp')
    await user.selectOptions(screen.getByLabelText(/^Service offering/), 'gpt-5.1')
    const config = screen.getByLabelText(/Config \(JSON\)/i)
    await user.clear(config)
    await user.type(config, '{{bad')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))
    expect(await screen.findByText(/Config must be valid JSON/i)).toBeInTheDocument()
    expect(api.createEntitlement).not.toHaveBeenCalled()
  })

  it('deletes from detail after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteEntitlement).mockResolvedValue(undefined)
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/:id" element={<EntitlementDetailPage />} />
        <Route path="/entitlements" element={<div>ent list</div>} />
      </Routes>,
      { route: `/entitlements/${entitlementActive.id}` },
    )
    await screen.findByText('Enterprise tier')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteEntitlement).toHaveBeenCalledWith(entitlementActive.id)
    })
    expect(await screen.findByText('ent list')).toBeInTheDocument()
  })

  it('deletes from list after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteEntitlement).mockResolvedValue(undefined)
    renderWithRouter(<EntitlementListPage />)
    await screen.findByText('Acme Corporation')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteEntitlement).toHaveBeenCalled()
    })
  })

  it('shows list load error', async () => {
    vi.mocked(api.listEntitlements).mockRejectedValue(new Error('list failed'))
    renderWithRouter(<EntitlementListPage />)
    expect(await screen.findByText(/list failed/)).toBeInTheDocument()
  })

  it('filters client-side by search text', async () => {
    const user = userEvent.setup()
    renderWithRouter(<EntitlementListPage />)
    await screen.findByText('Acme Corporation')
    await user.type(screen.getByLabelText(/Search names/i), 'no-such-notes')
    expect(screen.getByText(/No entitlements match/i)).toBeInTheDocument()
  })

  it('shows detail load error', async () => {
    vi.mocked(api.getEntitlement).mockRejectedValue(new Error('404 gone'))
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/:id" element={<EntitlementDetailPage />} />
      </Routes>,
      { route: `/entitlements/${entitlementActive.id}` },
    )
    expect(await screen.findByText(/404 gone/)).toBeInTheDocument()
  })

  it('resets list filters', async () => {
    const user = userEvent.setup()
    renderWithRouter(<EntitlementListPage />)
    await screen.findByText('Acme Corporation')
    await user.type(screen.getByPlaceholderText('acme-corp'), 'acme-corp')
    await user.click(screen.getByRole('button', { name: /reset/i }))
    expect(screen.getByPlaceholderText('acme-corp')).toHaveValue('')
  })

  it('shows detail and list delete errors', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteEntitlement).mockRejectedValue(new Error('delete blocked'))
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/:id" element={<EntitlementDetailPage />} />
      </Routes>,
      { route: `/entitlements/${entitlementActive.id}` },
    )
    await screen.findByText('Enterprise tier')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(await screen.findByText(/delete blocked/)).toBeInTheDocument()
  })

  it('shows form load error on edit', async () => {
    vi.mocked(api.getEntitlement).mockRejectedValue(new Error('load failed'))
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/:id/edit" element={<EntitlementFormPage />} />
      </Routes>,
      { route: `/entitlements/${entitlementActive.id}/edit` },
    )
    expect(await screen.findByText(/load failed/)).toBeInTheDocument()
  })

  it('shows form update API error', async () => {
    vi.mocked(api.updateEntitlement).mockRejectedValue(new Error('update failed'))
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/entitlements/:id/edit" element={<EntitlementFormPage />} />
      </Routes>,
      { route: `/entitlements/${entitlementActive.id}/edit` },
    )
    expect(await screen.findByDisplayValue('Enterprise tier')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /save changes/i }))
    expect(await screen.findByText(/update failed/)).toBeInTheDocument()
  })
})
