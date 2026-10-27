import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import Lookup, { type LookupOption } from '../../components/Lookup'
import { ApiError } from '../../shared/api/client'
import {
  createDocument,
  updateDocument,
  type DocumentLineRequest,
  type DocumentRequest,
  type DocumentType,
  type DocumentView,
} from '../../shared/api/inventory'
import { itemOptions, type ItemOption } from '../../shared/api/masterdata'

interface LineDraft {
  itemId: number | null
  itemLabel: string
  lotId: number | null
  lotLabel: string
  qty: string
  unitCost: string
  countedQty: string
  remark: string
}

interface DocumentEditorProps {
  initial: DocumentView | null
  warehouses: { id: number; code: string; name: string }[]
  onSaved: (document: DocumentView) => void
  onCancel: () => void
}

const TYPES: DocumentType[] = ['INBOUND', 'OUTBOUND', 'TRANSFER', 'COUNT']

function blankLine(): LineDraft {
  return {
    itemId: null,
    itemLabel: '',
    lotId: null,
    lotLabel: '',
    qty: '',
    unitCost: '',
    countedQty: '',
    remark: '',
  }
}

function toDraft(line: DocumentView['lines'][number]): LineDraft {
  return {
    itemId: line.itemId,
    itemLabel: line.itemCode ?? '',
    lotId: line.lotId,
    lotLabel: line.lotNo ?? '',
    qty: String(line.qty),
    unitCost: String(line.unitCost),
    countedQty: line.countedQty === null ? '' : String(line.countedQty),
    remark: line.remark ?? '',
  }
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * The document editor.
 *
 * <p>One form for four document types because they are one envelope: a warehouse, some
 * lines and a date. The type decides what a line means — a receipt states a cost, an
 * issue takes the average, a transfer needs a destination and a count compares the
 * shelf with the book — so the form hides the fields that do not apply instead of
 * asking the clerk to fill in fields that are ignored.
 */
export default function DocumentEditor({
  initial,
  warehouses,
  onSaved,
  onCancel,
}: DocumentEditorProps) {
  const { t } = useTranslation()
  const [docType, setDocType] = useState<DocumentType>(initial?.docType ?? 'INBOUND')
  const [warehouseId, setWarehouseId] = useState<number | ''>(initial?.warehouseId ?? '')
  const [toWarehouseId, setToWarehouseId] = useState<number | ''>(initial?.toWarehouseId ?? '')
  const [bizDate, setBizDate] = useState(initial?.bizDate ?? today())
  const [remark, setRemark] = useState(initial?.remark ?? '')
  const [lines, setLines] = useState<LineDraft[]>(
    initial && initial.lines.length > 0 ? initial.lines.map(toDraft) : [blankLine()],
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isCount = docType === 'COUNT'
  const isTransfer = docType === 'TRANSFER'
  const isInbound = docType === 'INBOUND'

  const searchItems = async (query: string): Promise<LookupOption[]> => {
    const result: ItemOption[] = await itemOptions(query, 20)
    return result.map((option) => ({ id: option.id, code: option.code, name: option.name }))
  }

  const patchLine = (index: number, changes: Partial<LineDraft>) =>
    setLines((current) =>
      current.map((line, position) => (position === index ? { ...line, ...changes } : line)),
    )

  const removeLine = (index: number) =>
    setLines((current) => current.filter((_, position) => position !== index))

  const addLine = () => setLines((current) => [...current, blankLine()])

  const totalQty = useMemo(
    () =>
      lines.reduce((sum, line) => {
        const value = isCount ? Number(line.countedQty || 0) : Number(line.qty || 0)
        return sum + (Number.isFinite(value) ? value : 0)
      }, 0),
    [lines, isCount],
  )

  const totalAmount = useMemo(
    () =>
      lines.reduce((sum, line) => {
        const qty = Number(line.qty || 0)
        const cost = Number(line.unitCost || 0)
        return sum + (Number.isFinite(qty) && Number.isFinite(cost) ? qty * cost : 0)
      }, 0),
    [lines],
  )

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (warehouseId === '') {
      setError(t('inventory.error.warehouseRequired'))
      return
    }
    const payloadLines: DocumentLineRequest[] = lines
      .filter((line) => line.itemId !== null)
      .map((line) => ({
        itemId: line.itemId as number,
        lotId: line.lotId,
        qty: Number(line.qty || 0),
        unitCost: isInbound ? Number(line.unitCost || 0) : line.unitCost ? Number(line.unitCost) : null,
        countedQty: isCount && line.countedQty !== '' ? Number(line.countedQty) : null,
        remark: line.remark || null,
      }))
    if (payloadLines.length === 0) {
      setError(t('inventory.error.linesRequired'))
      return
    }

    setSaving(true)
    setError(null)
    try {
      const payload: DocumentRequest = {
        docType,
        warehouseId: warehouseId as number,
        toWarehouseId: isTransfer ? (toWarehouseId as number) : null,
        bizDate,
        remark: remark || null,
        lines: payloadLines,
      }
      const saved = initial
        ? await updateDocument(initial.id, payload)
        : await createDocument(payload)
      onSaved(saved)
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} id="document-form">
      {error ? <div className="form-error">{error}</div> : null}

      <div className="form-section">{t('inventory.section.header')}</div>
      <div className="form-grid form-grid--3">
        <div className="field">
          <label htmlFor="doc-type">{t('inventory.field.docType')}</label>
          <select
            id="doc-type"
            className="select"
            value={docType}
            disabled={initial !== null}
            onChange={(event) => setDocType(event.target.value as DocumentType)}
          >
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`docType.${type}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="doc-warehouse">{t('inventory.field.warehouse')}</label>
          <select
            id="doc-warehouse"
            className="select"
            value={warehouseId}
            required
            onChange={(event) => setWarehouseId(event.target.value === '' ? '' : Number(event.target.value))}
          >
            <option value="">{t('common.pleaseSelect')}</option>
            {warehouses.map((warehouse) => (
              <option key={warehouse.id} value={warehouse.id}>
                {warehouse.code} · {warehouse.name}
              </option>
            ))}
          </select>
        </div>
        {isTransfer ? (
          <div className="field">
            <label htmlFor="doc-to-warehouse">{t('inventory.field.toWarehouse')}</label>
            <select
              id="doc-to-warehouse"
              className="select"
              value={toWarehouseId}
              required
              onChange={(event) =>
                setToWarehouseId(event.target.value === '' ? '' : Number(event.target.value))
              }
            >
              <option value="">{t('common.pleaseSelect')}</option>
              {warehouses
                .filter((warehouse) => warehouse.id !== warehouseId)
                .map((warehouse) => (
                  <option key={warehouse.id} value={warehouse.id}>
                    {warehouse.code} · {warehouse.name}
                  </option>
                ))}
            </select>
          </div>
        ) : null}
        <div className="field">
          <label htmlFor="doc-date">{t('inventory.field.bizDate')}</label>
          <input
            id="doc-date"
            className="input"
            type="date"
            value={bizDate}
            required
            onChange={(event) => setBizDate(event.target.value)}
          />
        </div>
        <div className="field span-2">
          <label htmlFor="doc-remark">{t('inventory.field.remark')}</label>
          <input
            id="doc-remark"
            className="input"
            value={remark}
            maxLength={500}
            onChange={(event) => setRemark(event.target.value)}
          />
        </div>
      </div>

      <div className="form-section">{t('inventory.section.lines')}</div>
      <div className="doc-line" style={{ marginBottom: 14 }}>
        <div className={isCount ? 'doc-line__head doc-line__head--count' : 'doc-line__head'}>
          <span>{t('inventory.field.item')}</span>
          <span>{t('inventory.field.qty')}</span>
          <span>{isInbound ? t('inventory.field.unitCost') : ''}</span>
          {isCount ? <span>{t('inventory.field.countedQty')}</span> : null}
          <span />
        </div>

        {lines.map((line, index) => (
          <div
            className={isCount ? 'doc-line__row doc-line__row--count' : 'doc-line__row'}
            key={index}
          >
            <Lookup
              value={
                line.itemId === null
                  ? null
                  : { id: line.itemId, code: line.itemLabel, name: '' }
              }
              onChange={(option) =>
                patchLine(index, {
                  itemId: option?.id ?? null,
                  itemLabel: option?.code ?? '',
                  lotId: null,
                  lotLabel: '',
                })
              }
              search={searchItems}
              placeholder={t('inventory.field.item')}
            />
            <input
              className="input input--num"
              type="number"
              min={0}
              step="any"
              value={line.qty}
              onChange={(event) => patchLine(index, { qty: event.target.value })}
            />
            {isInbound ? (
              <input
                className="input input--num"
                type="number"
                min={0}
                step="any"
                value={line.unitCost}
                onChange={(event) => patchLine(index, { unitCost: event.target.value })}
              />
            ) : (
              <span className="hint">—</span>
            )}
            {isCount ? (
              <input
                className="input input--num"
                type="number"
                min={0}
                step="any"
                value={line.countedQty}
                onChange={(event) => patchLine(index, { countedQty: event.target.value })}
              />
            ) : null}
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => removeLine(index)}
              disabled={lines.length === 1}
            >
              {t('common.remove')}
            </button>
          </div>
        ))}
      </div>

      <div className="row gap-8" style={{ justifyContent: 'space-between' }}>
        <button type="button" className="btn btn--ghost btn--sm" onClick={addLine}>
          {t('inventory.action.addLine')}
        </button>
        <span className="hint mono">
          {t('inventory.field.totalQty')}: {totalQty} · {t('inventory.field.totalAmount')}:{' '}
          {totalAmount.toFixed(2)}
        </span>
      </div>

      {isCount ? <p className="hint">{t('inventory.hint.count')}</p> : null}

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
