<template>
  <div class="validator-form">
    <el-form-item label="启用" prop="enabled">
      <el-switch v-model="localConfig.enabled" />
    </el-form-item>
    <el-form-item label="验证目标" prop="target">
      <el-select v-model="localConfig.target" placeholder="选择验证目标">
        <el-option label="状态码" value="status" />
        <el-option label="响应体" value="body" />
        <el-option label="响应头" value="header" />
        <el-option label="变量" value="variable" />
      </el-select>
    </el-form-item>
    <el-form-item label="表达式" prop="expression">
      <el-input v-model="localConfig.expression" placeholder="如: $.code 或 status 或 $.data.id" />
    </el-form-item>
    <el-form-item label="比较条件" prop="operator">
      <el-select v-model="localConfig.operator" placeholder="选择比较条件">
        <el-option label="等于" value="eq" />
        <el-option label="不等于" value="neq" />
        <el-option label="大于" value="gt" />
        <el-option label="小于" value="lt" />
        <el-option label="大于等于" value="gte" />
        <el-option label="小于等于" value="lte" />
        <el-option label="包含" value="contains" />
        <el-option label="不包含" value="not_contains" />
        <el-option label="正则匹配" value="matches" />
        <el-option label="为空" value="empty" />
        <el-option label="不为空" value="not_empty" />
      </el-select>
    </el-form-item>
    <el-form-item label="期望值" prop="expected">
      <el-input v-model="localConfig.expected" placeholder="期望值" />
    </el-form-item>
    <el-form-item label="断言描述" prop="description">
      <el-input v-model="localConfig.description" placeholder="描述此断言的目的" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

interface ValidatorConfig {
  enabled: boolean
  target: string
  expression: string
  operator: string
  expected: string
  description: string
}

const props = defineProps<{
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
}>()

const defaultConfig: ValidatorConfig = {
  enabled: true,
  target: '',
  expression: '',
  operator: 'eq',
  expected: '',
  description: '',
}

const localConfig = reactive<ValidatorConfig>({
  ...defaultConfig,
  ...props.modelValue as Partial<ValidatorConfig>,
})

watch(() => props.modelValue, (val) => {
  Object.assign(localConfig, { ...defaultConfig, ...val as Partial<ValidatorConfig> })
}, { deep: true })

watch(localConfig, (val) => {
  emit('update:modelValue', { ...val })
}, { deep: true })
</script>
