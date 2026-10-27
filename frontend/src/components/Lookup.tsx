import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

export interface LookupOption {
  id: number
  code: string
  name: string
}

export function optionLabel(option: LookupOption | null): string {
  return option ? `${option.code} · ${option.name}` : ''
}

interface LookupProps {
  value: LookupOption | null
  onChange: (option: LookupOption | null) => void
  search: (query: string) => Promise<LookupOption[]>
  placeholder?: string
  disabled?: boolean
  /** Element id so the surrounding <label> can point at the input. */
  id?: string
}

/**
 * Type-ahead picker for item / partner / work-centre references.
 *
 * <p>Never loads the whole master file: it always asks the server for a search term, so a
 * factory with 30 000 materials still gets an instant combobox. Debounced at 220 ms and
 * keyboard navigable, because entering a BOM with a mouse only is painful.
 */
export default function Lookup({ value, onChange, search, placeholder, disabled, id }: LookupProps) {
  const { t } = useTranslation()
  const generatedId = useId()
  const inputId = id ?? `lookup-${generatedId}`

  const [query, setQuery] = useState('')
  const [options, setOptions] = useState<LookupOption[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [activeIndex, setActiveIndex] = useState(0)
  const containerRef = useRef<HTMLDivElement | null>(null)

  const display = open ? query : optionLabel(value)

  useEffect(() => {
    if (!open) {
      return
    }
    let cancelled = false
    setLoading(true)
    const timer = window.setTimeout(() => {
      search(query)
        .then((result) => {
          if (!cancelled) {
            setOptions(result)
            setActiveIndex(0)
          }
        })
        .catch(() => {
          if (!cancelled) {
            setOptions([])
          }
        })
        .finally(() => {
          if (!cancelled) {
            setLoading(false)
          }
        })
    }, 220)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [query, open, search])

  useEffect(() => {
    const handler = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const select = (option: LookupOption) => {
    onChange(option)
    setOpen(false)
    setQuery('')
  }

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setActiveIndex((index) => Math.min(index + 1, options.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((index) => Math.max(index - 1, 0))
    } else if (event.key === 'Enter') {
      if (open && options[activeIndex]) {
        event.preventDefault()
        select(options[activeIndex])
      }
    } else if (event.key === 'Escape') {
      setOpen(false)
      setQuery('')
    }
  }

  return (
    <div className="lookup" ref={containerRef}>
      <input
        id={inputId}
        className="input"
        value={display}
        placeholder={placeholder ?? t('common.searchPlaceholder')}
        disabled={disabled}
        autoComplete="off"
        onFocus={() => {
          setOpen(true)
          setQuery('')
        }}
        onChange={(event) => {
          setQuery(event.target.value)
          setOpen(true)
          if (value) {
            onChange(null)
          }
        }}
        onKeyDown={onKeyDown}
      />
      {open ? (
        <div className="lookup__menu">
          {loading ? <div className="lookup__empty">{t('common.loading')}</div> : null}
          {!loading && options.length === 0 ? (
            <div className="lookup__empty">{t('common.noMatch')}</div>
          ) : null}
          {options.map((option, index) => (
            <button
              key={option.id}
              type="button"
              className={
                index === activeIndex ? 'lookup__item lookup__item--active' : 'lookup__item'
              }
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => select(option)}
            >
              <span className="lookup__code">{option.code}</span>
              <span>{option.name}</span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}
