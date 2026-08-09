import { Link, NavLink, Outlet } from 'react-router-dom'
import { useIsAuthenticated, useMsal } from '@azure/msal-react'
import { useAuthorization } from '../auth/AuthorizationContext'
import { signInWithPopup, signOutWithPopup } from '../auth/msalConfig'
import { buildInfo } from '../buildInfo'

function formatBuildTimestamp(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  // Compact UTC display: 2026-08-09 12:04 UTC
  return date.toISOString().replace('T', ' ').replace(/:\d{2}\.\d{3}Z$/, ' UTC')
}

const baseNav = [
  { to: '/participants', label: 'Participants' },
  { to: '/caller-registrations', label: 'Caller registrations' },
  { to: '/service-offerings', label: 'Service offerings' },
  { to: '/entitlements', label: 'Entitlements' },
  { to: '/consumptions', label: 'Consumptions' },
  { to: '/me', label: 'My token' },
]

export function Layout() {
  const { accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()
  const { canMaintain } = useAuthorization()
  const nav = canMaintain
    ? [
        ...baseNav.slice(0, 5),
        { to: '/connectors', label: 'Connectors' },
        ...baseNav.slice(5),
      ]
    : baseNav

  async function onSignIn() {
    try {
      await signInWithPopup()
    } catch (err) {
      console.error('Sign-in failed', err)
    }
  }

  async function onSignOut() {
    try {
      await signOutWithPopup()
    } catch (err) {
      console.error('Sign-out failed', err)
    }
  }

  return (
    <div className="layout">
      <header className="topbar">
        <Link to="/" className="brand">
          Platform Management Service
        </Link>
        {isAuthenticated && (
          <nav className="nav">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
        <div className="topbar-actions">
          {!isAuthenticated ? (
            <button type="button" className="primary" onClick={() => void onSignIn()}>
              Sign in
            </button>
          ) : (
            <>
              <span className="user-chip">{accounts[0]?.username ?? accounts[0]?.name}</span>
              <button type="button" className="button-ghost" onClick={() => void onSignOut()}>
                Sign out
              </button>
            </>
          )}
        </div>
      </header>
      <main className="layout-main">
        <Outlet />
      </main>
      <footer className="app-footer" aria-label="Build information">
        <span className="app-footer-item">
          UI <span className="app-footer-value">v{buildInfo.version}</span>
        </span>
        <span className="app-footer-sep" aria-hidden="true">
          ·
        </span>
        <span className="app-footer-item">
          built{' '}
          <time className="app-footer-value" dateTime={buildInfo.timestamp}>
            {formatBuildTimestamp(buildInfo.timestamp)}
          </time>
        </span>
      </footer>
    </div>
  )
}
