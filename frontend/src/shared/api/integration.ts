import { apiFetch } from './client'

export interface MesOutboxView {
  id: number
  messageType: string
  subject: string
  status: string
  attempts: number
  lastError?: string
  nextAttemptAt: string
  sentAt?: string
}

/**
 * The messages waiting to be delivered to MES.
 *
 * <p>A delivery failure is invisible from the ERP's own screens — the work order was
 * released either way — so the outbox is the only place a person can see that MES is
 * not hearing about something. It is a small screen for a small problem, but it is
 * the difference between "MES is down" and "we do not know why MES is down".
 */
export function listMesOutbox(): Promise<MesOutboxView[]> {
  return apiFetch<MesOutboxView[]>('/api/integration/mes/outbox')
}

export function retryMesMessage(id: number): Promise<void> {
  return apiFetch<void>(`/api/integration/mes/outbox/${id}/retry`, { method: 'POST' })
}
