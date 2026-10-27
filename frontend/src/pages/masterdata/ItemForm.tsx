import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import Lookup, { type LookupOption } from '../../components/Lookup'
import { ApiError } from '../../shared/api/client'
import {
  createItem,
  partnerOptions,
  updateItem,
  type CategoryView,
  type ItemRequest,
  type ItemType,
  type ItemView,
  type PartnerOption,
  type RecordStatus,
  type UomView,
} from '../../shared/api/masterdata'

interface ItemFormProps {
  initial: ItemView | null
  categories: CategoryView[]
  uoms: UomView[]
  onSaved: (item: ItemView) => void
  onCancel: () => void
}

const TYPES: ItemType[] = ['RAW', 'SEMI', 'FINISHED', 'CONSUMABLE', 'SERVICE']

function blank(): ItemRequest {
  return {
    code: '',
    name: '',
    spec: null,
    categoryId: null,
    itemType: 'RAW',
    uomCode: 'PCS',
    purchaseUomCode: null,
    purchaseFactor: 1,
    purchased: true,
    purchaseLeadTimeDays: 0,
    minOrderQty: 0,
    safetyStock: 0,
    defaultSupplierId: null,
    sold: false,
    salePrice: null,
    manufactured: false,
    lotControlled: false,
    serialControlled: false,
    shelfLifeDays: null,
    standardCost: 0,
    currency: 'CNY',
    taxRate: 13,
    remark: null,
    status: 'ACTIVE',
  }
}

function fromView(item: ItemView): ItemRequest {
  return {
    code: item.code,
    name: item.name,
    spec: item.spec,
    categoryId: item.categoryId,
    itemType: item.itemType,
    uomCode: item.uomCode,
    purchaseUomCode: item.purchaseUomCode,
    purchaseFactor: item.purchaseFactor,
    purchased: item.purchased,
    purchaseLeadTimeDays: item.purchaseLeadTimeDays,
    minOrderQty: item.minOrderQty,
    safetyStock: item.safetyStock,
    defaultSupplierId: item.defaultSupplierId,
    sold: item.sold,
    salePrice: item.salePrice,
    manufactured: item.manufactured,
    lotControlled: item.lotControlled,
    serialControlled: item.serialControlled,
    shelfLifeDays: item.shelfLifeDays,
    standardCost: item.standardCost,
    currency: item.currency,
    taxRate: item.taxRate,
    remark: item.remark,
    status: item.status,
  }
}

