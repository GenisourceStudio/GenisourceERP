import { apiFetch } from './client'
import type { PageResponse } from './masterdata'

/**
 * Typed client for the inventory API.
 *
 * <p>Same shape as the master-data client: a thin, explicit layer. The inventory
 * payloads are the public contract, and a hand-written file makes a breaking change
 * visible in review rather than hidden in a regenerated blob.
 */

export type { PageResponse } from './masterdata'

export type WarehouseType = 'RAW' | 'SEMI' | 'FINISHED' | 'GENERAL'
export type DocumentType = 'INBOUND' | 'OUTBOUND' | 'TRANSFER' | 'COUNT'
export type DocumentStatus = 'DRAFT' | 'POSTED' | 'REVERSED'
export type MovementType =
  | 'RECEIPT'
  | 'ISSUE'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'COUNT_GAIN'
  | 'COUNT_LOSS'
export type LotType = 'LOT' | 'SERIAL'

export interface WarehouseView {
  id: number
  code: string
  name: string
  warehouseType: WarehouseType
  address: string | null
  remark: string | null
  status: 'ACTIVE' | 'INACTIVE'
  rowVersion: number
  locationCount: number
  stockItemCount: number
  locations: LocationView[]
}

export interface LocationView {
  id: number
  warehouseId: number
  warehouseName: string | null
  code: string
  name: string
  remark: string | null
  status: 'ACTIVE' | 'INACTIVE'
  rowVersion: number
}

export interface WarehouseRequest {
  code: string
  name: string
  warehouseType: WarehouseType
  address?: string | null
  remark?: string | null
  status?: 'ACTIVE' | 'INACTIVE' | null
}

export interface LocationRequest {
  warehouseId: number
  code: string
  name: string
  remark?: string | null
  status?: 'ACTIVE' | 'INACTIVE' | null
}

export interface LotView {
  id: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  lotNo: string
  lotType: LotType
  productionDate: string | null
  expiryDate: string | null
  remark: string | null
  status: 'ACTIVE' | 'INACTIVE'
  onHand: number
  totalAmount: number
  rowVersion: number
}

export interface LotRequest {
  itemId: number
  lotNo: string
  lotType: LotType
  productionDate?: string | null
  expiryDate?: string | null
  remark?: string | null
  status?: 'ACTIVE' | 'INACTIVE' | null
}

export interface StockView {
  id: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  locationId: number | null
  locationCode: string | null
  locationName: string | null
  lotId: number | null
  lotNo: string | null
  lotType: string | null
  onHand: number
  reserved: number
  inTransit: number
  locked: number
  available: number
  avgCost: number
  totalAmount: number
  updatedAt: string
}

export interface StockSummary {
  itemId: number
  itemCode: string
  itemName: string
  uomCode: string
  onHand: number
  reserved: number
  locked: number
  inTransit: number
  available: number
  totalAmount: number
  locationCount: number
  lines: StockView[]
}

export interface MovementView {
  id: number
  docNo: string | null
  movementType: MovementType
  direction: 'IN' | 'OUT'
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  lotId: number | null
  lotNo: string | null
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  locationId: number | null
  locationName: string | null
  qty: number
  unitCost: number
  amount: number
  balanceQtyAfter: number
  balanceAmountAfter: number
  operator: string | null
  remark: string | null
  movedAt: string
}

export interface LotTrace {
  lotId: number
  lotNo: string
  itemId: number
  itemCode: string | null
  itemName: string | null
  lotType: string
  onHand: number
  totalAmount: number
  productionDate: string | null
  expiryDate: string | null
  movements: MovementView[]
}

export interface DocumentLineView {
  id: number
  lineNo: number
  itemId: number
  itemCode: string | null
  itemName: string | null
  uomCode: string | null
  lotId: number | null
  lotNo: string | null
  qty: number
  unitCost: number
  countedQty: number | null
  amount: number
  remark: string | null
}

export interface DocumentView {
  id: number
  docNo: string
  docType: DocumentType
  status: DocumentStatus
  warehouseId: number
  warehouseCode: string | null
  warehouseName: string | null
  locationId: number | null
  locationName: string | null
  toWarehouseId: number | null
  toWarehouseName: string | null
  toLocationId: number | null
  toLocationName: string | null
  partnerId: number | null
  partnerName: string | null
  bizDate: string
  remark: string | null
  reversalOf: number | null
  reversalOfNo: string | null
  postedAt: string | null
  createdBy: string | null
  rowVersion: number
  totalQty: number
  totalAmount: number
  lines: DocumentLineView[]
}

export interface DocumentLineRequest {
  itemId: number
  lotId?: number | null
  qty: number
  unitCost?: number | null
  countedQty?: number | null
  remark?: string | null
}

export interface DocumentRequest {
  docType: DocumentType
  warehouseId: number
  locationId?: number | null
  toWarehouseId?: number | null
  toLocationId?: number | null
  partnerId?: number | null
  bizDate: string
  remark?: string | null
  lines: DocumentLineRequest[]
}

// ------------------------------------------------------------------ warehouses

export async function listWarehouses(): Promise<WarehouseView[]> {
  return apiFetch<WarehouseView[]>('/api/inventory/warehouses')
}

