<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import type {
  ApiEnvironmentListItem,
  ApiScheduleExecutionItem,
  ApiSchedulePageItem,
  ApiScheduleSaveReq,
  ApiScenePageItem,
  ApiSwaggerUrlItem,
} from '@/types'
import {
  createSchedule,
  deleteSchedule,
  executeSchedule,
  fetchScheduleExecutions,
  fetchSchedulePage,
  fetchSwaggerUrlList,
  toggleSchedule,
  updateSchedule,
  validateCron,
} from '@/services/apiSchedule'
import { fetchScenePage } from '@/services/apiScene'
import { fetchEnvironments } from '@/services/apiEnvironment'
import { execStatusLabel, execStatusType, SCHEDULE_TASK_TYPES, CRON_PRESETS } from './schedulesModel'
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

const form = reactive<ApiScheduleSaveReq>({
  taskType: 'scene_execute',
  name: '',
  description: '',
  boundObjectId: '',
  environmentId: undefined,
  cronExpression: '',
  enabled: true,
})

// 绑定对象选项
const sceneOptions = ref<ApiScenePageItem[]>([])
const swaggerOptions = ref<ApiSwaggerUrlItem[]>([])
const boundObjectLoading = ref(false)

const isSceneTask = computed(() => form.taskType === 'scene_execute')

// 环境列表
const environmentOptions = ref<ApiEnvironmentListItem[]>([])
const environmentLoading = ref(false)

function openCreate() {
  editingId.value = null
  form.taskType = 'scene_execute'
  form.name = ''
  form.description = ''
  form.boundObjectId = ''
  form.environmentId = undefined
  form.cronExpression = ''
  form.enabled = true
  showFormDialog.value = true
  void loadBoundObjects()
  void loadEnvironments()
}

function openEdit(item: ApiSchedulePageItem) {
  editingId.value = item.id
  form.taskType = item.taskType
  form.name = item.name
  form.description = item.description ?? ''
  form.boundObjectId = item.boundObjectId
  form.environmentId = item.environmentId ?? undefined
  form.cronExpression = item.cronExpression
  form.enabled = item.enabled
  showFormDialog.value = true
  void loadBoundObjects()
  void loadEnvironments()
}

async function loadBoundObjects() {
  boundObjectLoading.value = true
  try {
    if (isSceneTask.value) {
      const page = await fetchScenePage({ pageNo: 1, pageSize: 200 })
      sceneOptions.value = page.list
    } else {
      swaggerOptions.value = await fetchSwaggerUrlList()
    }
  } catch {
    sceneOptions.value = []
    swaggerOptions.value = []
  } finally {
    boundObjectLoading.value = false
  }
}

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

watch(() => form.taskType, () => {
  form.boundObjectId = ''
  form.environmentId = undefined
  void loadBoundObjects()
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
  if (!form.boundObjectId) {
    ElMessage.warning('请选择绑定对象')
    return
  }
  if (isSceneTask.value && !form.environmentId) {
    ElMessage.warning('场景执行任务需选择目标环境')
    return
  }
  saving.value = true
  try {
    const req: ApiScheduleSaveReq = {
      taskType: form.taskType,
      name: form.name.trim(),
      description: form.description?.trim() || undefined,
      boundObjectId: form.boundObjectId,
      environmentId: form.environmentId || undefined,
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
          <el-button type="primary" @click="openCreate">新建任务</el-button>
        </div>
      </template>

      <el-table :data="rows">
        <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.taskType === 'scene_execute' ? '场景执行' : '接口导入' }}
          </template>
        </el-table-column>
        <el-table-column prop="boundObjectName" label="绑定对象" min-width="160" show-overflow-tooltip />
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
trigger="click" @command="(cmd: string) => {
              if (cmd === 'edit') openEdit(row as ApiSchedulePageItem)
              else if (cmd === 'delete') void handleDelete(row as ApiSchedulePageItem)
            }">
              <el-button link size="small">更多</el-button>
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
        <el-form-item label="绑定对象" prop="boundObjectId" :rules="[{ required: true, message: '请选择绑定对象', trigger: 'change' }]">
          <el-select
            v-model="form.boundObjectId"
            placeholder="请选择绑定对象"
            :loading="boundObjectLoading"
            style="width: 100%"
            filterable
          >
            <template v-if="isSceneTask">
              <el-option
                v-for="s in sceneOptions"
                :key="s.id"
                :value="s.id"
                :label="s.name"
              />
            </template>
            <template v-else>
              <el-option
                v-for="s in swaggerOptions"
                :key="s.id"
                :value="s.id"
                :label="`${s.name} (${s.url})`"
              />
            </template>
          </el-select>
        </el-form-item>
        <el-form-item v-if="isSceneTask" label="目标环境" prop="environmentId" :rules="[{ required: true, message: '请选择目标环境', trigger: 'change' }]">
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
          <div class="schedules-page__form-hint">场景执行任务需指定目标环境</div>
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

.schedules-page__cron-row {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
  width: 100%;
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
