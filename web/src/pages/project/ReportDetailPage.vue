<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiReportAssertion, ApiReportDetail, ApiReportStepResult } from '@/types'
import { exportReportUrl, fetchReportDetail, shareReport } from '@/services/apiReport'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  reportId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const loading = ref(true)
const report = ref<ApiReportDetail | null>(null)
const expandedStep = ref<ApiReportStepResult | null>(null)
const drawerVisible = ref(false)

async function loadDetail() {
  loading.value = true
  try {
    report.value = await fetchReportDetail(props.reportId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报告详情加载失败')
  } finally {
    loading.value = false
  }
}

// ==================== 状态 ====================
function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'partial') return 'warning'
  return 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    success: '通过',
    failed: '失败',
    partial: '部分通过',
    passed: '通过',
    skipped: '跳过',
    not_executed: '未执行',
  }
  return map[status] ?? status
}

// ==================== 通过率 ====================
const passRate = computed(() => {
  const s = report.value?.summary
  if (!s || !s.total) return '0'
  return ((s.passed / s.total) * 100).toFixed(1)
})

// ==================== 耗时 ====================
function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

// ==================== 报告名称 ====================
const reportName = computed(() => {
  if (!report.value) return ''
  const date = report.value.createdAt ? formatDateTime(report.value.createdAt) : ''
  return `${report.value.sceneName}-${date}`
})

// ==================== 步骤断言统计 ====================
function assertionStats(step: ApiReportStepResult): string {
  const list = step.assertions ?? step.validators ?? []
  if (!list.length) return '-'
  const passed = list.filter((a) => a.result === 'passed').length
  return `${passed}/${list.length}`
}

// ==================== 展开行 ====================
function openStepDetail(step: ApiReportStepResult) {
  expandedStep.value = step
  drawerVisible.value = true
}

