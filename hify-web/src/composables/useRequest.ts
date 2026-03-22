import { ref } from 'vue'
import { notifyError } from '@/utils/notify'

export function useRequest<T>(apiFn: (...args: unknown[]) => Promise<T>) {
  const data = ref<T | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  const execute = async (...args: unknown[]): Promise<T | null> => {
    loading.value = true
    error.value = null
    try {
      data.value = await apiFn(...args)
      return data.value
    } catch (e) {
      error.value = e as Error
      notifyError((e as Error).message || '请求失败')
      return null
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
