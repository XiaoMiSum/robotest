<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import ScenariosPage from './ScenariosPage.vue'
import SceneEditorPage from './SceneEditorPage.vue'

/** 单个编辑器 Tab 状态；dirty 由子编辑器上报，供关闭前做离开确认 */
interface EditorTab {
  key: string
  createMode: boolean
  sceneId?: string
  moduleId?: string
  name: string
  dirty: boolean
}

// 由刷新 / 直链恢复与列表页跳转驱动
const emit = defineEmits<{ (e: 'edit', sceneId: string): void }>()

const route = useRoute()
const router = useRouter()

const LIST_KEY = 'list'
const active = ref<string>(LIST_KEY)
const tabs = ref<EditorTab[]>([])

function clearRouterQuery() {
  void router.replace({ query: { ...route.query, tab: 'scenes' } })
}

function openEdit(id: string) {
  const existing = tabs.value.find((t) => !t.createMode && t.sceneId === id)
  if (existing) {
    active.value = existing.key
    return
  }
  tabs.value.push({ key: `edit-${id}`, createMode: false, sceneId: id, name: '', dirty: false })
  active.value = tabs.value[tabs.value.length - 1].key
  void router.replace({ query: { ...route.query, tab: 'scenes', sceneId: id } })
}

function openCreate(moduleId?: string) {
  const key = `create-${Date.now()}`
  tabs.value.push({ key, createMode: true, moduleId, name: '新场景', dirty: false })
  active.value = key
  void router.replace({ query: { tab: 'scenes', action: 'create', ...(moduleId ? { moduleId } : {}) } })
}

async function closeByName(name: string | number) {
  const key = String(name)
  const tab = tabs.value.find((t) => t.key === key)
  if (tab) await closeTab(tab)
}

async function closeTab(tab: EditorTab) {
  if (tab.dirty) {
    try {
      await ElMessageBox.confirm('该场景有未保存的修改，确定关闭？', '关闭编辑器', { type: 'warning' })
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

/** 新建成功（back）→ 关闭创建 Tab 回列表并让列表刷新 */
async function handleEditorBack(tab: EditorTab) {
  closeTabNoConfirm(tab)
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

// 刷新 / 直链恢复：与既有 query 约定一致（?tab=scenes&sceneId= / &action=create）
function restoreFromQuery() {
  const q = route.query
  if (typeof q.sceneId === 'string') {
    openEdit(q.sceneId)
  } else if (q.action === 'create') {
    openCreate((q.moduleId as string) ?? undefined)
  }
}
restoreFromQuery()
</script>

<template>
  <div class="scene-workspace">
    <el-tabs
      v-model="active"
      type="card"
      class="scene-workspace__tabs"
      @tab-remove="closeByName"
    >
      <el-tab-pane :name="LIST_KEY" :closable="false">
        <template #label>
          <span class="scene-workspace__list-label">全部场景</span>
        </template>
        <KeepAlive>
          <ScenariosPage
            v-if="active === LIST_KEY"
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
            class="scene-workspace__tab-label"
            :class="{ 'is-dirty': tab.dirty }"
          >
            {{ tab.name || (tab.createMode ? '新场景' : '') }}
          </span>
        </template>
        <KeepAlive>
          <SceneEditorPage
            v-if="active === tab.key"
            :key="tab.key"
            :scene-id="tab.createMode ? undefined : tab.sceneId"
            :create-mode="tab.createMode"
            :module-id="tab.moduleId"
            @back="handleEditorBack(tab)"
            @edit="(id: string) => emit('edit', id)"
            @title-update="(n: string) => handleTitle(tab.key, n)"
            @dirty-change="(d: boolean) => handleDirty(tab.key, d)"
          />
        </KeepAlive>
      </el-tab-pane>
    </el-tabs>

    <!-- 顶部最右侧：列表数据操作按钮，始终可见，不与 tab 条重叠 -->
    <div class="scene-workspace__actions">
      <el-button type="primary" data-test="scene-create-btn" @click="handleCreate()">
        <el-icon><Plus /></el-icon>新建场景
      </el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.scene-workspace {
  position: relative;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    margin-bottom: var(--space-sm);
    // 为最右侧「新建场景」按钮预留横向空间，避免 tab 条与其重叠
    padding-right: 120px;
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

.scene-workspace__tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

// 覆盖到最右侧，与 tab 条同一行、垂直居中，始终可见（不随激活 Tab 变化）
.scene-workspace__actions {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 10;
  height: 40px;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.scene-workspace__list-label {
  font-weight: 500;
}

.scene-workspace__tab-label {
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