// ==================== 请求/响应格式化 ====================
function formatJson(obj: unknown): string {
  if (!obj) return '-'
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

// ==================== 分享（生成时选有效期，参照创建邀请链接） ====================
const shareDialogVisible = ref(false)
const shareExpiryDays = ref(7)
const shareUrl = ref('')
const shareExpiresAt = ref('')
const shareLinkGenerated = ref(false)
const shareSubmitting = ref(false)

function openShareDialog() {
  shareUrl.value = ''
  shareExpiresAt.value = ''
  shareLinkGenerated.value = false
  shareDialogVisible.value = true
}

async function generateShareLink() {
  shareSubmitting.value = true
  try {
    const resp = await shareReport(props.reportId, shareExpiryDays.value)
    shareUrl.value = window.location.origin + resp.shareUrl
    shareExpiresAt.value = resp.expiresAt
    shareLinkGenerated.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分享失败')
  } finally {
    shareSubmitting.value = false
  }
}

function copyShareUrl() {
  void navigator.clipboard.writeText(shareUrl.value)
  ElMessage.success('已复制分享链接')
}

// ==================== 导出 ====================
function handleExportJson() {
  const url = exportReportUrl(props.reportId, 'json')
  const a = document.createElement('a')
  a.href = url
  a.download = `${reportName.value}.json`
  a.click()
}

function handleExportHtml() {
  const url = exportReportUrl(props.reportId, 'html')
  const a = document.createElement('a')
  a.href = url
  a.download = `${reportName.value}.html`
  a.click()
}

// ==================== 打开导出链接 ====================
function openExport(format: 'json' | 'html') {
  if (format === 'json') handleExportJson()
  else handleExportHtml()
}

onMounted(loadDetail)
</script>

<template>
  <div v-loading="loading" class="report-detail">
    <template v-if="report">
      <el-card shadow="never">
        <template #header>
          <div class="report-detail__header">
            <el-button text @click="emit('back')">
              <el-icon><ArrowLeft /></el-icon>返回列表
            </el-button>
            <div class="report-detail__title-row">
              <h2 class="report-detail__title">{{ reportName }}</h2>
              <el-tag size="small" :type="statusType(report.status)">{{ statusLabel(report.status) }}</el-tag>
            </div>
            <div class="report-detail__meta">
              <span>场景：{{ report.sceneName }}</span>
              <span v-if="report.environmentName">环境：{{ report.environmentName }}</span>
              <span>执行方式：{{ report.executionMode === 'pipeline' ? '仓库流水线' : '平台内执行' }}</span>
              <span>执行时间：{{ formatDateTime(report.createdAt) }}</span>
            </div>
            <div class="report-detail__actions">
              <el-button @click="openShareDialog">
                <el-icon><Share /></el-icon>分享
              </el-button>
              <el-dropdown @command="(cmd: string) => openExport(cmd as 'json' | 'html')">
                <el-button>
                  <el-icon><Download /></el-icon>导出
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="json">导出 JSON</el-dropdown-item>
                    <el-dropdown-item command="html">导出 HTML</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </template>

        <!-- 汇总卡片 -->
        <section class="report-detail__summary">
          <div class="summary-card">
            <div class="summary-card__value">{{ report.summary?.total ?? 0 }}</div>
            <div class="summary-card__label">总步骤</div>
          </div>
          <div class="summary-card summary-card--success">
            <div class="summary-card__value">{{ report.summary?.passed ?? 0 }}</div>
            <div class="summary-card__label">通过</div>
          </div>
          <div class="summary-card summary-card--danger">
            <div class="summary-card__value">{{ report.summary?.failed ?? 0 }}</div>
            <div class="summary-card__label">失败</div>
          </div>
          <div class="summary-card summary-card--info">
            <div class="summary-card__value">{{ report.summary?.skipped ?? 0 }}</div>
            <div class="summary-card__label">跳过</div>
          </div>
          <div class="summary-card summary-card--ring">
            <div class="ring-chart" :style="{ '--rate': passRate + '%' }">
              <span class="ring-chart__text">{{ passRate }}%</span>
            </div>
            <div class="summary-card__label">通过率</div>
          </div>
          <div class="summary-card">
            <div class="summary-card__value">{{ formatDuration(report.summary?.durationMs) }}</div>
            <div class="summary-card__label">总耗时</div>
          </div>
        </section>

        <!-- 步骤结果表 -->
        <section class="report-detail__steps">
          <h3 class="report-detail__section-title">步骤结果</h3>
          <el-table :data="report.stepResults ?? []" row-key="stepId">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="name" label="步骤名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="接口" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="(row as ApiReportStepResult).request?.method">
                  {{ (row as ApiReportStepResult).request!.method }}
                  {{ (row as ApiReportStepResult).request!.url }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType((row as ApiReportStepResult).status)">
                  {{ statusLabel((row as ApiReportStepResult).status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="90" align="center">
              <template #default="{ row }">{{ formatDuration((row as ApiReportStepResult).durationMs) }}</template>
            </el-table-column>
            <el-table-column label="断言" width="80" align="center">
              <template #default="{ row }">{{ assertionStats(row as ApiReportStepResult) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button link size="small" @click="openStepDetail(row as ApiReportStepResult)">
                  展开
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-card>

      <!-- 步骤详情抽屉 -->
      <el-drawer v-model="drawerVisible" :title="expandedStep?.name ?? '步骤详情'" size="600px">
        <template v-if="expandedStep">
          <div class="step-expanded">
            <div v-if="expandedStep.request" class="step-expanded__section">
              <h4>请求快照</h4>
              <pre class="code-block">{{ formatJson(expandedStep.request) }}</pre>
            </div>
            <div v-if="expandedStep.response" class="step-expanded__section">
              <h4>响应快照</h4>
              <pre class="code-block">{{ formatJson(expandedStep.response) }}</pre>
            </div>
            <div v-if="expandedStep.extractors?.length" class="step-expanded__section">
              <h4>提取器结果</h4>
              <el-table :data="expandedStep.extractors" size="small">
                <el-table-column prop="name" label="变量名" />
                <el-table-column prop="value" label="值" />
              </el-table>
            </div>
            <div v-if="expandedStep.assertions?.length || expandedStep.validators?.length" class="step-expanded__section">
              <h4>断言明细</h4>
              <el-table :data="(expandedStep.assertions ?? expandedStep.validators ?? [])" size="small">
                <el-table-column prop="name" label="断言描述" min-width="120" />
                <el-table-column prop="target" label="目标" width="100" />
                <el-table-column prop="condition" label="条件" width="100" />
                <el-table-column prop="expected" label="期望值" width="120" />
                <el-table-column prop="actual" label="实际值" width="120" />
                <el-table-column label="结果" width="80" align="center">
                  <template #default="{ row: a }">
                    <el-tag
                      size="small"
                      :type="(a as ApiReportAssertion).result === 'passed' ? 'success' : 'danger'"
                    >
                      {{ (a as ApiReportAssertion).result === 'passed' ? '通过' : '失败' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="错误信息" min-width="140" show-overflow-tooltip>
                  <template #default="{ row: a }">
                    <span v-if="(a as ApiReportAssertion).error" class="text-red-500">
                      {{ (a as ApiReportAssertion).error }}
                    </span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div v-if="expandedStep.errorMessage" class="step-expanded__section">
              <el-alert :title="expandedStep.errorMessage" type="error" show-icon :closable="false" />
            </div>
          </div>
        </template>
      </el-drawer>
    </template>

    <!-- 分享弹窗：两步式（选有效期 → 生成链接），参照创建邀请链接 -->
    <el-dialog v-model="shareDialogVisible" title="分享报告" width="480px" :close-on-click-modal="false">
      <template v-if="!shareLinkGenerated">
        <p class="share-dialog__hint">选择有效期后生成分享链接，有效期内可免登录访问</p>
        <el-radio-group v-model="shareExpiryDays" class="share-dialog__expiry">
          <el-radio-button :value="1">1 天</el-radio-button>
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
          <el-radio-button :value="90">90 天</el-radio-button>
        </el-radio-group>
        <div class="share-dialog__actions">
          <el-button @click="shareDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="shareSubmitting" @click="generateShareLink">
            生成分享链接
          </el-button>
        </div>
      </template>
      <template v-else>
        <el-input v-model="shareUrl" readonly>
          <template #append>
            <el-button @click="copyShareUrl">复制</el-button>
          </template>
        </el-input>
        <p class="share-dialog__expires">有效期至：{{ formatDateTime(shareExpiresAt) }}</p>
        <div class="share-dialog__actions">
          <el-button @click="openShareDialog">重新生成</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.report-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  height: 100%;
}

.report-detail__header {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.report-detail__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.report-detail__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.report-detail__meta {
  display: flex;
  gap: var(--space-lg);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.report-detail__actions {
  display: flex;
  gap: var(--space-sm);
}

.report-detail__summary {
  display: flex;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.summary-card {
  flex: 1;
  min-width: 100px;
  background: var(--color-neutral-50);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  text-align: center;

  &--success .summary-card__value {
    color: #16a34a;
  }

  &--danger .summary-card__value {
    color: #dc2626;
  }

  &--info .summary-card__value {
    color: #6b7280;
  }
}

.summary-card__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-neutral-800);
}

.summary-card__label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  margin-top: 4px;
}

.summary-card--ring {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ring-chart {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: conic-gradient(#16a34a 0 var(--rate), #e5e7eb var(--rate) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    width: 38px;
    height: 38px;
    border-radius: 50%;
    background: #fff;
  }
}

.ring-chart__text {
  position: relative;
  z-index: 1;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-neutral-700);
}

.report-detail__section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 var(--space-sm);
}

.step-expanded {
  padding: var(--space-md) var(--space-lg);
  background: var(--color-neutral-50);
  border-radius: var(--radius-md);
}

.step-expanded__section {
  margin-bottom: var(--space-md);

  &:last-child {
    margin-bottom: 0;
  }

  h4 {
    font-size: 13px;
    font-weight: 600;
    color: var(--color-neutral-600);
    margin: 0 0 var(--space-xs);
  }
}

.code-block {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: var(--radius-md);
  padding: 10px;
  font-size: 12px;
  overflow: auto;
  max-height: 320px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.share-dialog__hint {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
  margin: 0 0 var(--space-sm);
}

.share-dialog__expiry {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}

.share-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-xs);
  margin-top: var(--space-lg);
}

.share-dialog__expires {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  margin: var(--space-sm) 0 0;
}

.text-red-500 {
  color: var(--color-danger);
}
</style>
