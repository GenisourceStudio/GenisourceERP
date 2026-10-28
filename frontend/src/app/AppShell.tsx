import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

import { currentLocale, changeLocale } from '../shared/i18n'
import { useAuthStore } from '../shared/auth/store'
import { Icon } from '../shared/ui/Icon'
import { findNavItem, NAV, NAV_ITEMS } from './nav'
import { BUILD_MILESTONE, BUILD_VERSION } from './build'

/** Rail numbers are two digits, as on the MES console: 01 … 25. */
function railNumber(index: number): string {
  return String(index + 1).padStart(2, '0')
}

function useClock(): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  return now
}

export default function AppShell() {
  const { t, i18n } = useTranslation()
  const location = useLocation()
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const now = useClock()

  const current = findNavItem(location.pathname)
  const title = current ? t(`nav.${current.key}`) : t('app.name')
  const group = current ? NAV.find((g) => g.items.some((i) => i.path === current.path)) : undefined
  const locale = currentLocale()

  const toggleLocale = () => {
    const next = locale === 'zh-CN' ? 'en-US' : 'zh-CN'
    changeLocale(next)
    // Force a re-render of consumers that read i18n.language through the hook.
    void i18n.changeLanguage(next)
  }

  let counter = -1

  return (
    <div className="shell">
      <aside className="rail">
        <div className="rail__brand">
          <span className="rail__mark">G</span>
          <span className="rail__wordmark">
            <strong>GENISOURCE</strong>
            <span>ERP</span>
          </span>
        </div>

        <div className="rail__section">{t('shell.modules')}</div>

        <nav className="rail__nav">
          {NAV.map((g) => (
            <div key={g.key} className="rail__group">
              <div className="rail__group-label">{t(`nav.group.${g.key}`)}</div>
              {g.items.map((item) => {
                counter += 1
                const number = railNumber(counter)
                return (
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
                    <span className="rail__num">{number}</span>
                    <Icon name={item.icon} size={16} className="rail__icon" />
                    <span className="rail__label">{t(`nav.${item.key}`)}</span>
                    {item.milestone ? (
                      <span className="rail__pending">{item.milestone}</span>
                    ) : null}
                  </NavLink>
                )
              })}
            </div>
          ))}
        </nav>

        <div className="rail__footer">
          <div className="rail__stage">
            <span className="rail__stage-no">{BUILD_MILESTONE}</span>
            <span className="rail__stage-meta">
              {t('shell.moduleCount', { count: NAV_ITEMS.length })}
            </span>
          </div>
          <div className="rail__who">
            <span className="rail__dot" aria-hidden="true" />
            <span>{user?.displayName ?? '—'}</span>
          </div>
          <div className="rail__legal">
            APACHE-2.0 · {BUILD_VERSION}
          </div>
          <div className="rail__hazard" aria-hidden="true" />
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="topbar__lead">
            <span className="topbar__crumb">
              {group ? t(`nav.group.${group.key}`) : t('app.name')}
              <Icon name="chevron-down" size={12} className="topbar__crumb-sep" />
              {title}
            </span>
            <h1>{title}</h1>
          </div>

          <div className="topbar__actions">
            <span className="topbar__meta mono">{user?.tenantCode ?? ''}</span>
            <span className="topbar__clock mono">
              {now.toLocaleTimeString(locale === 'zh-CN' ? 'zh-CN' : 'en-US', { hour12: false })}
              <em>
                {now.toLocaleDateString(locale === 'zh-CN' ? 'zh-CN' : 'en-US', {
                  month: '2-digit',
                  day: '2-digit',
                })}
              </em>
            </span>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={toggleLocale}
              aria-label={t('common.language')}
            >
              {locale === 'zh-CN' ? 'EN' : '中文'}
            </button>
            <span className="topbar__user">
              <span className="topbar__user-name">{user?.displayName ?? '—'}</span>
              <span className="mono">{user?.username ?? ''}</span>
            </span>
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
