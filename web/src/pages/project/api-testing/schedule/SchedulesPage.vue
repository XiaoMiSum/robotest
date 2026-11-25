<script setup lang="ts">
import { computed } from 'vue'
import ScenePickerDialog from '@/components/project/api-testing/scene/ScenePickerDialog.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import ListWorkspace from '@/components/common/ListWorkspace.vue'
import ListTitleLink from '@/components/common/ListTitleLink.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'
import { useSchedules } from '@/composables/project/api-testing/schedule/useSchedules'

const SCHEDULE_COLUMNS: TableColumnMeta[] = [
  { key: 'name', label: '任务名称' },
  { key: 'type', label: '任务类型' },
  { key: 'target', label: '执行目标' },
  { key: 'cron', label: '调度表达式' },
  { key: 'enabled', label: '启用状态' },
  { key: 'lastExecution', label: '上次执行' },
  { key: 'actions', label: '操作', pinned: true },
]
const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'schedule-list',
  columns: SCHEDULE_COLUMNS,
})
import type { ApiSchedulePageItem } from '@/types'

const {
  rows,
  total,
  pageNo,
  pageSize,
  loading,
  hasLoaded,
  loadError,
  typeFilter,
  loadPage,
  showFormDialog,
  editingId,
  formRef,
  saving,
  form,
  isTestPlanTask,
  openCreate,
  openEdit,
  handleSave,
  moduleOptions,
  scopeOptionLoading,
  scenePickerVisible,
  selectedScenes,
  visibleSceneTags,
  sceneTagsExpanded,
  handleSceneConfirm,
  removeScene,
  environmentOptions,
  environmentLoading,
  cronValidation,
  cronValidating,
  handleValidateCron,
  handlePresetSelect,
  cronBuilder,
  showCronBuilder,
  applyCronBuilder,
  handleToggle,
  handleDelete,
  handleExecuteNow,
  showExecutionDrawer,
  executionTask,
  executionRows,
  executionTotal,
  executionPageNo,
  executionLoading,
  openExecutions,
  loadExecutions,
  triggerTypeLabel,
  formatDuration,
  SCHEDULE_TASK_TYPES,
  EXECUTION_SCOPES,
  CRON_PRESETS,
  taskExecutionSummary,
  execStatusLabel,
  execStatusType,
  formatDateTime,
  formatShortDateTime,
  MAX_VISIBLE_SCENE_TAGS,
} = useSchedules()

const hasFilters = computed(() => Boolean(typeFilter.value))
const emptyMode = computed(() =>
  loading.value ? 'loading' : loadError.value ? 'error' : hasFilters.value ? 'filtered' : 'empty',
)
const emptyTitle = computed(() => {
  if (loading.value) return '正在加载定时任务…'
  if (loadError.value) return '定时任务列表加载失败'
  if (hasFilters.value) return '无匹配结果'
  return '暂无定时任务'
})

function clearFilters() {
  typeFilter.value = ''
}

function handlePageSizeChange() {
  pageNo.value = 1
  void loadPage()
}
</script>