export async function createWarehouse(request: WarehouseRequest): Promise<WarehouseView> {
  return apiFetch<WarehouseView>('/api/inventory/warehouses', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export async function updateWarehouse(
  id: number,
  request: WarehouseRequest,
): Promise<WarehouseView> {
  return apiFetch<WarehouseView>(`/api/inventory/warehouses/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export async function changeWarehouseStatus(
  id: number,
  status: 'ACTIVE' | 'INACTIVE',
): Promise<WarehouseView> {
  return apiFetch<WarehouseView>(`/api/inventory/warehouses/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

// ------------------------------------------------------------------- locations

export async function listLocations(warehouseId?: number): Promise<LocationView[]> {
  const query = warehouseId ? `?warehouseId=${warehouseId}` : ''
  return apiFetch<LocationView[]>(`/api/inventory/locations${query}`)
}

export async function createLocation(request: LocationRequest): Promise<LocationView> {
  return apiFetch<LocationView>('/api/inventory/locations', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export async function updateLocation(id: number, request: LocationRequest): Promise<LocationView> {
  return apiFetch<LocationView>(`/api/inventory/locations/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export async function changeLocationStatus(
  id: number,
  status: 'ACTIVE' | 'INACTIVE',
): Promise<LocationView> {
  return apiFetch<LocationView>(`/api/inventory/locations/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

// ----------------------------------------------------------------------- lots

export async function listLots(itemId?: number): Promise<LotView[]> {
  const query = itemId ? `?itemId=${itemId}` : ''
  return apiFetch<LotView[]>(`/api/inventory/lots${query}`)
}

export async function createLot(request: LotRequest): Promise<LotView> {
  return apiFetch<LotView>('/api/inventory/lots', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export async function changeLotStatus(
  id: number,
  status: 'ACTIVE' | 'INACTIVE',
): Promise<LotView> {
  return apiFetch<LotView>(`/api/inventory/lots/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

// ---------------------------------------------------------------------- stock

export interface StockQuery {
  q?: string
  warehouseId?: number | null
  locationId?: number | null
  itemId?: number | null
  lotId?: number | null
  includeEmpty?: boolean
  page?: number
  size?: number
}

export async function listStock(query: StockQuery = {}): Promise<PageResponse<StockView>> {
  const params = new URLSearchParams()
  if (query.q) params.set('q', query.q)
  if (query.warehouseId) params.set('warehouseId', String(query.warehouseId))
  if (query.locationId) params.set('locationId', String(query.locationId))
  if (query.itemId) params.set('itemId', String(query.itemId))
  if (query.lotId) params.set('lotId', String(query.lotId))
  if (query.includeEmpty) params.set('includeEmpty', 'true')
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return apiFetch<PageResponse<StockView>>(`/api/inventory/stock?${params.toString()}`)
}

export async function stockSummary(itemId: number): Promise<StockSummary> {
  return apiFetch<StockSummary>(`/api/inventory/stock/items/${itemId}`)
}

// ------------------------------------------------------------------- movements

export interface MovementQuery {
  q?: string
  itemId?: number | null
  lotId?: number | null
  warehouseId?: number | null
  movementType?: MovementType | null
  from?: string | null
  to?: string | null
  page?: number
  size?: number
}

export async function listMovements(
  query: MovementQuery = {},
): Promise<PageResponse<MovementView>> {
  const params = new URLSearchParams()
  if (query.q) params.set('q', query.q)
  if (query.itemId) params.set('itemId', String(query.itemId))
  if (query.lotId) params.set('lotId', String(query.lotId))
  if (query.warehouseId) params.set('warehouseId', String(query.warehouseId))
  if (query.movementType) params.set('movementType', query.movementType)
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return apiFetch<PageResponse<MovementView>>(`/api/inventory/movements?${params.toString()}`)
}

export async function traceLot(lotId: number): Promise<LotTrace> {
  return apiFetch<LotTrace>(`/api/inventory/movements/lots/${lotId}/trace`)
}

// ------------------------------------------------------------------- documents

export interface DocumentQuery {
  q?: string
  docType?: DocumentType | null
  status?: DocumentStatus | null
  warehouseId?: number | null
  page?: number
  size?: number
}

export async function listDocuments(
  query: DocumentQuery = {},
): Promise<PageResponse<DocumentView>> {
  const params = new URLSearchParams()
  if (query.q) params.set('q', query.q)
  if (query.docType) params.set('docType', query.docType)
  if (query.status) params.set('status', query.status)
  if (query.warehouseId) params.set('warehouseId', String(query.warehouseId))
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return apiFetch<PageResponse<DocumentView>>(`/api/inventory/documents?${params.toString()}`)
}

export async function getDocument(id: number): Promise<DocumentView> {
  return apiFetch<DocumentView>(`/api/inventory/documents/${id}`)
}

export async function createDocument(request: DocumentRequest): Promise<DocumentView> {
  return apiFetch<DocumentView>('/api/inventory/documents', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export async function updateDocument(
  id: number,
  request: DocumentRequest,
): Promise<DocumentView> {
  return apiFetch<DocumentView>(`/api/inventory/documents/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export async function postDocument(id: number): Promise<DocumentView> {
  return apiFetch<DocumentView>(`/api/inventory/documents/${id}/post`, { method: 'POST' })
}

export async function reverseDocument(id: number): Promise<DocumentView> {
  return apiFetch<DocumentView>(`/api/inventory/documents/${id}/reverse`, { method: 'POST' })
}

export async function deleteDocument(id: number): Promise<void> {
  await apiFetch<void>(`/api/inventory/documents/${id}`, { method: 'DELETE' })
}
