<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { ApiPublicReportResp } from '@/types'
import { fetchPublicReport } from '@/services/project/report'
import ReportResultView from '@/components/project/api-testing/ReportResultView.vue'

const route = useRoute()

const loading = ref(true)
const error = ref('')
const report = ref<ApiPublicReportResp | null>(null)

async function loadReport() {
  loading.value = true
  error.value = ''
  try {
    const id = String(route.params.id ?? '')
    const token = String(route.query.token ?? '')
    if (!id || !token) {
      error.value = '分享链接无效'
      return
    }
    report.value = await fetchPublicReport(id, token)
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    if (msg.includes('7009') || msg.includes('分享')) {
      error.value = '分享链接无效或已过期'
    } else {
      error.value = msg || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadReport)
</script>

<template>
  <div class="share-page">
    <div v-if="loading" v-loading="true" class="share-page__loading" />

    <!-- 错误态 -->
    <el-result v-else-if="error" icon="error" :title="error" sub-title="请联系报告分享者重新生成链接">
      <template #extra />
    </el-result>

    <!-- 正常态：与报告详情页共用同一套结果展示组件（Hero/统计卡/处理器/场景/步骤） -->
    <template v-else-if="report">
      <section class="share-page__result">
        <ReportResultView :report="report" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.share-page {
  max-width: 1120px;
  margin: 0 auto;
  padding: var(--space-xl);
  min-height: 100vh;
  background: var(--color-neutral-50);
}

.share-page__loading {
  min-height: 300px;
}

.share-page__result {
  background: #f2f4f7;
  border-radius: 16px;
  padding: 20px 24px;
}
</style>