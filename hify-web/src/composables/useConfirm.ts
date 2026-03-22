import { ElMessageBox } from 'element-plus'
import { notifySuccess } from '@/utils/notify'

export function useConfirm() {
  const confirm = async (
    message: string,
    apiFn: () => Promise<unknown>,
    successMsg = '操作成功'
  ): Promise<boolean> => {
    await ElMessageBox.confirm(message, '确认操作', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await apiFn()
    notifySuccess(successMsg)
    return true
  }

  return { confirm }
}
