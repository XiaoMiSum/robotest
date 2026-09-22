<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiReportDetail, ApiReportShareInfo } from '@/types'
import { fetchReportDetail, shareReport } from '@/services/project/api-testing/report'
import { formatDateTime } from '@/utils/format'
import ReportResultView from '@/components/project/api-testing/report/ReportResultView.vue'

const props = defineProps<{
  reportId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const loading = ref(true)
const report = ref<ApiReportDetail | null>(null)

async function loadDetail() {
  loading.value = true
  try {
    report.value = await fetchReportDetail(props.reportId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报告详情加载失败')
  } finally {
    loading.value = false
  }
}

// ==================== 分享（有未过期分享时复用展示；无则两步式生成） ====================
const shareDialogVisible = ref(false)
/** create：两步式（选有效期→生成）；record：直接展示现有未过期分享 */
const shareView = ref<'create' | 'record'>('create')
const shareExpiryDays = ref(7)
const shareRecord = ref<ApiReportShareInfo | null>(null)
const shareSubmitting = ref(false)

function fullUrl(relative: string): string {
  return window.location.origin + relative
}

function openShareDialog() {
  // 已有未过期分享（详情接口 share 非空）→ 直接复用展示记录，不重复生成
  if (report.value?.share) {
    shareRecord.value = report.value.share
    shareView.value = 'record'
    shareExpiryDays.value = 7
  } else {
    shareView.value = 'create'
    shareExpiryDays.value = 7
  }
  shareDialogVisible.value = true
}

async function generateShareLink() {
  shareSubmitting.value = true
  try {
    const resp = await shareReport(props.reportId, shareExpiryDays.value)
    const record: ApiReportShareInfo = {
      shareUrl: resp.shareUrl,
      expiresAt: resp.expiresAt,
      shareBy: resp.shareBy,
    }
    // 落盘最新分享记录，重新打开弹窗时直接复用
    if (report.value) report.value.share = record
    shareRecord.value = record
    shareView.value = 'record'
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分享失败')
  } finally {
    shareSubmitting.value = false
  }
}

function shareText(): string {
  const record = shareRecord.value
  const shareBy = record?.shareBy || '-'
  const name = report.value?.name ?? '-'
  const url = record ? fullUrl(record.shareUrl) : ''
  const expires = record ? formatDateTime(record.expiresAt) : '-'
  // 复制文本需带描述：报告名称、链接、有效期、分享者（测试报告详细设计 4.2.3）
  return [
    '【测试报告分享】',
    `报告名称：${name}`,
    `分享链接：${url}`,
    `有效期至：${expires}`,
    `分享人：${shareBy}`,
  ].join('\n')
}

async function copyShare() {
  const text = shareText()
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制分享链接')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

onMounted(loadDetail)
</script>

<template>
  <div v-loading="loading" class="report-detail">
    <template v-if="report">
      <div class="report-detail__content">
        <ReportResultView :report="report">
          <template #hero-actions>
            <el-button class="detail-actions__btn" @click="openShareDialog">
              <el-icon><Share /></el-icon>分享
            </el-button>
            <el-button class="detail-actions__btn" @click="emit('back')">关闭</el-button>
          </template>
        </ReportResultView>
      </div>
    </template>

    <!-- 分享弹窗：有未过期分享复用展示（record）；无则两步式生成（create） -->
    <el-dialog v-model="shareDialogVisible" title="分享报告" width="480px" :close-on-click-modal="false">
      <template v-if="shareView === 'record' && shareRecord">
        <el-input :model-value="fullUrl(shareRecord.shareUrl)" readonly>
          <template #append>
            <el-button @click="copyShare">复制</el-button>
          </template>
        </el-input>
        <p class="share-dialog__expires">有效期至：{{ formatDateTime(shareRecord.expiresAt) }}</p>
        <p class="share-dialog__expires">分享人：{{ shareRecord.shareBy || '-' }}</p>
        <div class="share-dialog__actions">
          <el-button @click="shareView = 'create'">重新生成</el-button>
        </div>
      </template>
      <template v-else-if="shareView === 'create'">
        <p class="share-dialog__hint">
          选择有效期后生成分享链接，有效期内可免登录访问{{ report?.share ? '（将覆盖当前分享）' : '' }}
        </p>
        <el-radio-group v-model="shareExpiryDays" class="share-dialog__expiry">
          <el-radio-button :value="1">1 天</el-radio-button>
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
          <el-radio-button :value="90">90 天</el-radio-button>
        </el-radio-group>
        <div class="share-dialog__actions">
          <el-button @click="shareDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="shareSubmitting" @click="generateShareLink">
            生成分享链接
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.report-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: auto;
}

.report-detail__content {
  background: #f2f4f7;
  border-radius: 16px;
  padding: 20px 24px;
  flex: 1;
}

.detail-actions__btn {
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(61, 122, 85, 0.18);
  color: #1a4a2e;

  &:hover {
    background: #fff;
    border-color: rgba(61, 122, 85, 0.4);
  }
}

.share-dialog__hint {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
  margin: 0 0 var(--space-sm);
}

.share-dialog__expiry {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}

.share-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-xs);
  margin-top: var(--space-lg);
}

.share-dialog__expires {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  margin: var(--space-sm) 0 0;
}
</style>