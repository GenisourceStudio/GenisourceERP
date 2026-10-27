import { useTranslation } from 'react-i18next'

import { useAuthStore } from '../shared/auth/store'

export default function DashboardPage() {
  const { t } = useTranslation()
  const user = useAuthStore((state) => state.user)

  const delivered = [
    t('dashboard.delivered.tenant'),
    t('dashboard.delivered.auth'),
    t('dashboard.delivered.schema'),
    t('dashboard.delivered.deploy'),
  ]

  return (
    <div className="stack gap-16">
      <div className="grid grid--4">
        <div className="stat">
          <div className="stat__label">{t('dashboard.card.tenant')}</div>
          <div className="stat__value">{user?.tenantCode ?? 'default'}</div>
          <div className="stat__hint">tenant_id · row level</div>
        </div>
        <div className="stat">
          <div className="stat__label">{t('dashboard.card.account')}</div>
          <div className="stat__value">{user?.username ?? '—'}</div>
          <div className="stat__hint">{user?.displayName ?? ''}</div>
        </div>
        <div className="stat">
          <div className="stat__label">{t('dashboard.card.role')}</div>
          <div className="stat__value">{user?.roles.length ?? 0}</div>
          <div className="stat__hint">{user?.roles.join(' · ') ?? ''}</div>
        </div>
        <div className="stat">
          <div className="stat__label">{t('dashboard.card.permission')}</div>
          <div className="stat__value">{user?.permissions.length ?? 0}</div>
          <div className="stat__hint">RBAC · JWT claims</div>
        </div>
      </div>

      <div className="grid grid--2">
        <section className="panel">
          <div className="panel__header">
            <h2>{t('dashboard.delivered.title')}</h2>
            <span className="badge badge--ok">M1</span>
          </div>
          <div className="panel__body">
            <ul style={{ margin: 0, paddingLeft: 18, lineHeight: 1.9 }}>
              {delivered.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </div>
        </section>

        <section className="panel">
          <div className="panel__header">
            <h2>{t('dashboard.next.title')}</h2>
            <span className="badge badge--accent">M2</span>
          </div>
          <div className="panel__body">
            <p style={{ margin: 0, lineHeight: 1.75 }} className="muted">
              {t('dashboard.next.body')}
            </p>
            <p style={{ margin: '14px 0 0', lineHeight: 1.75 }} className="muted text-sm">
              {t('dashboard.scope.body')}
            </p>
          </div>
        </section>
      </div>
    </div>
  )
}
