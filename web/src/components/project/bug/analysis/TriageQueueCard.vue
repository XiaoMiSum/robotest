<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatShortId } from '@/utils/format'
import type { BugTriageContent } from '@/types'

const props = defineProps<{
  items: BugTriageContent['items']
  loading: boolean
  error: string
}>()

const emit = defineEmits<{ refresh: [] }>()

const router = useRouter()

/** 本地排序副本：调整仅作用于跟进视图，AI 不自动改派缺陷（交互 2.1.2） */
const ordered = ref<BugTriageContent['items']>([])

// 重新生成拿到新建议时重置本地排序
watch(
  () => props.items,
  (items) => {
    ordered.value = [...items]
  },
  { immediate: true },
)

const displayItems = computed(() =>
  ordered.value.length > 0 ? ordered.value : props.items,
)

// ---------- 原生 HTML5 拖拽排序（不新增外部依赖） ----------
const dragIndex = ref<number | null>(null)

function onDragStart(index: number): void {
  dragIndex.value = index
}

function onDrop(index: number): void {
  const from = dragIndex.value
  dragIndex.value = null
  if (from === null || from === index) return
  const next = [...displayItems.value]
  const [moved] = next.splice(from, 1)
  next.splice(index, 0, moved)
  // 仅本地排序落 ordered，不调用任何写接口
  ordered.value = next.map((item, position) => ({ ...item, rank: position + 1 }))
}

function openBug(bugId: string): void {
  void router.push(`/workspace/projects/bugs/${bugId}`)
}
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="triage-card">
    <template #header>
      <div class="triage-card__head">
        <div>
          <span class="triage-card__title">分诊队列</span>
          <span class="triage-card__scope">激活未指派 · 建议顺序可拖拽调整</span>
        </div>
        <el-button size="small" :disabled="loading" @click="emit('refresh')">重新生成</el-button>
      </div>
    </template>

    <el-alert
      v-if="error"
      type="info"
      :title="error"
      show-icon
      :closable="false"
      class="triage-card__error"
    />

    <el-empty
      v-else-if="displayItems.length === 0"
      description="暂无分诊建议"
      :image-size="60"
    />

    <ol v-else class="triage-card__list">
      <li
        v-for="(item, index) in displayItems"
        :key="item.bugId"
        class="triage-card__item"
        draggable="true"
        @dragstart="onDragStart(index)"
        @dragover.prevent
        @drop="onDrop(index)"
      >
        <span class="triage-card__rank">{{ index + 1 }}</span>
        <div class="triage-card__main">
          <el-link type="primary" :underline="false" @click="openBug(item.bugId)">
            {{ formatShortId(item.bugId) }}
          </el-link>
          <!-- 建议理由悬浮展示，不写入任何数据 -->
          <el-tooltip :content="item.reason || '无建议理由'" placement="top">
            <span class="triage-card__reason">{{ item.reason || '—' }}</span>
          </el-tooltip>
        </div>
        <el-icon class="triage-card__drag" aria-label="拖拽调整顺序"><Rank /></el-icon>
      </li>
    </ol>
  </el-card>
</template>

<style scoped lang="scss">
.triage-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.triage-card__title {
  font-weight: 600;
}

.triage-card__scope {
  margin-left: var(--space-sm);
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.triage-card__error {
  margin-bottom: var(--space-sm);
}

.triage-card__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.triage-card__item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  cursor: grab;
}

.triage-card__item:active {
  cursor: grabbing;
}

.triage-card__rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--color-primary-50);
  color: var(--color-primary-500);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.triage-card__main {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  min-width: 0;
  flex: 1;
}

.triage-card__reason {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.triage-card__drag {
  color: var(--color-neutral-400);
  flex-shrink: 0;
}
</style>
