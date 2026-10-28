import type { IconName } from '../shared/ui/Icon'

/**
 * Navigation is data, not markup: the rail, the router and the placeholder pages are all
 * generated from this table. Adding a module means adding one entry here.
 *
 * `milestone` marks modules that are not built yet. They render a placeholder instead of
 * a broken screen, which keeps the shell honest about what M0 actually ships.
 */
export interface NavItem {
  path: string
  key: string
  /** Rail glyph. Named in `shared/ui/Icon`. */
  icon: IconName
  milestone?: string
}

export interface NavGroup {
  key: string
  items: NavItem[]
}

export const NAV: NavGroup[] = [
  {
    key: 'overview',
    items: [{ path: '/', key: 'dashboard', icon: 'grid' }],
  },
  {
    key: 'masterdata',
    items: [
      { path: '/masterdata/items', key: 'item', icon: 'cube' },
      { path: '/masterdata/bom', key: 'bom', icon: 'layers' },
      { path: '/masterdata/partners', key: 'partner', icon: 'users' },
    ],
  },
  {
    key: 'inventory',
    items: [
      { path: '/inventory/stock', key: 'stock', icon: 'package' },
      { path: '/inventory/documents', key: 'inventoryDocument', icon: 'file-text' },
      { path: '/inventory/movements', key: 'movement', icon: 'swap' },
      { path: '/inventory/warehouses', key: 'warehouse', icon: 'warehouse' },
    ],
  },
  {
    key: 'trade',
    items: [
      { path: '/purchase/requisitions', key: 'requisition', icon: 'clipboard' },
      { path: '/purchase/orders', key: 'purchaseOrder', icon: 'truck' },
      { path: '/purchase/receipts', key: 'receipt', icon: 'download' },
      { path: '/sales/quotations', key: 'quotation', icon: 'calculator' },
      { path: '/sales/orders', key: 'salesOrder', icon: 'ledger' },
      { path: '/sales/shipments', key: 'shipment', icon: 'upload' },
    ],
  },
  {
    key: 'planning',
    items: [
      { path: '/planning/mrp', key: 'mrpRun', icon: 'bolt' },
      { path: '/planning/production-orders', key: 'productionOrder', icon: 'factory' },
    ],
  },
  {
    key: 'finance',
    items: [
      { path: '/finance/receivables', key: 'receivable', icon: 'arrow-down-left' },
      { path: '/finance/payables', key: 'payable', icon: 'arrow-up-right' },
      { path: '/finance/accounts', key: 'account', icon: 'tag' },
      { path: '/finance/vouchers', key: 'voucher', icon: 'receipt' },
      { path: '/finance/reports', key: 'report', icon: 'chart' },
    ],
  },
  {
    key: 'system',
    items: [
      { path: '/system/users', key: 'user', icon: 'shield' },
      { path: '/system/audit', key: 'audit', icon: 'history' },
      { path: '/system/integration', key: 'integration', icon: 'link' },
    ],
  },
]

/** Flattened view used by the router and the page title logic. */
export const NAV_ITEMS: NavItem[] = NAV.flatMap((group) => group.items)

export function findNavItem(pathname: string): NavItem | undefined {
  return NAV_ITEMS.find((item) => item.path === pathname)
}
