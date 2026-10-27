import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  const { t } = useTranslation()

  return (
    <section className="panel" style={{ maxWidth: 520, margin: '64px auto' }}>
      <div className="empty">
        <div className="empty__mark">404</div>
        <h3>{t('error.notFoundTitle')}</h3>
        <p>{t('error.notFoundBody')}</p>
        <Link className="btn btn--primary btn--sm" to="/">
          {t('nav.dashboard')}
        </Link>
      </div>
    </section>
  )
}
