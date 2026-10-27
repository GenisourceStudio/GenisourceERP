import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import { listWarehouses } from '../../shared/api/inventory'
import { useAuthStore } from '../../shared/auth/store'
import { formatMoney, formatQty } from '../../shared/format'
import { useDebounced } from '../../shared/hooks/useDebounced'
import PickLinesDialog from './PickLinesDialog'
import TradeDocumentEditor from './TradeDocumentEditor'
import {
  LIFECYCLE_STATUSES,
  type DocumentLifecycle,
  type TradeConfig,
  type TradeDocument,
} from './types'

const PAGE_SIZE = 20

type ActionKind =
  | 'submit'
  | 'approve'
  | 'reject'
  | 'post'
  | 'reverse'
  | 'close'
  | 'cancel'
  | 'delete'

interface Pending {
  kind: ActionKind
  document: TradeDocument
}

/** Statuses after which nothing more will happen to a document. */
function isTerminal(status: DocumentLifecycle): boolean {
  return status === 'POSTED' || status === 'REVERSED' || status === 'CLOSED' || status === 'CANCELLED'
}

/**
 * The list-and-detail screen for one kind of trade document.
 *
 * <p>Six document kinds, one screen. They differ in which transitions exist — a
 * requisition is converted, an order is received against, a receipt is posted — and the
 * config says which, so a button that would only ever come back as a 409 is not drawn at
 * all. What is shared is the part that is genuinely the same: a number, a partner, some
 * lines, and a status that tells you who has to do something next.
 */
