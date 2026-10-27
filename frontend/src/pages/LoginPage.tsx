import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { currentLocale, changeLocale } from '../shared/i18n'
import { ApiError } from '../shared/api/client'
import { useAuthStore } from '../shared/auth/store'

export default function LoginPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const login = useAuthStore((state) => state.login)

  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const locale = currentLocale()

  const toggleLocale = () => {
    const next = locale === 'zh-CN' ? 'en-US' : 'zh-CN'
    changeLocale(next)
    void i18n.changeLanguage(next)
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(username.trim(), password)
      navigate('/', { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
      } else {
        setError(t('login.network'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login">
      <aside className="login__aside">
        <div className="login__mark">
          Genisource <span>ERP</span>
        </div>

        <div className="login__pitch">
          <h2>{t('app.tagline')}</h2>
          <p>{t('dashboard.scope.body')}</p>
          <div className="login__flow">
            <span>BOM</span>
            <span>MRP</span>
            <span>Order</span>
            <span>Stock</span>
            <span>Ledger</span>
            <span>MES</span>
          </div>
        </div>

        <div className="login__meta">Apache-2.0 · M0 skeleton</div>
      </aside>

      <section className="login__panel">
        <form className="login__form" onSubmit={onSubmit}>
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <h1>{t('login.title')}</h1>
              <p>{t('login.subtitle')}</p>
            </div>
            <button type="button" className="btn btn--ghost btn--sm" onClick={toggleLocale}>
              {locale === 'zh-CN' ? 'EN' : '中文'}
            </button>
          </div>

          <div className="login__fields">
            <div className="field">
              <label htmlFor="username">{t('login.username')}</label>
              <input
                id="username"
                className="input"
                autoComplete="username"
                autoFocus
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                required
              />
            </div>

            <div className="field">
              <label htmlFor="password">{t('login.password')}</label>
              <input
                id="password"
                className="input"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </div>

            {error ? (
              <div className="form-error" role="alert">
                {error}
              </div>
            ) : null}

            <button
              type="submit"
              className="btn btn--primary btn--block"
              disabled={submitting || password.length === 0}
            >
              {submitting ? t('login.submitting') : t('login.submit')}
            </button>
          </div>

          <div className="login__hint">{t('login.hint')}</div>
        </form>
      </section>
    </div>
  )
}
