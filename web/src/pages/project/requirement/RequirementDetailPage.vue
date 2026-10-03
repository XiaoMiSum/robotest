<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatDateTime } from '@/utils/format'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
import {
  requirementChangeLines,
  requirementChangeTypeLabel,
  requirementCoverageMeta,
  requirementPriorityLabel,
  requirementSourceLabel,
  requirementStatusMeta,
} from '@/composables/project/requirement/requirementPresentation'
import { useRequirementDetail } from '@/composables/project/requirement/useRequirementDetail'

const {
  loading,
  loadError,
  notFound,
  detail,
  logs,
  logsTotal,
  logsLoading,
  canLoadMoreLogs,
  loadLogs,
  moduleTree,
  memberOptions,
  titleEditing,
  titleDraft,
  startEditTitle,
  cancelEditTitle,
  saveTitle,
  descriptionEditing,
  descriptionDraft,
  startEditDescription,
  cancelEditDescription,
  saveDescription,
  form,
  saving,
  versionError,
  attributeDirty,
  saveAttributes,
  isReadonly,
  canConfirm,
  canArchive,
  canUnarchive,
  canEdit,
  handleConfirm,
  handleArchive,
  handleUnarchive,
  aiAvailable,
  splitting,
  canSplit,
  handleSplit,
  retry,
} = useRequirementDetail()

const statusMeta = computed(() =>
  detail.value ? requirementStatusMeta(detail.value.status) : null,
)
const coverageMeta = computed(() =>
  detail.value ? requirementCoverageMeta(detail.value.coverageStatus) : null,
)

const priorityOptions = [
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
]

function onTitleKeydown(event: Event | KeyboardEvent): void {
  if (!('key' in event)) return
  if (event.key === 'Enter') {
    event.preventDefault()
    void saveTitle()
  } else if (event.key === 'Escape') {
    cancelEditTitle()
  }
}

const router = useRouter()

function goBack(): void {
  void router.push('/workspace/projects/requirements')
}
</script>

