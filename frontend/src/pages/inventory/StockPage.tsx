import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import {
  listStock,
  listWarehouses,
  stockSummary,
  type StockView,
} from '../../shared/api/inventory'
import { formatMoney, formatQty } from '../../shared/format'
import { useDebounced } from '../../shared/hooks/useDebounced'

const PAGE_SIZE = 20

/**
 * The live stock balance.
 *
 * <p>Clicking a row opens the item across every place it sits: a balance on one shelf
 * is only half the answer, and the warehouse is where the question usually comes from.
 */
export default function StockPage() {
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')
  const [warehouseId, setWarehouseId] = useState<number | ''>('')
  const [includeEmpty, setIncludeEmpty] = useState(false)
  const [page, setPage] = useState(0)
  const [summaryItem, setSummaryItem] = useState<StockView | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const warehousesQuery = useQuery({ queryKey: ['warehouses'], queryFn: listWarehouses })
  const stockQuery = useQuery({
    queryKey: ['stock', debouncedKeyword, warehouseId, includeEmpty, page],
    queryFn: () =>
      listStock({
        q: debouncedKeyword,
        warehouseId: warehouseId === '' ? null : warehouseId,
        includeEmpty,
        page,
        size: PAGE_SIZE,
      }),
  })

  const summaryQuery = useQuery({
    queryKey: ['stock-summary', summaryItem?.itemId],
    queryFn: () => stockSummary(summaryItem!.itemId),
    enabled: summaryItem !== null,
  })

  const columns: Column<StockView>[] = [
    {
      key: 'item',
      header: t('inventory.field.item'),
      render: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.itemName}</div>
          <div className="mono hint">{row.itemCode}</div>
        </div>
      ),
    },
    {
      key: 'warehouse',
      header: t('inventory.field.warehouse'),
      render: (row) => (
        <div>
          <div>{row.warehouseName}</div>
          <div className="hint mono">{row.warehouseCode}</div>
        </div>
      ),
    },
    {
      key: 'location',
      header: t('inventory.field.location'),
      width: '140px',
      render: (row) =>
        row.locationCode ? (
          <span className="mono hint">
            {row.locationCode} · {row.locationName}
          </span>
        ) : (
          <span className="hint">{t('common.none')}</span>
        ),
    },
    {
      key: 'lot',
      header: t('inventory.field.lot'),
      width: '110px',
      render: (row) =>
        row.lotNo ? <span className="mono">{row.lotNo}</span> : <span className="hint">—</span>,
    },
    {
      key: 'onHand',
      header: t('inventory.field.onHand'),
      align: 'right',
      width: '100px',
      render: (row) => (
        <span className="mono">
          {formatQty(row.onHand)} {row.uomCode}
        </span>
      ),
    },
    {
      key: 'available',
      header: t('inventory.field.available'),
      align: 'right',
      width: '100px',
      render: (row) => (
        <span className="mono">
          {formatQty(row.available)} {row.uomCode}
        </span>
      ),
    },
    {
      key: 'avgCost',
      header: t('inventory.field.avgCost'),
      align: 'right',
      width: '90px',
      render: (row) => <span className="mono">{formatMoney(row.avgCost)}</span>,
    },
    {
      key: 'amount',
      header: t('inventory.field.totalAmount'),
      align: 'right',
      width: '110px',
      render: (row) => <span className="mono">{formatMoney(row.totalAmount)}</span>,
    },
  ]

  const result = stockQuery.data

  return (
    <>
      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('inventory.searchPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
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
        <label className="check">
          <input
            type="checkbox"
            checked={includeEmpty}
            onChange={(event) => {
              setIncludeEmpty(event.target.checked)
              setPage(0)
            }}
          />
          {t('inventory.field.includeEmpty')}
        </label>
        <div className="toolbar__spacer" />
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(row) => row.id}
        loading={stockQuery.isLoading}
        error={stockQuery.error instanceof ApiError ? stockQuery.error.message : null}
        emptyTitle={t('inventory.emptyStock')}
        emptyBody={t('inventory.emptyStockBody')}
        onRowClick={(row) => setSummaryItem(row)}
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
        open={summaryItem !== null}
        title={t('inventory.summary.title')}
        subtitle={
          summaryItem ? `${summaryItem.itemCode} · ${summaryItem.itemName}` : ''
        }
        onClose={() => setSummaryItem(null)}
      >
        {summaryQuery.isLoading ? <p className="hint">{t('common.loading')}</p> : null}
        {summaryQuery.data ? (
          <div>
            <div className="form-grid form-grid--3" style={{ marginBottom: 18 }}>
              <div className="field">
                <span className="hint">{t('inventory.field.onHand')}</span>
                <div className="mono" style={{ fontSize: 18 }}>
                  {formatQty(summaryQuery.data.onHand)} {summaryQuery.data.uomCode}
                </div>
              </div>
              <div className="field">
                <span className="hint">{t('inventory.field.available')}</span>
                <div className="mono" style={{ fontSize: 18 }}>
                  {formatQty(summaryQuery.data.available)} {summaryQuery.data.uomCode}
                </div>
              </div>
              <div className="field">
                <span className="hint">{t('inventory.field.totalAmount')}</span>
                <div className="mono" style={{ fontSize: 18 }}>
                  {formatMoney(summaryQuery.data.totalAmount)}
                </div>
              </div>
            </div>

            <div className="line-list">
              {summaryQuery.data.lines.map((line) => (
                <div className="line-list__row" key={line.id}>
                  <span style={{ flex: 1 }}>
                    {line.warehouseName}
                    {line.locationCode ? ` · ${line.locationCode}` : ''}
                  </span>
                  {line.lotNo ? <span className="mono hint">{line.lotNo}</span> : null}
                  <span className="mono">{formatQty(line.onHand)}</span>
                  <span className="mono hint">{formatMoney(line.totalAmount)}</span>
                </div>
              ))}
            </div>
          </div>
        ) : null}
        {summaryQuery.error instanceof ApiError ? (
          <div className="form-error">{summaryQuery.error.message}</div>
        ) : null}
      </Drawer>
    </>
  )
}

