<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AiSettingSchemaGroup, AiSettingSchemaItem } from '@/types'
import {
  filterSettingGroups,
  isSettingModified,
  settingsStats,
  weightsSum,
} from '@/composables/admin/aiConfigForm'

const props = defineProps<{
  groups: AiSettingSchemaGroup[]
  form: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'reset', item: AiSettingSchemaItem): void
}>()

// 检索与仅看已修改（demo 先行交互，已备案）
const search = ref('')
const modifiedOnly = ref(false)
const filtering = computed(() => !!search.value.trim() || modifiedOnly.value)

const stats = computed(() => settingsStats(props.groups, props.form))
const visibleGroups = computed(() =>
  filterSettingGroups(props.groups, props.form, {
    query: search.value,
    modifiedOnly: modifiedOnly.value,
  }),
)

// 已修改分组默认展开（demo .set-group--modified），其余默认收起
const modifiedKeys = computed(() => {
  const keys = new Set<string>()
  for (const group of props.groups) {
    if (group.items.some((item) => isSettingModified(item, props.form[item.key]))) {
      keys.add(group.group)
    }
  }
  return keys
})

// 显式开合覆盖：未覆盖时回落到「已修改默认展开」推导
const openOverride = ref<Record<string, boolean>>({})
// 过滤自动展开的分组键，清除过滤时据此收回（已修改组保持现状）
const autoOpenedKeys: string[] = []

function isOpen(group: AiSettingSchemaGroup): boolean {
  const override = openOverride.value[group.group]
  return override !== undefined ? override : modifiedKeys.value.has(group.group)
}

function toggleGroup(group: AiSettingSchemaGroup): void {
  openOverride.value = { ...openOverride.value, [group.group]: !isOpen(group) }
}

function applyFilter(): void {
  if (filtering.value) {
    const next = { ...openOverride.value }
    for (const group of visibleGroups.value) {
      if (!isOpen(group)) {
        next[group.group] = true
        if (!autoOpenedKeys.includes(group.group)) autoOpenedKeys.push(group.group)
      }
    }
    openOverride.value = next
  } else {
    const next = { ...openOverride.value }
    for (const key of autoOpenedKeys) {
      // 已修改组保持现状（demo set-group--modified 不收起），其余收回
      if (!modifiedKeys.value.has(key)) next[key] = false
    }
    autoOpenedKeys.length = 0
    openOverride.value = next
  }
}

watch([search, modifiedOnly], applyFilter)

function settingModified(item: AiSettingSchemaItem, form: Record<string, unknown>): boolean {
  return isSettingModified(item, form[item.key])
}

function currentWeightsSum(item: AiSettingSchemaItem, form: Record<string, unknown>): number {
  return weightsSum(form[item.key])
}

// 网格内占整行的项：权重组合（object）恒整行；组内奇数项时最后一项补整行避免右侧留空
function settingItemIsFull(item: AiSettingSchemaItem, index: number, total: number): boolean {
  return item.type === 'object' || (index === total - 1 && total % 2 === 1)
}
</script>

