<script setup lang="ts">
import type { ApiDataSource, ApiHttpConfig } from '@/types'
import {
  COMPONENT_SCOPE_OPTIONS,
  COMPONENT_TYPE_OPTIONS,
  type ComponentFormData,
} from '@/composables/project/api-testing/component/componentModel'
import ProcessorConfigEditor from '@/components/project/api-testing/ProcessorConfigEditor.vue'
import ValidatorForm from '@/components/project/api-testing/ValidatorForm.vue'
import ExtractorForm from '@/components/project/api-testing/ExtractorForm.vue'

defineProps<{
  form: ComponentFormData
  /** true = 编辑态（类型禁用、不展示作用域）；false = 新建态 */
  editing: boolean
  saving: boolean
  httpOptions: ApiHttpConfig[]
  dsOptions: ApiDataSource[]
}>()

const emit = defineEmits<{
  (e: 'save'): void
  (e: 'cancel'): void
  (e: 'import-extractors'): void
}>()
</script>

<template>
  <div class="cp-detail__header">
    <div class="cp-detail__id">
      <h4 class="cp-detail__name">{{ editing ? '编辑组件' : '新建组件' }}</h4>
    </div>
    <div class="cp-detail__header-actions">
      <el-button size="small" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" size="small" :loading="saving" @click="emit('save')">保存</el-button>
    </div>
  </div>

  <el-form label-position="top" class="cp-form" @submit.prevent>
    <div class="cp-form__grid">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" maxlength="100" placeholder="如：Token 预置" />
      </el-form-item>
      <el-form-item label="类型" required>
        <el-select v-model="form.type" :disabled="editing" class="cp-form__control">
          <el-option
            v-for="opt in COMPONENT_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <p v-if="editing" class="cp-form__hint">编辑态类型不可修改</p>
      <el-form-item v-if="!editing" label="作用域" required>
        <el-select v-model="form.scope" class="cp-form__control">
          <el-option
            v-for="opt in COMPONENT_SCOPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <p v-if="!editing" class="cp-form__hint">编辑态不可修改作用域</p>
      <el-form-item label="描述" class="cp-form__desc">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="500"
          placeholder="组件用途说明"
        />
      </el-form-item>
    </div>

    <span class="cp-form__label">配置（随类型切换）</span>

    <ProcessorConfigEditor
      v-if="form.type === 'preprocessor' || form.type === 'postprocessor'"
      v-model="form.config"
      :http-options="httpOptions"
      :ds-options="dsOptions"
      @import-extractors="emit('import-extractors')"
    />
    <ValidatorForm v-else-if="form.type === 'validator'" v-model="form.config" />
    <ExtractorForm v-else v-model="form.config" />
  </el-form>
</template>
