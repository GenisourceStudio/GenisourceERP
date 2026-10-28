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
  changeItemStatus,
  listCategories,
  listItems,
  listUoms,
  type ItemType,
  type ItemView,
  type RecordStatus,
} from '../../shared/api/masterdata'
import ItemForm from './ItemForm'

const PAGE_SIZE = 20
const TYPES: ItemType[] = ['RAW', 'SEMI', 'FINISHED', 'CONSUMABLE', 'SERVICE']

export default function ItemsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('masterdata:item:write') ?? false

  const [keyword, setKeyword] = useState('')
  const [type, setType] = useState<ItemType | ''>('')
  const [status, setStatus] = useState<RecordStatus | ''>('ACTIVE')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [page, setPage] = useState(0)

  const [editing, setEditing] = useState<ItemView | 'new' | null>(null)
  const [deactivating, setDeactivating] = useState<ItemView | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const itemsQuery = useQuery({
    queryKey: ['items', debouncedKeyword, type, status, categoryId, page],
    queryFn: () =>
      listItems({
        q: debouncedKeyword,
        itemType: type,
        status,
        categoryId,
        page,
        size: PAGE_SIZE,
      }),
  })

  const categoriesQuery = useQuery({ queryKey: ['item-categories'], queryFn: listCategories })
  const uomsQuery = useQuery({ queryKey: ['uoms'], queryFn: listUoms })

  const statusMutation = useMutation({
    mutationFn: (item: ItemView) => changeItemStatus(item.id, 'INACTIVE'),
    onSuccess: () => {
      setDeactivating(null)
      setActionError(null)
      void queryClient.invalidateQueries({ queryKey: ['items'] })
    },
    onError: (error) => {
      setActionError(error instanceof ApiError ? error.firstMessage : t('common.saveFailed'))
      setDeactivating(null)
    },
  })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['items'] })
    void queryClient.invalidateQueries({ queryKey: ['item-categories'] })
  }

  const columns: Column<ItemView>[] = [
    {
      key: 'code',
      header: t('item.field.code'),
      width: '140px',
      render: (item) => <span className="mono" style={{ fontSize: 12 }}>{item.code}</span>,
    },
    {
      key: 'name',
      header: t('item.field.name'),
      render: (item) => (
        <div>
          <div style={{ fontWeight: 600 }}>{item.name}</div>
          {item.spec ? <div className="hint">{item.spec}</div> : null}
        </div>
      ),
    },
    {
      key: 'itemType',
      header: t('item.field.itemType'),
      width: '90px',
      render: (item) => <span className="tag">{t(`itemType.${item.itemType}`)}</span>,
    },
    {
      key: 'uom',
      header: t('item.field.uom'),
      width: '70px',
      render: (item) => <span className="mono" style={{ fontSize: 12 }}>{item.uomCode}</span>,
    },
    {
      key: 'category',
      header: t('item.field.category'),
      width: '110px',
      render: (item) => item.categoryName ?? '—',
    },
    {
      key: 'facets',
      header: t('item.field.facets'),
      width: '150px',
      render: (item) => (
        <div className="chip-set">
          {item.purchased ? <span className="tag">{t('item.facet.purchased')}</span> : null}
          {item.manufactured ? <span className="tag">{t('item.facet.manufactured')}</span> : null}
          {item.sold ? <span className="tag">{t('item.facet.sold')}</span> : null}
        </div>
      ),
    },
    {
      key: 'cost',
      header: t('item.field.standardCost'),
      align: 'right',
      width: '100px',
      render: (item) => item.standardCost.toFixed(2),
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '88px',
      render: (item) => <RecordTag status={item.status} />,
    },
    {
      key: 'actions',
      header: '',
      width: '120px',
      align: 'right',
      render: (item) => (
        <div className="row-actions">
          {canWrite ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setEditing(item)
              }}
            >
              {t('common.edit')}
            </button>
          ) : null}
          {canWrite && item.status === 'ACTIVE' ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setDeactivating(item)
              }}
            >
              {t('common.deactivate')}
            </button>
          ) : null}
        </div>
      ),
    },
  ]

  const result = itemsQuery.data

  return (
    <>
      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('item.searchPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <select
          className="select"
          value={type}
          onChange={(event) => {
            setType(event.target.value as ItemType | '')
            setPage(0)
          }}
        >
          <option value="">{t('item.filter.allTypes')}</option>
          {TYPES.map((value) => (
            <option key={value} value={value}>
              {t(`itemType.${value}`)}
            </option>
          ))}
        </select>
        <select
          className="select"
          value={categoryId ?? ''}
          onChange={(event) => {
            setCategoryId(event.target.value ? Number(event.target.value) : null)
            setPage(0)
          }}
        >
          <option value="">{t('item.filter.allCategories')}</option>
          {(categoriesQuery.data ?? []).map((category) => (
            <option key={category.id} value={category.id}>
              {category.name} ({category.itemCount})
            </option>
          ))}
        </select>
        <select
          className="select"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as RecordStatus | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allStatus')}</option>
          <option value="ACTIVE">{t('status.record.ACTIVE')}</option>
          <option value="INACTIVE">{t('status.record.INACTIVE')}</option>
        </select>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button type="button" className="btn btn--primary btn--sm" onClick={() => setEditing('new')}>
            {t('item.action.create')}
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
        rowKey={(item) => item.id}
        loading={itemsQuery.isLoading}
        error={itemsQuery.error instanceof ApiError ? itemsQuery.error.message : null}
        emptyTitle={t('item.emptyTitle')}
        emptyBody={t('item.emptyBody')}
        onRowClick={canWrite ? (item) => setEditing(item) : undefined}
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
        wide
        title={editing === 'new' ? t('item.action.create') : t('item.action.edit')}
        subtitle={editing && editing !== 'new' ? `${editing.code} · ${editing.name}` : t('item.hint.create')}
        onClose={() => setEditing(null)}
      >
        {editing !== null ? (
          <ItemForm
            initial={editing === 'new' ? null : editing}
            categories={categoriesQuery.data ?? []}
            uoms={uomsQuery.data ?? []}
            onCancel={() => setEditing(null)}
            onSaved={() => {
              setEditing(null)
              refresh()
            }}
          />
        ) : null}
      </Drawer>

      <ConfirmDialog
        open={deactivating !== null}
        danger
        title={t('item.confirm.deactivateTitle')}
        body={t('item.confirm.deactivateBody', {
          code: deactivating?.code ?? '',
          name: deactivating?.name ?? '',
        })}
        confirmLabel={t('common.deactivate')}
        busy={statusMutation.isPending}
        onCancel={() => setDeactivating(null)}
        onConfirm={() => deactivating && statusMutation.mutate(deactivating)}
      />
    </>
  )
}
