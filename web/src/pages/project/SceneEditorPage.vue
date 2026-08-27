<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiSceneDetail, ApiSceneStepItem, ApiSceneVariableItem, ApiExecutionHistoryItem, ApiChangeHistoryItem, ApiSceneStepDebugResp } from '@/types'
import {
  createScene,
  fetchSceneDetail,
  updateScene,
  deleteScene,
  copyScene,
  executeScene,
  getExecutionStatus,
  cancelExecution,
  debugStep,
  fetchExecutionHistory,
  fetchChangeHistory,
  deleteSceneStep,
  reorderSceneSteps,
  updateSceneStep,
  updateSceneSettings,
  updateSceneVariables,
  copySceneStep,
  followScene,
  unfollowScene,
} from '@/services/apiScene'
import { formatDateTime } from '@/utils/format'
import { sortedSteps, FAILURE_RULE_OPTIONS } from './scenesModel'
import StepCanvas from './scenes/StepCanvas.vue'
import StepEditorDrawer from './scenes/StepEditorDrawer.vue'
import AssociatedInterfacesPanel from './scenes/AssociatedInterfacesPanel.vue'
import StepDebugResultDialog from './scenes/StepDebugResultDialog.vue'

const props = defineProps<{ sceneId?: string; createMode?: boolean; moduleId?: string }>()
const emit = defineEmits<{ (e: 'back'): void; (e: 'edit', id: string): void }>()

// ==================== 场景数据 ====================
const loading = ref(false)
const saving = ref(false)
const detail = ref<ApiSceneDetail | null>(null)
const activeTab = ref('steps')
const dirty = ref(false)

// 编辑态字段
const editName = ref('')
const editDescription = ref('')
const editModuleId = ref<string | null>(null)
const editEnvironmentId = ref<string | null>(null)
const editFailureRule = ref('all')

// 关注状态
const followed = ref(false)

// 跟踪脏状态
watch([editName, editDescription, editFailureRule], () => { dirty.value = true })

// ==================== 加载场景 ====================
async function loadDetail() {
  if (!props.sceneId) return
  loading.value = true
  try {
    detail.value = await fetchSceneDetail(props.sceneId)
    editName.value = detail.value.name
    editDescription.value = detail.value.description ?? ''
    editModuleId.value = detail.value.moduleId ?? null
    editEnvironmentId.value = detail.value.environmentId ?? null
    editFailureRule.value = detail.value.failureRule
    followed.value = detail.value.followed ?? false
    dirty.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '场景加载失败')
  } finally {
    loading.value = false
  }
}

// ==================== 保存场景 ====================
async function handleSave(): Promise<boolean> {
  if (!editName.value.trim()) { ElMessage.warning('请填写场景名称'); return false }
  saving.value = true
  try {
    if (props.sceneId && detail.value) {
      await updateScene(props.sceneId, {
        name: editName.value.trim(),
        description: editDescription.value.trim() || undefined,
        moduleId: editModuleId.value,
        environmentId: editEnvironmentId.value,
        failureRule: editFailureRule.value,
        variables: detail.value.variables,
        processors: detail.value.processors,
        cookieConfig: detail.value.cookieConfig,
        changeVersion: detail.value.changeVersion,
      })
      ElMessage.success('已保存')
      await loadDetail()
      return true
    } else {
      await createScene({
        name: editName.value.trim(),
        description: editDescription.value.trim() || undefined,
        moduleId: props.moduleId ?? editModuleId.value,
        failureRule: editFailureRule.value,
      })
      ElMessage.success('已创建')
      emit('back')
      return true
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
    return false
  } finally {
    saving.value = false
  }
}

// ==================== 离开确认 ====================
function handleBack() {
  if (dirty.value) {
    ElMessageBox.confirm('存在未保存的修改，确定离开？', '提示', {
      confirmButtonText: '保存并离开',
      cancelButtonText: '直接离开',
      distinguishCancelAndClose: true,
      type: 'warning',
    }).then(async () => {
      const ok = await handleSave()
      if (ok) emit('back')
    }).catch((action: string) => {
      if (action === 'cancel') emit('back')
    })
  } else {
    emit('back')
  }
}

// ==================== 环境选择弹窗 ====================
const showEnvDialog = ref(false)
const execEnvId = ref<string | null>(null)

function handleExecute() {
  execEnvId.value = editEnvironmentId.value
  showEnvDialog.value = true
}

async function confirmExecute() {
  showEnvDialog.value = false
  if (!props.sceneId) return
  if (dirty.value) { const ok = await handleSave(); if (!ok) return }
  executing.value = true
  try {
    const resp = await executeScene(props.sceneId, { environmentId: execEnvId.value })
    executionId.value = resp.executionId
    ElMessage.success('执行已启动')
    startPolling()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行启动失败')
    executing.value = false
  }
}

// ==================== 执行 & 轮询 ====================
const executing = ref(false)
const executionId = ref<string | null>(null)
const execProgress = ref({ current: 0, total: 0 })
let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!props.sceneId || !executionId.value) return
    try {
      const status = await getExecutionStatus(props.sceneId, executionId.value)
      execProgress.value = { current: status.currentStepIndex ?? 0, total: status.totalSteps ?? 0 }
      if (['success', 'failed', 'error'].includes(status.status)) {
        stopPolling()
        executing.value = false
        executionId.value = null
        ElMessage.info(`执行${status.status === 'success' ? '成功' : '失败'}`)
        await loadDetail()
      }
    } catch {
      stopPolling()
      executing.value = false
    }
  }, 2000)
}

