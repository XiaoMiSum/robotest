<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getBugDetail } from '@/services/project'
import type { AiBugDedupItem, BugDetail } from '@/types'
import { formatDateTime, formatShortId } from '@/utils/format'
import { BUG_STATUS_LABEL, BUG_STATUS_TAG_TYPE, BUG_TYPE_LABEL } from '@/utils/bugStatus'
import MarkdownView from '@/components/common/MarkdownView.vue'

/**
 * 疑似重复缺陷卡片列表（US-AI-009，交互设计 3.2）：
 * 纯展示组件——查重数据由父级（BugAiSuggest）注入，自身不发起请求；
 * 点击卡片打开缺陷详情抽屉（不离开表单）。无结果时不占位。
 */
const { items, loading, semanticDegraded } = defineProps<{
  /** 查重命中列表（父级注入） */
  items: AiBugDedupItem[]
  /** 查重请求进行中（父级注入，驱动「查重中…」提示） */
  loading: boolean
  /** 语义检索降级为关键词匹配（父级注入，驱动顶部提示条） */
  semanticDegraded: boolean
}>()

const emit = defineEmits<{
  /** 卡片预选「原始缺陷」；重复点击取消预选，null 表示无选中 */
  'select-duplicate': [item: AiBugDedupItem | null]
  /** 确认重复后放弃本次提交，由创建页统一处理返回列表 */
  'abandon-submit': []
  /** 本次结果内忽略该条：由父级（BugAiSuggest）从列表剔除，不参与提交确认（交互设计 3.3） */
  ignore: [item: AiBugDedupItem]
}>()

const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }

// ==================== 缺陷详情抽屉 ====================

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<BugDetail | null>(null)

