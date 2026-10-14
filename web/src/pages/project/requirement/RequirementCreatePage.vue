<script setup lang="ts">
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
</script>

<template>
  <div class="requirement-create">
    <el-page-header @back="router.push('/workspace/projects/requirements')">
      <template #content><span class="requirement-create__title">新建需求</span></template>
    </el-page-header>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="requirement-create__form"
    >
      <el-card shadow="never">
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            maxlength="300"
            show-word-limit
            placeholder="请输入需求标题"
            size="large"
          />
        </el-form-item>
        <el-form-item label="描述">
          <MarkdownEditor
            v-model="form.description"
            height="260px"
            placeholder="需求描述（支持 Markdown，可选）"
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
      </el-card>

      <div class="requirement-create__footer">
        <el-button @click="router.push('/workspace/projects/requirements')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">创建</el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.requirement-create__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.requirement-create__form {
  margin-top: var(--space-lg);
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

.requirement-create__footer {
  display: flex;
  justify-content: center;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
}
</style>
