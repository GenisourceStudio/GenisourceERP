import { apiFetch } from './client'
import type { PageResponse } from './masterdata'

/**
 * Typed client for the procurement API.
 *
 * <p>One document table carries requisitions, orders and receipts, so the shapes here
 * are shared by all three and the doc type is a field rather than a separate interface.
 * What differs is which transitions are legal, and that is expressed on the server —
 * asking for something out of order comes back as a 409 with a reason, not as silence.
 */

export type PurchaseDocType = 'REQUISITION' | 'ORDER' | 'RECEIPT'

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

export interface PurchaseLineView {
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
}

export interface PurchaseDocumentView {
  id: number
  docNo: string
  docType: PurchaseDocType
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
  lines: PurchaseLineView[]
}

export interface PurchaseLineRequest {
  itemId: number
  qty: number
  unitPrice?: number | null
  taxRate?: number | null
  lotId?: number | null
  remark?: string | null
}

export interface PurchaseDocumentRequest {
  partnerId?: number | null
  warehouseId?: number | null
  locationId?: number | null
  bizDate: string
  requiredDate?: string | null
  paymentTermDays?: number | null
  remark?: string | null
  lines: PurchaseLineRequest[]
}

/** Which lines of a source document to take, and how much of each. Empty means all. */
export interface PickLine {
  lineId: number
  qty: number
}

export interface ConvertRequest {
  partnerId?: number | null
  bizDate?: string | null
  remark?: string | null
  lines?: PickLine[] | null
}

export interface ReceiveRequest {
  warehouseId: number
  locationId?: number | null
  bizDate: string
  remark?: string | null
  lines: PickLine[]
}

export interface ApprovalRequest {
  remark?: string | null
}

export interface PurchaseQuery {
  keyword?: string
  partnerId?: number | null
  status?: DocumentLifecycle | null
  from?: string | null
  to?: string | null
  page?: number
  size?: number
}

function queryString(query: PurchaseQuery): string {
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

// -------------------------------------------------------------- requisitions

export function listRequisitions(
  query: PurchaseQuery = {},
): Promise<PageResponse<PurchaseDocumentView>> {
  return apiFetch(`/api/purchase/requisitions?${queryString(query)}`)
}

export function getRequisition(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/requisitions/${id}`)
}

export function createRequisition(
  request: PurchaseDocumentRequest,
): Promise<PurchaseDocumentView> {
  return apiFetch('/api/purchase/requisitions', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateRequisition(
  id: number,
  request: PurchaseDocumentRequest,
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/requisitions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteRequisition(id: number): Promise<void> {
  return apiFetch(`/api/purchase/requisitions/${id}`, { method: 'DELETE' })
}

export function submitRequisition(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/requisitions/${id}/submit`, { method: 'POST' })
}

export function approveRequisition(
  id: number,
  request: ApprovalRequest = {},
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/requisitions/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function rejectRequisition(
  id: number,
  request: ApprovalRequest = {},
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/requisitions/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function convertRequisition(
  id: number,
  request: ConvertRequest = {},
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/requisitions/${id}/convert`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function cancelRequisition(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/requisitions/${id}/cancel`, { method: 'POST' })
}

// -------------------------------------------------------------------- orders

export function listPurchaseOrders(
  query: PurchaseQuery = {},
): Promise<PageResponse<PurchaseDocumentView>> {
  return apiFetch(`/api/purchase/orders?${queryString(query)}`)
}

export function getPurchaseOrder(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}`)
}

export function createPurchaseOrder(
  request: PurchaseDocumentRequest,
): Promise<PurchaseDocumentView> {
  return apiFetch('/api/purchase/orders', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updatePurchaseOrder(
  id: number,
  request: PurchaseDocumentRequest,
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deletePurchaseOrder(id: number): Promise<void> {
  return apiFetch(`/api/purchase/orders/${id}`, { method: 'DELETE' })
}

export function submitPurchaseOrder(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}/submit`, { method: 'POST' })
}

export function approvePurchaseOrder(
  id: number,
  request: ApprovalRequest = {},
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function rejectPurchaseOrder(
  id: number,
  request: ApprovalRequest = {},
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function receivePurchaseOrder(
  id: number,
  request: ReceiveRequest,
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}/receive`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function closePurchaseOrder(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}/close`, { method: 'POST' })
}

export function cancelPurchaseOrder(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/orders/${id}/cancel`, { method: 'POST' })
}

// ------------------------------------------------------------------ receipts

export function listReceipts(
  query: PurchaseQuery = {},
): Promise<PageResponse<PurchaseDocumentView>> {
  return apiFetch(`/api/purchase/receipts?${queryString(query)}`)
}

export function getReceipt(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/receipts/${id}`)
}

export function createReceipt(
  request: PurchaseDocumentRequest,
): Promise<PurchaseDocumentView> {
  return apiFetch('/api/purchase/receipts', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateReceipt(
  id: number,
  request: PurchaseDocumentRequest,
): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/receipts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function postReceipt(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/receipts/${id}/post`, { method: 'POST' })
}

export function reverseReceipt(id: number): Promise<PurchaseDocumentView> {
  return apiFetch(`/api/purchase/receipts/${id}/reverse`, { method: 'POST' })
}
