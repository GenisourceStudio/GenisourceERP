import { apiFetch } from './client'

/**
 * Typed client for the master-data API.
 *
 * Deliberately a thin, explicit layer rather than a generated client: the payloads are
 * part of the public contract (they are documented in Swagger), and a hand-written file
 * makes a breaking change visible in review instead of hidden in a regenerated blob.
 */

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  total: number
  totalPages: number
  hasNext: boolean
}

export type RecordStatus = 'ACTIVE' | 'INACTIVE'
export type RevisionStatus = 'DRAFT' | 'RELEASED' | 'OBSOLETE'
export type ItemType = 'RAW' | 'SEMI' | 'FINISHED' | 'CONSUMABLE' | 'SERVICE'
export type PartnerType = 'CUSTOMER' | 'SUPPLIER' | 'BOTH'

export interface UomView {
  code: string
  name: string
  nameEn: string
  decimals: number
  label: string
}

export interface ItemOption {
  id: number
  code: string
  name: string
  spec: string | null
  uomCode: string
}

export interface ItemView {
  id: number
  code: string
  name: string
  spec: string | null
  categoryId: number | null
  categoryName: string | null
  itemType: ItemType
  uomCode: string
  uomName: string | null
  purchaseUomCode: string | null
  purchaseFactor: number
  purchased: boolean
  purchaseLeadTimeDays: number
  minOrderQty: number
  safetyStock: number
  defaultSupplierId: number | null
  defaultSupplierName: string | null
  sold: boolean
  salePrice: number | null
  manufactured: boolean
  lotControlled: boolean
  serialControlled: boolean
  shelfLifeDays: number | null
  standardCost: number
  currency: string
  taxRate: number
  remark: string | null
  status: RecordStatus
  rowVersion: number
  updatedAt: string
}

export type ItemRequest = Omit<
  ItemView,
  'id' | 'uomName' | 'categoryName' | 'defaultSupplierName' | 'rowVersion' | 'updatedAt'
>

export interface CategoryView {
  id: number
  code: string
  name: string
  parentId: number | null
  parentName: string | null
  sortOrder: number
  status: RecordStatus
  itemCount: number
}

export interface CategoryRequest {
  code: string
  name: string
  parentId: number | null
  sortOrder: number
  status: RecordStatus
}

export interface PartnerView {
  id: number
  code: string
  name: string
  shortName: string | null
  partnerType: PartnerType
  taxNo: string | null
  contactName: string | null
  phone: string | null
  email: string | null
  address: string | null
  currency: string
  paymentTermsDays: number
  creditLimit: number
  remark: string | null
  status: RecordStatus
  rowVersion: number
  updatedAt: string
}

export type PartnerRequest = Omit<PartnerView, 'id' | 'rowVersion' | 'updatedAt'>

export interface PartnerOption {
  id: number
  code: string
  name: string
  partnerType: PartnerType
}

export interface WorkCenterView {
  id: number
  code: string
  name: string
  department: string | null
  capacityMinutesPerDay: number
  efficiencyPercent: number
  availableMinutesPerDay: number
  costRatePerHour: number
  status: RecordStatus
  rowVersion: number
  updatedAt: string
}

export type WorkCenterRequest = Omit<
  WorkCenterView,
  'id' | 'availableMinutesPerDay' | 'rowVersion' | 'updatedAt'
>

export interface BomSummary {
  id: number
  itemId: number
  itemCode: string
  itemName: string
  version: string
  status: RevisionStatus
  baseQty: number
  lineCount: number
  effectiveFrom: string | null
  effectiveTo: string | null
  releasedAt: string | null
  updatedAt: string
  remark: string | null
}

export interface BomSubstituteView {
  id: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  priority: number
  remark: string | null
}

export interface BomLineView {
  id: number
  lineNo: number
  componentItemId: number
  componentCode: string | null
  componentName: string | null
  componentSpec: string | null
  uomCode: string | null
  qty: number
  lossRate: number
  phantom: boolean
  remark: string | null
  substitutes: BomSubstituteView[]
}

