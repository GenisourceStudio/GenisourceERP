import { useTranslation } from 'react-i18next'

interface PlaceholderPageProps {
  moduleKey: string
  milestone: string
}

/**
 * Honest placeholder for modules that have not shipped yet. It names the module and the
 * milestone that will deliver it, instead of showing an empty table that looks broken.
 */
export default function PlaceholderPage({ moduleKey, milestone }: PlaceholderPageProps) {
  const { t } = useTranslation()

  return (
    <section className="panel">
      <div className="empty">
        <div className="empty__mark">{milestone || '—'}</div>
        <h3>{t('page.placeholder.title')}</h3>
        <p>
          {t('page.placeholder.body', {
            module: t(moduleKey),
            milestone: milestone || '—',
          })}
        </p>
        <span className="badge">{t('common.planned')}</span>
      </div>
    </section>
  )
}
