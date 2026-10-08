import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { importRequirement } from '@/services/project'

/** 导入白名单与后端 3.8 口径一致（Markdown / Word / 图片，Word 仅 .docx） */
const IMPORT_EXTENSIONS = ['md', 'docx', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp']
const MAX_IMPORT_SIZE = 20 * 1024 * 1024
/** 已有进行中导入任务（1000018013）：提示引导去任务中心 */
const REQUIREMENT_TASK_IN_PROGRESS = 1000018013

function extensionOf(fileName: string): string {
  const index = fileName.lastIndexOf('.')
  if (index < 0 || index === fileName.length - 1) return ''
  return fileName.slice(index + 1).toLowerCase()
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) return error.message
  return fallback
}

function errorCode(error: unknown): number | undefined {
  if (error instanceof Error && 'code' in error) {
    const code = (error as Error & { code?: number }).code
    return typeof code === 'number' ? code : undefined
  }
  return undefined
}

/** 需求导入（交互 06 §2.3）：文件预校验 → 提交 → 跳转任务详情页跟踪进度与审核 */
export function useRequirementImport() {
  const router = useRouter()
  const selectedFile = ref<File | null>(null)
  const fileError = ref('')
  const submitting = ref(false)

  function reset(): void {
    selectedFile.value = null
    fileError.value = ''
  }

  /** 预校验顺序对齐后端 3.8（类型 → 大小 → 空文件），不满足不发请求 */
  function pickFile(file: File): boolean {
    if (!IMPORT_EXTENSIONS.includes(extensionOf(file.name))) {
      selectedFile.value = null
      fileError.value = '不支持的文件类型，仅支持 Markdown（.md）、Word（.docx）与图片'
      return false
    }
    if (file.size > MAX_IMPORT_SIZE) {
      selectedFile.value = null
      fileError.value = '导入文件超过 20MB 限制'
      return false
    }
    if (file.size === 0) {
      selectedFile.value = null
      fileError.value = '导入文件为空或不可解析'
      return false
    }
    selectedFile.value = file
    fileError.value = ''
    return true
  }

  function removeFile(): void {
    selectedFile.value = null
    fileError.value = ''
  }

  /** @returns 提交成功（已跳转任务详情页）为 true；预校验失败或接口报错为 false 且保留对话框 */
  async function submit(): Promise<boolean> {
    if (selectedFile.value === null) {
      fileError.value = '请先选择要导入的文件'
      return false
    }
    submitting.value = true
    try {
      const result = await importRequirement(selectedFile.value)
      ElMessage.success('导入任务已提交')
      void router.push(`/workspace/projects/ai/tasks/${result.taskId}`)
      return true
    } catch (err) {
      // 同项目已有进行中导入任务：引导去任务中心看该任务（交互 06 §2.3.2）
      if (errorCode(err) === REQUIREMENT_TASK_IN_PROGRESS) {
        try {
          await ElMessageBox.confirm(
            errorMessage(err, '存在进行中的导入任务'),
            '无法提交导入',
            {
              type: 'warning',
              confirmButtonText: '前往任务中心',
              cancelButtonText: '留在本页',
            },
          )
        } catch {
          return false
        }
        void router.push('/workspace/projects/ai/tasks')
        return false
      }
      ElMessage.error(errorMessage(err, '导入需求失败'))
      return false
    } finally {
      submitting.value = false
    }
  }

  return { selectedFile, fileError, submitting, reset, pickFile, removeFile, submit }
}
