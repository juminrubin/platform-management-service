import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useIsAuthenticated } from '@azure/msal-react'
import { getMe } from '../api/client'
import type { AuthenticatedUser } from '../api/types'
import {
  collectRoles,
  deriveCapabilities,
  emptyCapabilities,
  type Capabilities,
} from './capabilities'

export type AuthorizationState = Capabilities & {
  loading: boolean
  roles: string[]
  user: AuthenticatedUser | null
  error: string | null
  refresh: () => Promise<void>
}

const AuthorizationContext = createContext<AuthorizationState | null>(null)

const noopRefresh = async () => undefined

function staticState(partial: Partial<AuthorizationState>): AuthorizationState {
  return {
    loading: false,
    roles: [],
    user: null,
    error: null,
    refresh: noopRefresh,
    ...emptyCapabilities,
    ...partial,
  }
}

type ProviderProps = {
  children: ReactNode
  /** When set, skip MSAL /auth/me and use this value (unit tests). */
  value?: Partial<AuthorizationState>
}

/**
 * Loads API roles from GET /api/v1/auth/me after MSAL sign-in and exposes
 * capability flags aligned with backend `@authz` helpers.
 */
export function AuthorizationProvider({ children, value: override }: ProviderProps) {
  if (override) {
    return (
      <AuthorizationContext.Provider value={staticState(override)}>
        {children}
      </AuthorizationContext.Provider>
    )
  }
  return <LiveAuthorizationProvider>{children}</LiveAuthorizationProvider>
}

function LiveAuthorizationProvider({ children }: { children: ReactNode }) {
  const isAuthenticated = useIsAuthenticated()
  const [loading, setLoading] = useState(false)
  const [roles, setRoles] = useState<string[]>([])
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [caps, setCaps] = useState<Capabilities>(emptyCapabilities)

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setUser(null)
      setRoles([])
      setCaps(emptyCapabilities)
      setError(null)
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const me = await getMe()
      const roleSet = collectRoles(me.roles ?? [], me.authorities ?? [])
      setUser(me)
      setRoles([...roleSet].sort())
      setCaps(deriveCapabilities(roleSet))
    } catch (e) {
      setUser(null)
      setRoles([])
      setCaps(emptyCapabilities)
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [isAuthenticated])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const value = useMemo<AuthorizationState>(
    () => ({
      loading,
      roles,
      user,
      error,
      refresh,
      ...caps,
    }),
    [loading, roles, user, error, refresh, caps],
  )

  return <AuthorizationContext.Provider value={value}>{children}</AuthorizationContext.Provider>
}

export function useAuthorization(): AuthorizationState {
  const ctx = useContext(AuthorizationContext)
  if (!ctx) {
    throw new Error('useAuthorization must be used within AuthorizationProvider')
  }
  return ctx
}
