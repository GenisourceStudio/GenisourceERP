import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog } from '../../components/Overlay'
import { ApiError } from '../../shared/api/client'
import {
  createAccount,
  listAccounts,
  updateAccount,
  type AccountType,
  type AccountView,
} from '../../shared/api/ledger'
import { useAuthStore } from '../../shared/auth/store'

/**
 * The chart of accounts.
 *
 * <p>Shown as a flat list with the level as indentation rather than a tree: a chart
 * is at most a few hundred rows, and a tree that expands one branch at a time hides
 * the shape of the chart — which is the thing a reader is actually looking for.
 *
 * <p>Editing is deliberately thin. A name, a switch and a role; the code and the type
 * are the account's identity and do not move. A chart that can be renumbered after
 * posting re-labels every report that was ever printed from it.
 */
export default function AccountsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('finance:account:write') ?? false

  const [keyword, setKeyword] = useState('')
  const [editing, setEditing] = useState<AccountView | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ code: '', name: '', accountType: 'ASSET' as AccountType })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const accountsQuery = useQuery({ queryKey: ['finance', 'accounts'], queryFn: listAccounts })
  const accounts = accountsQuery.data ?? []

  const visible = useMemo(() => {
    const term = keyword.trim().toLowerCase()
    if (!term) {
      return accounts
    }
    return accounts.filter(
      (account) =>
        account.code.toLowerCase().includes(term) || account.name.toLowerCase().includes(term),
    )
  }, [accounts, keyword])

  const save = useMutation({
    mutationFn: async () => {
      if (creating) {
        return createAccount({
          code: form.code,
          name: form.name,
          accountType: form.accountType,
        })
      }
      if (!editing) {
        throw new Error('nothing to save')
      }
      return updateAccount(editing.id, { name: form.name })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['finance', 'accounts'] })
      setEditing(null)
      setCreating(false)
      setForm({ code: '', name: '', accountType: 'ASSET' })
      setError(null)
    },
    onError: (exception: unknown) =>
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
  })

  const toggle = useMutation({
    mutationFn: async (account: AccountView) =>
      updateAccount(account.id, { name: account.name, active: !account.active }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['finance', 'accounts'] }),
    onError: (exception: unknown) =>
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
  })

  const columns: Column<AccountView>[] = [
    {
      key: 'code',
      header: t('ledger.field.code'),
      width: '120px',
      render: (row) => (
        <span className="mono" style={{ paddingLeft: (row.level - 1) * 14 }}>
          {row.code}
        </span>
      ),
    },
    {
      key: 'name',
      header: t('ledger.field.name'),
      render: (row) => (
        <span>
          {row.name}
          {row.active ? null : <span className="hint"> · {t('ledger.inactive')}</span>}
        </span>
      ),
    },
    {
      key: 'type',
      header: t('ledger.field.accountType'),
      width: '120px',
      render: (row) => <span className="tag">{t(`ledger.accountType.${row.accountType}`)}</span>,
    },
    {
      key: 'direction',
      header: t('ledger.field.direction'),
      width: '90px',
      render: (row) => (
        <span className="mono">
          {row.balanceDirection === 'DEBIT' ? t('ledger.debit') : t('ledger.credit')}
        </span>
      ),
    },
    {
      key: 'role',
      header: t('ledger.field.role'),
      width: '150px',
      render: (row) =>
        row.systemRole ? (
          <span className="hint mono">{t(`ledger.role.${row.systemRole}`)}</span>
        ) : null,
    },
    {
      key: 'actions',
      header: '',
      width: '150px',
      align: 'right',
      render: (row) =>
        canWrite ? (
          <div className="row-actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => {
                setEditing(row)
                setCreating(false)
                setForm({ code: row.code, name: row.name, accountType: row.accountType })
                setError(null)
              }}
            >
              {t('common.edit')}
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => toggle.mutate(row)}
            >
              {row.active ? t('common.deactivate') : t('common.activate')}
            </button>
          </div>
        ) : null,
    },
  ]

  const submit = () => {
    setBusy(true)
    save.mutate(undefined, { onSettled: () => setBusy(false) })
  }

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            type="search"
            placeholder={t('ledger.searchPlaceholder')}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => {
              setCreating(true)
              setEditing(null)
              setForm({ code: '', name: '', accountType: 'ASSET' })
              setError(null)
            }}
          >
            {t('ledger.action.addAccount')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={visible}
        rowKey={(row) => row.id}
        loading={accountsQuery.isLoading}
        error={accountsQuery.error instanceof ApiError ? accountsQuery.error.message : null}
        emptyTitle={t('ledger.emptyAccounts.title')}
        emptyBody={t('ledger.emptyAccounts.body')}
      />

      <ConfirmDialog
        open={creating || editing !== null}
        title={creating ? t('ledger.action.addAccount') : t('ledger.action.editAccount')}
        confirmLabel={t('common.save')}
        busy={busy}
        onConfirm={submit}
        onCancel={() => {
          setCreating(false)
          setEditing(null)
        }}
        body={
          <div className="form-grid">
            {creating ? (
              <>
                <label className="field">
                  <span className="hint">{t('ledger.field.code')}</span>
                  <input
                    className="input"
                    value={form.code}
                    onChange={(event) => setForm({ ...form, code: event.target.value })}
                  />
                </label>
                <label className="field">
                  <span className="hint">{t('ledger.field.accountType')}</span>
                  <select
                    className="select"
                    value={form.accountType}
                    onChange={(event) =>
                      setForm({ ...form, accountType: event.target.value as AccountType })
                    }
                  >
                    {(['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE', 'COST'] as const).map(
                      (type) => (
                        <option key={type} value={type}>
                          {t(`ledger.accountType.${type}`)}
                        </option>
                      ),
                    )}
                  </select>
                </label>
              </>
            ) : null}
            <label className="field">
              <span className="hint">{t('ledger.field.name')}</span>
              <input
                className="input"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
              />
            </label>
          </div>
        }
      />
    </>
  )
}
