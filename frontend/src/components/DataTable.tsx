import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

export interface Column<T> {
  key: string
  header: ReactNode
  align?: 'left' | 'right'
  width?: string
  /** Rendered cell content; returning null leaves an em dash so columns never look empty. */
  render: (row: T) => ReactNode
}

interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  loading?: boolean
  error?: string | null
  emptyTitle?: string
  emptyBody?: string
  onRowClick?: (row: T) => void
  activeKey?: string | number | null
}

/**
 * Dense read-only table used by every list screen.
 *
 * <p>Loading, empty and error states live here on purpose: a table that renders nothing
 * while a request is in flight reads as "this screen is broken", which is the single most
 * common complaint about internal tools.
 */
export default function DataTable<T>({
  columns,
  rows,
  rowKey,
  loading,
  error,
  emptyTitle,
  emptyBody,
  onRowClick,
  activeKey,
}: DataTableProps<T>) {
  const { t } = useTranslation()

  if (error) {
    return (
      <div className="empty">
        <div className="empty__mark">!</div>
        <h3>{t('common.loadFailed')}</h3>
        <p>{error}</p>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="empty">
        <div className="empty__mark">···</div>
        <p>{t('common.loading')}</p>
      </div>
    )
  }

  if (rows.length === 0) {
    return (
      <div className="empty">
        <div className="empty__mark">∅</div>
        <h3>{emptyTitle ?? t('common.emptyTitle')}</h3>
        <p>{emptyBody ?? t('common.emptyBody')}</p>
      </div>
    )
  }

  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                style={{ width: column.width, textAlign: column.align === 'right' ? 'right' : 'left' }}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const key = rowKey(row)
            const active = activeKey !== undefined && activeKey !== null && activeKey === key
            return (
              <tr
                key={key}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                style={{
                  cursor: onRowClick ? 'pointer' : undefined,
                  background: active ? 'var(--orange-soft)' : undefined,
                }}
              >
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={column.align === 'right' ? 'num' : undefined}
                    style={{ textAlign: column.align === 'right' ? 'right' : 'left' }}
                  >
                    {column.render(row) ?? '—'}
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