<template>
  <el-card shadow="never" class="ai-settings-section">
    <template #header>
      <div class="ai-settings-section__header">
        <div class="ai-settings-section__heading">
          <el-icon class="ai-settings-section__icon"><Setting /></el-icon>
          <span class="ai-settings-section__title">
            系统配置项
            <span class="ai-settings-section__subtitle">{{ stats.total }} 项 · 修改即自动保存</span>
          </span>
        </div>
        <div class="ai-settings-section__tools">
          <el-input
            v-model="search"
            class="ai-settings-section__search"
            placeholder="搜索配置项"
            @keydown.esc="search = ''"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <button
            type="button"
            class="ai-settings-section__filter"
            :class="{ 'is-active': modifiedOnly }"
            :aria-pressed="modifiedOnly"
            @click="modifiedOnly = !modifiedOnly"
          >
            仅看已修改
            <span class="ai-settings-section__filter-count"
              >{{ stats.modified }}/{{ stats.total }}</span
            >
          </button>
        </div>
      </div>
    </template>

    <div
      v-for="group in visibleGroups"
      :key="group.group"
      class="ai-settings-section__group"
      :class="{ 'is-open': isOpen(group) }"
    >
      <div class="ai-settings-section__group-head" @click="toggleGroup(group)">
        <span class="ai-settings-section__group-name">{{ group.groupLabel }}</span>
        <span class="ai-settings-section__group-count">{{ group.items.length }}</span>
        <span class="ai-settings-section__group-end">
          <el-tag
            v-if="modifiedKeys.has(group.group)"
            class="ai-settings-section__group-modified"
            size="small"
            type="warning"
            effect="light"
          >
            <span class="ai-settings-section__dot" />已修改
          </el-tag>
          <el-icon class="ai-settings-section__chevron" :class="{ 'is-open': isOpen(group) }">
            <ArrowDown />
          </el-icon>
        </span>
      </div>
      <div class="ai-settings-section__grid">
        <el-form-item
          v-for="(item, index) in group.items"
          :key="item.key"
          :label="item.label"
          label-position="top"
          :class="{
            'ai-settings-section__item--full': settingItemIsFull(item, index, group.items.length),
            'is-modified': settingModified(item, form),
          }"
        >
          <div class="ai-settings-section__control">
            <!-- 权重组合 -->
            <template v-if="item.type === 'object'">
              <div v-if="form[item.key]" class="ai-settings-section__weights">
                <el-input-number
                  v-for="sub in ['w1', 'w2', 'w3']"
                  :key="sub"
                  v-model="(form[item.key] as Record<string, number>)[sub]"
                  :min="0"
                  :max="1"
                  :step="0.1"
                  :controls="false"
                />
                <span
                  class="ai-settings-section__weights-sum"
                  :class="{ 'is-error': Math.abs(currentWeightsSum(item, form) - 1) > 0.001 }"
                >
                  Σ {{ currentWeightsSum(item, form).toFixed(2) }}
                </span>
              </div>
            </template>
            <!-- 多选 -->
            <el-select
              v-else-if="item.type === 'string[]'"
              v-model="form[item.key] as string[]"
              multiple
              class="ai-settings-section__multi"
            >
              <el-option v-for="opt in item.options ?? []" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <!-- 数字 -->
            <el-input-number
              v-else
              v-model="form[item.key] as number"
              class="ai-settings-section__number"
              :min="item.min ?? undefined"
              :max="item.max ?? undefined"
              :step="item.step ?? 1"
            />
            <span v-if="settingModified(item, form)" class="ai-settings-section__modified">
              <el-tag size="small" type="warning" effect="light"
                ><span class="ai-settings-section__dot" />已修改</el-tag
              >
              <el-button link type="primary" size="small" @click="emit('reset', item)"
                >恢复默认</el-button
              >
            </span>
          </div>
          <span class="ai-settings-section__hint"
            >{{ item.description }}（默认 {{ item.defaultValue }}）</span
          >
        </el-form-item>
      </div>
    </div>

    <div v-if="groups.length && !visibleGroups.length" class="ai-settings-section__empty">
      无匹配的配置项
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.ai-settings-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.ai-settings-section__heading {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.ai-settings-section__icon {
  color: var(--color-primary-500);
  font-size: 16px;
}

.ai-settings-section__title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.ai-settings-section__subtitle {
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-neutral-500);
  margin-left: 8px;
}

.ai-settings-section__tools {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ai-settings-section__search {
  width: 220px;
}

.ai-settings-section__filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--color-neutral-300);
  border-radius: 999px;
  background: var(--color-neutral-0);
  color: var(--color-neutral-700);
  font-family: inherit;
  font-size: var(--font-size-sm);
  cursor: pointer;
  white-space: nowrap;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);

  &:hover {
    background: var(--color-neutral-50);
  }

  /* 蓝仅表达交互：过滤激活态即交互态 */
  &.is-active {
    background: var(--color-primary-50);
    border-color: var(--color-primary-200);
    color: var(--color-primary-600);
  }
}