<template>
  <div class="requirement-detail">
    <div v-if="notFound" class="requirement-detail__missing">
      <el-empty description="需求不存在或无权访问" :image-size="120">
        <el-button type="primary" @click="goBack">返回需求列表</el-button>
      </el-empty>
    </div>

    <template v-else>
      <!-- UI-BC-01：列表 → 详情两级面包屑，回退入口是唯一可点击项 -->
      <div class="requirement-detail__breadcrumb">
        <router-link to="/workspace/projects/requirements">需求管理</router-link>
        <el-icon :size="13"><ArrowRight /></el-icon>
        <span>{{ detail?.title ?? '需求详情' }}</span>
      </div>

      <div v-loading="loading" class="requirement-detail__body">
        <el-alert
          v-if="loadError"
          type="error"
          :title="loadError"
          show-icon
          :closable="false"
          class="requirement-detail__error"
        >
          <template #default>
            <el-button size="small" type="danger" plain @click="retry">重试</el-button>
          </template>
        </el-alert>

        <template v-if="detail">
          <div class="requirement-detail__head">
            <div class="requirement-detail__head-main">
              <span class="requirement-detail__code">{{ detail.code }}</span>
              <el-input
                v-if="titleEditing"
                v-model="titleDraft"
                class="requirement-detail__title-input"
                maxlength="300"
                placeholder="需求标题"
                @blur="saveTitle"
                @keydown="onTitleKeydown"
              />
              <el-link
                v-else-if="!isReadonly && canEdit"
                class="requirement-detail__title"
                :underline="false"
                @click="startEditTitle"
              >
                {{ detail.title }}
                <el-icon class="requirement-detail__title-icon"><EditPen /></el-icon>
              </el-link>
              <h1 v-else class="requirement-detail__title requirement-detail__title--static">
                {{ detail.title }}
              </h1>
              <el-tag
                v-if="statusMeta"
                :type="statusMeta.tagType"
                size="small"
                effect="light"
                :class="{ 'status-tag--archived': statusMeta.archived }"
              >
                {{ statusMeta.label }}
              </el-tag>
            </div>
            <div class="requirement-detail__actions">
              <el-button
                v-if="canConfirm"
                type="primary"
                :loading="saving"
                @click="handleConfirm"
              >确认</el-button>
              <el-button v-if="canArchive" type="warning" plain @click="handleArchive">归档</el-button>
              <el-button v-if="canUnarchive" @click="handleUnarchive">取消归档</el-button>
              <el-tooltip
                v-if="canEdit && aiAvailable"
                :content="canSplit ? '提交 AI 拆分任务' : '当前状态不可拆分'"
                placement="bottom"
              >
                <span>
                  <el-button :disabled="!canSplit || splitting" :loading="splitting" @click="handleSplit">
                    AI 拆分
                  </el-button>
                </span>
              </el-tooltip>
            </div>
          </div>

          <div class="requirement-detail__layout">
            <div class="requirement-detail__main">
              <el-card shadow="never">
                <template #header>
                  <div class="requirement-detail__card-header">
                    <span class="requirement-detail__section">描述</span>
                    <el-button
                      v-if="!descriptionEditing && !isReadonly && canEdit"
                      link
                      type="primary"
                      @click="startEditDescription"
                    >编辑</el-button>
                  </div>
                </template>
                <template v-if="descriptionEditing">
                  <MarkdownEditor
                    v-model="descriptionDraft"
                    height="320px"
                    placeholder="需求描述（支持 Markdown）"
                  />
                  <div class="requirement-detail__edit-actions">
                    <el-button size="small" @click="cancelEditDescription">取消</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :loading="saving"
                      @click="saveDescription"
                    >保存</el-button>
                  </div>
                </template>
                <MarkdownView v-else-if="detail.description" :content="detail.description" />
                <el-empty v-else description="暂无描述" :image-size="48" />
              </el-card>

              <!-- 来源附件：文件管理模块落地前 sourceFile 恒为 null，不渲染附件区 -->
              <el-card v-if="detail.sourceFile" shadow="never">
                <template #header><span class="requirement-detail__section">来源附件</span></template>
                <span>{{ detail.sourceFile.name }}</span>
              </el-card>

              <el-card shadow="never">
                <template #header>
                  <div class="requirement-detail__card-header">
                    <span class="requirement-detail__section">变更记录</span>
                    <span v-if="logsTotal > 0" class="requirement-detail__muted">
                      共 {{ logsTotal }} 条
                    </span>
                  </div>
                </template>
                <el-timeline v-if="logs.length" v-loading="logsLoading" class="requirement-detail__timeline">
                  <el-timeline-item
                    v-for="log in logs"
                    :key="log.id"
                    :timestamp="formatDateTime(log.createdAt)"
                    placement="top"
                  >
                    <div class="requirement-detail__log-head">
                      <strong>{{ log.operatorName ?? '—' }}</strong>
                      <el-tag size="small" effect="plain" type="info">
                        {{ requirementChangeTypeLabel(log.changeType) }}
                      </el-tag>
                    </div>
                    <div
                      v-for="(line, index) in requirementChangeLines(log)"
                      :key="index"
                      class="requirement-detail__log-line"
                    >{{ line }}</div>
                  </el-timeline-item>
                </el-timeline>
                <el-empty v-else description="暂无变更记录" :image-size="48" />
                <div v-if="canLoadMoreLogs" class="requirement-detail__log-more">
                  <el-button size="small" :loading="logsLoading" @click="loadLogs">加载更多</el-button>
                </div>
              </el-card>
            </div>

            <el-card shadow="never" class="requirement-detail__side">
              <template #header><span class="requirement-detail__section">属性</span></template>

              <!-- 归档只读：属性降级为纯文本（交互设计 06 §2.2.3） -->
              <dl v-if="isReadonly" class="requirement-detail__props">
                <div class="requirement-detail__prop">
                  <dt>所属模块</dt>
                  <dd>{{ detail.moduleName ?? '—' }}</dd>
                </div>
                <div class="requirement-detail__prop">
                  <dt>版本</dt>
                  <dd>{{ detail.systemVersion ?? '—' }}</dd>
                </div>
                <div class="requirement-detail__prop">
                  <dt>负责人</dt>
                  <dd>{{ detail.ownerName ?? '—' }}</dd>
                </div>
                <div class="requirement-detail__prop">
                  <dt>优先级</dt>
                  <dd>{{ requirementPriorityLabel(detail.priority) }}</dd>
                </div>
                <div class="requirement-detail__prop">
                  <dt>标签</dt>
                  <dd>{{ detail.tags && detail.tags.length ? detail.tags.join('、') : '—' }}</dd>
                </div>
                <div class="requirement-detail__prop">
                  <dt>来源</dt>
                  <dd>{{ requirementSourceLabel(detail.source) }}</dd>
                </div>
                <div class="requirement-detail__prop">
                  <dt>覆盖状态</dt>
                  <dd>
                    <el-tag v-if="coverageMeta" :type="coverageMeta.tagType" size="small" effect="light">
                      {{ coverageMeta.label }}
                    </el-tag>
                    <span v-else>—</span>
                  </dd>
                </div>
              </dl>

              <el-form v-else label-position="top" @submit.prevent>
                <el-form-item label="所属模块">
                  <!-- 模块仅支持改为非空值（后端 null 视为不修改），不提供清空 -->
                  <el-tree-select
                    v-model="form.moduleId"
                    :data="moduleTree"
                    check-strictly
                    placeholder="未分配"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="版本" :error="versionError">
                  <el-input
                    v-model="form.systemVersion"
                    maxlength="50"
                    show-word-limit
                    placeholder="被测业务系统版本"
                  />
                </el-form-item>
                <el-form-item label="负责人">
                  <!-- 负责人同模块：后端仅支持改为非空值，不提供清空 -->
                  <el-select
                    v-model="form.ownerId"
                    filterable
                    placeholder="未指派"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="member in memberOptions"
                      :key="member.userId"
                      :value="member.userId"
                      :label="member.name || member.username"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="优先级">
                  <el-select v-model="form.priority" clearable placeholder="未设置" style="width: 100%">
                    <el-option v-for="option in priorityOptions" :key="option.value" v-bind="option" />
                  </el-select>
                </el-form-item>
                <el-form-item label="标签">
                  <el-select
                    v-model="form.tags"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    clearable
                    placeholder="回车创建"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="来源">
                  <span class="requirement-detail__muted">{{ requirementSourceLabel(detail.source) }}</span>
                </el-form-item>
                <el-form-item label="覆盖状态">
                  <el-tag v-if="coverageMeta" :type="coverageMeta.tagType" size="small" effect="light">
                    {{ coverageMeta.label }}
                  </el-tag>
                  <span v-else class="requirement-detail__muted">—</span>
                </el-form-item>
                <div v-if="!canEdit" class="requirement-detail__muted requirement-detail__hint">
                  你没有需求编辑权限，属性为只读。
                </div>
                <el-button
                  v-if="canEdit"
                  type="primary"
                  :loading="saving"
                  :disabled="!attributeDirty"
                  style="width: 100%"
                  @click="saveAttributes"
                >保存属性</el-button>
              </el-form>
            </el-card>
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.requirement-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: auto;
  padding-bottom: var(--space-lg);
}

