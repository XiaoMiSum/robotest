<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import {
  REQUIREMENT_PRIORITY_OPTIONS,
  useRequirementCreate,
} from '@/composables/project/requirement/useRequirementCreate'

const {
  router,
  formRef,
  submitting,
  form,
  rules,
  moduleTree,
  memberOptions,
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
    <!-- UI-BC-01：列表 → 新建两级面包屑，回退入口是唯一可点击项；操作区与面包屑同行右对齐 -->
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
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="form.title"
          maxlength="300"
          show-word-limit
          placeholder="请输入需求标题"
          size="large"
        />
      </el-form-item>
      <div class="requirement-create__row">
        <el-form-item label="所属模块">
          <el-tree-select
            v-model="form.moduleId"
            :data="moduleTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item label="版本" prop="systemVersion">
          <el-input v-model="form.systemVersion" maxlength="50" show-word-limit placeholder="可选" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" clearable placeholder="可选">
            <el-option
              v-for="option in REQUIREMENT_PRIORITY_OPTIONS"
              :key="option.value"
              v-bind="option"
            />
          </el-select>
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
      </div>
      <el-form-item label="标签">
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

      <!-- 无 label：编辑区撑满剩余视口高度，聚焦内容本身 -->
      <el-form-item label-width="0" class="requirement-create__desc">
        <div ref="editorWrap" class="requirement-create__editor">
          <MarkdownEditor
            v-model="form.description"
            :height="`${editorHeight}px`"
            maximizable
            placeholder="需求描述（支持 Markdown，可选）"
          />
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
// UI-BC-02：面包屑容器排版基准
.requirement-create__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-sm);
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

.requirement-create__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
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

.requirement-create__form :deep(.el-select),
.requirement-create__form :deep(.el-tree-select) {
  width: 100%;
}

.requirement-create__row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-sm);

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

// 无 label 的描述项：去掉表单项自身底距，由编辑器撑满剩余视口高度
.requirement-create__desc {
  margin-bottom: 0;
}

.requirement-create__editor {
  width: 100%;
}
</style>
