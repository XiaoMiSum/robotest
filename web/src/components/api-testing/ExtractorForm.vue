<template>
  <div class="extractor-form">
    <el-form-item class="extractor-form__field extractor-form__field--source" label="提取来源" prop="source">
      <el-select v-model="localConfig.source" placeholder="选择提取来源">
        <el-option v-for="s in EXTRACTOR_SOURCES" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
    </el-form-item>
    <el-form-item class="extractor-form__field extractor-form__field--expression" label="表达式" prop="expression">
      <el-input v-model="localConfig.expression" placeholder="表达式" />
    </el-form-item>
    <el-form-item class="extractor-form__field extractor-form__field--variable" label="变量名" prop="variableName">
      <el-input v-model="localConfig.variableName" placeholder="变量名" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { EXTRACTOR_SOURCES } from '@/pages/project/scenesModel'

interface ExtractorConfig {
  source: string
  expression: string
  variableName: string
}

const props = defineProps<{
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
}>()

const defaultConfig: ExtractorConfig = {
  source: 'json_field',
  expression: '',
  variableName: '',
}

const localConfig = reactive<ExtractorConfig>({
  ...defaultConfig,
  ...props.modelValue as Partial<ExtractorConfig>,
})

watch(() => props.modelValue, (val) => {
  Object.assign(localConfig, { ...defaultConfig, ...val as Partial<ExtractorConfig> })
}, { deep: true })

watch(localConfig, (val) => {
  emit('update:modelValue', { ...val })
}, { deep: true })
</script>

<style scoped>
.extractor-form {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
  flex-wrap: nowrap;
  gap: var(--space-sm);
}

.extractor-form__field {
  margin-right: 0;
  margin-bottom: 0;
  flex-shrink: 1;
}

/* 提取来源下拉收窄并与表达式/变量名对齐到统一列宽 */
.extractor-form__field--source {
  flex: 0 0 150px;
}

.extractor-form__field--expression,
.extractor-form__field--variable {
  flex: 1 1 0;
  min-width: 0;
}

/* 无论外层容器 label 朝向（含非 el-form 容器），强制标签置顶，保证控件同一水平线对齐 */
.extractor-form :deep(.el-form-item__label) {
  display: block;
  width: 100%;
  height: auto;
  padding: 0;
  margin-bottom: var(--space-xs);
  text-align: left;
  line-height: 1.4;
}

.extractor-form :deep(.el-form-item__content),
.extractor-form :deep(.el-form-item__content) .el-select {
  width: 100%;
}
</style>
