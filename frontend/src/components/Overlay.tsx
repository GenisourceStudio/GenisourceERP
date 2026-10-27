import { useEffect, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Side panel for editing a record, and a small centred dialog for confirmations.
 *
 * <p>Both lock the background scroll and close on Escape, because an ERP operator often
 * works with a keyboard and getting stuck in a panel with no visible way out is the
 * fastest way to lose trust in the tool.
 */

function useEscape(active: boolean, onClose: () => void) {
  useEffect(() => {
    if (!active) {
      return
    }
    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', handler)
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', handler)
      document.body.style.overflow = previous
    }
  }, [active, onClose])
}

interface DrawerProps {
  open: boolean
  title: ReactNode
  subtitle?: ReactNode
  wide?: boolean
  onClose: () => void
  footer?: ReactNode
  children: ReactNode
}

export function Drawer({ open, title, subtitle, wide, onClose, footer, children }: DrawerProps) {
  useEscape(open, onClose)
  if (!open) {
    return null
  }
  return (
    <>
      <div className="drawer__scrim" onClick={onClose} role="presentation" />
      <aside className={wide ? 'drawer drawer--wide' : 'drawer'} role="dialog" aria-modal="true">
        <header className="drawer__head">
          <div style={{ flex: 1, minWidth: 0 }}>
            <h2>{title}</h2>
            {subtitle ? <p>{subtitle}</p> : null}
          </div>
          <button type="button" className="btn btn--ghost btn--sm" onClick={onClose}>
            ✕
          </button>
        </header>
        <div className="drawer__body">{children}</div>
        {footer ? <footer className="drawer__foot">{footer}</footer> : null}
      </aside>
    </>
  )
}

interface ConfirmDialogProps {
  open: boolean
  title: ReactNode
  body: ReactNode
  confirmLabel?: string
  danger?: boolean
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  danger,
  busy,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const { t } = useTranslation()
  useEscape(open, onCancel)
  if (!open) {
    return null
  }
  return (
    <>
      <div className="modal__scrim" onClick={onCancel} role="presentation" />
      <div className="modal" role="alertdialog" aria-modal="true">
        <div className="modal__body">
          <h3>{title}</h3>
          <p>{body}</p>
        </div>
        <div className="modal__foot">
          <button type="button" className="btn btn--ghost btn--sm" onClick={onCancel} disabled={busy}>
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className={danger ? 'btn btn--danger btn--sm' : 'btn btn--primary btn--sm'}
            onClick={onConfirm}
            disabled={busy}
          >
            {confirmLabel ?? t('common.confirm')}
          </button>
        </div>
      </div>
    </>
  )
}
