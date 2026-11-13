<script setup lang="ts">
import { computed } from 'vue'

/**
 * 全站统一分页条（UI-SC-06/10 固定控件）：左文案或操作槽 + 右分页。
 * 统一档位 20/50/100、默认 20，与 API 分页协议默认值一致（20-contracts/01-api.md §5）。
 */
interface Props {
  total?: number
  pageNo?: number
  pageSize?: number
  /** 每页条数档位；空数组 = 隐藏尺寸选择器（弹窗/抽屉等固定条数场景） */
  pageSizes?: number[]
  /** 左侧文案单位：条 / 个计划 / 场 / 人… */
  unit?: string
  /** 左侧显示「共 N {unit} · 每页 N 条」；false 时 total 并入右侧 layout（左侧让给操作按钮） */
  showTotalText?: boolean
  /** 紧凑模式（弹窗/抽屉内，EP small） */
  small?: boolean
  /** 无分割线无留白（弹层/卡片网格下方等非卡片底栏场景） */
  bare?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  pageSizes: () => [20, 50, 100],
  unit: '条',
  showTotalText: true,
  small: false,
  bare: false,
})

const emit = defineEmits<{
  'update:page-no': [value: number]
  'update:page-size': [value: number]
  /** 页码或条数变化后的统一出口（切条数时 pageNo 已先置 1） */
  change: [value: { pageNo: number; pageSize: number }]
  'page-change': [value: { pageNo: number; pageSize: number }]
  'size-change': [value: { pageNo: number; pageSize: number }]
}>()

// total 不进右侧 layout（由左侧文案承载）除非显式隐藏左侧文案；sizes 由档位数组是否为空决定
const layout = computed(() => {
  const parts: string[] = []
  if (!props.showTotalText) parts.push('total')
  if (props.pageSizes.length > 0) parts.push('sizes')
  parts.push('prev', 'pager', 'next')
  return parts.join(', ')
})

function handlePageChange(page: number) {
  const payload = { pageNo: page, pageSize: props.pageSize }
  emit('update:page-no', page)
  emit('page-change', payload)
  emit('change', payload)
}

function handleSizeChange(size: number) {
  // 切换条数后回到第 1 页，避免停留在超出新容量的页码
  emit('update:page-size', size)
  emit('update:page-no', 1)
  const payload = { pageNo: 1, pageSize: size }
  emit('size-change', payload)
  emit('change', payload)
}
</script>

<template>
  <div class="list-pager" :class="{ 'list-pager--bare': bare }">
    <div class="list-pager__left">
      <slot name="left">
        <span v-if="showTotalText" class="list-pager__total">
          共 {{ total }} {{ unit }} · 每页 {{ pageSize }} 条
        </span>
      </slot>
    </div>
    <el-pagination
      :small="small"
      :layout="layout"
      :page-sizes="pageSizes"
      :page-size="pageSize"
      :current-page="pageNo"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.list-pager {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
}

/* 弹层/卡片网格下方等场景：无分割线无留白，避免悬浮线条与多余缩进 */
.list-pager--bare {
  padding: 0;
  margin-top: var(--space-md);
  border-top: 0;
}

.list-pager__total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}
</style>
