<script setup lang="ts">
import { type ComponentPublicInstance, computed, nextTick, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'
import { WORKSPACE_ROLE, workspaceRoleLabel } from '@/utils/workspaceRole'

const props = defineProps<{
  members: WorkspaceMember[]
  loading: boolean
  loadError: boolean
  total: number
  keyword: string
  workspaceRole: string
  pageNo: number
  pageSize: number
  roleOptions: { value: string; label: string }[]
  canManageMember: boolean
  currentUserId: string
  editingUserId: string
}>()

const emit = defineEmits<{
  'search-input': [value: string]
  'search-clear': []
  'role-filter-change': [value: string]
  'page-change': [pageNo: number]
  'start-edit-role': [userId: string]
  'role-change': [member: WorkspaceMember, roleId: string]
  'role-visible-change': [visible: boolean]
  remove: [member: WorkspaceMember]
  retry: []
}>()

const roleSelectRef = ref<ComponentPublicInstance>()

// 进入编辑态后由行内下拉接管焦点，父级只负责记录“正在编辑哪一行”
watch(
  () => props.editingUserId,
  (userId) => {
    if (!userId) return
    void nextTick().then(() => {
      const select = roleSelectRef.value as unknown as { focus?: () => void } | undefined
      select?.focus?.()
    })
  },
)

function resolveWorkspaceRoleLabel(roleId: string): string {
  const roleName = props.roleOptions.find((role) => role.value === roleId)?.label
  return workspaceRoleLabel(roleId, roleName)
}

function isWorkspaceAdminRole(roleId: string): boolean {
  return roleId === WORKSPACE_ROLE.ADMIN
}

function handleKeywordInput(value: string | number): void {
  emit('search-input', String(value))
}

function handleRoleFilterChange(value: unknown): void {
  emit('role-filter-change', typeof value === 'string' ? value : '')
}

function handlePageChange(pageNo: number): void {
  emit('page-change', pageNo)
}

// 空态要区分「筛选没命中」与「本来就没人」，否则用户会以为数据丢了
const hasMemberFilter = computed(() => Boolean(props.keyword.trim() || props.workspaceRole))
const emptyDescription = computed(() =>
  props.canManageMember ? '暂无成员，点击右上角「邀请成员」邀请加入' : '暂无成员',
)
</script>

<template>
  <section class="member-list-card">
    <header class="member-list-card__head">
      <div class="member-list-card__title-group">
        <h2 class="member-list-card__title">成员列表</h2>
        <span class="member-list-card__count">{{ total }} 人</span>
      </div>
      <div class="member-list-card__filters">
        <el-input
          :model-value="keyword"
          class="member-list-card__search"
          placeholder="姓名 / 邮箱"
          clearable
          :prefix-icon="Search"
          @input="handleKeywordInput"
          @clear="emit('search-clear')"
        />
        <el-select
          :model-value="workspaceRole"
          class="member-list-card__role-filter"
          placeholder="全部角色"
          clearable
          @change="handleRoleFilterChange"
        >
          <el-option
            v-for="role in roleOptions"
            :key="role.value"
            :label="role.label"
            :value="role.value"
          />
        </el-select>
      </div>
    </header>

    <div class="member-list-card__body">
      <el-table
        v-loading="loading"
        class="member-list-card__table"
        :data="members"
        height="100%"
        row-key="userId"
      >
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="member-list-card__user">
              <el-avatar :size="32" :src="row.avatarUrl || undefined">
                {{ (row.name || row.username).charAt(0).toUpperCase() }}
              </el-avatar>
              <div class="member-list-card__user-copy">
                <div class="member-list-card__user-name">{{ row.name || row.username }}</div>
                <div class="member-list-card__user-email">{{ row.email }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <el-select
              v-if="editingUserId === row.userId"
              ref="roleSelectRef"
              :model-value="row.workspaceRole"
              size="small"
              automatic-dropdown
              @change="(value: string) => emit('role-change', row as WorkspaceMember, value)"
              @visible-change="(visible: boolean) => emit('role-visible-change', visible)"
            >
              <el-option
                v-for="role in roleOptions"
                :key="role.value"
                :label="role.label"
                :value="role.value"
              />
            </el-select>
            <span
              v-else
              class="member-list-card__role-tag"
              :class="{
                'member-list-card__role-tag--admin': isWorkspaceAdminRole(row.workspaceRole),
              }"
            >
              {{ resolveWorkspaceRoleLabel(row.workspaceRole) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" min-width="170">
          <template #default="{ row }">
            <span class="member-list-card__num">{{ formatDateTime(row.joinedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="member-list-card__row-actions">
              <el-button
                v-if="canManageMember"
                link
                type="primary"
                @click="emit('start-edit-role', row.userId)"
              >
                改角色
              </el-button>
              <el-button
                v-if="canManageMember || row.userId === currentUserId"
                link
                type="danger"
                @click="emit('remove', row as WorkspaceMember)"
              >
                {{ row.userId === currentUserId ? '退出' : '移除' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="loadError" description="成员列表加载失败">
            <el-button type="primary" @click="emit('retry')">重新加载</el-button>
          </el-empty>
          <el-empty
            v-else-if="hasMemberFilter"
            description="暂无符合条件的成员"
          />
          <el-empty v-else :description="emptyDescription" />
        </template>
      </el-table>
    </div>

    <footer class="member-list-card__pager">
      <span>共 {{ total }} 人 · 每页 {{ pageSize }} 条</span>
      <el-pagination
        :current-page="pageNo"
        background
        layout="prev, pager, next"
        :page-size="pageSize"
        :pager-count="5"
        :total="total"
        @current-change="handlePageChange"
      />
    </footer>
  </section>
</template>

<style scoped lang="scss">
.member-list-card {
  display: flex;
  min-width: 0;
  /* 内容超出时由表体裁剪，卡片本身不参与页面滚动（UI-SC-02） */
  min-height: 0;
  overflow: hidden;
  flex-direction: column;
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
}

.member-list-card__head {
  display: flex;
  box-sizing: border-box;
  /* 与邀请链接卡片统一表头高度，保证两卡表头落在同一水平线 */
  min-height: 65px;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-lg) var(--card-pad);
  border-bottom: 1px solid var(--color-neutral-100);
}

.member-list-card__title-group {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.member-list-card__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-base);
  font-weight: 600;
}

.member-list-card__count {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.member-list-card__filters {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.member-list-card__search {
  width: 220px;
}

.member-list-card__role-filter {
  width: 160px;
}

.member-list-card__body {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex: 1;
}

/* 表格与卡片容器不留边距，首列文字由单元格留白对齐卡片头与分页 */
.member-list-card__table {
  width: 100%;
}

.member-list-card__user {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.member-list-card__user-copy {
  min-width: 0;
}

.member-list-card__user-name {
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-list-card__user-email {
  overflow: hidden;
  margin-top: 2px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-list-card__role-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 1px var(--space-sm);
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-sm);
  background: var(--color-neutral-0);
  color: var(--color-neutral-600);
  font-size: var(--font-size-xs);
  line-height: 1.4;
}

.member-list-card__role-tag--admin {
  border-color: var(--color-primary-200);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.member-list-card__num {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

.member-list-card__row-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.member-list-card__pager {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  box-sizing: border-box;
  min-height: 60px;
  padding: var(--space-md) var(--card-pad);
  border-top: 1px solid var(--color-neutral-100);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

@media (max-width: 900px) {
  .member-list-card__head {
    align-items: stretch;
    flex-direction: column;
  }

  /* 堆叠布局恢复页面级滚动，表体不再固定高度（UI-SC-08） */
  .member-list-card__body {
    overflow: visible;
    flex: none;
  }

  .member-list-card__filters {
    width: 100%;
  }

  .member-list-card__search,
  .member-list-card__role-filter {
    width: auto;
    flex: 1 1 180px;
  }
}

@media (max-width: 640px) {
  .member-list-card__pager {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
