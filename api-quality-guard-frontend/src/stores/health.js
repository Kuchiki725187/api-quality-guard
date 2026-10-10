import { defineStore } from 'pinia'
import http from '@/api/http'

export const useHealthStore = defineStore('health', {
  state: () => ({
    info: null,
    loading: false,
    error: '',
  }),
  actions: {
    async fetchHealth() {
      this.loading = true
      this.error = ''
      try {
        this.info = await http.get('/health')
      } catch (e) {
        this.info = null
        this.error = e.message
      } finally {
        this.loading = false
      }
    },
  },
})
