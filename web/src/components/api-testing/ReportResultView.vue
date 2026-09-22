<script setup lang="ts">
import type { ApiReportStepResult } from '@/types'
import { formatDateTime } from '@/utils/format'
import { useReportResultView } from '@/composables/useReportResultView'
import type { ReportViewReport } from '@/composables/useReportResultView'
import ReportProcessorsTabs from './report/ReportProcessorsTabs.vue'
import ReportStepCard from './report/ReportStepCard.vue'

const props = defineProps<{
  report: ReportViewReport
  focusSceneId?: string | null
}>()

const emit = defineEmits<{
  (e: 'view-suite'): void
}>()

const {
  focused,
  focusedScene,
  scenes,
  sceneKey,
  isSceneOpen,
  toggleScene,
  heroStatus,
  sourceLabel,
  heroEnv,
  heroTime,
  heroTimeLabel,
  heroDuration,
  heroId,
  formatDuration,
  stats,
  envPresent,
  suiteResult,
  sceneMiniStat,
  statusLabel,
  sceneStatusClass,
} = useReportResultView(
  () => props.report,
  () => props.focusSceneId ?? null,
)
</script>

<template>
  <div v-if="report.result" class="rr">
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

    <div class="rr-card rr-card--plain">
      <div class="rr-stats">
        <div v-for="item in stats" :key="item.label" class="rr-stat" :class="`rr-stat--${item.tone}`">
          <div class="rr-stat__num">{{ item.value }}</div>
          <div class="rr-stat__lbl">{{ item.label }}</div>
        </div>
      </div>
    </div>

    <el-alert v-if="focused" type="info" show-icon :closable="false" class="rr-focus-banner">
      <span>当前展示场景「{{ focusedScene?.sceneName ?? '-' }}」的执行明细</span>
      <el-button link type="primary" @click="emit('view-suite')">查看完整套件报告</el-button>
    </el-alert>

    <section v-if="envPresent" class="rr-card">
      <ReportProcessorsTabs
        level="task"
        :pre="(suiteResult?.preprocessors ?? []) as ApiReportStepResult[]"
        :post="(suiteResult?.postprocessors ?? []) as ApiReportStepResult[]"
      />
    </section>

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

    <p class="rr-footer">
      报告生成时间 {{ formatDateTime(report.createdAt) }} · 来源：{{ sourceLabel }} · 环境：{{ heroEnv }}
    </p>
  </div>
  <el-empty v-else description="暂无报告内容" />
</template>

<style scoped lang="scss">
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
