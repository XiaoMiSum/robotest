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
  copyFromId?: string
  name: string
  dirty: boolean
}

// 由刷新 / 直链恢复与列表页跳转驱动
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

/** 复制：打开新建态编辑器并预填源场景（测试场景详细设计 3.1.6），保存走创建接口 */
function openCopyFrom(id: string) {
  const key = `create-${Date.now()}`
  tabs.value.push({ key, createMode: true, copyFromId: id, name: '新场景', dirty: false })
  active.value = key
  void router.replace({ query: { ...route.query, tab: 'scenes', action: 'create', copyFrom: id } })
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

function handleCopy(id: string) {
  openCopyFrom(id)
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

// 刷新 / 直链恢复：与既有 query 约定一致（?tab=scenes&sceneId= / &action=create&copyFrom=）
function restoreFromQuery() {
  const q = route.query
  if (typeof q.sceneId === 'string') {
    openEdit(q.sceneId)
  } else if (q.action === 'create' && typeof q.copyFrom === 'string') {
    openCopyFrom(q.copyFrom)
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
      addable
      class="scene-workspace__tabs"
      @tab-add="handleCreate"
      @tab-remove="closeByName"
    >
      <template #add-icon>
        <span title="新建场景"><el-icon><Plus /></el-icon></span>
      </template>
      <el-tab-pane :name="LIST_KEY" :closable="false">
        <template #label>
          <span class="scene-workspace__list-label">全部场景</span>
        </template>
        <KeepAlive>
          <ScenariosPage
            v-if="active === LIST_KEY"
            @create="handleCreate"
            @edit="handleEdit"
            @copy="handleCopy"
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
            :copy-from-id="tab.copyFromId"
            @back="handleEditorBack(tab)"
            @title-update="(n: string) => handleTitle(tab.key, n)"
            @dirty-change="(d: boolean) => handleDirty(tab.key, d)"
          />
        </KeepAlive>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.scene-workspace {
  position: relative;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;

  // 整条标签头以快速调试 .debug-tabbar 为准，收敛 Element Plus 默认结构的样式差异（UI-DS-09 例外，已确认原型）
  :deep(.el-tabs__header) {
    margin-bottom: var(--space-sm);
    padding: 4px 6px 0;
    gap: var(--space-xs);
    border-bottom: 1px solid var(--color-neutral-200);
    justify-content: flex-start;
  }

  :deep(.el-tabs__nav-wrap) {
    flex: 0 1 auto;
    min-width: 0;
    margin-bottom: 0;

    // 页签头底边线已是公共基线，避免与激活下划线叠成双线
    &::after {
      display: none;
    }
  }

  :deep(.el-tabs__nav) {
    float: none;
    gap: 2px;
  }

  :deep(.el-tabs__item) {
    height: 36px;
    padding: 0 12px;
    color: var(--color-neutral-500);
    font-size: var(--font-size-sm);
    font-weight: 400;

    &:hover {
      color: var(--color-neutral-800);
    }

    &.is-active {
      color: var(--color-primary-600);
      font-weight: 600;
    }

    // 激活下划线压在底边线上，内缩 6px 与快速调试一致
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
  }

  // EP 对首尾页签有清零内边距的默认规则，需显式还原快速调试的 12px
  :deep(.el-tabs__item:nth-child(2)) {
    padding-left: 12px;
  }

  :deep(.el-tabs__item:last-child) {
    padding-right: 12px;
  }

  :deep(.el-tabs__active-bar) {
    display: none;
  }

  :deep(.el-tabs__item .is-icon-close) {
    margin-left: 0;
    padding: 0 3px;
    color: var(--color-neutral-400);
    font-size: 14px;

    &:hover {
      color: var(--color-neutral-700);
      background: var(--color-neutral-100);
      border-radius: 50%;
    }
  }

  // [+] 与快速调试新建入口同形态；固定 26px 高以与接口管理 [导入] 口径一致
  :deep(.el-tabs__new-tab) {
    box-sizing: border-box;
    height: 26px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
    margin: 0 auto 0 var(--space-xs);
    padding: 0 4px;
    border: none;
    background: transparent;
    color: var(--color-neutral-400);
    font-size: 14px;

    &:hover {
      color: var(--color-primary-500);
    }
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

}

.scene-workspace__tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
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
    background: var(--color-primary-500);
    vertical-align: middle;
  }
}
</style>
