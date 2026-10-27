import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import {
  collectReceivable,
  listPayables,
  listReceivables,
  payPayable,
  type LedgerQuery,
  type LedgerStatus,
  type LedgerView,
} from '../../shared/api/finance'
import { useAuthStore } from '../../shared/auth/store'
import { formatMoney } from '../../shared/format'
import { useDebounced } from '../../shared/hooks/useDebounced'

const PAGE_SIZE = 20
const STATUSES: LedgerStatus[] = ['OPEN', 'PARTIAL', 'SETTLED', 'VOID']

/**
 * The receivables and payables ledger.
 *
 * <p>Both sides are the same screen with the labels turned round, because both answer
 * the same question: who owes what, and how late is it. Overdue is not a stored bucket
 * — the server compares the due date with today, so a row that went past due overnight
 * shows it this morning without anybody running a job.
 *
 * <p>There is no "mark as paid" here. Money is recorded against a row and the status
 * follows from what is left, which means a half-paid invoice cannot be quietly closed.
 */
export default function LedgerPage({ kind }: { kind: 'payable' | 'receivable' }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite =
    permissions?.includes(
      kind === 'payable' ? 'finance:payable:write' : 'finance:receivable:write',
    ) ?? false

  const prefix = kind === 'payable' ? 'finance.payable' : 'finance.receivable'

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<LedgerStatus | ''>('')
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [page, setPage] = useState(0)

  const [settling, setSettling] = useState<LedgerView | null>(null)
  const [amount, setAmount] = useState('')
  const [detail, setDetail] = useState<LedgerView | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const listQuery = useQuery({
    queryKey: ['ledger', kind, debouncedKeyword, status, overdueOnly, page],
    queryFn: () => {
      const query: LedgerQuery = {
        keyword: debouncedKeyword,
        status: status === '' ? null : status,
        overdueOnly,
        page,
        size: PAGE_SIZE,
      }
      return kind === 'payable' ? listPayables(query) : listReceivables(query)
    },
  })

  const settle = () => {
    const row = settling
    if (!row) {
      return
    }
    setBusy(true)
    setError(null)
    const work =
      kind === 'payable'
        ? payPayable(row.id, { amount: Number(amount || 0), remark: null })
        : collectReceivable(row.id, { amount: Number(amount || 0), remark: null })
    void work
      .then(() => {
        void queryClient.invalidateQueries({ queryKey: ['ledger'] })
        setSettling(null)
        setAmount('')
      })
      .catch((exception: unknown) =>
        setError(
          exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'),
        ),
      )
      .finally(() => setBusy(false))
  }

  const columns: Column<LedgerView>[] = [
    {
      key: 'sourceNo',
      header: t('finance.field.source'),
      width: '160px',
      render: (row) => <span className="mono">{row.sourceNo}</span>,
    },
    {
      key: 'partner',
      header: t('trade.field.partner'),
      render: (row) => row.partnerName ?? null,
    },
    {
      key: 'bizDate',
      header: t('trade.field.bizDate'),
      width: '110px',
      render: (row) => <span className="hint mono">{row.bizDate}</span>,
    },
    {
      key: 'dueDate',
      header: t('finance.field.dueDate'),
      width: '110px',
      render: (row) => (
        <span className={row.overdue ? 'mono text-danger' : 'hint mono'}>{row.dueDate}</span>
      ),
    },
    {
      key: 'total',
      header: t('trade.field.totalAmount'),
      align: 'right',
      width: '110px',
      render: (row) => <span className="mono">{formatMoney(row.totalAmount)}</span>,
    },
    {
      key: 'paid',
      header: t('finance.field.amountPaid'),
      align: 'right',
      width: '110px',
      render: (row) => <span className="mono">{formatMoney(row.amountPaid)}</span>,
    },
    {
      key: 'balance',
      header: t('finance.field.balance'),
      align: 'right',
      width: '110px',
      render: (row) => (
        <strong className="mono">{formatMoney(row.balance)}</strong>
      ),
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '100px',
      render: (row) => (
        <span className={`tag tag--${row.status.toLowerCase()}`}>
          {t(`status.ledger.${row.status}`)}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: '110px',
      align: 'right',
      render: (row) =>
        canWrite && row.balance > 0 ? (
          <div className="row-actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setSettling(row)
                setAmount(String(row.balance))
              }}
            >
              {t(kind === 'payable' ? 'finance.action.pay' : 'finance.action.collect')}
            </button>
          </div>
        ) : null,
    },
  ]

  const result = listQuery.data
  const summary = result?.summary

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      {summary ? (
        <div className="grid grid--4" style={{ marginBottom: 16 }}>
          <div className="stat">
            <div className="stat__label">{t('finance.field.total')}</div>
            <div className="stat__value">{formatMoney(summary.totalAmount)}</div>
            <div className="stat__hint">
              {t('finance.field.openCount', { count: summary.openCount })}
            </div>
          </div>
          <div className="stat">
            <div className="stat__label">
              {t(kind === 'payable' ? 'finance.field.paid' : 'finance.field.collected')}
            </div>
            <div className="stat__value">{formatMoney(summary.amountPaid)}</div>
          </div>
          <div className="stat">
            <div className="stat__label">{t('finance.field.balance')}</div>
            <div className="stat__value">{formatMoney(summary.balance)}</div>
          </div>
          <div className="stat">
            <div className="stat__label">{t('finance.field.overdue')}</div>
            <div className="stat__value text-danger">
              {formatMoney(summary.overdueAmount)}
            </div>
            <div className="stat__hint">
              {t('finance.field.overdueCount', { count: summary.overdueCount })}
            </div>
          </div>
        </div>
      ) : null}

      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('finance.searchPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <select
          className="select"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as LedgerStatus | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allStatus')}</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {t(`status.ledger.${value}`)}
            </option>
          ))}
        </select>
        <label className="row gap-8" style={{ fontSize: 12, color: 'var(--muted)' }}>
          <input
            type="checkbox"
            checked={overdueOnly}
            onChange={(event) => {
              setOverdueOnly(event.target.checked)
              setPage(0)
            }}
          />
          {t('finance.field.overdueOnly')}
        </label>
        <div className="toolbar__spacer" />
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(row) => row.id}
        loading={listQuery.isLoading}
        error={listQuery.error instanceof ApiError ? listQuery.error.message : null}
        emptyTitle={t(`${prefix}.emptyTitle`)}
        emptyBody={t(`${prefix}.emptyBody`)}
        onRowClick={(row) => setDetail(row)}
      />

      {result ? (
        <Pager
          page={result.page}
          size={result.size}
          total={result.totalElements}
          pageCount={result.totalPages}
          onChange={setPage}
        />
      ) : null}

      <Drawer
        open={detail !== null}
        title={detail?.sourceNo ?? ''}
        subtitle={detail ? t(`status.ledger.${detail.status}`) : ''}
        onClose={() => setDetail(null)}
      >
        {detail ? (
          <div className="form-grid form-grid--3">
            <div className="field">
              <span className="hint">{t('trade.field.partner')}</span>
              <div>{detail.partnerName ?? '—'}</div>
            </div>
            <div className="field">
              <span className="hint">{t('trade.field.bizDate')}</span>
              <div className="mono">{detail.bizDate}</div>
            </div>
            <div className="field">
              <span className="hint">{t('finance.field.dueDate')}</span>
              <div className="mono">{detail.dueDate ?? '—'}</div>
            </div>
            <div className="field">
              <span className="hint">{t('trade.field.netAmount')}</span>
              <div className="mono">{formatMoney(detail.netAmount)}</div>
            </div>
            <div className="field">
              <span className="hint">{t('trade.field.taxAmount')}</span>
              <div className="mono">{formatMoney(detail.taxAmount)}</div>
            </div>
            <div className="field">
              <span className="hint">{t('trade.field.totalAmount')}</span>
              <div className="mono">{formatMoney(detail.totalAmount)}</div>
            </div>
            <div className="field">
              <span className="hint">{t('finance.field.amountPaid')}</span>
              <div className="mono">{formatMoney(detail.amountPaid)}</div>
            </div>
            <div className="field">
              <span className="hint">{t('finance.field.balance')}</span>
              <div className="mono">{formatMoney(detail.balance)}</div>
            </div>
            <div className="field">
              <span className="hint">{t('trade.field.source')}</span>
              <div className="mono">{detail.sourceNo}</div>
            </div>
          </div>
        ) : null}
      </Drawer>

      <ConfirmDialog
        open={settling !== null}
        title={t(kind === 'payable' ? 'finance.action.pay' : 'finance.action.collect')}
        body={
          settling ? (
            <div>
              <p>
                {t('finance.confirm.settle', {
                  sourceNo: settling.sourceNo,
                  balance: formatMoney(settling.balance),
                })}
              </p>
              <input
                className="input input--num"
                type="number"
                min={0}
                max={settling.balance}
                step="any"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
              />
            </div>
          ) : (
            ''
          )
        }
        confirmLabel={t(kind === 'payable' ? 'finance.action.pay' : 'finance.action.collect')}
        busy={busy}
        onCancel={() => {
          setSettling(null)
          setAmount('')
        }}
        onConfirm={settle}
      />
    </>
  )
}
