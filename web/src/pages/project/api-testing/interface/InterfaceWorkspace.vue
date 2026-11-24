<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import InterfacesPage from './InterfacesPage.vue'
import InterfaceEditorPage from './InterfaceEditorPage.vue'

/** 单个编辑器 Tab 状态；dirty 由子编辑器上报，供关闭前做离开确认 */
interface EditorTab {
  key: string
  createMode: boolean
  interfaceId?: string
  moduleId?: string
  copyFromId?: string
  name: string
  dirty: boolean
}

// 由 DebugPage「查看接口」注入：非空即打开对应编辑 Tab（多 Tab 并存下的跨子页跳转）
const props = defineProps<{ pendingInterfaceId?: string | null }>()
const emit = defineEmits<{ (e: 'view-handled'): void }>()

const route = useRoute()
const router = useRouter()

const LIST_KEY = 'list'
const active = ref<string>(LIST_KEY)
const tabs = ref<EditorTab[]>([])

function clearRouterQuery() {
  void router.replace({ query: { tab: 'interfaces' } })
}

function openEdit(id: string) {
  const existing = tabs.value.find((t) => !t.createMode && t.interfaceId === id)
  if (existing) {
    active.value = existing.key
    return
  }
  tabs.value.push({ key: `edit-${id}`, createMode: false, interfaceId: id, name: '', dirty: false })
  active.value = tabs.value[tabs.value.length - 1].key
  void router.replace({ query: { tab: 'interfaces', interfaceId: id } })
}

function openCreate(moduleId?: string) {
  const key = `create-${Date.now()}`
  tabs.value.push({ key, createMode: true, moduleId, name: '新接口', dirty: false })
  active.value = key
  void router.replace({ query: { tab: 'interfaces', action: 'create', ...(moduleId ? { moduleId } : {}) } })
}

/** 复制：打开新建态编辑器并回填源接口定义，保存走创建接口 */
function openCopyFrom(id: string) {
  const key = `create-${Date.now()}`
  tabs.value.push({ key, createMode: true, copyFromId: id, name: '新接口', dirty: false })
  active.value = key
  void router.replace({ query: { tab: 'interfaces', action: 'create', copyFrom: id } })
}

async function closeTab(tab: EditorTab) {
  if (tab.dirty) {
    try {
      await ElMessageBox.confirm('该接口有未保存的修改，确定关闭？', '关闭编辑器', { type: 'warning' })
    } catch {
      return
    }
  }
  tabs.value = tabs.value.filter((t) => t.key !== tab.key)
  if (active.value === tab.key) {
    active.value = LIST_KEY
    clearRouterQuery()
  }
}

function handleCreate(moduleId?: string) {
  openCreate(moduleId)
}

function handleEdit(id: string) {
  openEdit(id)
}

function handleCopy(id: string) {
  openCopyFrom(id)
}

// 页签条 [+] / [导入] 作用于列表数据：切回列表 Tab 后经 ref 委托列表页，复用当前模块上下文
const listRef = ref<InstanceType<typeof InterfacesPage> | null>(null)

async function ensureOnList() {
  if (active.value !== LIST_KEY) active.value = LIST_KEY
  await nextTick()
}

async function handleHeaderCreate() {
  await ensureOnList()
  listRef.value?.openCreate()
}

async function handleHeaderImport() {
  await ensureOnList()
  listRef.value?.openImport()
}

/** 新建成功（back）→ 关闭创建 Tab 回列表并让列表刷新 */
async function handleEditorBack(tab: EditorTab) {
  await closeTabNoConfirm(tab)
  active.value = LIST_KEY
  clearRouterQuery()
}

function closeTabNoConfirm(tab: EditorTab) {
  tabs.value = tabs.value.filter((t) => t.key !== tab.key)
}

function handleTitle(key: string, name: string) {
  const tab = tabs.value.find((t) => t.key === key)
  if (tab) tab.name = name
}

function handleDirty(key: string, dirty: boolean) {
  const tab = tabs.value.find((t) => t.key === key)
  if (tab) tab.dirty = dirty
}

// 刷新 / 直链恢复：与既有 query 约定一致（?tab=interfaces&interfaceId= / &action=create[&copyFrom=]）
function restoreFromQuery() {
  const q = route.query
  if (typeof q.interfaceId === 'string') {
    openEdit(q.interfaceId)
  } else if (q.action === 'create' && typeof q.copyFrom === 'string') {
    openCopyFrom(q.copyFrom)
  } else if (q.action === 'create') {
    openCreate((q.moduleId as string) ?? undefined)
  }
}
restoreFromQuery()

