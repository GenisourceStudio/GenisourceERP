import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

import { currentLocale, changeLocale } from '../shared/i18n'
import { useAuthStore } from '../shared/auth/store'
import { findNavItem, NAV } from './nav'

export default function AppShell() {
  const { t, i18n } = useTranslation()
  const location = useLocation()
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)

  const current = findNavItem(location.pathname)
  const title = current ? t(`nav.${current.key}`) : t('app.name')
  const locale = currentLocale()

  const toggleLocale = () => {
    changeLocale(locale === 'zh-CN' ? 'en-US' : 'zh-CN')
    // Force a re-render of consumers that read i18n.language through the hook.
    void i18n.changeLanguage(locale === 'zh-CN' ? 'en-US' : 'zh-CN')
  }

  return (
    <div className="shell">
      <aside className="rail">
        <div className="rail__brand">
          <strong>Genisource</strong>
          <span>ERP</span>
        </div>

        <nav className="rail__nav">
          {NAV.map((group) => (
            <div key={group.key} className="rail__group">
              <div className="rail__group-label">{t(`nav.group.${group.key}`)}</div>
              {group.items.map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  end={item.path === '/'}
                  className={({ isActive }) =>
                    [
                      'rail__link',
                      isActive ? 'rail__link--active' : '',
                      item.milestone && !isActive ? 'rail__link--muted' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')
                  }
                >
                  <span>{t(`nav.${item.key}`)}</span>
                  {item.milestone ? <span className="rail__pending">{item.milestone}</span> : null}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="rail__footer">
          <div className="eyebrow">{t('common.signedInAs')}</div>
          <div style={{ marginTop: 4, fontSize: 13, fontWeight: 600 }}>
            {user?.displayName ?? '—'}
          </div>
          <div className="mono" style={{ fontSize: 11, color: 'var(--muted-2)', marginTop: 2 }}>
            {user?.username ?? ''}
          </div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="topbar__title">
            <h1>{title}</h1>
            <span>{t('app.milestone')}</span>
          </div>

          <div className="topbar__actions">
            <button type="button" className="btn btn--ghost btn--sm" onClick={toggleLocale}>
              {locale === 'zh-CN' ? 'EN' : '中文'}
            </button>
            <button type="button" className="btn btn--sm" onClick={logout}>
              {t('common.logout')}
            </button>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
