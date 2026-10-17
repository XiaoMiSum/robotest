<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Download, Search } from '@element-plus/icons-vue'
import {
  STAGE_LABELS,
  detailTypeLabel,
  isRunningStage,
  useGitLabImport,
} from '@/composables/project/api-testing/gitlab/useGitLabImport'
import { formatDateTime } from '@/utils/format'
import type { ApiGitImportPreviewScenes, ApiGitRepoConfig } from '@/types'

const props = defineProps<{ repo: ApiGitRepoConfig }>()
const emit = defineEmits<{ refresh: [] }>()

const {
  canPreview,
  canExecute,
  previewing,
  preview,
  gateBlocked,
  gateReason,
  lastResultVisible,
  submitting,
  progress,
  importing,
  detailFilter,
  filteredDetails,
  runPreview,
  runImport,
  exportDetailsCsv,
} = useGitLabImport(
  () => props.repo,
  () => emit('refresh'),
)

/** 四段计数（交互稿 27 §3.2）：预览为「将发生」，导入完成为「已发生」 */
interface ImportCounts {
  created: number
  updated: number
  parseFailed: number
  errorFailed: number
}

const EMPTY_COUNTS: ImportCounts = { created: 0, updated: 0, parseFailed: 0, errorFailed: 0 }

function countsOf(scenes: ApiGitImportPreviewScenes): ImportCounts {
  return {
    created: scenes.create,
    updated: scenes.update,
    parseFailed: scenes.parseFailed,
    errorFailed: scenes.errorFailed,
  }
}

const summaryView = computed<{ label: string; counts: ImportCounts } | null>(() => {
  const scenes = preview.value?.scenes
  if (scenes) return { label: '预计导入', counts: countsOf(scenes) }
  const status = progress.value
  if (status && status.stage === 'done' && status.summary) {
    return {
      label: '导入结果',
      counts: {
        created: status.summary.created ?? 0,
        updated: status.summary.updated ?? 0,
        parseFailed: status.summary.parseFailed ?? 0,
        errorFailed: status.summary.errorFailed ?? 0,
      },
    }
  }
  return null
})
const hasSummary = computed(() => summaryView.value !== null)
const summaryLabel = computed(() => summaryView.value?.label ?? '')
const summaryCounts = computed(() => summaryView.value?.counts ?? EMPTY_COUNTS)

const previewMeta = computed(() => {
  const result = preview.value
  if (!result) return null
  const env = result.environment
  const envText =
    !env || env.action === 'skip'
      ? '本次不合并环境'
      : `环境并入「${env.target || '默认环境'}」· HTTP ${env.httpConfigs ?? 0} · 数据源 ${env.dataSources ?? 0}`
  return `测试类 ${result.classes ?? 0} · 有效用例 ${result.effectiveCases ?? 0} · ${envText}`
})

interface ProgressView {
  label: string
  text: string
  running: boolean
  failed: boolean
  error: string | null
  processed: number
  total: number
  percent: number
}

const progressView = computed<ProgressView | null>(() => {
  const status = progress.value
  if (!status || status.stage === 'idle') return null
  const label = STAGE_LABELS[status.stage]
  const running = isRunningStage(status.stage)
  let text = '导入完成'
  if (running) text = `进行中 · ${label}`
  else if (status.stage === 'failed') text = '导入失败'
  const percent =
    status.total > 0
      ? Math.min(100, Math.round((status.processed / status.total) * 100))
      : status.stage === 'done'
        ? 100
        : 0
  return {
    label,
    text,
    running,
    failed: status.stage === 'failed',
    error: status.error,
    processed: status.processed,
    total: status.total,
    percent,
  }
})

/** 无预览、无进行中进度且未被闸门拦截时呈现引导空态；重扫期间不闪现空态（交互稿 27 §3.2） */
const showEmpty = computed(
  () => !preview.value && !previewing.value && !progressView.value && !gateBlocked.value,
)

const parsedScenes = computed(() => preview.value?.parsedScenes ?? null)

/** 解析成功清单默认折叠；新预览结果到达即回落折叠态（交互稿 27 §3.2） */
const parsedVisible = ref(false)
watch(preview, () => {
  parsedVisible.value = false
})

const canRunImport = computed(
  () => canExecute.value && !!preview.value && !previewing.value && !importing.value && !submitting.value,
)

