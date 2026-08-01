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
        expect.objectContaining({
          id: 'new-model',
          name: 'New Model',
          category: 'LLM',
          provider: 'SYSTEM',
        }),
      )
    })
  })

  it('loads and updates an offering', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/:id/edit" element={<ServiceOfferingFormPage />} />
        <Route path="/service-offerings/:id" element={<div>detail</div>} />
      </Routes>,
      { route: '/service-offerings/gpt-5.1/edit' },
    )
    expect(await screen.findByDisplayValue('GPT 5.1')).toBeInTheDocument()
    const name = screen.getByLabelText(/^Name/)
    await user.clear(name)
    await user.type(name, 'GPT Renamed')
    await user.click(screen.getByRole('button', { name: /save changes/i }))
    await waitFor(() => {
      expect(api.updateServiceOffering).toHaveBeenCalledWith(
        'gpt-5.1',
        expect.objectContaining({ name: 'GPT Renamed', provider: 'SYSTEM' }),
      )
    })
  })

  it('rejects invalid config JSON on create', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/new" element={<ServiceOfferingFormPage />} />
      </Routes>,
      { route: '/service-offerings/new' },
    )
    await user.type(screen.getByLabelText(/^ID/), 'bad')
    await user.type(screen.getByLabelText(/^Name/), 'Bad')
    const config = screen.getByLabelText(/Config \(JSON\)/i)
    await user.clear(config)
    // userEvent treats `{` as a special key descriptor; double it for a literal brace.
    await user.type(config, '{{not-json')
    await user.click(screen.getByRole('button', { name: /^Create$/i }))
    expect(await screen.findByText(/Config must be valid JSON/i)).toBeInTheDocument()
    expect(api.createServiceOffering).not.toHaveBeenCalled()
  })

  it('deletes from detail after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteServiceOffering).mockResolvedValue(undefined)
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/:id" element={<ServiceOfferingDetailPage />} />
        <Route path="/service-offerings" element={<div>so list</div>} />
      </Routes>,
      { route: '/service-offerings/gpt-5.1' },
    )
    await screen.findByText('GPT 5.1')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    await waitFor(() => {
      expect(api.deleteServiceOffering).toHaveBeenCalledWith('gpt-5.1')
    })
    expect(await screen.findByText('so list')).toBeInTheDocument()
  })

  it('deletes from list after confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteServiceOffering).mockResolvedValue(undefined)
    renderWithRouter(<ServiceOfferingListPage />)
    await screen.findByText('GPT 5.1')
    const buttons = screen.getAllByRole('button', { name: /delete/i })
    await user.click(buttons[0])
    await waitFor(() => {
      expect(api.deleteServiceOffering).toHaveBeenCalled()
    })
  })

  it('shows list load error', async () => {
    vi.mocked(api.listServiceOfferings).mockRejectedValue(new Error('list failed'))
    renderWithRouter(<ServiceOfferingListPage />)
    expect(await screen.findByText(/list failed/)).toBeInTheDocument()
  })

  it('shows empty state', async () => {
    vi.mocked(api.listServiceOfferings).mockResolvedValue([])
    renderWithRouter(<ServiceOfferingListPage />)
    expect(await screen.findByText(/No service offerings match/i)).toBeInTheDocument()
  })

  it('shows detail load error', async () => {
    vi.mocked(api.getServiceOffering).mockRejectedValue(new Error('404 missing'))
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/:id" element={<ServiceOfferingDetailPage />} />
      </Routes>,
      { route: '/service-offerings/missing' },
    )
    expect(await screen.findByText(/404 missing/)).toBeInTheDocument()
  })

  it('resets list filters', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ServiceOfferingListPage />)
    await screen.findByText('GPT 5.1')
    await user.type(screen.getByPlaceholderText(/LLM, SPEECH/i), 'LLM')
    await user.click(screen.getByRole('button', { name: /reset/i }))
    expect(screen.getByPlaceholderText(/LLM, SPEECH/i)).toHaveValue('')
  })

  it('shows delete error on detail and list', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(api.deleteServiceOffering).mockRejectedValue(new Error('cannot delete'))
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/:id" element={<ServiceOfferingDetailPage />} />
      </Routes>,
      { route: '/service-offerings/gpt-5.1' },
    )
    await screen.findByText('GPT 5.1')
    await user.click(screen.getByRole('button', { name: /delete/i }))
    expect(await screen.findByText(/cannot delete/)).toBeInTheDocument()
  })

  it('shows form load error on edit', async () => {
    vi.mocked(api.getServiceOffering).mockRejectedValue(new Error('load failed'))
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/:id/edit" element={<ServiceOfferingFormPage />} />
      </Routes>,
      { route: '/service-offerings/gpt-5.1/edit' },
    )
    expect(await screen.findByText(/load failed/)).toBeInTheDocument()
  })

  it('shows form update API error', async () => {
    vi.mocked(api.updateServiceOffering).mockRejectedValue(new Error('update failed'))
    const user = userEvent.setup()
    renderWithRouter(
      <Routes>
        <Route path="/service-offerings/:id/edit" element={<ServiceOfferingFormPage />} />
      </Routes>,
      { route: '/service-offerings/gpt-5.1/edit' },
    )
    expect(await screen.findByDisplayValue('GPT 5.1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /save changes/i }))
    expect(await screen.findByText(/update failed/)).toBeInTheDocument()
  })
})
