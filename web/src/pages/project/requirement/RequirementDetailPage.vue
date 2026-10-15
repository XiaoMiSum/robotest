<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { TabPaneName } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
import GenerationConfigDialog from '@/components/project/ai/GenerationConfigDialog.vue'
import type { AiGenerationScopeItem, RequirementPriority } from '@/types'
import {
  requirementChangeLines,
  requirementChangeTypeLabel,
  requirementCoverageMeta,
  requirementPriorityLabel,
  requirementSourceLabel,
  requirementStatusMeta,
} from '@/composables/project/requirement/requirementPresentation'
import {
  REQUIREMENT_PRIORITY_COLOR,
  REQUIREMENT_PRIORITY_OPTIONS,
} from '@/composables/project/requirement/useRequirementCreate'
import { useRequirementDetail } from '@/composables/project/requirement/useRequirementDetail'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const canViewTrace = computed(() => authStore.hasPermission('trace:view'))

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
  editing,
  titleDraft,
  descriptionDraft,
  startEdit,
  cancelEdit,
  handlePriorityClick,
  form,
  saving,
  versionError,
  editDirty,
  saveAll,
  sideTab,
  ensureLogs,
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
  canLaunchGeneration,
  handleSplit,
  load,
  retry,
} = useRequirementDetail()

// ==================== AI 生成发起 ====================
const generationDialogVisible = ref(false)

const generationScope = computed<AiGenerationScopeItem[]>(() =>
  detail.value
    ? [
        {
          id: detail.value.id,
          code: detail.value.code,
          title: detail.value.title,
          status: detail.value.status,
        },
      ]
    : [],
)

const statusMeta = computed(() =>
  detail.value ? requirementStatusMeta(detail.value.status) : null,
)
const coverageMeta = computed(() =>
  detail.value ? requirementCoverageMeta(detail.value.coverageStatus) : null,
)

/** 分段控件选中态用优先级色（同新建页），EP 无逐按钮配色 prop，走组件 CSS 变量注入 */
function priorityButtonStyle(value: RequirementPriority): Record<string, string> {
  const color = REQUIREMENT_PRIORITY_COLOR[value]
  return {
    '--el-radio-button-checked-bg-color': color,
    '--el-radio-button-checked-border-color': color,
  }
}

/** 变更记录页签首次激活时拉取时间线（交互 06 §2.2.2） */
function onSideTabChange(name: TabPaneName): void {
  if (name === 'logs') ensureLogs()
}

// ==================== 描述编辑器高度自适应（同新建页） ====================
const MIN_EDITOR_HEIGHT = 320
/** 编辑器取剩余可用空间的 90%，底部保留回弹余量（交互 06 §2.5.2） */
const EDITOR_HEIGHT_RATIO = 0.9

const editorWrap = ref<HTMLDivElement>()
const editorTop = ref(0)
const viewportHeight = ref(typeof window === 'undefined' ? 0 : window.innerHeight)

/** 编辑器顶部相对视口的偏移，随布局（属性字段行数、进入编辑态）变化需重新测量 */
function measure(): void {
  editorTop.value = editorWrap.value?.getBoundingClientRect().top ?? 0
}

const editorHeight = computed(() =>
  Math.max(
    MIN_EDITOR_HEIGHT,
    Math.floor((viewportHeight.value - editorTop.value) * EDITOR_HEIGHT_RATIO),
  ),
)

function handleResize(): void {
  viewportHeight.value = window.innerHeight
  measure()
}

// 编辑器节点仅在编辑态渲染，进入后补测一次高度基准
watch(editing, (on) => {
  if (on) void nextTick(measure)
})

