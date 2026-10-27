import { useTranslation } from 'react-i18next'

import type { RecordStatus, RevisionStatus } from '../shared/api/masterdata'

/** Status chips share one visual grammar: green = live, amber = in progress, grey = retired. */
export function RecordTag({ status }: { status: RecordStatus }) {
  const { t } = useTranslation()
  return (
    <span className={status === 'ACTIVE' ? 'tag tag--active' : 'tag tag--inactive'}>
      {t(`status.record.${status}`)}
    </span>
  )
}

export function RevisionTag({ status }: { status: RevisionStatus }) {
  const { t } = useTranslation()
  return (
    <span className={`tag tag--${status.toLowerCase()}`}>{t(`status.revision.${status}`)}</span>
  )
}

export function TypeTag({ label }: { label: string }) {
  return <span className="tag">{label}</span>
}
