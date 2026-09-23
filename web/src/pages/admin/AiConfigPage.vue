<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAiConfigPage } from '@/composables/ai/useAiConfigPage'
import { useAiChatModels } from '@/composables/ai/useAiChatModels'
import AiAgentsTab from '@/components/admin/AiAgentsTab.vue'
import AiMasterSwitch from '@/components/admin/AiMasterSwitch.vue'
import AiConfigKpiRow from '@/components/admin/AiConfigKpiRow.vue'
import AiChatModelTable from '@/components/admin/AiChatModelTable.vue'
import AiEmbeddingForm from '@/components/admin/AiEmbeddingForm.vue'
import AiSettingsSection from '@/components/admin/AiSettingsSection.vue'
import AiStatisticsTab from '@/components/admin/AiStatisticsTab.vue'
import AiModelFormDialog from '@/components/admin/AiModelFormDialog.vue'

const cfg = useAiConfigPage()
const models = useAiChatModels({
  presetOf: cfg.presetOf,
  parsing: { parseJsonObject: cfg.parseJsonObject },
  saving: { saving: cfg.saving },
})

// 总开关「开启」前置校验依赖已启用对话模型数，模型列表变更时同步
watch(
  () => models.enabledCount.value,
  (count) => {
    cfg.chatModelsEnabledCount.value = count
  },
)

// tab 计数徽标经面板实例回传，页面不重复拉取智能体列表
const agentsTabRef = ref<InstanceType<typeof AiAgentsTab> | null>(null)
const agentsCount = computed(() => agentsTabRef.value?.agentsCount ?? 0)
const defaultModelName = computed(
  () => models.chatModels.value.find((m) => m.isDefault)?.name ?? '—',
)

// 供应商显示名取预设名，缺失回退 provider key，避免表格裸 key
const providerLabel = (provider: string): string =>
  cfg.presetOf(provider)?.name ?? provider

onMounted(async () => {
  await cfg.loadAll()
  await models.refresh()
})
</script>

<template>
  <div class="ai-config-page">
    <div class="ai-config-page__head">
      <div class="ai-config-page__head-text">
        <h1 class="ai-config-page__title">AI 配置</h1>
        <p class="ai-config-page__desc">
          关闭后前端隐藏全部 AI 入口，进行中任务被取消；开启需已启用至少一个对话模型
        </p>
      </div>
      <AiMasterSwitch
        v-model="cfg.form.enabled"
        :loading="cfg.loading.value"
        @before-change="cfg.handleMasterBeforeChange"
      />
    </div>

    <AiConfigKpiRow
      :models-enabled="models.enabledCount.value"
      :models-total="models.chatModels.value.length"
      :default-model-name="defaultModelName"
      :embedding-configured="cfg.embeddingConfigured.value"
      :embedding-model="cfg.form.embedding.model"
      :embedding-dimension="cfg.form.embedding.dimension"
      :settings-modified="cfg.settingsModifiedCount.value"
      :settings-total="cfg.settingsTotalCount.value"
    />

    <el-tabs
      v-model="cfg.activeTab.value"
      class="ai-config-page__tabs"
      @tab-change="cfg.handleTabChange"
    >
      <el-tab-pane label="AI 配置" name="config">
        <el-form v-loading="cfg.loading.value" label-position="top">
          <div class="model-row">
            <AiChatModelTable
              :models="models.chatModels.value"
              :row-testing-id="models.rowTestingId.value"
              :provider-label="providerLabel"
              @create="models.openCreateModel"
              @edit="models.openEditModel"
              @test="models.handleRowTest"
              @set-default="models.handleSetDefault"
              @toggle-enabled="models.handleToggleEnabled"
              @delete="models.handleDeleteModel"
            />

            <AiEmbeddingForm
              v-model="cfg.form.embedding"
              v-model:open="cfg.embeddingOpen.value"
              v-model:rebuild-dialog-visible="cfg.rebuildDialogVisible.value"
              :providers="cfg.embeddingProviderOptions.value"
              :unique-params="cfg.embeddingUniqueParams.value"
              :model-hints="cfg.embeddingModelHints.value"
              :configured="cfg.embeddingConfigured.value"
              :testing="cfg.testing.embedding"
              :saving="cfg.saving.value"
              :rebuild-task="cfg.rebuildTask.value"
              :rebuild-retryable="cfg.rebuildRetryable.value"
              @open-rebuild="cfg.rebuildDialogVisible.value = true"
              @retry-rebuild="cfg.handleRetryRebuild"
              @test="cfg.handleTestEmbedding"
              @save="cfg.handleSaveEmbedding"
            />
          </div>

          <AiSettingsSection
            :groups="cfg.settingsSchema.value"
            :form="cfg.settingsForm"
            @reset="cfg.resetSetting"
          />

          <div class="ai-config-page__footer">
            <span
              v-if="cfg.footerStatusText.value"
              class="ai-config-page__footer-status"
              :class="{ 'is-error': cfg.footerStatusError.value }"
            >
              {{ cfg.footerStatusText.value }}
            </span>
          </div>
        </el-form>
      </el-tab-pane>

      <!-- 不用 lazy：tab 计数徽标依赖面板挂载回传数量，lazy 会使徽标首屏为 0 -->
      <el-tab-pane name="agents">
        <template #label>
          智能体<span class="ai-config-page__tab-count">{{ agentsCount }}</span>
        </template>
        <AiAgentsTab ref="agentsTabRef" />
      </el-tab-pane>

      <el-tab-pane label="调用统计" name="statistics">
        <AiStatisticsTab
          v-model:group-by="cfg.statQuery.groupBy"
          :statistics="cfg.statistics.value"
          @change="cfg.loadStatistics"
        />
      </el-tab-pane>
    </el-tabs>

    <AiModelFormDialog
      v-model:visible="models.modelDialogVisible.value"
      v-model:form="models.modelForm"
      :mode="models.modelDialogMode.value"
      :providers="cfg.chatProviderOptions.value"
      :unique-params="models.modelUniqueParams.value"
      :model-hints="models.modelModelHints.value"
      :saving="cfg.saving.value"
      :testing="models.testing.modelDialog"
      @provider-change="models.handleModelProviderChange"
      @save="models.handleModelSave"
      @test="models.handleModelDialogTest"
    />
  </div>
