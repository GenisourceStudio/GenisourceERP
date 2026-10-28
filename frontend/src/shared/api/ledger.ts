import { apiFetch } from './client'
import type { PageResponse } from './masterdata'

export type { PageResponse } from './masterdata'

export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE' | 'COST'
export type BalanceDirection = 'DEBIT' | 'CREDIT'
export type AccountRole =
  | 'CASH'
  | 'BANK'
  | 'AR'
  | 'AP'
  | 'INVENTORY_RAW'
  | 'INVENTORY_FINISHED'
  | 'WIP'
  | 'TAX_INPUT'
  | 'TAX_OUTPUT'
  | 'REVENUE'
  | 'COST_OF_SALES'
  | 'PROFIT_CURRENT_YEAR'

export interface AccountView {
  id: number
  code: string
  name: string
  accountType: AccountType
  balanceDirection: BalanceDirection
  parentId?: number
  level: number
  leaf: boolean
  active: boolean
  systemRole?: AccountRole
  /** True once a voucher line points at this account; the identity then freezes. */
  used: boolean
  remark?: string
}

export interface AccountRequest {
  code: string
  name: string
  accountType: AccountType
  parentId?: number | null
  active?: boolean
  systemRole?: AccountRole | null
  remark?: string | null
}

export interface AccountUpdateRequest {
  name: string
  active?: boolean
  systemRole?: AccountRole | null
  remark?: string | null
}

export function listAccounts(): Promise<AccountView[]> {
  return apiFetch<AccountView[]>('/api/finance/accounts')
}

