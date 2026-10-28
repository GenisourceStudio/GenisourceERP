import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import Pager from '../../components/Pager'
import { ApiError } from '../../shared/api/client'
import {
  createRole,
  createUser,
  deleteRole,
  listPermissions,
  listRoles,
  listUsers,
  resetPassword,
  updateRole,
  updateUser,
  type AccountStatus,
  type RoleView,
  type UserView,
} from '../../shared/api/system'
import { useAuthStore } from '../../shared/auth/store'

/**
 * Accounts and roles.
 *
 * <p>One screen holding both, because a user row is meaningless without the roles it
 * can be granted and a role is meaningless without the users holding it. The two
 * panels share the role list, so granting a role here immediately shows up in the
 * role panel's holder count.
 */
export default function UsersPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const currentUsername = useAuthStore((state) => state.user?.username)
  const canWrite = permissions?.includes('sys:user:write') ?? false
  const canWriteRoles = permissions?.includes('sys:role:write') ?? false

  const [tab, setTab] = useState<'users' | 'roles'>('users')
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<AccountStatus | ''>('')
  const [page, setPage] = useState(0)

  const [editing, setEditing] = useState<UserView | 'new' | null>(null)
  const [passwordTarget, setPasswordTarget] = useState<UserView | null>(null)
  const [roleEditing, setRoleEditing] = useState<RoleView | 'new' | null>(null)
  const [roleDeleting, setRoleDeleting] = useState<RoleView | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const PAGE_SIZE = 20

  // --- form state, kept outside the drawer so it survives a re-render --------
  const [draftUser, setDraftUser] = useState({
    username: '',
    password: '',
    displayName: '',
    email: '',
    phone: '',
    status: 'ACTIVE' as AccountStatus,
    roles: [] as string[],
  })
  const [newPassword, setNewPassword] = useState('')
  const [roleDraft, setRoleDraft] = useState({
    code: '',
    name: '',
    description: '',
    permissions: [] as string[],
  })

  const usersQuery = useQuery({
    queryKey: ['system-users', keyword, status, page],
    queryFn: () => listUsers({ q: keyword, status: status || undefined, page, size: PAGE_SIZE }),
  })
  const rolesQuery = useQuery({ queryKey: ['system-roles'], queryFn: listRoles })
  const permissionQuery = useQuery({
    queryKey: ['system-permissions'],
    queryFn: listPermissions,
    enabled: roleEditing !== null,
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['system-users'] })
    void queryClient.invalidateQueries({ queryKey: ['system-roles'] })
  }

  const fail = (error: unknown) => {
    setActionError(error instanceof ApiError ? error.firstMessage : t('common.saveFailed'))
  }

  const saveUser = useMutation({
    mutationFn: () => {
      const draft = draftUser
      if (editing === 'new') {
        return createUser({
          username: draft.username,
          password: draft.password,
          displayName: draft.displayName,
          email: draft.email || undefined,
          phone: draft.phone || undefined,
          roles: draft.roles,
        })
      }
      if (!editing) throw new Error('no target')
      return updateUser(editing.id, {
        displayName: draft.displayName,
        email: draft.email || undefined,
        phone: draft.phone || undefined,
        status: draft.status,
        roles: draft.roles,
      })
    },
    onSuccess: () => {
      setEditing(null)
      setActionError(null)
      invalidate()
    },
    onError: fail,
  })

  const resetMutation = useMutation({
    mutationFn: () => resetPassword(passwordTarget!.id, newPassword),
    onSuccess: () => {
      setPasswordTarget(null)
      setNewPassword('')
      setActionError(null)
      invalidate()
    },
    onError: fail,
  })

  const saveRole = useMutation({
    mutationFn: () => {
      if (roleEditing === 'new') {
        return createRole({
          code: roleDraft.code,
          name: roleDraft.name,
          description: roleDraft.description || undefined,
          permissions: roleDraft.permissions,
        })
      }
      if (!roleEditing) throw new Error('no target')
      return updateRole(roleEditing.id, {
        name: roleDraft.name,
        description: roleDraft.description || undefined,
        permissions: roleDraft.permissions,
      })
    },
    onSuccess: () => {
      setRoleEditing(null)
      setActionError(null)
      invalidate()
    },
    onError: fail,
  })

  const removeRole = useMutation({
    mutationFn: () => deleteRole(roleDeleting!.id),
    onSuccess: () => {
      setRoleDeleting(null)
      setActionError(null)
      invalidate()
    },
    onError: fail,
  })

  const openUser = (user: UserView | 'new') => {
    setActionError(null)
    if (user === 'new') {
      setDraftUser({
        username: '',
        password: '',
        displayName: '',
        email: '',
        phone: '',
        status: 'ACTIVE',
        roles: [],
      })
    } else {
      setDraftUser({
        username: user.username,
        password: '',
        displayName: user.displayName,
        email: user.email ?? '',
        phone: user.phone ?? '',
        status: user.status,
        roles: [...user.roles],
      })
    }
    setEditing(user)
  }

  const openRole = (role: RoleView | 'new') => {
    setActionError(null)
    if (role === 'new') {
      setRoleDraft({ code: '', name: '', description: '', permissions: [] })
    } else {
      setRoleDraft({
        code: role.code,
        name: role.name,
        description: role.description ?? '',
        permissions: [...role.permissions],
      })
    }
    setRoleEditing(role)
  }

  const toggleRole = (code: string) => {
    setDraftUser((current) => ({
      ...current,
      roles: current.roles.includes(code)
        ? current.roles.filter((value) => value !== code)
        : [...current.roles, code],
    }))
  }

  const togglePermission = (code: string) => {
    setRoleDraft((current) => ({
      ...current,
      permissions: current.permissions.includes(code)
        ? current.permissions.filter((value) => value !== code)
        : [...current.permissions, code],
    }))
  }

  const groupedPermissions = useMemo(() => {
    const groups = new Map<string, typeof permissionQuery.data>()
    for (const permission of permissionQuery.data ?? []) {
      const bucket = groups.get(permission.module) ?? []
      bucket.push(permission)
      groups.set(permission.module, bucket)
    }
    return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [permissionQuery.data])

  const formatTime = (value: string | null) =>
    value
      ? new Date(value).toLocaleString(i18n.language === 'en-US' ? 'en-US' : 'zh-CN', {
          dateStyle: 'short',
          timeStyle: 'short',
        })
      : '—'

  const userColumns: Column<UserView>[] = [
    {
      key: 'username',
      header: t('system.user.username'),
      width: '160px',
      render: (user) => (
        <span className="mono" style={{ fontWeight: 600 }}>
          {user.username}
        </span>
      ),
    },
    {
      key: 'displayName',
      header: t('system.user.displayName'),
      render: (user) => (
        <div>
          <div style={{ fontWeight: 600 }}>{user.displayName}</div>
          {user.email ? <div className="hint">{user.email}</div> : null}
        </div>
      ),
    },
    {
      key: 'roles',
      header: t('system.user.roles'),
      width: '220px',
      render: (user) => (
        <div className="tag-row">
          {user.roles.length ? (
            user.roles.map((code) => (
              <span key={code} className="tag tag--neutral">
                {code}
              </span>
            ))
          ) : (
            <span className="hint">—</span>
          )}
        </div>
      ),
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '110px',
      render: (user) =>
        user.status === 'ACTIVE' ? (
          <span className="tag tag--ok">{t('system.user.status.ACTIVE')}</span>
        ) : (
          <span className="tag tag--warn">{t(`system.user.status.${user.status}`)}</span>
        ),
    },
    {
      key: 'lastLoginAt',
      header: t('system.user.lastLogin'),
      width: '150px',
      render: (user) => <span className="mono hint">{formatTime(user.lastLoginAt)}</span>,
    },
    {
      key: 'actions',
      header: '',
      width: '180px',
      align: 'right',
      render: (user) => (
        <div className="row-actions">
          {canWrite ? (
            <>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={(event) => {
                  event.stopPropagation()
                  openUser(user)
                }}
              >
                {t('common.edit')}
              </button>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={(event) => {
                  event.stopPropagation()
                  setActionError(null)
                  setNewPassword('')
                  setPasswordTarget(user)
                }}
              >
                {t('system.user.resetPassword')}
              </button>
            </>
          ) : null}
        </div>
      ),
    },
  ]

  const roleColumns: Column<RoleView>[] = [
    {
      key: 'code',
      header: t('system.role.code'),
      width: '150px',
      render: (role) => <span className="mono" style={{ fontWeight: 600 }}>{role.code}</span>,
    },
    {
      key: 'name',
      header: t('system.role.name'),
      render: (role) => (
        <div>
          <div style={{ fontWeight: 600 }}>
            {role.name}
            {role.builtin ? (
              <span className="tag tag--neutral" style={{ marginLeft: 8 }}>
                {t('system.role.builtin')}
              </span>
            ) : null}
          </div>
          {role.description ? <div className="hint">{role.description}</div> : null}
        </div>
      ),
    },
    {
      key: 'permissions',
      header: t('system.role.permissions'),
      width: '120px',
      align: 'right',
      render: (role) => <span className="mono">{role.permissions.length}</span>,
    },
    {
      key: 'userCount',
      header: t('system.role.holders'),
      width: '110px',
      align: 'right',
      render: (role) => <span className="mono">{role.userCount}</span>,
    },
    {
      key: 'actions',
      header: '',
      width: '170px',
      align: 'right',
      render: (role) => (
        <div className="row-actions">
          {canWriteRoles ? (
            <>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={(event) => {
                  event.stopPropagation()
                  openRole(role)
                }}
              >
                {t('common.edit')}
              </button>
              {!role.builtin ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={(event) => {
                    event.stopPropagation()
                    setActionError(null)
                    setRoleDeleting(role)
                  }}
                >
                  {t('common.delete')}
                </button>
              ) : null}
            </>
          ) : null}
        </div>
      ),
    },
  ]

  const result = usersQuery.data

  return (
    <>
      <div className="tabs">
        <button
          type="button"
          className={tab === 'users' ? 'tabs__btn tabs__btn--active' : 'tabs__btn'}
          onClick={() => setTab('users')}
        >
          {t('system.tab.users')}
        </button>
        <button
          type="button"
          className={tab === 'roles' ? 'tabs__btn tabs__btn--active' : 'tabs__btn'}
          onClick={() => setTab('roles')}
        >
          {t('system.tab.roles')}
        </button>
      </div>

      {actionError ? (
        <div style={{ padding: '10px 14px' }}>
          <div className="form-error">{actionError}</div>
        </div>
      ) : null}

      {tab === 'users' ? (
        <>
          <div className="toolbar">
            <div className="toolbar__search">
              <input
                className="input"
                value={keyword}
                placeholder={t('system.user.searchPlaceholder')}
                onChange={(event) => {
                  setKeyword(event.target.value)
                  setPage(0)
                }}
              />
            </div>
            <select
              className="select"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as AccountStatus | '')
                setPage(0)
              }}
            >
              <option value="">{t('common.allStatus')}</option>
              <option value="ACTIVE">{t('system.user.status.ACTIVE')}</option>
              <option value="DISABLED">{t('system.user.status.DISABLED')}</option>
              <option value="LOCKED">{t('system.user.status.LOCKED')}</option>
            </select>
            <div className="toolbar__spacer" />
            {canWrite ? (
              <button
                type="button"
                className="btn btn--primary btn--sm"
                onClick={() => openUser('new')}
              >
                {t('system.user.create')}
              </button>
            ) : null}
          </div>

          <DataTable
            columns={userColumns}
            rows={result?.content ?? []}
            rowKey={(user) => user.id}
            loading={usersQuery.isLoading}
            error={
              usersQuery.error instanceof ApiError ? usersQuery.error.message : null
            }
            emptyTitle={t('system.user.emptyTitle')}
            emptyBody={t('system.user.emptyBody')}
            onRowClick={canWrite ? (user) => openUser(user) : undefined}
          />

          {result ? (
            <Pager
              page={result.page}
              size={result.size}
              total={result.total}
              pageCount={result.totalPages}
              onChange={setPage}
            />
          ) : null}
        </>
      ) : (
        <>
          <div className="toolbar">
            <div className="toolbar__spacer" />
            {canWriteRoles ? (
              <button
                type="button"
                className="btn btn--primary btn--sm"
                onClick={() => openRole('new')}
              >
                {t('system.role.create')}
              </button>
            ) : null}
          </div>

          <DataTable
            columns={roleColumns}
            rows={rolesQuery.data ?? []}
            rowKey={(role) => role.id}
            loading={rolesQuery.isLoading}
            error={rolesQuery.error instanceof ApiError ? rolesQuery.error.message : null}
            emptyTitle={t('system.role.emptyTitle')}
            emptyBody={t('system.role.emptyBody')}
            onRowClick={canWriteRoles ? (role) => openRole(role) : undefined}
          />
        </>
      )}

      {/* ------------------------------------------------------------ user form */}
      <Drawer
        open={editing !== null}
        title={editing === 'new' ? t('system.user.createTitle') : t('system.user.editTitle')}
        subtitle={editing && editing !== 'new' ? editing.username : undefined}
        onClose={() => setEditing(null)}
        footer={
          <div className="row-actions">
            <button
              type="button"
              className="btn"
              onClick={() => setEditing(null)}
            >
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={saveUser.isPending}
              onClick={() => saveUser.mutate()}
            >
              {saveUser.isPending ? t('common.saving') : t('common.save')}
            </button>
          </div>
        }
      >
        <div className="form-grid">
          <div className="field">
            <label>{t('system.user.username')}</label>
            <input
              className="input mono"
              value={draftUser.username}
              disabled={editing !== 'new'}
              onChange={(event) =>
                setDraftUser({ ...draftUser, username: event.target.value })
              }
            />
          </div>
          <div className="field">
            <label>{t('system.user.displayName')}</label>
            <input
              className="input"
              value={draftUser.displayName}
              onChange={(event) =>
                setDraftUser({ ...draftUser, displayName: event.target.value })
              }
            />
          </div>
          {editing === 'new' ? (
            <div className="field">
              <label>{t('system.user.password')}</label>
              <input
                className="input"
                type="password"
                value={draftUser.password}
                onChange={(event) =>
                  setDraftUser({ ...draftUser, password: event.target.value })
                }
              />
              <span className="hint">{t('system.user.passwordHint')}</span>
            </div>
          ) : null}
          <div className="field">
            <label>{t('system.user.status')}</label>
            <select
              className="select"
              value={draftUser.status}
              disabled={editing !== 'new' && editing?.username === currentUsername}
              onChange={(event) =>
                setDraftUser({ ...draftUser, status: event.target.value as AccountStatus })
              }
            >
              <option value="ACTIVE">{t('system.user.status.ACTIVE')}</option>
              <option value="DISABLED">{t('system.user.status.DISABLED')}</option>
              <option value="LOCKED">{t('system.user.status.LOCKED')}</option>
            </select>
            {editing !== 'new' && editing?.username === currentUsername ? (
              <span className="hint">{t('system.user.selfStatusHint')}</span>
            ) : null}
          </div>
          <div className="field">
            <label>{t('system.user.email')}</label>
            <input
              className="input"
              value={draftUser.email}
              onChange={(event) => setDraftUser({ ...draftUser, email: event.target.value })}
            />
          </div>
          <div className="field">
            <label>{t('system.user.phone')}</label>
            <input
              className="input"
              value={draftUser.phone}
              onChange={(event) => setDraftUser({ ...draftUser, phone: event.target.value })}
            />
          </div>
        </div>

        <div className="form-section">
          <div className="section-title">{t('system.user.roles')}</div>
          <div className="check-grid">
            {(rolesQuery.data ?? []).map((role) => (
            <label key={role.id} className="check">
              <input
                type="checkbox"
                checked={draftUser.roles.includes(role.code)}
                onChange={() => toggleRole(role.code)}
              />
              <span>
                <span className="mono" style={{ fontWeight: 600 }}>
                  {role.code}
                </span>
                <span className="hint"> · {role.name}</span>
              </span>
            </label>
            ))}
          </div>
        </div>
      </Drawer>

      {/* -------------------------------------------------------- reset password */}
      <Drawer
        open={passwordTarget !== null}
        title={t('system.user.resetPassword')}
        subtitle={passwordTarget?.username}
        onClose={() => setPasswordTarget(null)}
        footer={
          <div className="row-actions">
            <button type="button" className="btn" onClick={() => setPasswordTarget(null)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={resetMutation.isPending || newPassword.length < 8}
              onClick={() => resetMutation.mutate()}
            >
              {resetMutation.isPending ? t('common.saving') : t('common.save')}
            </button>
          </div>
        }
      >
        <div className="field">
          <label>{t('system.user.newPassword')}</label>
          <input
            className="input"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <span className="hint">{t('system.user.passwordHint')}</span>
        </div>
      </Drawer>

      {/* ------------------------------------------------------------ role form */}
      <Drawer
        open={roleEditing !== null}
        wide
        title={roleEditing === 'new' ? t('system.role.createTitle') : t('system.role.editTitle')}
        subtitle={roleEditing && roleEditing !== 'new' ? roleEditing.code : undefined}
        onClose={() => setRoleEditing(null)}
        footer={
          <div className="row-actions">
            <button type="button" className="btn" onClick={() => setRoleEditing(null)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={saveRole.isPending}
              onClick={() => saveRole.mutate()}
            >
              {saveRole.isPending ? t('common.saving') : t('common.save')}
            </button>
          </div>
        }
      >
        <div className="form-grid">
          <div className="field">
            <label>{t('system.role.code')}</label>
            <input
              className="input mono"
              value={roleDraft.code}
              disabled={roleEditing !== 'new'}
              onChange={(event) => setRoleDraft({ ...roleDraft, code: event.target.value })}
            />
          </div>
          <div className="field">
            <label>{t('system.role.name')}</label>
            <input
              className="input"
              value={roleDraft.name}
              onChange={(event) => setRoleDraft({ ...roleDraft, name: event.target.value })}
            />
          </div>
          <div className="field span-2">
            <label>{t('system.role.description')}</label>
            <input
              className="input"
              value={roleDraft.description}
              onChange={(event) =>
                setRoleDraft({ ...roleDraft, description: event.target.value })
              }
            />
          </div>
        </div>

        <div className="form-section">
          <div className="section-title">
            {t('system.role.permissions')}
            <span className="hint">
              {' '}
              · {roleDraft.permissions.length}/{permissionQuery.data?.length ?? 0}
            </span>
          </div>
          {groupedPermissions.map(([module, items]) => (
            <div key={module} style={{ marginBottom: 14 }}>
              <div className="eyebrow" style={{ marginBottom: 6 }}>
                {module}
              </div>
              <div className="check-grid">
                {(items ?? []).map((permission) => (
                  <label key={permission.code} className="check">
                    <input
                      type="checkbox"
                      checked={roleDraft.permissions.includes(permission.code)}
                      onChange={() => togglePermission(permission.code)}
                    />
                    <span>
                      {permission.name}
                      <span className="hint mono"> · {permission.code}</span>
                    </span>
                  </label>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Drawer>

      <ConfirmDialog
        open={roleDeleting !== null}
        danger
        title={t('system.role.deleteTitle')}
        body={t('system.role.deleteBody', { code: roleDeleting?.code ?? '' })}
        confirmLabel={t('common.delete')}
        busy={removeRole.isPending}
        onConfirm={() => removeRole.mutate()}
        onCancel={() => setRoleDeleting(null)}
      />
    </>
  )
}
