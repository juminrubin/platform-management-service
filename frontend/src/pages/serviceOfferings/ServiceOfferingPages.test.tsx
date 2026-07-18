import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { ServiceOfferingListPage } from './ServiceOfferingListPage'
import { ServiceOfferingDetailPage } from './ServiceOfferingDetailPage'
import { ServiceOfferingFormPage } from './ServiceOfferingFormPage'
import { renderWithRouter } from '../../test/render'
import { serviceOfferingGpt, serviceOfferingLegacy } from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  listServiceOfferings: vi.fn(),
  getServiceOffering: vi.fn(),
  createServiceOffering: vi.fn(),
  updateServiceOffering: vi.fn(),
  deleteServiceOffering: vi.fn(),
}))

describe('Service offering pages', () => {
  beforeEach(() => {
    vi.mocked(api.listServiceOfferings).mockResolvedValue([serviceOfferingGpt, serviceOfferingLegacy])
    vi.mocked(api.getServiceOffering).mockResolvedValue(serviceOfferingGpt)
    vi.mocked(api.createServiceOffering).mockResolvedValue(serviceOfferingGpt)
    vi.mocked(api.updateServiceOffering).mockResolvedValue(serviceOfferingGpt)
  })

  it('lists offerings and client-filters by search', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ServiceOfferingListPage />)
    expect(await screen.findByText('GPT 5.1')).toBeInTheDocument()
    expect(screen.getByText('Legacy Batch')).toBeInTheDocument()

    await user.type(screen.getByRole('textbox', { name: /search id/i }), 'legacy')
    expect(screen.queryByText('GPT 5.1')).not.toBeInTheDocument()
    expect(screen.getByText('Legacy Batch')).toBeInTheDocument()
  })

  it('applies activeOnly API filter', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ServiceOfferingListPage />)
    await screen.findByText('GPT 5.1')
    await user.selectOptions(screen.getByLabelText(/active only/i), 'true')
    await user.click(screen.getByRole('button', { name: /apply filters/i }))
    await waitFor(() => {
      expect(api.listServiceOfferings).toHaveBeenLastCalledWith(
        expect.objectContaining({ activeOnly: true }),
      )
    })
  })

  it('shows detail with config', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/:id" element={<ServiceOfferingDetailPage />} />
      </Routes>,
      { route: '/service-offerings/gpt-5.1' },
    )
    expect(await screen.findByText('GPT 5.1')).toBeInTheDocument()
    expect(screen.getByText(/default_max_tpm/)).toBeInTheDocument()
  })

  it('creates an offering', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/new" element={<ServiceOfferingFormPage />} />
        <Route path="/service-offerings/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/service-offerings/new' },
    )

    await user.type(screen.getByLabelText(/^ID/), 'new-model')
    await user.type(screen.getByLabelText(/^Name/), 'New Model')
    await user.clear(screen.getByLabelText(/^Category/))
    await user.type(screen.getByLabelText(/^Category/), 'LLM')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))

    await waitFor(() => {
      expect(api.createServiceOffering).toHaveBeenCalledWith(
        expect.objectContaining({ id: 'new-model', name: 'New Model', category: 'LLM' }),
      )
    })
  })
})