onMounted(() => {
  void nextTick(handleResize)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => window.removeEventListener('resize', handleResize))

const router = useRouter()

function goBack(): void {
  void router.push('/workspace/projects/requirements')
}

/** 切至需求管理页内「追溯矩阵」子页并打开本需求的链路抽屉（交互 04 §2.2 入口） */
function openTrace(): void {
  if (!detail.value) return
  void router.push({
    path: '/workspace/projects/requirements',
    query: { tab: 'trace', requirementId: detail.value.id, title: `${detail.value.code} ${detail.value.title}` },
  })
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
      <!-- UI-BC-01：列表 → 详情两级面包屑，回退入口是唯一可点击项；操作区与面包屑同行（同新建页 2.5.2） -->
      <div class="requirement-detail__header">
        <div class="requirement-detail__breadcrumb">
          <router-link to="/workspace/projects/requirements">需求管理</router-link>
          <el-icon :size="13"><ArrowRight /></el-icon>
          <span>{{ detail?.title ?? '需求详情' }}</span>
        </div>
        <div class="requirement-detail__actions">
          <template v-if="editing">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!editDirty"
              @click="saveAll"
            >保存</el-button>
          </template>
          <template v-else>
            <el-button
              v-if="detail && canEdit && !isReadonly"
              type="primary"
              @click="startEdit"
            >编辑</el-button>
            <el-button v-if="canViewTrace" @click="openTrace">
              <el-icon><Grid /></el-icon>查看追溯
            </el-button>
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
            <el-tooltip
              v-if="canLaunchGeneration"
              :content="detail?.status === 'confirmed' ? '基于该需求生成测试设计' : '仅已确认需求可生成'"
              placement="bottom"
            >
              <span>
                <el-button
                  :disabled="detail?.status !== 'confirmed'"
                  @click="generationDialogVisible = true"
                >AI 生成测试设计</el-button>
              </span>
            </el-tooltip>
          </template>
        </div>
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
          <div class="requirement-detail__title-row">
            <span class="requirement-detail__code">{{ detail.code }}</span>
            <el-input
              v-if="editing"
              v-model="titleDraft"
              class="requirement-detail__title-input"
              maxlength="300"
              show-word-limit
              placeholder="请输入需求标题"
            />
            <h1 v-else class="requirement-detail__title">{{ detail.title }}</h1>
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

          <!-- 属性栏在 DOM 中置前：窄屏回流保持「标题 → 属性 → 描述」顺序（同新建页）；桌面端由 grid 定位到右栏 -->
          <div class="requirement-detail__layout">
            <el-card shadow="never" class="requirement-detail__side">
              <el-tabs v-model="sideTab" @tab-change="onSideTabChange">
                <el-tab-pane label="属性" name="attributes">
                  <!-- 查看态：label 在上、值在下的只读文本，字段节奏与新建页一致 -->
                  <dl v-if="!editing" class="requirement-detail__attrs">
                    <div class="requirement-detail__attr">
                      <dt>所属模块</dt>
                      <dd>{{ detail.moduleName ?? '—' }}</dd>
                    </div>
                    <div class="requirement-detail__attr">
                      <dt>版本</dt>
                      <dd>{{ detail.systemVersion ?? '—' }}</dd>
                    </div>
                    <div class="requirement-detail__attr">
                      <dt>负责人</dt>
                      <dd>{{ detail.ownerName ?? '—' }}</dd>
                    </div>
                    <div class="requirement-detail__attr">
                      <dt>优先级</dt>
                      <dd>{{ requirementPriorityLabel(detail.priority) }}</dd>
                    </div>
                    <div class="requirement-detail__attr">
                      <dt>标签</dt>
                      <dd>{{ detail.tags && detail.tags.length ? detail.tags.join('、') : '—' }}</dd>
                    </div>
                    <div class="requirement-detail__attr">
                      <dt>来源</dt>
                      <dd>{{ requirementSourceLabel(detail.source) }}</dd>
                    </div>
                    <div class="requirement-detail__attr">
                      <dt>覆盖状态</dt>
                      <dd>
                        <el-tag v-if="coverageMeta" :type="coverageMeta.tagType" size="small" effect="light">
                          {{ coverageMeta.label }}
                        </el-tag>
                        <span v-else>—</span>
                      </dd>
                    </div>
                  </dl>

                  <!-- 编辑态：字段与新建页同款；模块/负责人后端仅支持改为非空值，不提供清空 -->
                  <el-form v-else label-position="top" @submit.prevent>
                    <div class="requirement-detail__fields">
                      <el-form-item label="所属模块">
                        <!-- ProjectModule 文本字段为 name，须显式映射 label，否则选中值无法回显模块名 -->
                        <el-tree-select
                          v-model="form.moduleId"
                          :data="moduleTree"
                          :props="{ label: 'name', children: 'children' }"
                          node-key="id"
                          check-strictly
                          filterable
                          placeholder="未分配"
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
                      <el-form-item label="优先级">
                        <el-radio-group v-model="form.priority">
                          <el-radio-button
                            v-for="option in REQUIREMENT_PRIORITY_OPTIONS"
                            :key="option.value"
                            :value="option.value"
                            :style="priorityButtonStyle(option.value)"
                            @click.capture="handlePriorityClick(option.value, $event)"
                          >{{ option.label }}</el-radio-button>
                        </el-radio-group>
                      </el-form-item>
                      <el-form-item label="负责人">
                        <el-select
                          v-model="form.ownerId"
                          filterable
                          placeholder="未指派"
                        >
                          <el-option
                            v-for="member in memberOptions"
                            :key="member.userId"
                            :value="member.userId"
                            :label="member.name || member.username"
                          />
                        </el-select>
                      </el-form-item>
                      <el-form-item label="标签" class="requirement-detail__tags">
                        <el-select
                          v-model="form.tags"
                          multiple
                          filterable
                          allow-create
                          default-first-option
                          clearable
                          placeholder="回车创建"
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
                    </div>
                  </el-form>
                </el-tab-pane>

                <el-tab-pane name="logs">
                  <template #label>变更记录</template>
                  <span v-if="logsTotal > 0" class="requirement-detail__muted">
                    共 {{ logsTotal }} 条
                  </span>
                  <el-timeline
                    v-if="logs.length"
                    v-loading="logsLoading"
                    class="requirement-detail__timeline"
                  >
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
                  <el-empty
                    v-else-if="!logsLoading"
                    description="暂无变更记录"
                    :image-size="48"
                  />
                  <div v-if="canLoadMoreLogs" class="requirement-detail__log-more">
                    <el-button size="small" :loading="logsLoading" @click="loadLogs">加载更多</el-button>
                  </div>
                </el-tab-pane>
              </el-tabs>
            </el-card>

            <div class="requirement-detail__main">
              <!-- 两态共用同一外部容器高度（取编辑器自适应高度），切换模式不产生高度跳动 -->
              <div
                ref="editorWrap"
                class="requirement-detail__description"
                :style="{ height: `${editorHeight}px` }"
              >
                <template v-if="editing">
                  <MarkdownEditor
                    v-model="descriptionDraft"
                    :height="`${editorHeight}px`"
                    maximizable
                    placeholder="需求描述（支持 Markdown）"
                  />
                </template>
                <template v-else>
                  <MarkdownView v-if="detail.description" :content="detail.description" />
                  <el-empty v-else description="暂无描述" :image-size="48" />
                </template>
              </div>

              <!-- 来源附件：文件管理模块落地前 sourceFile 恒为 null，不渲染附件区 -->
              <el-card v-if="detail.sourceFile" shadow="never">
                <template #header><span class="requirement-detail__section">来源附件</span></template>
                <span>{{ detail.sourceFile.name }}</span>
              </el-card>
            </div>
          </div>
        </template>
      </div>

      <GenerationConfigDialog
        v-model="generationDialogVisible"
        :requirements="generationScope"
        :module-tree="moduleTree"
        @stale="load"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
.requirement-detail {
  padding-bottom: var(--space-lg);
}

.requirement-detail__missing {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

// UI-BC-02：面包屑容器排版基准（同新建页）
.requirement-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;

  a {
    color: var(--color-primary-500);
    font-weight: 500;
    text-decoration: none;
    flex-shrink: 0;
  }

  a:hover {
    color: var(--color-primary-600);
  }

  .el-icon {
    color: var(--color-neutral-400);
    flex-shrink: 0;
  }

  > :last-child {
    color: var(--color-neutral-900);
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// 负偏移贴住滚动容器内容盒上缘：滚动容器带 16px padding，top:0 会留下内容穿行的白带（同新建页）
.requirement-detail__header {
  position: sticky;
  top: calc(-1 * var(--page-pad));
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-md);
  background: var(--color-neutral-0);
  border-bottom: 1px solid var(--color-neutral-200);
}

.requirement-detail__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.requirement-detail__body {
  margin-top: var(--space-md);
}

.requirement-detail__error {
  margin-bottom: var(--space-md);
}

.requirement-detail__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-xl);
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
  flex: 1;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: var(--font-size-xl);
  font-weight: 650;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.requirement-detail__title-input {
  flex: 1;
  min-width: 0;
}

// 编辑态标题无状态徽标占位也保持分区间距（同新建页 --space-xl）
.requirement-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--space-xl);
  // 默认 stretch：右栏拉伸至与左栏等高（同新建页）
}