const lastSummary = computed(() => props.repo.lastImportSummary ?? {})
const shortCommit = computed(() => props.repo.lastImportCommit?.slice(0, 7) ?? '')
const commitUrl = computed(
  () => `${props.repo.gitlabBaseUrl}/${props.repo.repoPath}/-/commit/${props.repo.lastImportCommit}`,
)
</script>

<template>
  <div class="gi-pane">
    <!-- 上次导入信息：常驻页签顶部，提交号可跳转 GitLab（交互稿 27 §3.2） -->
    <div v-if="repo.lastImportAt" class="gi-last">
      <span class="gi-last__label">上次导入</span>
      <span>{{ formatDateTime(repo.lastImportAt) }}</span>
      <a
        v-if="repo.lastImportCommit"
        class="gi-last__commit mono"
        :href="commitUrl"
        target="_blank"
        rel="noreferrer"
        title="在 GitLab 中查看本次导入提交"
      >{{ shortCommit }}</a>
      <span class="gi-last__sep" />
      <span class="gi-last__count">新建 {{ lastSummary.created ?? 0 }}</span>
      <span class="gi-last__count">更新 {{ lastSummary.updated ?? 0 }}</span>
      <span class="gi-last__count gi-last__count--warn">解析失败 {{ lastSummary.parseFailed ?? 0 }}</span>
      <span class="gi-last__count gi-last__count--danger">真实错误 {{ lastSummary.errorFailed ?? 0 }}</span>
    </div>

    <!-- 幂等拦截：非错误提示（info 色信息条 + 查看上次结果，交互稿 27 §4）；el-alert 无 action 槽，动作随描述槽渲染 -->
    <el-alert
      v-if="gateBlocked"
      class="gi-alert"
      type="info"
      :closable="false"
      show-icon
      :title="gateReason"
    >
      <el-button size="small" class="gi-alert__action" @click="lastResultVisible = true">
        查看上次结果
      </el-button>
    </el-alert>

    <el-alert
      v-if="!canPreview"
      class="gi-alert"
      type="info"
      :closable="false"
      show-icon
      title="当前账号缺少 api-scene:view 权限，本页只读；扫描预览与导入进度不可用"
    />

    <!-- 动作行：[导出 CSV] 与权限提示靠左，[扫描预览] / [执行导入] 以图标 link 按钮固定右端（交互稿 27 §3.2） -->
    <div v-if="canPreview || canExecute" class="gi-actions">
      <div class="gi-actions__left">
        <el-button
          v-if="canPreview && preview && preview.details.length > 0"
          :disabled="filteredDetails.length === 0"
          @click="exportDetailsCsv"
        >导出 CSV</el-button>
        <span v-if="canPreview && !canExecute" class="gi-actions__hint">缺少 api-scene:edit 权限，不可执行导入</span>
      </div>
      <div class="gi-actions__right">
        <el-button
          v-if="canPreview"
          link
          type="primary"
          :icon="Search"
          :loading="previewing"
          :disabled="importing"
          @click="runPreview"
        >扫描预览</el-button>
        <el-button
          v-if="canExecute"
          link
          type="primary"
          :icon="Download"
          :loading="submitting"
          :disabled="!canRunImport"
          @click="runImport"
        >执行导入</el-button>
      </div>
    </div>

    <!-- 汇总条：四段横排，解析失败段可点击展开对应明细（交互稿 27 §3.2） -->
    <div v-if="hasSummary" class="gi-sum">
      <span class="gi-sum__ctx">{{ summaryLabel }}</span>
      <span class="gi-seg">新建 {{ summaryCounts.created }}</span>
      <span class="gi-seg">更新 {{ summaryCounts.updated }}</span>
      <button
        v-if="preview"
        type="button"
        class="gi-seg gi-seg--warn gi-seg--link"
        @click="detailFilter = 'parseFailed'"
      >解析失败 {{ summaryCounts.parseFailed }}</button>
      <span v-else class="gi-seg gi-seg--warn">解析失败 {{ summaryCounts.parseFailed }}</span>
      <span class="gi-seg gi-seg--danger">真实错误 {{ summaryCounts.errorFailed }}</span>
      <span v-if="previewMeta" class="gi-sum__meta">{{ previewMeta }}</span>
    </div>

    <!-- 解析成功清单：默认折叠，新结果到达即回落折叠（交互稿 27 §3.2） -->
    <div v-if="preview && parsedScenes" class="gi-parsed">
      <el-button
        link
        type="primary"
        :icon="parsedVisible ? ArrowUp : ArrowDown"
        @click="parsedVisible = !parsedVisible"
      >解析成功 {{ parsedScenes.total }}</el-button>
      <div v-if="parsedVisible" class="gi-parsed__body">
        <p v-if="parsedScenes.truncated" class="gi-parsed__note">
          仅展示前 {{ parsedScenes.items.length }} 条，共 {{ parsedScenes.total }} 条
        </p>
        <ul class="gi-parsed__list">
          <li v-for="(item, index) in parsedScenes.items" :key="`${index}-${item.sceneKey}`" class="gi-parsed__item">
            <el-tag size="small" :type="item.action === 'create' ? 'success' : 'info'">
              {{ item.action === 'create' ? '新建' : '更新' }}
            </el-tag>
            <span class="gi-parsed__key mono">{{ item.sceneKey }}</span>
            <span class="gi-parsed__name">{{ item.name }}</span>
          </li>
        </ul>
      </div>
    </div>

    <!-- 失败明细：紧凑多行列表（类型 / 类或方法 / 原因），支持按类型筛选与导出（SRS F1.2 功能点 4） -->
    <div v-if="preview" class="gi-details">
      <div class="gi-details__bar">
        <el-radio-group v-model="detailFilter" size="small">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="parseFailed">解析失败</el-radio-button>
          <el-radio-button value="errorFailed">真实错误</el-radio-button>
        </el-radio-group>
        <span class="gi-details__count">共 {{ filteredDetails.length }} 条</span>
      </div>
      <ul v-if="filteredDetails.length" class="gi-details__list">
        <li v-for="(row, index) in filteredDetails" :key="`${index}-${row.sceneKey ?? ''}`" class="gi-details__row">
          <el-tag :type="row.type === 'errorFailed' ? 'danger' : 'warning'" size="small">
            {{ detailTypeLabel(row.type) }}
          </el-tag>
          <span class="gi-details__key mono">{{ row.sceneKey || '文件级' }}</span>
          <span class="gi-details__reason">{{ row.reason }}</span>
        </li>
      </ul>
      <p v-else class="gi-details__empty">无失败明细，全部用例均可解析</p>
    </div>

    <!-- 进度：阶段中文 + 计数；total 未知（拉取阶段）时不确定进度（交互稿 27 §3.2 / §4） -->
    <div v-if="progressView" class="gi-progress">
      <div class="gi-progress__head">
        <span class="gi-progress__text" :class="{ 'gi-progress__text--failed': progressView.failed }">
          {{ progressView.text }}
        </span>
        <span v-if="progressView.total > 0" class="gi-progress__counts mono">
          {{ progressView.processed }} / {{ progressView.total }}
        </span>
      </div>
      <el-progress
        :percentage="progressView.percent"
        :indeterminate="progressView.running && progressView.total === 0"
        :status="progressView.failed ? 'exception' : undefined"
      />
      <p v-if="progressView.failed && progressView.error" class="gi-progress__error">
        {{ progressView.error }}
      </p>
    </div>

    <div v-if="showEmpty" class="gi-state">
      <span class="gi-state__icon"><el-icon :size="40"><UploadFilled /></el-icon></span>
      <div class="gi-state__title">扫描预览后执行导入</div>
      <p class="gi-state__desc">先扫描默认分支，确认将新建 / 更新的场景与失败明细，再执行导入</p>
    </div>

    <el-dialog v-model="lastResultVisible" title="上次导入结果" width="440px" append-to-body>
      <div class="gi-result">
        <div class="gi-result__row">
          <span>时间</span>
          <b>{{ formatDateTime(repo.lastImportAt) }}</b>
        </div>
        <div class="gi-result__row">
          <span>提交</span>
          <b>
            <a
              v-if="repo.lastImportCommit"
              class="gi-result__link mono"
              :href="commitUrl"
              target="_blank"
              rel="noreferrer"
            >{{ shortCommit }}</a>
            <template v-else>-</template>
          </b>
        </div>
        <div class="gi-result__row">
          <span>新建 / 更新</span>
          <b>{{ lastSummary.created ?? 0 }} / {{ lastSummary.updated ?? 0 }}</b>
        </div>
        <div class="gi-result__row">
          <span>解析失败 / 真实错误</span>
          <b>{{ lastSummary.parseFailed ?? 0 }} / {{ lastSummary.errorFailed ?? 0 }}</b>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.gi-pane {
  padding-top: var(--space-lg);
}

