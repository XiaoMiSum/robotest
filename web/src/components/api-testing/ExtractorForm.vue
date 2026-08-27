<template>
  <div class="extractor-form">
    <el-form-item label="启用" prop="enabled">
      <el-switch v-model="localConfig.enabled" />
    </el-form-item>
    <el-form-item label="提取来源" prop="source">
      <el-select v-model="localConfig.source" placeholder="选择提取来源">
        <el-option label="响应体" value="body" />
        <el-option label="响应头" value="header" />
        <el-option label="状态码" value="status" />
      </el-select>
    </el-form-item>
    <el-form-item label="表达式" prop="expression">
      <el-input v-model="localConfig.expression" placeholder="如: $.data.token 或 Content-Type" />
    </el-form-item>
    <el-form-item label="目标变量名" prop="variableName">
      <el-input v-model="localConfig.variableName" placeholder="提取结果将存入此变量" />
    </el-form-item>
    <el-form-item label="提取描述" prop="description">
      <el-input v-model="localConfig.description" placeholder="描述此提取器的目的" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

interface ExtractorConfig {
  enabled: boolean
  source: string
  expression: string
  variableName: string
  description: string
}

const props = defineProps<{
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
}>()

const defaultConfig: ExtractorConfig = {
  enabled: true,
  source: '',
  expression: '',
  variableName: '',
  description: '',
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
