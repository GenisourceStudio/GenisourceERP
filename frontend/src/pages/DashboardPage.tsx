import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'

import { Icon } from '../shared/ui/Icon'
import { useAuthStore } from '../shared/auth/store'
import { listItems } from '../shared/api/masterdata'
import { listStock } from '../shared/api/inventory'
import { listReceivables, listPayables } from '../shared/api/finance'
import { listProductionOrders } from '../shared/api/production'
import { BUILD_MILESTONE, BUILD_VERSION } from '../app/build'
import { NAV_ITEMS } from '../app/nav'

/**
 * The workbench shows what is actually on the books, not what the build
 * contains. A planner opening the console wants to know whether anything is
 * waiting for them; "you have 47 permissions" does not answer that.
 */
export default function DashboardPage() {
  const { t, i18n } = useTranslation()
  const user = useAuthStore((state) => state.user)

  const items = useQuery({ queryKey: ['dash-items'], queryFn: () => listItems({ size: 1 }) })
  const stock = useQuery({ queryKey: ['dash-stock'], queryFn: () => listStock({ size: 1 }) })
  const orders = useQuery({
    queryKey: ['dash-orders'],
    queryFn: () => listProductionOrders({ size: 1 }),
  })
  const receivables = useQuery({ queryKey: ['dash-ar'], queryFn: () => listReceivables({ size: 1 }) })
  const payables = useQuery({ queryKey: ['dash-ap'], queryFn: () => listPayables({ size: 1 }) })

  const money = (value: number) =>
    new Intl.NumberFormat(i18n.language === 'en-US' ? 'en-US' : 'zh-CN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value)

  const cards = [
    {
      icon: 'cube' as const,
      label: t('dashboard.metric.items'),
      value: items.data?.total?.toLocaleString() ?? '—',
      hint: t('dashboard.metric.itemsHint'),
    },
    {
      icon: 'package' as const,
      label: t('dashboard.metric.stock'),
      value: stock.data?.total?.toLocaleString() ?? '—',
      hint: t('dashboard.metric.stockHint'),
    },
    {
      icon: 'factory' as const,
      label: t('dashboard.metric.orders'),
      value: orders.data?.total?.toLocaleString() ?? '—',
      hint: t('dashboard.metric.ordersHint'),
    },
    {
      icon: 'arrow-down-left' as const,
      label: t('dashboard.metric.receivable'),
      value: receivables.data ? money(receivables.data.summary.balance) : '—',
      hint: t('dashboard.metric.receivableHint', {
        count: receivables.data?.summary.openCount ?? 0,
      }),
    },
    {
      icon: 'arrow-up-right' as const,
      label: t('dashboard.metric.payable'),
      value: payables.data ? money(payables.data.summary.balance) : '—',
      hint: t('dashboard.metric.payableHint', {
        count: payables.data?.summary.openCount ?? 0,
      }),
    },
  ]

  const delivered = [
    t('dashboard.delivered.tenant'),
    t('dashboard.delivered.auth'),
    t('dashboard.delivered.schema'),
    t('dashboard.delivered.deploy'),
  ]

  return (
    <div className="stack gap-16">
      <section className="hero">
        <div className="hero__lead">
          <span className="hero__crumb">
            <span className="hero__bullet" aria-hidden="true" />
            {t('app.name')} / {BUILD_MILESTONE}
          </span>
          <h2>{t('dashboard.hero.title')}</h2>
          <p className="hero__pitch">{t('dashboard.hero.pitch')}</p>
        </div>
        <span className="hero__stamp" aria-hidden="true">
          {BUILD_MILESTONE} {BUILD_VERSION}
        </span>
        <dl className="hero__spec">
          <div>
            <dt>{t('dashboard.card.tenant')}</dt>
            <dd className="mono">{user?.tenantCode ?? 'default'}</dd>
          </div>
          <div>
            <dt>{t('dashboard.card.account')}</dt>
            <dd className="mono">{user?.username ?? '—'}</dd>
          </div>
          <div>
            <dt>{t('shell.modules')}</dt>
            <dd className="mono">{String(NAV_ITEMS.length).padStart(2, '0')}</dd>
          </div>
        </dl>
      </section>

      <div className="grid grid--4">
        {cards.map((card) => (
          <div key={card.label} className="stat">
            <div className="stat__label">
              <Icon name={card.icon} size={13} />
              {card.label}
            </div>
            <div className="stat__value">{card.value}</div>
            <div className="stat__hint">{card.hint}</div>
          </div>
        ))}
      </div>

      <div className="grid grid--2">
        <section className="panel">
          <div className="panel__header">
            <h2>{t('dashboard.delivered.title')}</h2>
            <span className="badge badge--ok">{BUILD_MILESTONE}</span>
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
            <span className="badge badge--accent">{t('dashboard.next.badge')}</span>
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