.requirement-detail__main {
  min-width: 0;
  grid-column: 1;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.requirement-detail__side {
  min-width: 0;
  grid-column: 2;
  grid-row: 1;
  // 高度随左栏描述容器拉伸（两态高度同源，参考编辑态），卡片内部改由 flex 承接
  height: 100%;

  // 顶部留白归零：tabs 行与左栏编辑器工具栏同一起始线（工具栏 35px + 1px 下边框 → tabs 头 36px）
  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding-top: 0;
    overflow: hidden;
  }

  :deep(.el-tabs) {
    --el-tabs-header-height: 36px;
    flex: 1;
    min-height: 0;
  }

  :deep(.el-tabs__header) {
    margin-bottom: var(--space-xs);
  }

  // 页签容器高度固定，内容超出在卡片内滚动
  :deep(.el-tabs__content) {
    min-height: 0;
    overflow: auto;
  }

  // 表单项自带底距已由 flex gap 承担，否则间距翻倍（同新建页）
  :deep(.el-form-item) {
    --el-form-item-margin-bottom: 0;
  }

  :deep(.el-select),
  :deep(.el-tree-select) {
    width: 100%;
  }
}

.requirement-detail__fields {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

// 高度由父级行内样式锁定为编辑器自适应高度；只读态超长内容在容器内滚动
.requirement-detail__description {
  width: 100%;
  overflow: auto;
}

// 空态撑满固定高度容器居中展示
.requirement-detail__description :deep(.el-empty) {
  height: 100%;
  padding: 0;
}

.requirement-detail__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
}

