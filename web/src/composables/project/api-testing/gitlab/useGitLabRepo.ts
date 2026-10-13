import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createGitRepo,
  deleteGitRepo,
  fetchGitRepo,
  testGitRepoConnection,
  updateGitRepo,
} from '@/services/project/api-testing/gitRepo'
import type {
  ApiGitConnectionTestReq,
  ApiGitConnectionTestResp,
  ApiGitImportMode,
  ApiGitRepoConfig,
  ApiGitRepoCreateReq,
  ApiGitRepoUpdateReq,
} from '@/types'

/** 后端业务错误带 10 位 code（拦截器透传），网络等异常只有 message */
export interface GitLabErrorInfo {
  code?: number
  message: string
}

export function toGitLabError(err: unknown): GitLabErrorInfo {
  const error = err as { code?: number; message?: string } | null
  if (error && typeof error.code === 'number') {
    return { code: error.code, message: error.message || '操作失败，请稍后重试' }
  }
  return { message: error?.message || '操作失败，请稍后重试' }
}

/** 连通性 / 保存失败时行内或 Toast 展示「错误码 + 原因」，便于对照排查（BR-1.5） */
export function formatGitLabError(info: GitLabErrorInfo): string {
  return info.code ? `${info.code} ${info.message}` : info.message
}

/** 表单缺省值与创建接口缺省口径一致（详设 3.1） */
function emptyForm(): GitLabRepoForm {
  return {
    name: '',
    gitlabBaseUrl: '',
    token: '',
    testSourceDir: 'src/test/java',
    resourceDir: 'src/test/resources',
    envFilePath: 'common/env/env.yaml',
    reimportOverwrite: false,
    repoPath: '',
    defaultBranch: '',
    importMode: 'platform',
  }
}

/** 页面表单：创建态含三锁定字段，已配置态三字段由只读区回显、不参与提交 */
export interface GitLabRepoForm {
  name: string
  gitlabBaseUrl: string
  /** 只写不读：编辑态留空表示沿用已存令牌；仅存内存，不落 store / localStorage */
  token: string
  testSourceDir: string
  resourceDir: string
  envFilePath: string
  reimportOverwrite: boolean
  repoPath: string
  defaultBranch: string
  importMode: ApiGitImportMode
}

export type GitLabTab = 'basic' | 'import'

