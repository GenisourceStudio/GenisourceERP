import { apiFetch } from './client'

/**
 * Typed client for the receivables and payables ledger.
 *
 * <p>A ledger row is raised by posting a trade document and is never edited afterwards —
 * money against it arrives as settlements. The status is not a stored column but read
 * off what is left to pay, which is why there is no "mark as paid" here: paying the
 * balance is the only way to close one.
 */

export type LedgerStatus = 'OPEN' | 'PARTIAL' | 'SETTLED' | 'VOID'

export interface LedgerView {
  id: number
  partnerId: number | null
  partnerCode: string | null
  partnerName: string | null
  sourceType: string | null
  sourceId: number | null
  sourceNo: string | null
  bizDate: string
  dueDate: string | null
  netAmount: number
  taxAmount: number
  totalAmount: number
  amountPaid: number
  balance: number
  status: LedgerStatus
  overdue: boolean
  remark: string | null
  rowVersion: number
}

export interface SettleRequest {
  amount: number
  remark?: string | null
}

export interface LedgerSummary {
  totalAmount: number
  amountPaid: number
  balance: number
  overdueAmount: number
  openCount: number
  overdueCount: number
}

export interface LedgerPage {
  content: LedgerView[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  summary: LedgerSummary
}

export interface LedgerQuery {
  keyword?: string
  partnerId?: number | null
  status?: LedgerStatus | null
  from?: string | null
  to?: string | null
  overdueOnly?: boolean
  page?: number
  size?: number
}

function queryString(query: LedgerQuery): string {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.partnerId) params.set('partnerId', String(query.partnerId))
  if (query.status) params.set('status', query.status)
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  if (query.overdueOnly) params.set('overdueOnly', 'true')
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return params.toString()
}

// ---------------------------------------------------------------- receivables

export function listReceivables(query: LedgerQuery = {}): Promise<LedgerPage> {
  return apiFetch(`/api/finance/receivables?${queryString(query)}`)
}

export function getReceivable(id: number): Promise<LedgerView> {
  return apiFetch(`/api/finance/receivables/${id}`)
}

export function collectReceivable(id: number, request: SettleRequest): Promise<LedgerView> {
  return apiFetch(`/api/finance/receivables/${id}/payment`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

// ------------------------------------------------------------------- payables

export function listPayables(query: LedgerQuery = {}): Promise<LedgerPage> {
  return apiFetch(`/api/finance/payables?${queryString(query)}`)
}

export function getPayable(id: number): Promise<LedgerView> {
  return apiFetch(`/api/finance/payables/${id}`)
}

export function payPayable(id: number, request: SettleRequest): Promise<LedgerView> {
  return apiFetch(`/api/finance/payables/${id}/payment`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
