import { apiFetch } from './client'

/**
 * Typed client for the administration screens: accounts, roles and the audit trail.
 *
 * <p>Three resources, one file, because the role form cannot be drawn without the
 * permission catalogue and the user form cannot be drawn without the roles — they are
 * fetched together and they change together.
 */

export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

export interface UserView {
  id: number
  username: string
  displayName: string
  email: string | null
  phone: string | null
  status: AccountStatus
  forcePasswordChange: boolean
  lastLoginAt: string | null
  roles: string[]
  version: number
}

export interface RoleView {
  id: number
  code: string
  name: string
  description: string | null
  builtin: boolean
  userCount: number
  permissions: string[]
}

export interface PermissionView {
  code: string
  name: string
  module: string
  type: string
}

export interface AuditView {
  id: number
  username: string | null
  action: string
  resourceType: string | null
  resourceId: string | null
  detail: string | null
  ipAddress: string | null
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  total: number
  totalPages: number
  hasNext: boolean
}

export interface UserQuery {
  q?: string
  status?: AccountStatus
  page?: number
  size?: number
}

export interface AuditQuery {
  action?: string
  username?: string
  resourceType?: string
  resourceId?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

function query(params: object): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params as Record<string, unknown>)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function listUsers(params: UserQuery = {}): Promise<PageResponse<UserView>> {
  return apiFetch(`/api/system/users${query(params)}`)
}

export function createUser(request: {
  username: string
  password: string
  displayName: string
  email?: string
  phone?: string
  roles: string[]
}): Promise<UserView> {
  return apiFetch('/api/system/users', { method: 'POST', body: JSON.stringify(request) })
}

export function updateUser(
  id: number,
  request: {
    displayName: string
    email?: string
    phone?: string
    status: AccountStatus
    roles: string[]
  },
): Promise<UserView> {
  return apiFetch(`/api/system/users/${id}`, { method: 'PUT', body: JSON.stringify(request) })
}

export function resetPassword(id: number, password: string): Promise<void> {
  return apiFetch(`/api/system/users/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ password }),
  })
}

export function listRoles(): Promise<RoleView[]> {
  return apiFetch('/api/system/roles')
}

export function listPermissions(): Promise<PermissionView[]> {
  return apiFetch('/api/system/permissions')
}

export function createRole(request: {
  code: string
  name: string
  description?: string
  permissions: string[]
}): Promise<RoleView> {
  return apiFetch('/api/system/roles', { method: 'POST', body: JSON.stringify(request) })
}

export function updateRole(
  id: number,
  request: { name: string; description?: string; permissions: string[] },
): Promise<RoleView> {
  return apiFetch(`/api/system/roles/${id}`, { method: 'PUT', body: JSON.stringify(request) })
}

export function deleteRole(id: number): Promise<void> {
  return apiFetch(`/api/system/roles/${id}/delete`, { method: 'POST' })
}

export function listAudit(params: AuditQuery = {}): Promise<PageResponse<AuditView>> {
  return apiFetch(`/api/system/audit${query(params)}`)
}
