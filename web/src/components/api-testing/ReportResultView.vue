<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ApiReportSceneResult, ApiReportStepResult, ApiReportSummary, ApiReportSuiteResult } from '@/types'
import { formatDateTime } from '@/utils/format'
import ReportProcessorsTabs from './report/ReportProcessorsTabs.vue'
import ReportStepCard from './report/ReportStepCard.vue'

/**
 * 报告结果数据集通用展示（重设计交互稿）——顶部概览（Hero）+ 执行概览统计卡 +
 * 环境处理器 Tab（套件）+ 场景卡（处理器 Tab + 步骤卡）。场景 / 套件 / 分享 / 弹窗复用同一组件。
 */
interface ReportViewReport {
  reportType: string
  name: string
  status: string
  environmentName: string | null
  executionMode?: string | null
  summary?: ApiReportSummary | null
  result: ApiReportSceneResult | ApiReportSuiteResult | null
  createdAt: string
}

const props = defineProps<{
  report: ReportViewReport
  focusSceneId?: string | null
}>()

const emit = defineEmits<{
  (e: 'view-suite'): void
}>()

const isSuite = computed(() => props.report.reportType === 'suite')

const suiteResult = computed<ApiReportSuiteResult | null>(() =>
  isSuite.value ? (props.report.result as ApiReportSuiteResult | null) : null,
)

const sceneResult = computed<ApiReportSceneResult | null>(() =>
  !isSuite.value ? (props.report.result as ApiReportSceneResult | null) : null,
)

/** 套件报告在弹窗里定位单场景时聚焦展示 */
const focused = computed(() => isSuite.value && !!props.focusSceneId && !!suiteResult.value?.scenes)

const focusedScene = computed<ApiReportSceneResult | null>(() => {
  if (!focused.value || !props.focusSceneId) return null
  return suiteResult.value?.scenes.find((scene) => scene.sceneId === props.focusSceneId) ?? null
})

const scenes = computed<ApiReportSceneResult[]>(() => {
  if (focused.value) return focusedScene.value ? [focusedScene.value] : []
  if (isSuite.value) return (suiteResult.value?.scenes ?? []) as ApiReportSceneResult[]
  return sceneResult.value ? [sceneResult.value] : []
})

// ==================== 场景卡折叠（套件默认折叠；单场景/聚焦默认展开） ====================
function sceneKey(scene: ApiReportSceneResult, index: number): string {
  return scene.sceneId ?? `scene-${index}`
}

function defaultSceneExpanded(): Set<string> {
  const v = new Set<string>()
  // 套件整体展示时默认折叠场景卡，避免长页；单场景报告默认展开便于直接查看明细
  if (!isSuite.value || focused.value) {
    scenes.value.forEach((scene, index) => v.add(sceneKey(scene, index)))
  }
  return v
}

const sceneExpanded = ref<Set<string>>(new Set(defaultSceneExpanded()))

function isSceneOpen(scene: ApiReportSceneResult, index: number): boolean {
  return sceneExpanded.value.has(sceneKey(scene, index))
}

function toggleScene(scene: ApiReportSceneResult, index: number) {
  const key = sceneKey(scene, index)
  const set = new Set(sceneExpanded.value)
  if (set.has(key)) set.delete(key)
  else set.add(key)
  sceneExpanded.value = set
}

watch(
  () => [props.report, props.focusSceneId] as const,
  () => {
    sceneExpanded.value = defaultSceneExpanded()
  },
)

// ==================== Hero 概览 ====================
const heroStatus = computed<{ ok: boolean; label: string }>(() => {
  const status = props.report.status
  if (status === 'success' || status === 'passed') return { ok: true, label: '执行成功' }
  if (status === 'failed') return { ok: false, label: '执行失败' }
  if (status === 'partial') return { ok: false, label: '部分通过' }
  return { ok: true, label: status || '执行成功' }
})

const sourceLabel = computed(() => {
  if (suiteResult.value?.source === 'schedule') return '定时任务'
  return '平台内执行'
})

const heroEnv = computed(() => props.report.environmentName ?? suiteResult.value?.environmentName ?? sceneResult.value?.environmentName ?? '-')

const heroTime = computed(() => {
  if (isSuite.value) return suiteResult.value?.triggeredAt ?? props.report.createdAt
  return sceneResult.value?.executedAt ?? props.report.createdAt
})