export function createAccount(request: AccountRequest): Promise<AccountView> {
  return apiFetch<AccountView>('/api/finance/accounts', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateAccount(
  id: number,
  request: AccountUpdateRequest,
): Promise<AccountView> {
  return apiFetch<AccountView>(`/api/finance/accounts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

// ----------------------------------------------------------------- vouchers

export type VoucherType =
  | 'MANUAL'
  | 'OPENING'
  | 'PURCHASE'
  | 'SALES'
  | 'PAYMENT'
  | 'RECEIPT'
  | 'PRODUCTION'
  | 'REVERSAL'

export type VoucherStatus = 'DRAFT' | 'POSTED' | 'REVERSED'

export interface VoucherLineView {
  id: number
  lineNo: number
  accountId: number
  accountCode: string
  accountName: string
  debit: number
  credit: number
  partnerId?: number
  partnerName?: string
  summary?: string
}

export interface VoucherView {
  id: number
  voucherNo: string
  voucherType: VoucherType
  bizDate: string
  status: VoucherStatus
  sourceType?: string
  sourceId?: number
  reversalOf?: number
  reversedBy?: number
  totalDebit: number
  totalCredit: number
  postedBy?: string
  postedAt?: string
  remark?: string
  lines: VoucherLineView[]
}

export interface VoucherLineRequest {
  accountId: number
  debit?: number | null
  credit?: number | null
  partnerId?: number | null
  summary?: string | null
}

export interface VoucherRequest {
  voucherType: VoucherType
  bizDate: string
  remark?: string | null
  lines: VoucherLineRequest[]
}

export function listVouchers(params: {
  keyword?: string
  voucherType?: VoucherType | null
  status?: VoucherStatus | null
  from?: string | null
  to?: string | null
  page?: number
  size?: number
}): Promise<PageResponse<VoucherView>> {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.voucherType) query.set('voucherType', params.voucherType)
  if (params.status) query.set('status', params.status)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  query.set('page', String(params.page ?? 0))
  query.set('size', String(params.size ?? 20))
  return apiFetch<PageResponse<VoucherView>>(`/api/finance/vouchers?${query.toString()}`)
}

export function getVoucher(id: number): Promise<VoucherView> {
  return apiFetch<VoucherView>(`/api/finance/vouchers/${id}`)
}

export function createVoucher(request: VoucherRequest): Promise<VoucherView> {
  return apiFetch<VoucherView>('/api/finance/vouchers', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateVoucher(id: number, request: VoucherRequest): Promise<VoucherView> {
  return apiFetch<VoucherView>(`/api/finance/vouchers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function postVoucher(id: number): Promise<VoucherView> {
  return apiFetch<VoucherView>(`/api/finance/vouchers/${id}/post`, { method: 'POST' })
}

export function reverseVoucher(id: number, bizDate: string): Promise<VoucherView> {
  return apiFetch<VoucherView>(`/api/finance/vouchers/${id}/reverse`, {
    method: 'POST',
    body: JSON.stringify({ bizDate }),
  })
}

// ------------------------------------------------------------------ reports

export interface TrialBalanceRow {
  accountId: number
  code: string
  name: string
  accountType: AccountType
  balanceDirection: BalanceDirection
  openingDebit: number
  openingCredit: number
  periodDebit: number
  periodCredit: number
  closingDebit: number
  closingCredit: number
}

export interface TrialBalance {
  from: string
  to: string
  rows: TrialBalanceRow[]
  totalOpeningDebit: number
  totalOpeningCredit: number
  totalPeriodDebit: number
  totalPeriodCredit: number
  totalClosingDebit: number
  totalClosingCredit: number
  closingDifference: number
}

export interface LedgerEntry {
  voucherNo: string
  voucherType: VoucherType
  bizDate: string
  lineNo: number
  debit: number
  credit: number
  direction: string
  balance: number
  summary?: string
}

export interface AccountLedger {
  accountId: number
  code: string
  name: string
  balanceDirection: BalanceDirection
  openingBalance: number
  entries: LedgerEntry[]
  periodDebit: number
  periodCredit: number
  closingBalance: number
}

export interface StatementLine {
  accountId: number
  code: string
  name: string
  amount: number
}

export interface BalanceSheet {
  to: string
  assets: StatementLine[]
  liabilities: StatementLine[]
  equity: StatementLine[]
  undistributedProfit: number
  totalAssets: number
  totalLiabilities: number
  totalEquity: number
  difference: number
}

export interface IncomeStatement {
  from: string
  to: string
  revenue: StatementLine[]
  cost: StatementLine[]
  expense: StatementLine[]
  totalRevenue: number
  totalCost: number
  totalExpense: number
  profit: number
}

export interface CashFlowSection {
  section: string
  inflow: number
  outflow: number
  net: number
}

export interface CashFlow {
  from: string
  to: string
  openingCash: number
  closingCash: number
  sections: CashFlowSection[]
}

export function trialBalance(from?: string | null, to?: string | null): Promise<TrialBalance> {
  const query = new URLSearchParams()
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  return apiFetch<TrialBalance>(`/api/finance/reports/trial-balance?${query.toString()}`)
}

export function accountLedger(
  accountId: number,
  from?: string | null,
  to?: string | null,
): Promise<AccountLedger> {
  const query = new URLSearchParams()
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  return apiFetch<AccountLedger>(
    `/api/finance/reports/accounts/${accountId}/ledger?${query.toString()}`,
  )
}

export function balanceSheet(to?: string | null): Promise<BalanceSheet> {
  const query = new URLSearchParams()
  if (to) query.set('to', to)
  return apiFetch<BalanceSheet>(`/api/finance/reports/balance-sheet?${query.toString()}`)
}

export function incomeStatement(from?: string | null, to?: string | null): Promise<IncomeStatement> {
  const query = new URLSearchParams()
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  return apiFetch<IncomeStatement>(`/api/finance/reports/income-statement?${query.toString()}`)
}

export function cashFlow(from?: string | null, to?: string | null): Promise<CashFlow> {
  const query = new URLSearchParams()
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  return apiFetch<CashFlow>(`/api/finance/reports/cash-flow?${query.toString()}`)
}
