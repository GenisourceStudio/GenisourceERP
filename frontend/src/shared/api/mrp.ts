import { apiFetch } from './client'
import type { PageResponse } from './masterdata'

/**
 * Typed client for material requirements planning.
 *
 * <p>A run is a snapshot: it is created, read, and never edited. What changes afterwards
 * is the plan rows hanging off it — each one can be turned into a real order or dropped,
 * and that decision is recorded so the next run can explain itself.
 */

export type { PageResponse } from './masterdata'

export type MrpSupplyType = 'PURCHASE' | 'PRODUCTION'
export type MrpPlanStatus = 'PLANNED' | 'CONVERTED' | 'IGNORED'

export interface MrpRunView {
  id: number
  runNo: string
  status: string
  horizonDays: number
  demandFrom: string | null
  demandTo: string | null
  includeSafety: boolean
  maxLevels: number
  itemCount: number
  supplyCount: number
  shortageCount: number
  runBy: string | null
  runAt: string
  remark: string | null
}

export interface MrpDemandView {
  id: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  itemLevel: number
  grossQty: number
  onHand: number
  reservedQty: number
  lockedQty: number
  inTransit: number
  safetyStock: number
  netQty: number
  requiredDate: string | null
  fromSales: number
  fromComponents: number
  shortage: boolean
}

export interface MrpPlanView {
  id: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  supplyType: MrpSupplyType
  qty: number
  netQty: number
  orderMultiple: number
  supplierId: number | null
  supplierName: string | null
  leadTimeDays: number
  startDate: string | null
  dueDate: string | null
  status: MrpPlanStatus
  convertedType: string | null
  convertedId: string | null
  convertedNo: string | null
  remark: string | null
}

export interface MrpRunDetail {
  run: MrpRunView
  demands: MrpDemandView[]
  plans: MrpPlanView[]
}

export interface MrpRunRequest {
  horizonDays?: number | null
  demandFrom?: string | null
  demandTo?: string | null
  includeSafety?: boolean | null
  maxLevels?: number | null
  warehouseId?: number | null
  remark?: string | null
}

export interface MrpConvertRequest {
  qty?: number | null
  supplierId?: number | null
  unitPrice?: number | null
  bizDate?: string | null
  warehouseId?: number | null
  remark?: string | null
}

export function listMrpRuns(page = 0, size = 20): Promise<PageResponse<MrpRunView>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch<PageResponse<MrpRunView>>(`/api/mrp/runs?${params.toString()}`)
}

export function getMrpRun(id: number): Promise<MrpRunDetail> {
  return apiFetch<MrpRunDetail>(`/api/mrp/runs/${id}`)
}

/** Runs the calculation. The response is the run it just created, evidence included. */
export function runMrp(request: MrpRunRequest = {}): Promise<MrpRunDetail> {
  return apiFetch<MrpRunDetail>('/api/mrp/runs', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function convertToPurchaseOrder(
  planId: number,
  request: MrpConvertRequest = {},
): Promise<MrpPlanView> {
  return apiFetch<MrpPlanView>(`/api/mrp/plans/${planId}/to-purchase-order`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function convertToProductionOrder(
  planId: number,
  request: MrpConvertRequest = {},
): Promise<MrpPlanView> {
  return apiFetch<MrpPlanView>(`/api/mrp/plans/${planId}/to-production-order`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function ignorePlan(planId: number, remark?: string | null): Promise<MrpPlanView> {
  return apiFetch<MrpPlanView>(`/api/mrp/plans/${planId}/ignore`, {
    method: 'POST',
    body: JSON.stringify({ remark: remark ?? null }),
  })
}
