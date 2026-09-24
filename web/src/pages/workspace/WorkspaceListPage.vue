<script setup lang="ts">
import { ref } from 'vue'
import { useWorkspaceListPage } from '@/composables/workspace/useWorkspaceListPage'
import WorkspaceCard from '@/components/workspace/WorkspaceCard.vue'
import WorkspaceCreateDialog from '@/components/workspace/WorkspaceCreateDialog.vue'
import type { WorkspaceItem, WorkspaceScope } from '@/types'

const {
  workspaces,
  total,
  counts,
  keyword,
  scope,
  pageNo,
  pageSize,
  error,
  canCreate,
  activeWorkspaceId,
  isInitialLoading,
  isRefreshing,
  hasFilters,
  handleSearch,
  handleClear,
  clearFilters,
  changeScope,
  changePage,
  changePageSize,
  retry,
  enterWorkspace,
} = useWorkspaceListPage()

const scopeOptions: { value: WorkspaceScope; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'managed', label: '我管理的' },
  { value: 'archived', label: '已归档' },
]

const createDialogVisible = ref(false)

function openCreateDialog(): void {
  if (!canCreate.value) return
  createDialogVisible.value = true
}

function handleCreated(): void {
  void clearFilters()
}

function handleKeywordInput(event: Event): void {
  const target = event.target
  if (target instanceof HTMLInputElement && target.value === '') {
    void handleClear()
  }
}

function handleEnter(workspace: WorkspaceItem): void {
  void enterWorkspace(workspace)
}
</script>

<template>
  <main class="workspace-list-page">
    <header class="page-head workspace-list-page__head">
      <div>
        <h1 class="page-head__title">我的空间</h1>
        <p class="page-head__desc">选择一个工作空间开始协作，或创建新的空间</p>
      </div>
      <div v-if="canCreate" class="page-head__actions">
        <button type="button" class="workspace-list-page__primary-button" @click="openCreateDialog">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <path d="M12 5v14M5 12h14" stroke-linecap="round" />
          </svg>
          新建空间
        </button>
      </div>
    </header>

    <section class="workspace-list-page__toolbar-card" aria-label="工作空间筛选">
      <div class="workspace-list-page__toolbar">
        <div class="workspace-list-page__filters">
          <form class="workspace-list-page__search" role="search" @submit.prevent="handleSearch">
            <label class="workspace-list-page__visually-hidden" for="workspace-keyword"
              >搜索空间名称</label
            >
            <span class="workspace-list-page__search-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <circle cx="10.8" cy="10.8" r="6.3" />
                <path d="m16 16 4.2 4.2" stroke-linecap="round" />
              </svg>
            </span>
            <input
              id="workspace-keyword"
              v-model="keyword"
              class="workspace-list-page__search-input"
              type="search"
              placeholder="搜索空间名称"
              autocomplete="off"
              :disabled="isInitialLoading"
              @keyup.enter="handleSearch"
              @input="handleKeywordInput"
              @clear="handleClear"
            />
          </form>

          <div class="workspace-list-page__segment" role="group" aria-label="空间范围">
            <button
              v-for="option in scopeOptions"
              :key="option.value"
              type="button"
              class="workspace-list-page__segment-button"
              :class="{ 'workspace-list-page__segment-button--active': scope === option.value }"
              :aria-pressed="scope === option.value"
              :disabled="isInitialLoading"
              @click="changeScope(option.value)"
            >
              {{ option.label }}（{{ counts[option.value] }}）
            </button>
          </div>
        </div>
        <span class="workspace-list-page__sort-note">按最近访问排序</span>
      </div>
    </section>

    <div v-if="isRefreshing" class="workspace-list-page__refresh" role="status" aria-live="polite">
      正在刷新工作空间列表…
    </div>

    <div
      v-if="isInitialLoading"
      class="workspace-list-page__grid"
      aria-busy="true"
      aria-label="正在加载工作空间"
    >
      <div v-for="index in 6" :key="index" class="workspace-list-page__skeleton-card">
        <div class="workspace-list-page__skeleton-icon" />
        <div class="workspace-list-page__skeleton-line workspace-list-page__skeleton-line--title" />
        <div class="workspace-list-page__skeleton-line" />
        <div class="workspace-list-page__skeleton-line workspace-list-page__skeleton-line--short" />
        <div class="workspace-list-page__skeleton-stats" />
      </div>
    </div>

    <section
      v-else-if="error && !workspaces.length"
      class="workspace-list-page__state"
      role="alert"
    >
      <div
        class="workspace-list-page__state-icon workspace-list-page__state-icon--danger"
        aria-hidden="true"
      >
        !
      </div>
      <h2 class="workspace-list-page__state-title">{{ error }}</h2>
      <p class="workspace-list-page__state-desc">请检查网络连接后重试</p>
      <button type="button" class="workspace-list-page__secondary-button" @click="retry">
        重试
      </button>
    </section>

    <section v-else-if="!workspaces.length" class="workspace-list-page__state">
      <div class="workspace-list-page__state-icon" aria-hidden="true">＋</div>
      <template v-if="hasFilters">
        <h2 class="workspace-list-page__state-title">未找到匹配的工作空间</h2>
        <p class="workspace-list-page__state-desc">请调整名称或切换空间范围</p>
        <button type="button" class="workspace-list-page__secondary-button" @click="clearFilters">
          清除筛选
        </button>
      </template>
      <template v-else>
        <h2 class="workspace-list-page__state-title">暂无归属的工作空间，请联系管理员</h2>
        <p class="workspace-list-page__state-desc">
          {{ canCreate ? '你还可以创建一个新的工作空间' : '请联系管理员分配工作空间' }}
        </p>
        <button
          v-if="canCreate"
          type="button"
          class="workspace-list-page__secondary-button"
          @click="openCreateDialog"
        >
          新建空间
        </button>
      </template>
    </section>

    <template v-else>
      <div v-if="error" class="workspace-list-page__error-banner" role="alert">
        <span>{{ error }}</span>
        <button type="button" @click="retry">重试</button>
      </div>
      <div class="workspace-list-page__grid">
        <WorkspaceCard
          v-for="workspace in workspaces"
          :key="workspace.id"
          :workspace="workspace"
          :active="activeWorkspaceId === workspace.id"
          @enter="handleEnter(workspace)"
        />
        <button
          v-if="canCreate"
          type="button"
          class="workspace-list-page__create-card"
          @click="openCreateDialog"
        >
          <span class="workspace-list-page__create-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M12 5v14M5 12h14" stroke-linecap="round" />
            </svg>
          </span>
          <strong>新建工作空间</strong>
          <span>从空间开始组织你的项目与成员</span>
        </button>
      </div>
    </template>

    <div v-if="!isInitialLoading && !error && total > pageSize" class="workspace-list-page__pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        @current-change="changePage"
        @size-change="changePageSize"
      />
    </div>

    <WorkspaceCreateDialog
      v-if="canCreate"
      v-model="createDialogVisible"
      @created="handleCreated"
    />
  </main>
