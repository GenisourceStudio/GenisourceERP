import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { ApiError } from '../../shared/api/client'
import {
  createWarehouse,
  updateWarehouse,
  type WarehouseRequest,
  type WarehouseType,
  type WarehouseView,
} from '../../shared/api/inventory'

const TYPES: WarehouseType[] = ['RAW', 'SEMI', 'FINISHED', 'GENERAL']

interface WarehouseFormProps {
  initial: WarehouseView | null
  onSaved: (warehouse: WarehouseView) => void
  onCancel: () => void
}

function blank(): WarehouseRequest {
  return {
    code: '',
    name: '',
    warehouseType: 'GENERAL',
    address: null,
    remark: null,
    status: 'ACTIVE',
  }
}

export default function WarehouseForm({ initial, onSaved, onCancel }: WarehouseFormProps) {
  const { t } = useTranslation()
  const [form, setForm] = useState<WarehouseRequest>(
    initial
      ? {
          code: initial.code,
          name: initial.name,
          warehouseType: initial.warehouseType,
          address: initial.address,
          remark: initial.remark,
          status: initial.status,
        }
      : blank(),
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const patch = (changes: Partial<WarehouseRequest>) =>
    setForm((current) => ({ ...current, ...changes }))

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const saved = initial
        ? await updateWarehouse(initial.id, form)
        : await createWarehouse(form)
      onSaved(saved)
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} id="warehouse-form">
      {error ? <div className="form-error">{error}</div> : null}

      <div className="form-grid">
        <div className="field">
          <label htmlFor="warehouse-code">{t('inventory.field.code')}</label>
          <input
            id="warehouse-code"
            className="input"
            value={form.code}
            required
            maxLength={32}
            placeholder="WH-RAW"
            onChange={(event) => patch({ code: event.target.value.toUpperCase() })}
          />
          <span className="hint">{t('inventory.hint.code')}</span>
        </div>
        <div className="field">
          <label htmlFor="warehouse-name">{t('inventory.field.name')}</label>
          <input
            id="warehouse-name"
            className="input"
            value={form.name}
            required
            maxLength={128}
            onChange={(event) => patch({ name: event.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="warehouse-type">{t('inventory.field.warehouseType')}</label>
          <select
            id="warehouse-type"
            className="select"
            value={form.warehouseType}
            onChange={(event) => patch({ warehouseType: event.target.value as WarehouseType })}
          >
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`warehouseType.${type}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="field span-2">
          <label htmlFor="warehouse-address">{t('inventory.field.address')}</label>
          <input
            id="warehouse-address"
            className="input"
            value={form.address ?? ''}
            maxLength={255}
            onChange={(event) => patch({ address: event.target.value || null })}
          />
        </div>
        <div className="field span-2">
          <label htmlFor="warehouse-remark">{t('inventory.field.remark')}</label>
          <textarea
            id="warehouse-remark"
            className="input"
            rows={2}
            value={form.remark ?? ''}
            maxLength={500}
            onChange={(event) => patch({ remark: event.target.value || null })}
          />
        </div>
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