</template>

<style scoped lang="scss">
.ai-config-page__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.ai-config-page__title {
  margin: 0;
  font-size: var(--font-size-2xl);
  font-weight: 650;
  color: var(--color-neutral-900);
  letter-spacing: -0.01em;
}

.ai-config-page__desc {
  margin: 4px 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

/* 双卡并排等高（demo：460px 按表单展开态实测留余量），超出卡内滚动 */
.model-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--block-gap);
  margin-bottom: var(--block-gap);

  > * {
    height: 460px;
    display: flex;
    flex-direction: column;
    min-height: 0;
    /* grid gap 已承担间距，覆盖组件堆叠期遗留的 margin */
    margin-bottom: 0;
  }

  > * :deep(.el-card__header) {
    flex-shrink: 0;
  }

  > * :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    overflow: auto;
  }
}

.ai-config-page__tabs {
  :deep(.el-tabs__header) {
    margin-bottom: var(--block-gap) !important;
  }

  /* 条目两侧内缩 10px（对齐 demo .ai-tabs），底线仍通栏 */
  :deep(.el-tabs__nav-wrap) {
    padding: 0 10px;
  }

  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background: var(--color-neutral-200);
  }

  :deep(.el-tabs__item) {
    position: relative;
    padding: 12px 14px;
    height: auto;
    line-height: 1.5;
    font-size: var(--font-size-base);
    color: var(--color-neutral-500);

    &:hover:not(.is-active) {
      color: var(--color-neutral-800);
    }

    &.is-active {
      color: var(--color-neutral-900);
      font-weight: 600;
    }
  }

  /* 自绘 2px 品牌下划线（demo .tab--active::after），隐藏 EP 活动条避免双线 */
  :deep(.el-tabs__item.is-active)::after {
    content: '';
    position: absolute;
    left: 14px;
    right: 14px;
    bottom: -1px;
    height: 2px;
    border-radius: 1px;
    background: var(--color-primary-500);
  }

  :deep(.el-tabs__active-bar) {
    display: none;
  }
}

.ai-config-page__tab-count {
  margin-left: 6px;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
  background: var(--color-neutral-100);
  border-radius: 999px;
  padding: 1px 6px;
  font-weight: 500;
}

.ai-config-page__footer {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-md) 0;
}

.ai-config-page__footer-status {
  font-size: 12px;
  color: var(--color-neutral-400);

  &.is-error {
    color: var(--color-danger);
  }
}
</style>
