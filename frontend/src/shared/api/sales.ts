import { apiFetch } from './client'
import type { PageResponse } from './masterdata'

/**
 * Typed client for the sales API.
 *
 * <p>Mirrors the procurement client because the two domains are the same shape viewed
 * from opposite ends: a quotation becomes an order becomes a shipment, each line
 * remembering the line that asked for it. The one thing that has no purchase-side
 * counterpart is {@code qtyReserved} — how much of a line has been promised out of
 * stock but not yet shipped, which is what stops two orders selling the same units.
 */

export type SalesDocType = 'QUOTATION' | 'ORDER' | 'SHIPMENT'

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

export interface SalesLineView {
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
  /** How much of this line stock has been promised for. */
  qtyReserved: number
  openQty: number
  lotId: number | null
  lotNo: string | null
  sourceLineId: number | null
  remark: string | null
}

export interface SalesDocumentView {
  id: number
  docNo: string
  docType: SalesDocType
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
  /** Whether this order has already taken stock out of the free pool. */
  stockReserved: boolean
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
  lines: SalesLineView[]
}

export interface SalesLineRequest {
  itemId: number
  qty: number
  unitPrice?: number | null
  taxRate?: number | null
  lotId?: number | null
  remark?: string | null
}

export interface SalesDocumentRequest {
  partnerId?: number | null
  warehouseId?: number | null
  locationId?: number | null
  bizDate: string
  requiredDate?: string | null
  paymentTermDays?: number | null
  remark?: string | null
  lines: SalesLineRequest[]
}

/** Which lines of a source document to take, and how much of each. Empty means all. */
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

export interface ShipRequest {
  warehouseId: number
  locationId?: number | null
  bizDate: string
  remark?: string | null
  lines: PickLine[]
}

export interface ApprovalRequest {
  remark?: string | null
}

export interface SalesQuery {
  keyword?: string
  partnerId?: number | null
  status?: DocumentLifecycle | null
  from?: string | null
  to?: string | null
  page?: number
  size?: number
}

function queryString(query: SalesQuery): string {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.partnerId) params.set('partnerId', String(query.partnerId))
  if (query.status) params.set('status', query.status)
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return params.toString()
}

// ---------------------------------------------------------------- quotations

export function listQuotations(
  query: SalesQuery = {},
): Promise<PageResponse<SalesDocumentView>> {
  return apiFetch(`/api/sales/quotations?${queryString(query)}`)
}

export function getQuotation(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/quotations/${id}`)
}

export function createQuotation(
  request: SalesDocumentRequest,
): Promise<SalesDocumentView> {
  return apiFetch('/api/sales/quotations', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateQuotation(
  id: number,
  request: SalesDocumentRequest,
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/quotations/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteQuotation(id: number): Promise<void> {
  return apiFetch(`/api/sales/quotations/${id}`, { method: 'DELETE' })
}

export function submitQuotation(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/quotations/${id}/submit`, { method: 'POST' })
}

export function approveQuotation(
  id: number,
  request: ApprovalRequest = {},
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/quotations/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function rejectQuotation(
  id: number,
  request: ApprovalRequest = {},
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/quotations/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function convertQuotation(
  id: number,
  request: ConvertRequest = {},
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/quotations/${id}/convert`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function cancelQuotation(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/quotations/${id}/cancel`, { method: 'POST' })
}

// -------------------------------------------------------------------- orders

export function listSalesOrders(
  query: SalesQuery = {},
): Promise<PageResponse<SalesDocumentView>> {
  return apiFetch(`/api/sales/orders?${queryString(query)}`)
}

export function getSalesOrder(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}`)
}

export function createSalesOrder(request: SalesDocumentRequest): Promise<SalesDocumentView> {
  return apiFetch('/api/sales/orders', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateSalesOrder(
  id: number,
  request: SalesDocumentRequest,
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteSalesOrder(id: number): Promise<void> {
  return apiFetch(`/api/sales/orders/${id}`, { method: 'DELETE' })
}

export function submitSalesOrder(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}/submit`, { method: 'POST' })
}

export function approveSalesOrder(
  id: number,
  request: ApprovalRequest = {},
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function rejectSalesOrder(
  id: number,
  request: ApprovalRequest = {},
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function shipSalesOrder(
  id: number,
  request: ShipRequest,
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}/ship`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function closeSalesOrder(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}/close`, { method: 'POST' })
}

export function cancelSalesOrder(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/orders/${id}/cancel`, { method: 'POST' })
}

// ----------------------------------------------------------------- shipments

export function listShipments(
  query: SalesQuery = {},
): Promise<PageResponse<SalesDocumentView>> {
  return apiFetch(`/api/sales/shipments?${queryString(query)}`)
}

export function getShipment(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/shipments/${id}`)
}

export function createShipment(request: SalesDocumentRequest): Promise<SalesDocumentView> {
  return apiFetch('/api/sales/shipments', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateShipment(
  id: number,
  request: SalesDocumentRequest,
): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/shipments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function postShipment(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/shipments/${id}/post`, { method: 'POST' })
}

export function reverseShipment(id: number): Promise<SalesDocumentView> {
  return apiFetch(`/api/sales/shipments/${id}/reverse`, { method: 'POST' })
}
