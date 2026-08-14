<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { suggestBugForm } from '@/services/ai'
import { useBugDedup } from '@/composables/useBugDedup'
import BugDedupList from '@/components/project/BugDedupList.vue'
import type { AiBugDedupItem, AiBugSuggestion, BugPriority, BugSeverity } from '@/types'

/**
 * 缺陷表单 AI 结果面板（US-AI-008 / US-AI-009，交互设计 1.1/2.1/3.1）：
 * 入口按钮由父组件置于标题输入框 #append，点击后并发发起建议与查重两个请求，
 * 结果合并展示于本面板（建议区 + 疑似重复缺陷区），各区域独立 loading；
 * 头部 [收起/展开] 仅折叠内容（结果保留、不重新请求，交互设计 2.1）；
 * 查重卡片 [忽略] 为本次结果内过滤，被忽略条目不参与提交确认（交互设计 3.3）；
 * 仅回填表单待用户确认（一键替换标题 / 采纳等级），不产生任何自动提交；
 * 调用失败仅 Toast 轻提示，不影响表单任何操作；无任何结果时不占位（2.7 零影响原则）。
 */
const props = defineProps<{
  title: string
  reproSteps?: string
}>()

const emit = defineEmits<{
  applyTitle: [title: string]
  applySeverity: [severity: BugSeverity]
  applyPriority: [priority: BugPriority]
  /** 过滤忽略后的查重命中列表上抛：创建页提交时据此决定是否弹确认层（US-AI-009） */
  'dedup-change': [items: AiBugDedupItem[]]
  /** 卡片预选「原始缺陷」透传 */
  'select-duplicate': [item: AiBugDedupItem | null]
  /** 确认重复后放弃提交透传 */
  'abandon-submit': []
}>()

const severityLabel: Record<BugSeverity, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<BugPriority, string> = { high: '高', medium: '中', low: '低' }

const suggestion = ref<AiBugSuggestion | null>(null)
const loading = ref(false)
const titleState = ref<'idle' | 'accepted' | 'dismissed'>('idle')
const severityAdopted = ref(false)
const priorityAdopted = ref(false)

// 面板收起为仅标题行；本地 UI 状态，收起不清除结果（交互设计 2.1）
const collapsed = ref(false)

// 本次结果内被忽略的查重条目标记：重新查重（新结果注入）时清空（交互设计 3.3）
const ignoredBugIds = ref<string[]>([])

// 查重随「AI 建议」按钮并发触发：建议与查重各持独立 loading（交互设计 2.1）
const {
  items: dedupItems,
  semanticDegraded,
  loading: dedupLoading,
  run: runDedup,
} = useBugDedup({
  title: () => props.title,
  reproSteps: () => props.reproSteps,
})

// 渲染列表 = 查重结果剔除被忽略条目
const filteredDedupItems = computed(() =>
  dedupItems.value.filter((item) => !ignoredBugIds.value.includes(item.bugId)),
)

// 新查重结果注入时清空旧忽略标记，避免旧结果污染新结果
watch(dedupItems, () => {
  ignoredBugIds.value = []
})

// 过滤后列表上抛给父级，父级据其最新值决定提交时是否拦截确认（无命中不弹层）
watch(filteredDedupItems, (list) => emit('dedup-change', list))

// 建议请求进行中不阻塞输入与提交（非侵入原则）；标题为空时后端会校验拒绝，先在前端拦截
async function requestSuggestion(): Promise<void> {
  if (!props.title.trim()) {
    ElMessage.warning('请先输入缺陷标题')
    return
  }
  loading.value = true
  // 新请求触发时自动展开面板，便于查看最新结果
  collapsed.value = false
  // 查重与建议并发发起，各区域独立 loading，互不阻断
  void runDedup()
  try {
    suggestion.value = await suggestBugForm({
      title: props.title,
      reproSteps: props.reproSteps?.trim() || undefined,
    })
    titleState.value = 'idle'
    severityAdopted.value = false
    priorityAdopted.value = false
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : 'AI 建议获取失败')
  } finally {
    loading.value = false
  }
}

// 父组件标题输入框 #append 按钮经 ref 调用；loading 解包后驱动按钮 loading 态
defineExpose({ requestSuggestion, loading })

function acceptTitle(): void {
  if (!suggestion.value) return
  emit('applyTitle', suggestion.value.optimizedTitle)
  titleState.value = 'accepted'
}

function dismissTitle(): void {
  titleState.value = 'dismissed'
}

function adoptSeverity(): void {
  if (!suggestion.value) return
  emit('applySeverity', suggestion.value.severity)
  severityAdopted.value = true
}

function adoptPriority(): void {
  if (!suggestion.value) return
  emit('applyPriority', suggestion.value.priority)
  priorityAdopted.value = true
}

