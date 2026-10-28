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
import IntegrationPage from '../pages/integration/IntegrationPage'
import DocumentsPage from '../pages/inventory/DocumentsPage'
import MovementsPage from '../pages/inventory/MovementsPage'
import StockPage from '../pages/inventory/StockPage'
import WarehousesPage from '../pages/inventory/WarehousesPage'
import AccountsPage from '../pages/finance/AccountsPage'
import PayablesPage from '../pages/finance/PayablesPage'
import ReceivablesPage from '../pages/finance/ReceivablesPage'
import ReportsPage from '../pages/finance/ReportsPage'
import VouchersPage from '../pages/finance/VouchersPage'
import MrpPage from '../pages/planning/MrpPage'
import ProductionOrdersPage from '../pages/planning/ProductionOrdersPage'
import TradeDocumentPage from '../pages/trade/TradeDocumentPage'
import {
  PURCHASE_ORDER,
  QUOTATION,
  RECEIPT,
  REQUISITION,
  SALES_ORDER,
  SHIPMENT,
} from '../pages/trade/configs'
import { useAuthStore } from '../shared/auth/store'

/**
 * Nav entries map to real screens once a milestone ships. Anything missing falls back to
 * the placeholder, so adding a nav item never produces a broken route.
 */
const PAGES: Record<string, ReactNode> = {
  '/masterdata/items': <ItemsPage />,
  '/masterdata/bom': <BomsPage />,
  '/masterdata/partners': <PartnersPage />,
  '/inventory/stock': <StockPage />,
  '/inventory/documents': <DocumentsPage />,
  '/inventory/movements': <MovementsPage />,
  '/inventory/warehouses': <WarehousesPage />,
  '/purchase/requisitions': <TradeDocumentPage config={REQUISITION} />,
  '/purchase/orders': <TradeDocumentPage config={PURCHASE_ORDER} />,
  '/purchase/receipts': <TradeDocumentPage config={RECEIPT} />,
  '/sales/quotations': <TradeDocumentPage config={QUOTATION} />,
  '/sales/orders': <TradeDocumentPage config={SALES_ORDER} />,
  '/sales/shipments': <TradeDocumentPage config={SHIPMENT} />,
  '/planning/mrp': <MrpPage />,
  '/planning/production-orders': <ProductionOrdersPage />,
  '/finance/receivables': <ReceivablesPage />,
  '/finance/payables': <PayablesPage />,
  '/finance/accounts': <AccountsPage />,
  '/finance/vouchers': <VouchersPage />,
  '/finance/reports': <ReportsPage />,
  '/system/integration': <IntegrationPage />,
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