export interface BomDetail {
  id: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  itemUomCode: string | null
  version: string
  status: RevisionStatus
  baseQty: number
  effectiveFrom: string | null
  effectiveTo: string | null
  releasedAt: string | null
  remark: string | null
  rowVersion: number
  lines: BomLineView[]
}

export interface BomTreeNode {
  itemId: number
  itemCode: string
  itemName: string
  itemSpec: string | null
  uomCode: string | null
  qty: number
  perParentQty: number
  lossRate: number
  phantom: boolean
  level: number
  bomVersion: string | null
  source: string
  children: BomTreeNode[]
}

export interface BomLineRequest {
  lineNo: number | null
  componentItemId: number
  qty: number
  lossRate: number
  phantom: boolean
  remark: string | null
  substitutes: { itemId: number; priority: number | null; remark: string | null }[]
}

export interface BomRequest {
  itemId: number
  version: string
  baseQty: number
  effectiveFrom: string | null
  effectiveTo: string | null
  remark: string | null
  lines: BomLineRequest[]
}

export interface OperationView {
  id: number
  seq: number
  workCenterId: number
  workCenterCode: string | null
  workCenterName: string | null
  name: string
  setupMinutes: number
  runMinutesPerUnit: number
  remark: string | null
}

export interface RoutingSummary {
  id: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  version: string
  status: RevisionStatus
  operationCount: number
  runMinutesPerUnit: number
  releasedAt: string | null
  updatedAt: string
  remark: string | null
}

export interface RoutingDetail extends RoutingSummary {
  itemUomCode: string | null
  rowVersion: number
  totalRunMinutesPerUnit: number
  operations: OperationView[]
}

export interface RoutingRequest {
  itemId: number
  version: string
  remark: string | null
  operations: {
    seq: number | null
    workCenterId: number
    name: string
    setupMinutes: number
    runMinutesPerUnit: number
    remark: string | null
  }[]
}

// --------------------------------------------------------------- helpers

function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

const BASE = '/api/masterdata'

// ----------------------------------------------------------------- items

export function listItems(params: {
  q?: string
  itemType?: ItemType | ''
  status?: RecordStatus | ''
  categoryId?: number | null
  page?: number
  size?: number
}): Promise<PageResponse<ItemView>> {
  return apiFetch(`${BASE}/items${query(params)}`)
}

export function getItem(id: number): Promise<ItemView> {
  return apiFetch(`${BASE}/items/${id}`)
}

export function createItem(body: ItemRequest): Promise<ItemView> {
  return apiFetch(`${BASE}/items`, { method: 'POST', body: JSON.stringify(body) })
}

