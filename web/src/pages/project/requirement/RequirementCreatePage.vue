<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import {
  REQUIREMENT_PRIORITY_COLOR,
  REQUIREMENT_PRIORITY_OPTIONS,
  useRequirementCreate,
} from '@/composables/project/requirement/useRequirementCreate'
import type { RequirementPriority } from '@/types'

const {
  router,
  formRef,
  submitting,
  form,
  rules,
  moduleTree,
  memberOptions,
  handlePriorityClick,
  handleSubmit,
} = useRequirementCreate()

// ==================== 描述编辑器高度自适应 ====================
const MIN_EDITOR_HEIGHT = 320
const EDITOR_BOTTOM_GAP = 24

const editorWrap = ref<HTMLDivElement>()
const editorTop = ref(0)
const viewportHeight = ref(typeof window === 'undefined' ? 0 : window.innerHeight)

/** 编辑器顶部相对视口的偏移，随布局（属性字段行数）变化需重新测量 */
function measure(): void {
  editorTop.value = editorWrap.value?.getBoundingClientRect().top ?? 0
}

const editorHeight = computed(() =>
  Math.max(MIN_EDITOR_HEIGHT, viewportHeight.value - editorTop.value - EDITOR_BOTTOM_GAP),
)

/** 分段控件选中态用优先级色（交互 06 §4），EP 无逐按钮配色 prop，按规范走组件 CSS 变量注入 */
function priorityButtonStyle(value: RequirementPriority): Record<string, string> {
  const color = REQUIREMENT_PRIORITY_COLOR[value]
  return {
    '--el-radio-button-checked-bg-color': color,
    '--el-radio-button-checked-border-color': color,
  }
}

function handleResize(): void {
  viewportHeight.value = window.innerHeight
  measure()
}

onMounted(() => {
  void nextTick(handleResize)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => window.removeEventListener('resize', handleResize))
</script>

<template>
  <div class="requirement-create">
    <!-- UI-BC-01：两级面包屑，回退入口是唯一可点击项；操作区同行右对齐，页头吸顶 -->
    <div class="requirement-create__header">
      <div class="requirement-create__breadcrumb">
        <router-link to="/workspace/projects/requirements">需求管理</router-link>
        <el-icon :size="13"><ArrowRight /></el-icon>
        <span>新建需求</span>
      </div>
      <div class="requirement-create__actions">
        <el-button @click="router.push('/workspace/projects/requirements')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">创建</el-button>
      </div>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="requirement-create__form"
    >
      <!-- 无 label：必填由失焦 / 提交校验提示表达，不显示星号（交互 06 §2.5.2） -->
      <el-form-item prop="title" class="requirement-create__title">
        <el-input
          v-model="form.title"
          maxlength="300"
          show-word-limit
          placeholder="请输入需求标题"
        />
      </el-form-item>

      <div class="requirement-create__layout">
        <!-- 属性栏在 DOM 中置前：窄屏回流保持「标题 → 属性 → 描述」顺序；桌面端由 grid 显式定位到右栏 -->
        <div class="requirement-create__side">
          <!-- 无投影卡片容器包裹属性字段（交互 06 §2.5.2），与缺陷域新建页范式一致；等高齐平左栏编辑器 -->
          <el-card shadow="never" class="requirement-create__side-card">
            <div class="requirement-create__fields">
              <el-form-item label="所属模块">
                <el-tree-select
                  v-model="form.moduleId"
                  :data="moduleTree"
                  :props="{ label: 'name', children: 'children' }"
                  node-key="id"
                  check-strictly
                  filterable
                  clearable
                  placeholder="可选"
                />
              </el-form-item>
              <el-form-item label="版本">
                <el-input v-model="form.systemVersion" maxlength="50" show-word-limit placeholder="可选" />
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
                <el-select v-model="form.ownerId" clearable filterable placeholder="可选">
                  <el-option
                    v-for="member in memberOptions"
                    :key="member.userId"
                    :value="member.userId"
                    :label="member.name || member.username"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="标签" class="requirement-create__tags">
                <el-select
                  v-model="form.tags"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  placeholder="可选，回车创建"
                />
              </el-form-item>
            </div>
          </el-card>
        </div>

        <div class="requirement-create__main">
          <!-- 无 label：编辑区撑满左栏，聚焦内容本身 -->
          <div ref="editorWrap" class="requirement-create__editor">
            <MarkdownEditor
              v-model="form.description"
              :height="`${editorHeight}px`"
              maximizable
              placeholder="需求描述（支持 Markdown，可选）"
            />
          </div>
        </div>
      </div>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
// UI-BC-02：面包屑容器排版基准
.requirement-create__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);

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

// 负偏移贴住滚动容器内容盒上缘：滚动容器带 16px padding，top:0 会留下内容穿行的白带
.requirement-create__header {
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

.requirement-create__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.requirement-create__form {
  margin-top: var(--space-md);
}

// 无 label 的标题项：底距即分区间距，同时给绝对定位的错误文案留出固定占位
.requirement-create__title {
  --el-form-item-margin-bottom: var(--space-xl);
}

.requirement-create__form :deep(.el-select),
.requirement-create__form :deep(.el-tree-select) {
  width: 100%;
}

.requirement-create__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--space-xl);
  // 默认 stretch：右栏拉伸至与左栏编辑器等高（交互 06 §2.5.2），等高后页面无滚动量
}

.requirement-create__main {
  min-width: 0;
  grid-column: 1;
  grid-row: 1;
}

.requirement-create__side {
  min-width: 0;
  grid-column: 2;
  grid-row: 1;
}

// 卡片容器内距沿用 EP 默认，仅重排字段（交互 06 §2.5.2）；高度撑满右栏与编辑器齐平
.requirement-create__side-card {
  height: 100%;
}

// 卡片容器内距沿用 EP 默认，仅重排字段（交互 06 §2.5.2）
.requirement-create__fields {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

// 表单项自带底距已由 flex gap 承担，否则间距翻倍
.requirement-create__side :deep(.el-form-item) {
  --el-form-item-margin-bottom: 0;
}

.requirement-create__editor {
  width: 100%;
}

@media (max-width: 1024px) {
  .requirement-create__layout {
    grid-template-columns: 1fr;
  }

  .requirement-create__main,
  .requirement-create__side {
    grid-column: 1;
    grid-row: auto;
  }

  // 回流后右栏独立成行，卡片高度回落为内容高
  .requirement-create__side-card {
    height: auto;
  }

  .requirement-create__fields {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-md);
  }

  // 标签多选需要横向空间，窄屏跨满整行
  .requirement-create__tags {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .requirement-create__fields {
    grid-template-columns: 1fr;
  }
}
</style>
