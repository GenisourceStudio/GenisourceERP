import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import {
  createVoucher,
  getVoucher,
  listAccounts,
  listVouchers,
  postVoucher,
  reverseVoucher,
  updateVoucher,
  type VoucherLineRequest,
  type VoucherStatus,
  type VoucherType,
  type VoucherView,
} from '../../shared/api/ledger'
import { useAuthStore } from '../../shared/auth/store'
import { formatMoney } from '../../shared/format'
import { useDebounced } from '../../shared/hooks/useDebounced'

const PAGE_SIZE = 20

interface DraftLine {
  accountId: string
  debit: string
  credit: string
  summary: string
}

const emptyLine = (): DraftLine => ({ accountId: '', debit: '', credit: '', summary: '' })

/**
 * The general ledger: a list of vouchers and a way to write one.
 *
 * <p>Writing a voucher is a two-sided form, and the form refuses to let the two
 * sides disagree: the difference is shown while typing, and the save button stays
 * disabled until it is zero. A voucher that does not balance is not a voucher that
 * the server will accept, and finding that out after saving is a worse way to learn
 * it.
 */
export default function VouchersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('finance:voucher:write') ?? false
  const canPost = permissions?.includes('finance:voucher:post') ?? false

  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<VoucherStatus | ''>('')
  const [openId, setOpenId] = useState<number | null>(null)
  const [writing, setWriting] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [voucherType, setVoucherType] = useState<VoucherType>('MANUAL')
  const [bizDate, setBizDate] = useState(new Date().toISOString().slice(0, 10))
  const [remark, setRemark] = useState('')
  const [lines, setLines] = useState<DraftLine[]>([emptyLine(), emptyLine()])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState<'post' | 'reverse' | null>(null)

  const debounced = useDebounced(keyword)

  const listQuery = useQuery({
    queryKey: ['finance', 'vouchers', debounced, status, page],
    queryFn: () =>
      listVouchers({
        keyword: debounced,
        status: status === '' ? null : status,
        page,
        size: PAGE_SIZE,
      }),
  })

  const detailQuery = useQuery({
    queryKey: ['finance', 'voucher', openId],
    queryFn: () => getVoucher(openId as number),
    enabled: openId !== null,
  })

  const accountsQuery = useQuery({ queryKey: ['finance', 'accounts'], queryFn: listAccounts })

  const totalDebit = lines.reduce((sum, line) => sum + Number(line.debit || 0), 0)
  const totalCredit = lines.reduce((sum, line) => sum + Number(line.credit || 0), 0)
  const difference = Math.round((totalDebit - totalCredit) * 10000) / 10000
  const balanced = Math.abs(difference) < 0.0001

  const reset = () => {
    setWriting(false)
    setEditingId(null)
    setVoucherType('MANUAL')
    setRemark('')
    setLines([emptyLine(), emptyLine()])
    setError(null)
  }

  const save = useMutation({
    mutationFn: async () => {
      const request = {
        voucherType,
        bizDate,
        remark: remark === '' ? null : remark,
        lines: lines.map<VoucherLineRequest>((line) => ({
          accountId: Number(line.accountId),
          debit: line.debit === '' ? null : Number(line.debit),
          credit: line.credit === '' ? null : Number(line.credit),
          summary: line.summary === '' ? null : line.summary,
        })),
      }
      return editingId === null
        ? createVoucher(request)
        : updateVoucher(editingId, request)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
      reset()
    },
    onError: (exception: unknown) =>
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
  })

  const act = useMutation({
    mutationFn: async (kind: 'post' | 'reverse') => {
      if (kind === 'post') {
        return postVoucher(openId as number)
      }
      return reverseVoucher(openId as number, new Date().toISOString().slice(0, 10))
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
      setConfirming(null)
    },
    onError: (exception: unknown) =>
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
  })

  const columns: Column<VoucherView>[] = [
    {
      key: 'no',
      header: t('ledger.field.voucherNo'),
      width: '160px',
      render: (row) => <span className="mono">{row.voucherNo}</span>,
    },
    {
      key: 'date',
      header: t('ledger.field.bizDate'),
      width: '110px',
      render: (row) => row.bizDate,
    },
    {
      key: 'type',
      header: t('ledger.field.voucherType'),
      width: '120px',
      render: (row) => (
        <span className="tag">{t(`ledger.voucherType.${row.voucherType}`)}</span>
      ),
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '110px',
      render: (row) => (
        <span className={`tag tag--${row.status.toLowerCase()}`}>
          {t(`ledger.status.${row.status}`)}
        </span>
      ),
    },
    {
      key: 'amount',
      header: t('ledger.field.amount'),
      width: '140px',
      align: 'right',
      render: (row) => <span className="mono">{formatMoney(row.totalDebit)}</span>,
    },
    {
      key: 'remark',
      header: t('common.remark'),
      render: (row) => row.remark ?? '',
    },
  ]

  const submit = () => {
    setBusy(true)
    save.mutate(undefined, { onSettled: () => setBusy(false) })
  }

  const detail = detailQuery.data

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            type="search"
            placeholder={t('ledger.searchPlaceholder')}
            value={keyword}
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
            setStatus(event.target.value as VoucherStatus | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allStatus')}</option>
          {(['DRAFT', 'POSTED', 'REVERSED'] as const).map((value) => (
            <option key={value} value={value}>
              {t(`ledger.status.${value}`)}
            </option>
          ))}
        </select>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => {
              reset()
              setWriting(true)
            }}
          >
            {t('ledger.action.newVoucher')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={listQuery.data?.content ?? []}
        rowKey={(row) => row.id}
        loading={listQuery.isLoading}
        error={listQuery.error instanceof ApiError ? listQuery.error.message : null}
        emptyTitle={t('ledger.emptyVouchers.title')}
        emptyBody={t('ledger.emptyVouchers.body')}
        onRowClick={(row) => setOpenId(row.id)}
        activeKey={openId}
      />

      {listQuery.data ? (
        <Pager
          page={listQuery.data.page}
          size={listQuery.data.size}
          total={listQuery.data.total}
          pageCount={listQuery.data.totalPages}
          onChange={setPage}
        />
      ) : null}

      <Drawer
        open={openId !== null}
        wide
        title={detail?.voucherNo ?? ''}
        subtitle={
          detail ? (
            <span>
              {t(`ledger.voucherType.${detail.voucherType}`)} · {detail.bizDate} ·{' '}
              {t(`ledger.status.${detail.status}`)}
            </span>
          ) : null
        }
        onClose={() => setOpenId(null)}
        footer={
          detail && detail.status === 'DRAFT' && canPost ? (
            <button
              type="button"
              className="btn btn--primary btn--sm"
              onClick={() => setConfirming('post')}
            >
              {t('ledger.action.post')}
            </button>
          ) : detail && detail.status === 'POSTED' && canPost ? (
            <button
              type="button"
              className="btn btn--danger btn--sm"
              onClick={() => setConfirming('reverse')}
            >
              {t('ledger.action.reverse')}
            </button>
          ) : null
        }
      >
        {detail ? (
          <>
            <table className="line-list">
              <thead>
                <tr>
                  <th>{t('ledger.field.account')}</th>
                  <th className="num">{t('ledger.debit')}</th>
                  <th className="num">{t('ledger.credit')}</th>
                  <th>{t('common.remark')}</th>
                </tr>
              </thead>
              <tbody>
                {detail.lines.map((line) => (
                  <tr key={line.id}>
                    <td>
                      <span className="mono">{line.accountCode}</span> {line.accountName}
                      {line.partnerName ? (
                        <span className="hint"> · {line.partnerName}</span>
                      ) : null}
                    </td>
                    <td className="num mono">
                      {line.debit ? formatMoney(line.debit) : ''}
                    </td>
                    <td className="num mono">
                      {line.credit ? formatMoney(line.credit) : ''}
                    </td>
                    <td>{line.summary ?? ''}</td>
                  </tr>
                ))}
                <tr>
                  <td className="mono">{t('ledger.field.total')}</td>
                  <td className="num mono">{formatMoney(detail.totalDebit)}</td>
                  <td className="num mono">{formatMoney(detail.totalCredit)}</td>
                  <td />
                </tr>
              </tbody>
            </table>
            {detail.remark ? <p className="hint">{detail.remark}</p> : null}
            {detail.reversedBy ? (
              <p className="hint">{t('ledger.hint.reversed', { no: detail.reversedBy })}</p>
            ) : null}
          </>
        ) : null}
      </Drawer>

      <Drawer
        open={writing}
        wide
        title={editingId === null ? t('ledger.action.newVoucher') : t('ledger.action.editVoucher')}
        onClose={reset}
        footer={
          <button
            type="button"
            className="btn btn--primary btn--sm"
            disabled={!balanced || busy || lines.every((line) => line.accountId === '')}
            onClick={submit}
          >
            {t('common.save')}
          </button>
        }
      >
        <div className="form-grid">
          <label className="field">
            <span className="hint">{t('ledger.field.voucherType')}</span>
            <select
              className="select"
              value={voucherType}
              onChange={(event) => setVoucherType(event.target.value as VoucherType)}
            >
              {(['MANUAL', 'OPENING'] as const).map((value) => (
                <option key={value} value={value}>
                  {t(`ledger.voucherType.${value}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="hint">{t('ledger.field.bizDate')}</span>
            <input
              className="input"
              type="date"
              value={bizDate}
              onChange={(event) => setBizDate(event.target.value)}
            />
          </label>
        </div>

        <table className="line-list">
          <thead>
            <tr>
              <th>{t('ledger.field.account')}</th>
              <th className="num">{t('ledger.debit')}</th>
              <th className="num">{t('ledger.credit')}</th>
              <th>{t('common.remark')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {lines.map((line, index) => (
              <tr key={index}>
                <td>
                  <select
                    className="select"
                    value={line.accountId}
                    onChange={(event) => {
                      const next = [...lines]
                      next[index] = { ...line, accountId: event.target.value }
                      setLines(next)
                    }}
                  >
                    <option value="">{t('common.pleaseSelect')}</option>
                    {(accountsQuery.data ?? [])
                      .filter((account) => account.leaf && account.active)
                      .map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.code} {account.name}
                        </option>
                      ))}
                  </select>
                </td>
                <td>
                  <input
                    className="input input--num"
                    type="number"
                    step="any"
                    value={line.debit}
                    onChange={(event) => {
                      const next = [...lines]
                      next[index] = { ...line, debit: event.target.value, credit: '' }
                      setLines(next)
                    }}
                  />
                </td>
                <td>
                  <input
                    className="input input--num"
                    type="number"
                    step="any"
                    value={line.credit}
                    onChange={(event) => {
                      const next = [...lines]
                      next[index] = { ...line, credit: event.target.value, debit: '' }
                      setLines(next)
                    }}
                  />
                </td>
                <td>
                  <input
                    className="input"
                    value={line.summary}
                    onChange={(event) => {
                      const next = [...lines]
                      next[index] = { ...line, summary: event.target.value }
                      setLines(next)
                    }}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    disabled={lines.length <= 2}
                    onClick={() => setLines(lines.filter((_, position) => position !== index))}
                  >
                    {t('common.remove')}
                  </button>
                </td>
              </tr>
            ))}
            <tr>
              <td>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => setLines([...lines, emptyLine()])}
                >
                  {t('ledger.action.addLine')}
                </button>
              </td>
              <td className="num mono">{formatMoney(totalDebit)}</td>
              <td className="num mono">{formatMoney(totalCredit)}</td>
              <td colSpan={2} className={balanced ? 'hint' : 'form-error'}>
                {balanced
                  ? t('ledger.hint.balanced')
                  : t('ledger.hint.difference', { difference: formatMoney(difference) })}
              </td>
            </tr>
          </tbody>
        </table>

        <label className="field">
          <span className="hint">{t('common.remark')}</span>
          <input
            className="input"
            value={remark}
            onChange={(event) => setRemark(event.target.value)}
          />
        </label>
      </Drawer>

      <ConfirmDialog
        open={confirming !== null}
        title={
          confirming === 'post' ? t('ledger.action.post') : t('ledger.action.reverse')
        }
        confirmLabel={t('common.confirm')}
        danger={confirming === 'reverse'}
        busy={busy}
        body={
          <p>
            {confirming === 'post'
              ? t('ledger.confirm.post')
              : t('ledger.confirm.reverse')}
          </p>
        }
        onConfirm={() => {
          setBusy(true)
          act.mutate(confirming as 'post' | 'reverse', { onSettled: () => setBusy(false) })
        }}
        onCancel={() => setConfirming(null)}
      />
    </>
  )
}
