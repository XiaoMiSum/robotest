<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  VALIDATOR_TARGETS,
  VALIDATOR_CONDITIONS,
  EXTRACTOR_SOURCES,
  type PaneValidatorItem,
  type PaneExtractorItem,
} from '../scenesModel'

// 断言 + 提取器 统一 tab 面板：供请求配置 tabs（http）与 jdbc 配置 tabs 复用同一编辑交互
// 与 KeyValueTable 同策略：本地编辑副本，变更后整表回传，父级负责序列化过滤
const props = defineProps<{
  validators?: PaneValidatorItem[]
  extractors?: PaneExtractorItem[]
}>()

const emit = defineEmits<{
  (e: 'update:validators', value: PaneValidatorItem[]): void
  (e: 'update:extractors', value: PaneExtractorItem[]): void
  (e: 'add-validator'): void
  (e: 'add-extractor'): void
  (e: 'import-validators'): void
  (e: 'import-extractors'): void
}>()

const editValidators = ref<PaneValidatorItem[]>(props.validators ? props.validators.map((row) => ({ ...row })) : [])
const editExtractors = ref<PaneExtractorItem[]>(props.extractors ? props.extractors.map((row) => ({ ...row })) : [])

// 父级整表回传时同步本地副本（值相等，仅实例刷新，不打断输入）
watch(
  () => props.validators,
  (v) => {
    editValidators.value = v ? v.map((row) => ({ ...row })) : []
  },
  { deep: true },
)
watch(
  () => props.extractors,
  (v) => {
    editExtractors.value = v ? v.map((row) => ({ ...row })) : []
  },
  { deep: true },
)

// 徽标计数与序列化过滤口径一致（target / source 有值即算一行）
const validatorCount = computed(() => editValidators.value.filter((row) => row.target.trim()).length)
const extractorCount = computed(() => editExtractors.value.filter((row) => row.source.trim()).length)

function pushValidators() {
  emit('update:validators', editValidators.value.map((row) => ({ ...row })))
}
function pushExtractors() {
  emit('update:extractors', editExtractors.value.map((row) => ({ ...row })))
}
function removeValidator(index: number) {
  editValidators.value.splice(index, 1)
  pushValidators()
}
function removeExtractor(index: number) {
  editExtractors.value.splice(index, 1)
  pushExtractors()
}
</script>

<template>
  <el-tab-pane v-if="validators !== undefined" name="validators">
    <template #label>
      <span class="pane-label">
        断言
        <span v-if="validatorCount" class="pane-label__badge">{{ validatorCount }}</span>
      </span>
    </template>
    <div class="pane-actions">
      <el-button size="small" link type="primary" @click="emit('add-validator')">+ 添加断言</el-button>
      <el-button size="small" link type="primary" @click="emit('import-validators')">从公共组件获取</el-button>
    </div>
    <div class="pane-list">
      <div v-for="(row, i) in editValidators" :key="i" class="pane-card">
        <div class="pane-card__row">
          <el-switch v-model="row.enabled" size="small" @change="pushValidators" />
          <el-select v-model="row.target" size="small" class="pane-field--target" placeholder="验证目标" @change="pushValidators">
            <el-option v-for="t in VALIDATOR_TARGETS" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
          <el-select v-model="row.condition" size="small" class="pane-field--condition" placeholder="比较条件" @change="pushValidators">
            <el-option v-for="c in VALIDATOR_CONDITIONS" :key="c.value" :value="c.value" :label="c.label" />
          </el-select>
          <el-input v-model="row.expression" size="small" placeholder="表达式（如 $.code）" class="pane-field--flex" @change="pushValidators" />
          <el-input v-model="row.expected" size="small" placeholder="期望值" class="pane-field--flex" @change="pushValidators" />
          <el-button link size="small" type="danger" @click="removeValidator(i)">删除</el-button>
        </div>
      </div>
    </div>
  </el-tab-pane>

  <el-tab-pane v-if="extractors !== undefined" name="extractors">
    <template #label>
      <span class="pane-label">
        提取器
        <span v-if="extractorCount" class="pane-label__badge">{{ extractorCount }}</span>
      </span>
    </template>
    <div class="pane-actions">
      <el-button size="small" link type="primary" @click="emit('add-extractor')">+ 添加提取器</el-button>
      <el-button size="small" link type="primary" @click="emit('import-extractors')">从公共组件获取</el-button>
    </div>
    <div class="pane-list">
      <div v-for="(row, i) in editExtractors" :key="i" class="pane-card">
        <div class="pane-card__row">
          <el-switch v-model="row.enabled" size="small" @change="pushExtractors" />
          <el-select v-model="row.source" size="small" class="pane-field--source" placeholder="提取来源" @change="pushExtractors">
            <el-option v-for="s in EXTRACTOR_SOURCES" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
          <el-input v-model="row.expression" size="small" placeholder="表达式" class="pane-field--flex" @change="pushExtractors" />
          <el-input v-model="row.variableName" size="small" placeholder="变量名" class="pane-field--flex" @change="pushExtractors" />
          <el-button link size="small" type="danger" @click="removeExtractor(i)">删除</el-button>
        </div>
      </div>
    </div>
  </el-tab-pane>
</template>

<style scoped lang="scss">
.pane-actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-sm);
}

.pane-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.pane-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
}

.pane-card__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: nowrap;
}

.pane-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.pane-label__badge {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-primary, #409eff);
  color: #fff;
  font-size: 10px;
  text-align: center;
}

.pane-field--flex {
  flex: 1 1 0;
  min-width: 0;
}

.pane-field--target {
  flex: 0 0 260px;
}

.pane-field--condition {
  flex: 0 0 150px;
}

.pane-field--source {
  flex: 0 0 240px;
}
</style>