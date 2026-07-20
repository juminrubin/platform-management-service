import { useIsAuthenticated, useMsal } from '@azure/msal-react'
import { Link } from 'react-router-dom'
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

export function HomePage() {
  const { accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()

  async function onSignIn() {
    try {
      await signInWithPopup()
    } catch (err) {
      console.error('Sign-in failed', err)
    }
  }

  return (
    <section className="card">
      <h1>Platform Management Service</h1>
      <p className="muted">
        Manage participants, caller registrations, offerings, entitlements, and consumption. API roles:{' '}
        <code>System.Maintainer</code>, <code>System.Reader</code>, <code>Entitlement.Reader</code>,{' '}
        <code>Consumption.Registrator</code>.
      </p>

      {!isAuthenticated ? (
        <button type="button" className="primary" onClick={() => void onSignIn()}>
          Sign in with Microsoft
        </button>
      ) : (
        <div className="stack">
          <p>
            Signed in as <strong>{accounts[0]?.username ?? accounts[0]?.name}</strong>
          </p>
          <div className="module-grid">
            {modules.map((m) => (
              <Link key={m.to} className="module-card" to={m.to}>
                <strong>{m.title}</strong>
                <span className="muted">{m.desc}</span>
              </Link>
            ))}
          </div>
          <Link className="button" to="/me">
            Inspect my token / roles
          </Link>
        </div>
      )}
    </section>
  )
}
