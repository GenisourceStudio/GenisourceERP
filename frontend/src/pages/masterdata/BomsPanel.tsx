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
  bomTree,
  getBom,
  listBoms,
  type BomDetail,
  type BomSummary,
  type RevisionStatus,
} from '../../shared/api/masterdata'
import BomEditor from './BomEditor'
import BomTreeView from './BomTreeView'

const PAGE_SIZE = 20

export default function BomsPanel() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('masterdata:bom:write') ?? false

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<RevisionStatus | ''>('')
  const [page, setPage] = useState(0)

  const [editorId, setEditorId] = useState<number | 'new' | null>(null)
  const [treeTarget, setTreeTarget] = useState<BomSummary | null>(null)
  const [treeQty, setTreeQty] = useState(1)

  const debouncedKeyword = useDebounced(keyword)

  const bomsQuery = useQuery({
    queryKey: ['boms', debouncedKeyword, status, page],
    queryFn: () => listBoms({ q: debouncedKeyword, status, page, size: PAGE_SIZE }),
  })

  const editorQuery = useQuery({
    queryKey: ['bom', editorId],
    queryFn: () => getBom(editorId as number),
    enabled: typeof editorId === 'number',
  })

  const treeQuery = useQuery({
    queryKey: ['bom-tree', treeTarget?.id, treeQty],
    queryFn: () => bomTree(treeTarget!.id, treeQty),
    enabled: treeTarget !== null,
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['boms'] })
    void queryClient.invalidateQueries({ queryKey: ['bom'] })
  }

  const columns: Column<BomSummary>[] = [
    {
      key: 'item',
      header: t('bom.field.item'),
      render: (bom) => (
        <div>
          <div style={{ fontWeight: 600 }}>{bom.itemName}</div>
          <div className="mono hint">{bom.itemCode}</div>
        </div>
      ),
    },
    {
      key: 'version',
      header: t('bom.field.version'),
      width: '80px',
      render: (bom) => <span className="mono" style={{ fontSize: 12 }}>{bom.version}</span>,
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '96px',
      render: (bom) => <RevisionTag status={bom.status} />,
    },
    {
      key: 'baseQty',
      header: t('bom.field.baseQty'),
      align: 'right',
      width: '80px',
      render: (bom) => bom.baseQty,
    },
    {
      key: 'lines',
      header: t('bom.field.lineCount'),
      align: 'right',
      width: '70px',
      render: (bom) => bom.lineCount,
    },
    {
      key: 'effective',
      header: t('bom.field.effective'),
      width: '170px',
      render: (bom) => (
        <span className="hint mono">
          {bom.effectiveFrom ?? '—'} → {bom.effectiveTo ?? '—'}
        </span>
      ),
    },
    {
      key: 'updated',
      header: t('bom.field.updatedAt'),
      width: '150px',
      render: (bom) => <span className="hint">{formatTime(bom.updatedAt)}</span>,
    },
    {
      key: 'actions',
      header: '',
      width: '110px',
      align: 'right',
      render: (bom) => (
        <div className="row-actions">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={(event) => {
              event.stopPropagation()
              setTreeQty(1)
              setTreeTarget(bom)
            }}
          >
            {t('bom.action.explode')}
          </button>
        </div>
      ),
    },
  ]

  const result = bomsQuery.data

  return (
    <>
      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('bom.searchPlaceholder')}
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
          <button type="button" className="btn btn--primary btn--sm" onClick={() => setEditorId('new')}>
            {t('bom.action.create')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(bom) => bom.id}
        loading={bomsQuery.isLoading}
        error={bomsQuery.error instanceof ApiError ? bomsQuery.error.message : null}
        emptyTitle={t('bom.emptyTitle')}
        emptyBody={t('bom.emptyBody')}
        onRowClick={(bom) => setEditorId(bom.id)}
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
        title={editorId === 'new' ? t('bom.action.create') : t('bom.action.edit')}
        subtitle={
          editorId === 'new'
            ? t('bom.hint.create')
            : editorQuery.data
              ? `${editorQuery.data.itemCode ?? ''} · ${editorQuery.data.version}`
              : t('common.loading')
        }
        onClose={() => setEditorId(null)}
      >
        {editorId === 'new' ? (
          <BomEditor
            initial={null}
            onClose={() => setEditorId(null)}
            onSaved={(saved: BomDetail) => {
              invalidate()
              setEditorId(saved.id)
            }}
          />
        ) : editorQuery.data ? (
          <BomEditor
            initial={editorQuery.data}
            onClose={() => setEditorId(null)}
            onSaved={() => {
              invalidate()
            }}
          />
        ) : (
          <p className="hint">{t('common.loading')}</p>
        )}
      </Drawer>

      <Drawer
        open={treeTarget !== null}
        title={t('bom.tree.title')}
        subtitle={
          treeTarget ? `${treeTarget.itemCode} · ${treeTarget.itemName} · ${treeTarget.version}` : ''
        }
        onClose={() => setTreeTarget(null)}
      >
        <div className="row gap-12" style={{ alignItems: 'flex-end', marginBottom: 16 }}>
          <div className="field" style={{ width: 160 }}>
            <label htmlFor="tree-qty">{t('bom.tree.produceQty')}</label>
            <input
              id="tree-qty"
              className="input input--num"
              type="number"
              min={0.001}
              step="1"
              value={treeQty}
              onChange={(event) => setTreeQty(Number(event.target.value) || 1)}
            />
          </div>
          <p className="hint" style={{ margin: 0, flex: 1 }}>
            {t('bom.tree.hint')}
          </p>
        </div>

        {treeQuery.isLoading ? <p className="hint">{t('common.loading')}</p> : null}
        {treeQuery.data ? <BomTreeView node={treeQuery.data} /> : null}
        {treeQuery.error instanceof ApiError ? (
          <div className="form-error">{treeQuery.error.message}</div>
        ) : null}
      </Drawer>
    </>
  )
}

function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