.requirement-detail__missing {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

// UI-BC-02：flex 行、gap 6px、字号与基础文字色取自令牌
.requirement-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-sm);
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  flex-shrink: 0;

  a {
    color: var(--color-primary-500);
    font-weight: 500;
    text-decoration: none;
  }

  a:hover {
    color: var(--color-primary-600);
  }

  .el-icon {
    color: var(--color-neutral-400);
  }

  > :last-child {
    color: var(--color-neutral-900);
  }
}

.requirement-detail__body {
  flex: 1;
  min-height: 0;
}

.requirement-detail__error {
  margin-bottom: var(--space-md);
}

.requirement-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}

.requirement-detail__head-main {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.requirement-detail__code {
  flex-shrink: 0;
  padding: 2px var(--space-xs);
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-sm);
  color: var(--color-neutral-600);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
}

.requirement-detail__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-xl);
  font-weight: 650;
  line-height: 1.4;
}

.requirement-detail__title--static {
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.requirement-detail__title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;

  &:hover .requirement-detail__title-icon {
    opacity: 1;
  }
}

.requirement-detail__title-icon {
  color: var(--color-neutral-400);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.requirement-detail__title-input {
  flex: 1;
  min-width: 0;
}

.requirement-detail__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.requirement-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-lg);
  align-items: start;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.requirement-detail__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  min-width: 0;
}

.requirement-detail__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
}

.requirement-detail__card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.requirement-detail__edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.requirement-detail__muted {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.requirement-detail__hint {
  margin-top: var(--space-sm);
  text-align: center;
}

.requirement-detail__timeline {
  padding-left: var(--space-xs);
}

.requirement-detail__log-head {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.requirement-detail__log-line {
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.requirement-detail__log-more {
  display: flex;
  justify-content: center;
  margin-top: var(--space-sm);
}

.requirement-detail__props {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin: 0;
}

.requirement-detail__prop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);

  dt {
    color: var(--color-neutral-500);
    font-size: var(--font-size-sm);
  }

  dd {
    margin: 0;
    color: var(--color-neutral-800);
    font-size: var(--font-size-sm);
    text-align: right;
    word-break: break-all;
  }
}

.status-tag--archived {
  --el-tag-text-color: var(--color-neutral-400);
  --el-tag-bg-color: var(--color-neutral-50);
  --el-tag-border-color: var(--color-neutral-300);
}
</style>
