import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { apiFetch, setAccessToken } from '../api/client'

export interface UserView {
  id: number
  username: string
  displayName: string
  tenantCode: string | null
  roles: string[]
  permissions: string[]
  forcePasswordChange: boolean
}

interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserView
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: UserView | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  has: (permission: string) => boolean
  hasRole: (role: string) => boolean
}

/**
 * Auth state is persisted so a reload keeps the session. The token is not secret from
 * the browser (it is a bearer token), but it is short lived; a refresh endpoint exists
 * for M1 when sessions become long enough to matter.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,

      async login(username, password) {
        const response = await apiFetch<TokenResponse>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ username, password }),
        })
        setAccessToken(response.accessToken)
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          user: response.user,
        })
      },

      logout() {
        setAccessToken(null)
        set({ accessToken: null, refreshToken: null, user: null })
      },

      has(permission) {
        return get().user?.permissions.includes(permission) ?? false
      },

      hasRole(role) {
        return get().user?.roles.includes(role) ?? false
      },
    }),
    {
      name: 'genisource.erp.auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
      }),
      onRehydrateStorage: () => (state) => {
        // Re-arm the module-level token after a page reload.
        setAccessToken(state?.accessToken ?? null)
      },
    },
  ),
)
