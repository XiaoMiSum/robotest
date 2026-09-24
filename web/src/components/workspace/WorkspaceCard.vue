<script setup lang="ts">
import { computed } from 'vue'
import type { WorkspaceItem } from '@/types'
import { isWorkspaceAdmin, isWorkspaceArchived, workspaceRoleLabel } from '@/utils/workspaceRole'

const props = withDefaults(
  defineProps<{
    workspace: WorkspaceItem
    active?: boolean
  }>(),
  { active: false },
)

const emit = defineEmits<{
  enter: []
}>()

const archived = computed(() => isWorkspaceArchived(props.workspace.status))
const roleLabel = computed(() =>
  workspaceRoleLabel(props.workspace.workspaceRole, props.workspace.workspaceRoleName),
)
const adminRole = computed(() => isWorkspaceAdmin(props.workspace.workspaceRole))
const enterLabel = computed(() => `进入工作空间：${props.workspace.name}`)

function formatCount(value: number): string {
  return value.toLocaleString('en-US')
}
</script>

<template>
  <button
    v-if="!archived"
    type="button"
    class="workspace-card"
    :class="{ 'workspace-card--active': active }"
    :aria-label="enterLabel"
    :aria-current="active ? 'page' : undefined"
    @click="emit('enter')"
  >
    <span class="workspace-card__head">
      <span
        class="workspace-card__icon"
        :class="{ 'workspace-card__icon--admin': adminRole }"
        aria-hidden="true"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path
            d="M3.5 6.5A1.5 1.5 0 0 1 5 5h4l2 2.5h8A1.5 1.5 0 0 1 20.5 9v8.5A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5z"
          />
        </svg>
      </span>
      <span class="workspace-card__badges">
        <span
          class="workspace-card__role"
          :class="{ 'workspace-card__role--admin': adminRole }"
        >
          {{ roleLabel }}
        </span>
        <span v-if="active" class="workspace-card__current">当前空间</span>
      </span>
    </span>
    <span class="workspace-card__name" :title="workspace.name">{{ workspace.name }}</span>
    <span class="workspace-card__desc" :title="workspace.description || '暂无描述'">
      {{ workspace.description || '暂无描述' }}
    </span>
    <span class="workspace-card__stats" aria-label="工作空间统计">
      <span
        ><strong>{{ formatCount(workspace.memberCount) }}</strong
        >成员</span
      >
      <span
        ><strong>{{ formatCount(workspace.projectCount) }}</strong
        >项目</span
      >
      <span
        ><strong>{{ formatCount(workspace.testCaseCount) }}</strong
        >用例</span
      >
    </span>
  </button>

  <article
    v-else
    class="workspace-card workspace-card--archived"
    :aria-label="`已归档工作空间：${workspace.name}，只读`"
  >
    <span class="workspace-card__head">
      <span
        class="workspace-card__icon"
        :class="{ 'workspace-card__icon--admin': adminRole }"
        aria-hidden="true"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path
            d="M3.5 6.5A1.5 1.5 0 0 1 5 5h4l2 2.5h8A1.5 1.5 0 0 1 20.5 9v8.5A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5z"
          />
        </svg>
      </span>
      <span class="workspace-card__badges">
        <span
          class="workspace-card__role"
          :class="{ 'workspace-card__role--admin': adminRole }"
        >
          {{ roleLabel }}
        </span>
        <span class="workspace-card__archived">已归档</span>
        <span class="workspace-card__readonly">只读</span>
      </span>
    </span>
    <span class="workspace-card__name" :title="workspace.name">{{ workspace.name }}</span>
    <span class="workspace-card__desc" :title="workspace.description || '暂无描述'">
      {{ workspace.description || '暂无描述' }}
    </span>
    <span class="workspace-card__stats" aria-label="工作空间统计">
      <span
        ><strong>{{ formatCount(workspace.memberCount) }}</strong
        >成员</span
      >
      <span
        ><strong>{{ formatCount(workspace.projectCount) }}</strong
        >项目</span
      >
      <span
        ><strong>{{ formatCount(workspace.testCaseCount) }}</strong
        >用例</span
      >
    </span>
  </article>
</template>

<style scoped lang="scss">
.workspace-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  min-height: 200px;
  padding: 22px var(--card-pad);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
  color: var(--color-neutral-700);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);

  &:hover,
  &:focus-visible {
    border-color: var(--color-primary-300);
    box-shadow: var(--shadow-card-hover);
    transform: translateY(-2px);
  }

  &--active {
    border-color: var(--color-primary-200);
  }
}

.workspace-card--archived {
  cursor: default;

  &:hover,
  &:focus-visible {
    border-color: var(--color-neutral-200);
    box-shadow: var(--shadow-card);
    transform: none;
  }
}

.workspace-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
}

.workspace-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--color-neutral-900);
  color: var(--color-neutral-0);

  &--admin {
    background: var(--color-primary-500);
  }

  svg {
    width: 20px;
    height: 20px;
  }
}

.workspace-card__badges {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-xs);
  min-width: 0;
  font-size: var(--font-size-xs);
}

.workspace-card__role,
.workspace-card__current,
.workspace-card__archived,
.workspace-card__readonly {
  display: inline-flex;
  align-items: center;
  min-height: 18px;
  padding: 2px 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  line-height: 1;
  white-space: nowrap;
}

.workspace-card__role {
  background: var(--color-neutral-100);
  border-color: var(--color-neutral-200);
  color: var(--color-neutral-600);

  &--admin {
    border-color: var(--color-primary-100);
    background: var(--color-primary-50);
    color: var(--color-primary-600);
  }
}

.workspace-card__current {
  background: var(--color-primary-50);
  border-color: var(--color-primary-100);
  color: var(--color-primary-700);
}

.workspace-card__archived,
.workspace-card__readonly {
  background: var(--color-neutral-100);
  border-color: var(--color-neutral-200);
  color: var(--color-neutral-500);
}

.workspace-card__name {
  display: block;
  min-width: 0;
  margin-top: 14px;
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: 15px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-card__desc {
  display: -webkit-box;
  min-height: 40px;
  margin-top: 6px;
  overflow: hidden;
  color: var(--color-neutral-500);
  font-size: 12.5px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.workspace-card__stats {
  display: flex;
  gap: 18px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--color-neutral-200);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);

  strong {
    margin-right: 3px;
    color: var(--color-neutral-900);
    font-weight: 650;
    font-variant-numeric: tabular-nums;
  }
}

@media (prefers-reduced-motion: reduce) {
  .workspace-card {
    transition: none;
  }
}
</style>
