import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createRequirement, fetchProjectModuleTree } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type { ProjectModule, RequirementPriority, WorkspaceMember } from '@/types'

/** 需求挂在模块目录上，选择树只留目录（文档不承载需求） */
function stripDocuments(nodes: ProjectModule[]): ProjectModule[] {
  return nodes
    .filter((node) => node.type === 'directory')
    .map((node) => ({ ...node, children: stripDocuments(node.children) }))
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

export const REQUIREMENT_PRIORITY_OPTIONS: { value: RequirementPriority; label: string }[] = [
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
]

// ==================== 新建需求页（交互 06 §2.5） ====================

export function useRequirementCreate() {
  const router = useRouter()

  const formRef = ref<FormInstance>()
  const submitting = ref(false)

  const form = reactive({
    title: '',
    description: '',
    moduleId: '',
    systemVersion: '',
    priority: '',
    ownerId: '',
    tags: [] as string[],
  })

  const rules: FormRules = {
    title: [
      { required: true, message: '请输入需求标题', trigger: 'blur' },
      { max: 300, message: '标题不能超过 300 字符', trigger: 'blur' },
    ],
    systemVersion: [{ max: 50, message: '版本不能超过 50 字符', trigger: 'blur' }],
  }

  // ==================== 选项数据 ====================
  const moduleTree = ref<ProjectModule[]>([])
  async function loadModuleTree(): Promise<void> {
    try {
      moduleTree.value = stripDocuments(await fetchProjectModuleTree())
    } catch {
      // 选项树失败不阻塞录入：模块可选，降级为空树
      moduleTree.value = []
    }
  }

  const memberOptions = ref<WorkspaceMember[]>([])
  async function loadMembers(): Promise<void> {
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      memberOptions.value = page.list
    } catch {
      memberOptions.value = []
    }
  }
  void loadModuleTree()
  void loadMembers()

  // ==================== 提交 ====================
  async function handleSubmit(): Promise<void> {
    if (!formRef.value) return
    try {
      await formRef.value.validate()
    } catch {
      return
    }
    submitting.value = true
    try {
      const created = await createRequirement({
        title: form.title.trim(),
        description: form.description,
        moduleId: form.moduleId || undefined,
        systemVersion: form.systemVersion.trim() || undefined,
        priority: (form.priority || undefined) as RequirementPriority | undefined,
        ownerId: form.ownerId || undefined,
        tags: form.tags,
      })
      ElMessage.success('需求已创建')
      void router.push(`/workspace/projects/requirements/${created.id}`)
    } catch (err) {
      // 保留已填内容，用户可修改后重试（交互 06 §2.5.3）
      ElMessage.error(errorMessage(err, '创建需求失败'))
    } finally {
      submitting.value = false
    }
  }

  return {
    router,
    formRef,
    submitting,
    form,
    rules,
    moduleTree,
    memberOptions,
    handleSubmit,
  }
}
