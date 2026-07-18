import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { ConsumptionListPage } from './ConsumptionListPage'
import { ConsumptionDetailPage } from './ConsumptionDetailPage'
import { ConsumptionFormPage } from './ConsumptionFormPage'
import { renderWithRouter } from '../../test/render'
import {
  callerIdentityActive,
  consumptionLater,
  consumptionSample,
  serviceOfferingGpt,
} from '../../test/fixtures'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  listConsumptions: vi.fn(),
  getConsumption: vi.fn(),
  createConsumption: vi.fn(),
  deleteConsumption: vi.fn(),
  listCallerIdentities: vi.fn(),
  listServiceOfferings: vi.fn(),
}))

describe('Consumption pages', () => {
  beforeEach(() => {
    vi.mocked(api.listConsumptions).mockResolvedValue([consumptionSample, consumptionLater])
    vi.mocked(api.getConsumption).mockResolvedValue(consumptionSample)
    vi.mocked(api.listCallerIdentities).mockResolvedValue([callerIdentityActive])
    vi.mocked(api.listServiceOfferings).mockResolvedValue([serviceOfferingGpt])
    vi.mocked(api.createConsumption).mockResolvedValue(consumptionSample)
    vi.mocked(api.deleteConsumption).mockResolvedValue(undefined)
  })

  it('lists consumptions with previews', async () => {
    renderWithRouter(<ConsumptionListPage />)
    expect(await screen.findAllByText('alice@acme.example')).not.toHaveLength(0)
    expect(screen.getByText(/input_token=1200/)).toBeInTheDocument()
    expect(
      screen.getByText((_, el) => el?.textContent === 'Showing 2 of 2 loaded records (API filters re-fetch; client filters refine the result set).'),
    ).toBeInTheDocument()
  })

  it('applies API filter for service offering', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ConsumptionListPage />)
    await screen.findByText(/input_token=1200/)
    await user.selectOptions(screen.getByLabelText(/Service offering \(API\)/i), 'gpt-5.1')
    await user.click(screen.getByRole('button', { name: /apply filters/i }))
    await waitFor(() => {
      expect(api.listConsumptions).toHaveBeenLastCalledWith(
        expect.objectContaining({ serviceOfferingId: 'gpt-5.1' }),
      )
    })
  })

  it('filters client-side by JSON query', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ConsumptionListPage />)
    await screen.findByText(/input_token=1200/)
    // Match raw JSON payload of the later record only
    await user.type(screen.getByPlaceholderText(/input_token/i), '"output_token":10')
    await waitFor(() => {
      expect(screen.getByText(/input_token=50/)).toBeInTheDocument()
      expect(screen.queryByText(/input_token=1200/)).not.toBeInTheDocument()
    })
  })

  it('filters client-side by date from', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ConsumptionListPage />)
    await screen.findByText(/input_token=1200/)
    // Exclude June 2024 record; keep July
    await user.type(screen.getByLabelText(/From \(client/i), '2024-07-01T00:00')
    await waitFor(() => {
      expect(screen.getByText(/input_token=50/)).toBeInTheDocument()
      expect(screen.queryByText(/input_token=1200/)).not.toBeInTheDocument()
    })
  })

  it('shows detail JSON', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/:id" element={<ConsumptionDetailPage />} />
      </Routes>,
      { route: `/consumptions/${consumptionSample.id}` },
    )
    expect(await screen.findByText(/input_token/)).toBeInTheDocument()
    expect(api.getConsumption).toHaveBeenCalledWith(consumptionSample.id)
  })

  it('creates a consumption record', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/new" element={<ConsumptionFormPage />} />
        <Route path="/consumptions/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/consumptions/new' },
    )

    await screen.findByRole('option', { name: /alice@acme.example/ })
    await user.selectOptions(
      screen.getByLabelText(/^Caller identity/),
      callerIdentityActive.id,
    )
    await user.selectOptions(screen.getByLabelText(/^Service offering/), 'gpt-5.1')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))

    await waitFor(() => {
      expect(api.createConsumption).toHaveBeenCalledWith(
        expect.objectContaining({
          participantCallerIdentityId: callerIdentityActive.id,
          serviceOfferingId: 'gpt-5.1',
        }),
      )
    })
  })
})
