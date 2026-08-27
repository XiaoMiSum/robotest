<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  data: { mockUrl: string; method: string; name: string; headers?: Record<string, unknown> } | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = ref(props.modelValue)

watch(
  () => props.modelValue,
  (val) => { visible.value = val },
)

watch(visible, (val) => emit('update:modelValue', val))

async function handleCopy() {
  if (!props.data?.mockUrl) return
  try {
    await navigator.clipboard.writeText(props.data.mockUrl)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="Mock 访问地址"
    width="520px"
    :close-on-click-modal="false"
  >
    <div v-if="data" class="mock-address">
      <p class="mock-address__desc">
        以下地址可免登录直接访问 Mock 响应：
      </p>
      <div class="mock-address__url-box">
        <el-tag size="small" :type="data.method === 'GET' ? 'success' : 'primary'" disable-transitions>
          {{ data.method }}
        </el-tag>
        <code class="mock-address__url">{{ data.mockUrl }}</code>
      </div>
      <div v-if="data.headers && Object.keys(data.headers).length" class="mock-address__headers">
        <p class="mock-address__headers-title">需要携带的请求头：</p>
        <div v-for="(val, key) in data.headers" :key="String(key)" class="mock-address__header-row">
          <code class="mock-address__header-key">{{ key }}</code>
          <code class="mock-address__header-val">{{ val }}</code>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" @click="handleCopy">复制地址</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.mock-address__desc {
  font-size: 13px;
  color: var(--color-neutral-500);
  margin: 0 0 12px;
}

.mock-address__url-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--color-neutral-50, #f5f5f5);
  border: 1px solid var(--color-neutral-200, #e5e5e5);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}

.mock-address__url {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  word-break: break-all;
  flex: 1;
}

.mock-address__headers {
  margin-top: 12px;
}

.mock-address__headers-title {
  font-size: 13px;
  color: var(--color-neutral-500);
  margin: 0 0 8px;
}

.mock-address__header-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.mock-address__header-key {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  background: var(--color-neutral-100, #eee);
  padding: 2px 6px;
  border-radius: var(--radius-sm, 3px);
}

.mock-address__header-val {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: var(--color-neutral-500);
}
</style>
