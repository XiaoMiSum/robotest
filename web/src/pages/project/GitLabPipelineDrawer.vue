<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { GitLabPipelineResult, GitLabPipelineStatusResult, GitLabPipelineReportResult } from '@/types'
import { triggerGitLabPipeline, fetchGitLabPipelineStatus, pullGitLabPipelineReport } from '@/services/gitLabRepo'

const props = defineProps<{ repoId: string; visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', val: boolean): void }>()

const sceneId = ref('')
const variables = ref<Record<string, string>>({})
const triggering = ref(false)
const triggerResult = ref<GitLabPipelineResult | null>(null)
const statusResult = ref<GitLabPipelineStatusResult | null>(null)
const reportResult = ref<GitLabPipelineReportResult | null>(null)
const polling = ref(false)
const pollingTimer = ref<ReturnType<typeof setInterval> | null>(null)

async function handleTrigger() {
  if (!sceneId.value) {
    ElMessage.warning('请输入场景 ID')
    return
  }
  triggering.value = true
  try {
    triggerResult.value = await triggerGitLabPipeline(props.repoId, {
      sceneId: sceneId.value,
      variables: Object.keys(variables.value).length > 0 ? variables.value : undefined,
    })
    ElMessage.success('流水线已触发')
    startPolling(triggerResult.value.executionRecordId)
  } catch {
    ElMessage.error('流水线触发失败')
  } finally {
    triggering.value = false
  }
}

function startPolling(executionId: string) {
  stopPolling()
  polling.value = true
  pollingTimer.value = setInterval(async () => {
    try {
      statusResult.value = await fetchGitLabPipelineStatus(executionId)
      const status = statusResult.value.status
      if (status === 'success' || status === 'failed' || status === 'error') {
        stopPolling()
        if (status === 'success') {
          reportResult.value = await pullGitLabPipelineReport(executionId)
        }
      }
    } catch {
      stopPolling()
    }
  }, 5000)
}

function stopPolling() {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
  polling.value = false
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '等待中', running: '运行中', success: '成功', failed: '失败', error: '错误', cancelled: '已取消', timeout: '超时',
  }
  return map[status] ?? status
}

function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  const map: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
    success: 'success', failed: 'danger', error: 'danger', running: 'warning', pending: 'info', cancelled: 'info', timeout: 'warning',
  }
  return map[status] ?? 'info'
}

watch(() => props.visible, (val) => {
  if (!val) {
    stopPolling()
    triggerResult.value = null
    statusResult.value = null
    reportResult.value = null
  }
})
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="流水线触发"
    size="600px"
    @update:model-value="emit('update:visible', $event)"
  >
    <template #header>
      <div class="pipeline-drawer__header">
        <span class="pipeline-drawer__title">流水线触发</span>
      </div>
    </template>

    <!-- 触发表单 -->
    <el-form label-width="100px" style="max-width: 500px">
      <el-form-item label="场景 ID">
        <el-input v-model="sceneId" placeholder="输入测试场景 UUID" />
      </el-form-item>
      <el-form-item label="自定义变量">
        <div class="pipeline-drawer__vars">
          <div v-for="(_val, key) in variables" :key="key" class="pipeline-drawer__var-row">
            <el-input :model-value="key as string" size="small" style="width: 140px" disabled />
            <el-input v-model="variables[key as string]" size="small" style="width: 180px" placeholder="值" />
            <el-button link type="danger" size="small" @click="delete variables[key as string]">删除</el-button>
          </div>
          <el-button link type="primary" size="small" @click="variables[''] = ''">+ 添加变量</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="triggering" @click="handleTrigger">触发流水线</el-button>
      </el-form-item>
    </el-form>

    <!-- 触发结果 -->
    <div v-if="triggerResult" class="pipeline-drawer__result">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="流水线 ID">{{ triggerResult.pipelineId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(triggerResult.status)" size="small">{{ statusLabel(triggerResult.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="流水线链接" :span="2">
          <a :href="triggerResult.pipelineUrl" target="_blank" rel="noopener">查看流水线</a>
        </el-descriptions-item>
        <el-descriptions-item v-if="triggerResult.metadataExpired" label="元数据已过期" :span="2">
          <el-text type="warning">已自动同步，同步类数：{{ triggerResult.metadataSyncClassCount }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 轮询状态 -->
    <div v-if="polling" class="pipeline-drawer__polling">
      <el-text type="info">正在轮询流水线状态…</el-text>
    </div>

    <div v-if="statusResult && !polling" class="pipeline-drawer__status">
      <el-divider>最终状态</el-divider>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(statusResult.status)" size="small">{{ statusLabel(statusResult.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ statusResult.duration ? `${statusResult.duration}s` : '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="statusResult.stages?.length" class="pipeline-drawer__stages">
        <el-tag
          v-for="stage in statusResult.stages"
          :key="stage.name"
          :type="statusType(stage.status)"
          size="small"
          class="pipeline-drawer__stage"
        >
          {{ stage.name }}: {{ statusLabel(stage.status) }}
        </el-tag>
      </div>
    </div>

    <!-- 报告 -->
    <div v-if="reportResult" class="pipeline-drawer__report">
      <el-divider>测试报告</el-divider>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="报告 ID">{{ reportResult.reportId }}</el-descriptions-item>
        <el-descriptions-item label="摘要">
          <pre class="pipeline-drawer__summary">{{ JSON.stringify(reportResult.summary, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </el-drawer>
</template>

<style scoped>
.pipeline-drawer__header {
  display: flex;
  align-items: center;
  width: 100%;
}
.pipeline-drawer__title {
  font-size: 16px;
  font-weight: 600;
}
.pipeline-drawer__vars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pipeline-drawer__var-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pipeline-drawer__result {
  margin-top: 16px;
}
.pipeline-drawer__polling {
  margin-top: 12px;
  text-align: center;
}
.pipeline-drawer__status {
  margin-top: 16px;
}
.pipeline-drawer__stages {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.pipeline-drawer__stage {
  font-size: 12px;
}
.pipeline-drawer__report {
  margin-top: 16px;
}
.pipeline-drawer__summary {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