const heroTimeLabel = computed(() => (isSuite.value ? '触发时间' : '执行时间'))

const heroDuration = computed<number | null>(() => {
  const ms = scenes.value.length === 1 ? scenes.value[0]?.summary?.durationMs : suiteResult.value?.summary?.durationMs
  return ms ?? props.report.summary?.durationMs ?? null
})

const heroId = computed<{ label: string; value: string | null }>(() => {
  if (isSuite.value) return { label: 'Task ID', value: suiteResult.value?.taskId ?? null }
  return { label: '场景 ID', value: sceneResult.value?.sceneId ?? null }
})

// ==================== 执行概览统计卡 ====================
function percent(num: number | undefined, denom: number | undefined): string {
  if (!denom) return '-'
  return `${((num ?? 0) / denom * 100).toFixed(1)}%`
}

function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

const stats = computed<Array<{ label: string; value: string; tone: 'g' | 'r' | 'b' | 'n' }>>(() => {
  if (isSuite.value && !focused.value) {
    const s = suiteResult.value?.summary
    return [
      { label: '场景总数', value: String(s?.totalScenes ?? 0), tone: 'b' },
      { label: '通过场景', value: String(s?.passedScenes ?? 0), tone: 'g' },
      { label: '失败场景', value: String(s?.failedScenes ?? 0), tone: 'r' },
      { label: '通过率', value: percent(s?.passedScenes, s?.totalScenes), tone: 'g' },
      { label: '总耗时', value: formatDuration(s?.durationMs ?? null), tone: 'b' },
    ]
  }
  const s = focused.value ? focusedScene.value?.summary : sceneResult.value?.summary
  return [
    { label: '总步骤', value: String(s?.total ?? 0), tone: 'b' },
    { label: '通过', value: String(s?.passed ?? 0), tone: 'g' },
    { label: '失败', value: String(s?.failed ?? 0), tone: 'r' },
    { label: '通过率', value: percent(s?.passed, s?.total), tone: 'g' },
    { label: '总耗时', value: formatDuration(s?.durationMs ?? null), tone: 'b' },
  ]
})

// ==================== 场景 / 处理器 ====================
/** 环境级处理器：仅套件整体展示（聚焦单场景时不重复展示） */
const envPresent = computed(() => {
  if (!isSuite.value || focused.value) return false
  return (suiteResult.value?.preprocessors?.length ?? 0) > 0 || (suiteResult.value?.postprocessors?.length ?? 0) > 0
})

function sceneMiniStat(scene: ApiReportSceneResult, key: 'total' | 'passed' | 'failed' | 'durationMs'): number | string {
  const s = scene.summary
  if (key === 'durationMs') return formatDuration(s?.durationMs ?? null)
  return s && s[key] != null ? s[key]! : 0
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    success: '通过',
    passed: '通过',
    failed: '失败',
    partial: '部分通过',
    skipped: '跳过',
  }
  return map[status] ?? status
}

function sceneStatusClass(status: string): string {
  if (status === 'success' || status === 'passed') return 'rr-badge--ok'
  if (status === 'failed') return 'rr-badge--fail'
  return 'rr-badge--skip'
}
</script>

