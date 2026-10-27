import type { ReactNode } from 'react'
import { Navigate, Outlet, Route, Routes } from 'react-router-dom'

import AppShell from './AppShell'
import { NAV_ITEMS } from './nav'
import DashboardPage from '../pages/DashboardPage'
import LoginPage from '../pages/LoginPage'
import NotFoundPage from '../pages/NotFoundPage'
import PlaceholderPage from '../pages/PlaceholderPage'
import BomsPage from '../pages/masterdata/BomsPage'
import ItemsPage from '../pages/masterdata/ItemsPage'
import PartnersPage from '../pages/masterdata/PartnersPage'
import { useAuthStore } from '../shared/auth/store'

/**
 * Nav entries map to real screens once a milestone ships. Anything missing falls back to
 * the placeholder, so adding a nav item never produces a broken route.
 */
const PAGES: Record<string, ReactNode> = {
  '/masterdata/items': <ItemsPage />,
  '/masterdata/bom': <BomsPage />,
  '/masterdata/partners': <PartnersPage />,
}

function RequireAuth() {
  const accessToken = useAuthStore((state) => state.accessToken)
  if (!accessToken) {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}

export default function App() {
  const accessToken = useAuthStore((state) => state.accessToken)

  return (
    <Routes>
      <Route
        path="/login"
        element={accessToken ? <Navigate to="/" replace /> : <LoginPage />}
      />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          {NAV_ITEMS.filter((item) => item.path !== '/').map((item) => (
            <Route
              key={item.path}
              path={item.path}
              element={
                PAGES[item.path] ?? (
                  <PlaceholderPage moduleKey={`nav.${item.key}`} milestone={item.milestone ?? ''} />
                )
              }
            />
          ))}
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
