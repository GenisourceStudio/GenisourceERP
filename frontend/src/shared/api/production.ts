import { apiFetch } from './client'
import type { PageResponse } from './masterdata'

/**
 * Typed client for works orders.
 *
 * <p>Four transitions that read like the shop floor: raise it, release it (which sets the
 * material aside), issue the material, and book the finished goods back in. Closing and
 * cancelling exist so nothing has to be deleted to make a mistake go away.
 */

export type { PageResponse } from './masterdata'

export type ProductionOrderStatus =
  | 'PLANNED'
  | 'RELEASED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CLOSED'
  | 'CANCELLED'

export type OperationStatus = 'PENDING' | 'DONE'

export interface ProductionLineView {
  id: number
  lineNo: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  qtyRequired: number
  qtyIssued: number
  qtyLocked: number
  openQty: number
  warehouseId: number | null
  warehouseCode: string | null
  remark: string | null
}

export interface ProductionOperationView {
  sequence: number
  name: string
  workCenterId: number | null
  workCenterCode: string | null
  setupMinutes: number
  runMinutes: number
  status: OperationStatus
  completedAt: string | null
}

export interface ProductionOrderView {
  id: number
  docNo: string
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  bomId: number | null
  bomVersion: string | null
  qtyPlanned: number
  qtyCompleted: number
  remainingQty: number
  status: ProductionOrderStatus
  warehouseId: number | null
  warehouseCode: string | null
  locationId: number | null
  locationName: string | null
  plannedStart: string | null
  plannedEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  mesWoNo: string | null
  sourceType: string | null
  sourceId: string | null
  remark: string | null
  rowVersion: number
  lines: ProductionLineView[]
  operations: ProductionOperationView[]
}

export interface ProductionLineRequest {
  itemId: number
  qtyRequired: number
  warehouseId?: number | null
  remark?: string | null
}

export interface ProductionOrderRequest {
  itemId: number
  qtyPlanned: number
  bomId?: number | null
  warehouseId?: number | null
  locationId?: number | null
  plannedStart?: string | null
  plannedEnd?: string | null
  remark?: string | null
  /** Empty means "expand the released BOM for the planned quantity". */
  lines?: ProductionLineRequest[]
}

export interface ProductionIssueLine {
  orderLineId: number
  qty: number
  lotId?: number | null
}

export interface ProductionIssueRequest {
  bizDate: string
  remark?: string | null
  lines: ProductionIssueLine[]
}

export interface ProductionCompleteRequest {
  qty: number
  warehouseId: number
  locationId?: number | null
  bizDate: string
  remark?: string | null
}

export interface ProductionOrderQuery {
  keyword?: string
  itemId?: number | null
  status?: ProductionOrderStatus | null
  from?: string | null
  to?: string | null
  page?: number
  size?: number
}

function queryString(query: ProductionOrderQuery): string {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.itemId) params.set('itemId', String(query.itemId))
  if (query.status) params.set('status', query.status)
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return params.toString()
}

export function listProductionOrders(
  query: ProductionOrderQuery = {},
): Promise<PageResponse<ProductionOrderView>> {
  return apiFetch<PageResponse<ProductionOrderView>>(
    `/api/production/orders?${queryString(query)}`,
  )
}

export function getProductionOrder(id: number): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>(`/api/production/orders/${id}`)
}

export function createProductionOrder(
  request: ProductionOrderRequest,
): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>('/api/production/orders', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateProductionOrder(
  id: number,
  request: ProductionOrderRequest,
): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>(`/api/production/orders/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export async function deleteProductionOrder(id: number): Promise<void> {
  await apiFetch<void>(`/api/production/orders/${id}`, { method: 'DELETE' })
}

export function releaseOrder(id: number): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>(`/api/production/orders/${id}/release`, {
    method: 'POST',
  })
}

export function issueMaterial(
  id: number,
  request: ProductionIssueRequest,
): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>(`/api/production/orders/${id}/issue`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function completeOrder(
  id: number,
  request: ProductionCompleteRequest,
): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>(`/api/production/orders/${id}/complete`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function closeOrder(id: number): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>(`/api/production/orders/${id}/close`, {
    method: 'POST',
  })
}

export function cancelOrder(id: number): Promise<ProductionOrderView> {
  return apiFetch<ProductionOrderView>(`/api/production/orders/${id}/cancel`, {
    method: 'POST',
  })
}
