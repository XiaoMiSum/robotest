<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAiAgentDetail,
  fetchAiAgents,
  restoreAiAgentDefault,
  saveAiAgent,
} from '@/services/admin'
import type { AiAgent, AiAgentDetail } from '@/types'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const agents = ref<AiAgent[]>([])
const drawerVisible = ref(false)
const saving = ref(false)
const detail = ref<AiAgentDetail | null>(null)

const editForm = reactive({
  functionType: '',
  roleInstruction: '',
  formatConstraint: '',
  formatEditable: false,
})

async function loadAgents() {
  loading.value = true
  try {
    agents.value = await fetchAiAgents()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载智能体列表失败')
  } finally {
    loading.value = false
  }
}

async function openEditor(row: AiAgent) {
  try {
    const data = await fetchAiAgentDetail(row.functionType)
    detail.value = data
    editForm.functionType = data.functionType
    editForm.roleInstruction = data.roleInstruction
    editForm.formatConstraint = data.formatConstraint
    editForm.formatEditable = data.formatEditable
    drawerVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载智能体详情失败')
  }
}

// 高级开关开启（false → true）时二次确认，说明结构化校验失败风险
async function handleFormatEditableChange(next: boolean) {
  if (next) {
    try {
      await ElMessageBox.confirm(
        '开启格式约束段编辑可能导致结构化输出校验失败，确定继续？',
        '高级选项',
        { type: 'warning' },
      )
    } catch {
      editForm.formatEditable = false
    }
  }
}

async function handleSave() {
  saving.value = true
  try {
    await saveAiAgent(editForm.functionType, {
      roleInstruction: editForm.roleInstruction,
      formatEditable: editForm.formatEditable,
      formatConstraint: editForm.formatEditable ? editForm.formatConstraint : null,
    })
    ElMessage.success('保存成功')
    drawerVisible.value = false
    loadAgents()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleRestore(row: AiAgent) {
  try {
    await ElMessageBox.confirm(`确定将「${row.name}」恢复为内置默认模板吗？`, '恢复默认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await restoreAiAgentDefault(row.functionType)
    ElMessage.success('已恢复默认')
    loadAgents()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '恢复默认失败')
  }
}

onMounted(loadAgents)
</script>

<template>
  <div class="ai-agents-page">
    <el-table v-loading="loading" :data="agents" border>
      <el-table-column prop="name" label="功能类型" min-width="180" />
      <el-table-column label="是否自定义" width="120">
        <template #default="{ row }">
          <el-tag :type="row.customized ? 'success' : 'info'">
            {{ row.customized ? '已自定义' : '默认' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedBy" label="更新人" width="140">
        <template #default="{ row }">{{ row.updatedBy ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openEditor(row as AiAgent)">
            编辑
          </el-button>
          <el-button
            v-if="row.customized"
            size="small"
            link
            type="warning"
            @click="handleRestore(row as AiAgent)"
          >
            恢复默认
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawerVisible" :title="detail?.name ?? '编辑智能体'" size="600px">
      <el-form v-if="detail" label-position="top">
        <el-form-item label="角色指令段">
          <el-input v-model="editForm.roleInstruction" type="textarea" :rows="8" maxlength="8000" show-word-limit />
        </el-form-item>
        <el-form-item label="格式约束段编辑（高级）">
          <el-switch v-model="editForm.formatEditable" @change="handleFormatEditableChange" />
        </el-form-item>
        <el-form-item label="输出格式约束段">
          <el-input
            v-model="editForm.formatConstraint"
            type="textarea"
            :rows="8"
            :disabled="!editForm.formatEditable"
            maxlength="8000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="drawerVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </div>
</template>

<style scoped>
.ai-agents-page {
  padding: 8px 0;
}
</style>
