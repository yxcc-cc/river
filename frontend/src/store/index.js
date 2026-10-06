import { defineStore } from 'pinia'
import { getUnhandledCount } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || '{}')
  }),
  getters: {
    isLoggedIn: state => !!state.token,
    roleKey: state => state.userInfo.roleKey || '',
    isAdmin: state => state.userInfo.roleKey === 'admin'
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.userInfo = {
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        roleKey: data.roleKey,
        roleName: data.roleName,
        permissions: data.permissions
      }
      localStorage.setItem('token', data.token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = {}
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})

export const useAlertStore = defineStore('alert', {
  state: () => ({
    unhandledCount: 0
  }),
  actions: {
    async refreshCount() {
      try {
        const res = await getUnhandledCount()
        this.unhandledCount = res.data
      } catch (e) {
        // ignore
      }
    }
  }
})