.mono {
  font-family: var(--font-mono);
  font-size: 0.95em;
}

/* 上次导入信息条 */
.gi-last {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 9px 14px;
  margin-bottom: var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
}

.gi-last__label {
  font-weight: 600;
  color: var(--color-neutral-700);
}

.gi-last__commit {
  color: var(--color-primary-500);
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

.gi-last__sep {
  width: 1px;
  height: 14px;
  background: var(--color-neutral-200);
}

.gi-last__count--warn {
  color: var(--color-warning-strong);
}

.gi-last__count--danger {
  color: var(--color-danger-strong);
}

.gi-alert {
  margin-bottom: var(--space-md);

  :deep(.el-alert__description) {
    margin-top: 8px;
  }
}

/* 动作行：左侧次级动作与权限提示，主操作 link 按钮固定右端 */
.gi-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.gi-actions__left,
.gi-actions__right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.gi-actions__hint {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

/* 汇总条：四段横排 + 环境说明 */
.gi-sum {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  padding: 12px 14px;
  margin-bottom: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
}

.gi-sum__ctx {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-500);
}

.gi-sum__meta {
  flex-basis: 100%;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  line-height: 1.6;
}

.gi-seg {
  padding: 4px 10px;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
}

.gi-seg--warn {
  color: var(--color-warning-strong);
  border-color: var(--color-warning-border);
  background: var(--color-warning-light);
}

.gi-seg--danger {
  color: var(--color-danger-strong);
  border-color: var(--color-danger-border);
  background: var(--color-danger-light);
}

.gi-seg--link {
  cursor: pointer;
  transition: border-color var(--transition-fast);

  &:hover {
    border-color: var(--color-warning-strong);
  }
}

/* 解析成功清单：折叠入口 + 展开列表（内部滚动，避免长清单推挤失败明细） */
.gi-parsed {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.gi-parsed__note {
  margin: 0 0 6px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.gi-parsed__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 10px 14px;
  max-height: 280px;
  overflow-y: auto;
  list-style: none;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
}

.gi-parsed__item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-sm);
}

