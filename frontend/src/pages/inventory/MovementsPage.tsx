import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import {
  listMovements,
  listWarehouses,
  traceLot,
  type MovementType,
  type MovementView,
} from '../../shared/api/inventory'
import { useDebounced } from '../../shared/hooks/useDebounced'
import { formatMoney, formatQty } from '../../shared/format'

const PAGE_SIZE = 20
const TYPES: MovementType[] = [
  'RECEIPT',
  'ISSUE',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'COUNT_GAIN',
  'COUNT_LOSS',
]

/**
 * The inventory ledger.
 *
 * <p>Clicking an entry that carries a batch opens the whole story of that batch:
 * every movement it ever had and what is left of it. That is the question a customer
 * asks when a machine fails.
 */
export default function MovementsPage() {
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')
  const [movementType, setMovementType] = useState<MovementType | ''>('')
  const [warehouseId, setWarehouseId] = useState<number | ''>('')
  const [page, setPage] = useState(0)
  const [traceLotId, setTraceLotId] = useState<number | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const warehousesQuery = useQuery({ queryKey: ['warehouses'], queryFn: listWarehouses })
  const movementsQuery = useQuery({
    queryKey: ['movements', debouncedKeyword, movementType, warehouseId, page],
    queryFn: () =>
      listMovements({
        q: debouncedKeyword,
        movementType: movementType === '' ? null : movementType,
        warehouseId: warehouseId === '' ? null : warehouseId,
        page,
        size: PAGE_SIZE,
      }),
  })

  const traceQuery = useQuery({
    queryKey: ['lot-trace', traceLotId],
    queryFn: () => traceLot(traceLotId!),
    enabled: traceLotId !== null,
  })

  const columns: Column<MovementView>[] = [
    {
      key: 'docNo',
      header: t('inventory.field.document'),
      width: '170px',
      render: (row) => <span className="mono hint">{row.docNo ?? '—'}</span>,
    },
    {
      key: 'type',
      header: t('inventory.field.movementType'),
      width: '120px',
      render: (row) => (
        <span className={row.direction === 'IN' ? 'tag tag--active' : 'tag tag--neutral'}>
          {t(`movementType.${row.movementType}`)}
        </span>
      ),
    },
    {
      key: 'item',
      header: t('inventory.field.item'),
      render: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.itemName}</div>
          <div className="mono hint">
            {row.itemCode}
            {row.lotNo ? ` · ${row.lotNo}` : ''}
          </div>
        </div>
      ),
    },
    {
      key: 'warehouse',
      header: t('inventory.field.warehouse'),
      render: (row) => (
        <div>
          <div>{row.warehouseName}</div>
          {row.locationName ? <div className="hint">{row.locationName}</div> : null}
        </div>
      ),
    },
    {
      key: 'qty',
      header: t('inventory.field.qty'),
      align: 'right',
      width: '100px',
      render: (row) => (
        <span className="mono">
          {row.direction === 'IN' ? '+' : '−'}
          {formatQty(row.qty)} {row.uomCode}
        </span>
      ),
    },
    {
      key: 'unitCost',
      header: t('inventory.field.unitCost'),
      align: 'right',
      width: '90px',
      render: (row) => <span className="mono">{formatMoney(row.unitCost)}</span>,
    },
    {
      key: 'amount',
      header: t('inventory.field.amount'),
      align: 'right',
      width: '110px',
      render: (row) => <span className="mono">{formatMoney(row.amount)}</span>,
    },
    {
      key: 'balance',
      header: t('inventory.field.balanceAfter'),
      align: 'right',
      width: '100px',
      render: (row) => <span className="mono hint">{formatQty(row.balanceQtyAfter)}</span>,
    },
  ]

  const result = movementsQuery.data

  return (
    <>
      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('inventory.searchMovementsPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <select
          className="select"
          value={movementType}
          onChange={(event) => {
            setMovementType(event.target.value as MovementType | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allTypes')}</option>
          {TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`movementType.${type}`)}
            </option>
          ))}
        </select>
        <select
          className="select"
          value={warehouseId}
          onChange={(event) => {
            setWarehouseId(event.target.value === '' ? '' : Number(event.target.value))
            setPage(0)
          }}
        >
          <option value="">{t('common.allWarehouses')}</option>
          {(warehousesQuery.data ?? []).map((warehouse) => (
            <option key={warehouse.id} value={warehouse.id}>
              {warehouse.code} · {warehouse.name}
            </option>
          ))}
        </select>
        <div className="toolbar__spacer" />
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(row) => row.id}
        loading={movementsQuery.isLoading}
        error={movementsQuery.error instanceof ApiError ? movementsQuery.error.message : null}
        emptyTitle={t('inventory.emptyMovements')}
        emptyBody={t('inventory.emptyMovementsBody')}
        onRowClick={(row) => setTraceLotId(row.lotId)}
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
        open={traceLotId !== null}
        title={t('inventory.trace.title')}
        subtitle={traceQuery.data ? `${traceQuery.data.lotNo} · ${traceQuery.data.itemName}` : ''}
        onClose={() => setTraceLotId(null)}
      >
        {traceQuery.isLoading ? <p className="hint">{t('common.loading')}</p> : null}
        {traceQuery.data ? (
          <div>
            <div className="form-grid form-grid--3" style={{ marginBottom: 18 }}>
              <div className="field">
                <span className="hint">{t('inventory.field.onHand')}</span>
                <div className="mono" style={{ fontSize: 18 }}>
                  {formatQty(traceQuery.data.onHand)}
                </div>
              </div>
              <div className="field">
                <span className="hint">{t('inventory.field.totalAmount')}</span>
                <div className="mono" style={{ fontSize: 18 }}>
                  {formatMoney(traceQuery.data.totalAmount)}
                </div>
              </div>
              <div className="field">
                <span className="hint">{t('inventory.field.expiryDate')}</span>
                <div className="mono" style={{ fontSize: 18 }}>
                  {traceQuery.data.expiryDate ?? '—'}
                </div>
              </div>
            </div>

            <div className="line-list">
              {traceQuery.data.movements.map((movement) => (
                <div className="line-list__row" key={movement.id}>
                  <span className="mono hint" style={{ width: 150 }}>
                    {movement.docNo}
                  </span>
                  <span style={{ flex: 1 }}>
                    {t(`movementType.${movement.movementType}`)} · {movement.warehouseName}
                  </span>
                  <span className="mono">
                    {movement.direction === 'IN' ? '+' : '−'}
                    {formatQty(movement.qty)}
                  </span>
                  <span className="mono hint">{formatMoney(movement.amount)}</span>
                </div>
              ))}
            </div>
          </div>
        ) : null}
        {traceQuery.error instanceof ApiError ? (
          <div className="form-error">{traceQuery.error.message}</div>
        ) : null}
      </Drawer>
    </>
  )
}