</template>

<style scoped lang="scss">
.workspace-list-page {
  min-width: 0;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: 4px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.page-head__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: var(--space-md);
}

.workspace-list-page__head {
  margin-bottom: var(--block-gap);
}

.workspace-list-page__primary-button,
.workspace-list-page__secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 15px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font: inherit;
  font-size: var(--font-size-base);
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.workspace-list-page__primary-button {
  background: var(--color-primary-500);
  color: var(--color-neutral-0);

  svg {
    width: 15px;
    height: 15px;
  }

  &:hover {
    background: var(--color-primary-600);
  }
}

.workspace-list-page__secondary-button {
  border-color: var(--color-neutral-300);
  background: var(--color-neutral-0);
  color: var(--color-neutral-700);

  &:hover {
    border-color: var(--color-neutral-400);
    background: var(--color-neutral-50);
  }
}

.workspace-list-page__toolbar-card {
  margin-bottom: var(--space-lg);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
}

.workspace-list-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-bottom: 0;
  flex-wrap: wrap;
}

.workspace-list-page__filters {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  min-width: 0;
  flex-wrap: wrap;
}

.workspace-list-page__search {
  position: relative;
  width: 220px;
}

.workspace-list-page__search-input {
  width: 100%;
  height: 32px;
  padding: 0 11px 0 32px;
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  color: var(--color-neutral-900);
  font: inherit;
  outline: none;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);

  &::placeholder {
    color: var(--color-neutral-400);
  }

  &:hover {
    border-color: var(--color-neutral-400);
  }

  &:focus {
    border-color: var(--color-primary-500);
    box-shadow: 0 0 0 3px var(--color-primary-100);
  }

  &:disabled {
    background: var(--color-neutral-50);
    color: var(--color-neutral-400);
    cursor: not-allowed;
  }
}

.workspace-list-page__search-icon {
  position: absolute;
  top: 50%;
  left: 10px;
  z-index: 1;
  width: 15px;
  height: 15px;
  color: var(--color-neutral-400);
  pointer-events: none;
  transform: translateY(-50%);

  svg {
    width: 100%;
    height: 100%;
  }
}

