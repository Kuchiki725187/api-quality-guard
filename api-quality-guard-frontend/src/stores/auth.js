import { defineStore } from 'pinia'
import http from '@/api/http'
import { getToken, setToken, clearToken } from '@/utils/authSession'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: null,
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
  },

  actions: {
    async login(form) {
      const data = await http.post('/auth/login', form)
      this.token = data.token
      this.user = data.user
      setToken(data.token)
      return data.user
    },

    async ensureUser() {
      if (this.user) return this.user
      const user = await http.get('/auth/me')
      this.user = user
      return user
    },

    async logout() {
      try {
        await http.post('/auth/logout')
      } finally {
        this.clear()
      }
    },

    clear() {
      this.token = null
      this.user = null
      clearToken()
    },
  },
})
