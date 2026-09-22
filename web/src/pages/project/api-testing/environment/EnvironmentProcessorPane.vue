<script setup lang="ts">
import type { ApiProcessor, ApiProcessorType } from '@/types'
import type { HttpConfigForm, DsForm } from '@/composables/project/api-testing/environment/useEnvironmentConfig'
import ProcessorForm from '@/components/project/api-testing/ProcessorForm.vue'

defineProps<{
  processorType: ApiProcessorType
  title: string
  emptyDescription: string
  count: number
  canEdit: boolean
  processors: ApiProcessor[]
  activeProcId: string
  selectedProcessor: ApiProcessor | null
  procTestclass: string
  procHttpRef: string
  procDsRef: string
  procHttpRefOptions: { value: string; label: string }[]
  procDsRefOptions: { value: string; label: string }[]
  configForms: HttpConfigForm[]
  dsForms: DsForm[]
  procTags: (processor: ApiProcessor) => { text: string; type: string }[]
  procDisplayName: (processor: ApiProcessor, index: number) => string
}>()

const emit = defineEmits<{
  (e: 'select', processor: ApiProcessor): void
  (e: 'add'): void
  (e: 'remove', processor: ApiProcessor): void
  (e: 'move', index: number, direction: -1 | 1): void
  (e: 'copy', processor: ApiProcessor): void
  (e: 'import'): void
  (e: 'import-extractors'): void
  (e: 'update:procTestclass', value: string): void
  (e: 'update:procHttpRef', value: string): void
  (e: 'update:procDsRef', value: string): void
}>()
</script>

<template>
  <div class="env-proc-pane">
    <div class="env-proc-pane__left">
      <div class="env-proc-pane__head">
        <span>{{ title }}</span>
        <div class="env-proc-pane__actions">
          <el-button link type="primary" size="small" :disabled="!canEdit" @click="emit('import')">从公共组件引入</el-button>
          <el-button link type="primary" size="small" :disabled="!canEdit" @click="emit('add')">+ 添加处理器</el-button>
        </div>
      </div>
      <template v-for="(processor, i) in processors" :key="processor.id">
        <div
          class="env-proc-pane__item"
          :class="{ 'is-selected': processor.id === activeProcId, 'is-disabled': !processor.enabled }"
          @click="emit('select', processor)"
        >
          <div class="env-proc-pane__item-header">
            <span class="env-proc-pane__index">{{ i + 1 }}</span>
            <el-tag v-for="t in procTags(processor)" :key="t.text" size="small" :type="t.type as 'success' | 'primary' | 'warning' | 'info' | 'danger'">{{ t.text }}</el-tag>
            <div class="env-proc-pane__header-spacer" />
            <el-switch v-model="processor.enabled" size="small" :disabled="!canEdit" @click.stop />
            <el-dropdown v-if="canEdit" trigger="click" @click.stop>
              <el-button link size="small">操作</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="emit('select', processor)">编辑</el-dropdown-item>
                  <el-dropdown-item :disabled="i === 0" @click="emit('move', i, -1)">上移</el-dropdown-item>
                  <el-dropdown-item :disabled="i === count - 1" @click="emit('move', i, 1)">下移</el-dropdown-item>
                  <el-dropdown-item divided @click="emit('copy', processor)">复制</el-dropdown-item>
                  <el-dropdown-item divided style="color: var(--el-color-danger)" @click="emit('remove', processor)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="env-proc-pane__item-name">{{ procDisplayName(processor, i) }}</div>
        </div>
      </template>
      <el-empty v-if="count === 0" :description="emptyDescription" :image-size="60" />
      <el-button v-if="count === 0 && canEdit" size="small" class="env-proc-pane__add" @click="emit('add')">
        <el-icon><Plus /></el-icon> 添加处理器
      </el-button>
    </div>

    <div class="env-proc-pane__right">
      <template v-if="selectedProcessor">
        <div class="env-proc-pane__inline">
          <header class="env-proc-pane__inline-head">
            <el-input v-model="selectedProcessor.name" placeholder="处理器名称" class="env-proc-pane__inline-name" :disabled="!canEdit" />
            <el-switch v-model="selectedProcessor.enabled" :disabled="!canEdit" active-text="启用" />
            <el-divider direction="vertical" />
            <el-radio-group :model-value="procTestclass" :disabled="!canEdit" size="small" @update:model-value="emit('update:procTestclass', $event as string)">
              <el-radio-button value="http">HTTP</el-radio-button>
              <el-radio-button value="jdbc">JDBC</el-radio-button>
            </el-radio-group>
            <el-select
              v-if="procTestclass === 'http'"
              :model-value="procHttpRef"
              placeholder="选择环境 HTTP 配置"
              filterable
              :disabled="!canEdit"
              class="env-proc-pane__inline-ref"
              @update:model-value="emit('update:procHttpRef', $event as string)"
            >
              <el-option v-for="opt in procHttpRefOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
            </el-select>
            <el-select
              v-else-if="procTestclass === 'jdbc'"
              :model-value="procDsRef"
              placeholder="选择环境数据源"
              filterable
              :disabled="!canEdit"
              class="env-proc-pane__inline-ref"
              @update:model-value="emit('update:procDsRef', $event as string)"
            >
              <el-option v-for="opt in procDsRefOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
            </el-select>
          </header>
          <div class="env-proc-pane__inline-body" :class="{ 'is-readonly': !canEdit }">
            <ProcessorForm
              v-model="selectedProcessor.config"
              :http-options="configForms"
              :ds-options="dsForms"
              :show-type-select="false"
              :show-ref-select="false"
              @import-extractors="emit('import-extractors')"
            />
          </div>
        </div>
      </template>
      <div v-else class="env-proc-pane__right-empty">
        <p>选中左侧处理器后在右侧编辑</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.env-proc-pane {
  display: flex;
  align-items: flex-start;
  gap: var(--space-lg);
}

.env-proc-pane__left {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.env-proc-pane__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
  font-weight: 600;
}

.env-proc-pane__actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.env-proc-pane__item {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: 88px;
  padding: var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  cursor: pointer;
  margin: 2px 0;
  overflow: hidden;

  &:hover {
    border-color: var(--color-primary-300);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }

  &.is-selected {
    border-color: var(--color-primary-400);
    background: var(--color-primary-50, #eff6ff);
    box-shadow: 0 0 0 1px var(--color-primary-300);
  }

  &.is-disabled {
    opacity: 0.5;
  }
}

.env-proc-pane__item-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.env-proc-pane__index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-neutral-100);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-neutral-600);
  flex-shrink: 0;
}

.env-proc-pane__header-spacer {
  flex: 1;
}

.env-proc-pane__item-name {
  padding: var(--space-xs) 0 0 0;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-proc-pane__add {
  border-style: dashed;
  width: 100%;
  margin-top: var(--space-sm);
}

.env-proc-pane__right {
  flex: 1;
  min-width: 0;
}

.env-proc-pane__inline {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.env-proc-pane__inline-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-neutral-100);
  background: var(--color-neutral-50);
  flex-wrap: wrap;
}

.env-proc-pane__inline-name {
  flex: 1;
  min-width: 160px;
  max-width: 320px;
}

.env-proc-pane__inline-ref {
  width: 240px;
  flex-shrink: 0;
}

.env-proc-pane__inline-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  padding: var(--space-lg);

  &::-webkit-scrollbar {
    display: none;
  }

  &.is-readonly {
    pointer-events: none;
    opacity: 0.65;
  }
}

.env-proc-pane__right-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}
</style>