.ai-settings-section__filter-count {
  font-size: var(--font-size-2xs);
  font-weight: 600;
  color: var(--color-neutral-500);
  background: var(--color-neutral-100);
  border-radius: 999px;
  padding: 1px 7px;
}

.ai-settings-section__filter.is-active .ai-settings-section__filter-count {
  color: var(--color-primary-600);
  background: var(--color-primary-100);
}

/* 分组折叠：箭头右置、展开朝下（demo .set-group，头行即折叠面） */
.ai-settings-section__group + .ai-settings-section__group {
  border-top: 1px solid var(--color-neutral-100);
}

.ai-settings-section__group-head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-800);
  cursor: pointer;
  user-select: none;

  &:hover {
    background: var(--color-neutral-50);
  }
}

.ai-settings-section__group-count {
  font-size: var(--font-size-2xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  background: var(--color-neutral-100);
  border-radius: 999px;
  padding: 1px 7px;
}

.ai-settings-section__group-end {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
}

.ai-settings-section__dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 4px;
}

.ai-settings-section__chevron {
  display: inline-flex;
  color: var(--color-neutral-400);
  font-size: 14px;
  transform: rotate(-90deg);
  transition: transform var(--transition-fast);

  &.is-open {
    transform: rotate(0);
  }
}

.ai-settings-section__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--space-xl);
  padding: 4px 12px 20px;
}

.ai-settings-section__item--full {
  grid-column: 1 / -1;
}

.ai-settings-section__empty {
  padding: 40px 0;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-400);
}

/* 读态降噪：未修改控件去框成行，hover/聚焦回框；已修改项常显框体（demo .field:not(.is-modified)） */
.ai-settings-section__item:not(.is-modified) {
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    box-shadow: none;
    background-color: transparent;
  }

  /* EP 数字增减按钮为绝对定位填充底，须与 wrapper 同口径降噪 */
  :deep(.el-input-number__decrease),
  :deep(.el-input-number__increase) {
    border-color: transparent;
    background-color: transparent;
  }

  &:hover {
    :deep(.el-input__wrapper),
    :deep(.el-select__wrapper) {
      box-shadow: inset 0 0 0 1px var(--color-neutral-300);
      background-color: var(--color-neutral-0);
    }

    :deep(.el-input-number__decrease),
    :deep(.el-input-number__increase) {
      border-color: var(--color-neutral-300);
      background-color: var(--color-neutral-0);
    }
  }

  /* 聚焦回品牌框（声明在 hover 后，同特异性下聚焦优先） */
  :deep(.el-input__wrapper:focus-within),
  :deep(.el-select__wrapper:focus-within) {
    box-shadow: inset 0 0 0 1px var(--color-primary-500);
    background-color: var(--color-neutral-0);
  }
}

.ai-settings-section__control {
  width: 100%;
}

.ai-settings-section__weights {
  display: flex;
  align-items: center;
  gap: var(--space-sm);

  :deep(.el-input-number) {
    width: 90px;
  }
}

.ai-settings-section__weights-sum {
  font-size: 12px;
  color: var(--color-neutral-500);
  margin-left: var(--space-sm);

  &.is-error {
    color: var(--color-danger);
  }
}

.ai-settings-section__number {
  width: 100%;
}

.ai-settings-section__multi {
  width: 100%;
}

.ai-settings-section__modified {
  display: inline-flex;
  align-items: center;
  margin-top: var(--space-sm);
  gap: var(--space-sm);
}

.ai-settings-section__hint {
  color: var(--color-neutral-400);
  font-size: 12px;
  line-height: 1.5;
}
</style>
