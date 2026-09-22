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

async function closeByName(name: string | number) {
  const key = String(name)
  const tab = tabs.value.find((t) => t.key === key)
  if (tab) await closeTab(tab)
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

// 头部「新建/导入」作用于列表数据：确保切回列表 Tab 后，经 ref 委托给列表页（复用其当前模块上下文）
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

// 刷新 / 直链恢复：与既有 query 约定一致（?tab=interfaces&interfaceId= / &action=create）
function restoreFromQuery() {
  const q = route.query
  if (typeof q.interfaceId === 'string') {
    openEdit(q.interfaceId)
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
    <el-tabs
      v-model="active"
      type="card"
      class="interface-workspace__tabs"
      @tab-remove="closeByName"
    >
      <el-tab-pane :name="LIST_KEY" :closable="false">
        <template #label>
          <span class="interface-workspace__list-label">全部接口</span>
        </template>
        <KeepAlive>
          <InterfacesPage
            v-if="active === LIST_KEY"
            ref="listRef"
            @create="handleCreate"
            @edit="handleEdit"
          />
        </KeepAlive>
      </el-tab-pane>

      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.key"
        :name="tab.key"
        :closable="true"
      >
        <template #label>
          <span
            class="interface-workspace__tab-label"
            :class="{ 'is-dirty': tab.dirty }"
          >
            {{ tab.name || (tab.createMode ? '新接口' : '') }}
          </span>
        </template>
        <KeepAlive>
          <InterfaceEditorPage
            v-if="active === tab.key"
            :key="tab.key"
            :interface-id="tab.createMode ? undefined : tab.interfaceId"
            :create-mode="tab.createMode"
            :module-id="tab.moduleId"
            @back="handleEditorBack(tab)"
            @title-update="(n: string) => handleTitle(tab.key, n)"
            @dirty-change="(d: boolean) => handleDirty(tab.key, d)"
          />
        </KeepAlive>
      </el-tab-pane>
    </el-tabs>

    <!-- 顶部最右侧：列表数据操作按钮，始终可见，不与 tab 条重叠 -->
    <div class="interface-workspace__actions">
      <el-button data-test="interface-import-btn" @click="handleHeaderImport">导入</el-button>
      <el-button type="primary" data-test="interface-create-btn" @click="handleHeaderCreate">
        <el-icon><Plus /></el-icon>新建接口
      </el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.interface-workspace {
  position: relative;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    margin-bottom: var(--space-sm);
    // 为最右侧「导入 / 新建接口」按钮预留横向空间，避免 tab 条与其重叠
    padding-right: 200px;
  }

  :deep(.el-tabs__content) {
    // 让内容区垂直伸满 tab 容器，保证编辑器/列表页的 height:100% 能解析到满高
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  :deep(.el-tab-pane) {
    height: 100%;
    overflow: hidden;
  }

  :deep(.el-tabs__item.is-closable:hover .el-icon-close) {
    background: var(--color-neutral-100, #e8e8e8);
    border-radius: 50%;
  }
}

.interface-workspace__tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

// 覆盖到最右侧，与 tab 条同一行、垂直居中，始终可见（不随激活 Tab 变化）
.interface-workspace__actions {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 10;
  height: 40px;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.interface-workspace__list-label {
  font-weight: 500;
}

.interface-workspace__tab-label {
  &.is-dirty::before {
    content: '';
    display: inline-block;
    width: 6px;
    height: 6px;
    margin-right: 6px;
    border-radius: 50%;
    background: var(--color-primary-500, #409eff);
    vertical-align: middle;
  }
}
</style>
