import axios from 'axios'
import { ElMessage } from 'element-plus'

const instance = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

instance.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code !== 200) {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
    return data
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export const get = <T>(url: string, params?: object): Promise<T> =>
  instance.get(url, { params })

export const post = <T>(url: string, data?: object): Promise<T> =>
  instance.post(url, data)

export const put = <T>(url: string, data?: object): Promise<T> =>
  instance.put(url, data)

export const del = <T>(url: string): Promise<T> =>
  instance.delete(url)