.requirement-detail__muted {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

// 查看态属性：label 在上、值在下，字段间 --space-md 对齐新建页节奏
.requirement-detail__attrs {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  margin: 0;
}

.requirement-detail__attr {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);

  dt {
    color: var(--color-neutral-600);
    font-size: var(--font-size-base);
    line-height: 1.4;
  }

  dd {
    margin: 0;
    color: var(--color-neutral-800);
    font-size: var(--font-size-sm);
    word-break: break-all;
  }
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

.status-tag--archived {
  --el-tag-text-color: var(--color-neutral-400);
  --el-tag-bg-color: var(--color-neutral-50);
  --el-tag-border-color: var(--color-neutral-300);
}

@media (max-width: 1024px) {
  .requirement-detail__layout {
    grid-template-columns: 1fr;
  }

  .requirement-detail__main,
  .requirement-detail__side {
    grid-column: 1;
    grid-row: auto;
  }

  // 回流后右栏独立成行，卡片高度回落为内容高（同新建页）
  .requirement-detail__side {
    height: auto;
  }

  // 查看态属性回流为 2 行（同新建页断点口径）
  .requirement-detail__attrs,
  .requirement-detail__fields {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-md);
  }

  // 标签多选需要横向空间，窄屏跨满整行（同新建页）
  .requirement-detail__tags {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .requirement-detail__attrs,
  .requirement-detail__fields {
    grid-template-columns: 1fr;
  }
}
</style>
