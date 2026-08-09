import { useIsAuthenticated, useMsal } from '@azure/msal-react'
import { Link } from 'react-router-dom'
import { useAuthorization } from '../auth/AuthorizationContext'
import { signInWithPopup } from '../auth/msalConfig'

const modules = [
  { to: '/participants', title: 'Participants', desc: 'Billing groups for caller registrations' },
  {
    to: '/caller-registrations',
    title: 'Caller registrations',
    desc: 'Unique caller principals (email / Entra SP / MI)',
  },
  { to: '/service-offerings', title: 'Service offerings', desc: 'Catalog of entitled services' },
  { to: '/entitlements', title: 'Entitlements', desc: 'Participant access rights to offerings' },
  { to: '/consumptions', title: 'Consumptions', desc: 'Token / usage events with rich filters' },
]

const maintainerModules = [
  {
    to: '/connectors',
    title: 'Connectors',
    desc: 'Operate Entra, Blob, and Event Hub processes (start/stop, config, logs)',
  },
]

export function HomePage() {
  const { accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()
  const { canMaintain } = useAuthorization()
  const cards = canMaintain ? [...modules, ...maintainerModules] : modules

  async function onSignIn() {
    try {
      await signInWithPopup()
    } catch (err) {
      console.error('Sign-in failed', err)
    }
  }

  return (
    <section className="card home-card">
      <header className="home-hero">
        <h1 className="home-title">Platform Management Service</h1>
        <p className="muted home-lead">
          Manage participants, caller registrations, offerings, entitlements, and consumption.
        </p>
        <p className="home-roles muted">
          API roles:{' '}
          <code>System.Maintainer</code>, <code>System.Reader</code>, <code>Entitlement.Reader</code>,{' '}
          <code>Consumption.Registrator</code>
        </p>
      </header>

      {!isAuthenticated ? (
        <div className="home-actions">
          <button type="button" className="primary" onClick={() => void onSignIn()}>
            Sign in with Microsoft
          </button>
        </div>
      ) : (
        <div className="stack home-content">
          <p className="home-signed-in">
            Signed in as <strong>{accounts[0]?.username ?? accounts[0]?.name}</strong>
          </p>
          <div className="module-grid">
            {cards.map((m) => (
              <Link key={m.to} className="module-card" to={m.to}>
                <strong>{m.title}</strong>
                <span className="muted">{m.desc}</span>
              </Link>
            ))}
          </div>
          <div className="home-actions">
            <Link className="button" to="/me">
              Inspect my token / roles
            </Link>
          </div>
        </div>
      )}
    </section>
  )
}
