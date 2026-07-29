<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchBugs } from '@/services/project'
import type { BugListItem, BugResolution } from '@/types'
import { BUG_RESOLUTION_LABEL } from '@/utils/bugStatus'

const props = defineProps<{
  modelValue: boolean
  /** 当前缺陷 ID，重复缺陷选项中排除自身 */
  excludeBugId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [payload: { resolution: BugResolution; duplicateOfBugId?: string; comment?: string }]
}>()

const form = reactive({
  resolution: 'fixed' as BugResolution,
  duplicateOfBugId: '',
  comment: '',
})

const duplicateOptions = ref<BugListItem[]>([])
const searching = ref(false)

async function searchBugs(keyword: string) {
  searching.value = true
  try {
    const page = await fetchBugs({ keyword: keyword || undefined, pageNo: 1, pageSize: 20 })
    duplicateOptions.value = page.list.filter((b) => b.id !== props.excludeBugId)
  } catch {
    // 搜索失败不阻塞，用户可重试
  } finally {
    searching.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      form.resolution = 'fixed'
      form.duplicateOfBugId = ''
      form.comment = ''
      searchBugs('')
    }
  },
)

function handleConfirm() {
  if (form.resolution === 'duplicate' && !form.duplicateOfBugId) {
    ElMessage.warning('请选择重复的原始缺陷')
    return
  }
  emit('confirm', {
    resolution: form.resolution,
    duplicateOfBugId: form.resolution === 'duplicate' ? form.duplicateOfBugId : undefined,
    comment: form.comment.trim() || undefined,
  })
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="解决缺陷"
    width="480px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="96px">
      <el-form-item label="解决方案" required>
        <el-select v-model="form.resolution" style="width: 240px">
          <el-option
            v-for="(label, key) in BUG_RESOLUTION_LABEL"
            :key="key"
            :label="label"
            :value="key"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.resolution === 'duplicate'" label="重复缺陷" required>
        <el-select
          v-model="form.duplicateOfBugId"
          filterable
          remote
          :remote-method="searchBugs"
          :loading="searching"
          placeholder="搜索并选择原始缺陷"
          style="width: 320px"
        >
          <el-option v-for="b in duplicateOptions" :key="b.id" :label="b.title" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="解决说明（选填）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>
