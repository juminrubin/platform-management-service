import { describe, expect, it } from 'vitest'
import {
  AppRoles,
  collectRoles,
  deriveCapabilities,
  normalizeRole,
  readerCapabilities,
} from './capabilities'

describe('capabilities', () => {
  it('normalizes ROLE_ prefix', () => {
    expect(normalizeRole('ROLE_System.Maintainer')).toBe('System.Maintainer')
    expect(normalizeRole('System.Reader')).toBe('System.Reader')
  })

  it('collects roles from claims and authorities', () => {
    const set = collectRoles(['System.Reader'], ['ROLE_System.Maintainer', 'SCOPE_access_as_user'])
    expect(set.has(AppRoles.SYSTEM_READER)).toBe(true)
    expect(set.has(AppRoles.SYSTEM_MAINTAINER)).toBe(true)
    // scopes are not app roles; only ROLE_ prefix is stripped
    expect(set.has('SCOPE_access_as_user')).toBe(true)
  })

  it('reader can read but not maintain or register consumption', () => {
    expect(readerCapabilities.canRead).toBe(true)
    expect(readerCapabilities.canMaintain).toBe(false)
    expect(readerCapabilities.canRegisterConsumption).toBe(false)
    expect(readerCapabilities.canCheckEntitlement).toBe(true)
  })

  it('consumption registrator can register but not maintain', () => {
    const caps = deriveCapabilities(new Set([AppRoles.CONSUMPTION_REGISTRATOR]))
    expect(caps.canRegisterConsumption).toBe(true)
    expect(caps.canMaintain).toBe(false)
    expect(caps.canRead).toBe(false)
  })

  it('ignores blank role strings', () => {
    const set = collectRoles(['', '  ', AppRoles.SYSTEM_READER], ['ROLE_', ' ROLE_System.Reader '])
    expect(set.has('')).toBe(false)
    expect(set.has(AppRoles.SYSTEM_READER)).toBe(true)
  })

  it('entitlement reader can check but not maintain or read lists', () => {
    const caps = deriveCapabilities(new Set([AppRoles.ENTITLEMENT_READER]))
    expect(caps.canCheckEntitlement).toBe(true)
    expect(caps.canMaintain).toBe(false)
    expect(caps.canRead).toBe(false)
  })
})
