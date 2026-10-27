import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import {
  listDocuments,
  listWarehouses,
  postDocument,
  reverseDocument,
  type DocumentStatus,
  type DocumentType,
  type DocumentView,
} from '../../shared/api/inventory'
import { useAuthStore } from '../../shared/auth/store'
import { useDebounced } from '../../shared/hooks/useDebounced'
import DocumentEditor from './DocumentEditor'
import { formatMoney, formatQty } from './StockPage'

const PAGE_SIZE = 20
const TYPES: DocumentType[] = ['INBOUND', 'OUTBOUND', 'TRANSFER', 'COUNT']

/**
 * Stock documents: receipts, issues, transfers and counts.
 *
 * <p>Posting is the moment the document stops being a piece of paper and starts being
 * the stock. It is irreversible in the literal sense, so the button that does it says
 * so, and the way back is a red-ink reversal rather than an edit.
 */
export default function DocumentsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('inventory:stock:write') ?? false

  const [keyword, setKeyword] = useState('')
  const [docType, setDocType] = useState<DocumentType | ''>('')
  const [status, setStatus] = useState<DocumentStatus | ''>('')
  const [warehouseId, setWarehouseId] = useState<number | ''>('')
  const [page, setPage] = useState(0)

  const [editorId, setEditorId] = useState<number | 'new' | null>(null)
  const [detail, setDetail] = useState<DocumentView | null>(null)
  const [posting, setPosting] = useState<DocumentView | null>(null)
  const [reversing, setReversing] = useState<DocumentView | null>(null)
  const [error, setError] = useState<string | null>(null)

  const debouncedKeyword = useDebounced(keyword)

  const warehousesQuery = useQuery({ queryKey: ['warehouses'], queryFn: listWarehouses })
  const documentsQuery = useQuery({
    queryKey: ['documents', debouncedKeyword, docType, status, warehouseId, page],
    queryFn: () =>
      listDocuments({
        q: debouncedKeyword,
        docType: docType === '' ? null : docType,
        status: status === '' ? null : status,
        warehouseId: warehouseId === '' ? null : warehouseId,
        page,
        size: PAGE_SIZE,
      }),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['documents'] })
    void queryClient.invalidateQueries({ queryKey: ['stock'] })
    void queryClient.invalidateQueries({ queryKey: ['movements'] })
  }

  const post = useMutation({
    mutationFn: (id: number) => postDocument(id),
    onSuccess: invalidate,
    onError: (exception) =>
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
  })

  const reverse = useMutation({
    mutationFn: (id: number) => reverseDocument(id),
    onSuccess: invalidate,
    onError: (exception) =>
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed')),
  })

  const columns: Column<DocumentView>[] = [
    {
      key: 'docNo',
      header: t('inventory.field.document'),
      width: '170px',
      render: (document) => <span className="mono">{document.docNo}</span>,
    },
    {
      key: 'type',
      header: t('inventory.field.docType'),
      width: '110px',
      render: (document) => (
        <span className="tag tag--neutral">{t(`docType.${document.docType}`)}</span>
      ),
    },
    {
      key: 'warehouse',
      header: t('inventory.field.warehouse'),
      render: (document) => (
        <div>
          <div>{document.warehouseName}</div>
          {document.toWarehouseName ? (
            <div className="hint">→ {document.toWarehouseName}</div>
          ) : null}
        </div>
      ),
    },
    {
      key: 'partner',
      header: t('inventory.field.partner'),
      width: '130px',
      render: (document) =>
        document.partnerName ? (
          <span>{document.partnerName}</span>
        ) : (
          <span className="hint">—</span>
        ),
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '90px',
      render: (document) => (
        <span className={`tag tag--${document.status.toLowerCase()}`}>
          {t(`status.document.${document.status}`)}
        </span>
      ),
    },
    {
      key: 'qty',
      header: t('inventory.field.totalQty'),
      align: 'right',
      width: '90px',
      render: (document) => <span className="mono">{formatQty(document.totalQty)}</span>,
    },
    {
      key: 'amount',
      header: t('inventory.field.totalAmount'),
      align: 'right',
      width: '110px',
      render: (document) => <span className="mono">{formatMoney(document.totalAmount)}</span>,
    },
    {
      key: 'date',
      header: t('inventory.field.bizDate'),
      width: '110px',
      render: (document) => <span className="hint mono">{document.bizDate}</span>,
    },
    {
      key: 'actions',
      header: '',
      width: '150px',
      align: 'right',
      render: (document) => (
        <div className="row-actions">
          {document.status === 'DRAFT' && canWrite ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setPosting(document)
              }}
            >
              {t('inventory.action.post')}
            </button>
          ) : null}
          {document.status === 'POSTED' && canWrite ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setReversing(document)
              }}
            >
              {t('inventory.action.reverse')}
            </button>
          ) : null}
        </div>
      ),
    },
  ]

  const result = documentsQuery.data
  const warehouses = warehousesQuery.data ?? []

  return (
    <>
      {error ? <div className="form-error">{error}</div> : null}

      <div className="toolbar">
        <div className="toolbar__search">
          <input
            className="input"
            value={keyword}
            placeholder={t('inventory.searchDocumentsPlaceholder')}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <select
          className="select"
          value={docType}
          onChange={(event) => {
            setDocType(event.target.value as DocumentType | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allTypes')}</option>
          {TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`docType.${type}`)}
            </option>
          ))}
        </select>
        <select
          className="select"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as DocumentStatus | '')
            setPage(0)
          }}
        >
          <option value="">{t('common.allStatus')}</option>
          <option value="DRAFT">{t('status.document.DRAFT')}</option>
          <option value="POSTED">{t('status.document.POSTED')}</option>
          <option value="REVERSED">{t('status.document.REVERSED')}</option>
        </select>
        <select
          className="select"
          value={warehouseId}
          onChange={(event) => {
            setWarehouseId(event.target.value === '' ? '' : Number(event.target.value))
            setPage(0)
          }}
        >
          <option value="">{t('common.allWarehouses')}</option>
          {warehouses.map((warehouse) => (
            <option key={warehouse.id} value={warehouse.id}>
              {warehouse.code} · {warehouse.name}
            </option>
          ))}
        </select>
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => setEditorId('new')}
          >
            {t('inventory.action.createDocument')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={result?.content ?? []}
        rowKey={(document) => document.id}
        loading={documentsQuery.isLoading}
        error={documentsQuery.error instanceof ApiError ? documentsQuery.error.message : null}
        emptyTitle={t('inventory.emptyDocuments')}
        emptyBody={t('inventory.emptyDocumentsBody')}
        onRowClick={(document) => setDetail(document)}
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
        title={editorId === 'new' ? t('inventory.action.createDocument') : t('inventory.action.editDocument')}
        subtitle={editorId === 'new' ? t('inventory.hint.createDocument') : undefined}
        onClose={() => setEditorId(null)}
      >
        <DocumentEditor
          initial={editorId === 'new' ? null : (detail ?? null)}
          warehouses={warehouses.map((warehouse) => ({
            id: warehouse.id,
            code: warehouse.code,
            name: warehouse.name,
          }))}
          onSaved={() => {
            invalidate()
            setEditorId(null)
          }}
          onCancel={() => setEditorId(null)}
        />
      </Drawer>

      <Drawer
        open={detail !== null && editorId === null}
        title={detail?.docNo ?? ''}
        subtitle={
          detail
            ? `${t(`docType.${detail.docType}`)} · ${t(`status.document.${detail.status}`)}`
            : ''
        }
        onClose={() => setDetail(null)}
      >
        {detail ? (
          <div>
            <div className="form-grid form-grid--3" style={{ marginBottom: 18 }}>
              <div className="field">
                <span className="hint">{t('inventory.field.warehouse')}</span>
                <div>{detail.warehouseName}</div>
              </div>
              {detail.toWarehouseName ? (
                <div className="field">
                  <span className="hint">{t('inventory.field.toWarehouse')}</span>
                  <div>{detail.toWarehouseName}</div>
                </div>
              ) : null}
              <div className="field">
                <span className="hint">{t('inventory.field.bizDate')}</span>
                <div className="mono">{detail.bizDate}</div>
              </div>
              {detail.reversalOfNo ? (
                <div className="field">
                  <span className="hint">{t('inventory.field.reversalOf')}</span>
                  <div className="mono">{detail.reversalOfNo}</div>
                </div>
              ) : null}
              {detail.partnerName ? (
                <div className="field">
                  <span className="hint">{t('inventory.field.partner')}</span>
                  <div>{detail.partnerName}</div>
                </div>
              ) : null}
            </div>

            <div className="line-list">
              {detail.lines.map((line) => (
                <div className="line-list__row" key={line.id}>
                  <span style={{ flex: 1 }}>
                    <strong>{line.itemCode}</strong> {line.itemName}
                    {line.lotNo ? <span className="mono hint"> · {line.lotNo}</span> : null}
                  </span>
                  <span className="mono">{formatQty(line.qty)}</span>
                  <span className="mono hint">{formatMoney(line.amount)}</span>
                </div>
              ))}
            </div>

            {detail.remark ? <p className="hint">{detail.remark}</p> : null}

            {detail.status === 'DRAFT' && canWrite ? (
              <div className="row gap-8" style={{ marginTop: 18 }}>
                <button
                  type="button"
                  className="btn btn--primary"
                  onClick={() => {
                    setDetail(null)
                    setEditorId(detail.id)
                  }}
                >
                  {t('common.edit')}
                </button>
                <button
                  type="button"
                  className="btn btn--ghost"
                  onClick={() => {
                    setDetail(null)
                    setPosting(detail)
                  }}
                >
                  {t('inventory.action.post')}
                </button>
              </div>
            ) : null}
          </div>
        ) : null}
      </Drawer>

      <ConfirmDialog
        open={posting !== null}
        title={t('inventory.action.post')}
        body={
          posting ? t('inventory.confirm.post', { docNo: posting.docNo }) : ''
        }
        confirmLabel={t('inventory.action.post')}
        busy={post.isPending}
        onCancel={() => setPosting(null)}
        onConfirm={() => {
          if (posting) {
            post.mutate(posting.id)
          }
          setPosting(null)
        }}
      />

      <ConfirmDialog
        open={reversing !== null}
        title={t('inventory.action.reverse')}
        body={reversing ? t('inventory.confirm.reverse', { docNo: reversing.docNo }) : ''}
        confirmLabel={t('inventory.action.reverse')}
        danger
        busy={reverse.isPending}
        onCancel={() => setReversing(null)}
        onConfirm={() => {
          if (reversing) {
            reverse.mutate(reversing.id)
          }
          setReversing(null)
        }}
      />
    </>
  )
}
