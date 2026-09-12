<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import type {
  ApiEnvironmentListItem,
  ApiScheduleExecutionItem,
  ApiScheduleExecutionScope,
  ApiSchedulePageItem,
  ApiScheduleSaveReq,
  ProjectModule,
} from '@/types'
import ScenePickerDialog from '@/components/project/ScenePickerDialog.vue'
import {
  createSchedule,
  deleteSchedule,
  executeSchedule,
  fetchScheduleExecutions,
  fetchSchedulePage,
  toggleSchedule,
  updateSchedule,
  validateCron,
} from '@/services/apiSchedule'
import { fetchScenePage } from '@/services/apiScene'
import { fetchEnvironments } from '@/services/apiEnvironment'
import { fetchProjectModuleTree } from '@/services/project'
import { CRON_PRESETS, EXECUTION_SCOPES, SCHEDULE_TASK_TYPES, taskExecutionSummary, execStatusLabel, execStatusType } from './schedulesModel'
import { formatDateTime, formatShortDateTime } from '@/utils/format'

// ==================== 列表 ====================

const rows = ref<ApiSchedulePageItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20
const loading = ref(false)
const typeFilter = ref<string>('')

async function loadPage() {
  loading.value = true
  try {
    const params: { pageNo: number; pageSize: number; taskType?: string } = { pageNo: pageNo.value, pageSize }
    if (typeFilter.value) params.taskType = typeFilter.value
    const page = await fetchSchedulePage(params)
    rows.value = page.list
    total.value = page.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '定时任务列表加载失败')
  } finally {
    loading.value = false
  }
}

watch(typeFilter, () => {
  pageNo.value = 1
  void loadPage()
})

// ==================== 新建/编辑弹窗 ====================

const showFormDialog = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<{
  taskType: ApiScheduleSaveReq['taskType']
  name: string
  description: string
  executionScope: ApiScheduleExecutionScope
  moduleIds: string[]
  sceneIds: string[]
  openapiUrl: string
  environmentId: string | undefined
  cronExpression: string
  enabled: boolean
}>({
  taskType: 'scene_execute',
  name: '',
  description: '',
  executionScope: 'all',
  moduleIds: [],
  sceneIds: [],
  openapiUrl: '',
  environmentId: undefined,
  cronExpression: '',
  enabled: true,
})

const isTestPlanTask = computed(() => form.taskType === 'scene_execute')

// 执行范围选项加载（模块树）
const moduleOptions = ref<ProjectModule[]>([])
const scopeOptionLoading = ref(false)

async function loadScopeOptions(scope: ApiScheduleExecutionScope) {
  scopeOptionLoading.value = true
  try {
    if (scope === 'modules') {
      moduleOptions.value = await fetchProjectModuleTree('scene')
    }
  } catch (error) {
    moduleOptions.value = []
    if (scope === 'modules') {
      ElMessage.error(error instanceof Error ? `模块加载失败：${error.message}` : '模块加载失败')
    }
  } finally {
    scopeOptionLoading.value = false
  }
}

// 指定场景：选择对话框 + 已选标签
const scenePickerVisible = ref(false)
const selectedScenes = ref<{ id: string; name: string }[]>([])

// 标签过多时折叠，避免挤占表单单行高度
const MAX_VISIBLE_SCENE_TAGS = 8
const sceneTagsExpanded = ref(false)
const visibleSceneTags = computed(() =>
  sceneTagsExpanded.value ? selectedScenes.value : selectedScenes.value.slice(0, MAX_VISIBLE_SCENE_TAGS),
)

function handleSceneConfirm(selected: { id: string; name: string }[]) {
  selectedScenes.value = selected
  form.sceneIds = selected.map((s) => s.id)
}

function removeScene(id: string) {
  form.sceneIds = form.sceneIds.filter((sceneId) => sceneId !== id)
  selectedScenes.value = selectedScenes.value.filter((s) => s.id !== id)
}

// 编辑场景任务回填标签：PageParam.pageSize 上限 100，需分页拉全量场景名
async function hydrateSelectedSceneNames(): Promise<void> {
  if (!form.sceneIds.length) {
    selectedScenes.value = []
    return
  }
  const names = new Map<string, string>()
  let pageNo = 1
  let total = Infinity
  while (names.size < total) {
    const page = await fetchScenePage({ pageNo, pageSize: 100 })
    for (const s of page.list) names.set(s.id, s.name)
    total = page.total
    if (!page.list.length) break
    pageNo += 1
  }
  selectedScenes.value = form.sceneIds.map((id) => ({ id, name: names.get(id) ?? '（已删除场景）' }))
}

