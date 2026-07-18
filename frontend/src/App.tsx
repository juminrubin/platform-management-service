import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { MsalProvider, useIsAuthenticated } from '@azure/msal-react'
import { msalInstance } from './auth/msalConfig'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { MePage } from './pages/MePage'
import { ParticipantListPage } from './pages/participants/ParticipantListPage'
import { ParticipantDetailPage } from './pages/participants/ParticipantDetailPage'
import { ParticipantFormPage } from './pages/participants/ParticipantFormPage'
import { CallerIdentityListPage } from './pages/callerIdentities/CallerIdentityListPage'
import { CallerIdentityDetailPage } from './pages/callerIdentities/CallerIdentityDetailPage'
import { CallerIdentityFormPage } from './pages/callerIdentities/CallerIdentityFormPage'
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
            path="/caller-identities"
            element={
              <RequireAuth>
                <CallerIdentityListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/caller-identities/new"
            element={
              <RequireAuth>
                <CallerIdentityFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="/caller-identities/:id"
            element={
              <RequireAuth>
                <CallerIdentityDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="/caller-identities/:id/edit"
            element={
              <RequireAuth>
                <CallerIdentityFormPage />
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
