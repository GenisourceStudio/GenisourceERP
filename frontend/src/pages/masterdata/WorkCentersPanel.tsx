import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import { RecordTag } from '../../components/Tags'
import { ApiError } from '../../shared/api/client'
import { useAuthStore } from '../../shared/auth/store'
import {
  changeWorkCenterStatus,
  createWorkCenter,
  listWorkCenters,
  updateWorkCenter,
  type RecordStatus,
  type WorkCenterRequest,
  type WorkCenterView,
} from '../../shared/api/masterdata'

function blank(): WorkCenterRequest {
  return {
    code: '',
    name: '',
    department: null,
    capacityMinutesPerDay: 480,
    efficiencyPercent: 100,
    costRatePerHour: 0,
    status: 'ACTIVE',
  }
}

export default function WorkCentersPanel() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('masterdata:routing:write') ?? false

  const [editing, setEditing] = useState<WorkCenterView | 'new' | null>(null)
  const [form, setForm] = useState<WorkCenterRequest>(blank())
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deactivating, setDeactivating] = useState<WorkCenterView | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const centersQuery = useQuery({ queryKey: ['work-centers'], queryFn: () => listWorkCenters() })

  const statusMutation = useMutation({
    mutationFn: (center: WorkCenterView) => changeWorkCenterStatus(center.id, 'INACTIVE'),
    onSuccess: () => {
      setDeactivating(null)
      void queryClient.invalidateQueries({ queryKey: ['work-centers'] })
    },
    onError: (error) => {
      setActionError(error instanceof ApiError ? error.firstMessage : t('common.saveFailed'))
      setDeactivating(null)
    },
  })

  const patch = (changes: Partial<WorkCenterRequest>) =>
    setForm((current) => ({ ...current, ...changes }))

  const openEdit = (center: WorkCenterView) => {
    setForm({
      code: center.code,
      name: center.name,
      department: center.department,
      capacityMinutesPerDay: center.capacityMinutesPerDay,
      efficiencyPercent: center.efficiencyPercent,
      costRatePerHour: center.costRatePerHour,
      status: center.status,
    })
    setFormError(null)
    setEditing(center)
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setFormError(null)
    try {
      if (editing && editing !== 'new') {
        await updateWorkCenter(editing.id, form)
      } else {
        await createWorkCenter(form)
      }
      setEditing(null)
      void queryClient.invalidateQueries({ queryKey: ['work-centers'] })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.firstMessage : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  const columns: Column<WorkCenterView>[] = [
    {
      key: 'code',
      header: t('workCenter.field.code'),
      width: '120px',
      render: (center) => <span className="mono" style={{ fontSize: 12 }}>{center.code}</span>,
    },
    {
      key: 'name',
      header: t('workCenter.field.name'),
      render: (center) => (
        <div>
          <div style={{ fontWeight: 600 }}>{center.name}</div>
          {center.department ? <div className="hint">{center.department}</div> : null}
        </div>
      ),
    },
    {
      key: 'capacity',
      header: t('workCenter.field.capacity'),
      align: 'right',
      width: '130px',
      render: (center) => `${center.capacityMinutesPerDay} min`,
    },
    {
      key: 'efficiency',
      header: t('workCenter.field.efficiency'),
      align: 'right',
      width: '90px',
      render: (center) => `${center.efficiencyPercent}%`,
    },
    {
      key: 'available',
      header: t('workCenter.field.available'),
      align: 'right',
      width: '120px',
      render: (center) => `${center.availableMinutesPerDay} min`,
    },
    {
      key: 'rate',
      header: t('workCenter.field.costRate'),
      align: 'right',
      width: '110px',
      render: (center) => center.costRatePerHour.toFixed(2),
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '88px',
      render: (center) => <RecordTag status={center.status} />,
    },
    {
      key: 'actions',
      header: '',
      width: '130px',
      align: 'right',
      render: (center) => (
        <div className="row-actions">
          {canWrite ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                openEdit(center)
              }}
            >
              {t('common.edit')}
            </button>
          ) : null}
          {canWrite && center.status === 'ACTIVE' ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setDeactivating(center)
              }}
            >
              {t('common.deactivate')}
            </button>
          ) : null}
        </div>
      ),
    },
  ]

  return (
    <>
      <div className="toolbar">
        <p className="hint" style={{ margin: 0 }}>
          {t('workCenter.hint.list')}
        </p>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => {
              setForm(blank())
              setFormError(null)
              setEditing('new')
            }}
          >
            {t('workCenter.action.create')}
          </button>
        ) : null}
      </div>

      {actionError ? (
        <div style={{ padding: '10px 14px' }}>
          <div className="form-error">{actionError}</div>
        </div>
      ) : null}

      <DataTable
        columns={columns}
        rows={centersQuery.data ?? []}
        rowKey={(center) => center.id}
        loading={centersQuery.isLoading}
        error={centersQuery.error instanceof ApiError ? centersQuery.error.message : null}
        emptyTitle={t('workCenter.emptyTitle')}
        emptyBody={t('workCenter.emptyBody')}
        onRowClick={canWrite ? openEdit : undefined}
      />

      <Drawer
        open={editing !== null}
        title={editing === 'new' ? t('workCenter.action.create') : t('workCenter.action.edit')}
        subtitle={editing && editing !== 'new' ? editing.code : t('workCenter.hint.create')}
        onClose={() => setEditing(null)}
      >
        <form onSubmit={submit}>
          {formError ? <div className="form-error">{formError}</div> : null}

          <div className="form-grid">
            <div className="field">
              <label htmlFor="wc-code">{t('workCenter.field.code')}</label>
              <input
                id="wc-code"
                className="input"
                value={form.code}
                required
                maxLength={32}
                onChange={(event) => patch({ code: event.target.value.toUpperCase() })}
              />
            </div>
            <div className="field">
              <label htmlFor="wc-name">{t('workCenter.field.name')}</label>
              <input
                id="wc-name"
                className="input"
                value={form.name}
                required
                maxLength={128}
                onChange={(event) => patch({ name: event.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="wc-dept">{t('workCenter.field.department')}</label>
              <input
                id="wc-dept"
                className="input"
                value={form.department ?? ''}
                maxLength={64}
                onChange={(event) => patch({ department: event.target.value || null })}
              />
            </div>
            <div className="field">
              <label htmlFor="wc-status">{t('common.status')}</label>
              <select
                id="wc-status"
                className="select"
                value={form.status ?? 'ACTIVE'}
                onChange={(event) => patch({ status: event.target.value as RecordStatus })}
              >
                <option value="ACTIVE">{t('status.record.ACTIVE')}</option>
                <option value="INACTIVE">{t('status.record.INACTIVE')}</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="wc-capacity">{t('workCenter.field.capacity')}</label>
              <input
                id="wc-capacity"
                className="input input--num"
                type="number"
                min={1}
                value={form.capacityMinutesPerDay}
                onChange={(event) =>
                  patch({ capacityMinutesPerDay: Number(event.target.value) || 1 })
                }
              />
              <span className="hint">{t('workCenter.hint.capacity')}</span>
            </div>
            <div className="field">
              <label htmlFor="wc-efficiency">{t('workCenter.field.efficiency')}</label>
              <input
                id="wc-efficiency"
                className="input input--num"
                type="number"
                min={1}
                max={200}
                step="0.1"
                value={form.efficiencyPercent}
                onChange={(event) =>
                  patch({ efficiencyPercent: Number(event.target.value) || 100 })
                }
              />
            </div>
            <div className="field">
              <label htmlFor="wc-rate">{t('workCenter.field.costRate')}</label>
              <input
                id="wc-rate"
                className="input input--num"
                type="number"
                min={0}
                step="0.01"
                value={form.costRatePerHour}
                onChange={(event) => patch({ costRatePerHour: Number(event.target.value) || 0 })}
              />
              <span className="hint">{t('workCenter.hint.costRate')}</span>
            </div>
          </div>

          <div className="line-actions" style={{ justifyContent: 'flex-end' }}>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => setEditing(null)}
              disabled={saving}
            >
              {t('common.cancel')}
            </button>
            <button type="submit" className="btn btn--primary btn--sm" disabled={saving}>
              {saving ? t('common.saving') : t('common.save')}
            </button>
          </div>
        </form>
      </Drawer>

      <ConfirmDialog
        open={deactivating !== null}
        danger
        title={t('workCenter.confirm.deactivateTitle')}
        body={t('workCenter.confirm.deactivateBody', { code: deactivating?.code ?? '' })}
        confirmLabel={t('common.deactivate')}
        busy={statusMutation.isPending}
        onCancel={() => setDeactivating(null)}
        onConfirm={() => deactivating && statusMutation.mutate(deactivating)}
      />
    </>
  )
}
