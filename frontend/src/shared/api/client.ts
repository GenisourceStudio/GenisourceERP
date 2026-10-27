/**
 * Thin fetch wrapper around the Spring API.
 *
 * The access token lives in a module variable rather than being read from the store on
 * every call: the store persists to localStorage, and touching it inside the fetch path
 * would couple every request to React state. The auth store keeps both in sync.
 */
let accessToken: string | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  /** Per-field validation messages, keyed by the field name in the request payload. */
  readonly fields: Record<string, string>

  constructor(status: number, code: string, detail: string, fields: Record<string, string> = {}) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }

  /** First usable message, preferring field-level feedback over the generic detail. */
  get firstMessage(): string {
    const fieldMessages = Object.values(this.fields)
    return fieldMessages.length > 0 ? fieldMessages[0] : this.message
  }
}

interface ProblemDetail {
  detail?: string
  code?: string
  title?: string
  fields?: Record<string, string>
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(path, { ...init, headers })

  if (!response.ok) {
    let problem: ProblemDetail | null = null
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      problem = null
    }
    throw new ApiError(
      response.status,
      problem?.code ?? 'unknown',
      problem?.detail ?? response.statusText,
      problem?.fields ?? {},
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