// 环境列表
const environmentOptions = ref<ApiEnvironmentListItem[]>([])
const environmentLoading = ref(false)

async function loadEnvironments() {
  environmentLoading.value = true
  try {
    environmentOptions.value = await fetchEnvironments()
  } catch {
    environmentOptions.value = []
  } finally {
    environmentLoading.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.taskType = 'scene_execute'
  form.name = ''
  form.description = ''
  form.executionScope = 'all'
  form.moduleIds = []
  form.sceneIds = []
  form.openapiUrl = ''
  form.environmentId = undefined
  form.cronExpression = ''
  form.enabled = true
  moduleOptions.value = []
  selectedScenes.value = []
}

function openCreate() {
  resetForm()
  showFormDialog.value = true
  void loadScopeOptions(form.executionScope)
  void loadEnvironments()
}

function openEdit(item: ApiSchedulePageItem) {
  editingId.value = item.id
  // 旧版绑定对象任务（历史遗留 taskType）按测试计划口径打开，保存即迁移为新模型
  form.taskType = item.taskType === 'import_swagger' ? 'import_swagger' : 'scene_execute'
  form.name = item.name
  form.description = item.description ?? ''
  form.executionScope = item.executionScope ?? 'all'
  form.moduleIds = item.moduleIds ?? []
  form.sceneIds = item.sceneIds ?? []
  form.openapiUrl = item.openapiUrl ?? ''
  form.environmentId = item.environmentId ?? undefined
  form.cronExpression = item.cronExpression
  form.enabled = item.enabled
  showFormDialog.value = true
  void loadScopeOptions(form.executionScope)
  void loadEnvironments()
  if (form.executionScope === 'scenes') void hydrateSelectedSceneNames()
}

watch(() => form.taskType, (type) => {
  if (type === 'import_swagger') {
    form.executionScope = 'all'
    form.moduleIds = []
    form.sceneIds = []
    form.environmentId = undefined
  } else {
    form.openapiUrl = ''
    void loadScopeOptions('all')
  }
})

watch(() => form.executionScope, (scope) => {
  void loadScopeOptions(scope)
  if (scope === 'scenes') void hydrateSelectedSceneNames()
})

// Cron 校验
const cronValidation = ref<{ valid: boolean; description: string | null; nextExecutions: string[] | null } | null>(null)
const cronValidating = ref(false)

async function handleValidateCron() {
  if (!form.cronExpression.trim()) return
  cronValidating.value = true
  try {
    cronValidation.value = await validateCron({ cronExpression: form.cronExpression.trim() })
  } catch {
    cronValidation.value = { valid: false, description: null, nextExecutions: null }
  } finally {
    cronValidating.value = false
  }
}

function handlePresetSelect(preset: string) {
  form.cronExpression = preset
  void handleValidateCron()
}

// Cron 构建器状态
const cronBuilder = reactive({
  minute: '0',
  hour: '2',
  day: '*',
  month: '*',
  weekday: '*',
})
const showCronBuilder = ref(false)

function applyCronBuilder() {
  form.cronExpression = `${cronBuilder.minute} ${cronBuilder.hour} ${cronBuilder.day} ${cronBuilder.month} ${cronBuilder.weekday}`
  showCronBuilder.value = false
  void handleValidateCron()
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (!form.cronExpression.trim()) {
    ElMessage.warning('请输入 Cron 表达式')
    return
  }
  if (isTestPlanTask.value) {
    if (!form.environmentId) {
      ElMessage.warning('测试计划任务需选择目标环境')
      return
    }
    if (form.executionScope === 'modules' && !form.moduleIds.length) {
      ElMessage.warning('请选择执行模块')
      return
    }
    if (form.executionScope === 'scenes' && !form.sceneIds.length) {
      ElMessage.warning('请选择执行场景')
      return
    }
  } else if (!form.openapiUrl.trim()) {
    ElMessage.warning('接口同步任务需填写接口文档 URL')
    return
  }
  saving.value = true
  try {
    const req: ApiScheduleSaveReq = {
      taskType: form.taskType,
      name: form.name.trim(),
      description: form.description?.trim() || undefined,
      executionScope: isTestPlanTask.value ? form.executionScope : undefined,
      moduleIds: isTestPlanTask.value && form.executionScope === 'modules' ? form.moduleIds : undefined,
      sceneIds: isTestPlanTask.value && form.executionScope === 'scenes' ? form.sceneIds : undefined,
      openapiUrl: isTestPlanTask.value ? undefined : form.openapiUrl.trim() || undefined,
      environmentId: isTestPlanTask.value ? form.environmentId || undefined : undefined,
      cronExpression: form.cronExpression.trim(),
      enabled: form.enabled,
    }
    if (editingId.value) {
      await updateSchedule(editingId.value, req)
      ElMessage.success('已更新')
    } else {
      await createSchedule(req)
      ElMessage.success('已创建')
    }
    showFormDialog.value = false
    await loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 启停 ====================

async function handleToggle(item: ApiSchedulePageItem) {
  const newEnabled = !item.enabled
  const label = newEnabled ? '启用' : '停用'
  try {
    await toggleSchedule(item.id, { enabled: newEnabled })
    ElMessage.success(`已${label}`)
    await loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${label}失败`)
  }
}

// ==================== 删除 ====================

async function handleDelete(item: ApiSchedulePageItem) {
  await ElMessageBox.confirm(`删除定时任务「${item.name}」？删除不影响已产生的执行记录与报告。`, '删除定时任务', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger',
  })
  try {
    await deleteSchedule(item.id)
    ElMessage.success('已删除')
    if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
    else await loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ==================== 立即执行 ====================

async function handleExecuteNow(item: ApiSchedulePageItem) {
  if (item.lastExecutionStatus === 'running') {
    ElMessage.warning('上一次执行未结束，请稍后再试')
    return
  }
  await ElMessageBox.confirm(`立即执行定时任务「${item.name}」？`, '立即执行', { type: 'info' })
  try {
    await executeSchedule(item.id)
    ElMessage.success('已触发执行')
    await loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行失败')
  }
}

// ==================== 执行记录抽屉 ====================

const showExecutionDrawer = ref(false)
const executionTask = ref<ApiSchedulePageItem | null>(null)
const executionRows = ref<ApiScheduleExecutionItem[]>([])
const executionTotal = ref(0)
const executionPageNo = ref(1)
const executionLoading = ref(false)

async function openExecutions(item: ApiSchedulePageItem) {
  executionTask.value = item
  executionRows.value = []
  executionTotal.value = 0
  executionPageNo.value = 1
  showExecutionDrawer.value = true
  await loadExecutions()
}

async function loadExecutions() {
  if (!executionTask.value) return
  executionLoading.value = true
  try {
    const page = await fetchScheduleExecutions(executionTask.value.id, { pageNo: executionPageNo.value, pageSize: 10 })
    executionRows.value = page.list
    executionTotal.value = page.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行记录加载失败')
  } finally {
    executionLoading.value = false
  }
}

function triggerTypeLabel(type: string): string {
  return type === 'manual' ? '手动' : '定时'
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

// ==================== 初始化 ====================

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <div class="schedules-page">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="schedules-page__toolbar">
          <el-select v-model="typeFilter" placeholder="全部类型" clearable style="width: 140px">
            <el-option
              v-for="opt in SCHEDULE_TASK_TYPES"
              :key="opt.value"
              :value="opt.value"
              :label="opt.label"
            />
          </el-select>
          <div class="schedules-page__spacer" />
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon>新建任务
          </el-button>
        </div>
      </template>

      <el-table :data="rows">
        <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.taskType === 'scene_execute' ? '测试计划' : row.taskType === 'import_swagger' ? '接口同步' : row.taskType }}
          </template>
        </el-table-column>
        <el-table-column label="执行范围" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ taskExecutionSummary(row as ApiSchedulePageItem) }}
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" label="调度" width="130" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              size="small"
              @change="() => handleToggle(row as ApiSchedulePageItem)"
            />
          </template>
        </el-table-column>
        <el-table-column label="上次执行" width="130">
          <template #default="{ row }">
            <div v-if="row.lastExecutionStatus">
              <el-tag size="small" :type="execStatusType(row.lastExecutionStatus)">
                {{ execStatusLabel(row.lastExecutionStatus) }}
              </el-tag>
              <div v-if="row.lastExecutionAt" class="schedules-page__exec-time">
                {{ formatShortDateTime(row.lastExecutionAt) }}
              </div>
            </div>
            <span v-else class="text-neutral-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" :disabled="row.lastExecutionStatus === 'running'" @click="handleExecuteNow(row as ApiSchedulePageItem)">立即执行</el-button>
            <el-button link size="small" @click="openExecutions(row as ApiSchedulePageItem)">执行记录</el-button>
            <el-dropdown
              trigger="click" class="schedules-page__more" @command="(cmd: string) => {
                if (cmd === 'edit') openEdit(row as ApiSchedulePageItem)
                else if (cmd === 'delete') void handleDelete(row as ApiSchedulePageItem)
              }">
              <el-button link size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无定时任务，点击右上角「新建任务」创建第一个任务" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="schedules-page__pagination"
        @current-change="loadPage"
      />
    </el-card>

    <!-- ==================== 新建/编辑弹窗 ==================== -->
    <el-dialog
      v-model="showFormDialog"
      :title="editingId ? '编辑定时任务' : '新建定时任务'"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-width="100px" label-position="right">
        <el-form-item label="任务名称" prop="name" :rules="[{ required: true, message: '请输入任务名称', trigger: 'blur' }]">
          <el-input v-model="form.name" placeholder="请输入任务名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选，任务描述" maxlength="500" />
        </el-form-item>
        <el-form-item label="任务类型" prop="taskType" :rules="[{ required: true, message: '请选择任务类型', trigger: 'change' }]">
          <el-radio-group v-model="form.taskType">
            <el-radio v-for="opt in SCHEDULE_TASK_TYPES" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="isTestPlanTask">
          <el-form-item label="执行方式" prop="executionScope" :rules="[{ required: true, message: '请选择执行方式', trigger: 'change' }]">
            <el-radio-group v-model="form.executionScope">
              <el-radio v-for="opt in EXECUTION_SCOPES" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.executionScope === 'modules'" label="指定模块" prop="moduleIds" :rules="[{ required: true, message: '请选择执行模块', trigger: 'change' }]">
            <el-tree-select
              v-model="form.moduleIds"
              :data="moduleOptions"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              multiple
              show-checkbox
              check-strictly
              default-expand-all
              :loading="scopeOptionLoading"
              placeholder="选择执行模块（勾选父模块将包含其全部子模块）"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-else-if="form.executionScope === 'scenes'" label="指定场景" prop="sceneIds" :rules="[{ required: true, message: '请选择执行场景', trigger: 'change' }]">
            <div class="schedules-page__scene-picker">
              <el-button link type="primary" @click="scenePickerVisible = true">
                {{ form.sceneIds.length ? `已选 ${form.sceneIds.length} 个场景` : '选择场景' }}
              </el-button>
              <el-tag
                v-for="s in visibleSceneTags"
                :key="s.id"
                size="small"
                closable
                :disable-transitions="true"
                :title="s.name"
                @close="removeScene(s.id)"
              >
                <span class="schedules-page__scene-tag-text">{{ s.name }}</span>
              </el-tag>
              <el-button v-if="selectedScenes.length > MAX_VISIBLE_SCENE_TAGS" link @click="sceneTagsExpanded = !sceneTagsExpanded">
                {{ sceneTagsExpanded ? '收起' : `+${selectedScenes.length - MAX_VISIBLE_SCENE_TAGS}` }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="目标环境" prop="environmentId" :rules="[{ required: true, message: '请选择目标环境', trigger: 'change' }]">
            <el-select
              v-model="form.environmentId"
              placeholder="请选择目标环境"
              :loading="environmentLoading"
              style="width: 100%"
              filterable
            >
              <el-option
                v-for="env in environmentOptions"
                :key="env.id"
                :value="env.id"
                :label="env.name"
              />
            </el-select>
            <div class="schedules-page__form-hint">测试计划任务需指定目标环境</div>
          </el-form-item>
        </template>
        <el-form-item v-else label="接口文档 URL" prop="openapiUrl" :rules="[{ required: true, message: '请填写接口文档 URL', trigger: 'blur' }]">
          <el-input v-model="form.openapiUrl" placeholder="https://example.com/v3/api-docs" maxlength="2000" />
          <div class="schedules-page__form-hint">接口同步任务必填；保存时校验 URL 可达性与合法性</div>
        </el-form-item>

        <el-divider content-position="left">调度配置</el-divider>

        <el-form-item label="预设表达式">
          <el-select placeholder="选择预设" style="width: 200px" @update:model-value="(v: string) => handlePresetSelect(v)">
            <el-option v-for="p in CRON_PRESETS" :key="p.expression" :value="p.expression" :label="`${p.label} (${p.expression})`" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron 表达式" prop="cronExpression" :rules="[{ required: true, message: '请输入 Cron 表达式', trigger: 'blur' }]">
          <div class="schedules-page__cron-row">
            <el-input v-model="form.cronExpression" placeholder="0 2 * * *" style="flex: 1" maxlength="50" />
            <el-button :loading="cronValidating" @click="handleValidateCron">校验</el-button>
            <el-button @click="showCronBuilder = !showCronBuilder">构建器</el-button>
          </div>
          <div v-if="cronValidation" class="schedules-page__cron-result">
            <template v-if="cronValidation.valid">
              <el-tag type="success" size="small">合法</el-tag>
              <span class="schedules-page__cron-desc">{{ cronValidation.description }}</span>
            </template>
            <el-tag v-else type="danger" size="small">表达式不合法</el-tag>
          </div>
          <div v-if="cronValidation?.nextExecutions?.length" class="schedules-page__cron-preview">
            下次执行：{{ cronValidation.nextExecutions.slice(0, 3).map((t) => formatShortDateTime(t)).join(' · ') }}
          </div>
        </el-form-item>

        <!-- Cron 构建器 -->
        <el-form-item v-if="showCronBuilder" label="Cron 构建">
          <div class="schedules-page__cron-builder">
            <el-input v-model="cronBuilder.minute" placeholder="分钟" style="width: 80px" />
            <el-input v-model="cronBuilder.hour" placeholder="小时" style="width: 80px" />
            <el-input v-model="cronBuilder.day" placeholder="日" style="width: 80px" />
            <el-input v-model="cronBuilder.month" placeholder="月" style="width: 80px" />
            <el-input v-model="cronBuilder.weekday" placeholder="星期" style="width: 80px" />
            <el-button type="primary" @click="applyCronBuilder">应用</el-button>
          </div>
          <div class="schedules-page__form-hint">五段：分钟(0-59) 小时(0-23) 日(1-31) 月(1-12) 星期(0-7)</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <ScenePickerDialog v-model="scenePickerVisible" :selected-ids="form.sceneIds" @confirm="handleSceneConfirm" />

    <!-- ==================== 执行记录抽屉 ==================== -->
    <el-drawer
      v-model="showExecutionDrawer"
      :title="`执行记录 — ${executionTask?.name ?? ''}`"
      size="600px"
      direction="rtl"
    >
      <div v-loading="executionLoading">
        <el-table :data="executionRows" size="small">
          <el-table-column label="触发时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.triggeredAt) }}</template>
          </el-table-column>
          <el-table-column label="触发方式" width="80">
            <template #default="{ row }">{{ triggerTypeLabel(row.triggerType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="execStatusType(row.status)">{{ execStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.errorMessage" class="text-danger">{{ row.errorMessage }}</span>
              <span v-else class="text-neutral-400">-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="executionPageNo"
          :page-size="10"
          :total="executionTotal"
          layout="total, prev, pager, next"
          class="schedules-page__pagination"
          @current-change="loadExecutions"
        />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.schedules-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.schedules-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.schedules-page__spacer {
  flex: 1;
}

.schedules-page__pagination {
  justify-content: flex-end;
  margin-top: var(--space-md);
}

.schedules-page__exec-time {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  margin-top: 2px;
}

// 与前面的 link 操作按钮保持同基线、同间距
.schedules-page__more {
  margin-left: 12px;
  vertical-align: middle;
}

.schedules-page__cron-row {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
  width: 100%;
}

.schedules-page__scene-picker {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-xs);
  width: 100%;

  // .el-button.is-link 高度 auto（约 20px），显式撑到组件行高，与 label 垂直居中
  .el-button {
    height: var(--el-component-size);
  }
}

.schedules-page__cron-result {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-xs);
}

.schedules-page__cron-desc {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
}

.schedules-page__cron-preview {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  margin-top: var(--space-xs);
}

.schedules-page__cron-builder {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
}

.schedules-page__form-hint {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  margin-top: var(--space-xs);
}

.text-neutral-400 {
  color: var(--color-neutral-400);
}

.text-danger {
  color: var(--color-danger-500);
}
</style>