<script setup lang="ts">
import { computed } from 'vue'
import type { ApiComponentListItem } from '@/types'
import { formatDateTime } from '@/utils/format'
import {
  SCOPE_TAG_TYPE,
  buildComponentConfigRows,
  componentScopeLabel,
  componentTypeLabel,
} from '@/composables/project/api-testing/component/componentModel'
import { parseComponentConfig } from '@/composables/project/api-testing/processorFormModel'
import ProcessorConfigDetail from '@/components/project/api-testing/ProcessorConfigDetail.vue'

const props = defineProps<{
  item: ApiComponentListItem
  canEdit: boolean
}>()

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'copy'): void
  (e: 'delete'): void
  (e: 'toggle', value: boolean): void
}>()

const isProcessor = computed(() => props.item.type === 'preprocessor' || props.item.type === 'postprocessor')
// 处理器配置与提取器由共享明细组件渲染，验证器/提取器仍走行摘要
const configRows = computed(() => buildComponentConfigRows(props.item.type, props.item.config))
const processorElement = computed(() => parseComponentConfig(props.item.config))
const updatedAt = computed(() => formatDateTime(props.item.updatedAt))
const description = computed(() => props.item.description || '—')

function onToggle(value: string | number | boolean) {
  emit('toggle', value === true)
}
</script>

<template>
  <div class="cp-detail__header">
    <div class="cp-detail__id">
      <h4 class="cp-detail__name">{{ item.name }}</h4>
      <el-tag size="small" effect="plain">{{ componentTypeLabel(item.type) }}</el-tag>
      <el-tag size="small" :type="SCOPE_TAG_TYPE[item.scope]">{{ componentScopeLabel(item.scope) }}</el-tag>
      <el-checkbox v-if="canEdit" :model-value="item.enabled" @change="onToggle">启用</el-checkbox>
    </div>
    <div class="cp-detail__header-actions">
      <el-button v-if="canEdit" size="small" @click="emit('edit')">编辑</el-button>
      <el-button size="small" @click="emit('copy')">复制</el-button>
      <el-button v-if="canEdit" size="small" type="danger" @click="emit('delete')">删除</el-button>
    </div>
  </div>

  <div class="cp-detail__section">
    <label class="cp-detail__label">基本信息</label>
    <div class="cp-meta">
      <div class="cp-meta__item">
        <span class="cp-meta__k">更新时间</span>
        <span class="cp-meta__v">{{ updatedAt }}</span>
      </div>
      <div class="cp-meta__item cp-meta__item--wide">
        <span class="cp-meta__k">描述</span>
        <span class="cp-meta__v">{{ description }}</span>
      </div>
    </div>
  </div>

  <div class="cp-detail__section">
    <label class="cp-detail__label">配置</label>
    <ProcessorConfigDetail v-if="isProcessor" :element="processorElement" />
    <div v-else class="cp-meta">
      <div
        v-for="row in configRows"
        :key="row.label"
        class="cp-meta__item"
        :class="{ 'cp-meta__item--wide': row.kind !== 'text' }"
      >
        <span class="cp-meta__k">{{ row.label }}</span>
        <code v-if="row.kind === 'code'" class="cp-code">{{ row.value }}</code>
        <div v-else-if="row.kind === 'kv'" class="cp-meta__v">
          <div v-for="pair in row.pairs" :key="pair.key" class="cp-kv">
            <code class="cp-kv__k">{{ pair.key }}</code>
            <span class="cp-kv__v">{{ pair.value }}</span>
          </div>
        </div>
        <span v-else class="cp-meta__v">{{ row.value }}</span>
      </div>
    </div>
  </div>
</template>
