<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiReportDetail } from '@/types'
import { fetchReportDetail } from '@/services/project/api-testing/report'
import ReportResultView from '@/components/project/api-testing/ReportResultView.vue'

const props = defineProps<{
  modelValue: boolean
  reportId: string
  /** 当前场景 ID（套件共享报告时用于定位该场景的步骤明细） */
  sceneId?: string | null
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const loading = ref(false)
const report = ref<ApiReportDetail | null>(null)
const viewingFullSuite = ref(false)

watch(
  () => [props.modelValue, props.reportId] as const,
  async ([visible, reportId]) => {
    if (!visible) return
    loading.value = true
    report.value = null
    viewingFullSuite.value = false
    try {
      // 执行记录中的报告（含场景页运行）仅详情可见，不走报告列表接口
      report.value = await fetchReportDetail(reportId)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '报告详情加载失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

// 套件共享报告默认定位当前场景；查看完整套件后切换为两级展示
const focusSceneId = computed(() => (viewingFullSuite.value ? null : props.sceneId ?? null))
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="报告详情"
    width="1100px"
    top="4vh"
    destroy-on-close
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-loading="loading">
      <template v-if="report">
        <!-- 套件且当前在完整套件视图：返回当前场景明细 -->
        <div v-if="report.reportType === 'suite' && sceneId && viewingFullSuite" class="report-dialog__toolbar">
          <el-button link size="small" @click="viewingFullSuite = false">
            <el-icon><Back /></el-icon>返回当前场景明细
          </el-button>
        </div>

        <!-- 结果数据集（Hero/统计卡/处理器/场景/步骤，共用组件） -->
        <ReportResultView
          :report="report"
          :focus-scene-id="focusSceneId"
          @view-suite="viewingFullSuite = true"
        />
      </template>
      <el-empty v-else-if="!loading" description="暂无报告内容" />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.report-dialog__toolbar {
  margin-bottom: var(--space-sm);
}
</style>