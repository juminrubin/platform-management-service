import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { ConnectorListPage } from './ConnectorListPage'
import { ConnectorDetailPage } from './ConnectorDetailPage'
import { renderWithRouter } from '../../test/render'
import type { ConnectorInfo, ConnectorSummary } from '../../api/types'
import * as api from '../../api/client'

vi.mock('../../api/client', () => ({
  listConnectors: vi.fn(),
  getConnector: vi.fn(),
  startConnector: vi.fn(),
  stopConnector: vi.fn(),
  updateConnectorConfig: vi.fn(),
}))

const summaryEntra: ConnectorSummary = {
  id: 'entra-directory',
  enabled: true,
  configured: true,
  running: false,
  status: 'STOPPED',
  detail: 'stopped',
  attributes: {},
}

const summaryEh: ConnectorSummary = {
  id: 'consumption-eventhub',
  enabled: true,
  configured: true,
  running: true,
  status: 'RUNNING',
  detail: 'running',
  attributes: {},
}

const infoEntra: ConnectorInfo = {
  id: 'entra-directory',
  enabled: true,
  configured: true,
  running: false,
  status: 'STOPPED',
  detail: 'stopped',
  lastStartedBy: null,
  lastStartedAt: null,
  lastStoppedBy: null,
  lastStoppedAt: null,
  lastError: null,
  attributes: { dataPlane: '/api/v1/entra/groups' },
  configuration: {
    enabled: true,
    autoStart: true,
    refreshIntervalMs: 900000,
    groupNamePrefix: 'Platform-System-',
  },
  logSnapshot: {
    maxBytes: 32768,
    bytes: 20,
    lineCount: 1,
    lines: ['2024-01-01T00:00:00Z INFO ready'],
  },
}

describe('ConnectorListPage', () => {
  beforeEach(() => {
    vi.mocked(api.listConnectors).mockResolvedValue({
      connectors: [summaryEntra, summaryEh],
    })
    vi.mocked(api.startConnector).mockResolvedValue({ ...infoEntra, running: true, status: 'RUNNING' })
    vi.mocked(api.stopConnector).mockResolvedValue({ ...infoEntra, running: false, status: 'STOPPED' })
  })

  it('lists connectors for maintainers', async () => {
    renderWithRouter(<ConnectorListPage />)
    expect(await screen.findByText('Entra directory')).toBeInTheDocument()
    expect(screen.getByText('Consumption Event Hub')).toBeInTheDocument()
    expect(api.listConnectors).toHaveBeenCalled()
  })

  it('starts a stopped connector from the list', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ConnectorListPage />)
    await screen.findByText('Entra directory')

    const startButtons = screen.getAllByRole('button', { name: /^start$/i })
    // entra is stopped → first Start should be enabled
    await user.click(startButtons[0])
    await waitFor(() => {
      expect(api.startConnector).toHaveBeenCalledWith('entra-directory')
    })
  })

  it('stops a running connector from the list', async () => {
    const user = userEvent.setup()
    renderWithRouter(<ConnectorListPage />)
    await screen.findByText('Consumption Event Hub')

    const stopButtons = screen.getAllByRole('button', { name: /^stop$/i })
    // event hub is running
    await user.click(stopButtons[1])
    await waitFor(() => {
      expect(api.stopConnector).toHaveBeenCalledWith('consumption-eventhub')
    })
  })
})

describe('ConnectorDetailPage', () => {
  beforeEach(() => {
    vi.mocked(api.getConnector).mockResolvedValue(infoEntra)
    vi.mocked(api.startConnector).mockResolvedValue({
      ...infoEntra,
      running: true,
      status: 'RUNNING',
      detail: 'running',
    })
    vi.mocked(api.stopConnector).mockResolvedValue(infoEntra)
    vi.mocked(api.updateConnectorConfig).mockResolvedValue({
      id: 'entra-directory',
      configuration: { ...infoEntra.configuration, refreshIntervalMs: 60000 },
    })
  })

  function renderDetail() {
    return renderWithRouter(
      <Routes>
        <Route path="/connectors/:id" element={<ConnectorDetailPage />} />
      </Routes>,
      { route: '/connectors/entra-directory' },
    )
  }

  it('loads connector info, config, and logs', async () => {
    renderDetail()
    expect(await screen.findByText('Entra directory')).toBeInTheDocument()
    expect(screen.getByText('Edit runtime configuration')).toBeInTheDocument()
    expect(screen.getByDisplayValue('900000')).toBeInTheDocument()
    expect(screen.getByText(/INFO ready/)).toBeInTheDocument()
    expect(api.getConnector).toHaveBeenCalledWith('entra-directory')
  })

  it('starts the connector', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText('Entra directory')
    await user.click(screen.getByRole('button', { name: /^start$/i }))
    await waitFor(() => {
      expect(api.startConnector).toHaveBeenCalledWith('entra-directory')
    })
    expect(await screen.findByText(/connector started/i)).toBeInTheDocument()
  })

  it('saves runtime configuration', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByDisplayValue('900000')
    const input = screen.getByDisplayValue('900000')
    await user.clear(input)
    await user.type(input, '60000')
    await user.click(screen.getByRole('button', { name: /save configuration/i }))
    await waitFor(() => {
      expect(api.updateConnectorConfig).toHaveBeenCalledWith('entra-directory', {
        configuration: { refreshIntervalMs: 60000 },
      })
    })
  })
})
