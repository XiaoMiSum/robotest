<script setup lang="ts">
import { usePlanOrderRecommend } from '@/composables/project/functional-testing/plan/usePlanOrderRecommend'
import { ref } from 'vue'
import type { AiPlanOrderRecommendItem } from '@/types'
import {
  resolveCaseTitle,
  scoreLabel,
} from './planOrderRecommend'

/**
 * 执行顺序推荐标签页内容（US-AI-017，交互设计第 5 章）：
 * 打开先读最近一次推荐结果与 stale 失效态；[开始计算]/[重新计算] 走确定性加权计算
 * （同步长调用 70s 可取消）；结果行按 #序号降序展示，展开因子明细并支持按需生成理由。
 * 行点击定位脑图由父组件完成，本组件仅 emit locate；入口权限由父组件控制。
 */
const props = defineProps<{ planId: string }>()
const emit = defineEmits<{
  locate: [snapshotNodeId: string]
  /** 推荐结果就绪时通知父组件注入脑图 #序号 徽标（双向联动数据源） */
  result: [items: AiPlanOrderRecommendItem[]]
}>()

const {
  computing,
  loaded,
  expanded,
  reasoning,
  caseMeta,
  items,
  stale,
  hasResult,
  metaOf,
  priorityTagType,
  cancelCompute,
  toggleExpand,
  generateReason,
  rowClick,
  load,
  compute,
  scrollToOrder,
} = usePlanOrderRecommend({
  getPlanId: () => props.planId,
  onResult: (items) => emit('result', items),
  getListRef: () => listRef.value,
  onLocate: (snapshotNodeId) => emit('locate', snapshotNodeId),
})

const listRef = ref<HTMLElement>()

// 暴露计算入口与结果状态，供页头标签栏 [开始计算]/[重新计算] 按钮复用（交互设计 5.1）
defineExpose({ compute, load, scrollToOrder, hasResult, computing })
</script>

<template>
  <div class="plan-order">
    <!-- stale 失效提示：计划快照已重新同步，需重新计算（3.4.2） -->
    <el-alert
      v-if="stale"
      type="warning"
      :closable="false"
      show-icon
      title="计划快照已重新同步，推荐结果已失效，请重新计算"
      class="plan-order__stale"
    />

    <!-- 计算中：同步长调用展示取消入口 -->
    <div v-if="computing" class="plan-order__computing">
      <el-progress :percentage="100" :indeterminate="true" :duration="2" :stroke-width="8" />
      <div class="plan-order__computing-text">正在计算执行顺序（确定性加权，非 LLM）…</div>
      <el-button size="small" @click="cancelCompute">取消计算</el-button>
    </div>

    <!-- 空态：首次进入无结果 -->
    <div v-else-if="!hasResult && loaded" class="plan-order__empty">
      <el-empty description="暂无执行顺序推荐结果，点击标签栏右上角 [开始计算]" :image-size="72" />
    </div>

    <!-- 结果列表：按推荐执行指数降序 -->
    <div v-else-if="hasResult" ref="listRef" class="plan-order__list">
      <div
        v-for="item in items"
        :key="item.snapshotNodeId"
        :data-order="item.order"
        class="plan-order__row"
        @click="rowClick(item.snapshotNodeId)"
      >
        <div class="plan-order__row-head">
          <span class="plan-order__no">#{{ item.order }}</span>
          <span class="plan-order__title">{{ resolveCaseTitle(caseMeta, item.snapshotNodeId) }}</span>
          <span class="plan-order__score">{{ scoreLabel(item.score) }}</span>
          <el-tag v-if="metaOf(item.snapshotNodeId)?.priority" size="small" :type="priorityTagType(metaOf(item.snapshotNodeId)?.priority)">
            {{ metaOf(item.snapshotNodeId)?.priority }}
          </el-tag>
          <el-button size="small" text @click.stop="toggleExpand(item.snapshotNodeId)">
            {{ expanded.has(item.snapshotNodeId) ? '▾' : '▸' }} 因子明细
          </el-button>
        </div>

        <div v-if="expanded.has(item.snapshotNodeId)" class="plan-order__detail">
          <div class="plan-order__factors">
            <span class="plan-order__factor">历史关联缺陷数：{{ item.factors.relatedBugCount }}</span>
            <span class="plan-order__factor">优先级权重：{{ item.factors.priorityWeight }}</span>
            <span class="plan-order__factor">模块缺陷密度：{{ item.factors.moduleBugDensity }}</span>
          </div>
          <div v-if="item.reason" class="plan-order__reason">
            <div class="plan-order__reason-text">{{ item.reason }}</div>
          </div>
          <el-button
            v-else
            size="small"
            :loading="reasoning.has(item.snapshotNodeId)"
            @click.stop="generateReason(item)"
          >
            生成理由
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.plan-order {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  overflow: hidden;
}

.plan-order__stale {
  flex-shrink: 0;
}

.plan-order__computing {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  padding: 24px 0;
}

.plan-order__computing-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.plan-order__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.plan-order__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 2px;
}

.plan-order__row {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
  }
}

.plan-order__row-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.plan-order__no {
  flex-shrink: 0;
  font-weight: 700;
  color: var(--color-order-badge);
}

.plan-order__title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.plan-order__score {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.plan-order__detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.plan-order__factors {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
}

.plan-order__factor {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.plan-order__reason {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
}
</style>
