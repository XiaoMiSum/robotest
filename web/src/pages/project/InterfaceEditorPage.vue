<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from 'vue'
import KeyValueTable from './debug/KeyValueTable.vue'
import ValidatorsExtractorsPanes from '@/components/api-testing/ValidatorsExtractorsPanes.vue'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'
import InterfaceEditorAuth from './InterfaceEditorAuth.vue'
import InterfaceEditorBody from './InterfaceEditorBody.vue'
import InterfaceEditorResponse from './InterfaceEditorResponse.vue'
import { useInterfaceEditor } from '@/composables/project/api-testing/interface/useInterfaceEditor'

const props = defineProps<{ interfaceId?: string; createMode?: boolean; moduleId?: string }>()
const emit = defineEmits<{
  (e: 'back'): void
  (e: 'title-update', name: string): void
  (e: 'dirty-change', dirty: boolean): void
}>()

const {
  form,
  loading,
  saving,
  activeTab,
  moduleOptions,
  handlePathBlur,
  addValidator,
  addExtractor,
  paneValidators,
  paneExtractors,
  handleValidatorsUpdate,
  handleExtractorsUpdate,
  assetPickerVisible,
  assetPickerLoading,
  assetPickerItems,
  assetPickerKeyword,
  assetPickerKind,
  openAssetPicker,
  loadAssetPicker,
  handleAssetPicked,
  save,
  containerRef,
  requestHeight,
  onDividerMouseDown,
  METHOD_OPTIONS,
  PROTOCOL_OPTIONS,
  ASSET_TITLE,
  mount,
  unmount,
} = useInterfaceEditor(props, emit)

const activeHeaderCount = computed(() => form.value.headers.filter((h) => h.key.trim() !== '' && h.enabled).length)
const activeParamCount = computed(() => form.value.params.filter((p) => p.key.trim() !== '' && p.enabled).length)

onMounted(mount)
onBeforeUnmount(unmount)
</script>

<template>
  <div v-loading="loading" class="interface-editor">
    <el-card shadow="never" class="interface-editor__card">
      <div class="interface-editor__request-line">
        <el-select v-model="form.protocol" style="width: 96px" data-test="editor-protocol-select">
          <el-option v-for="opt in PROTOCOL_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
        </el-select>
        <el-select v-model="form.method" style="width: 130px" data-test="editor-method-select">
          <el-option v-for="method in METHOD_OPTIONS" :key="method" :value="method" :label="method" />
        </el-select>
        <el-input
          v-model="form.path"
          placeholder="/api/resource"
          style="flex: 1"
          data-test="editor-path-input"
          @blur="handlePathBlur"
        />
        <el-input v-model="form.name" placeholder="接口名称" style="width: 240px" data-test="editor-name-input" />
        <el-button
          type="primary"
          :loading="saving"
          data-test="editor-save-btn"
          class="interface-editor__save"
          @click="save"
        >
          保存
        </el-button>
      </div>

      <div ref="containerRef" class="interface-editor__split">
        <div class="interface-editor__request" :style="{ '--req-h': requestHeight + '%' }">
          <el-tabs v-model="activeTab" class="interface-editor__tabs">
            <el-tab-pane name="basic" label="基本信息">
              <div class="interface-editor__field-row">
                <span class="interface-editor__field-label">所属模块</span>
                <el-cascader
                  v-model="form.moduleId"
                  :options="moduleOptions"
                  :props="{ checkStrictly: true, emitPath: false, value: 'value', label: 'label' }"
                  placeholder="选择所属模块"
                  clearable
                  style="width: 100%"
                  data-test="editor-module-cascader"
                />
              </div>
              <div class="interface-editor__field-row interface-editor__field-row--top">
                <span class="interface-editor__field-label">描述</span>
                <el-input v-model="form.description" type="textarea" :rows="3" data-test="editor-description-input" />
              </div>
            </el-tab-pane>

            <el-tab-pane name="headers">
              <template #label>
                <span class="interface-editor__tab-label">
                  请求头
                  <span v-if="activeHeaderCount" class="interface-editor__badge">{{ activeHeaderCount }}</span>
                </span>
              </template>
              <KeyValueTable v-model:entries="form.headers" placeholder-key="Header" />
            </el-tab-pane>

            <el-tab-pane name="query">
              <template #label>
                <span class="interface-editor__tab-label">
                  Query 参数
                  <span v-if="activeParamCount" class="interface-editor__badge">{{ activeParamCount }}</span>
                </span>
              </template>
              <KeyValueTable v-model:entries="form.params" placeholder-key="参数名" />
            </el-tab-pane>

            <el-tab-pane name="body">
              <template #label>
                <span class="interface-editor__tab-label">
                  请求体
                  <span v-if="form.bodyType !== 'none'" class="interface-editor__badge">1</span>
                </span>
              </template>
              <InterfaceEditorBody v-model="form" />
            </el-tab-pane>

            <el-tab-pane name="auth" label="认证">
              <InterfaceEditorAuth :form="form" />
            </el-tab-pane>

            <ValidatorsExtractorsPanes
              :validators="paneValidators"
              :extractors="paneExtractors"
              @update:validators="handleValidatorsUpdate"
              @update:extractors="handleExtractorsUpdate"
              @add-validator="addValidator"
              @add-extractor="addExtractor"
              @import-validators="openAssetPicker('validator')"
              @import-extractors="openAssetPicker('extractor')"
            />
          </el-tabs>
        </div>

        <div class="interface-editor__divider" @mousedown="onDividerMouseDown">
          <div class="interface-editor__divider-line" />
        </div>

        <InterfaceEditorResponse v-model="form" />
      </div>
    </el-card>

    <ExtractorAssetPicker
      v-model="assetPickerVisible"
      :loading="assetPickerLoading"
      :items="assetPickerItems"
      :keyword="assetPickerKeyword"
      :title="ASSET_TITLE[assetPickerKind]"
      tip="仅展示启用的组件资产；引入为复制，得到独立副本，与源资产无关联。"
      empty-text="暂无可用组件"
      search-placeholder="搜索组件名称..."
      @update:keyword="assetPickerKeyword = $event"
      @search="loadAssetPicker"
      @confirm="handleAssetPicked"
    />
  </div>
</template>

<style scoped lang="scss">
.interface-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.interface-editor__request-line {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.interface-editor__save {
  flex-shrink: 0;
}

.interface-editor__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.interface-editor__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 11px;
  line-height: 1;
}

.interface-editor__tabs {
  :deep(.el-tabs__content) {
    padding-top: var(--space-sm);
  }
}

.interface-editor__field-row {
  display: flex;
  gap: var(--space-md);
  align-items: center;

  &--top {
    align-items: flex-start;
  }

  & + & {
    margin-top: var(--space-md);
  }
}

.interface-editor__field-label {
  width: 80px;
  flex-shrink: 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  line-height: 32px;
}

.interface-editor__card {
  display: flex;
  flex-direction: column;
  height: 100%;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
    padding: var(--space-md);
    gap: var(--space-md);
  }
}

.interface-editor__split {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.interface-editor__request {
  height: var(--req-h, 50%);
  min-height: 80px;
  overflow: auto;
  flex-shrink: 0;
}

.interface-editor__divider {
  height: 6px;
  cursor: row-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  z-index: 1;

  &:hover .interface-editor__divider-line,
  &:active .interface-editor__divider-line {
    background: var(--color-primary-300, #a0cfff);
  }
}

.interface-editor__divider-line {
  width: 75%;
  height: 2px;
  border-radius: 1px;
  background: var(--color-neutral-200, #dcdfe6);
  transition: background 0.15s;
}
</style>
