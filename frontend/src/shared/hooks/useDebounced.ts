import { useEffect, useState } from 'react'

/**
 * Delays a rapidly changing value.
 *
 * <p>Used by the list toolbars so typing a material name does not fire a query per
 * keystroke. 300 ms is long enough to coalesce typing and short enough to still feel
 * immediate.
 */
export function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])

  return debounced
}