function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }

async function handleCancelExecution() {
  if (!props.sceneId || !executionId.value) return
  try {
    await cancelExecution(props.sceneId, executionId.value)
    ElMessage.success('已取消执行')
    stopPolling()
    executing.value = false
    executionId.value = null
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消失败')
  }
}

onUnmounted(() => stopPolling())

// ==================== 更多操作 ====================
async function toggleFollow() {
  if (!props.sceneId) return
  try {
    if (followed.value) await unfollowScene(props.sceneId)
    else await followScene(props.sceneId)
    followed.value = !followed.value
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleCopyScene() {
  if (!props.sceneId) return
  try {
    const newId = await copyScene(props.sceneId)
    ElMessage.success('已复制场景')
    emit('edit', newId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制失败')
  }
}

async function handleDeleteScene() {
  if (!props.sceneId) return
  await ElMessageBox.confirm('删除场景后不可恢复，确定删除？', '删除场景', { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' })
  try {
    await deleteScene(props.sceneId)
    ElMessage.success('已删除')
    emit('back')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ==================== 报告 & 定时任务 ====================
function handleViewReport(reportId: string) {
  emit('edit', `report:${reportId}`)
}
function handleCreateSchedule() {
  if (!props.sceneId) return
  emit('edit', `schedule:${props.sceneId}`)
}

// ==================== 步骤操作 ====================
const showStepEditor = ref(false)
const editingStep = ref<ApiSceneStepItem | null>(null)

function handleAddStep() { editingStep.value = null; showStepEditor.value = true }
function handleEditStep(step: ApiSceneStepItem) { editingStep.value = step; showStepEditor.value = true }

async function handleDeleteStep(step: ApiSceneStepItem) {
  if (!props.sceneId) return
  await ElMessageBox.confirm(`删除步骤「${step.name}」？`, '删除步骤', { type: 'warning' })
  try {
    await deleteSceneStep(props.sceneId, step.id)
    ElMessage.success('步骤已删除')
    await loadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function handleToggleStep(step: ApiSceneStepItem) {
  if (!props.sceneId) return
  try {
    await updateSceneStep(props.sceneId, step.id, {
      name: step.name,
      stepType: step.stepType,
      enabled: !step.enabled,
      requestConfig: step.requestConfig,
      processors: step.processors,
      validators: step.validators,
      extractors: step.extractors,
    })
    step.enabled = !step.enabled
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleReorderSteps(newSteps: ApiSceneStepItem[]) {
  if (!props.sceneId) return
  try {
    await reorderSceneSteps(props.sceneId, { stepIds: newSteps.map((s) => s.id) })
    if (detail.value) detail.value.steps = newSteps
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '排序失败')
  }
}

async function handleCopyStep(step: ApiSceneStepItem) {
  if (!props.sceneId) return
  try {
    await copySceneStep(props.sceneId, step.id)
    ElMessage.success('已复制步骤')
    await loadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制失败')
  }
}

async function handleStepSaved() { showStepEditor.value = false; await loadDetail() }

// ==================== 单步调试 ====================
const debugResult = ref<ApiSceneStepDebugResp | null>(null)
const showDebugResult = ref(false)
const debugStepId = ref<string | null>(null)

async function handleDebugStep(step: ApiSceneStepItem) {
  if (!props.sceneId || executing.value) return
  debugStepId.value = step.id
  try {
    const resp = await debugStep(props.sceneId, step.id)
    debugResult.value = resp
    showDebugResult.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '调试失败')
  } finally {
    debugStepId.value = null
  }
}

function handleInsertBefore(index: number) {
  editingStep.value = null
  showStepEditor.value = true
  void index
}

// ==================== 变量管理 ====================
const editVariables = ref<{ name: string; value: string; description: string }[]>([])

watch(detail, (d) => {
  if (d) editVariables.value = d.variables.map((v: ApiSceneVariableItem) => ({ ...v, value: v.value ?? '', description: v.description ?? '' }))
})

async function handleSaveVariables() {
  if (!props.sceneId || !detail.value) return
  try {
    await updateSceneVariables(props.sceneId, { variables: editVariables.value.filter((v) => v.name.trim()) })
    ElMessage.success('变量已保存')
    dirty.value = false
    await loadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

function addVariable() { editVariables.value.push({ name: '', value: '', description: '' }) }
function removeVariable(index: number) { editVariables.value.splice(index, 1) }

// ==================== 设置 ====================
const editCookieSharedEnabled = ref(false)
const editCookieItems = ref<{ key: string; value: string; domain: string; enabled: boolean }[]>([])

watch(detail, (d) => {
  if (d?.cookieConfig) {
    const cc = d.cookieConfig as Record<string, unknown>
    editCookieSharedEnabled.value = Boolean(cc.sharedEnabled)
    editCookieItems.value = (cc.items as Array<Record<string, unknown>> ?? []).map((item) => ({
      key: String(item.key ?? ''),
      value: String(item.value ?? ''),
      domain: String(item.domain ?? ''),
      enabled: item.enabled !== false,
    }))
  }
})

const editProcessors = ref<{ name: string; type: string; script: string }[]>([])

watch(detail, (d) => {
  if (d?.processors) {
    editProcessors.value = d.processors.map((p) => ({
      name: String((p as Record<string, unknown>).name ?? ''),
      type: String((p as Record<string, unknown>).type ?? 'pre'),
      script: String((p as Record<string, unknown>).script ?? ''),
    }))
  }
})

async function handleSaveSettings() {
  if (!props.sceneId) return
  try {
    await updateSceneSettings(props.sceneId, {
      failureRule: editFailureRule.value,
      cookieConfig: {
        sharedEnabled: editCookieSharedEnabled.value,
        items: editCookieItems.value.filter((c) => c.key.trim()),
      },
    })
    if (detail.value) {
      await updateScene(props.sceneId, {
        name: detail.value.name,
        moduleId: detail.value.moduleId ?? undefined,
        failureRule: editFailureRule.value,
        variables: detail.value.variables,
        processors: editProcessors.value.map((p) => ({ name: p.name, type: p.type, script: p.script })),
        cookieConfig: {
          sharedEnabled: editCookieSharedEnabled.value,
          items: editCookieItems.value.filter((c) => c.key.trim()),
        },
        changeVersion: detail.value.changeVersion,
      })
    }
    ElMessage.success('设置已保存')
    dirty.value = false
    await loadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

// ==================== 历史 ====================
const executionHistory = ref<ApiExecutionHistoryItem[]>([])
const executionHistoryTotal = ref(0)
const executionHistoryPage = ref(1)
const changeHistory = ref<ApiChangeHistoryItem[]>([])
const changeHistoryTotal = ref(0)
const changeHistoryPage = ref(1)
const historyLoading = ref(false)

async function loadHistory(page?: number) {
  if (!props.sceneId) return
  if (page !== undefined) {
    if (activeTab.value === 'history') {
      // determine which sub-table triggered pagination
    }
  }
  historyLoading.value = true
  try {
    const [execResp, changeResp] = await Promise.all([
      fetchExecutionHistory(props.sceneId, executionHistoryPage.value, 20),
      fetchChangeHistory(props.sceneId, changeHistoryPage.value, 20),
    ])
    executionHistory.value = execResp.list
    executionHistoryTotal.value = execResp.total
    changeHistory.value = changeResp.list
    changeHistoryTotal.value = changeResp.total
  } catch { /* 静默 */ } finally { historyLoading.value = false }
}

watch(activeTab, (tab) => { if (tab === 'history') void loadHistory() })

// ==================== 关联接口刷新 ====================
function handleAssociationRefresh() { void loadDetail() }

// ==================== 计算属性 ====================
const sorted = computed(() => (detail.value ? sortedSteps(detail.value.steps) : []))
const isCreateMode = computed(() => props.createMode || !props.sceneId)

onMounted(async () => { if (props.sceneId) await loadDetail() })
</script>

<template>
  <div v-loading="loading" class="scene-editor">
    <header class="scene-editor__header">
      <el-button text @click="handleBack">
        <el-icon><ArrowLeft /></el-icon> 返回列表
      </el-button>
      <el-button
        v-if="!isCreateMode"
        text
        :type="followed ? 'warning' : 'info'"
        @click="toggleFollow"
      >
        {{ followed ? '★ 已关注' : '☆ 关注' }}
      </el-button>
      <el-input v-model="editName" placeholder="场景名称" style="width: 320px" data-test="scene-name-input" />
      <div class="scene-editor__header-spacer" />

      <!-- 执行进度 -->
      <template v-if="executing">
        <span class="scene-editor__progress-text">执行中 {{ execProgress.current }}/{{ execProgress.total }}</span>
        <el-button size="small" type="danger" @click="handleCancelExecution">取消执行</el-button>
      </template>

      <el-dropdown v-if="!isCreateMode" trigger="click">
        <el-button text>更多</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleCopyScene">复制场景</el-dropdown-item>
            <el-dropdown-item @click="handleCreateSchedule">创建定时任务</el-dropdown-item>
            <el-dropdown-item divided style="color: var(--el-color-danger)" @click="handleDeleteScene">删除场景</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button :disabled="saving" @click="handleSave">{{ isCreateMode ? '创建' : '保存' }}</el-button>
      <el-button v-if="!isCreateMode" type="primary" :loading="executing" @click="handleExecute">执行</el-button>
    </header>

    <template v-if="detail || isCreateMode">
      <el-tabs v-model="activeTab" class="scene-editor__tabs">
        <!-- ==================== 步骤标签页 ==================== -->
        <el-tab-pane label="步骤编排" name="steps">
          <div class="scene-editor__desc">
            <el-input v-model="editDescription" type="textarea" :rows="2" placeholder="场景描述（可选）" />
          </div>
          <StepCanvas
            v-if="detail"
            :steps="sorted"
            :is-executing="executing"
            @add="handleAddStep"
            @edit="handleEditStep"
            @delete="handleDeleteStep"
            @toggle="handleToggleStep"
            @reorder="handleReorderSteps"
            @copy="handleCopyStep"
            @debug="handleDebugStep"
            @insert-before="handleInsertBefore"
          />
          <div v-else class="scene-editor__empty">
            <el-empty description="请先保存场景，然后添加测试步骤" />
            <el-button type="primary" @click="handleSave">保存并继续</el-button>
          </div>

          <!-- 关联接口面板 -->
          <div v-if="detail && !isCreateMode" class="scene-editor__assoc-panel">
            <AssociatedInterfacesPanel :scene-id="props.sceneId ?? ''" @refresh="handleAssociationRefresh" />
          </div>
        </el-tab-pane>

        <!-- ==================== 变量标签页 ==================== -->
        <el-tab-pane label="场景变量" name="variables">
          <div class="scene-editor__section">
            <div class="scene-editor__section-header">
              <span>场景变量</span>
              <el-button size="small" @click="addVariable">+ 添加变量</el-button>
            </div>
            <el-table :data="editVariables" size="small">
              <el-table-column label="变量名" min-width="160">
                <template #default="{ row }"><el-input v-model="row.name" size="small" placeholder="name" /></template>
              </el-table-column>
              <el-table-column label="值" min-width="200">
                <template #default="{ row }"><el-input v-model="row.value" size="small" placeholder="值（支持 ${} 引用）" /></template>
              </el-table-column>
              <el-table-column label="描述" min-width="180">
                <template #default="{ row }"><el-input v-model="row.description" size="small" placeholder="描述" /></template>
              </el-table-column>
              <el-table-column width="60">
                <template #default="{ $index }"><el-button link size="small" type="danger" @click="removeVariable($index as number)">删除</el-button></template>
              </el-table-column>
              <template #empty><span class="scene-editor__empty-text">暂无变量</span></template>
            </el-table>
            <el-button style="margin-top: var(--space-md)" @click="handleSaveVariables">保存变量</el-button>
          </div>
        </el-tab-pane>

        <!-- ==================== 设置标签页 ==================== -->
        <el-tab-pane label="场景设置" name="settings">
          <div class="scene-editor__section">
            <div class="scene-editor__field">
              <label>失败规则</label>
              <el-radio-group v-model="editFailureRule">
                <el-radio v-for="opt in FAILURE_RULE_OPTIONS" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                  <span class="scene-editor__field-hint">{{ opt.desc }}</span>
                </el-radio>
              </el-radio-group>
            </div>

            <!-- Cookie 配置 -->
            <div class="scene-editor__field">
              <label>Cookie 配置</label>
              <el-checkbox v-model="editCookieSharedEnabled">启用共享 Cookie（覆盖环境默认配置 Cookie 与场景变量 Cookie）</el-checkbox>
              <div v-if="editCookieSharedEnabled" class="scene-editor__cookie-items">
                <div class="scene-editor__section-header">
                  <span>Cookie 条目</span>
                  <el-button size="small" @click="editCookieItems.push({ key: '', value: '', domain: '', enabled: true })">+ 添加</el-button>
                </div>
                <el-table :data="editCookieItems" size="small">
                  <el-table-column label="启用" width="60">
                    <template #default="{ row }"><el-switch v-model="row.enabled" size="small" /></template>
                  </el-table-column>
                  <el-table-column label="Key" min-width="120">
                    <template #default="{ row }"><el-input v-model="row.key" size="small" placeholder="cookie name" /></template>
                  </el-table-column>
                  <el-table-column label="Value" min-width="160">
                    <template #default="{ row }"><el-input v-model="row.value" size="small" placeholder="cookie value" /></template>
                  </el-table-column>
                  <el-table-column label="Domain" width="160">
                    <template #default="{ row }"><el-input v-model="row.domain" size="small" placeholder="可选 domain" /></template>
                  </el-table-column>
                  <el-table-column width="50">
                    <template #default="{ $index }"><el-button link size="small" type="danger" @click="editCookieItems.splice($index, 1)">删除</el-button></template>
                  </el-table-column>
                </el-table>
              </div>
            </div>

            <!-- 场景处理器 -->
            <div class="scene-editor__field">
              <label>场景处理器（前置/后置）</label>
              <div v-if="detail?.processors?.length" class="scene-editor__processors-list">
                <div v-for="(proc, idx) in editProcessors" :key="idx" class="scene-editor__processor-row">
                  <el-input v-model="proc.name" size="small" placeholder="处理器名称" style="flex:1" />
                  <el-select v-model="proc.type" size="small" style="width:120px" placeholder="类型">
                    <el-option value="pre" label="前置处理器" />
                    <el-option value="post" label="后置处理器" />
                  </el-select>
                  <el-input v-model="proc.script" size="small" type="textarea" :rows="2" placeholder="脚本内容" style="flex:2" />
                  <el-button link size="small" type="danger" @click="editProcessors.splice(idx, 1)">删除</el-button>
                </div>
              </div>
              <div v-else style="color:var(--color-neutral-400);font-size:var(--font-size-sm);margin-bottom:var(--space-sm)">暂无处理器</div>
              <el-button size="small" @click="editProcessors.push({ name: '', type: 'pre', script: '' })">+ 添加处理器</el-button>
            </div>

            <el-button style="margin-top: var(--space-md)" @click="handleSaveSettings">保存设置</el-button>
          </div>
        </el-tab-pane>

        <!-- ==================== 历史标签页 ==================== -->
        <el-tab-pane label="执行历史" name="history">
          <div v-loading="historyLoading" class="scene-editor__section">
            <div class="scene-editor__section-header"><span>执行历史</span></div>
            <el-table :data="executionHistory" size="small">
              <el-table-column prop="executedAt" label="执行时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.executedAt) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="triggerType" label="触发方式" width="100" />
              <el-table-column prop="durationMs" label="耗时" width="100">
                <template #default="{ row }">{{ row.durationMs }}ms</template>
              </el-table-column>
              <el-table-column label="报告" width="80">
                <template #default="{ row }">
                  <el-button v-if="row.reportId" link size="small" type="primary" @click="handleViewReport(row.reportId)">查看</el-button>
                  <span v-else class="scene-editor__empty-text">-</span>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-if="executionHistoryTotal > 20"
              layout="prev, pager, next"
              :total="executionHistoryTotal"
              :page-size="20"
              :current-page="executionHistoryPage"
              size="small"
              style="margin-top: var(--space-sm); justify-content: flex-end"
              @current-change="(p: number) => { executionHistoryPage = p; loadHistory() }"
            />
            <el-divider />
            <div class="scene-editor__section-header"><span>变更历史</span></div>
            <el-table :data="changeHistory" size="small">
              <el-table-column prop="version" label="版本" width="80" />
              <el-table-column prop="changeSummary" label="变更内容" min-width="200" />
              <el-table-column prop="operatorName" label="操作人" width="100" />
              <el-table-column prop="changedAt" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.changedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-if="changeHistoryTotal > 20"
              layout="prev, pager, next"
              :total="changeHistoryTotal"
              :page-size="20"
              :current-page="changeHistoryPage"
              size="small"
              style="margin-top: var(--space-sm); justify-content: flex-end"
              @current-change="(p: number) => { changeHistoryPage = p; loadHistory() }"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 步骤编辑抽屉 -->
    <StepEditorDrawer v-model="showStepEditor" :scene-id="props.sceneId ?? ''" :step="editingStep" @saved="handleStepSaved" />

    <!-- 单步调试结果弹窗 -->
    <StepDebugResultDialog v-model="showDebugResult" :result="debugResult" />

    <!-- 执行环境选择弹窗 -->
    <el-dialog v-model="showEnvDialog" title="执行配置" width="400px">
      <el-form label-position="top">
        <el-form-item label="执行环境（可选）">
          <el-input v-model="execEnvId" placeholder="留空使用场景默认环境" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEnvDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmExecute">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.scene-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);
}

.scene-editor__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.scene-editor__header-spacer { flex: 1; }

.scene-editor__progress-text {
  font-size: var(--font-size-sm);
  color: var(--color-primary-600, #2563eb);
  margin-right: var(--space-sm);
}

.scene-editor__tabs {
  flex: 1;
  min-height: 0;

  :deep(.el-tabs__content) { flex: 1; overflow-y: auto; }
}

.scene-editor__desc { margin-bottom: var(--space-md); }

.scene-editor__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xl);
}

.scene-editor__section { padding: var(--space-md) 0; }

.scene-editor__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
  font-weight: 600;
}

.scene-editor__field {
  margin-bottom: var(--space-md);

  label {
    display: block;
    margin-bottom: var(--space-xs);
    font-weight: 500;
    font-size: var(--font-size-sm);
  }
}

.scene-editor__field-hint {
  color: var(--color-neutral-400);
  margin-left: 4px;
  font-size: 12px;
}

.scene-editor__assoc-panel { margin-top: var(--space-lg); }

.scene-editor__empty-text { color: var(--color-neutral-400); }

.scene-editor__cookie-items {
  margin-top: var(--space-sm);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  padding: var(--space-sm);
}

.scene-editor__processors-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.scene-editor__processor-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}
</style>
