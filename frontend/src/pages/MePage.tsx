import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMe } from '../api/client'
import type { AuthenticatedUser } from '../api/types'
import { ErrorBox, Loading, PageHeader } from '../components/ui'

export function MePage() {
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    getMe()
      .then(setUser)
      .catch((e: Error) => setError(e.message))
  }, [])
  return (
    <section className="card">
      <PageHeader
        title="Authenticated principal"
        subtitle="GET /api/v1/auth/me — Entra JWT claims used by the API."
        actions={
          <Link className="button" to="/">
            Home
          </Link>
        }
      />
      <ErrorBox error={error} />
      {!user && !error && <Loading />}
      {user && <pre className="code">{JSON.stringify(user, null, 2)}</pre>}
    </section>
  )
}
