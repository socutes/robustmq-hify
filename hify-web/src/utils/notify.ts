import { ElMessage } from 'element-plus'

export const notifySuccess = (message: string) =>
  ElMessage({ type: 'success', message, duration: 2500 })

export const notifyError = (message: string) =>
  ElMessage({ type: 'error', message, duration: 3500 })

export const notifyWarning = (message: string) =>
  ElMessage({ type: 'warning', message, duration: 3000 })