export default function ItemForm({ initial, categories, uoms, onSaved, onCancel }: ItemFormProps) {
  const { t } = useTranslation()
  const [form, setForm] = useState<ItemRequest>(initial ? fromView(initial) : blank())
  const [supplier, setSupplier] = useState<LookupOption | null>(
    initial && initial.defaultSupplierId
      ? { id: initial.defaultSupplierId, code: '', name: initial.defaultSupplierName ?? '' }
      : null,
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const patch = (changes: Partial<ItemRequest>) => setForm((current) => ({ ...current, ...changes }))

  const activeCategories = useMemo(
    () => categories.filter((category) => category.status === 'ACTIVE' || category.id === form.categoryId),
    [categories, form.categoryId],
  )

  const searchSuppliers = async (query: string): Promise<LookupOption[]> => {
    const result: PartnerOption[] = await partnerOptions(query, 'SUPPLIER', 20)
    return result.map((option) => ({ id: option.id, code: option.code, name: option.name }))
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload: ItemRequest = {
        ...form,
        defaultSupplierId: supplier?.id ?? null,
      }
      const saved = initial ? await updateItem(initial.id, payload) : await createItem(payload)
      onSaved(saved)
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} id="item-form">
      {error ? <div className="form-error">{error}</div> : null}

      <div className="form-section">{t('item.section.basic')}</div>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="item-code">{t('item.field.code')}</label>
          <input
            id="item-code"
            className="input"
            value={form.code}
            required
            maxLength={64}
            placeholder="RAW-300"
            onChange={(event) => patch({ code: event.target.value.toUpperCase() })}
          />
          <span className="hint">{t('item.hint.code')}</span>
        </div>
        <div className="field">
          <label htmlFor="item-name">{t('item.field.name')}</label>
          <input
            id="item-name"
            className="input"
            value={form.name}
            required
            maxLength={200}
            onChange={(event) => patch({ name: event.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="item-spec">{t('item.field.spec')}</label>
          <input
            id="item-spec"
            className="input"
            value={form.spec ?? ''}
            maxLength={255}
            onChange={(event) => patch({ spec: event.target.value || null })}
          />
        </div>
        <div className="field">
          <label htmlFor="item-category">{t('item.field.category')}</label>
          <select
            id="item-category"
            className="select"
            value={form.categoryId ?? ''}
            onChange={(event) =>
              patch({ categoryId: event.target.value ? Number(event.target.value) : null })
            }
          >
            <option value="">{t('common.none')}</option>
            {activeCategories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.code} · {category.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="item-type">{t('item.field.itemType')}</label>
          <select
            id="item-type"
            className="select"
            value={form.itemType}
            onChange={(event) => patch({ itemType: event.target.value as ItemType })}
          >
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`itemType.${type}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="item-uom">{t('item.field.uom')}</label>
          <select
            id="item-uom"
            className="select"
            value={form.uomCode}
            onChange={(event) => patch({ uomCode: event.target.value })}
          >
            {uoms.map((uom) => (
              <option key={uom.code} value={uom.code}>
                {uom.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="form-section">{t('item.section.purchasing')}</div>
      <div className="form-grid form-grid--3">
        <label className="check">
          <input
            type="checkbox"
            checked={form.purchased}
            onChange={(event) =>
              patch({
                purchased: event.target.checked,
                defaultSupplierId: event.target.checked ? form.defaultSupplierId : null,
              })
            }
          />
          {t('item.field.purchased')}
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={form.sold}
            onChange={(event) => patch({ sold: event.target.checked })}
          />
          {t('item.field.sold')}
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={form.manufactured}
            onChange={(event) => patch({ manufactured: event.target.checked })}
            disabled={form.itemType === 'SERVICE'}
          />
          {t('item.field.manufactured')}
        </label>

        <div className="field">
          <label htmlFor="item-lead">{t('item.field.leadTime')}</label>
          <input
            id="item-lead"
            className="input input--num"
            type="number"
            min={0}
            value={form.purchaseLeadTimeDays}
            onChange={(event) => patch({ purchaseLeadTimeDays: Number(event.target.value) || 0 })}
          />
        </div>
        <div className="field">
          <label htmlFor="item-moq">{t('item.field.minOrderQty')}</label>
          <input
            id="item-moq"
            className="input input--num"
            type="number"
            min={0}
            step="0.001"
            value={form.minOrderQty}
            onChange={(event) => patch({ minOrderQty: Number(event.target.value) || 0 })}
          />
        </div>
        <div className="field">
          <label htmlFor="item-safety">{t('item.field.safetyStock')}</label>
          <input
            id="item-safety"
            className="input input--num"
            type="number"
            min={0}
            step="0.001"
            value={form.safetyStock}
            onChange={(event) => patch({ safetyStock: Number(event.target.value) || 0 })}
          />
        </div>

        <div className="field span-2">
          <label htmlFor="item-supplier">{t('item.field.defaultSupplier')}</label>
          <Lookup
            id="item-supplier"
            value={supplier}
            onChange={(option) => {
              setSupplier(option)
              patch({ defaultSupplierId: option?.id ?? null })
            }}
            search={searchSuppliers}
            disabled={!form.purchased}
            placeholder={
              form.purchased ? t('item.placeholder.supplier') : t('item.hint.notPurchased')
            }
          />
        </div>
        <div className="field">
          <label htmlFor="item-purchase-uom">{t('item.field.purchaseUom')}</label>
          <select
            id="item-purchase-uom"
            className="select"
            value={form.purchaseUomCode ?? ''}
            onChange={(event) => patch({ purchaseUomCode: event.target.value || null })}
          >
            <option value="">{t('item.field.purchaseUomSame')}</option>
            {uoms.map((uom) => (
              <option key={uom.code} value={uom.code}>
                {uom.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="item-factor">{t('item.field.purchaseFactor')}</label>
          <input
            id="item-factor"
            className="input input--num"
            type="number"
            min={0.000001}
            step="0.000001"
            value={form.purchaseFactor}
            onChange={(event) => patch({ purchaseFactor: Number(event.target.value) || 1 })}
          />
          <span className="hint">{t('item.hint.purchaseFactor')}</span>
        </div>
      </div>

      <div className="form-section">{t('item.section.manufacturing')}</div>
      <div className="form-grid form-grid--3">
        <label className="check">
          <input
            type="checkbox"
            checked={form.lotControlled}
            onChange={(event) => patch({ lotControlled: event.target.checked })}
            disabled={form.itemType === 'SERVICE'}
          />
          {t('item.field.lotControlled')}
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={form.serialControlled}
            onChange={(event) => patch({ serialControlled: event.target.checked })}
            disabled={form.itemType === 'SERVICE'}
          />
          {t('item.field.serialControlled')}
        </label>
        <div className="field">
          <label htmlFor="item-shelf">{t('item.field.shelfLife')}</label>
          <input
            id="item-shelf"
            className="input input--num"
            type="number"
            min={0}
            value={form.shelfLifeDays ?? ''}
            onChange={(event) =>
              patch({ shelfLifeDays: event.target.value ? Number(event.target.value) : null })
            }
          />
        </div>
      </div>

      <div className="form-section">{t('item.section.costing')}</div>
      <div className="form-grid form-grid--3">
        <div className="field">
          <label htmlFor="item-cost">{t('item.field.standardCost')}</label>
          <input
            id="item-cost"
            className="input input--num"
            type="number"
            min={0}
            step="0.000001"
            value={form.standardCost}
            onChange={(event) => patch({ standardCost: Number(event.target.value) || 0 })}
          />
          <span className="hint">{t('item.hint.standardCost')}</span>
        </div>
        <div className="field">
          <label htmlFor="item-price">{t('item.field.salePrice')}</label>
          <input
            id="item-price"
            className="input input--num"
            type="number"
            min={0}
            step="0.0001"
            value={form.salePrice ?? ''}
            onChange={(event) =>
              patch({ salePrice: event.target.value ? Number(event.target.value) : null })
            }
            disabled={!form.sold}
          />
        </div>
        <div className="field">
          <label htmlFor="item-tax">{t('item.field.taxRate')}</label>
          <input
            id="item-tax"
            className="input input--num"
            type="number"
            min={0}
            max={100}
            step="0.01"
            value={form.taxRate}
            onChange={(event) => patch({ taxRate: Number(event.target.value) || 0 })}
          />
        </div>
        <div className="field">
          <label htmlFor="item-currency">{t('item.field.currency')}</label>
          <select
            id="item-currency"
            className="select"
            value={form.currency}
            onChange={(event) => patch({ currency: event.target.value })}
          >
            {['CNY', 'USD', 'EUR', 'JPY'].map((code) => (
              <option key={code} value={code}>
                {code}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="item-status">{t('common.status')}</label>
          <select
            id="item-status"
            className="select"
            value={form.status ?? 'ACTIVE'}
            onChange={(event) => patch({ status: event.target.value as RecordStatus })}
          >
            <option value="ACTIVE">{t('status.record.ACTIVE')}</option>
            <option value="INACTIVE">{t('status.record.INACTIVE')}</option>
          </select>
          <span className="hint">{t('item.hint.status')}</span>
        </div>
      </div>

      <div className="form-section">{t('common.remark')}</div>
      <div className="field">
        <label htmlFor="item-remark">{t('common.remark')}</label>
        <textarea
          id="item-remark"
          className="input"
          value={form.remark ?? ''}
          maxLength={500}
          onChange={(event) => patch({ remark: event.target.value || null })}
        />
      </div>

      <div className="line-actions" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn btn--ghost btn--sm" onClick={onCancel} disabled={saving}>
          {t('common.cancel')}
        </button>
        <button type="submit" className="btn btn--primary btn--sm" disabled={saving}>
          {saving ? t('common.saving') : t('common.save')}
        </button>
      </div>    </form>
  )
}
