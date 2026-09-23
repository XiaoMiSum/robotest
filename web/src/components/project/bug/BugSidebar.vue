<script setup lang="ts">
import { formatDateTime } from '@/utils/format'
import type { BugDetail, ProjectModule, TestCaseNode, TestPlanListItem, WorkspaceMember } from '@/types'
import type { Badge } from '@/minder/badges'

export interface BugSidebarForm {
  severity: string
  priority: string
  bugType: string
  moduleId: string
  keywords: string
  dueDate: string
  assigneeId: string
  relatedCaseId: string
  relatedPlanId: string
}

defineProps<{
  detail: BugDetail
  form: BugSidebarForm
  isClosed: boolean
  isResolved: boolean
  isRejected: boolean
  dirTree: ProjectModule[]
  planOptions: TestPlanListItem[]
  relatedPlanName: string
  memberOptions: WorkspaceMember[]
  severityLabel: Record<string, string>
  priorityLabel: Record<string, string>
  severityType: Record<string, 'primary' | 'danger' | 'warning' | 'info'>
  priorityType: Record<string, 'primary' | 'warning' | 'info'>
  bugResolutionLabel: Record<string, string>
  bugTypeLabel: Record<string, string>
  currentRelatedCaseId: string
  caseDetail: TestCaseNode | null
  caseDetailLoading: boolean
  caseDetailRows: { id: string; depth: number; badge: Badge; title: string }[]
  caseDocName: string
  loadCaseDetail: () => void
  openCaseDocument: () => void
}>()

const emit = defineEmits<{
  'open-case-selector': []
  'clear-case': []
}>()
</script>

