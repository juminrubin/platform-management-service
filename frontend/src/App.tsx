import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { MsalProvider, useIsAuthenticated } from '@azure/msal-react'
import { msalInstance } from './auth/msalConfig'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { MePage } from './pages/MePage'
import { ParticipantListPage } from './pages/participants/ParticipantListPage'
import { ParticipantDetailPage } from './pages/participants/ParticipantDetailPage'
import { ParticipantFormPage } from './pages/participants/ParticipantFormPage'
import { CallerRegistrationListPage } from './pages/callerRegistrations/CallerRegistrationListPage'
import { CallerRegistrationDetailPage } from './pages/callerRegistrations/CallerRegistrationDetailPage'
import { CallerRegistrationFormPage } from './pages/callerRegistrations/CallerRegistrationFormPage'
import { ServiceOfferingListPage } from './pages/serviceOfferings/ServiceOfferingListPage'
import { ServiceOfferingDetailPage } from './pages/serviceOfferings/ServiceOfferingDetailPage'
import { ServiceOfferingFormPage } from './pages/serviceOfferings/ServiceOfferingFormPage'
import { EntitlementListPage } from './pages/entitlements/EntitlementListPage'
import { EntitlementDetailPage } from './pages/entitlements/EntitlementDetailPage'
import { EntitlementFormPage } from './pages/entitlements/EntitlementFormPage'
import { ConsumptionListPage } from './pages/consumptions/ConsumptionListPage'
import { ConsumptionDetailPage } from './pages/consumptions/ConsumptionDetailPage'
import { ConsumptionFormPage } from './pages/consumptions/ConsumptionFormPage'
import './App.css'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useIsAuthenticated()
  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}

function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route
            path="/me"
            element={
              <RequireAuth>
                <MePage />
              </RequireAuth>
            }
          />

          <Route
            path="/participants"
            element={
              <RequireAuth>
                <ParticipantListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/participants/new"
            element={
              <RequireAuth>
                <ParticipantFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="/participants/:id"
            element={
              <RequireAuth>
                <ParticipantDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="/participants/:id/edit"
            element={
              <RequireAuth>
                <ParticipantFormPage />
              </RequireAuth>
            }
          />

          <Route
            path="/caller-registrations"
            element={
              <RequireAuth>
                <CallerRegistrationListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/caller-registrations/new"
            element={
              <RequireAuth>
                <CallerRegistrationFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="/caller-registrations/:callerId"
            element={
              <RequireAuth>
                <CallerRegistrationDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="/caller-registrations/:callerId/edit"
            element={
              <RequireAuth>
                <CallerRegistrationFormPage />
              </RequireAuth>
            }
          />

          <Route
            path="/service-offerings"
            element={
              <RequireAuth>
                <ServiceOfferingListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/service-offerings/new"
            element={
              <RequireAuth>
                <ServiceOfferingFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="/service-offerings/:id"
            element={
              <RequireAuth>
                <ServiceOfferingDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="/service-offerings/:id/edit"
            element={
              <RequireAuth>
                <ServiceOfferingFormPage />
              </RequireAuth>
            }
          />

          <Route
            path="/entitlements"
            element={
              <RequireAuth>
                <EntitlementListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/entitlements/new"
            element={
              <RequireAuth>
                <EntitlementFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="/entitlements/:id"
            element={
              <RequireAuth>
                <EntitlementDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="/entitlements/:id/edit"
            element={
              <RequireAuth>
                <EntitlementFormPage />
              </RequireAuth>
            }
          />

          <Route
            path="/consumptions"
            element={
              <RequireAuth>
                <ConsumptionListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/consumptions/new"
            element={
              <RequireAuth>
                <ConsumptionFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="/consumptions/:id"
            element={
              <RequireAuth>
                <ConsumptionDetailPage />
              </RequireAuth>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default function App() {
  return (
    <MsalProvider instance={msalInstance}>
      <AppRoutes />
    </MsalProvider>
  )
}
