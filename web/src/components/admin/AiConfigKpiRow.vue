<script setup lang="ts">
// KPI 真值与配置/统计同源派生，防止两处口径漂移
defineProps<{
  modelsEnabled: number
  modelsTotal: number
  defaultModelName: string
  embeddingConfigured: boolean
  embeddingModel: string
  embeddingDimension: number | null
  settingsModified: number
  settingsTotal: number
}>()
</script>

<template>
  <div class="kpi-row">
    <div class="kpi">
      <div class="kpi__label">
        <el-icon :size="14"><ChatDotRound /></el-icon>
        对话模型
      </div>
      <div class="kpi__value">
        {{ modelsEnabled }}<span class="kpi__unit">个已启用</span>
      </div>
      <div class="kpi__foot">共 {{ modelsTotal }} 个 · 默认 {{ defaultModelName }}</div>
    </div>

    <div class="kpi">
      <div class="kpi__label">
        <el-icon :size="14"><DataLine /></el-icon>
        Embedding 模型
      </div>
      <div class="kpi__value">
        <template v-if="embeddingConfigured && embeddingDimension">
          {{ embeddingDimension }}<span class="kpi__unit">维</span>
        </template>
        <template v-else>—</template>
      </div>
      <div class="kpi__foot">
        <template v-if="embeddingConfigured">
          {{ embeddingModel }} · <span class="kpi__delta">已配置</span>
        </template>
        <template v-else>未配置</template>
      </div>
    </div>

    <div class="kpi">
      <div class="kpi__label">
        <el-icon :size="14"><Setting /></el-icon>
        系统配置项
      </div>
      <div
        class="kpi__value"
        :class="{ 'kpi__value--modified': settingsModified > 0 }"
      >
        {{ settingsModified }}<span class="kpi__unit">项已修改</span>
      </div>
      <div class="kpi__foot">共 {{ settingsTotal }} 项 · 修改即自动保存</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 网格自带 gap，兄弟卡不得再叠加 margin，否则行内错位 */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--block-gap);
  margin-bottom: var(--block-gap);
}

.kpi {
  padding: 20px 24px;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.kpi__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.kpi__value {
  margin-top: 10px;
  font-size: 30px;
  font-weight: 650;
  color: var(--color-neutral-900);
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;

  /* 存在已修改项时以警示橙点出，不占用交互蓝 */
  &--modified,
  &--modified .kpi__unit {
    color: var(--color-warning);
  }
}

.kpi__unit {
  font-size: var(--font-size-base);
  font-weight: 500;
  color: var(--color-neutral-500);
  margin-left: 4px;
}

.kpi__foot {
  margin-top: 10px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  display: flex;
  align-items: center;
  gap: 6px;
}

.kpi__delta {
  font-weight: 600;
  color: var(--color-success);
}
</style>
