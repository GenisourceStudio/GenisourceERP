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
  milestone?: string
}

export interface NavGroup {
  key: string
  items: NavItem[]
}

export const NAV: NavGroup[] = [
  {
    key: 'overview',
    items: [{ path: '/', key: 'dashboard' }],
  },
  {
    key: 'masterdata',
    items: [
      { path: '/masterdata/items', key: 'item' },
      { path: '/masterdata/bom', key: 'bom' },
      { path: '/masterdata/partners', key: 'partner' },
    ],
  },
  {
    key: 'inventory',
    items: [
      { path: '/inventory/stock', key: 'stock' },
      { path: '/inventory/documents', key: 'inventoryDocument' },
      { path: '/inventory/movements', key: 'movement' },
      { path: '/inventory/warehouses', key: 'warehouse' },
    ],
  },
  {
    key: 'trade',
    items: [
      { path: '/purchase/requisitions', key: 'requisition' },
      { path: '/purchase/orders', key: 'purchaseOrder' },
      { path: '/purchase/receipts', key: 'receipt' },
      { path: '/sales/quotations', key: 'quotation' },
      { path: '/sales/orders', key: 'salesOrder' },
      { path: '/sales/shipments', key: 'shipment' },
    ],
  },
  {
    key: 'planning',
    items: [
      { path: '/planning/mrp', key: 'mrpRun' },
      { path: '/planning/production-orders', key: 'productionOrder' },
    ],
  },
  {
    key: 'finance',
    items: [
      { path: '/finance/receivables', key: 'receivable' },
      { path: '/finance/payables', key: 'payable' },
      { path: '/finance/accounts', key: 'account' },
      { path: '/finance/vouchers', key: 'voucher' },
      { path: '/finance/reports', key: 'report' },
    ],
  },
  {
    key: 'system',
    items: [
      // M1 shipped master data, not user management: keep the badge honest.
      { path: '/system/users', key: 'user', milestone: 'M2' },
      { path: '/system/audit', key: 'audit', milestone: 'M2' },
      { path: '/system/integration', key: 'integration' },
    ],
  },
]

/** Flattened view used by the router and the page title logic. */
export const NAV_ITEMS: NavItem[] = NAV.flatMap((group) => group.items)

export function findNavItem(pathname: string): NavItem | undefined {
  return NAV_ITEMS.find((item) => item.path === pathname)
}
