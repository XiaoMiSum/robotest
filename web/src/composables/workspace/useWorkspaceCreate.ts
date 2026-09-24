import { computed, getCurrentInstance, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createWorkspace, fetchSimpleUserList } from '@/services/admin'
import { useAuthStore } from '@/stores/auth'
import type { UserSimple } from '@/types'

export interface WorkspaceCreateForm {
  name: string
  description: string
  adminUserId: string
}

function createErrorMessage(error: unknown): string {
  return error instanceof Error && error.message ? error.message : '创建工作空间失败'
}

function validateWorkspaceName(
  _rule: unknown,
  value: unknown,
  callback: (error?: Error) => void,
): void {
  const length = typeof value === 'string' ? value.trim().length : 0
  if (length < 2 || length > 50) {
    callback(new Error('名称长度需在 2-50 字符之间'))
    return
  }
  callback()
}

export function useWorkspaceCreate() {
  const authStore = useAuthStore()
  const currentAdminOption = computed<UserSimple | null>(() => {
    const user = authStore.user
    if (!user) return null
    return {
      id: user.id,
      name: user.username || user.email || user.id,
    }
  })
  const formRef = ref<FormInstance>()
  const form = reactive<WorkspaceCreateForm>({
    name: '',
    description: '',
    adminUserId: currentAdminOption.value?.id ?? '',
  })
  const submitting = ref(false)
  const adminSearching = ref(false)
  const adminOptions = ref<UserSimple[]>(currentAdminOption.value ? [currentAdminOption.value] : [])

  const rules: FormRules = {
    name: [
      { required: true, whitespace: true, message: '请输入工作空间名称', trigger: 'blur' },
      { validator: validateWorkspaceName, trigger: 'blur' },
    ],
    adminUserId: [{ required: true, message: '请选择空间管理员', trigger: 'change' }],
    description: [{ max: 200, message: '描述最多 200 个字符', trigger: 'blur' }],
  }

  let adminRequestSequence = 0

  function mergeAdminOptions(results: UserSimple[]): UserSimple[] {
    const merged: UserSimple[] = []
    const candidates = [currentAdminOption.value, ...results]
    const selected = adminOptions.value.find((option) => option.id === form.adminUserId)
    if (selected) candidates.push(selected)
    for (const candidate of candidates) {
      if (candidate && !merged.some((option) => option.id === candidate.id)) {
        merged.push(candidate)
      }
    }
    return merged
  }

  function reset(): void {
    form.name = ''
    form.description = ''
    form.adminUserId = currentAdminOption.value?.id ?? ''
    adminOptions.value = currentAdminOption.value ? [currentAdminOption.value] : []
    formRef.value?.clearValidate()
  }

  async function searchAdmins(keyword: string): Promise<void> {
    const normalizedKeyword = keyword.trim()
    const sequence = ++adminRequestSequence
    if (!normalizedKeyword) {
      adminSearching.value = false
      adminOptions.value = mergeAdminOptions([])
      return
    }

    adminSearching.value = true
    try {
      const results = await fetchSimpleUserList(normalizedKeyword)
      if (sequence !== adminRequestSequence) return
      adminOptions.value = mergeAdminOptions(results)
    } catch {
      if (sequence === adminRequestSequence) {
        adminOptions.value = mergeAdminOptions([])
      }
    } finally {
      if (sequence === adminRequestSequence) adminSearching.value = false
    }
  }

  async function submit(): Promise<string | null> {
    if (submitting.value || !formRef.value) return null
    submitting.value = true
    try {
      try {
        await formRef.value.validate()
      } catch {
        return null
      }

      const id = await createWorkspace({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        adminUserId: form.adminUserId,
      })
      ElMessage.success('工作空间已创建')
      return id
    } catch (error) {
      ElMessage.error(createErrorMessage(error))
      return null
    } finally {
      submitting.value = false
    }
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      adminRequestSequence += 1
    })
  }

  return {
    formRef,
    form,
    rules,
    submitting,
    adminSearching,
    adminOptions,
    currentAdminOption,
    reset,
    searchAdmins,
    submit,
  }
}
