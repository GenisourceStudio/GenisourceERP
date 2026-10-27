import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { RecordTag } from '../../components/Tags'
import { ApiError } from '../../shared/api/client'
import { useAuthStore } from '../../shared/auth/store'
import { useDebounced } from '../../shared/hooks/useDebounced'
import {
  changePartnerStatus,
  createPartner,
  listPartners,
  updatePartner,
  type PartnerRequest,
  type PartnerType,
  type PartnerView,
  type RecordStatus,
} from '../../shared/api/masterdata'

const PAGE_SIZE = 20
const ROLES: PartnerType[] = ['CUSTOMER', 'SUPPLIER', 'BOTH']

function blank(): PartnerRequest {
  return {
    code: '',
    name: '',
    shortName: null,
    partnerType: 'SUPPLIER',
    taxNo: null,
    contactName: null,
    phone: null,
    email: null,
    address: null,
    currency: 'CNY',
    paymentTermsDays: 0,
    creditLimit: 0,
    remark: null,
    status: 'ACTIVE',
  }
}

export default function PartnersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('masterdata:partner:write') ?? false

  const [keyword, setKeyword] = useState('')
  const [role, setRole] = useState<PartnerType | ''>('')
  const [page, setPage] = useState(0)

  const [editing, setEditing] = useState<PartnerView | 'new' | null>(null)
  const [form, setForm] = useState<PartnerRequest>(blank())
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deactivating, setDeactivating] = useState<PartnerView | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const partnersQuery = useQuery({
    queryKey: ['partners', debouncedKeyword, role, page],
    queryFn: () => listPartners({ q: debouncedKeyword, role, page, size: PAGE_SIZE }),
  })

  const statusMutation = useMutation({
    mutationFn: (partner: PartnerView) => changePartnerStatus(partner.id, 'INACTIVE'),
    onSuccess: () => {
      setDeactivating(null)
      void queryClient.invalidateQueries({ queryKey: ['partners'] })
    },
    onError: (error) => {
      setActionError(error instanceof ApiError ? error.firstMessage : t('common.saveFailed'))
      setDeactivating(null)
    },
  })

  const openCreate = () => {
    setForm(blank())
    setFormError(null)
    setEditing('new')
  }

  const openEdit = (partner: PartnerView) => {
    setForm({
      code: partner.code,
      name: partner.name,
      shortName: partner.shortName,
      partnerType: partner.partnerType,
      taxNo: partner.taxNo,
      contactName: partner.contactName,
      phone: partner.phone,
      email: partner.email,
      address: partner.address,
      currency: partner.currency,
      paymentTermsDays: partner.paymentTermsDays,
      creditLimit: partner.creditLimit,
      remark: partner.remark,
      status: partner.status,
    })
    setFormError(null)
    setEditing(partner)
  }

  const patch = (changes: Partial<PartnerRequest>) => setForm((current) => ({ ...current, ...changes }))

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setFormError(null)
    try {
      if (editing && editing !== 'new') {
        await updatePartner(editing.id, form)
      } else {
        await createPartner(form)
      }
      setEditing(null)
      void queryClient.invalidateQueries({ queryKey: ['partners'] })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.firstMessage : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  const columns: Column<PartnerView>[] = [
    {
      key: 'code',
      header: t('partner.field.code'),
      width: '120px',
      render: (partner) => <span className="mono" style={{ fontSize: 12 }}>{partner.code}</span>,
    },
    {
      key: 'name',
      header: t('partner.field.name'),
      render: (partner) => (
        <div>
          <div style={{ fontWeight: 600 }}>{partner.name}</div>
          {partner.shortName ? <div className="hint">{partner.shortName}</div> : null}
        </div>
      ),
    },
    {
      key: 'type',
      header: t('partner.field.partnerType'),
      width: '96px',
      render: (partner) => <span className="tag">{t(`partnerType.${partner.partnerType}`)}</span>,
    },
    {
      key: 'contact',
      header: t('partner.field.contact'),
      width: '170px',
      render: (partner) => (
        <div>
          <div>{partner.contactName ?? '—'}</div>
          {partner.phone ? <div className="hint mono">{partner.phone}</div> : null}
        </div>
      ),
    },
    {
      key: 'terms',
      header: t('partner.field.paymentTerms'),
      align: 'right',
      width: '80px',
      render: (partner) => partner.paymentTermsDays,
    },
    {
      key: 'credit',
      header: t('partner.field.creditLimit'),
      align: 'right',
      width: '120px',
      render: (partner) => partner.creditLimit.toFixed(2),
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '88px',
      render: (partner) => <RecordTag status={partner.status} />,
    },
    {
      key: 'actions',
      header: '',
      width: '130px',
      align: 'right',
      render: (partner) => (
        <div className="row-actions">
          {canWrite ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                openEdit(partner)
              }}
            >
              {t('common.edit')}
            </button>
          ) : null}
          {canWrite && partner.status === 'ACTIVE' ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setDeactivating(partner)
              }}
            >
              {t('common.deactivate')}
            </button>
          ) : null}
        </div>
      ),
    },
  ]

  const result = partnersQuery.data

  return (
    <section className="panel">
      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('partner.searchPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <select
          className="select"
          value={role}
          onChange={(event) => {
            setRole(event.target.value as PartnerType | '')
            setPage(0)
          }}
        >
          <option value="">{t('partner.filter.allRoles')}</option>
          {ROLES.filter((value) => value !== 'BOTH').map((value) => (
            <option key={value} value={value}>
              {t(`partnerType.${value}`)}
            </option>
          ))}
        </select>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button type="button" className="btn btn--primary btn--sm" onClick={openCreate}>
            {t('partner.action.create')}
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
        rows={result?.content ?? []}
        rowKey={(partner) => partner.id}
        loading={partnersQuery.isLoading}
        error={partnersQuery.error instanceof ApiError ? partnersQuery.error.message : null}
        emptyTitle={t('partner.emptyTitle')}
        emptyBody={t('partner.emptyBody')}
        onRowClick={canWrite ? openEdit : undefined}
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
        open={editing !== null}
        title={editing === 'new' ? t('partner.action.create') : t('partner.action.edit')}
        subtitle={editing && editing !== 'new' ? editing.code : t('partner.hint.create')}
        onClose={() => setEditing(null)}
      >
        <form onSubmit={submit}>
          {formError ? <div className="form-error">{formError}</div> : null}

          <div className="form-grid">
            <div className="field">
              <label htmlFor="partner-code">{t('partner.field.code')}</label>
              <input
                id="partner-code"
                className="input"
                value={form.code}
                required
                maxLength={32}
                onChange={(event) => patch({ code: event.target.value.toUpperCase() })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-type">{t('partner.field.partnerType')}</label>
              <select
                id="partner-type"
                className="select"
                value={form.partnerType}
                onChange={(event) => patch({ partnerType: event.target.value as PartnerType })}
              >
                {ROLES.map((value) => (
                  <option key={value} value={value}>
                    {t(`partnerType.${value}`)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field span-2">
              <label htmlFor="partner-name">{t('partner.field.name')}</label>
              <input
                id="partner-name"
                className="input"
                value={form.name}
                required
                maxLength={200}
                onChange={(event) => patch({ name: event.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-short">{t('partner.field.shortName')}</label>
              <input
                id="partner-short"
                className="input"
                value={form.shortName ?? ''}
                maxLength={64}
                onChange={(event) => patch({ shortName: event.target.value || null })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-tax">{t('partner.field.taxNo')}</label>
              <input
                id="partner-tax"
                className="input"
                value={form.taxNo ?? ''}
                maxLength={64}
                onChange={(event) => patch({ taxNo: event.target.value || null })}
              />
            </div>
          </div>

          <div className="form-section">{t('partner.section.contact')}</div>
          <div className="form-grid">
            <div className="field">
              <label htmlFor="partner-contact">{t('partner.field.contactName')}</label>
              <input
                id="partner-contact"
                className="input"
                value={form.contactName ?? ''}
                maxLength={64}
                onChange={(event) => patch({ contactName: event.target.value || null })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-phone">{t('partner.field.phone')}</label>
              <input
                id="partner-phone"
                className="input"
                value={form.phone ?? ''}
                maxLength={32}
                onChange={(event) => patch({ phone: event.target.value || null })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-email">{t('partner.field.email')}</label>
              <input
                id="partner-email"
                className="input"
                type="email"
                value={form.email ?? ''}
                maxLength={128}
                onChange={(event) => patch({ email: event.target.value || null })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-address">{t('partner.field.address')}</label>
              <input
                id="partner-address"
                className="input"
                value={form.address ?? ''}
                maxLength={255}
                onChange={(event) => patch({ address: event.target.value || null })}
              />
            </div>
          </div>

          <div className="form-section">{t('partner.section.terms')}</div>
          <div className="form-grid form-grid--3">
            <div className="field">
              <label htmlFor="partner-currency">{t('partner.field.currency')}</label>
              <select
                id="partner-currency"
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
              <label htmlFor="partner-days">{t('partner.field.paymentTerms')}</label>
              <input
                id="partner-days"
                className="input input--num"
                type="number"
                min={0}
                value={form.paymentTermsDays}
                onChange={(event) => patch({ paymentTermsDays: Number(event.target.value) || 0 })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-limit">{t('partner.field.creditLimit')}</label>
              <input
                id="partner-limit"
                className="input input--num"
                type="number"
                min={0}
                step="0.01"
                value={form.creditLimit}
                onChange={(event) => patch({ creditLimit: Number(event.target.value) || 0 })}
              />
            </div>
            <div className="field">
              <label htmlFor="partner-status">{t('common.status')}</label>
              <select
                id="partner-status"
                className="select"
                value={form.status ?? 'ACTIVE'}
                onChange={(event) => patch({ status: event.target.value as RecordStatus })}
              >
                <option value="ACTIVE">{t('status.record.ACTIVE')}</option>
                <option value="INACTIVE">{t('status.record.INACTIVE')}</option>
              </select>
            </div>
          </div>

          <div className="form-section">{t('common.remark')}</div>
          <div className="field">
            <label htmlFor="partner-remark">{t('common.remark')}</label>
            <textarea
              id="partner-remark"
              className="input"
              value={form.remark ?? ''}
              maxLength={500}
              onChange={(event) => patch({ remark: event.target.value || null })}
            />
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
        title={t('partner.confirm.deactivateTitle')}
        body={t('partner.confirm.deactivateBody', {
          code: deactivating?.code ?? '',
          name: deactivating?.name ?? '',
        })}
        confirmLabel={t('common.deactivate')}
        busy={statusMutation.isPending}
        onCancel={() => setDeactivating(null)}
        onConfirm={() => deactivating && statusMutation.mutate(deactivating)}
      />
    </section>
  )
}