// 跨子页「查看接口」：从快速调试切到接口管理并打开对应编辑 Tab
watch(
  () => props.pendingInterfaceId,
  (id) => {
    if (id) {
      openEdit(id)
      emit('view-handled')
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="interface-workspace">
    <!-- 标签头直接沿用快速调试页 .debug-tabbar 的结构与样式，不再用 el-tabs 包裹 -->
    <div class="workspace-tabbar">
      <div class="workspace-tabs">
        <div
          class="workspace-tab"
          :class="{ 'is-active': active === LIST_KEY }"
          @click="active = LIST_KEY"
        >
          <span>全部接口</span>
        </div>
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="workspace-tab"
          :class="{ 'is-active': active === tab.key, 'is-dirty': tab.dirty }"
          @click="active = tab.key"
        >
          <span class="workspace-tab__label">
            {{ tab.name || (tab.createMode ? '新接口' : '') }}
          </span>
          <el-button
            class="workspace-tab__close"
            link
            aria-label="关闭标签"
            @click.stop="closeTab(tab)"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <el-button link class="workspace-tabbar__add" title="新建接口" @click="handleHeaderCreate">
        <el-icon><Plus /></el-icon>
      </el-button>

      <div class="workspace-tabbar__right">
        <el-button link type="primary" size="small" data-test="interface-import-btn" @click="handleHeaderImport">
          <el-icon><Download /></el-icon>
          <span>导入</span>
        </el-button>
      </div>
    </div>

    <div class="workspace-content">
      <KeepAlive>
        <InterfacesPage
          v-if="active === LIST_KEY"
          ref="listRef"
          @create="handleCreate"
          @edit="handleEdit"
          @copy="handleCopy"
        />
      </KeepAlive>
      <template v-for="tab in tabs" :key="tab.key">
        <KeepAlive>
          <InterfaceEditorPage
            v-if="active === tab.key"
            :key="tab.key"
            :interface-id="tab.createMode ? undefined : tab.interfaceId"
            :create-mode="tab.createMode"
            :module-id="tab.moduleId"
            :copy-from-id="tab.copyFromId"
            @back="handleEditorBack(tab)"
            @title-update="(n: string) => handleTitle(tab.key, n)"
            @dirty-change="(d: boolean) => handleDirty(tab.key, d)"
          />
        </KeepAlive>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
// 以下标签头样式逐条对齐 DebugPage.vue 的 .debug-page__tabbar / .debug-tab，保持三页视觉一致
.interface-workspace {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.workspace-tabbar {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 4px 6px 0;
  border-bottom: 1px solid var(--color-neutral-200);
}

.workspace-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 0 1 auto;
  min-width: 0;
  overflow-x: auto;

  &::-webkit-scrollbar {
    display: none;
  }
}

.workspace-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
  max-width: 200px;
  flex-shrink: 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition: color var(--transition-fast);

  &:hover {
    color: var(--color-neutral-800);
  }

  &.is-active {
    color: var(--color-primary-600);
    font-weight: 600;
  }

  // 激活下划线压在标签条底边线上，所有页签共用一条基线
  &.is-active::after {
    content: '';
    position: absolute;
    left: 6px;
    right: 6px;
    bottom: -1px;
    height: 2px;
    border-radius: 1px;
    background: var(--color-primary-500);
  }

  // 未保存修改的蓝点：与快速调试页签内的状态标记同尺寸
  &.is-dirty::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-primary-500);
    flex-shrink: 0;
  }

  &__label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  // el-button link 的文字色与 hover 背景由 EP 类控制，需借父级选择器提升优先级覆盖
  &__close {
    color: var(--color-neutral-400);
    font-size: 14px;
    flex-shrink: 0;
    height: auto;
    padding: 0 3px;
  }

  &__close:not(:disabled):hover {
    color: var(--color-neutral-700);
    background: var(--color-neutral-100);
    border-radius: 50%;
  }
}

.workspace-tabbar__add {
  padding: 2px 4px;
  color: var(--color-neutral-400);
  flex-shrink: 0;

  &:hover:not(:disabled) {
    color: var(--color-primary-500);
  }
}

.workspace-tabbar__right {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-shrink: 0;
  margin-left: auto;
  padding-left: var(--space-xs);
}

.workspace-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
</style>
