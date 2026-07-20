import { Navigate } from 'react-router-dom'
import { Loading } from '../components/ui'
import { useAuthorization, type AuthorizationState } from './AuthorizationContext'

type Capability = keyof Pick<
  AuthorizationState,
  'canMaintain' | 'canRead' | 'canCheckEntitlement' | 'canRegisterConsumption'
>

/**
 * Route guard: renders children only when the signed-in principal has the capability.
 * While roles load, shows a spinner; on denial redirects to [fallback].
 */
export function RequireCapability({
  capability,
  fallback = '/',
  children,
}: {
  capability: Capability
  fallback?: string
  children: React.ReactNode
}) {
  const auth = useAuthorization()

  if (auth.loading) {
    return (
      <section className="card">
        <Loading />
      </section>
    )
  }

  if (!auth[capability]) {
    return <Navigate to={fallback} replace />
  }

  return <>{children}</>
}
