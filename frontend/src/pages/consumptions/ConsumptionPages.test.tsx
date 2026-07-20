import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { ConsumptionListPage } from './ConsumptionListPage'
import { ConsumptionDetailPage } from './ConsumptionDetailPage'
import { ConsumptionFormPage } from './ConsumptionFormPage'
import { renderWithRouter } from '../../test/render'
import {
  callerRegistrationActive,
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
  listCallerRegistrations: vi.fn(),
  listServiceOfferings: vi.fn(),
}))

describe('Consumption pages', () => {
  beforeEach(() => {
    vi.mocked(api.listConsumptions).mockResolvedValue([consumptionSample, consumptionLater])
    vi.mocked(api.getConsumption).mockResolvedValue(consumptionSample)
    vi.mocked(api.listCallerRegistrations).mockResolvedValue([callerRegistrationActive])
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
    await user.selectOptions(screen.getByLabelText(/^Caller ID/), callerRegistrationActive.callerId)
    await user.selectOptions(screen.getByLabelText(/^Service offering/), 'gpt-5.1')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))

    await waitFor(() => {
      expect(api.createConsumption).toHaveBeenCalledWith(
        expect.objectContaining({
          callerId: callerRegistrationActive.callerId,
          serviceOfferingId: 'gpt-5.1',
        }),
      )
    })
  })

  it('rejects invalid consumption JSON', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/new" element={<ConsumptionFormPage />} />
      </Routes>,
      { route: '/consumptions/new' },
    )
    await screen.findByRole('option', { name: /alice@acme.example/ })
    await user.selectOptions(screen.getByLabelText(/^Caller ID/), callerRegistrationActive.callerId)
    await user.selectOptions(screen.getByLabelText(/^Service offering/), 'gpt-5.1')
    const data = screen.getByLabelText(/Consumption data/i)
    await user.clear(data)
    await user.type(data, '{{bad')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))
    expect(await screen.findByText(/consumptionData must be valid JSON/i)).toBeInTheDocument()
  })

  it('creates with optional event time', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/new" element={<ConsumptionFormPage />} />
        <Route path="/consumptions/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/consumptions/new' },
    )
    await screen.findByRole('option', { name: /alice@acme.example/ })
    await user.selectOptions(screen.getByLabelText(/^Caller ID/), callerRegistrationActive.callerId)
    await user.selectOptions(screen.getByLabelText(/^Service offering/), 'gpt-5.1')
    // datetime-local typing is flaky in jsdom — set value via change event
    fireEvent.change(screen.getByLabelText(/Event time/i), {
      target: { value: '2024-08-01T10:30' },
    })
    await user.click(screen.getByRole('button', { name: /^Create$/i }))
    await waitFor(() => {
      expect(api.createConsumption).toHaveBeenCalledWith(
        expect.objectContaining({
          callerId: callerRegistrationActive.callerId,
          consumedAt: expect.stringMatching(/2024-08-01/),
        }),
      )
    })
  })

  it('deletes from detail after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/:id" element={<ConsumptionDetailPage />} />
        <Route path="/consumptions" element={<div>cons list</div>} />
      </Routes>,
      { route: `/consumptions/${consumptionSample.id}` },
    )
    await screen.findByText(/input_token/)
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteConsumption).toHaveBeenCalledWith(consumptionSample.id)
    })
    expect(await screen.findByText('cons list')).toBeInTheDocument()
  })

  it('deletes from list after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderWithRouter(<ConsumptionListPage />)
    await screen.findByText(/input_token=1200/)
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    await user.click(deleteButtons[0])
    await waitFor(() => {
      expect(api.deleteConsumption).toHaveBeenCalled()
    })
  })

  it('shows list load error', async () => {
    vi.mocked(api.listConsumptions).mockRejectedValue(new Error('list failed'))
    renderWithRouter(<ConsumptionListPage />)
    expect(await screen.findByText(/list failed/)).toBeInTheDocument()
  })

  it('applies client participant and caller filters and reset', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ConsumptionListPage />)
    await screen.findByText(/input_token=1200/)
    await user.selectOptions(screen.getByLabelText(/Participant \(client\)/i), 'acme-corp')
    await user.type(screen.getByPlaceholderText(/email \/ client id/i), 'alice')
    fireEvent.change(screen.getByLabelText(/To \(client/i), { target: { value: '2024-12-31T23:59' } })
    expect(screen.getAllByText(/alice@acme.example/).length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: /reset/i }))
    await waitFor(() => {
      expect(api.listConsumptions).toHaveBeenCalledWith()
    })
  })

  it('shows detail load error', async () => {
    vi.mocked(api.getConsumption).mockRejectedValue(new Error('404 gone'))
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/:id" element={<ConsumptionDetailPage />} />
      </Routes>,
      { route: `/consumptions/${consumptionSample.id}` },
    )
    expect(await screen.findByText(/404 gone/)).toBeInTheDocument()
  })

  it('shows form options load error', async () => {
    vi.mocked(api.listCallerRegistrations).mockRejectedValue(new Error('options failed'))
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/new" element={<ConsumptionFormPage />} />
      </Routes>,
      { route: '/consumptions/new' },
    )
    expect(await screen.findByText(/options failed/)).toBeInTheDocument()
  })

  it('shows create API error', async () => {
    vi.mocked(api.createConsumption).mockRejectedValue(new Error('create failed'))
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/new" element={<ConsumptionFormPage />} />
      </Routes>,
      { route: '/consumptions/new' },
    )
    await screen.findByRole('option', { name: /alice@acme.example/ })
    await user.selectOptions(screen.getByLabelText(/^Caller ID/), callerRegistrationActive.callerId)
    await user.selectOptions(screen.getByLabelText(/^Service offering/), 'gpt-5.1')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))
    expect(await screen.findByText(/create failed/)).toBeInTheDocument()
  })

  it('shows detail and list delete errors', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteConsumption).mockRejectedValue(new Error('delete blocked'))
    renderWithRouter(
      <Routes>
        <Route path="/consumptions/:id" element={<ConsumptionDetailPage />} />
      </Routes>,
      { route: `/consumptions/${consumptionSample.id}` },
    )
    await screen.findByText(/input_token/)
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(await screen.findByText(/delete blocked/)).toBeInTheDocument()
  })

  it('filters out records by non-matching caller text', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ConsumptionListPage />)
    await screen.findByText(/input_token=1200/)
    await user.type(screen.getByPlaceholderText(/email \/ client id/i), 'nobody@example.com')
    expect(screen.getByText(/No consumption records match/i)).toBeInTheDocument()
  })
})
