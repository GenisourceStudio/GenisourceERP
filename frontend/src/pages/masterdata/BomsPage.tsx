import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

import BomsPanel from './BomsPanel'
import RoutingsPanel from './RoutingsPanel'
import WorkCentersPanel from './WorkCentersPanel'

type Tab = 'bom' | 'routing' | 'workCenter'

const TABS: Tab[] = ['bom', 'routing', 'workCenter']

/**
 * BOM, routings and work centres share one screen because they are edited together:
 * a routing cannot be written without a work centre, and costing a BOM needs the routings.
 * Keeping them as tabs avoids three nav entries that are always visited in sequence.
 *
 * <p>The active tab lives in the query string so a link can point straight at work
 * centres — useful when someone asks "add a machine" and needs a URL, not instructions.
 */
export default function BomsPage() {
  const { t } = useTranslation()
  const [params, setParams] = useSearchParams()

  const requested = params.get('tab') as Tab | null
  const tab: Tab = requested && TABS.includes(requested) ? requested : 'bom'

  const select = (next: Tab) => {
    const updated = new URLSearchParams(params)
    if (next === 'bom') {
      updated.delete('tab')
    } else {
      updated.set('tab', next)
    }
    setParams(updated, { replace: true })
  }

  return (
    <section className="panel">
      <div className="tabs" style={{ padding: '0 14px', marginBottom: 0, borderBottom: 0 }}>
        <button
          type="button"
          className={tab === 'bom' ? 'tabs__btn tabs__btn--active' : 'tabs__btn'}
          onClick={() => select('bom')}
        >
          {t('nav.bom')}
        </button>
        <button
          type="button"
          className={tab === 'routing' ? 'tabs__btn tabs__btn--active' : 'tabs__btn'}
          onClick={() => select('routing')}
        >
          {t('bom.tab.routing')}
        </button>
        <button
          type="button"
          className={tab === 'workCenter' ? 'tabs__btn tabs__btn--active' : 'tabs__btn'}
          onClick={() => select('workCenter')}
        >
          {t('bom.tab.workCenter')}
        </button>
      </div>

      {tab === 'bom' ? <BomsPanel /> : null}
      {tab === 'routing' ? <RoutingsPanel /> : null}
      {tab === 'workCenter' ? <WorkCentersPanel /> : null}
    </section>
  )
}