.workspace-list-page__visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.workspace-list-page__segment {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border-radius: var(--radius-md);
  background: var(--color-neutral-100);
}

.workspace-list-page__segment-button {
  min-height: 26px;
  padding: 4px 14px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-neutral-600);
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 1.4;
  cursor: pointer;
  white-space: nowrap;

  &:hover:not(:disabled) {
    color: var(--color-neutral-900);
  }

  &--active {
    background: var(--color-neutral-0);
    color: var(--color-neutral-900);
    box-shadow: var(--shadow-sm);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.workspace-list-page__sort-note,
.workspace-list-page__refresh {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.workspace-list-page__refresh {
  margin: -8px 0 var(--space-md);
}

.workspace-list-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.workspace-list-page__skeleton-card {
  min-height: 200px;
  padding: var(--space-xl) var(--card-pad);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
}

.workspace-list-page__skeleton-icon,
.workspace-list-page__skeleton-line,
.workspace-list-page__skeleton-stats {
  background: linear-gradient(
    90deg,
    var(--color-neutral-100) 25%,
    var(--color-neutral-50) 37%,
    var(--color-neutral-100) 63%
  );
  background-size: 400% 100%;
  animation: workspace-list-page-skeleton 1.4s ease infinite;
}

.workspace-list-page__skeleton-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.workspace-list-page__skeleton-line {
  width: 100%;
  height: 12px;
  margin-top: 14px;
  border-radius: var(--radius-sm);

  &--title {
    width: 42%;
    margin-top: 18px;
  }

  &--short {
    width: 65%;
    margin-top: 8px;
  }
}

.workspace-list-page__skeleton-stats {
  height: 16px;
  margin-top: 28px;
  border-top: 1px dashed var(--color-neutral-200);
}

@keyframes workspace-list-page-skeleton {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

.workspace-list-page__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  padding: 56px 24px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  text-align: center;
}

.workspace-list-page__state-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 16px;
  border: 1px solid var(--color-neutral-200);
  border-radius: 50%;
  background: var(--color-neutral-50);
  color: var(--color-neutral-400);
  font-size: var(--font-size-2xl);

  &--danger {
    border-color: var(--color-danger-border);
    background: var(--color-danger-light);
    color: var(--color-danger);
    font-weight: 700;
  }
}

.workspace-list-page__state-title {
  margin: 0;
  color: var(--color-neutral-800);
  font-size: var(--font-size-base);
  font-weight: 600;
}

.workspace-list-page__state-desc {
  margin: 6px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.workspace-list-page__state .workspace-list-page__secondary-button {
  margin-top: 18px;
}

.workspace-list-page__error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
  padding: 10px 14px;
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-md);
  background: var(--color-danger-light);
  color: var(--color-danger-strong);
  font-size: var(--font-size-sm);

  button {
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    cursor: pointer;
    text-decoration: underline;
  }
}

.workspace-list-page__create-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: var(--space-xl) var(--card-pad);
  border: 1px dashed var(--color-neutral-300);
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--color-neutral-700);
  font: inherit;
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast),
    transform var(--transition-fast);

  &:hover,
  &:focus-visible {
    border-color: var(--color-primary-400);
    background: var(--color-primary-50);
    transform: translateY(-2px);
  }

  strong {
    color: var(--color-neutral-900);
    font-size: var(--font-size-sm);
  }

  > span:last-child {
    margin-top: 4px;
    color: var(--color-neutral-500);
    font-size: var(--font-size-xs);
  }
}

.workspace-list-page__create-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 12px;
  border: 1px solid var(--color-neutral-200);
  border-radius: 50%;
  background: var(--color-neutral-50);
  color: var(--color-neutral-400);

  svg {
    width: 24px;
    height: 24px;
  }
}

.workspace-list-page__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--space-xl);
}

@media (max-width: 768px) {
  .workspace-list-page__head {
    flex-direction: column;
  }

  .workspace-list-page__toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .workspace-list-page__search {
    width: 100%;
  }

  .workspace-list-page__filters {
    width: 100%;
  }

  .workspace-list-page__segment {
    flex-wrap: wrap;
  }
}

@media (max-width: 480px) {
  .workspace-list-page__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .workspace-list-page__filters {
    align-items: stretch;
    flex-direction: column;
  }

  .workspace-list-page__segment-button {
    flex: 1;
    padding-right: 8px;
    padding-left: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .workspace-list-page__primary-button,
  .workspace-list-page__secondary-button,
  .workspace-list-page__create-card {
    transition: none;
  }

  .workspace-list-page__skeleton-icon,
  .workspace-list-page__skeleton-line,
  .workspace-list-page__skeleton-stats {
    animation: none;
  }
}
</style>
