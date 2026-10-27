import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import { listWarehouses } from '../../shared/api/inventory'
import {
  convertToProductionOrder,
  convertToPurchaseOrder,
  getMrpRun,
  ignorePlan,
  listMrpRuns,
  runMrp,
  type MrpDemandView,
  type MrpPlanView,
  type MrpRunView,
} from '../../shared/api/mrp'
import { useAuthStore } from '../../shared/auth/store'
import { formatQty } from '../../shared/format'

const PAGE_SIZE = 10

type Action =
  | { kind: 'purchase'; plan: MrpPlanView }
  | { kind: 'production'; plan: MrpPlanView }
  | { kind: 'ignore'; plan: MrpPlanView }
  | null

/**
 * Material requirements planning.
 *
 * <p>Two tables, in the order the planner reads them: what the run recommends obtaining,
 * then the arithmetic that produced each recommendation. Showing only the recommendations
 * would make the screen a to-do list nobody can argue with; showing the working lets a
 * planner say "we are not buying that, we have them on order" and act on it.
 *
 * <p>Running MRP is an explicit button, not something that happens on page load. A run
 * writes a snapshot with its own number, and generating one every time somebody opens a
 * tab would bury the run they came to read.
 */
export default function MrpPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canRun = permissions?.includes('mrp:run:write') ?? false
  const canBuy = permissions?.includes('purchase:order:write') ?? false
  const canBuild = permissions?.includes('production:order:write') ?? false

  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [running, setRunning] = useState(false)
  const [action, setAction] = useState<Action>(null)
  const [quantity, setQuantity] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const [warehouseId, setWarehouseId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Run parameters. Seeded from the run on screen so pressing run again reproduces it,
  // which is what a planner comparing two windows actually wants.
  const [horizon, setHorizon] = useState('30')
  const [includeSafety, setIncludeSafety] = useState(true)

  const runsQuery = useQuery({
    queryKey: ['mrp', 'runs', page],
    queryFn: () => listMrpRuns(page, PAGE_SIZE),
  })

  const activeId = selectedId ?? runsQuery.data?.content[0]?.id ?? null

  const detailQuery = useQuery({
    queryKey: ['mrp', 'run', activeId],
    queryFn: () => getMrpRun(activeId as number),
    enabled: activeId !== null,
  })

  const warehousesQuery = useQuery({
    queryKey: ['warehouses'],
    queryFn: listWarehouses,
  })

  const detail = detailQuery.data
  const activeRun = detail?.run ?? null

  useEffect(() => {
    if (!activeRun) {
      return
    }
    setHorizon(String(activeRun.horizonDays))
    setIncludeSafety(activeRun.includeSafety)
  }, [activeRun?.id, activeRun?.horizonDays, activeRun?.includeSafety])

  const startRun = () => {
    setRunning(true)
    setError(null)
    void runMrp({
      horizonDays: Number(horizon) > 0 ? Number(horizon) : 30,
      includeSafety,
    })
      .then((created) => {
        setSelectedId(created.run.id)
        setPage(0)
        void queryClient.invalidateQueries({ queryKey: ['mrp'] })
      })
      .catch((exception: unknown) =>
        setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
      )
      .finally(() => setRunning(false))
  }

  const confirm = () => {
    if (!action) {
      return
    }
    setBusy(true)
    setError(null)
    const { plan } = action
    const request = {
      qty: quantity === '' ? null : Number(quantity),
      bizDate: new Date().toISOString().slice(0, 10),
      unitPrice: action.kind === 'purchase' && unitPrice !== '' ? Number(unitPrice) : null,
      warehouseId: warehouseId === '' ? null : Number(warehouseId),
    }
    const work =
      action.kind === 'purchase'
        ? convertToPurchaseOrder(plan.id, request)
        : action.kind === 'production'
          ? convertToProductionOrder(plan.id, request)
          : ignorePlan(plan.id, null)
    void work
      .then(() => {
        void queryClient.invalidateQueries({ queryKey: ['mrp'] })
        setAction(null)
        setQuantity('')
        setUnitPrice('')
        setWarehouseId('')
      })
      .catch((exception: unknown) =>
        setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
      )
      .finally(() => setBusy(false))
  }

  const runColumns: Column<MrpRunView>[] = useMemo(
    () => [
      {
        key: 'runNo',
        header: t('planning.field.runNo'),
        width: '190px',
        render: (row) => <span className="mono">{row.runNo}</span>,
      },
      {
        key: 'window',
        header: t('planning.field.window'),
        render: (row) => (
          <span className="hint mono">
            {row.demandFrom ?? '—'} → {row.demandTo ?? '—'}
          </span>
        ),
      },
      {
        key: 'shortage',
        header: t('planning.field.shortageCount'),
        align: 'right',
        width: '90px',
        render: (row) => (
          <span className={row.shortageCount > 0 ? 'mono text-danger' : 'mono'}>
            {row.shortageCount}
          </span>
        ),
      },
      {
        key: 'supply',
        header: t('planning.field.supplyCount'),
        align: 'right',
        width: '90px',
        render: (row) => <span className="mono">{row.supplyCount}</span>,
      },
      {
        key: 'items',
        header: t('planning.field.itemCount'),
        align: 'right',
        width: '90px',
        render: (row) => <span className="hint mono">{row.itemCount}</span>,
      },
      {
        key: 'runBy',
        header: t('planning.field.runBy'),
        width: '140px',
        render: (row) => row.runBy ?? null,
      },
      {
        key: 'runAt',
        header: t('planning.field.runAt'),
        width: '170px',
        render: (row) => <span className="hint mono">{row.runAt.slice(0, 16).replace('T', ' ')}</span>,
      },
    ],
    [t],
  )

  const planColumns: Column<MrpPlanView>[] = [
    {
      key: 'item',
      header: t('planning.field.item'),
      render: (row) => (
        <div>
          <div className="mono">{row.itemCode}</div>
          <div className="hint">{row.itemName}</div>
        </div>
      ),
    },
    {
      key: 'supplyType',
      header: t('planning.field.supplyType'),
      width: '110px',
      render: (row) => (
        <span className={`tag tag--${row.supplyType === 'PURCHASE' ? 'approved' : 'submitted'}`}>
          {t(`planning.supply.${row.supplyType}`)}
        </span>
      ),
    },
    {
      key: 'netQty',
      header: t('planning.field.netQty'),
      align: 'right',
      width: '100px',
      render: (row) => <span className="hint mono">{formatQty(row.netQty)}</span>,
    },
    {
      key: 'qty',
      header: t('planning.field.qty'),
      align: 'right',
      width: '110px',
      render: (row) => (
        <strong className="mono">
          {formatQty(row.qty)} {row.uomCode ?? ''}
        </strong>
      ),
    },
    {
      key: 'dueDate',
      header: t('planning.field.dueDate'),
      width: '110px',
      render: (row) => <span className="mono">{row.dueDate ?? '—'}</span>,
    },
    {
      key: 'party',
      header: t('planning.field.party'),
      width: '140px',
      render: (row) => row.supplierName ?? null,
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '100px',
      render: (row) => (
        <span className={`tag tag--${row.status.toLowerCase()}`}>
          {t(`planning.planStatus.${row.status}`)}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: '210px',
      align: 'right',
      render: (row) => {
        if (!canRun || row.status !== 'PLANNED') {
          return row.convertedNo ? <span className="hint mono">{row.convertedNo}</span> : null
        }
        return (
          <div className="row-actions">
            {row.supplyType === 'PURCHASE' && canBuy ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={(event) => {
                  event.stopPropagation()
                  setAction({ kind: 'purchase', plan: row })
                  setQuantity(String(row.qty))
                }}
              >
                {t('planning.action.toPurchase')}
              </button>
            ) : null}
            {row.supplyType === 'PRODUCTION' && canBuild ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={(event) => {
                  event.stopPropagation()
                  setAction({ kind: 'production', plan: row })
                  setQuantity(String(row.qty))
                }}
              >
                {t('planning.action.toProduction')}
              </button>
            ) : null}
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setAction({ kind: 'ignore', plan: row })
              }}
            >
              {t('planning.action.ignore')}
            </button>
          </div>
        )
      },
    },
  ]

  const demandColumns: Column<MrpDemandView>[] = [
    {
      key: 'item',
      header: t('planning.field.item'),
      render: (row) => (
        <div>
          <span className="mono">{row.itemCode}</span>
          <span className="hint"> · L{row.itemLevel}</span>
        </div>
      ),
    },
    {
      key: 'gross',
      header: t('planning.field.grossQty'),
      align: 'right',
      width: '96px',
      render: (row) => <span className="mono">{formatQty(row.grossQty)}</span>,
    },
    {
      key: 'sales',
      header: t('planning.field.fromSales'),
      align: 'right',
      width: '96px',
      render: (row) => <span className="hint mono">{formatQty(row.fromSales)}</span>,
    },
    {
      key: 'components',
      header: t('planning.field.fromComponents'),
      align: 'right',
      width: '96px',
      render: (row) => <span className="hint mono">{formatQty(row.fromComponents)}</span>,
    },
    {
      key: 'onHand',
      header: t('planning.field.onHand'),
      align: 'right',
      width: '96px',
      render: (row) => <span className="mono">{formatQty(row.onHand)}</span>,
    },
    {
      key: 'reserved',
      header: t('planning.field.reservedQty'),
      align: 'right',
      width: '96px',
      render: (row) => <span className="hint mono">{formatQty(row.reservedQty)}</span>,
    },
    {
      key: 'inTransit',
      header: t('planning.field.inTransit'),
      align: 'right',
      width: '96px',
      render: (row) => <span className="hint mono">{formatQty(row.inTransit)}</span>,
    },
    {
      key: 'safety',
      header: t('planning.field.safetyStock'),
      align: 'right',
      width: '96px',
      render: (row) => <span className="hint mono">{formatQty(row.safetyStock)}</span>,
    },
    {
      key: 'net',
      header: t('planning.field.netQty'),
      align: 'right',
      width: '100px',
      render: (row) => (
        <strong className={row.shortage ? 'mono text-danger' : 'mono'}>
          {formatQty(row.netQty)}
        </strong>
      ),
    },
  ]

  const runs = runsQuery.data

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      <div className="toolbar">
        <div className="toolbar__search">
          <select
            className="select"
            value={activeId ?? ''}
            onChange={(event) => setSelectedId(Number(event.target.value))}
            disabled={!runs || runs.content.length === 0}
          >
            {(runs?.content ?? []).map((run) => (
              <option key={run.id} value={run.id}>
                {run.runNo} · {run.shortageCount} {t('planning.field.shortageUnit')}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar__spacer" />
        {activeRun ? (
          <span className="hint">
            {t('planning.field.window')} {activeRun.demandFrom} → {activeRun.demandTo}
            {' · '}
            {activeRun.includeSafety
              ? t('planning.field.withSafety')
              : t('planning.field.withoutSafety')}
          </span>
        ) : null}
        <label className="row gap-8" style={{ fontSize: 12, color: 'var(--muted)' }}>
          {t('planning.field.horizon')}
          <input
            className="input input--num"
            style={{ width: 72 }}
            type="number"
            min={1}
            max={365}
            value={horizon}
            onChange={(event) => setHorizon(event.target.value)}
            disabled={!canRun || running}
          />
        </label>
        <label className="row gap-8" style={{ fontSize: 12, color: 'var(--muted)' }}>
          <input
            type="checkbox"
            checked={includeSafety}
            onChange={(event) => setIncludeSafety(event.target.checked)}
            disabled={!canRun || running}
          />
          {t('planning.field.safetyStock')}
        </label>
        <button
          type="button"
          className="btn btn--primary btn--sm"
          onClick={startRun}
          disabled={!canRun || running}
        >
          {running ? t('common.saving') : t('planning.action.run')}
        </button>
      </div>

      {activeRun ? (
        <div className="grid grid--4" style={{ marginBottom: 16 }}>
          <div className="stat">
            <div className="stat__label">{t('planning.field.shortageCount')}</div>
            <div className="stat__value text-danger">{activeRun.shortageCount}</div>
          </div>
          <div className="stat">
            <div className="stat__label">{t('planning.field.supplyCount')}</div>
            <div className="stat__value">{activeRun.supplyCount}</div>
          </div>
          <div className="stat">
            <div className="stat__label">{t('planning.field.itemCount')}</div>
            <div className="stat__value">{activeRun.itemCount}</div>
          </div>
          <div className="stat">
            <div className="stat__label">{t('planning.field.runAt')}</div>
            <div className="stat__value" style={{ fontSize: 16 }}>
              {activeRun.runAt.slice(0, 16).replace('T', ' ')}
            </div>
            <div className="stat__hint">{activeRun.runBy ?? ''}</div>
          </div>
        </div>
      ) : null}

      <h3 className="section-title">{t('planning.section.recommendations')}</h3>
      <DataTable
        columns={planColumns}
        rows={detail?.plans ?? []}
        rowKey={(row) => row.id}
        loading={detailQuery.isLoading}
        error={detailQuery.error instanceof ApiError ? detailQuery.error.message : null}
        emptyTitle={t('planning.emptyPlans.title')}
        emptyBody={t('planning.emptyPlans.body')}
      />

      <h3 className="section-title" style={{ marginTop: 28 }}>
        {t('planning.section.evidence')}
      </h3>
      <DataTable
        columns={demandColumns}
        rows={detail?.demands ?? []}
        rowKey={(row) => row.id}
        loading={detailQuery.isLoading}
        emptyTitle={t('planning.emptyDemands.title')}
        emptyBody={t('planning.emptyDemands.body')}
      />

      <h3 className="section-title" style={{ marginTop: 28 }}>
        {t('planning.section.history')}
      </h3>
      <DataTable
        columns={runColumns}
        rows={runs?.content ?? []}
        rowKey={(row) => row.id}
        loading={runsQuery.isLoading}
        error={runsQuery.error instanceof ApiError ? runsQuery.error.message : null}
        activeKey={activeId}
        onRowClick={(row) => setSelectedId(row.id)}
        emptyTitle={t('planning.emptyRuns.title')}
        emptyBody={t('planning.emptyRuns.body')}
      />
      {runs ? (
        <Pager
          page={runs.page}
          size={runs.size}
          total={runs.total}
          pageCount={runs.totalPages}
          onChange={setPage}
        />
      ) : null}

      <ConfirmDialog
        open={action !== null}
        title={
          action?.kind === 'purchase'
            ? t('planning.action.toPurchase')
            : action?.kind === 'production'
              ? t('planning.action.toProduction')
              : t('planning.action.ignore')
        }
        body={
          action ? (
            <div>
              <p>
                {action.kind === 'ignore'
                  ? t('planning.confirm.ignore', {
                      item: action.plan.itemCode,
                      qty: formatQty(action.plan.qty),
                    })
                  : t('planning.confirm.convert', {
                      item: action.plan.itemCode,
                      qty: formatQty(action.plan.qty),
                    })}
              </p>
              {action.kind === 'ignore' ? null : (
                <div className="form-grid" style={{ marginTop: 8 }}>
                  <label className="field">
                    <span className="hint">{t('planning.field.qty')}</span>
                    <input
                      className="input input--num"
                      type="number"
                      min={0}
                      step="any"
                      value={quantity}
                      onChange={(event) => setQuantity(event.target.value)}
                    />
                  </label>
                  {action.kind === 'purchase' ? (
                    <label className="field">
                      <span className="hint">{t('planning.field.unitPrice')}</span>
                      <input
                        className="input input--num"
                        type="number"
                        min={0}
                        step="any"
                        value={unitPrice}
                        placeholder={t('planning.field.unitPriceHint')}
                        onChange={(event) => setUnitPrice(event.target.value)}
                      />
                    </label>
                  ) : (
                    <label className="field">
                      <span className="hint">{t('planning.field.warehouse')}</span>
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
                  )}
                </div>
              )}
            </div>
          ) : (
            ''
          )
        }
        confirmLabel={
          action?.kind === 'ignore' ? t('planning.action.ignore') : t('common.confirm')
        }
        danger={action?.kind === 'ignore'}
        busy={busy}
        onCancel={() => {
          setAction(null)
          setQuantity('')
          setUnitPrice('')
          setWarehouseId('')
        }}
        onConfirm={confirm}
      />
    </>
  )
}
