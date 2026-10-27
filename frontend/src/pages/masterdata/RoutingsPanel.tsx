import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { RevisionTag } from '../../components/Tags'
import { ApiError } from '../../shared/api/client'
import { useAuthStore } from '../../shared/auth/store'
import { useDebounced } from '../../shared/hooks/useDebounced'
import {
  getRouting,
  listRoutings,
  listWorkCenters,
  type RevisionStatus,
  type RoutingDetail,
  type RoutingSummary,
} from '../../shared/api/masterdata'
import RoutingEditor from './RoutingEditor'

const PAGE_SIZE = 20

export default function RoutingsPanel() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('masterdata:routing:write') ?? false

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<RevisionStatus | ''>('')
  const [page, setPage] = useState(0)
  const [editorId, setEditorId] = useState<number | 'new' | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const routingsQuery = useQuery({
    queryKey: ['routings', debouncedKeyword, status, page],
    queryFn: () => listRoutings({ q: debouncedKeyword, status, page, size: PAGE_SIZE }),
  })

  const workCentersQuery = useQuery({ queryKey: ['work-centers'], queryFn: () => listWorkCenters() })

  const editorQuery = useQuery({
    queryKey: ['routing', editorId],
    queryFn: () => getRouting(editorId as number),
    enabled: typeof editorId === 'number',
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['routings'] })
    void queryClient.invalidateQueries({ queryKey: ['routing'] })
  }

  const columns: Column<RoutingSummary>[] = [
    {
      key: 'item',
      header: t('routing.field.item'),
      render: (routing) => (
        <div>
          <div style={{ fontWeight: 600 }}>{routing.itemName}</div>
          <div className="mono hint">{routing.itemCode}</div>
        </div>
      ),
    },
    {
      key: 'version',
      header: t('routing.field.version'),
      width: '80px',
      render: (routing) => <span className="mono" style={{ fontSize: 12 }}>{routing.version}</span>,
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '96px',
      render: (routing) => <RevisionTag status={routing.status} />,
    },
    {
      key: 'count',
      header: t('routing.field.operationCount'),
      align: 'right',
      width: '80px',
      render: (routing) => routing.operationCount,
    },
    {
      key: 'minutes',
      header: t('routing.field.totalMinutes'),
      align: 'right',
      width: '110px',
      render: (routing) => routing.runMinutesPerUnit,
    },
    {
      key: 'updated',
      header: t('bom.field.updatedAt'),
      width: '150px',
      render: (routing) => (
        <span className="hint">
          {new Date(routing.updatedAt).toLocaleString(undefined, {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
          })}
        </span>
      ),
    },
  ]

  const result = routingsQuery.data

  return (
    <>
      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('routing.searchPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <select
          className="select"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as RevisionStatus | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allStatus')}</option>
          <option value="DRAFT">{t('status.revision.DRAFT')}</option>
          <option value="RELEASED">{t('status.revision.RELEASED')}</option>
          <option value="OBSOLETE">{t('status.revision.OBSOLETE')}</option>
        </select>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => setEditorId('new')}
            disabled={(workCentersQuery.data ?? []).length === 0}
            title={
              (workCentersQuery.data ?? []).length === 0 ? t('routing.hint.noWorkCenter') : undefined
            }
          >
            {t('routing.action.create')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(routing) => routing.id}
        loading={routingsQuery.isLoading}
        error={routingsQuery.error instanceof ApiError ? routingsQuery.error.message : null}
        emptyTitle={t('routing.emptyTitle')}
        emptyBody={
          (workCentersQuery.data ?? []).length === 0
            ? t('routing.emptyNoWorkCenter')
            : t('routing.emptyBody')
        }
        onRowClick={(routing) => setEditorId(routing.id)}
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
        open={editorId !== null}
        wide
        title={editorId === 'new' ? t('routing.action.create') : t('routing.action.edit')}
        subtitle={
          editorId === 'new'
            ? t('routing.hint.create')
            : editorQuery.data
              ? `${editorQuery.data.itemCode ?? ''} · ${editorQuery.data.version}`
              : t('common.loading')
        }
        onClose={() => setEditorId(null)}
      >
        {editorId === 'new' ? (
          <RoutingEditor
            initial={null}
            workCenters={workCentersQuery.data ?? []}
            onClose={() => setEditorId(null)}
            onSaved={(saved: RoutingDetail) => {
              invalidate()
              setEditorId(saved.id)
            }}
          />
        ) : editorQuery.data ? (
          <RoutingEditor
            initial={editorQuery.data}
            workCenters={workCentersQuery.data ?? []}
            onClose={() => setEditorId(null)}
            onSaved={() => invalidate()}
          />
        ) : (
          <p className="hint">{t('common.loading')}</p>
        )}
      </Drawer>
    </>
  )
}