export function updateItem(id: number, body: ItemRequest): Promise<ItemView> {
  return apiFetch(`${BASE}/items/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function changeItemStatus(id: number, status: RecordStatus): Promise<ItemView> {
  return apiFetch(`${BASE}/items/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

export function itemOptions(q: string, limit = 20): Promise<ItemOption[]> {
  return apiFetch(`${BASE}/items/options${query({ q, limit })}`)
}

export function listUoms(): Promise<UomView[]> {
  return apiFetch(`${BASE}/uoms`)
}

export function listCategories(): Promise<CategoryView[]> {
  return apiFetch(`${BASE}/item-categories`)
}

export function createCategory(body: CategoryRequest): Promise<CategoryView> {
  return apiFetch(`${BASE}/item-categories`, { method: 'POST', body: JSON.stringify(body) })
}

export function updateCategory(id: number, body: CategoryRequest): Promise<CategoryView> {
  return apiFetch(`${BASE}/item-categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function deactivateCategory(id: number): Promise<void> {
  return apiFetch(`${BASE}/item-categories/${id}`, { method: 'DELETE' })
}

// -------------------------------------------------------------- partners

export function listPartners(params: {
  q?: string
  role?: PartnerType | ''
  status?: RecordStatus | ''
  page?: number
  size?: number
}): Promise<PageResponse<PartnerView>> {
  return apiFetch(`${BASE}/partners${query(params)}`)
}

export function createPartner(body: PartnerRequest): Promise<PartnerView> {
  return apiFetch(`${BASE}/partners`, { method: 'POST', body: JSON.stringify(body) })
}

export function updatePartner(id: number, body: PartnerRequest): Promise<PartnerView> {
  return apiFetch(`${BASE}/partners/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function changePartnerStatus(id: number, status: RecordStatus): Promise<PartnerView> {
  return apiFetch(`${BASE}/partners/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

export function partnerOptions(q: string, role: PartnerType = 'SUPPLIER', limit = 20): Promise<PartnerOption[]> {
  return apiFetch(`${BASE}/partners/options${query({ q, role, limit })}`)
}

// --------------------------------------------------------- work centres

export function listWorkCenters(status?: RecordStatus): Promise<WorkCenterView[]> {
  return apiFetch(`${BASE}/work-centers${query({ status })}`)
}

export function createWorkCenter(body: WorkCenterRequest): Promise<WorkCenterView> {
  return apiFetch(`${BASE}/work-centers`, { method: 'POST', body: JSON.stringify(body) })
}

export function updateWorkCenter(id: number, body: WorkCenterRequest): Promise<WorkCenterView> {
  return apiFetch(`${BASE}/work-centers/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function changeWorkCenterStatus(id: number, status: RecordStatus): Promise<WorkCenterView> {
  return apiFetch(`${BASE}/work-centers/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

// ------------------------------------------------------------------ boms

export function listBoms(params: {
  q?: string
  status?: RevisionStatus | ''
  itemId?: number | null
  page?: number
  size?: number
}): Promise<PageResponse<BomSummary>> {
  return apiFetch(`${BASE}/boms${query(params)}`)
}

export function getBom(id: number): Promise<BomDetail> {
  return apiFetch(`${BASE}/boms/${id}`)
}

export function createBom(body: BomRequest): Promise<BomDetail> {
  return apiFetch(`${BASE}/boms`, { method: 'POST', body: JSON.stringify(body) })
}

export function updateBom(id: number, body: BomRequest): Promise<BomDetail> {
  return apiFetch(`${BASE}/boms/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function releaseBom(id: number): Promise<BomDetail> {
  return apiFetch(`${BASE}/boms/${id}/release`, { method: 'POST' })
}

export function obsoleteBom(id: number): Promise<BomDetail> {
  return apiFetch(`${BASE}/boms/${id}/obsolete`, { method: 'POST' })
}

export function copyBom(id: number, version: string): Promise<BomDetail> {
  return apiFetch(`${BASE}/boms/${id}/copy`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  })
}

export function deleteBom(id: number): Promise<void> {
  return apiFetch(`${BASE}/boms/${id}`, { method: 'DELETE' })
}

export function bomTree(id: number, qty = 1, maxLevels = 10): Promise<BomTreeNode> {
  return apiFetch(`${BASE}/boms/${id}/tree${query({ qty, maxLevels })}`)
}

// -------------------------------------------------------------- routings

export function listRoutings(params: {
  q?: string
  status?: RevisionStatus | ''
  itemId?: number | null
  page?: number
  size?: number
}): Promise<PageResponse<RoutingSummary>> {
  return apiFetch(`${BASE}/routings${query(params)}`)
}

export function getRouting(id: number): Promise<RoutingDetail> {
  return apiFetch(`${BASE}/routings/${id}`)
}

export function createRouting(body: RoutingRequest): Promise<RoutingDetail> {
  return apiFetch(`${BASE}/routings`, { method: 'POST', body: JSON.stringify(body) })
}

export function updateRouting(id: number, body: RoutingRequest): Promise<RoutingDetail> {
  return apiFetch(`${BASE}/routings/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function releaseRouting(id: number): Promise<RoutingDetail> {
  return apiFetch(`${BASE}/routings/${id}/release`, { method: 'POST' })
}

export function obsoleteRouting(id: number): Promise<RoutingDetail> {
  return apiFetch(`${BASE}/routings/${id}/obsolete`, { method: 'POST' })
}

export function copyRouting(id: number, version: string): Promise<RoutingDetail> {
  return apiFetch(`${BASE}/routings/${id}/copy`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  })
}

export function deleteRouting(id: number): Promise<void> {
  return apiFetch(`${BASE}/routings/${id}`, { method: 'DELETE' })
}
