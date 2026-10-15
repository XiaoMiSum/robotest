<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { List } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 能力页就近任务入口（交互 07 §1）：需求列表 / 追溯矩阵 / 缺陷 AI 分析页头复用，
 * 按 ai:task 权限显隐；无权限时不渲染，页面无需再各自判权。
 */
const router = useRouter()
const authStore = useAuthStore()

const canView = computed(() => authStore.hasPermission('ai:task'))
</script>

<template>
  <el-button v-if="canView" @click="router.push('/workspace/projects/ai/tasks')">
    <el-icon><List /></el-icon>任务中心
  </el-button>
</template>
