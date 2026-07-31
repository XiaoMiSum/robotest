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
async function handleFormatEditableChange(next: string | number | boolean) {
  if (next === true) {
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
  <div v-loading="loading" class="ai-agents-page">
    <el-empty v-if="!loading && !agents.length" description="暂无智能体" />

    <div v-else class="ai-agents-page__grid">
      <div
        v-for="agent in agents"
        :key="agent.functionType"
        class="agent-card"
        :class="{ 'agent-card--customized': agent.customized }"
        @click="openEditor(agent)"
      >
        <div class="agent-card__head">
          <div class="agent-card__icon">
            <el-icon :size="22"><MagicStick /></el-icon>
          </div>
          <el-tag :type="agent.customized ? 'success' : 'info'" size="small" effect="light" round>
            {{ agent.customized ? '已自定义' : '默认' }}
          </el-tag>
        </div>
        <div class="agent-card__name">{{ agent.name }}</div>
        <div class="agent-card__type">{{ agent.functionType }}</div>
        <div class="agent-card__meta">
          <template v-if="agent.customized">
            <el-icon><User /></el-icon>
            <span>{{ agent.updatedBy ?? '-' }}</span>
            <span class="agent-card__meta-dot" />
            <span>{{ formatDateTime(agent.updatedAt) }}</span>
          </template>
          <span v-else>使用内置默认模板</span>
        </div>
        <div class="agent-card__actions" @click.stop>
          <el-button size="small" type="primary" plain @click="openEditor(agent)">
            <el-icon><EditPen /></el-icon>编辑
          </el-button>
          <el-button
            v-if="agent.customized"
            size="small"
            type="warning"
            plain
            @click="handleRestore(agent)"
          >
            <el-icon><RefreshLeft /></el-icon>恢复默认
          </el-button>
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" size="600px" class="ai-agents-page__drawer">
      <template #header>
        <div class="ai-agents-page__drawer-title">
          <el-icon :size="18"><MagicStick /></el-icon>
          <span>{{ detail?.name ?? '编辑智能体' }}</span>
          <el-tag v-if="detail?.customized" type="success" size="small" effect="light" round>
            已自定义
          </el-tag>
        </div>
      </template>
      <el-form v-if="detail" label-position="top">
        <el-form-item label="角色指令段">
          <el-input v-model="editForm.roleInstruction" type="textarea" :rows="10" maxlength="8000" show-word-limit />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="ai-agents-page__advanced-label">
              格式约束段编辑（高级）
              <span class="ai-agents-page__advanced-hint">开启后可修改输出格式约束，可能影响结构化校验</span>
            </span>
          </template>
          <el-switch v-model="editForm.formatEditable" @change="handleFormatEditableChange" />
        </el-form-item>
        <el-form-item label="输出格式约束段">
          <el-input
            v-model="editForm.formatConstraint"
            type="textarea"
            :rows="10"
            :disabled="!editForm.formatEditable"
            maxlength="8000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.ai-agents-page {
  min-height: 200px;
}

.ai-agents-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-lg);
}

.agent-card {
  display: flex;
  flex-direction: column;
  padding: var(--space-lg) var(--space-xl);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: all var(--transition-base);

  &:hover {
    transform: translateY(-2px);
    border-color: var(--color-primary-200);
    box-shadow: var(--shadow-card-hover);
  }
}

.agent-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
}

.agent-card__icon {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.agent-card--customized .agent-card__icon {
  background: var(--color-success-light);
  color: var(--color-success);
}

.agent-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-neutral-800);
  margin-bottom: 2px;
}

.agent-card__type {
  font-size: 12px;
  color: var(--color-neutral-400);
  font-family: monospace;
  margin-bottom: var(--space-md);
}

.agent-card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: 12px;
  color: var(--color-neutral-500);
  margin-bottom: var(--space-lg);
}

.agent-card__meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--color-neutral-300);
}

.agent-card__actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: auto;
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100);

  .el-icon {
    margin-right: 4px;
  }
}

.ai-agents-page__drawer-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-agents-page__advanced-label {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ai-agents-page__advanced-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-neutral-400);
}
</style>