/** 本次结果内忽略查重条目：从渲染列表剔除，不参与提交确认（交互设计 3.3） */
function ignoreDedupItem(item: AiBugDedupItem): void {
  ignoredBugIds.value = [...ignoredBugIds.value, item.bugId]
}
</script>

<template>
  <!-- 无任何结果不渲染（2.7 布局零侵入）；查重 loading 仅随 AI 建议点击出现 -->
  <div
    v-if="(suggestion && titleState !== 'dismissed') || dedupItems.length || dedupLoading"
    class="bug-ai-suggest"
  >
    <div class="bug-ai-suggest__header">
      <div class="bug-ai-suggest__head">
        <el-icon><MagicStick /></el-icon><span>AI 结果</span>
      </div>
      <el-button size="small" text class="bug-ai-suggest__toggle" @click="collapsed = !collapsed">
        <el-icon>
          <ArrowUp v-if="!collapsed" />
          <ArrowDown v-else />
        </el-icon>
        {{ collapsed ? '展开' : '收起' }}
      </el-button>
    </div>

    <!-- v-show 折叠：结果数据保留不销毁，展开即刻恢复 -->
    <div v-show="!collapsed" class="bug-ai-suggest__body">
      <div v-if="suggestion && titleState !== 'dismissed'" class="bug-ai-suggest__section">
        <div class="bug-ai-suggest__item">
          <div class="bug-ai-suggest__label">优化标题</div>
          <div class="bug-ai-suggest__title">
            {{ suggestion.optimizedTitle }}
            <el-tag v-if="titleState === 'accepted'" size="small" type="success" effect="light">已采纳</el-tag>
          </div>
          <div v-if="titleState === 'idle'" class="bug-ai-suggest__actions">
            <el-button size="small" type="primary" @click="acceptTitle">一键替换</el-button>
            <el-button size="small" @click="dismissTitle">忽略</el-button>
          </div>
        </div>

        <el-divider class="bug-ai-suggest__divider" />

        <div class="bug-ai-suggest__item">
          <div class="bug-ai-suggest__label">等级建议</div>
          <div class="bug-ai-suggest__values">
            <el-tag
              size="small"
              class="bug-ai-suggest__value"
              :class="{ 'bug-ai-suggest__value--adopted': severityAdopted }"
              @click="adoptSeverity"
            >
              {{ severityLabel[suggestion.severity] }}
            </el-tag>
            <span v-if="severityAdopted" class="bug-ai-suggest__adopted-tip">已回填</span>
          </div>
        </div>

        <div class="bug-ai-suggest__item">
          <div class="bug-ai-suggest__label">优先级建议</div>
          <div class="bug-ai-suggest__values">
            <el-tag
              size="small"
              class="bug-ai-suggest__value"
              :class="{ 'bug-ai-suggest__value--adopted': priorityAdopted }"
              @click="adoptPriority"
            >
              {{ priorityLabel[suggestion.priority] }}
            </el-tag>
            <span v-if="priorityAdopted" class="bug-ai-suggest__adopted-tip">已回填</span>
          </div>
        </div>

        <div class="bug-ai-suggest__reason">{{ suggestion.reason }}</div>
      </div>

      <BugDedupList
        :items="filteredDedupItems"
        :loading="dedupLoading"
        :semantic-degraded="semanticDegraded"
        @select-duplicate="emit('select-duplicate', $event)"
        @abandon-submit="emit('abandon-submit')"
        @ignore="ignoreDedupItem"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
// 内联建议面板：primary 语义高亮（背景 50 / 边框 200 / 左侧 accent 条），与普通表单卡片区分以突出 AI 能力
.bug-ai-suggest {
  margin-bottom: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-200);
  border-left: 3px solid var(--color-primary-500);
  border-radius: var(--radius-md);
}

.bug-ai-suggest__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.bug-ai-suggest__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
}

.bug-ai-suggest__head {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: var(--font-size-base);
  color: var(--color-primary-600);
}

// 收起/展开按钮：轻量文字按钮，不喧宾夺主
.bug-ai-suggest__toggle {
  padding: 0 4px;
  color: var(--color-neutral-400);
}

.bug-ai-suggest__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bug-ai-suggest__label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.bug-ai-suggest__title {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
  line-height: 1.6;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bug-ai-suggest__actions {
  display: flex;
  gap: var(--space-sm);
}

.bug-ai-suggest__divider {
  margin: var(--space-sm) 0;
}

.bug-ai-suggest__values {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

// 建议值以可点击标签呈现，悬停高亮暗示可回填
.bug-ai-suggest__value {
  cursor: pointer;

  &:hover {
    opacity: 0.8;
  }

  &--adopted {
    opacity: 0.6;
  }
}

.bug-ai-suggest__adopted-tip {
  font-size: var(--font-size-xs);
  color: var(--color-success);
}

.bug-ai-suggest__reason {
  margin-top: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  line-height: 1.6;
}
</style>
