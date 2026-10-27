import { useTranslation } from 'react-i18next'

interface PagerProps {
  page: number
  size: number
  total: number
  pageCount: number
  onChange: (page: number) => void
}

/**
 * Pager for server-side paging.
 *
 * <p>Shows "第 x / y 页 · 共 n 条" rather than an ellipsis of page numbers: with 20 rows a
 * page a factory rarely has more than a handful of pages, and knowing the total is more
 * useful than jumping.
 */
export default function Pager({ page, size, total, pageCount, onChange }: PagerProps) {
  const { t } = useTranslation()
  const from = total === 0 ? 0 : page * size + 1
  const to = Math.min((page + 1) * size, total)

  return (
    <div className="pager">
      <span>
        {t('common.rangeOfTotal', { from, to, total })}
      </span>
      <div className="pager__controls">
        <button
          type="button"
          className="pager__btn"
          disabled={page <= 0}
          onClick={() => onChange(0)}
          aria-label={t('common.firstPage')}
        >
          «
        </button>
        <button
          type="button"
          className="pager__btn"
          disabled={page <= 0}
          onClick={() => onChange(page - 1)}
        >
          {t('common.prev')}
        </button>
        <span style={{ padding: '0 6px' }}>
          {page + 1} / {Math.max(pageCount, 1)}
        </span>
        <button
          type="button"
          className="pager__btn"
          disabled={page + 1 >= pageCount}
          onClick={() => onChange(page + 1)}
        >
          {t('common.next')}
        </button>
        <button
          type="button"
          className="pager__btn"
          disabled={page + 1 >= pageCount}
          onClick={() => onChange(pageCount - 1)}
          aria-label={t('common.lastPage')}
        >
          »
        </button>
      </div>
    </div>
  )
}