<template>
  <div v-if="report.result" class="rr">
    <!-- ===== 顶部概览（Hero） ===== -->
    <div class="rr-hero">
      <div class="rr-hero__top">
        <h1 class="rr-hero__title">{{ report.name }}</h1>
        <span class="rr-pill" :class="heroStatus.ok ? 'rr-pill--ok' : 'rr-pill--fail'">
          {{ heroStatus.ok ? '✓' : '✗' }} {{ heroStatus.label }}
        </span>
        <span class="rr-pill rr-pill--on-dark">{{ sourceLabel }}</span>
        <div class="rr-hero__actions">
          <slot name="hero-actions" />
        </div>
      </div>
      <div class="rr-hero__meta">
        <span>环境：<b>{{ heroEnv }}</b></span>
        <span>{{ heroTimeLabel }}：<b>{{ formatDateTime(heroTime) }}</b></span>
        <span>总耗时：<b class="rr-mono">{{ formatDuration(heroDuration) }}</b></span>
        <span>{{ heroId.label }}：<b class="rr-mono">{{ heroId.value ?? '-' }}</b></span>
      </div>
    </div>

    <!-- ===== 执行概览（5 项统计卡） ===== -->
    <div class="rr-card rr-card--plain">
      <div class="rr-stats">
        <div v-for="item in stats" :key="item.label" class="rr-stat" :class="`rr-stat--${item.tone}`">
          <div class="rr-stat__num">{{ item.value }}</div>
          <div class="rr-stat__lbl">{{ item.label }}</div>
        </div>
      </div>
    </div>

    <!-- 聚焦单场景提示 -->
    <el-alert v-if="focused" type="info" show-icon :closable="false" class="rr-focus-banner">
      <span>当前展示场景「{{ focusedScene?.sceneName ?? '-' }}」的执行明细</span>
      <el-button link type="primary" @click="emit('view-suite')">查看完整套件报告</el-button>
    </el-alert>

    <!-- ===== 环境级处理器卡（套件） ===== -->
    <section v-if="envPresent" class="rr-card">
      <ReportProcessorsTabs
        level="task"
        :pre="(suiteResult?.preprocessors ?? []) as ApiReportStepResult[]"
        :post="(suiteResult?.postprocessors ?? []) as ApiReportStepResult[]"
      />
    </section>

    <!-- ===== 场景卡列表 ===== -->
    <section
      v-for="(scene, index) in scenes"
      :key="sceneKey(scene, index)"
      class="rr-card rr-scene"
      :class="{
        'rr-scene--open': isSceneOpen(scene, index),
        'rr-scene--failed': scene.status === 'failed',
      }"
    >
      <div class="rr-scene__head" @click="toggleScene(scene, index)">
        <div class="rr-scene__ident">
          <h2 class="rr-scene__title">{{ scene.sceneName ?? '场景' }}</h2>
          <div class="rr-scene__meta">
            <span>场景 ID：<b class="rr-mono">{{ scene.sceneId ?? '-' }}</b></span>
            <span>执行时间：{{ formatDateTime(scene.executedAt) }}</span>
          </div>
        </div>
        <div class="rr-scene__mini">
          <div class="rr-mini">
            <div class="rr-mini__n">{{ sceneMiniStat(scene, 'total') }}</div>
            <div class="rr-mini__t">步骤</div>
          </div>
          <div class="rr-mini rr-mini--g">
            <div class="rr-mini__n">{{ sceneMiniStat(scene, 'passed') }}</div>
            <div class="rr-mini__t">通过</div>
          </div>
          <div class="rr-mini rr-mini--r">
            <div class="rr-mini__n">{{ sceneMiniStat(scene, 'failed') }}</div>
            <div class="rr-mini__t">失败</div>
          </div>
          <div class="rr-mini">
            <div class="rr-mini__n">{{ sceneMiniStat(scene, 'durationMs') }}</div>
            <div class="rr-mini__t">耗时</div>
          </div>
        </div>
        <span class="rr-badge" :class="sceneStatusClass(scene.status)">{{ statusLabel(scene.status) }}</span>
        <button type="button" class="rr-chev" :class="{ 'rr-chev--open': isSceneOpen(scene, index) }" @click.stop="toggleScene(scene, index)">
          <span class="rr-chev__i"></span>
        </button>
      </div>

      <div v-show="isSceneOpen(scene, index)" class="rr-scene__body">
        <ReportProcessorsTabs
          v-if="scene.preprocessors?.length || scene.postprocessors?.length"
          level="scene"
          :pre="(scene.preprocessors ?? []) as ApiReportStepResult[]"
          :post="(scene.postprocessors ?? []) as ApiReportStepResult[]"
        />
        <ReportStepCard :steps="(scene.steps ?? []) as ApiReportStepResult[]" />
      </div>
    </section>

    <!-- 页脚 -->
    <p class="rr-footer">
      报告生成时间 {{ formatDateTime(report.createdAt) }} · 来源：{{ sourceLabel }} · 环境：{{ heroEnv }}
    </p>
  </div>
  <el-empty v-else description="暂无报告内容" />
</template>

