<script setup lang="ts">
import RequirementSelector from '@/components/project/RequirementSelector.vue'
import { useMissingPointsPanel } from '@/composables/project/functional-testing/case/useMissingPointsPanel'
import { MagicStick, Close } from '@element-plus/icons-vue'

const props = defineProps<{ docId: string }>()
const visible = defineModel<boolean>({ required: true })

const {
  keywords,
  text,
  requirementIds,
  requirementTitles,
  reqSelectorVisible,
  analyzing,
  result,
  checkedIndexes,
  hasAnyInput,
  allChecked,
  toggleAll,
  toggleItem,
  handleRequirementConfirm,
  removeRequirement,
  analyze,
  cancelAnalyze,
  documentOptions,
  docSelectVisible,
  targetDocId,
  openTargetSelect,
  toCaseGenerate,
} = useMissingPointsPanel(() => props.docId, visible)
</script>

<template>
  <transition name="mp-slide">
    <aside v-show="visible" class="mp-drawer" role="dialog" aria-label="遗漏测试点分析">
      <header class="mp-drawer__header">
        <span class="mp-title"><el-icon><MagicStick /></el-icon> 遗漏测试点分析</span>
        <el-button link @click="visible = false"><el-icon><Close /></el-icon></el-button>
      </header>

      <div class="mp-drawer__body">
        <div class="mp">
      <div class="mp-inputs">
        <div class="mp-field">
          <div class="mp-field__label">关键词</div>
          <el-select
            v-model="keywords"
            multiple
            filterable
            allow-create
            default-first-option
            :disabled="analyzing"
            placeholder="输入关键词，回车确认"
          >
            <el-option v-for="k in keywords" :key="k" :label="k" :value="k" />
          </el-select>
        </div>

        <div class="mp-field">
          <div class="mp-field__label">需求文本</div>
          <el-input
            v-model="text"
            type="textarea"
            :rows="5"
            maxlength="20000"
            :disabled="analyzing"
            placeholder="粘贴需求描述文本（可空；填写时后端先抽取关键词再分析）"
          />
        </div>

        <div class="mp-field">
          <div class="mp-field__bar">
            <span class="mp-field__label">需求池</span>
            <el-button size="small" :disabled="analyzing" @click="reqSelectorVisible = true">
              + 选择需求
            </el-button>
          </div>
          <div v-if="requirementTitles.length" class="mp-req-tags">
            <el-tag
              v-for="item in requirementTitles"
              :key="item.id"
              size="small"
              closable
              @close="removeRequirement(item.id)"
            >
              {{ item.title }}
            </el-tag>
          </div>
          <span v-else class="mp-field__hint">未选择需求，将仅依据输入文本生成</span>
        </div>
      </div>

      <div class="mp-actions">
        <div v-if="analyzing" class="mp-actions__progress">
          <el-progress
            :percentage="100"
            :indeterminate="true"
            :duration="2"
            :stroke-width="4"
            :show-text="false"
          />
        </div>
        <el-button v-if="analyzing" @click="cancelAnalyze">取消</el-button>
        <el-button type="primary" :loading="analyzing" :disabled="!hasAnyInput" @click="analyze">
          {{ result ? '重新分析' : '开始分析' }}
        </el-button>
      </div>

      <el-alert
        v-if="result && result.semanticDegraded"
        type="warning"
        :closable="false"
        show-icon
        title="当前为关键词匹配结果"
      />

      <template v-if="result">
        <div class="mp-result-head">
          <el-checkbox
            :model-value="allChecked"
            :indeterminate="checkedIndexes.size > 0 && !allChecked"
            @update:model-value="(v) => toggleAll(v === true)"
          >全选</el-checkbox>
          <span class="mp-result-count">共 {{ result.points.length }} 条，已选 {{ checkedIndexes.size }} 条</span>
        </div>

        <div v-if="result.points.length" class="mp-list">
          <div v-for="(point, index) in result.points" :key="index" class="mp-item">
            <el-checkbox
              :model-value="checkedIndexes.has(index)"
              @update:model-value="(v) => toggleItem(index, v === true)"
            />
            <div class="mp-item__body">
              <div class="mp-item__title">{{ point.title }}</div>
              <div class="mp-item__desc">{{ point.description }}</div>
              <div v-if="point.suggestedModulePath" class="mp-item__tags">
                <el-tag size="small" effect="plain" type="info">
                  建议模块：{{ point.suggestedModulePath }}
                </el-tag>
              </div>
              <div v-if="point.relatedCaseTitles.length" class="mp-item__related">
                <span class="mp-item__related-label">相关用例：</span>
                <el-tag
                  v-for="title in point.relatedCaseTitles"
                  :key="title"
                  size="small"
                  effect="plain"
                  class="mp-item__related-tag"
                >
                  {{ title }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="未发现遗漏测试点" :image-size="72" />
      </template>

      <div v-if="result && result.points.length" class="mp-footer">
        <el-button type="primary" :disabled="!checkedIndexes.size" @click="openTargetSelect">
          转用例生成（{{ checkedIndexes.size }}）
        </el-button>
      </div>
        </div>
      </div>
    </aside>
  </transition>

  <RequirementSelector
    v-model="reqSelectorVisible"
    :selected-ids="requirementIds"
    @confirm="handleRequirementConfirm"
  />

  <el-dialog v-model="docSelectVisible" title="选择目标文档" width="440px" append-to-body>
    <div class="mp-doc-tip">默认已预选出现次数最多的建议模块，可更换</div>
    <el-select v-model="targetDocId" filterable placeholder="搜索文档路径" class="mp-doc-select">
      <el-option v-for="doc in documentOptions" :key="doc.id" :label="doc.path" :value="doc.id" />
    </el-select>
    <template #footer>
      <el-button @click="docSelectVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!targetDocId" @click="toCaseGenerate">生成用例</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.mp-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 2001;
  display: flex;
  flex-direction: column;
  width: 640px;
  max-width: 90vw;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
}

.mp-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.mp-drawer__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 20px;
}

.mp-slide-enter-active,
.mp-slide-leave-active {
  transition: transform 0.3s ease;
}

.mp-slide-enter-from,
.mp-slide-leave-to {
  transform: translateX(100%);
}

.mp-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.mp {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mp-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mp-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mp-field__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.mp-field__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mp-field__hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.mp-req-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mp-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.mp-actions__progress {
  flex: 1;
  min-width: 0;
}

.mp-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mp-result-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mp-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
}

.mp-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.mp-item__body {
  flex: 1;
  min-width: 0;
}

.mp-item__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mp-item__desc {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
  white-space: pre-wrap;
}

.mp-item__tags {
  margin-top: 6px;
}

.mp-item__related {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mp-item__related-label {
  flex-shrink: 0;
}

.mp-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.mp-doc-tip {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mp-doc-select {
  width: 100%;
}
</style>
