<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assignBug, changeBugStatus, getBugDetail, getBugLogs, updateBug } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type { BugDetail, BugLog, BugStatus, WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'
import { BUG_STATUS_LABEL, getValidTargetStatuses, promptStatusChangeComment } from '@/utils/bugStatus'

const route = useRoute()
const router = useRouter()
const bugId = route.params.bugId as string

const loading = ref(false)
const saving = ref(false)
const detail = ref<BugDetail | null>(null)
const logs = ref<BugLog[]>([])
const memberOptions = ref<WorkspaceMember[]>([])

const isClosed = computed(() => detail.value?.status === 'closed')

const form = reactive({
  title: '',
  severity: '' as string,
  priority: '' as string,
  status: '' as string,
  description: '',
  assigneeId: '' as string,
})

const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<string, string> = { high: '高', medium: '中', low: '低' }
const statusLabel = BUG_STATUS_LABEL

// 状态下拉仅展示当前状态与状态机允许的目标状态，避免非法流转
const statusOptions = computed(() => {
  const current = detail.value?.status
  if (!current) return []
  return [
    { value: current, label: statusLabel[current], disabled: true },
    ...getValidTargetStatuses(current).map((s) => ({
      value: s,
      label: statusLabel[s],
      disabled: false,
    })),
  ]
})

async function load() {
  loading.value = true
  try {
    const [bugData, logData, memberData] = await Promise.all([
      getBugDetail(bugId),
      getBugLogs(bugId),
      fetchMembers({ pageNo: 1, pageSize: 100 }),
    ])
    detail.value = bugData
    logs.value = logData
    memberOptions.value = memberData.list
    form.title = bugData.title
    form.severity = bugData.severity
    form.priority = bugData.priority
    form.status = bugData.status
    form.description = bugData.description ?? ''
    form.assigneeId = bugData.assignee?.id ?? ''
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载缺陷详情失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!detail.value) return
  saving.value = true
  try {
    await updateBug(bugId, {
      title: form.title.trim(),
      severity: form.severity as BugDetail['severity'],
      priority: form.priority as BugDetail['priority'],
      description: form.description.trim() || undefined,
    })
    // 处理人变更走专用指派接口，后端会校验工作空间成员并写指派日志
    const originalAssigneeId = detail.value.assignee?.id ?? ''
    if (form.assigneeId && form.assigneeId !== originalAssigneeId) {
      await assignBug(bugId, form.assigneeId)
    }
    ElMessage.success('已保存')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleStatusChange(newStatus: string) {
  const current = detail.value?.status
  if (!current || newStatus === current) return
  const comment = await promptStatusChangeComment(current, newStatus as BugStatus)
  if (comment === null) {
    form.status = current
    return
  }
  try {
    await changeBugStatus(bugId, { status: newStatus as BugStatus, comment: comment || undefined })
    ElMessage.success('状态已更新')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '状态变更失败')
    form.status = current
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="bug-detail">
    <el-page-header @back="router.push('/workspace/projects/bugs')">
      <template #content><span class="bug-detail__title">缺陷详情</span></template>
    </el-page-header>

    <el-card v-if="detail" shadow="never" class="bug-detail__card">
      <el-form label-width="96px" class="bug-detail__form">
        <el-form-item label="标题">
          <el-input v-if="!isClosed" v-model="form.title" />
          <span v-else class="bug-detail__text">{{ detail.title }}</span>
        </el-form-item>
        <el-form-item label="严重等级">
          <el-select v-if="!isClosed" v-model="form.severity" style="width: 160px">
            <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key" />
          </el-select>
          <el-tag v-else size="small" effect="light" round>{{ severityLabel[detail.severity] }}</el-tag>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-if="!isClosed" v-model="form.priority" style="width: 160px">
            <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
          </el-select>
          <span v-else class="bug-detail__text">{{ priorityLabel[detail.priority] }}</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-if="!isClosed" v-model="form.status" style="width: 160px" @change="handleStatusChange">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
              :disabled="opt.disabled"
            />
          </el-select>
          <template v-else>
            <el-tag>{{ statusLabel[detail.status] }}</el-tag>
            <el-button link type="primary" class="bug-detail__reopen" @click="handleStatusChange('fixing')">
              重新打开
            </el-button>
          </template>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-if="!isClosed" v-model="form.description" type="textarea" :rows="4" />
          <span v-else class="bug-detail__text">{{ detail.description || '-' }}</span>
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-if="!isClosed" v-model="form.assigneeId" filterable style="width: 240px">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
          <span v-else class="bug-detail__text">{{ detail.assignee?.name ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="报告人">
          <span class="bug-detail__text">{{ detail.reporter.name }}</span>
        </el-form-item>
        <el-form-item v-if="!isClosed">
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="router.push('/workspace/projects/bugs')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="logs.length" shadow="never" class="bug-detail__logs">
      <template #header><span class="bug-detail__section">操作记录</span></template>
      <el-timeline>
        <el-timeline-item
          v-for="log in logs"
          :key="log.id"
          :timestamp="formatDateTime(log.createdAt)"
          placement="top"
        >
          <strong>{{ log.operatorName }}</strong> {{ log.operationType }}
          <span v-if="log.content" class="bug-detail__log-content">{{ log.content }}</span>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.bug-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.bug-detail__card {
  margin-top: var(--space-lg);
}

.bug-detail__form {
  max-width: 640px;
}

.bug-detail__text {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  line-height: 1.6;
}

.bug-detail__reopen {
  margin-left: var(--space-sm);
}

.bug-detail__logs {
  margin-top: var(--space-lg);
}

.bug-detail__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.bug-detail__log-content {
  color: var(--color-neutral-500);
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
}
</style>