.gi-parsed__key {
  flex: 0 1 auto;
  min-width: 0;
  color: var(--color-neutral-700);
}

.gi-parsed__name {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--color-neutral-500);
  overflow-wrap: anywhere;
}

/* 明细表 */
.gi-details {
  margin-bottom: var(--space-md);
}

.gi-details__bar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: 10px;
}

.gi-details__count {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.gi-details__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.gi-details__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px 12px;
  font-size: var(--font-size-sm);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
}

.gi-details__key {
  flex: 0 1 auto;
  min-width: 0;
  color: var(--color-neutral-700);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gi-details__reason {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--color-neutral-500);
  overflow-wrap: anywhere;
}

.gi-details__empty {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-400);
}

/* 进度区 */
.gi-progress {
  padding: 12px 14px;
  margin-bottom: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
}

.gi-progress__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: 8px;
}

.gi-progress__text {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.gi-progress__text--failed {
  color: var(--color-danger-strong);
}

.gi-progress__counts {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.gi-progress__error {
  margin: 8px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-danger-strong);
  line-height: 1.6;
}

/* 空态引导 */
.gi-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-xl) 0;
}

.gi-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  margin-bottom: var(--space-lg);
  border-radius: var(--radius-xl);
  background: var(--color-neutral-100);
  color: var(--color-neutral-400);
}

.gi-state__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-700);
}

.gi-state__desc {
  max-width: 480px;
  margin: var(--space-sm) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
  line-height: 1.6;
}

/* 上次导入结果对话框 */
.gi-result__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 7px 0;
  font-size: var(--font-size-sm);

  span {
    color: var(--color-neutral-500);
  }

  b {
    font-weight: 600;
    color: var(--color-neutral-800);
    text-align: right;
    word-break: break-all;
  }
}

.gi-result__link {
  color: var(--color-primary-500);
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}
</style>
