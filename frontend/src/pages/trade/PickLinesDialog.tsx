import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Drawer } from '../../components/Overlay'
import { formatMoney, formatQty } from '../../shared/format'
import type { PickLine, TradeDocument } from './types'

interface PickLinesDialogProps {
  open: boolean
  title: string
  subtitle?: string
  source: TradeDocument | null
  warehouses: { id: number; code: string; name: string }[]
  /** Goods-moving dialogs ask where and when; a conversion only asks how much. */
  askWarehouse: boolean
  confirmLabel: string
  busy?: boolean
  onSubmit: (request: {
    warehouseId: number
    bizDate: string
    remark: string | null
    lines: PickLine[]
  }) => void
  onCancel: () => void
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * Picking quantities off a source document.
 *
 * <p>Converting a requisition and receiving against an order are the same gesture —
 * choose how much of each line to take — so they share this dialog. Each row starts at
 * the line's open quantity because that is the answer almost every time, and the row
 * shows the ceiling next to the box so taking too much is a visible mistake rather than
 * a 409 from the server.
 */
export default function PickLinesDialog({
  open,
  title,
  subtitle,
  source,
  warehouses,
  askWarehouse,
  confirmLabel,
  busy,
  onSubmit,
  onCancel,
}: PickLinesDialogProps) {
  const { t } = useTranslation()
  const [warehouseId, setWarehouseId] = useState<number | ''>('')
  const [bizDate, setBizDate] = useState(today())
  const [remark, setRemark] = useState('')
  const [takes, setTakes] = useState<Record<number, string>>({})

  const lines = (source?.lines ?? []).filter((line) => Number(line.openQty) > 0)

  const takeOf = (lineId: number, openQty: number): string =>
    takes[lineId] ?? String(openQty)

  const takeValue = (lineId: number, openQty: number): number => {
    const raw = Number(takeOf(lineId, openQty))
    return Number.isFinite(raw) ? raw : 0
  }

  const submit = () => {
    if (!source) {
      return
    }
    const picked: PickLine[] = lines
      .map((line) => ({ lineId: line.id, qty: takeValue(line.id, Number(line.openQty)) }))
      .filter((line) => line.qty > 0)
    if (picked.length === 0) {
      return
    }
    onSubmit({
      warehouseId: warehouseId === '' ? 0 : warehouseId,
      bizDate,
      remark: remark || null,
      lines: picked,
    })
  }

  const total = lines.reduce(
    (sum, line) => sum + takeValue(line.id, Number(line.openQty)) * Number(line.unitPrice),
    0,
  )

  return (
    <Drawer
      open={open}
      title={title}
      subtitle={subtitle ?? source?.docNo}
      onClose={onCancel}
      footer={
        <div className="row gap-8" style={{ justifyContent: 'space-between', width: '100%' }}>
          <span className="hint mono">
            {t('trade.field.qty')}: {formatQty(lines.reduce((sum, line) => sum + takeValue(line.id, Number(line.openQty)), 0))}
            {' · '}
            {t('trade.field.totalAmount')}: {formatMoney(total)}
          </span>
          <div className="row gap-8">
            <button type="button" className="btn btn--ghost btn--sm" onClick={onCancel}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={busy || !source || (askWarehouse && warehouseId === '')}
              onClick={submit}
            >
              {confirmLabel}
            </button>
          </div>
        </div>
      }
    >
      {askWarehouse ? (
        <div className="form-grid form-grid--3" style={{ marginBottom: 18 }}>
          <div className="field">
            <label htmlFor="pick-warehouse">{t('trade.field.warehouse')}</label>
            <select
              id="pick-warehouse"
              className="select"
              value={warehouseId}
              onChange={(event) =>
                setWarehouseId(event.target.value === '' ? '' : Number(event.target.value))
              }
            >
              <option value="">{t('common.pleaseSelect')}</option>
              {warehouses.map((warehouse) => (
                <option key={warehouse.id} value={warehouse.id}>
                  {warehouse.code} · {warehouse.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="pick-date">{t('trade.field.bizDate')}</label>
            <input
              id="pick-date"
              className="input"
              type="date"
              value={bizDate}
              onChange={(event) => setBizDate(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="pick-remark">{t('trade.field.remark')}</label>
            <input
              id="pick-remark"
              className="input"
              value={remark}
              maxLength={500}
              onChange={(event) => setRemark(event.target.value)}
            />
          </div>
        </div>
      ) : null}

      {lines.length === 0 ? (
        <div className="empty">
          <div className="empty__mark">∅</div>
          <p>{t('trade.empty.nothingOpen')}</p>
        </div>
      ) : (
        <div className="line-list">
          <div className="line-list__row line-list__row--head">
            <span style={{ flex: 1 }}>{t('trade.field.item')}</span>
            <span className="hint">{t('trade.field.openQty')}</span>
            <span className="hint">{t('trade.field.qty')}</span>
          </div>
          {lines.map((line) => (
            <div className="line-list__row" key={line.id}>
              <span style={{ flex: 1 }}>
                <strong>{line.itemCode}</strong> {line.itemName}
                <span className="hint"> · {line.uomCode}</span>
              </span>
              <span className="mono hint">{formatQty(Number(line.openQty))}</span>
              <input
                className="input input--num"
                style={{ width: 110 }}
                type="number"
                min={0}
                max={Number(line.openQty)}
                step="any"
                value={takeOf(line.id, Number(line.openQty))}
                onChange={(event) =>
                  setTakes((current) => ({ ...current, [line.id]: event.target.value }))
                }
              />
            </div>
          ))}
        </div>
      )}
    </Drawer>
  )
}