<style scoped lang="scss">
/* ==================== 顶部概览（Hero） ==================== */
.rr-hero {
  background: linear-gradient(135deg, #f2f9f4 0%, #d8ecdd 50%, #b8dcc2 100%);
  border-radius: 16px;
  padding: 24px 26px;
  color: #1a3d2a;
  margin-bottom: 18px;
  box-shadow: 0 10px 30px -14px rgba(61, 122, 85, 0.35);
  border: 1px solid rgba(61, 122, 85, 0.1);
}

.rr-hero__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.rr-hero__title {
  margin: 0;
  font-size: 21px;
  font-weight: 650;
  letter-spacing: 0.3px;
  color: #143322;
}

.rr-hero__meta {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  font-size: 12.8px;
  color: rgba(26, 61, 42, 0.72);

  b {
    font-weight: 600;
    color: #143322;
  }
}

.rr-hero__actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.rr-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;

  &--ok {
    background: #ffffff;
    color: #15803d;
    box-shadow: 0 1px 3px rgba(61, 122, 85, 0.12);
  }

  &--fail {
    background: #ffffff;
    color: #b91c1c;
    box-shadow: 0 1px 3px rgba(220, 38, 38, 0.12);
  }

  &--on-dark {
    background: rgba(26, 61, 42, 0.1);
    color: #1a3d2a;
  }
}

.rr-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

/* ==================== 通用卡片 ==================== */
.rr-card {
  background: #fff;
  border-radius: 14px;
  box-shadow:
    0 1px 2px rgba(16, 24, 40, 0.05),
    0 6px 20px -8px rgba(16, 24, 40, 0.12);
  margin-bottom: 18px;
  overflow: hidden;

  &--plain {
    padding: 18px 20px;
  }
}

/* ==================== 执行概览统计卡 ==================== */
.rr-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 12px;
}

.rr-stat {
  background: #fff;
  border: 1px solid #eef1f5;
  border-radius: 12px;
  padding: 14px 16px;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: #e5e7eb;
  }

  &--g::before {
    background: #22c55e;
  }

  &--r::before {
    background: #ef4444;
  }

  &--b::before {
    background: #3d7a55;
  }

  &--n::before {
    background: #94a3b8;
  }

  &--g .rr-stat__num {
    color: #16a34a;
  }

  &--r .rr-stat__num {
    color: #dc2626;
  }

  &--b .rr-stat__num {
    color: #3d7a55;
  }
}

.rr-stat__num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.5px;
  color: #1f2937;
}

.rr-stat__lbl {
  font-size: 12.5px;
  color: #6b7280;
  margin-top: 2px;
}

.rr-focus-banner {
  margin-bottom: 18px;
}

/* ==================== 场景卡 ==================== */
.rr-scene__head {
  padding: 18px 20px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  background: linear-gradient(180deg, #fbfdff, #fff);
  border-bottom: 1px solid #eef1f5;
  cursor: pointer;
  user-select: none;
}

.rr-scene__title {
  font-size: 16px;
  font-weight: 650;
  margin: 0;
}

.rr-scene__meta {
  font-size: 12.2px;
  color: #6b7280;
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;

  .rr-mono {
    color: #94a3b8;
  }
}

.rr-scene__mini {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

.rr-mini {
  background: #f8fafc;
  border: 1px solid #eef1f5;
  border-radius: 9px;
  padding: 6px 12px;
  text-align: center;
  min-width: 66px;

  &--g .rr-mini__n {
    color: #16a34a;
  }

  &--r .rr-mini__n {
    color: #dc2626;
  }
}

.rr-mini__n {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.rr-mini__t {
  font-size: 11px;
  color: #94a3b8;
}

.rr-chev {
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  padding: 6px 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  border-radius: 6px;
  flex: 0 0 auto;
  font-family: inherit;
  align-self: center;
  transition:
    background 0.15s,
    color 0.15s;

  &:hover {
    background: #eef2f7;
    color: #475569;
  }
}

.rr-chev__i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(45deg) translate(-2px, -2px);
  transition: transform 0.2s ease;
  pointer-events: none;
}

.rr-chev--open .rr-chev__i {
  transform: rotate(-45deg) translate(-1px, 1px);
}

.rr-scene__body {
  padding: 18px 20px 20px;
}

.rr-scene--failed .rr-scene__head {
  border-left: 3px solid var(--color-danger);
}

.rr-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;
  align-self: center;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }

  &--ok {
    background: #ecfdf3;
    color: #15803d;
  }

  &--fail {
    background: #fef2f2;
    color: #b91c1c;
  }

  &--skip {
    background: #f3f4f6;
    color: #4b5563;
  }
}

.rr-footer {
  font-size: 12.5px;
  color: #6b7280;
  text-align: center;
  margin: 22px 0 0;
}
</style>