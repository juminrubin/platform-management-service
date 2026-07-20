/**
 * Entra app role values for this API (must match backend AppRoles / JWT `roles` claim).
 */
export const AppRoles = {
  SYSTEM_MAINTAINER: 'System.Maintainer',
  SYSTEM_READER: 'System.Reader',
  ENTITLEMENT_READER: 'Entitlement.Reader',
  CONSUMPTION_REGISTRATOR: 'Consumption.Registrator',
} as const

export type AppRole = (typeof AppRoles)[keyof typeof AppRoles]

export type Capabilities = {
  /** Full CRUD (System.Maintainer). */
  canMaintain: boolean
  /** List/get admin resources (System.Maintainer | System.Reader). */
  canRead: boolean
  /** Entitlement check (maintainer | system reader | Entitlement.Reader). */
  canCheckEntitlement: boolean
  /** POST consumption (maintainer | Consumption.Registrator). */
  canRegisterConsumption: boolean
}

/** Strip optional `ROLE_` prefix and normalize claim values. */
export function normalizeRole(value: string): string {
  const trimmed = value.trim()
  if (trimmed.startsWith('ROLE_')) {
    return trimmed.slice('ROLE_'.length)
  }
  return trimmed
}

export function collectRoles(roles: string[] = [], authorities: string[] = []): Set<string> {
  const out = new Set<string>()
  for (const r of roles) {
    const n = normalizeRole(r)
    if (n) out.add(n)
  }
  for (const a of authorities) {
    const n = normalizeRole(a)
    if (n) out.add(n)
  }
  return out
}

export function deriveCapabilities(roleSet: Set<string>): Capabilities {
  const has = (...wanted: string[]) => wanted.some((r) => roleSet.has(r))
  return {
    canMaintain: has(AppRoles.SYSTEM_MAINTAINER),
    canRead: has(AppRoles.SYSTEM_MAINTAINER, AppRoles.SYSTEM_READER),
    canCheckEntitlement: has(
      AppRoles.SYSTEM_MAINTAINER,
      AppRoles.SYSTEM_READER,
      AppRoles.ENTITLEMENT_READER,
    ),
    canRegisterConsumption: has(AppRoles.SYSTEM_MAINTAINER, AppRoles.CONSUMPTION_REGISTRATOR),
  }
}

export const emptyCapabilities: Capabilities = {
  canMaintain: false,
  canRead: false,
  canCheckEntitlement: false,
  canRegisterConsumption: false,
}

/** Test / Storybook helper: full maintainer access. */
export const maintainerCapabilities: Capabilities = deriveCapabilities(
  new Set([AppRoles.SYSTEM_MAINTAINER]),
)

/** Test helper: read-only system access. */
export const readerCapabilities: Capabilities = deriveCapabilities(new Set([AppRoles.SYSTEM_READER]))
