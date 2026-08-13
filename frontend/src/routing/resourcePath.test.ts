import { describe, expect, it } from 'vitest'
import {
  serviceOfferingDetailPath,
  serviceOfferingEditPath,
  serviceOfferingIdFromPath,
} from './resourcePath'

describe('serviceOfferingIdFromPath', () => {
  it('reads a simple id', () => {
    expect(serviceOfferingIdFromPath('/service-offerings/gpt-5.1')).toEqual({
      id: 'gpt-5.1',
      isEdit: false,
      isNew: false,
    })
  })

  it('reads an encoded slash id after React Router decodes %2F', () => {
    expect(serviceOfferingIdFromPath('/service-offerings/Group1/Service1a')).toEqual({
      id: 'Group1/Service1a',
      isEdit: false,
      isNew: false,
    })
  })

  it('reads an still-encoded slash id', () => {
    expect(serviceOfferingIdFromPath('/service-offerings/Group1%2FService1a')).toEqual({
      id: 'Group1/Service1a',
      isEdit: false,
      isNew: false,
    })
  })

  it('strips a trailing /edit for slash ids', () => {
    expect(serviceOfferingIdFromPath('/service-offerings/Group1/Service1a/edit')).toEqual({
      id: 'Group1/Service1a',
      isEdit: true,
      isNew: false,
    })
  })

  it('detects the create route', () => {
    expect(serviceOfferingIdFromPath('/service-offerings/new')).toEqual({
      id: '',
      isEdit: false,
      isNew: true,
    })
  })
})

describe('service offering path builders', () => {
  it('encodes slash ids for links', () => {
    expect(serviceOfferingDetailPath('Group1/Service1a')).toBe(
      '/service-offerings/Group1%2FService1a',
    )
    expect(serviceOfferingEditPath('Group1/Service1a')).toBe(
      '/service-offerings/Group1%2FService1a/edit',
    )
  })
})
