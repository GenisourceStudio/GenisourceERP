import type { PageResponse } from '../../shared/api/masterdata'

/**
 * The structural shape every trade document satisfies.
 *
 * <p>Purchase and sales are the same envelope seen from opposite ends, and the screens
 * for them should be one screen rather than six. These types are what the shared page
 * needs and nothing more; the concrete {@code PurchaseDocumentView} and
 * {@code SalesDocumentView} carry extra fields on top and still fit here, which is the
 * point — a sales order has a reserved quantity a purchase order has no use for, and the
 * page reads it only when the config says to.
 */

export type DocumentLifecycle =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'PARTIAL'
  | 'COMPLETED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'POSTED'
  | 'REVERSED'

/** Every status a trade document can be in, for the filter dropdown. */
export const LIFECYCLE_STATUSES: DocumentLifecycle[] = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'REJECTED',
  'PARTIAL',
  'COMPLETED',
  'CLOSED',
  'CANCELLED',
  'POSTED',
  'REVERSED',
]

/** Statuses that still expect something to happen: the useful default filter. */
export const OPEN_STATUSES: DocumentLifecycle[] = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'PARTIAL',
]

export interface TradeLine {
  id: number
  lineNo: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  qty: number
  unitPrice: number
  taxRate: number
  netAmount: number
  taxAmount: number
  totalAmount: number
  qtyFulfilled: number
  openQty: number
  lotId: number | null
  lotNo: string | null
  sourceLineId: number | null
  remark: string | null
  /** Sales only: how much of this line stock has promised but not yet shipped. */
  qtyReserved?: number
}

export interface TradeDocument {
  id: number
  docNo: string
  docType: string
  status: DocumentLifecycle
  partnerId: number | null
  partnerCode: string | null
  partnerName: string | null
  warehouseId: number | null
  warehouseCode: string | null
  warehouseName: string | null
  locationId: number | null
  locationName: string | null
  bizDate: string
  requiredDate: string | null
  paymentTermDays: number
  netAmount: number
  taxAmount: number
  totalAmount: number
  totalQty: number
  sourceType: string | null
  sourceId: string | null
  submittedBy: string | null
  approvedBy: string | null
  approvedAt: string | null
  approvalRemark: string | null
  postedAt: string | null
  reversalOf: number | null
  reversalOfNo: string | null
  remark: string | null
  rowVersion: number
  lines: TradeLine[]
}

export interface TradeLineRequest {
  itemId: number
  qty: number
  unitPrice?: number | null
  taxRate?: number | null
  lotId?: number | null
  remark?: string | null
}

export interface TradeDocumentRequest {
  partnerId?: number | null
  warehouseId?: number | null
  locationId?: number | null
  bizDate: string
  requiredDate?: string | null
  paymentTermDays?: number | null
  remark?: string | null
  lines: TradeLineRequest[]
}

export interface TradeQuery {
  keyword?: string
  partnerId?: number | null
  status?: DocumentLifecycle | null
  from?: string | null
  to?: string | null
  page?: number
  size?: number
}

export interface ApprovalRequest {
  remark?: string | null
}

export interface PickLine {
  lineId: number
  qty: number
  lotId?: number | null
}

export interface ConvertRequest {
  partnerId?: number | null
  bizDate?: string | null
  remark?: string | null
  lines?: PickLine[] | null
}

export interface FulfilRequest {
  warehouseId: number
  locationId?: number | null
  bizDate: string
  remark?: string | null
  lines: PickLine[]
}

/**
 * What one document kind needs from the shared page.
 *
 * <p>Each entry is either an endpoint or a fact about the kind. The transitions are
 * optional because not every document has all of them: a requisition is converted, an
 * order is received against, a receipt is posted, and none of them can do the others'
 * jobs. Leaving the key out is how the page knows not to offer the button.
 */
export interface TradeConfig {
  /** i18n key of this document kind, e.g. {@code purchase.kind.order}. */
  kindKey: string
  /** i18n key of the label shown in the type column, e.g. {@code docType.PURCHASE_ORDER}. */
  docTypeKey: string
  readPermission: string
  writePermission: string
  /** Which partner list the header picker searches: suppliers or customers. */
  partnerRole: 'SUPPLIER' | 'CUSTOMER'
  /** A requisition has no supplier yet — that is what converting it is for. */
  needsPartner: boolean
  /** A requisition is not tied to a warehouse either. */
  needsWarehouse: boolean
  /** Sales order lines show how much stock has been promised against them. */
  showReserved?: boolean

  list: (query: TradeQuery) => Promise<PageResponse<TradeDocument>>
  get: (id: number) => Promise<TradeDocument>
  create: (request: TradeDocumentRequest) => Promise<TradeDocument>
  update: (id: number, request: TradeDocumentRequest) => Promise<TradeDocument>
  remove?: (id: number) => Promise<void>
  submit?: (id: number) => Promise<TradeDocument>
  approve?: (id: number, request: ApprovalRequest) => Promise<TradeDocument>
  reject?: (id: number, request: ApprovalRequest) => Promise<TradeDocument>
  /** Creates the next document in the chain from this one. */
  convert?: (id: number, request: ConvertRequest) => Promise<TradeDocument>
  /** Creates the document that moves goods: a receipt, or a shipment. */
  fulfil?: (id: number, request: FulfilRequest) => Promise<TradeDocument>
  post?: (id: number) => Promise<TradeDocument>
  reverse?: (id: number) => Promise<TradeDocument>
  close?: (id: number) => Promise<TradeDocument>
  cancel?: (id: number) => Promise<TradeDocument>

  /** i18n key of what {@code convert} produces, e.g. {@code purchase.kind.order}. */
  convertKindKey?: string
  /** i18n key of what {@code fulfil} produces, e.g. {@code purchase.kind.receipt}. */
  fulfilKindKey?: string
  /** Query keys to refresh after a write, on top of this page's own list. */
  invalidates?: string[][]
  /** Whether {@code fulfil} needs a warehouse and a date asked for in the dialog. */
  fulfilAsksWarehouse?: boolean
}
