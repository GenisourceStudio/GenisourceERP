import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import { listWarehouses } from '../../shared/api/inventory'
import { listItems } from '../../shared/api/masterdata'
import {
  cancelOrder,
  closeOrder,
  completeOrder,
  createProductionOrder,
  deleteProductionOrder,
  getProductionOrder,
  issueMaterial,
  listProductionOrders,
  releaseOrder,
  type ProductionOrderStatus,
  type ProductionOrderView,
} from '../../shared/api/production'
import { useAuthStore } from '../../shared/auth/store'
import { formatQty } from '../../shared/format'
import { useDebounced } from '../../shared/hooks/useDebounced'

const PAGE_SIZE = 20
const STATUSES: ProductionOrderStatus[] = [
  'PLANNED',
  'RELEASED',
  'IN_PROGRESS',
  'COMPLETED',
  'CLOSED',
  'CANCELLED',
]

const today = () => new Date().toISOString().slice(0, 10)

/**
 * Works orders.
 *
 * <p>The drawer is where the order is actually worked on: the component list shows what
 * has been issued, what is being held and what is still short, and the operations show
 * the steps that were frozen onto the order. Buttons appear only in the states where the
 * action makes sense, so an operator cannot press "issue" on a draft and get a refusal
 * they did not expect.
 *
 * <p>Nothing here is deleted to undo a mistake. An order that has consumed material is
 * closed so the issue stays on the books; only a draft can be removed outright.
 */
