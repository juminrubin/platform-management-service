import { useLocation } from 'react-router-dom'
import { RequireCapability } from '../../auth/RequireCapability'
import { serviceOfferingIdFromPath } from '../../routing/resourcePath'
import { ServiceOfferingDetailPage } from './ServiceOfferingDetailPage'
import { ServiceOfferingFormPage } from './ServiceOfferingFormPage'

/** Detail or edit for ids that may contain `/`. */
export function ServiceOfferingResourcePage() {
  const { isEdit } = serviceOfferingIdFromPath(useLocation().pathname)
  if (isEdit) {
    return (
      <RequireCapability capability="canMaintain" fallback="/service-offerings">
        <ServiceOfferingFormPage />
      </RequireCapability>
    )
  }
  return <ServiceOfferingDetailPage />
}
