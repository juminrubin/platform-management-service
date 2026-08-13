/**
 * Resource ids are business keys and may contain `/` (e.g. Group1/Service1a).
 * React Router decodes `%2F` before matching `:id`, so slash-containing ids
 * must be read from the remainder of the path, not a single path segment.
 */

export function decodePathSegment(raw: string): string {
  try {
    return decodeURIComponent(raw)
  } catch {
    return raw
  }
}

export function serviceOfferingIdFromPath(pathname: string): {
  id: string
  isEdit: boolean
  isNew: boolean
} {
  const prefix = '/service-offerings/'
  const start = pathname.indexOf(prefix)
  if (start < 0) {
    return { id: '', isEdit: false, isNew: false }
  }
  const rest = decodePathSegment(pathname.slice(start + prefix.length))
  if (rest === 'new') {
    return { id: '', isEdit: false, isNew: true }
  }
  const isEdit = rest.endsWith('/edit')
  const id = isEdit ? rest.slice(0, -'/edit'.length) : rest
  return { id, isEdit, isNew: false }
}

export function serviceOfferingDetailPath(id: string): string {
  return `/service-offerings/${encodeURIComponent(id)}`
}

export function serviceOfferingEditPath(id: string): string {
  return `${serviceOfferingDetailPath(id)}/edit`
}