export default function ProductionOrdersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('production:order:write') ?? false
  const canIssue = permissions?.includes('production:issue:write') ?? false
  const canComplete = permissions?.includes('production:complete:write') ?? false

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<ProductionOrderStatus | ''>('')
  const [page, setPage] = useState(0)

  const [openId, setOpenId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [issuing, setIssuing] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [confirming, setConfirming] = useState<'cancel' | 'delete' | 'close' | null>(null)

  const [itemId, setItemId] = useState('')
  const [qtyPlanned, setQtyPlanned] = useState('1')
  const [warehouseId, setWarehouseId] = useState('')
  const [plannedEnd, setPlannedEnd] = useState('')

  const [issueQty, setIssueQty] = useState<Record<number, string>>({})
  const [completeQty, setCompleteQty] = useState('')
  const [completeWarehouse, setCompleteWarehouse] = useState('')

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const listQuery = useQuery({
    queryKey: ['production', 'orders', debouncedKeyword, status, page],
    queryFn: () =>
      listProductionOrders({
        keyword: debouncedKeyword,
        status: status === '' ? null : status,
        page,
        size: PAGE_SIZE,
      }),
  })

  const detailQuery = useQuery({
    queryKey: ['production', 'order', openId],
    queryFn: () => getProductionOrder(openId as number),
    enabled: openId !== null,
  })

  const warehousesQuery = useQuery({ queryKey: ['warehouses'], queryFn: listWarehouses })
  const itemsQuery = useQuery({
    queryKey: ['items', 'manufacturable'],
    queryFn: () => listItems({ page: 0, size: 200 }),
  })

  const order = detailQuery.data

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['production'] })
  }

  const guard = (work: Promise<unknown>, done?: () => void) => {
    setBusy(true)
    setError(null)
    void work
      .then(() => {
        refresh()
        done?.()
      })
      .catch((exception: unknown) =>
        setError(
          exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'),
        ),
      )
      .finally(() => setBusy(false))
  }

  const submitCreate = () => {
    if (itemId === '') {
      setError(t('production.error.itemRequired'))
      return
    }
    guard(
      createProductionOrder({
        itemId: Number(itemId),
        qtyPlanned: Number(qtyPlanned || 0),
        warehouseId: warehouseId === '' ? null : Number(warehouseId),
        plannedStart: today(),
        plannedEnd: plannedEnd === '' ? null : plannedEnd,
      }),
      () => {
        setCreating(false)
        setItemId('')
        setQtyPlanned('1')
        setWarehouseId('')
        setPlannedEnd('')
      },
    )
  }

  const submitIssue = () => {
    if (!order) {
      return
    }
    const lines = order.lines
      .filter((line) => Number(issueQty[line.id] ?? 0) > 0)
      .map((line) => ({ orderLineId: line.id, qty: Number(issueQty[line.id]) }))
    if (lines.length === 0) {
      setError(t('production.error.nothingToIssue'))
      return
    }
    guard(issueMaterial(order.id, { bizDate: today(), lines }), () => {
      setIssuing(false)
      setIssueQty({})
    })
  }

  const submitComplete = () => {
    if (!order || completeWarehouse === '') {
      setError(t('production.error.warehouseRequired'))
      return
    }
    guard(
      completeOrder(order.id, {
        qty: Number(completeQty || 0),
        warehouseId: Number(completeWarehouse),
        bizDate: today(),
      }),
      () => {
        setCompleting(false)
        setCompleteQty('')
      },
    )
  }

  const columns: Column<ProductionOrderView>[] = [
    {
      key: 'docNo',
      header: t('production.field.docNo'),
      width: '180px',
      render: (row) => <span className="mono">{row.docNo}</span>,
    },
    {
      key: 'item',
      header: t('production.field.product'),
      render: (row) => (
        <div>
          <div className="mono">{row.itemCode}</div>
          <div className="hint">{row.itemName}</div>
        </div>
      ),
    },
    {
      key: 'qty',
      header: t('production.field.progress'),
      align: 'right',
      width: '150px',
      render: (row) => (
        <span className="mono">
          {formatQty(row.qtyCompleted)} / {formatQty(row.qtyPlanned)}
        </span>
      ),
    },
    {
      key: 'due',
      header: t('production.field.plannedEnd'),
      width: '110px',
      render: (row) => <span className="mono">{row.plannedEnd ?? '—'}</span>,
    },
    {
      key: 'warehouse',
      header: t('production.field.warehouse'),
      width: '110px',
      render: (row) => <span className="mono">{row.warehouseCode ?? '—'}</span>,
    },
    {
      key: 'source',
      header: t('production.field.source'),
      width: '120px',
      render: (row) =>
        row.sourceType === 'MRP_PLAN' ? (
          <span className="tag tag--submitted">{t('production.source.mrp')}</span>
        ) : null,
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '110px',
      render: (row) => (
        <span className={`tag tag--${row.status.toLowerCase()}`}>
          {t(`status.production.${row.status}`)}
        </span>
      ),
    },
  ]

  const result = listQuery.data

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('production.searchPlaceholder')}
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
            setStatus(event.target.value as ProductionOrderStatus | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allStatus')}</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {t(`status.production.${value}`)}
            </option>
          ))}
        </select>
        <div className="toolbar__spacer" />
        <button
          type="button"
          className="btn btn--primary btn--sm"
          onClick={() => setCreating(true)}
          disabled={!canWrite}
        >
          {t('production.action.create')}
        </button>
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(row) => row.id}
        loading={listQuery.isLoading}
        error={listQuery.error instanceof ApiError ? listQuery.error.message : null}
        activeKey={openId}
        onRowClick={(row) => {
          setOpenId(row.id)
          setIssueQty({})
          setCompleteQty(String(row.remainingQty))
          setCompleteWarehouse(row.warehouseId ? String(row.warehouseId) : '')
        }}
        emptyTitle={t('production.empty.title')}
        emptyBody={t('production.empty.body')}
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
        open={openId !== null}
        wide
        title={order?.docNo ?? ''}
        subtitle={
          order
            ? `${order.itemCode ?? ''} · ${t(`status.production.${order.status}`)}`
            : undefined
        }
        onClose={() => setOpenId(null)}
        footer={
          order ? (
            <div className="row gap-8">
              {canWrite && order.status === 'PLANNED' ? (
                <button
                  type="button"
                  className="btn btn--primary btn--sm"
                  disabled={busy}
                  onClick={() => guard(releaseOrder(order.id))}
                >
                  {t('production.action.release')}
                </button>
              ) : null}
              {canIssue && (order.status === 'RELEASED' || order.status === 'IN_PROGRESS') ? (
                <button
                  type="button"
                  className="btn btn--sm"
                  disabled={busy}
                  onClick={() => {
                    setIssuing(true)
                    const next: Record<number, string> = {}
                    order.lines.forEach((line) => {
                      next[line.id] = String(line.openQty)
                    })
                    setIssueQty(next)
                  }}
                >
                  {t('production.action.issue')}
                </button>
              ) : null}
              {canComplete &&
              (order.status === 'RELEASED' || order.status === 'IN_PROGRESS') ? (
                <button
                  type="button"
                  className="btn btn--sm"
                  disabled={busy}
                  onClick={() => {
                    setCompleting(true)
                    setCompleteQty(String(order.remainingQty))
                  }}
                >
                  {t('production.action.complete')}
                </button>
              ) : null}
              {canWrite && order.status !== 'COMPLETED' && order.status !== 'CANCELLED' ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  disabled={busy}
                  onClick={() => setConfirming('close')}
                >
                  {t('production.action.close')}
                </button>
              ) : null}
              {canWrite && order.status === 'PLANNED' ? (
                <>
                  <button
                    type="button"
                    className="btn btn--danger btn--sm"
                    disabled={busy}
                    onClick={() => setConfirming('cancel')}
                  >
                    {t('production.action.cancel')}
                  </button>
                  <button
                    type="button"
                    className="btn btn--danger btn--sm"
                    disabled={busy}
                    onClick={() => setConfirming('delete')}
                  >
                    {t('common.delete')}
                  </button>
                </>
              ) : null}
            </div>
          ) : null
        }
      >
        {order ? (
          <>
            <div className="form-grid form-grid--3">
              <div className="field">
                <span className="hint">{t('production.field.qtyPlanned')}</span>
                <div className="mono">{formatQty(order.qtyPlanned)}</div>
              </div>
              <div className="field">
                <span className="hint">{t('production.field.qtyCompleted')}</span>
                <div className="mono">{formatQty(order.qtyCompleted)}</div>
              </div>
              <div className="field">
                <span className="hint">{t('production.field.remaining')}</span>
                <div className="mono">{formatQty(order.remainingQty)}</div>
              </div>
              <div className="field">
                <span className="hint">{t('production.field.bom')}</span>
                <div className="mono">{order.bomVersion ?? '—'}</div>
              </div>
              <div className="field">
                <span className="hint">{t('production.field.plannedEnd')}</span>
                <div className="mono">{order.plannedEnd ?? '—'}</div>
              </div>
              <div className="field">
                <span className="hint">{t('production.field.warehouse')}</span>
                <div className="mono">{order.warehouseCode ?? '—'}</div>
              </div>
            </div>

            <h3 className="section-title">{t('production.section.components')}</h3>
            <table className="line-list">
              <thead>
                <tr>
                  <th>{t('planning.field.item')}</th>
                  <th className="num">{t('production.field.required')}</th>
                  <th className="num">{t('production.field.issued')}</th>
                  <th className="num">{t('production.field.locked')}</th>
                  <th className="num">{t('production.field.open')}</th>
                </tr>
              </thead>
              <tbody>
                {order.lines.map((line) => (
                  <tr key={line.id}>
                    <td>
                      <span className="mono">{line.itemCode}</span>
                      <span className="hint"> · {line.itemName}</span>
                    </td>
                    <td className="num mono">{formatQty(line.qtyRequired)}</td>
                    <td className="num mono">{formatQty(line.qtyIssued)}</td>
                    <td className="num mono">{formatQty(line.qtyLocked)}</td>
                    <td className="num mono">{formatQty(line.openQty)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {order.operations.length > 0 ? (
              <>
                <h3 className="section-title">{t('production.section.operations')}</h3>
                <table className="line-list">
                  <thead>
                    <tr>
                      <th style={{ width: 60 }}>{t('production.field.sequence')}</th>
                      <th>{t('production.field.operation')}</th>
                      <th>{t('production.field.workCenter')}</th>
                      <th className="num">{t('production.field.setupMinutes')}</th>
                      <th className="num">{t('production.field.runMinutes')}</th>
                      <th className="num">{t('common.status')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.operations.map((operation) => (
                      <tr key={operation.sequence}>
                        <td className="mono">{operation.sequence}</td>
                        <td>{operation.name}</td>
                        <td className="mono">{operation.workCenterCode ?? '—'}</td>
                        <td className="num mono">{formatQty(operation.setupMinutes)}</td>
                        <td className="num mono">{formatQty(operation.runMinutes)}</td>
                        <td className="num">
                          <span
                            className={`tag tag--${operation.status === 'DONE' ? 'completed' : 'draft'}`}
                          >
                            {t(`status.operation.${operation.status}`)}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            ) : null}

            {order.remark ? <p className="hint" style={{ marginTop: 16 }}>{order.remark}</p> : null}
          </>
        ) : null}
      </Drawer>

      <ConfirmDialog
        open={creating}
        title={t('production.action.create')}
        confirmLabel={t('common.confirm')}
        busy={busy}
        onCancel={() => setCreating(false)}
        onConfirm={submitCreate}
        body={
          <div className="form-grid">
            <label className="field">
              <span className="hint">{t('production.field.product')}</span>
              <select
                className="select"
                value={itemId}
                onChange={(event) => setItemId(event.target.value)}
              >
                <option value="">{t('common.pleaseSelect')}</option>
                {(itemsQuery.data?.content ?? [])
                  .filter((item) => item.status === 'ACTIVE')
                  .map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.code} · {item.name}
                    </option>
                  ))}
              </select>
            </label>
            <label className="field">
              <span className="hint">{t('production.field.qtyPlanned')}</span>
              <input
                className="input input--num"
                type="number"
                min={0}
                step="any"
                value={qtyPlanned}
                onChange={(event) => setQtyPlanned(event.target.value)}
              />
            </label>
            <label className="field">
              <span className="hint">{t('production.field.warehouse')}</span>
              <select
                className="select"
                value={warehouseId}
                onChange={(event) => setWarehouseId(event.target.value)}
              >
                <option value="">{t('planning.field.warehouseLater')}</option>
                {(warehousesQuery.data ?? []).map((warehouse) => (
                  <option key={warehouse.id} value={warehouse.id}>
                    {warehouse.code} · {warehouse.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span className="hint">{t('production.field.plannedEnd')}</span>
              <input
                className="input"
                type="date"
                value={plannedEnd}
                onChange={(event) => setPlannedEnd(event.target.value)}
              />
            </label>
          </div>
        }
      />

      <ConfirmDialog
        open={issuing}
        title={t('production.action.issue')}
        confirmLabel={t('production.action.issue')}
        busy={busy}
        onCancel={() => setIssuing(false)}
        onConfirm={submitIssue}
        body={
          order ? (
            <div>
              <p>{t('production.confirm.issue')}</p>
              <table className="line-list">
                <tbody>
                  {order.lines.map((line) => (
                    <tr key={line.id}>
                      <td>
                        <span className="mono">{line.itemCode}</span>
                        <span className="hint"> · {formatQty(line.openQty)}</span>
                      </td>
                      <td style={{ width: 120 }}>
                        <input
                          className="input input--num"
                          type="number"
                          min={0}
                          step="any"
                          value={issueQty[line.id] ?? ''}
                          onChange={(event) =>
                            setIssueQty({ ...issueQty, [line.id]: event.target.value })
                          }
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            ''
          )
        }
      />

      <ConfirmDialog
        open={completing}
        title={t('production.action.complete')}
        confirmLabel={t('production.action.complete')}
        busy={busy}
        onCancel={() => setCompleting(false)}
        onConfirm={submitComplete}
        body={
          <div>
            <p>{t('production.confirm.complete')}</p>
            <label className="field">
              <span className="hint">{t('production.field.qtyCompleted')}</span>
              <input
                className="input input--num"
                type="number"
                min={0}
                step="any"
                value={completeQty}
                onChange={(event) => setCompleteQty(event.target.value)}
              />
            </label>
            <label className="field">
              <span className="hint">{t('production.field.intoWarehouse')}</span>
              <select
                className="select"
                value={completeWarehouse}
                onChange={(event) => setCompleteWarehouse(event.target.value)}
              >
                <option value="">{t('common.pleaseSelect')}</option>
                {(warehousesQuery.data ?? []).map((warehouse) => (
                  <option key={warehouse.id} value={warehouse.id}>
                    {warehouse.code} · {warehouse.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
        }
      />

      <ConfirmDialog
        open={confirming !== null}
        danger={confirming !== 'close'}
        title={
          confirming === 'close'
            ? t('production.action.close')
            : confirming === 'delete'
              ? t('common.delete')
              : t('production.action.cancel')
        }
        confirmLabel={t('common.confirm')}
        busy={busy}
        onCancel={() => setConfirming(null)}
        onConfirm={() => {
          if (!order || confirming === null) {
            return
          }
          const target = order.id
          guard(
            confirming === 'close'
              ? closeOrder(target)
              : confirming === 'delete'
                ? deleteProductionOrder(target)
                : cancelOrder(target),
            () => {
              setConfirming(null)
              if (confirming === 'delete') {
                setOpenId(null)
              }
            },
          )
        }}
        body={
          confirming === 'close'
            ? t('production.confirm.close')
            : confirming === 'delete'
              ? t('production.confirm.delete')
              : t('production.confirm.cancel')
        }
      />
    </>
  )
}
