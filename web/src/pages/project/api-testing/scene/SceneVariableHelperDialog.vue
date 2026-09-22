<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { ApiVariable } from '@/types'

defineProps<{
  modelValue: boolean
  environmentVariables: ApiVariable[]
  sceneVariables: { name: string; value: string; description: string }[]
  environmentName: string
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

// 复制 `${var_name}` 引用形式，便于直接粘贴到表达式输入框
async function copyReference(name: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(`\${${name}}`)
    ElMessage.success('已复制变量引用到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="变量助手"
    width="680px"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="var-helper">
      <!-- 环境变量 -->
      <div class="var-helper__section">
        <div class="var-helper__head">
          <span class="var-helper__title">环境变量（{{ environmentName || '未选择环境' }}）</span>
          <el-tag v-if="!environmentName" size="small" type="warning" effect="light">展示默认环境变量</el-tag>
        </div>
        <el-empty v-if="environmentVariables.length === 0" description="该环境暂未配置变量" :image-size="50" />
        <div
          v-for="v in environmentVariables"
          :key="v.id"
          class="var-helper__row"
        >
          <span class="var-helper__name">{{ v.name }}</span>
          <span class="var-helper__value" :title="v.value">{{ v.value ?? '' }}</span>
          <el-button link size="small" @click="copyReference(v.name)">复制</el-button>
        </div>
      </div>

      <!-- 场景变量 -->
      <div class="var-helper__section">
        <div class="var-helper__head">
          <span class="var-helper__title">场景变量（含未保存）</span>
          <el-tag size="small" type="info" effect="light">{{ sceneVariables.length }} 个</el-tag>
        </div>
        <el-empty v-if="sceneVariables.length === 0" description="场景暂未定义变量" :image-size="50" />
        <div
          v-for="(v, i) in sceneVariables"
          :key="i"
          class="var-helper__row"
        >
          <span class="var-helper__name">{{ v.name }}</span>
          <span class="var-helper__value" :title="v.value">{{ v.value }}</span>
          <el-button link size="small" @click="copyReference(v.name)">复制</el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped lang="scss">
.var-helper {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.var-helper__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.var-helper__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.var-helper__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.var-helper__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 6px 8px;
  border: 1px solid var(--color-neutral-100, #f3f4f6);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);

  &:hover { background: var(--color-neutral-50, #f9fafb); }
}

.var-helper__name {
  width: 30%;
  flex-shrink: 0;
  font-family: monospace;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.var-helper__value {
  flex: 1;
  min-width: 0;
  color: var(--color-neutral-500);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
