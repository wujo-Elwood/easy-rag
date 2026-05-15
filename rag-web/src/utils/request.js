import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '',
  timeout: 30000
})

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  response => {
    const { data } = response
    if (data.code === 200) {
      return data
    }
    ElMessage.error(data.message || 'Request failed')
    return Promise.reject(data)
  },
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
      ElMessage.error('Session expired, please login again')
    } else {
      ElMessage.error(error.message || 'Network error')
    }
    return Promise.reject(error)
  }
)

export default request
