import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import { listAudit, type AuditView } from '../../shared/api/system'

/**
 * The audit trail.
 *
 * <p>Read-only on purpose. The console can filter and inspect; it cannot correct an
 * entry, because a trail that the subject of the trail can edit is worth nothing. The
 * detail drawer shows the raw JSON snapshot — an auditor asking "what exactly changed"
 * wants the values, not a paraphrase.
 */
export default function AuditPage() {
  const { t, i18n } = useTranslation()

  const [keyword, setKeyword] = useState('')
  const [action, setAction] = useState('')
  const [resourceType, setResourceType] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [detail, setDetail] = useState<AuditView | null>(null)

  const PAGE_SIZE = 20

  const auditQuery = useQuery({
    queryKey: ['system-audit', keyword, action, resourceType, from, to, page],
    queryFn: () =>
      listAudit({
        username: keyword || undefined,
        action: action || undefined,
        resourceType: resourceType || undefined,
        from: from || undefined,
        to: to || undefined,
        page,
        size: PAGE_SIZE,
      }),
  })

  const formatTime = (value: string) =>
    new Date(value).toLocaleString(i18n.language === 'en-US' ? 'en-US' : 'zh-CN', {
      dateStyle: 'short',
      timeStyle: 'medium',
    })

  /** Actions seen in the current result set, so the filter list never offers a dead option. */
  const actionOptions = [...new Set((auditQuery.data?.content ?? []).map((row) => row.action))].sort()

  const columns: Column<AuditView>[] = [
    {
      key: 'createdAt',
      header: t('system.audit.time'),
      width: '165px',
      render: (row) => <span className="mono">{formatTime(row.createdAt)}</span>,
    },
    {
      key: 'username',
      header: t('system.audit.username'),
      width: '130px',
      render: (row) => (
        <span className="mono" style={{ fontWeight: 600 }}>
          {row.username ?? '—'}
        </span>
      ),
    },
    {
      key: 'action',
      header: t('system.audit.action'),
      width: '190px',
      render: (row) => <span className="tag tag--neutral">{row.action}</span>,
    },
    {
      key: 'resource',
      header: t('system.audit.resource'),
      render: (row) =>
        row.resourceType ? (
          <span className="mono hint">
            {row.resourceType}
            {row.resourceId ? ` · ${row.resourceId}` : ''}
          </span>
        ) : (
          '—'
        ),
    },
    {
      key: 'ip',
      header: t('system.audit.ip'),
      width: '130px',
      render: (row) => <span className="mono hint">{row.ipAddress ?? '—'}</span>,
    },
    {
      key: 'detail',
      header: '',
      width: '90px',
      align: 'right',
      render: (row) =>
        row.detail ? <span className="hint">{t('system.audit.viewDetail')}</span> : null,
    },
  ]

  const result = auditQuery.data

  return (
    <>
      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('system.audit.searchPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <select
          className="select"
          value={action}
          onChange={(event) => {
            setAction(event.target.value)
            setPage(0)
          }}
        >
          <option value="">{t('system.audit.allActions')}</option>
          {actionOptions.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
        <select
          className="select"
          value={resourceType}
          onChange={(event) => {
            setResourceType(event.target.value)
            setPage(0)
          }}
        >
          <option value="">{t('system.audit.allResources')}</option>
          <option value="USER">{t('system.audit.target.USER')}</option>
          <option value="ROLE">{t('system.audit.target.ROLE')}</option>
          <option value="ITEM">{t('system.audit.target.ITEM')}</option>
          <option value="VOUCHER">{t('system.audit.target.VOUCHER')}</option>
        </select>
        <input
          className="input"
          type="date"
          style={{ width: 150 }}
          value={from}
          onChange={(event) => {
            setFrom(event.target.value)
            setPage(0)
          }}
        />
        <input
          className="input"
          type="date"
          style={{ width: 150 }}
          value={to}
          onChange={(event) => {
            setTo(event.target.value)
            setPage(0)
          }}
        />
        <div className="toolbar__spacer" />
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => {
            setKeyword('')
            setAction('')
            setResourceType('')
            setFrom('')
            setTo('')
            setPage(0)
          }}
        >
          {t('common.reset')}
        </button>
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(row) => row.id}
        loading={auditQuery.isLoading}
        error={auditQuery.error instanceof ApiError ? auditQuery.error.message : null}
        emptyTitle={t('system.audit.emptyTitle')}
        emptyBody={t('system.audit.emptyBody')}
        onRowClick={(row) => (row.detail ? setDetail(row) : undefined)}
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
        open={detail !== null}
        title={t('system.audit.detailTitle')}
        subtitle={detail ? `${detail.action} · ${detail.username ?? '—'}` : undefined}
        onClose={() => setDetail(null)}
      >
        {detail ? (
          <div>
            <div className="form-grid form-grid--3" style={{ marginBottom: 16 }}>
              <div className="field">
                <span className="hint">{t('system.audit.time')}</span>
                <div className="mono">{formatTime(detail.createdAt)}</div>
              </div>
              <div className="field">
                <span className="hint">{t('system.audit.resource')}</span>
                <div className="mono">
                  {detail.resourceType ?? '—'}
                  {detail.resourceId ? ` · ${detail.resourceId}` : ''}
                </div>
              </div>
              <div className="field">
                <span className="hint">{t('system.audit.ip')}</span>
                <div className="mono">{detail.ipAddress ?? '—'}</div>
              </div>
            </div>
            <div className="section-title">{t('system.audit.detail')}</div>
            <pre className="mono" style={{ whiteSpace: 'pre-wrap', margin: 0, fontSize: 12 }}>
              {prettify(detail.detail)}
            </pre>
          </div>
        ) : null}
      </Drawer>
    </>
  )
}

/** Indent the stored JSON, or hand it back untouched when it is not JSON. */
function prettify(value: string | null): string {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