async function openDetail(item: AiBugDedupItem): Promise<void> {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getBugDetail(item.bugId)
  } catch {
    // 详情加载失败仅关闭抽屉，不打断表单
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const router = useRouter()
function goDetail(bugId: string): void {
  drawerVisible.value = false
  router.push(`/workspace/projects/bugs/${bugId}`)
}

/** 卡片级预选「原始缺陷」：选中态高亮，重复点击取消 */
const selectedBugId = ref('')
function toggleSelect(item: AiBugDedupItem): void {
  const next = selectedBugId.value === item.bugId ? null : item
  selectedBugId.value = next ? next.bugId : ''
  emit('select-duplicate', next)
}

/** 忽略该条：若其正被预选为「原始」，先取消预选，避免提交确认引用已忽略条目 */
function ignoreItem(item: AiBugDedupItem): void {
  if (selectedBugId.value === item.bugId) {
    selectedBugId.value = ''
    emit('select-duplicate', null)
  }
  emit('ignore', item)
}
</script>

<template>
  <!-- loading 仅随「AI 建议」点击出现（查重中提示），无结果时不占位（交互设计 3.2） -->
  <div v-if="items.length || loading" class="bug-dedup">
    <div class="bug-dedup__header">
      <span v-if="items.length" class="bug-dedup__count">疑似重复缺陷（{{ items.length }}）</span>
      <span v-else-if="loading" class="bug-dedup__hint">查重中…</span>
      <div class="bug-dedup__actions">
        <el-button v-if="items.length" size="small" text type="danger" @click="emit('abandon-submit')">
          放弃提交
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="semanticDegraded && items.length"
      type="warning"
      :closable="false"
      show-icon
      title="当前为关键词匹配结果（语义检索暂不可用）"
      class="bug-dedup__degraded"
    />

    <div v-if="items.length" class="bug-dedup__list">
      <div
        v-for="item in items"
        :key="item.bugId"
        class="bug-dedup__item"
        :class="{ 'bug-dedup__item--selected': selectedBugId === item.bugId }"
        @click="openDetail(item)"
      >
        <span v-if="item.similarity !== null" class="bug-dedup__similarity">
          {{ Math.round(item.similarity * 100) }}%
        </span>
        <span class="bug-dedup__title">{{ item.title }}</span>
        <el-tag :type="BUG_STATUS_TAG_TYPE[item.status]" size="small" effect="light" round>
          {{ BUG_STATUS_LABEL[item.status] }}
        </el-tag>
        <span v-if="item.assigneeName" class="bug-dedup__assignee">{{ item.assigneeName }}</span>
        <el-tooltip content="提交时将新缺陷标记为这条缺陷的重复" placement="top">
          <el-button
            size="small"
            text
            type="primary"
            class="bug-dedup__select"
            @click.stop="toggleSelect(item)"
          >
            {{ selectedBugId === item.bugId ? '已选' : '选为原始' }}
          </el-button>
        </el-tooltip>
        <el-tooltip content="本次结果内不再提示，不参与提交确认" placement="top">
          <el-button size="small" text class="bug-dedup__ignore" @click.stop="ignoreItem(item)">
            忽略
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" size="420px" :modal="false" title="疑似重复缺陷详情">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="bug-dedup-detail__title">{{ detail.title }}</div>
          <div class="bug-dedup-detail__meta">
            <el-tag :type="BUG_STATUS_TAG_TYPE[detail.status]" size="small" effect="light" round>
              {{ BUG_STATUS_LABEL[detail.status] }}
            </el-tag>
            <el-tag size="small" effect="plain" round>{{ severityLabel[detail.severity] }}</el-tag>
            <el-tag size="small" effect="plain" round>{{ BUG_TYPE_LABEL[detail.bugType] }}</el-tag>
          </div>
          <el-descriptions :column="1" size="small" class="bug-dedup-detail__desc">
            <el-descriptions-item label="编号">{{ formatShortId(detail.id) }}</el-descriptions-item>
            <el-descriptions-item label="模块">{{ detail.moduleName ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detail.assignee?.name ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="报告人">{{ detail.reporter.name }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="detail.reproSteps" class="bug-dedup-detail__repro">
            <div class="bug-dedup-detail__repro-label">重现步骤</div>
            <MarkdownView :content="detail.reproSteps" />
          </div>
          <el-button class="bug-dedup-detail__link" link type="primary" @click="goDetail(detail.id)">
            打开完整详情页
          </el-button>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.bug-dedup {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
}

// 查重区与建议区间以细分隔线划分，保持同一 AI 结果面板内的视觉层次
.bug-dedup {
  border-top: 1px dashed var(--color-primary-200);
}

.bug-dedup__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.bug-dedup__actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.bug-dedup__count {
  font-size: var(--font-size-2xs);
  font-weight: 600;
  color: var(--color-warning);
}

.bug-dedup__hint {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.bug-dedup__degraded {
  padding: 6px 12px;
}

.bug-dedup__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bug-dedup__item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px 10px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--color-primary-400);
  }

  // 卡片预选「原始缺陷」的选中态
  &--selected {
    border-color: var(--color-primary-400);
    background: var(--color-primary-50);
  }
}

.bug-dedup__select {
  flex-shrink: 0;
  margin-left: var(--space-xs);
  padding: 0 4px;
}

.bug-dedup__ignore {
  flex-shrink: 0;
  margin-left: 0;
  padding: 0 4px;
  color: var(--color-neutral-400);
}

.bug-dedup__similarity {
  flex-shrink: 0;
  font-size: var(--font-size-2xs);
  font-weight: 700;
  color: var(--color-warning);
  min-width: 36px;
}

.bug-dedup__title {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-dedup__assignee {
  flex-shrink: 0;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.bug-dedup-detail__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
  margin-bottom: var(--space-sm);
}

.bug-dedup-detail__meta {
  display: flex;
  gap: var(--space-xs);
  margin-bottom: var(--space-sm);
}

.bug-dedup-detail__desc {
  margin-bottom: var(--space-sm);
}

.bug-dedup-detail__repro {
  margin-bottom: var(--space-sm);
}

.bug-dedup-detail__repro-label {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
  margin-bottom: var(--space-xs);
}
</style>
