<script setup lang="ts">
import { useRequirementPicker } from '@/composables/project/functional-testing/requirement/useRequirementPicker'
import { useRequirementDetail } from '@/composables/project/functional-testing/requirement/useRequirementDetail'
import MarkdownView from '@/components/common/MarkdownView.vue'
import { formatDate } from '@/utils/format'
import { watch } from 'vue'
import type { RequirementSummary } from '@/types'

/**
 * 需求选取器（US-AI-004 交互设计 6.2，可复用）：
 * 供「文档关联」「AI 生成/补全」「遗漏测试点分析」「回归子集推荐」入口调用。跨页多选以 selected Map 保序保留。
 */
const props = defineProps<{
  selectedIds?: string[]
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  confirm: [selected: RequirementSummary[]]
}>()

const {
  loading,
  items,
  total,
  keyword,
  pageNo,
  pageSize,
  selected,
  search,
  handlePageChange,
  load,
} = useRequirementPicker()

function toggle(id: string, title: string, checked: boolean): void {
  const next = new Map(selected.value)
  if (checked) next.set(id, title)
  else next.delete(id)
  selected.value = next
}

function confirm(): void {
  emit(
    'confirm',
    Array.from(selected.value, ([id, title]) => ({ id, title })),
  )
  visible.value = false
}

const {
  detailId,
  content: detailContent,
  loading: detailLoading,
  toggle: toggleDetail,
  close: closeDetail,
} = useRequirementDetail()

// 每次打开同步外部已选并加载首页
watch(
  visible,
  (open) => {
    if (!open) {
      closeDetail()
      return
    }
    const map = new Map<string, string>()
    // 外部仅传 id，标题在列表加载后补全展示；此处先占位空串
    for (const id of props.selectedIds ?? []) map.set(id, '')
    selected.value = map
    keyword.value = ''
    pageNo.value = 1
    load()
  },
  { immediate: true },
)

// 翻页或过滤后锚点所在行已换，气泡随之收起
watch(items, () => closeDetail())
</script>

<template>
  <el-dialog v-model="visible" title="选择需求" width="520px" append-to-body>
    <div class="req-selector">
      <div class="req-selector__search">
        <el-input
          v-model="keyword"
          placeholder="按标题过滤"
          clearable
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">过滤</el-button>
      </div>

      <div v-loading="loading" class="req-selector__list">
        <div v-if="!items.length" class="req-selector__empty">
          <el-empty description="暂无需求" :image-size="60" />
        </div>
        <label v-for="item in items" :key="item.id" class="req-selector__item">
          <el-checkbox
            :model-value="selected.has(item.id)"
            @update:model-value="(v: unknown) => toggle(item.id, item.title, v === true)"
          />
          <span class="req-selector__title">{{ item.title }}</span>
          <span class="req-selector__date">{{ formatDate(item.updatedAt) }}</span>
          <!-- [明细] 位于 label 内：阻断冒泡避免 label 把点击转发给勾选框，气泡不联动勾选（52 §1.2） -->
          <el-popover
            :visible="detailId === item.id"
            placement="left-start"
            :width="340"
            trigger="click"
            popper-class="req-detail-pop"
          >
            <template #reference>
              <el-button
                link
                size="small"
                class="req-selector__detail"
                data-req-detail="true"
                @click.stop="toggleDetail(item.id)"
              >
                明细
              </el-button>
            </template>
            <div class="req-detail">
              <el-button
                link
                class="req-detail__close"
                aria-label="关闭明细"
                @click="closeDetail"
              >
                <el-icon><Close /></el-icon>
              </el-button>
              <div class="req-detail__body">
                <span v-if="detailLoading" class="req-detail__hint">加载中…</span>
                <MarkdownView v-else-if="detailContent" :content="detailContent" />
                <span v-else class="req-detail__hint">正文为空</span>
              </div>
            </div>
          </el-popover>
        </label>
      </div>

      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNo"
        :page-size="pageSize"
        @current-change="handlePageChange"
      />
    </div>

    <template #footer>
      <div class="req-selector__footer">
        <span class="req-selector__count">已选 {{ selected.size }} 条</span>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="confirm">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.req-selector__search {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.req-selector__list {
  min-height: 200px;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 4px 0;
}

.req-selector__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.req-selector__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 更新日期列：固定不参与标题省略，与筛选出的标题同行对齐（52 §1.2 选取器布局） */
.req-selector__date {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.req-selector__detail {
  flex-shrink: 0;
}

/* 明细气泡：[×] 常驻右上角，仅正文层滚动（52 §1.2） */
.req-detail {
  position: relative;
}

.req-detail__close {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
}

.req-detail__body {
  max-height: 60vh;
  overflow-y: auto;
  /* 右上留白避免正文钻到 [×] 下 */
  padding: 14px 34px 14px 16px;
}

.req-detail__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.req-selector__footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.req-selector__count {
  flex: 1;
  text-align: right;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
