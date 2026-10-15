<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { List } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import { useAiTaskStore } from '@/stores/aiTask'

/**
 * 顶栏 AI 任务指示器（交互 07 §1）：项目模式 + ai:task 权限显隐，
 * 角标为进行中（pending + running）任务数，0 时不显示角标；点击进任务中心。
 * AI 未启用时后端已将进行中任务批量置 failed，计数自然归零，无需额外判断。
 */
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const navStore = useNavStore()
const aiTaskStore = useAiTaskStore()

const visible = computed(() => navStore.isProjectMode && authStore.hasPermission('ai:task'))

// 计数轮询随显隐启停（交互 07 §5）：workspace 模式或无权限时不空转请求
watch(
  visible,
  (show) => {
    if (show) aiTaskStore.startCountPolling()
    else aiTaskStore.stopCountPolling()
  },
  { immediate: true },
)

onBeforeUnmount(() => aiTaskStore.stopCountPolling())

// 提交/重试后均跳任务详情页：进入任务中心域即刷新角标，不等下一轮 15s 轮询
watch(
  () => route.path,
  (path) => {
    if (visible.value && path.startsWith('/workspace/projects/ai/tasks')) {
      void aiTaskStore.refreshRunningCount()
    }
  },
)

function openTaskCenter(): void {
  void router.push('/workspace/projects/ai/tasks')
}
</script>

<template>
  <div v-if="visible" class="top-nav__icon-btn" @click="openTaskCenter">
    <el-badge :value="aiTaskStore.runningCount" :hidden="aiTaskStore.runningCount === 0">
      <el-icon><List /></el-icon>
    </el-badge>
    <span>AI 任务</span>
  </div>
</template>
