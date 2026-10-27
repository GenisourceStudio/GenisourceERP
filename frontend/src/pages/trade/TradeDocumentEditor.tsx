import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import Lookup, { type LookupOption } from '../../components/Lookup'
import { ApiError } from '../../shared/api/client'
import { itemOptions, partnerOptions, type ItemOption } from '../../shared/api/masterdata'
import { formatMoney, formatQty } from '../../shared/format'
import type { TradeDocument, TradeDocumentRequest, TradeLineRequest } from './types'

interface LineDraft {
  itemId: number | null
  itemLabel: string
  qty: string
  unitPrice: string
  taxRate: string
  remark: string
}

interface EditorProps {
  initial: TradeDocument | null
  config: {
    partnerRole: 'SUPPLIER' | 'CUSTOMER'
    needsPartner: boolean
    needsWarehouse: boolean
  }
  warehouses: { id: number; code: string; name: string }[]
  submit: (request: TradeDocumentRequest) => Promise<TradeDocument>
  onSaved: (document: TradeDocument) => void
  onCancel: () => void
}

function blankLine(): LineDraft {
  return { itemId: null, itemLabel: '', qty: '', unitPrice: '', taxRate: '', remark: '' }
}

function toDraft(line: TradeDocument['lines'][number]): LineDraft {
  return {
    itemId: line.itemId,
    itemLabel: line.itemCode ?? '',
    qty: String(line.qty),
    unitPrice: String(line.unitPrice),
    taxRate: String(line.taxRate),
    remark: line.remark ?? '',
  }
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * The header-and-lines form shared by all six trade documents.
 *
 * <p>The price is typed ex tax with the rate next to it, and the form shows what the line
 * comes to as you type. That is not decoration: the gross is what the customer is
 * invoiced and the net is what the accounts book, and seeing both before saving is the
 * only way a clerk notices a rate entered as 13 instead of 0.13.
 */
export default function TradeDocumentEditor({
  initial,
  config,
  warehouses,
  submit,
  onSaved,
  onCancel,
}: EditorProps) {
  const { t } = useTranslation()
  const [partner, setPartner] = useState<LookupOption | null>(
    initial?.partnerId
      ? { id: initial.partnerId, code: initial.partnerCode ?? '', name: initial.partnerName ?? '' }
      : null,
  )
  const [warehouseId, setWarehouseId] = useState<number | ''>(initial?.warehouseId ?? '')
  const [bizDate, setBizDate] = useState(initial?.bizDate ?? today())
  const [requiredDate, setRequiredDate] = useState(initial?.requiredDate ?? '')
  const [paymentTermDays, setPaymentTermDays] = useState(
    initial ? String(initial.paymentTermDays) : '',
  )
  const [remark, setRemark] = useState(initial?.remark ?? '')
  const [lines, setLines] = useState<LineDraft[]>(
    initial && initial.lines.length > 0 ? initial.lines.map(toDraft) : [blankLine()],
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const searchItems = async (query: string): Promise<LookupOption[]> => {
    const result: ItemOption[] = await itemOptions(query, 20)
    return result.map((option) => ({ id: option.id, code: option.code, name: option.name }))
  }

  const searchPartners = async (query: string): Promise<LookupOption[]> =>
    partnerOptions(query, config.partnerRole, 20).then((result) =>
      result.map((option) => ({ id: option.id, code: option.code, name: option.name })),
    )

  const patchLine = (index: number, changes: Partial<LineDraft>) =>
    setLines((current) =>
      current.map((line, position) => (position === index ? { ...line, ...changes } : line)),
    )

  const totals = useMemo(() => {
    let net = 0
    let tax = 0
    let qty = 0
    for (const line of lines) {
      const quantity = Number(line.qty || 0)
      const price = Number(line.unitPrice || 0)
      const rate = Number(line.taxRate || 0)
      if (!Number.isFinite(quantity) || !Number.isFinite(price)) {
        continue
      }
      const lineNet = quantity * price
      qty += quantity
      net += lineNet
      tax += Number.isFinite(rate) ? lineNet * rate : 0
    }
    return { qty, net, tax, total: net + tax }
  }, [lines])

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    const payloadLines: TradeLineRequest[] = lines
      .filter((line) => line.itemId !== null)
      .map((line) => ({
        itemId: line.itemId as number,
        qty: Number(line.qty || 0),
        unitPrice: line.unitPrice === '' ? null : Number(line.unitPrice),
        taxRate: line.taxRate === '' ? null : Number(line.taxRate),
        remark: line.remark || null,
      }))
    if (payloadLines.length === 0) {
      setError(t('trade.error.linesRequired'))
      return
    }
    if (config.needsPartner && partner === null) {
      setError(t('trade.error.partnerRequired'))
      return
    }
    if (config.needsWarehouse && warehouseId === '') {
      setError(t('trade.error.warehouseRequired'))
      return
    }

    setSaving(true)
    setError(null)
    try {
      const payload: TradeDocumentRequest = {
        partnerId: partner?.id ?? null,
        warehouseId: warehouseId === '' ? null : warehouseId,
        bizDate,
        requiredDate: requiredDate || null,
        paymentTermDays: paymentTermDays === '' ? null : Number(paymentTermDays),
        remark: remark || null,
        lines: payloadLines,
      }
      onSaved(await submit(payload))
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={save}>
      {error ? <div className="form-error">{error}</div> : null}

      <div className="form-section">{t('trade.section.header')}</div>
      <div className="form-grid form-grid--3">
        {config.needsPartner ? (
          <div className="field">
            <label>{t('trade.field.partner')}</label>
            <Lookup
              value={partner}
              onChange={setPartner}
              search={searchPartners}
              placeholder={t('trade.field.partner')}
            />
          </div>
        ) : null}
        {config.needsWarehouse ? (
          <div className="field">
            <label htmlFor="trade-warehouse">{t('trade.field.warehouse')}</label>
            <select
              id="trade-warehouse"
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
        ) : null}
        <div className="field">
          <label htmlFor="trade-biz-date">{t('trade.field.bizDate')}</label>
          <input
            id="trade-biz-date"
            className="input"
            type="date"
            value={bizDate}
            required
            onChange={(event) => setBizDate(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="trade-required-date">{t('trade.field.requiredDate')}</label>
          <input
            id="trade-required-date"
            className="input"
            type="date"
            value={requiredDate}
            onChange={(event) => setRequiredDate(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="trade-terms">{t('trade.field.paymentTermDays')}</label>
          <input
            id="trade-terms"
            className="input input--num"
            type="number"
            min={0}
            step={1}
            placeholder="0"
            value={paymentTermDays}
            onChange={(event) => setPaymentTermDays(event.target.value)}
          />
        </div>
        <div className="field span-2">
          <label htmlFor="trade-remark">{t('trade.field.remark')}</label>
          <input
            id="trade-remark"
            className="input"
            value={remark}
            maxLength={500}
            onChange={(event) => setRemark(event.target.value)}
          />
        </div>
      </div>

      <div className="form-section">{t('trade.section.lines')}</div>
      <div className="doc-line" style={{ marginBottom: 14 }}>
        <div className="doc-line__head doc-line__head--trade">
          <span>{t('trade.field.item')}</span>
          <span>{t('trade.field.qty')}</span>
          <span>{t('trade.field.unitPrice')}</span>
          <span>{t('trade.field.taxRate')}</span>
          <span>{t('trade.field.remark')}</span>
          <span />
        </div>

        {lines.map((line, index) => (
          <div className="doc-line__row doc-line__row--trade" key={index}>
            <Lookup
              value={line.itemId === null ? null : { id: line.itemId, code: line.itemLabel, name: '' }}
              onChange={(option) =>
                patchLine(index, { itemId: option?.id ?? null, itemLabel: option?.code ?? '' })
              }
              search={searchItems}
              placeholder={t('trade.field.item')}
            />
            <input
              className="input input--num"
              type="number"
              min={0}
              step="any"
              value={line.qty}
              onChange={(event) => patchLine(index, { qty: event.target.value })}
            />
            <input
              className="input input--num"
              type="number"
              min={0}
              step="any"
              value={line.unitPrice}
              onChange={(event) => patchLine(index, { unitPrice: event.target.value })}
            />
            <input
              className="input input--num"
              type="number"
              min={0}
              step="any"
              placeholder="0.13"
              value={line.taxRate}
              onChange={(event) => patchLine(index, { taxRate: event.target.value })}
            />
            <input
              className="input"
              value={line.remark}
              maxLength={255}
              onChange={(event) => patchLine(index, { remark: event.target.value })}
            />
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => setLines((current) => current.filter((_, at) => at !== index))}
              disabled={lines.length === 1}
            >
              {t('common.remove')}
            </button>
          </div>
        ))}
      </div>

      <div className="row gap-8" style={{ justifyContent: 'space-between' }}>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => setLines((current) => [...current, blankLine()])}
        >
          {t('trade.action.addLine')}
        </button>
        <span className="hint mono">
          {t('trade.field.qty')}: {formatQty(totals.qty)} · {t('trade.field.netAmount')}:{' '}
          {formatMoney(totals.net)} · {t('trade.field.totalAmount')}: {formatMoney(totals.total)}
        </span>
      </div>

      <div className="row gap-8" style={{ justifyContent: 'flex-end', marginTop: 18 }}>
        <button type="button" className="btn btn--ghost" onClick={onCancel}>
          {t('common.cancel')}
        </button>
        <button type="submit" className="btn btn--primary" disabled={saving}>
          {saving ? t('common.saving') : t('common.save')}
        </button>
      </div>
    </form>
  )
}
