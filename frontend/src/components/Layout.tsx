import { Link, NavLink, Outlet } from 'react-router-dom'
import { useIsAuthenticated, useMsal } from '@azure/msal-react'
import { signInWithPopup, signOutWithPopup } from '../auth/msalConfig'

const nav = [
  { to: '/participants', label: 'Participants' },
  { to: '/caller-identities', label: 'Caller identities' },
  { to: '/service-offerings', label: 'Service offerings' },
  { to: '/entitlements', label: 'Entitlements' },
  { to: '/consumptions', label: 'Consumptions' },
  { to: '/me', label: 'My token' },
]

export function Layout() {
  const { accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()

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
      <main>
        <Outlet />
      </main>
    </div>
  )
}