export default function TradeDocumentPage({ config }: { config: TradeConfig }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes(config.writePermission) ?? false

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<DocumentLifecycle | ''>('')
  const [page, setPage] = useState(0)

  const [editorId, setEditorId] = useState<number | 'new' | null>(null)
  const [detail, setDetail] = useState<TradeDocument | null>(null)
  const [pending, setPending] = useState<Pending | null>(null)
  const [picking, setPicking] = useState<'convert' | 'fulfil' | null>(null)
  const [remark, setRemark] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const warehousesQuery = useQuery({ queryKey: ['warehouses'], queryFn: listWarehouses })
  const listQuery = useQuery({
    queryKey: ['trade', config.kindKey, debouncedKeyword, status, page],
    queryFn: () =>
      config.list({
        keyword: debouncedKeyword,
        status: status === '' ? null : status,
        page,
        size: PAGE_SIZE,
      }),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['trade'] })
    void queryClient.invalidateQueries({ queryKey: ['stock'] })
    void queryClient.invalidateQueries({ queryKey: ['movements'] })
    void queryClient.invalidateQueries({ queryKey: ['documents'] })
    void queryClient.invalidateQueries({ queryKey: ['ledger'] })
    for (const key of config.invalidates ?? []) {
      void queryClient.invalidateQueries({ queryKey: key })
    }
  }

  const run = async (work: () => Promise<unknown>, closeDetail = true) => {
    setBusy(true)
    setError(null)
    try {
      await work()
      invalidate()
      setPending(null)
      setRemark('')
      if (closeDetail) {
        setDetail(null)
      }
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  const confirmPending = () => {
    const target = pending
    if (!target) {
      return
    }
    const id = target.document.id
    switch (target.kind) {
      case 'submit':
        void run(() => config.submit!(id))
        break
      case 'approve':
        void run(() => config.approve!(id, { remark: remark || null }))
        break
      case 'reject':
        void run(() => config.reject!(id, { remark: remark || null }))
        break
      case 'post':
        void run(() => config.post!(id))
        break
      case 'reverse':
        void run(() => config.reverse!(id))
        break
      case 'close':
        void run(() => config.close!(id))
        break
      case 'cancel':
        void run(() => config.cancel!(id))
        break
      case 'delete':
        void run(() => config.remove!(id))
        break
    }
  }

  const submitPick = (request: {
    warehouseId: number
    bizDate: string
    remark: string | null
    lines: { lineId: number; qty: number }[]
  }) => {
    const source = detail
    if (!source) {
      return
    }
    const work =
      picking === 'convert'
        ? config.convert!(source.id, {
            partnerId: source.partnerId,
            bizDate: request.bizDate,
            remark: request.remark,
            lines: request.lines,
          })
        : config.fulfil!(source.id, {
            warehouseId: request.warehouseId,
            bizDate: request.bizDate,
            remark: request.remark,
            lines: request.lines,
          })
    setBusy(true)
    setError(null)
    void work
      .then(() => {
        invalidate()
        setPicking(null)
        setDetail(null)
      })
      .catch((exception: unknown) =>
        setError(
          exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'),
        ),
      )
      .finally(() => setBusy(false))
  }

  const can = (kind: ActionKind, document: TradeDocument): boolean => {
    if (!canWrite) {
      return false
    }
    switch (kind) {
      case 'submit':
        return !!config.submit && document.status === 'DRAFT'
      case 'approve':
      case 'reject':
        return !!config[kind] && document.status === 'SUBMITTED'
      case 'post':
        return !!config.post && document.status === 'DRAFT'
      case 'reverse':
        return !!config.reverse && document.status === 'POSTED'
      case 'close':
        return (
          !!config.close &&
          (document.status === 'APPROVED' ||
            document.status === 'PARTIAL' ||
            document.status === 'COMPLETED')
        )
      case 'cancel':
        return !!config.cancel && !isTerminal(document.status) && document.status !== 'COMPLETED'
      case 'delete':
        return !!config.remove && document.status === 'DRAFT'
    }
  }

  /** What the list offers in the row: the one thing most likely to be next. */
  const nextStep = (document: TradeDocument): Pending | null => {
    if (!canWrite) {
      return null
    }
    if (can('submit', document)) {
      return { kind: 'submit', document }
    }
    if (can('post', document)) {
      return { kind: 'post', document }
    }
    if (can('reverse', document)) {
      return { kind: 'reverse', document }
    }
    if (
      config.fulfil &&
      (document.status === 'APPROVED' || document.status === 'PARTIAL')
    ) {
      return null
    }
    return null
  }

  const columns: Column<TradeDocument>[] = [
    {
      key: 'docNo',
      header: t('trade.field.docNo'),
      width: '170px',
      render: (document) => <span className="mono">{document.docNo}</span>,
    },
    {
      key: 'partner',
      header: t('trade.field.partner'),
      render: (document) => document.partnerName ?? null,
    },
    {
      key: 'warehouse',
      header: t('trade.field.warehouse'),
      width: '150px',
      render: (document) => document.warehouseName ?? null,
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '110px',
      render: (document) => (
        <span className={`tag tag--${document.status.toLowerCase()}`}>
          {t(`status.trade.${document.status}`)}
        </span>
      ),
    },
    {
      key: 'qty',
      header: t('trade.field.qty'),
      align: 'right',
      width: '90px',
      render: (document) => <span className="mono">{formatQty(document.totalQty)}</span>,
    },
    {
      key: 'amount',
      header: t('trade.field.totalAmount'),
      align: 'right',
      width: '110px',
      render: (document) => <span className="mono">{formatMoney(document.totalAmount)}</span>,
    },
    {
      key: 'date',
      header: t('trade.field.bizDate'),
      width: '110px',
      render: (document) => <span className="hint mono">{document.bizDate}</span>,
    },
    {
      key: 'actions',
      header: '',
      width: '120px',
      align: 'right',
      render: (document) => {
        const step = nextStep(document)
        return (
          <div className="row-actions">
            {step ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={(event) => {
                  event.stopPropagation()
                  setDetail(document)
                  setPending(step)
                }}
              >
                {t(`trade.action.${step.kind}`)}
              </button>
            ) : null}
          </div>
        )
      },
    },
  ]

  const result = listQuery.data
  const warehouses = (warehousesQuery.data ?? []).map((warehouse) => ({
    id: warehouse.id,
    code: warehouse.code,
    name: warehouse.name,
  }))

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('trade.searchPlaceholder')}
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
            setStatus(event.target.value as DocumentLifecycle | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allStatus')}</option>
          {LIFECYCLE_STATUSES.map((value) => (
            <option key={value} value={value}>
              {t(`status.trade.${value}`)}
            </option>
          ))}
        </select>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => setEditorId('new')}
          >
            {t('trade.action.create')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(document) => document.id}
        loading={listQuery.isLoading}
        error={listQuery.error instanceof ApiError ? listQuery.error.message : null}
        emptyTitle={t('trade.empty.title')}
        emptyBody={t('trade.empty.body')}
        onRowClick={(document) => setDetail(document)}
      />

      {result ? (
        <Pager
          page={result.page}
          size={result.size}
          total={result.total}
          pageCount={result.totalPages}
          onChange={setPage}
        />
      ) : null}

      <Drawer
        open={editorId !== null}
        wide
        title={editorId === 'new' ? t('trade.action.create') : t('common.edit')}
        subtitle={t(config.kindKey)}
        onClose={() => setEditorId(null)}
      >
        <TradeDocumentEditor
          initial={editorId === 'new' ? null : detail}
          config={config}
          warehouses={warehouses}
          submit={(request) =>
            editorId === 'new'
              ? config.create(request)
              : config.update(editorId as number, request)
          }
          onSaved={() => {
            invalidate()
            setEditorId(null)
            setDetail(null)
          }}
          onCancel={() => setEditorId(null)}
        />
      </Drawer>

      <Drawer
        open={detail !== null && editorId === null && picking === null}
        title={detail?.docNo ?? ''}
        subtitle={
          detail
            ? `${t(config.docTypeKey)} · ${t(`status.trade.${detail.status}`)}`
            : ''
        }
        onClose={() => setDetail(null)}
      >
        {detail ? (
          <div>
            <div className="form-grid form-grid--3" style={{ marginBottom: 18 }}>
              <div className="field">
                <span className="hint">{t('trade.field.partner')}</span>
                <div>{detail.partnerName ?? '—'}</div>
              </div>
              <div className="field">
                <span className="hint">{t('trade.field.warehouse')}</span>
                <div>{detail.warehouseName ?? '—'}</div>
              </div>
              <div className="field">
                <span className="hint">{t('trade.field.bizDate')}</span>
                <div className="mono">{detail.bizDate}</div>
              </div>
              <div className="field">
                <span className="hint">{t('trade.field.paymentTermDays')}</span>
                <div className="mono">{detail.paymentTermDays}</div>
              </div>
              {detail.sourceId ? (
                <div className="field">
                  <span className="hint">{t('trade.field.source')}</span>
                  <div className="mono">{detail.sourceId}</div>
                </div>
              ) : null}
              {detail.approvalRemark ? (
                <div className="field">
                  <span className="hint">{t('trade.field.approvalRemark')}</span>
                  <div>{detail.approvalRemark}</div>
                </div>
              ) : null}
              {detail.reversalOfNo ? (
                <div className="field">
                  <span className="hint">{t('trade.field.reversalOf')}</span>
                  <div className="mono">{detail.reversalOfNo}</div>
                </div>
              ) : null}
            </div>

            <div className="line-list">
              {detail.lines.map((line) => (
                <div className="line-list__row" key={line.id}>
                  <span style={{ flex: 1 }}>
                    <strong>{line.itemCode}</strong> {line.itemName}
                  </span>
                  <span className="mono">
                    {formatQty(Number(line.qty))} {line.uomCode}
                  </span>
                  <span className="mono hint">{formatMoney(Number(line.unitPrice))}</span>
                  <span className="mono">{formatMoney(Number(line.totalAmount))}</span>
                  <span className="hint mono">
                    {t('trade.field.openQty')} {formatQty(Number(line.openQty))}
                    {config.showReserved
                      ? ` · ${t('trade.field.reserved')} ${formatQty(Number(line.qtyReserved ?? 0))}`
                      : ''}
                  </span>
                </div>
              ))}
            </div>

            {detail.remark ? <p className="hint">{detail.remark}</p> : null}

            <div className="row gap-8" style={{ marginTop: 18, flexWrap: 'wrap' }}>
              {detail.status === 'DRAFT' && canWrite ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => {
                    setEditorId(detail.id)
                  }}
                >
                  {t('common.edit')}
                </button>
              ) : null}
              {(['submit', 'approve', 'reject'] as ActionKind[]).map((kind) =>
                can(kind, detail) ? (
                  <button
                    key={kind}
                    type="button"
                    className={kind === 'reject' ? 'btn btn--danger btn--sm' : 'btn btn--primary btn--sm'}
                    onClick={() => setPending({ kind, document: detail })}
                  >
                    {t(`trade.action.${kind}`)}
                  </button>
                ) : null,
              )}
              {config.convert &&
              (detail.status === 'APPROVED' || detail.status === 'PARTIAL') &&
              canWrite ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => setPicking('convert')}
                >
                  {t('trade.action.convert', { kind: t(config.convertKindKey ?? '') })}
                </button>
              ) : null}
              {config.fulfil &&
              (detail.status === 'APPROVED' || detail.status === 'PARTIAL') &&
              canWrite ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => setPicking('fulfil')}
                >
                  {t('trade.action.fulfil', { kind: t(config.fulfilKindKey ?? '') })}
                </button>
              ) : null}
              {can('post', detail) ? (
                <button
                  type="button"
                  className="btn btn--primary btn--sm"
                  onClick={() => setPending({ kind: 'post', document: detail })}
                >
                  {t('trade.action.post')}
                </button>
              ) : null}
              {can('reverse', detail) ? (
                <button
                  type="button"
                  className="btn btn--danger btn--sm"
                  onClick={() => setPending({ kind: 'reverse', document: detail })}
                >
                  {t('trade.action.reverse')}
                </button>
              ) : null}
              {can('close', detail) ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => setPending({ kind: 'close', document: detail })}
                >
                  {t('trade.action.close')}
                </button>
              ) : null}
              {can('cancel', detail) ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => setPending({ kind: 'cancel', document: detail })}
                >
                  {t('trade.action.cancel')}
                </button>
              ) : null}
              {can('delete', detail) ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => setPending({ kind: 'delete', document: detail })}
                >
                  {t('common.delete')}
                </button>
              ) : null}
            </div>
          </div>
        ) : null}
      </Drawer>

      <PickLinesDialog
        open={picking !== null}
        title={
          picking === 'convert'
            ? t('trade.action.convert', { kind: t(config.convertKindKey ?? '') })
            : t('trade.action.fulfil', { kind: t(config.fulfilKindKey ?? '') })
        }
        source={detail}
        warehouses={warehouses}
        askWarehouse={picking === 'fulfil' ? config.fulfilAsksWarehouse ?? true : false}
        confirmLabel={t('common.confirm')}
        busy={busy}
        onSubmit={submitPick}
        onCancel={() => setPicking(null)}
      />

      <ConfirmDialog
        open={pending !== null}
        title={pending ? t(`trade.action.${pending.kind}`) : ''}
        body={
          pending ? (
            <div>
              <p>
                {t(`trade.confirm.${pending.kind}`, {
                  docNo: pending.document.docNo,
                  kind: t(config.kindKey),
                })}
              </p>
              {pending.kind === 'approve' || pending.kind === 'reject' ? (
                <input
                  className="input"
                  value={remark}
                  maxLength={255}
                  placeholder={t('trade.field.approvalRemark')}
                  onChange={(event) => setRemark(event.target.value)}
                />
              ) : null}
            </div>
          ) : (
            ''
          )
        }
        confirmLabel={pending ? t(`trade.action.${pending.kind}`) : ''}
        danger={pending?.kind === 'reject' || pending?.kind === 'reverse' || pending?.kind === 'delete'}
        busy={busy}
        onCancel={() => {
          setPending(null)
          setRemark('')
        }}
        onConfirm={confirmPending}
      />
    </>
  )
}
