<template>
  <div class="validator-form">
    <el-form-item class="validator-form__field validator-form__field--target" label="验证目标" prop="target">
      <el-select v-model="localConfig.target" placeholder="选择验证目标">
        <el-option v-for="t in VALIDATOR_TARGETS" :key="t.value" :value="t.value" :label="t.label" />
      </el-select>
    </el-form-item>
    <el-form-item class="validator-form__field validator-form__field--condition" prop="condition">
      <el-select v-model="localConfig.condition" placeholder="选择比较条件">
        <el-option v-for="c in VALIDATOR_CONDITIONS" :key="c.value" :value="c.value" :label="c.label" />
      </el-select>
    </el-form-item>
    <el-form-item class="validator-form__field validator-form__field--expression" prop="expression">
      <el-input v-model="localConfig.expression" placeholder="表达式（如 $.code）" />
    </el-form-item>
    <el-form-item class="validator-form__field validator-form__field--expected" prop="expected">
      <el-input v-model="localConfig.expected" placeholder="期望值" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { VALIDATOR_TARGETS, VALIDATOR_CONDITIONS } from '@/pages/project/scenesModel'

interface ValidatorConfig {
  target: string
  expression: string
  condition: string
  expected: string
}

const props = defineProps<{
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
}>()

const defaultConfig: ValidatorConfig = {
  target: 'status_code',
  expression: '',
  condition: 'equals',
  expected: '',
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

<style scoped>
.validator-form {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: var(--space-sm);
}

.validator-form__field {
  margin-right: 0;
  margin-bottom: 0;
  flex-shrink: 1;
}

.validator-form__field--target {
  flex: 0 0 260px;
}

.validator-form__field--condition {
  flex: 0 0 150px;
}

.validator-form__field--expression,
.validator-form__field--expected {
  flex: 1 1 0;
}

.validator-form :deep(.el-form-item__content),
.validator-form :deep(.el-form-item__content) .el-select {
  width: 100%;
}
</style>
