<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

export interface RowAction {
  key: string
  label: string
  form?: 'PLsm' | 'DLsm' | 'DGsm'
  disabled?: boolean
  loading?: boolean
  visible?: boolean
}

const props = withDefaults(
  defineProps<{
    actions: RowAction[]
    max?: number
  }>(),
  { max: 3 },
)

const emit = defineEmits<{ action: [key: string] }>()

const shown = computed(() => props.actions.filter((action) => action.visible !== false))
const inline = computed(() => shown.value.slice(0, props.max))
const overflow = computed(() => shown.value.slice(props.max))

function formAttrs(form: RowAction['form']): Record<string, string | boolean> {
  if (form === 'DLsm') return { link: true, size: 'small' }
  if (form === 'DGsm') return { link: true, type: 'danger', size: 'small' }
  return { link: true, type: 'primary', size: 'small' }
}
</script>

<template>
  <div class="row-actions">
    <el-button
      v-for="action in inline"
      :key="action.key"
      v-bind="formAttrs(action.form)"
      :disabled="action.disabled"
      :loading="action.loading"
      @click="emit('action', action.key)"
    >{{ action.label }}</el-button>
    <el-dropdown v-if="overflow.length" :teleported="false" trigger="click">
      <el-button link size="small" class="row-actions__more">
        更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-for="action in overflow" :key="action.key" :disabled="action.disabled">
            <el-button
              link
              size="small"
              :type="action.form === 'DGsm' ? 'danger' : action.form === 'PLsm' ? 'primary' : undefined"
              @click.stop="emit('action', action.key)"
            >{{ action.label }}</el-button>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped lang="scss">
.row-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}
</style>
