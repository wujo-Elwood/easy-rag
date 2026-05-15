import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(localStorage.getItem('userId') || '')
  const username = ref(localStorage.getItem('username') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')

  function setUserInfo(data) {
    token.value = data.token
    userId.value = data.userId
    username.value = data.username
    nickname.value = data.nickname

    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname)
  }

  function logout() {
    token.value = ''
    userId.value = ''
    username.value = ''
    nickname.value = ''

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
  }

  return {
    token,
    userId,
    username,
    nickname,
    setUserInfo,
    logout
  }
})
