<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { ApiPublicReportResp, ApiReportAssertion, ApiReportStepResult } from '@/types'
import { fetchPublicReport } from '@/services/apiReport'
import { formatDateTime } from '@/utils/format'

const route = useRoute()

const loading = ref(true)
const error = ref('')
const report = ref<ApiPublicReportResp | null>(null)
const expandedStep = ref<ApiReportStepResult | null>(null)
const drawerVisible = ref(false)

async function loadReport() {
  loading.value = true
  error.value = ''
  try {
    const id = String(route.params.id ?? '')
    const token = String(route.query.token ?? '')
    if (!id || !token) {
      error.value = '分享链接无效'
      return
    }
    report.value = await fetchPublicReport(id, token)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    if (msg.includes('7009') || msg.includes('分享')) {
      error.value = '分享链接无效或已过期'
    } else {
      error.value = msg || '加载失败'
    }
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

// ==================== 格式化 ====================
function formatJson(obj: unknown): string {
  if (!obj) return '-'
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

onMounted(loadReport)
</script>

<template>
  <div class="share-page">
    <div v-if="loading" v-loading="true" class="share-page__loading" />

    <!-- 错误态 -->
    <el-result v-else-if="error" icon="error" :title="error" sub-title="请联系报告分享者重新生成链接">
      <template #extra />
    </el-result>

    <!-- 正常态 -->
    <template v-else-if="report">
      <header class="share-page__header">
        <h2 class="share-page__title">
          {{ report.sceneName }}
          <el-tag size="small" :type="statusType(report.status)">{{ statusLabel(report.status) }}</el-tag>
        </h2>
        <div class="share-page__meta">
          <span>场景：{{ report.sceneName }}</span>
          <span v-if="report.environmentName">环境：{{ report.environmentName }}</span>
          <span>执行时间：{{ formatDateTime(report.createdAt) }}</span>
        </div>
      </header>

      <!-- 汇总卡片 -->
      <section class="share-page__summary">
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
        <div class="summary-card">
          <div class="summary-card__value">{{ passRate }}%</div>
          <div class="summary-card__label">通过率</div>
        </div>
        <div class="summary-card">
          <div class="summary-card__value">{{ formatDuration(report.summary?.durationMs) }}</div>
          <div class="summary-card__label">总耗时</div>
        </div>
      </section>

      <!-- 步骤结果表（只读，无操作列） -->
      <section class="share-page__steps">
        <h3 class="share-page__section-title">步骤结果</h3>
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
              </el-table>
            </div>
          </div>
        </template>
      </el-drawer>
    </template>
  </div>
</template>

<style scoped lang="scss">
.share-page {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-xl);
  min-height: 100vh;
  background: var(--color-neutral-50);
}

.share-page__loading {
  min-height: 300px;
}

.share-page__header {
  margin-bottom: var(--space-lg);
}

.share-page__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 var(--space-sm);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.share-page__meta {
  display: flex;
  gap: var(--space-lg);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.share-page__summary {
  display: flex;
  gap: var(--space-md);
  flex-wrap: wrap;
  margin-bottom: var(--space-lg);
}

.summary-card {
  flex: 1;
  min-width: 100px;
  background: #fff;
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

.share-page__section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 var(--space-sm);
}

.step-expanded {
  padding: var(--space-md) var(--space-lg);
  background: #fff;
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
</style>
