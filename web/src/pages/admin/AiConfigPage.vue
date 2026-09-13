<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useAiConfigPage } from '@/composables/useAiConfigPage'
import { useAiChatModels } from '@/composables/useAiChatModels'
import AiAgentsTab from '@/components/admin/AiAgentsTab.vue'
import AiMasterSwitchCard from '@/components/admin/AiMasterSwitchCard.vue'
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

onMounted(async () => {
  await cfg.loadAll()
  await models.refresh()
})
</script>

<template>
  <div class="ai-config-page">
    <el-tabs v-model="cfg.activeTab.value" @tab-change="cfg.handleTabChange">
      <el-tab-pane label="AI 配置" name="config">
        <el-form v-loading="cfg.loading.value" label-width="120px">
          <AiMasterSwitchCard
            v-model="cfg.form.enabled"
            :loading="cfg.loading.value"
            @before-change="cfg.handleMasterBeforeChange"
          />

          <AiChatModelTable
            :models="models.chatModels.value"
            :row-testing-id="models.rowTestingId.value"
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
            :providers="cfg.embeddingProviderOptions.value"
            :unique-params="cfg.embeddingUniqueParams.value"
            :model-hints="cfg.embeddingModelHints.value"
            :configured="cfg.embeddingConfigured.value"
            :testing="cfg.testing.embedding"
            :saving="cfg.saving.value"
            @test="cfg.handleTestEmbedding"
            @save="cfg.handleSaveEmbedding"
          />

          <AiSettingsSection
            :groups="cfg.settingsSchema.value"
            :form="cfg.settingsForm"
            @reset="cfg.resetSetting"
          />

          <el-alert
            v-if="cfg.rebuildTask.value"
            class="ai-config-page__rebuild"
            :type="cfg.rebuildRetryable.value ? 'error' : 'info'"
            :closable="false"
          >
            向量重建任务状态：{{ cfg.rebuildTask.value.status }}（进度 {{ cfg.rebuildTask.value.progress }}%）
            <span v-if="cfg.rebuildTask.value.errorMessage">，原因：{{ cfg.rebuildTask.value.errorMessage }}</span>
            <el-button v-if="cfg.rebuildRetryable.value" size="small" type="primary" link @click="cfg.handleRetryRebuild">
              重试
            </el-button>
          </el-alert>

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

      <el-tab-pane label="智能体" name="agents" lazy>
        <AiAgentsTab />
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
.ai-config-page__rebuild {
  margin-bottom: var(--space-lg);
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