export function useGitLabRepo() {
  const authStore = useAuthStore()
  const canEdit = computed(() => authStore.hasPermission('api-env:edit'))

  const loading = ref(false)
  const loadFailed = ref(false)
  const repo = ref<ApiGitRepoConfig | null>(null)
  const creating = ref(false)
  const activeTab = ref<GitLabTab>('basic')

  const form = reactive<GitLabRepoForm>(emptyForm())

  // 连通性测试结果只记录本会话最近一次：配置保存本身已由服务端强制实测通过
  const testing = ref(false)
  const testOk = ref<boolean | null>(null)
  const testMessage = ref('')
  const connDetailVisible = ref(false)

  const saving = ref(false)

  const isPlatformMode = computed(() => form.importMode === 'platform')
  const connDown = computed(() => testOk.value === false)

  function fillFormFromRepo(config: ApiGitRepoConfig): void {
    form.name = config.name
    form.gitlabBaseUrl = config.gitlabBaseUrl
    form.testSourceDir = config.testSourceDir
    form.resourceDir = config.resourceDir
    form.envFilePath = config.envFilePath
    form.reimportOverwrite = config.reimportOverwrite
    form.repoPath = config.repoPath
    form.defaultBranch = config.defaultBranch
    form.importMode = config.importMode
    form.token = ''
  }

  function resetForm(): void {
    Object.assign(form, emptyForm())
  }

  function resetTestState(): void {
    testOk.value = null
    testMessage.value = ''
    connDetailVisible.value = false
  }

  async function load(): Promise<void> {
    loading.value = true
    loadFailed.value = false
    try {
      repo.value = await fetchGitRepo()
      creating.value = false
      resetTestState()
      if (repo.value) {
        fillFormFromRepo(repo.value)
      } else {
        resetForm()
      }
    } catch (err) {
      loadFailed.value = true
      ElMessage.error(formatGitLabError(toGitLabError(err)))
    } finally {
      loading.value = false
    }
  }

  function startCreate(): void {
    if (!canEdit.value) {
      ElMessage.warning('无 GitLab 配置编辑权限')
      return
    }
    resetForm()
    resetTestState()
    creating.value = true
    activeTab.value = 'basic'
  }

  function setImportMode(mode: ApiGitImportMode): void {
    if (!canEdit.value) return
    form.importMode = mode
  }

  function toggleConnDetail(): void {
    if (!connDown.value) return
    connDetailVisible.value = !connDetailVisible.value
  }

  /** 客户端前置校验只拦必填与明显格式问题，服务端仍全量校验并强制连通性实测 */
  function validate(): string | null {
    if (!form.name.trim()) return '请填写名称'
    if (!form.gitlabBaseUrl.trim()) return '请填写 GitLab 地址'
    if (!/^https?:\/\/.+/i.test(form.gitlabBaseUrl.trim())) return 'GitLab 地址仅支持 http/https 且结尾不留 /'
    if (creating.value) {
      const repoPath = form.repoPath.trim()
      if (!repoPath) return '请填写项目路径'
      if (repoPath.includes('.git')) return '项目路径不含 .git 后缀'
      if (/^https?:\/\//i.test(repoPath)) return '项目路径不含协议前缀'
    }
    return null
  }

  function buildCreatePayload(): ApiGitRepoCreateReq {
    return {
      name: form.name.trim(),
      gitlabBaseUrl: form.gitlabBaseUrl.trim(),
      repoPath: form.repoPath.trim(),
      defaultBranch: form.defaultBranch.trim() || undefined,
      importMode: form.importMode,
      token: form.token,
      testSourceDir: form.testSourceDir.trim() || undefined,
      resourceDir: form.resourceDir.trim() || undefined,
      envFilePath: form.envFilePath.trim() || undefined,
      reimportOverwrite: form.reimportOverwrite,
    }
  }

  function buildUpdatePayload(): ApiGitRepoUpdateReq {
    // 部分更新口径（C11）：清空的可选字段视为不修改（omitted），令牌留空沿用已存令牌
    return {
      name: form.name.trim(),
      gitlabBaseUrl: form.gitlabBaseUrl.trim(),
      token: form.token.trim() || undefined,
      testSourceDir: form.testSourceDir.trim() || undefined,
      resourceDir: form.resourceDir.trim() || undefined,
      envFilePath: form.envFilePath.trim() || undefined,
      reimportOverwrite: form.reimportOverwrite,
    }
  }

  async function save(): Promise<void> {
    if (!canEdit.value) {
      ElMessage.warning('无 GitLab 配置编辑权限')
      return
    }
    const invalid = validate()
    if (invalid) {
      ElMessage.warning(invalid)
      return
    }
    saving.value = true
    try {
      if (creating.value || !repo.value) {
        const created = await createGitRepo(buildCreatePayload())
        repo.value = created
        creating.value = false
        fillFormFromRepo(created)
      } else {
        await updateGitRepo(repo.value.id, buildUpdatePayload())
        await load()
      }
      // 令牌只写不读：保存后即从内存表单清除，不回填不落任何存储
      form.token = ''
      resetTestState()
      ElMessage.success('已保存')
      // 保存成功自动落到「导入」页签（交互稿 27 §1）
      activeTab.value = 'import'
    } catch (err) {
      // 保存失败不落库也不清空表单：保留已填内容供修正后重试
      ElMessage.error(formatGitLabError(toGitLabError(err)))
    } finally {
      saving.value = false
    }
  }

  async function runConnectionTest(): Promise<void> {
    testing.value = true
    connDetailVisible.value = false
    try {
      const payload: ApiGitConnectionTestReq = {
        gitlabBaseUrl: form.gitlabBaseUrl.trim() || undefined,
        // 令牌留空时省略，服务端回落已存令牌（编辑态无需重输）
        token: form.token.trim() || undefined,
      }
      if (creating.value) {
        payload.repoPath = form.repoPath.trim() || undefined
        payload.defaultBranch = form.defaultBranch.trim() || undefined
      }
      const result: ApiGitConnectionTestResp = await testGitRepoConnection(payload)
      testOk.value = true
      testMessage.value = `连接正常 · 实际分支 ${result.resolvedBranch}`
      // 首次配置未填默认分支时回显服务端解析出的实际分支名（交互稿 27 §3.1）
      if (creating.value && !form.defaultBranch.trim()) {
        form.defaultBranch = result.resolvedBranch
      }
    } catch (err) {
      testOk.value = false
      testMessage.value = formatGitLabError(toGitLabError(err))
    } finally {
      testing.value = false
    }
  }

  async function handleDelete(): Promise<void> {
    if (!repo.value || !canEdit.value) return
    try {
      await ElMessageBox.confirm(
        '删除后本项目不再与该仓库绑定，已导入场景保留并标记「来源已移除」，可查看与编辑但不可再由流水线引擎执行。'
          + '变更绑定仓库或接入方式须先删除再重建；存在启用中的 GitLab 同步任务时删除将被拒绝。',
        '删除仓库配置',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      await deleteGitRepo(repo.value.id)
      ElMessage.success('已删除')
      repo.value = null
      resetForm()
      resetTestState()
      creating.value = false
      activeTab.value = 'basic'
    } catch (err) {
      ElMessage.error(formatGitLabError(toGitLabError(err)))
    }
  }

  onMounted(() => void load())

  return {
    canEdit,
    loading,
    loadFailed,
    repo,
    creating,
    activeTab,
    form,
    testing,
    testOk,
    testMessage,
    connDetailVisible,
    saving,
    isPlatformMode,
    connDown,
    load,
    startCreate,
    setImportMode,
    toggleConnDetail,
    save,
    runConnectionTest,
    handleDelete,
  }
}
