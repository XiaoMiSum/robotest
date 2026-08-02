<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAiAgentDetail,
  fetchAiAgents,
  restoreAiAgentDefault,
  saveAiAgent,
} from '@/services/admin'
import type { AiAgent, AiAgentDetail } from '@/types'
import { formatShortDateTime } from '@/utils/format'

const AGENT_DESCRIPTIONS: Record<string, string> = {
  case_generation: '根据需求条目或选中节点智能生成测试用例子树',
  step_completion: '为已有用例补全测试步骤与预期结果',
  text_import: '解析外部粘贴的文本并转换为结构化用例',
  review_summary: '汇总评审快照并生成结构化评审摘要',
  assistant_chat: '面向平台业务的问答与操作辅助助手',
  priority_recommendation: '为用例推荐 P0–P3 测试优先级',
  bug_form_suggestion: '优化缺陷标题并建议缺陷严重等级',
  dsl_translation: '将自然语言指令翻译为脑图操作指令',
  plan_order_reason: '为计划关联用例生成执行顺序推荐及理由',
  missing_point_analysis: '对照需求分析当前用例遗漏的测试点',
  keyword_extraction: '抽取需求条目的关键要素用于检索',
  regression_recommendation: '推荐与本次变更相关的回归用例子集',
  review_check: '检查评审快照的缺失项与风险点',
  bug_clustering: '按根因聚类归纳缺陷清单',
}

function agentDescription(functionType: string): string {
  return AGENT_DESCRIPTIONS[functionType] ?? 'AI 功能模板'
}

const loading = ref(false)
const agents = ref<AiAgent[]>([])
const drawerVisible = ref(false)
const saving = ref(false)
const detail = ref<AiAgentDetail | null>(null)
const customizedCount = computed(() => agents.value.filter((agent) => agent.customized).length)

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
    <el-card shadow="never" class="ai-agents-page__header">
      <div class="ai-agents-page__header-left">
        <div class="ai-agents-page__header-icon">
          <el-icon :size="20"><MagicStick /></el-icon>
        </div>
        <div>
          <div class="ai-agents-page__header-title">智能体</div>
          <div class="ai-agents-page__header-sub">配置各 AI 功能的提示词模板，默认内置，可自定义角色指令并一键恢复</div>
        </div>
      </div>
      <div class="ai-agents-page__header-stats">
        <div class="ai-agents-page__stat">
          <span class="ai-agents-page__stat-value">{{ agents.length }}</span>
          <span class="ai-agents-page__stat-label">全部</span>
        </div>
        <div class="ai-agents-page__stat-divider" />
        <div class="ai-agents-page__stat">
          <span class="ai-agents-page__stat-value ai-agents-page__stat-value--success">{{ customizedCount }}</span>
          <span class="ai-agents-page__stat-label">已自定义</span>
        </div>
      </div>
    </el-card>

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
          <div class="agent-card__head-actions" @click.stop>
            <el-tag :type="agent.customized ? 'success' : 'info'" size="small" effect="light" round>
              {{ agent.customized ? '已自定义' : '默认' }}
            </el-tag>
            <el-button
              v-if="agent.customized"
              size="small"
              text
              type="warning"
              class="agent-card__hover-action"
              @click="handleRestore(agent)"
            >
              <el-icon><RefreshLeft /></el-icon>恢复默认
            </el-button>
            <el-button
              size="small"
              type="primary"
              plain
              class="agent-card__hover-action"
              @click="openEditor(agent)"
            >
              <el-icon><EditPen /></el-icon>编辑
            </el-button>
          </div>
        </div>
        <div class="agent-card__name">{{ agent.name }}</div>
        <div class="agent-card__desc">{{ agentDescription(agent.functionType) }}</div>
        <div class="agent-card__meta">
          <template v-if="agent.customized">
            <el-icon><User /></el-icon>
            <span>{{ agent.updatedBy ?? '-' }}</span>
            <span class="agent-card__meta-dot" />
            <span>{{ formatShortDateTime(agent.updatedAt) }}</span>
          </template>
          <template v-else>
            <el-icon><InfoFilled /></el-icon>
            <span>内置默认模板</span>
          </template>
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
      <el-form v-if="detail" label-position="top" class="ai-agents-page__form">
        <el-form-item>
          <template #label>
            <span class="ai-agents-page__label">
              <el-icon><ChatLineSquare /></el-icon>角色指令段
            </span>
          </template>
          <el-input v-model="editForm.roleInstruction" type="textarea" :rows="10" maxlength="8000" show-word-limit />
        </el-form-item>
        <div class="ai-agents-page__advanced-card">
          <div class="ai-agents-page__advanced-row">
            <div class="ai-agents-page__advanced-text">
              <div class="ai-agents-page__advanced-title">格式约束段编辑（高级）</div>
              <div class="ai-agents-page__advanced-hint">开启后可修改输出格式约束，可能影响结构化校验</div>
            </div>
            <el-switch v-model="editForm.formatEditable" @change="handleFormatEditableChange" />
          </div>
        </div>
        <el-form-item>
          <template #label>
            <span class="ai-agents-page__label">
              <el-icon><Document /></el-icon>输出格式约束段
            </span>
          </template>
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

.ai-agents-page__header {
  margin-bottom: var(--space-lg);

  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--space-lg) var(--space-xl);
  }
}

.ai-agents-page__header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.ai-agents-page__header-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.ai-agents-page__header-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-agents-page__header-sub {
  font-size: 12px;
  color: var(--color-neutral-500);
  margin-top: 2px;
}

.ai-agents-page__header-stats {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.ai-agents-page__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ai-agents-page__stat-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--color-neutral-800);
}

.ai-agents-page__stat-value--success {
  color: var(--color-success);
}

.ai-agents-page__stat-label {
  font-size: 12px;
  color: var(--color-neutral-400);
  margin-top: 2px;
}

.ai-agents-page__stat-divider {
  width: 1px;
  height: 28px;
  background: var(--color-neutral-200);
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

.agent-card__head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.agent-card__hover-action {
  opacity: 0;
  visibility: hidden;
  transition:
    opacity var(--transition-fast),
    visibility var(--transition-fast);

  .agent-card:hover & {
    opacity: 1;
    visibility: visible;
  }
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
  margin-bottom: var(--space-xs);
}

.agent-card__desc {
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-neutral-500);
  margin-bottom: var(--space-md);
}

.agent-card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: 12px;
  color: var(--color-neutral-400);
  margin-top: auto;
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100);
}

.agent-card__meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--color-neutral-300);
}

.ai-agents-page__drawer-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-agents-page__form :deep(.el-form-item) {
  margin-bottom: var(--space-xl);
}

.ai-agents-page__label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  font-weight: 600;
}

.ai-agents-page__advanced-card {
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-xl);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
}

.ai-agents-page__advanced-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.ai-agents-page__advanced-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-neutral-700);
}

.ai-agents-page__advanced-hint {
  font-size: 12px;
  color: var(--color-neutral-400);
  margin-top: 2px;
}
</style>