<template>
  <main class="schedules-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">定时任务</h1>
        <p class="page-head__desc">测试计划、接口同步与 GitLab 同步的统一周期调度</p>
      </div>
      <el-button type="primary" @click="openCreate">新建任务</el-button>
    </header>

    <ListWorkspace label="定时任务列表" :loading="loading">
      <template #toolbar>
        <el-select v-model="typeFilter" placeholder="任务类型" clearable style="width: 140px">
          <el-option v-for="opt in SCHEDULE_TASK_TYPES" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <span class="schedules-page__count">共 {{ total }} 条</span>
      </template>
      <template v-if="hasLoaded && loadError" #feedback>
        <ListLoadFeedback
          mode="refresh-error"
          title="刷新失败，当前显示上次加载的数据"
          :description="loadError"
          :retrying="loading"
          @retry="loadPage"
        />
      </template>

    <div v-if="hasLoaded || !loadError" class="schedules-page__table">
      <el-table :data="rows" height="100%" stripe>
        <el-table-column v-if="visibleKeys.includes('name')" label="任务名称" min-width="180">
          <template #default="{ row }">
            <ListTitleLink :title="(row as ApiSchedulePageItem).name" @open="openEdit(row as ApiSchedulePageItem)" />
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('type')" label="任务类型" width="120">
          <template #default="{ row }">
            {{ SCHEDULE_TASK_TYPES.find((o) => o.value === (row as ApiSchedulePageItem).taskType)?.label ?? (row as ApiSchedulePageItem).taskType }}
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('target')" label="执行目标" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ taskExecutionSummary(row as ApiSchedulePageItem) }}
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('cron')" label="调度表达式" prop="cronExpression" width="140" />
        <el-table-column v-if="visibleKeys.includes('enabled')" label="启用状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="(row as ApiSchedulePageItem).enabled" @change="handleToggle(row as ApiSchedulePageItem)" />
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('lastExecution')" label="上次执行" min-width="160">
          <template #default="{ row }">
            <template v-if="(row as ApiSchedulePageItem).lastExecutionStatus">
              <el-tag size="small" :type="execStatusType((row as ApiSchedulePageItem).lastExecutionStatus!)">
                {{ execStatusLabel((row as ApiSchedulePageItem).lastExecutionStatus!) }}
              </el-tag>
              <span class="text-muted text-xs ml-4">{{ formatShortDateTime((row as ApiSchedulePageItem).lastExecutionAt) }}</span>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #header>
            <div class="schedules-page__action-header">
              <span>操作</span>
              <TableColumnSetting
                icon-only
                :columns="SCHEDULE_COLUMNS"
                :visible-keys="visibleKeys"
                @change="(key, visible) => setVisible(key, visible)"
                @select-all="selectAll"
                @reset="reset"
              />
            </div>
          </template>
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row as ApiSchedulePageItem)">编辑</el-button>
            <el-button link type="primary" size="small" @click="handleExecuteNow(row as ApiSchedulePageItem)">立即执行</el-button>
            <el-button link type="primary" size="small" @click="openExecutions(row as ApiSchedulePageItem)">执行记录</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as ApiSchedulePageItem)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <ListLoadFeedback
            v-if="loading || !hasLoaded || !loadError"
            :mode="emptyMode"
            :title="emptyTitle"
            :description="loadError"
            :create-label="!hasFilters && !loadError ? '新建任务' : undefined"
            @retry="loadPage"
            @reset="clearFilters"
            @create="openCreate"
          />
        </template>
      </el-table>
    </div>

    <div v-else class="schedules-page__first-error">
      <ListLoadFeedback mode="error" :title="emptyTitle" :description="loadError" :retrying="loading" @retry="loadPage" />
    </div>

      <template v-if="hasLoaded || !loadError" #pagination>
        <ListPagination
          v-model:page-no="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          @change="loadPage"
          @size-change="handlePageSizeChange"
        />
      </template>
    </ListWorkspace>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="showFormDialog"
      :title="editingId ? '编辑定时任务' : '新建定时任务'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" placeholder="可选" />
        </el-form-item>
        <el-form-item label="任务类型" required>
          <el-radio-group v-model="form.taskType" :disabled="!!editingId">
            <el-radio v-for="opt in SCHEDULE_TASK_TYPES" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 测试计划任务 -->
        <template v-if="isTestPlanTask">
          <el-form-item label="执行方式" required>
            <el-radio-group v-model="form.executionScope">
              <el-radio v-for="opt in EXECUTION_SCOPES" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.executionScope === 'modules'" label="选择模块" required>
            <el-tree-select
              v-model="form.moduleIds"
              :data="moduleOptions"
              :loading="scopeOptionLoading"
              multiple
              show-checkbox
              check-strictly
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              placeholder="请选择模块"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-if="form.executionScope === 'scenes'" label="选择场景" required>
            <div class="scene-tags">
              <el-tag
                v-for="scene in visibleSceneTags"
                :key="scene.id"
                closable
                class="scene-tag"
                @close="removeScene(scene.id)"
              >
                {{ scene.name }}
              </el-tag>
              <el-button v-if="selectedScenes.length > MAX_VISIBLE_SCENE_TAGS" link type="primary" @click="sceneTagsExpanded = !sceneTagsExpanded">
                {{ sceneTagsExpanded ? '收起' : `+${selectedScenes.length - MAX_VISIBLE_SCENE_TAGS}` }}
              </el-button>
              <el-button size="small" @click="scenePickerVisible = true">选择场景</el-button>
            </div>
          </el-form-item>
          <el-form-item label="目标环境" required>
            <el-select v-model="form.environmentId" :loading="environmentLoading" placeholder="请选择执行环境" style="width: 100%">
              <el-option v-for="env in environmentOptions" :key="env.id" :label="env.name" :value="env.id" />
            </el-select>
          </el-form-item>
        </template>

        <!-- Swagger 同步任务 -->
        <template v-else>
          <el-form-item label="文档地址" required>
            <el-input v-model="form.openapiUrl" placeholder="请输入 Swagger/OpenAPI 文档 URL" />
          </el-form-item>
        </template>

        <el-form-item label="调度策略" required>
          <div class="cron-field">
            <el-select placeholder="预设" style="width: 160px" clearable @change="handlePresetSelect">
              <el-option v-for="preset in CRON_PRESETS" :key="preset.expression" :label="preset.label" :value="preset.expression" />
            </el-select>
            <el-input v-model="form.cronExpression" placeholder="Cron 表达式" style="flex: 1" />
            <el-button :loading="cronValidating" @click="handleValidateCron">校验</el-button>
            <el-button @click="showCronBuilder = !showCronBuilder">{{ showCronBuilder ? '收起' : '构建' }}</el-button>
          </div>
          <div v-if="cronValidation" class="cron-validation" :class="{ 'is-invalid': !cronValidation.valid }">
            <template v-if="cronValidation.valid">
              <span>{{ cronValidation.description }}</span>
              <ul v-if="cronValidation.nextExecutions?.length" class="cron-next">
                <li v-for="(t, i) in cronValidation.nextExecutions" :key="i">{{ t }}</li>
              </ul>
            </template>
            <span v-else class="text-danger">Cron 表达式无效</span>
          </div>
          <div v-if="showCronBuilder" class="cron-builder">
            <el-input v-model="cronBuilder.minute" placeholder="分" style="width: 80px" />
            <el-input v-model="cronBuilder.hour" placeholder="时" style="width: 80px" />
            <el-input v-model="cronBuilder.day" placeholder="日" style="width: 80px" />
            <el-input v-model="cronBuilder.month" placeholder="月" style="width: 80px" />
            <el-input v-model="cronBuilder.weekday" placeholder="周" style="width: 80px" />
            <el-button @click="applyCronBuilder">应用</el-button>
          </div>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <ScenePickerDialog
      v-model="scenePickerVisible"
      :initial-selected-ids="form.sceneIds"
      @confirm="handleSceneConfirm"
    />

    <!-- 执行记录抽屉 -->
    <el-drawer v-model="showExecutionDrawer" :title="`执行记录 - ${executionTask?.name ?? ''}`" size="720px">
      <div v-loading="executionLoading">
        <el-table :data="executionRows" stripe>
          <el-table-column label="触发方式" width="90">
            <template #default="{ row }">{{ triggerTypeLabel(row.triggerType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="execStatusType(row.status)">{{ execStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column label="结果" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.errorMessage" class="text-danger">{{ row.errorMessage }}</span>
              <span v-else-if="row.reportId">报告: {{ row.reportId }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
        </el-table>
        <ListPagination
          v-model:page-no="executionPageNo"
          :page-size="10"
          :total="executionTotal"
          bare
          hide-sizes
          @change="loadExecutions"
        />
      </div>
    </el-drawer>
  </main>
</template>

<style scoped lang="scss">
.schedules-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  flex-shrink: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
}

.page-head__desc {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}

.schedules-page__count {
  margin-left: auto;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.schedules-page__table {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.schedules-page__first-error {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  padding: 14px 0;
}

.text-muted {
  color: var(--el-text-color-secondary);
}

.text-xs {
  font-size: 12px;
}

.ml-4 {
  margin-left: 4px;
}

.text-danger {
  color: var(--el-color-danger);
}

.scene-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.scene-tag {
  max-width: 200px;
}

.cron-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.cron-validation {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  &.is-invalid {
    color: var(--el-color-danger);
  }
}

.cron-next {
  margin: 4px 0 0;
  padding-left: 18px;
}

.cron-builder {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.schedules-page__action-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  width: 100%;
}
</style>
