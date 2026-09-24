<script setup lang="ts">
import { computed } from 'vue'
import type { Project } from '@/types'
import { formatDate } from '@/utils/format'
import { Monitor, StarFilled } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    project: Project
    canEdit?: boolean
    isAdmin?: boolean
  }>(),
  {
    canEdit: false,
    isAdmin: false,
  },
)

const emit = defineEmits<{
  enter: []
  setDefault: []
  edit: []
  archive: []
  unarchive: []
  delete: []
}>()

const archived = computed(() => props.project.status === 'archived')

function handleCardClick(): void {
  if (!archived.value) emit('enter')
}

function handleCardKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    handleCardClick()
  }
}
</script>

<template>
  <article
    class="project-card"
    :class="{ 'project-card--archived': archived }"
    :tabindex="archived ? undefined : 0"
    :aria-label="archived ? `已归档项目：${project.name}，只读` : `进入项目：${project.name}`"
    :aria-disabled="archived"
    @click="handleCardClick"
    @keydown="handleCardKeydown"
  >
    <div class="project-card__head">
      <span class="project-card__icon" aria-hidden="true">
        <el-icon><Monitor /></el-icon>
      </span>
      <span class="project-card__badges">
        <span v-if="project.isDefault" class="project-card__default">默认项目</span>
        <el-tag :type="archived ? 'info' : 'success'" size="small" effect="light">
          {{ archived ? '已归档' : '活跃' }}
        </el-tag>
      </span>
    </div>

    <div class="project-card__title-row">
      <h3 class="project-card__name" :title="project.name">
        <el-icon v-if="project.isDefault" class="project-card__star" aria-label="默认项目">
          <StarFilled />
        </el-icon>
        {{ project.name }}
      </h3>
    </div>

    <p class="project-card__description" :title="project.description || '暂无描述'">
      {{ project.description || '暂无描述' }}
    </p>

    <div class="project-card__dates">
      <span>开始：{{ formatDate(project.startTime) }}</span>
      <span>结束：{{ formatDate(project.endTime) }}</span>
    </div>

    <div class="project-card__creator">
      {{ project.createdBy?.name || '未知创建人' }} 创建于 {{ formatDate(project.createdAt) }}
    </div>

    <div class="project-card__actions" @click.stop>
      <template v-if="!archived">
        <el-button link type="primary" @click="emit('enter')">进入</el-button>
        <el-button v-if="!project.isDefault" link type="primary" @click="emit('setDefault')">
          设为默认
        </el-button>
        <el-button v-if="canEdit" link @click="emit('edit')">编辑</el-button>
        <el-button v-if="isAdmin" link type="warning" @click="emit('archive')">归档</el-button>
      </template>
      <template v-else>
        <el-button v-if="isAdmin" link @click="emit('unarchive')">启封</el-button>
        <el-button v-if="isAdmin" link type="danger" @click="emit('delete')">删除</el-button>
        <span class="project-card__readonly">只读</span>
      </template>
    </div>
  </article>
</template>

<style scoped lang="scss">
.project-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 270px;
  padding: 20px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
  color: var(--color-neutral-700);
  cursor: pointer;
  outline: none;
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

  &--archived {
    cursor: default;

    &:hover,
    &:focus-visible {
      border-color: var(--color-neutral-200);
      box-shadow: var(--shadow-card);
      transform: none;
    }
  }
}

.project-card__head,
.project-card__title-row,
.project-card__badges,
.project-card__dates,
.project-card__actions {
  display: flex;
  align-items: center;
}

.project-card__head {
  justify-content: space-between;
  gap: var(--space-sm);
}

.project-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--color-primary-100);
  border-radius: 10px;
  background: var(--color-primary-50);
  color: var(--color-primary-500);
}

.project-card__badges {
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-xs);
}

.project-card__default,
.project-card__readonly {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 2px 6px;
  border: 1px solid var(--color-primary-100);
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.project-card__readonly {
  border-color: var(--color-neutral-200);
  background: var(--color-neutral-100);
  color: var(--color-neutral-500);
}

.project-card__title-row {
  min-width: 0;
  margin-top: 16px;
}

.project-card__name {
  display: flex;
  align-items: center;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: 16px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-card__star {
  flex-shrink: 0;
  margin-right: 5px;
  color: var(--color-warning);
}

.project-card__description {
  display: -webkit-box;
  min-height: 42px;
  margin: 10px 0 0;
  overflow: hidden;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.project-card__dates {
  justify-content: space-between;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  color: var(--color-neutral-600);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.project-card__creator {
  margin-top: var(--space-xs);
  overflow: hidden;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-card__actions {
  flex-wrap: wrap;
  gap: 2px;
  margin-top: auto;
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-100);
}
</style>
