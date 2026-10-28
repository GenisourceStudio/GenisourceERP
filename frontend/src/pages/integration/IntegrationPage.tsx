import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ApiError } from '../../shared/api/client'
import { listMesOutbox, retryMesMessage, type MesOutboxView } from '../../shared/api/integration'
import { useAuthStore } from '../../shared/auth/store'

/**
 * The messages waiting to be delivered to MES.
 *
 * <p>Almost always empty, and that is the point: an empty list means ERP and MES
 * agree about everything. A message that keeps failing is the one thing on this
 * screen worth acting on, so the failure reason is shown in full rather than as a
 * code.
 */
export default function IntegrationPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('integration:mes:write') ?? false
  const [error, setError] = useState<string | null>(null)

  const outboxQuery = useQuery({ queryKey: ['integration', 'mes', 'outbox'], queryFn: listMesOutbox })

  const retry = useMutation({
    mutationFn: retryMesMessage,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['integration', 'mes'] })
      setError(null)
    },
    onError: (exception: unknown) =>
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
  })

  const columns: Column<MesOutboxView>[] = [
    {
      key: 'subject',
      header: t('integration.field.subject'),
      width: '180px',
      render: (row) => <span className="mono">{row.subject}</span>,
    },
    {
      key: 'type',
      header: t('integration.field.type'),
      width: '140px',
      render: (row) => <span className="tag">{t(`integration.type.${row.messageType}`)}</span>,
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '110px',
      render: (row) => (
        <span className={`tag tag--${row.status.toLowerCase()}`}>
          {t(`integration.status.${row.status}`)}
        </span>
      ),
    },
    {
      key: 'attempts',
      header: t('integration.field.attempts'),
      width: '90px',
      align: 'right',
      render: (row) => <span className="mono">{row.attempts}</span>,
    },
    {
      key: 'next',
      header: t('integration.field.nextAttempt'),
      width: '170px',
      render: (row) =>
        row.status === 'SENT' ? (
          <span className="hint">{row.sentAt ? row.sentAt.replace('T', ' ').slice(0, 16) : '—'}</span>
        ) : (
          <span className="hint">{row.nextAttemptAt.replace('T', ' ').slice(0, 16)}</span>
        ),
    },
    {
      key: 'error',
      header: t('integration.field.lastError'),
      render: (row) => (row.lastError ? <span className="form-error">{row.lastError}</span> : ''),
    },
    {
      key: 'actions',
      header: '',
      width: '90px',
      align: 'right',
      render: (row) =>
        canWrite && row.status !== 'SENT' ? (
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => retry.mutate(row.id)}
          >
            {t('integration.action.retry')}
          </button>
        ) : null,
    },
  ]

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      <p className="hint">{t('integration.hint.standby')}</p>

      <DataTable
        columns={columns}
        rows={outboxQuery.data ?? []}
        rowKey={(row) => row.id}
        loading={outboxQuery.isLoading}
        error={outboxQuery.error instanceof ApiError ? outboxQuery.error.message : null}
        emptyTitle={t('integration.empty.title')}
        emptyBody={t('integration.empty.body')}
      />
    </>
  )
}
