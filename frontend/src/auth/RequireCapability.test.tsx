import { describe, expect, it } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { RequireCapability } from './RequireCapability'
import { renderWithRouter } from '../test/render'
import { readerCapabilities } from './capabilities'

describe('RequireCapability', () => {
  it('renders children when capability is granted', () => {
    renderWithRouter(
      <RequireCapability capability="canMaintain">
        <div>secret admin</div>
      </RequireCapability>,
    )
    expect(screen.getByText('secret admin')).toBeInTheDocument()
  })

  it('shows loading while roles resolve', () => {
    renderWithRouter(
      <RequireCapability capability="canMaintain">
        <div>secret admin</div>
      </RequireCapability>,
      { auth: { loading: true, canMaintain: false } },
    )
    expect(screen.queryByText('secret admin')).not.toBeInTheDocument()
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('redirects when capability is missing', () => {
    renderWithRouter(
      <Routes>
        <Route
          path="/edit"
          element={
            <RequireCapability capability="canMaintain" fallback="/list">
              <div>editor</div>
            </RequireCapability>
          }
        />
        <Route path="/list" element={<div>list only</div>} />
      </Routes>,
      {
        route: '/edit',
        auth: { ...readerCapabilities, roles: ['System.Reader'] },
      },
    )
    expect(screen.getByText('list only')).toBeInTheDocument()
    expect(screen.queryByText('editor')).not.toBeInTheDocument()
  })
})
