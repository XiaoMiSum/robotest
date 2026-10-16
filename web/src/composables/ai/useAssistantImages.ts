import { ElMessage } from 'element-plus'
import { invalidImageReason } from '@/composables/common/markdownImage'
import { uploadFile } from '@/services/files'
import { useAiAssistantStore } from '@/stores/aiAssistant'

/** 每条消息附图上限（详设 3.6 与后端同口径） */
const MAX_IMAGES = 3

/**
 * 待发附图动作编排（交互 05 §2.1）：类型 / 大小 / 张数校验与提示在组合式层，
 * 待发状态与上传回写在 store；选择即上传，失败保留条目供点击重试。
 */
export function useAssistantImages() {
  const store = useAiAssistantStore()

  function addImages(files: File[]): void {
    const pending = store.imagesForCurrent
    const accepted: File[] = []
    for (const file of files) {
      const reason = invalidImageReason(file)
      if (reason) {
        ElMessage.warning(`${reason}，无法发送`)
        continue
      }
      if (pending.length + accepted.length >= MAX_IMAGES) {
        ElMessage.warning(`附图最多携带 ${MAX_IMAGES} 张`)
        break
      }
      accepted.push(file)
    }
    for (const entry of store.stageImages(accepted)) {
      void upload(entry.conversationKey, entry.key, entry.file)
    }
  }

  async function upload(conversationKey: string, key: string, file: File): Promise<void> {
    try {
      const item = await uploadFile(file)
      store.updatePendingImage(conversationKey, key, { status: 'done', fileId: item.id })
    } catch {
      store.updatePendingImage(conversationKey, key, { status: 'failed' })
      ElMessage.error(`「${file.name}」上传失败，点击重试`)
    }
  }

  function retryImage(key: string): void {
    const source = store.retrySource(key)
    if (!source) return
    store.updatePendingImage(source.conversationKey, key, { status: 'uploading' })
    void upload(source.conversationKey, key, source.file)
  }

  function removeImage(key: string): void {
    store.removeImage(key)
  }

  return { addImages, retryImage, removeImage }
}