<template>
  <div class="bug-detail__side">
    <el-card v-if="isResolved || isClosed" shadow="never" class="bug-detail__resolution">
      <template #header><span class="bug-detail__section">解决信息</span></template>
      <el-descriptions :column="1" size="small">
        <el-descriptions-item label="解决方案">
          <el-tag v-if="detail.resolution" size="small" type="success" effect="light" round>
            {{ bugResolutionLabel[detail.resolution] }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.duplicateOfBugId" label="重复缺陷">
          <el-link
            type="primary"
            underline="never"
            :href="`/workspace/projects/bugs/${detail.duplicateOfBugId}`"
          >
            查看原始缺陷
          </el-link>
        </el-descriptions-item>
        <el-descriptions-item label="解决人">
          {{ detail.resolvedBy ? `${detail.resolvedBy.name}（${formatDateTime(detail.resolvedAt!)}）` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="isClosed" label="关闭人">
          {{ detail.closedBy ? `${detail.closedBy.name}（${formatDateTime(detail.closedAt!)}）` : '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span class="bug-detail__section">属性</span></template>
      <el-form label-position="top" class="bug-detail__props">
        <el-form-item label="缺陷类型">
          <el-select v-if="!isClosed" v-model="form.bugType">
            <el-option v-for="(label, key) in bugTypeLabel" :key="key" :label="label" :value="key" />
          </el-select>
          <span v-else class="bug-detail__text">{{ bugTypeLabel[detail.bugType] }}</span>
        </el-form-item>
        <el-form-item label="所属模块">
          <el-tree-select
            v-if="!isClosed"
            v-model="form.moduleId"
            :data="dirTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            placeholder="选择所属模块"
          />
          <span v-else class="bug-detail__text">{{ detail.moduleName ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="严重等级">
          <el-select v-if="!isClosed" v-model="form.severity">
            <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key">
              <span class="bug-detail__severity-dot" :class="`bug-detail__severity-dot--${key}`" />{{ label }}
            </el-option>
          </el-select>
          <el-tag v-else :type="severityType[detail.severity]" size="small" effect="light" round>
            {{ severityLabel[detail.severity] }}
          </el-tag>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-if="!isClosed" v-model="form.priority">
            <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
          </el-select>
          <el-tag v-else :type="priorityType[detail.priority]" size="small" effect="light" round>
            {{ priorityLabel[detail.priority] }}
          </el-tag>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-if="!isClosed"
            v-model="form.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择截止日期"
            class="bug-detail__date"
          />
          <span v-else class="bug-detail__text">{{ detail.dueDate ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-if="!isClosed" v-model="form.keywords" maxlength="255" placeholder="多个关键词用空格分隔" />
          <span v-else class="bug-detail__text">{{ detail.keywords || '-' }}</span>
        </el-form-item>
        <el-form-item label="指派给">
          <el-select v-if="!isClosed" v-model="form.assigneeId" filterable>
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
          <span v-else class="bug-detail__text">{{ detail.assignee?.name ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="关联用例">
          <div class="bug-detail__relation">
            <el-popover
              v-if="currentRelatedCaseId"
              placement="left-start"
              :width="380"
              trigger="hover"
              @before-enter="loadCaseDetail"
            >
              <template #reference>
                <span class="bug-detail__text bug-detail__case-trigger">已关联 1 个用例</span>
              </template>
              <div v-loading="caseDetailLoading" class="bug-detail__case-pop">
                <template v-if="caseDetail">
                  <div class="bug-detail__case-pop-header">
                    <span class="bug-detail__case-pop-title">{{ caseDetail.title }}</span>
                    <span
                      v-if="caseDetail.priority"
                      class="bug-detail__case-pop-priority"
                      :class="`bug-detail__case-pop-priority--${caseDetail.priority.toLowerCase()}`"
                    >
                      {{ caseDetail.priority }}
                    </span>
                  </div>
                  <div v-if="caseDocName" class="bug-detail__case-pop-doc">所属文档：{{ caseDocName }}</div>
                  <div v-if="caseDetailRows.length" class="bug-detail__case-pop-body">
                    <div
                      v-for="row in caseDetailRows"
                      :key="row.id"
                      class="bug-detail__case-pop-row"
                      :style="{ paddingLeft: `${row.depth * 14}px` }"
                    >
                      <span
                        class="bug-detail__case-pop-type"
                        :style="{ background: row.badge.color }"
                      >
                        {{ row.badge.label }}
                      </span>
                      <span class="bug-detail__case-pop-text">{{ row.title }}</span>
                    </div>
                  </div>
                  <div class="bug-detail__case-pop-footer">
                    <el-link type="primary" underline="never" @click="openCaseDocument">
                      <el-icon><Position /></el-icon>打开所在文档
                    </el-link>
                  </div>
                </template>
                <el-empty
                  v-else-if="!caseDetailLoading"
                  description="用例不存在或已删除"
                  :image-size="40"
                />
              </div>
            </el-popover>
            <span v-else class="bug-detail__text">{{ isClosed ? '-' : '未关联' }}</span>
            <template v-if="!isClosed">
              <el-button size="small" @click="emit('open-case-selector')">
                {{ form.relatedCaseId ? '更换' : '选择用例' }}
              </el-button>
              <el-button v-if="form.relatedCaseId" size="small" link type="danger" @click="emit('clear-case')">
                清除
              </el-button>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="关联计划">
          <el-select v-if="!isClosed" v-model="form.relatedPlanId" filterable clearable placeholder="选择计划（可选）">
            <el-option v-for="p in planOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <span v-else class="bug-detail__text">{{ relatedPlanName }}</span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.bug-detail__resolution :deep(.el-descriptions__label) {
  color: var(--color-neutral-500);
}

.bug-detail__props :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--color-neutral-600);
  margin-bottom: var(--space-xs);
}

.bug-detail__props :deep(.el-form-item) {
  margin-bottom: var(--space-md);
}

.bug-detail__props :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.bug-detail__props :deep(.el-select),
.bug-detail__props :deep(.el-tree-select) {
  width: 100%;
}

.bug-detail__props :deep(.bug-detail__date) {
  width: 100%;
  --el-date-editor-width: 100%;
}

.bug-detail__text {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  line-height: 1.6;
}

.bug-detail__relation {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;

  .bug-detail__text {
    flex: 1;
  }
}

.bug-detail__case-trigger {
  cursor: pointer;
  color: var(--el-color-primary);
  text-decoration: underline dashed;
  text-underline-offset: 3px;
}

.bug-detail__case-pop {
  min-height: 60px;
}

.bug-detail__case-pop-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.bug-detail__case-pop-title {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
  flex: 1;
  min-width: 0;
  word-break: break-all;
}

.bug-detail__case-pop-priority {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
  flex-shrink: 0;

  &--p0 { background: var(--color-priority-p0); }
  &--p1 { background: var(--color-priority-p1); }
  &--p2 { background: var(--color-priority-p2); }
  &--p3 { background: var(--color-priority-p3); }
}

.bug-detail__case-pop-doc {
  margin-top: var(--space-xs);
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
}

.bug-detail__case-pop-body {
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--el-border-color-lighter);
  max-height: 260px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bug-detail__case-pop-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: var(--font-size-xs);
  line-height: 18px;
}

.bug-detail__case-pop-type {
  font-size: 11px;
  color: #fff;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
  flex-shrink: 0;
}

.bug-detail__case-pop-text {
  color: var(--color-neutral-700);
  word-break: break-all;
}

.bug-detail__case-pop-footer {
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--el-border-color-lighter);
  text-align: right;

  .el-icon {
    margin-right: var(--space-xs);
  }
}

.bug-detail__severity-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: var(--space-sm);
  vertical-align: middle;

  &--fatal { background: var(--color-bug-fatal); }
  &--serious { background: var(--color-bug-serious); }
  &--general { background: var(--color-bug-general); }
  &--minor { background: var(--color-bug-minor); }
}
</style>
