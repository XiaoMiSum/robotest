<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAiAdminStore } from '@/stores/aiAdmin'
import { isTerminalTaskStatus, useAiTaskStore } from '@/stores/aiTask'

const route = useRoute()
const store = useAiAdminStore()
const aiTaskStore = useAiTaskStore()

/** 左侧分组导航，与子路由一一对应（交互 2.1.4） */
const GROUPS = [
  { label: '模型配置', path: '/admin/ai/models' },
  { label: '向量 API', path: '/admin/ai/embedding' },
  { label: '场景提示词', path: '/admin/ai/prompts' },
  { label: '用量分析', path: '/admin/ai/usage' },
]

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

// ---------- 页头：AI 总开关（交互 2.1.2） ----------

async function handleToggle(next: boolean | string | number) {
  const enabled = next === true // el-switch change 事件载荷类型为宽联合，收敛为布尔
  if (!store.settings) return
  if (!enabled) {
    try {
      await ElMessageBox.confirm('关闭后业务端 AI 入口将隐藏，历史任务与产物保留', '关闭 AI 能力', {
        type: 'warning',
        confirmButtonText: '关闭',
        cancelButtonText: '取消',
      })
    } catch {
      return // 取消关闭：model-value 取自 store，开关自动回位
    }
  }
  try {
    await store.updateSettings({ enabled })
    ElMessage.success(enabled ? 'AI 能力已开启' : 'AI 能力已关闭')
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败'))
  }
}

async function changeDefaultModel(modelId: string) {
  try {
    await store.updateSettings({ defaultModelId: modelId })
    ElMessage.success('默认模型已切换')
  } catch (error) {
    ElMessage.error(errorMessage(error, '切换默认模型失败'))
  }
}

/** 未就绪引导（交互 2.6）：页头提示下一步动作 */
const readinessHint = computed(() => {
  const settings = store.settings
  if (!settings) return ''
  if (!settings.enabled) return 'AI 总开关已关闭'
  if (!settings.modelReady) return '请先添加并启用模型'
  return ''
})

const defaultModelPlaceholder = computed(() => {
  if (store.modelsError) return '模型列表加载失败'
  if (store.settings === null) return '加载中…'
  return store.enabledModels.length === 0 ? '请先添加并启用模型' : '选择默认模型'
})

/** 首次加载完成前不渲染分组，避免子路由表单闪空值（UI 状态要求） */
const shellReady = ref(false)

// ---------- 重建引导条（交互 2.1.3，跨分组常驻） ----------

const reindexRunning = computed(() => store.reindexTaskId !== null)
const reindexProgress = ref(0)
const reindexPhase = ref('')

const showReindexBanner = computed(
  () =>
    store.embedding?.requiresReindex === true ||
    reindexRunning.value ||
    store.reindexError !== '',
)

/** 重建进度复用 aiTask store 轮询（交互 5），到达终态时收口横幅状态 */
watch(
  () => aiTaskStore.detail,
  (cached) => {
    if (!cached || store.reindexTaskId === null || cached.taskId !== store.reindexTaskId) return
    if (!isTerminalTaskStatus(cached.status)) {
      reindexProgress.value = cached.progress ?? 0
      reindexPhase.value = cached.phase ?? ''
      return
    }
    if (cached.status === 'succeeded') {
      store.settleReindex(null)
      void store.loadEmbedding()
      ElMessage.success('向量重建完成')
    } else if (cached.status === 'failed') {
      store.settleReindex(cached.error?.msg ?? '向量重建失败')
    } else {
      store.settleReindex(null) // 取消：回待发起态，requiresReindex 仍为 true
    }
  },
)

async function handleStartReindex() {
  const ok = await store.startReindex()
  if (!ok) return // 失败原因由横幅展示 reindexError
  reindexProgress.value = 0
  reindexPhase.value = ''
  if (store.reindexTaskId) aiTaskStore.startPolling(store.reindexTaskId)
}

onMounted(async () => {
  await Promise.all([store.loadSettings(), store.loadModels(), store.loadEmbedding()])
  shellReady.value = true
  // 会话内已有进行中的重建任务（切走再切回）：续接 aiTask 轮询
  if (store.reindexTaskId) aiTaskStore.startPolling(store.reindexTaskId)
})
</script>

<template>
  <div class="ai-shell">
    <!-- 页头（跨分组常驻）：AI 总开关 + 默认模型 -->
    <header class="ai-shell__head">
      <div class="ai-shell__title-block">
        <h1 class="ai-shell__title">AI 配置</h1>
        <span v-if="readinessHint" class="ai-shell__hint">{{ readinessHint }}</span>
      </div>
      <div class="ai-shell__globals">
        <span class="ai-shell__global-label">AI 总开关</span>
        <el-switch
          :model-value="store.settings?.enabled ?? false"
          :disabled="store.settings === null"
          @change="handleToggle"
        />
        <span class="ai-shell__global-label">默认模型</span>
        <el-select
          :model-value="store.settings?.defaultModelId ?? ''"
          :disabled="store.settings === null || store.enabledModels.length === 0"
          :placeholder="defaultModelPlaceholder"
          filterable
          class="ai-shell__default-model"
          @change="changeDefaultModel"
        >
          <el-option
            v-for="model in store.enabledModels"
            :key="model.id"
            :label="`${model.name}（${model.modelName}）`"
            :value="model.id"
          />
        </el-select>
      </div>
    </header>

    <!-- 设置加载失败（UI-PAGE-11：页面捕获 + 重试） -->
    <div v-if="store.settingsError" class="ai-shell__error">
      <span>{{ store.settingsError }}</span>
      <el-button size="small" @click="store.loadSettings()">重试</el-button>
    </div>

    <!-- 重建引导条：全宽横幅，切换分组不消失 -->
    <div
      v-if="showReindexBanner"
      class="ai-shell__reindex"
      :class="{ 'ai-shell__reindex--running': reindexRunning }"
    >
      <template v-if="reindexRunning">
        <span class="ai-shell__reindex-text">向量重建进行中</span>
        <el-progress
          class="ai-shell__reindex-progress"
          :percentage="reindexProgress"
          :stroke-width="6"
        />
        <span v-if="reindexPhase" class="ai-shell__reindex-phase">{{ reindexPhase }}</span>
      </template>
      <template v-else-if="store.reindexError">
        <span class="ai-shell__reindex-text">向量重建失败：{{ store.reindexError }}</span>
        <el-button size="small" type="primary" @click="handleStartReindex">重试</el-button>
      </template>
      <template v-else>
        <span class="ai-shell__reindex-text">向量维度已变更，需全量重建</span>
        <el-button size="small" type="primary" @click="handleStartReindex">发起重建</el-button>
      </template>
    </div>

    <div class="ai-shell__body">
      <!-- 左侧分组导航：当前项主色文字 + 指示条（视觉 6.2） -->
      <nav class="ai-shell__nav">
        <router-link
          v-for="group in GROUPS"
          :key="group.path"
          :to="group.path"
          class="ai-shell__nav-item"
          :class="{ 'ai-shell__nav-item--active': route.path === group.path }"
        >
          {{ group.label }}
        </router-link>
      </nav>

      <!-- 右侧仅渲染当前分组内容；首载完成前给加载占位 -->
      <main v-loading="!shellReady" class="ai-shell__main">
        <router-view v-if="shellReady" />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.ai-shell__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  flex-shrink: 0;
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-neutral-200);
}

.ai-shell__title-block {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  min-width: 0;
}

.ai-shell__title {
  margin: 0;
  font-size: var(--font-size-2xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-neutral-900);
}

.ai-shell__hint {
  font-size: var(--font-size-sm);
  color: var(--color-warning);
}

.ai-shell__globals {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.ai-shell__global-label {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
}

.ai-shell__default-model {
  width: 240px;
}

.ai-shell__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  flex-shrink: 0;
  margin-top: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-md);
}

.ai-shell__reindex {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-shrink: 0;
  margin-top: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-warning);
  background: var(--color-warning-light);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
}

.ai-shell__reindex--running {
  color: var(--color-neutral-700);
  background: var(--color-primary-50);
  border-color: var(--color-primary-200);
}

.ai-shell__reindex-text {
  flex-shrink: 0;
}

.ai-shell__reindex-progress {
  flex: 1;
  min-width: 120px;

  // 侵入进度条内部只是取色对齐主色，无更深作用域写法
  :deep(.el-progress-bar__inner) {
    background: var(--color-primary-500);
  }
}

.ai-shell__reindex-phase {
  flex-shrink: 0;
  color: var(--color-neutral-500);
}

.ai-shell__body {
  display: flex;
  flex: 1;
  min-height: 0;
  padding-top: var(--space-md);
}

.ai-shell__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 150px;
  flex-shrink: 0;
  overflow-y: auto;
}

.ai-shell__nav-item {
  position: relative;
  display: block;
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-base);
  color: var(--shell-text);
  border-radius: var(--radius-md);
  text-decoration: none;
  transition:
    background 0.15s,
    color 0.15s;

  &:hover {
    background: var(--shell-item-hover);
    color: var(--shell-text-strong);
  }
}

.ai-shell__nav-item--active {
  color: var(--color-primary-500);
  font-weight: 600;
  background: transparent;

  &::after {
    content: '';
    position: absolute;
    right: 4px;
    top: 50%;
    transform: translateY(-50%);
    width: 2px;
    height: 16px;
    background: var(--color-primary-500);
    border-radius: 1px;
  }

  &:hover {
    background: var(--shell-item-active);
    color: var(--color-primary-500);
  }
}

.ai-shell__main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding-left: var(--space-md);
  border-left: 1px solid var(--color-neutral-200);
}
</style>
