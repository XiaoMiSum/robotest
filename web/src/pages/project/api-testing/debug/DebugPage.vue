<script setup lang="ts">
import { useDebugPage } from '@/composables/project/api-testing/debug/useDebugPage'
import DebugRequestPanel from './DebugRequestPanel.vue'
import DebugResponseViewer from './DebugResponseViewer.vue'
import DebugHistoryView from './DebugHistoryView.vue'
import SaveInterfaceDialog from './SaveInterfaceDialog.vue'

const emit = defineEmits<{ (e: 'view-interface', interfaceId: string): void }>()

const {
  tabs,
  activeTabId,
  showHistory,
  debugEnvironmentId,
  activeTab,
  canAddTab,
  saveVisible,
  saveRecordId,
  canSave,
  executing,
  curlVisible,
  curlText,
  requestHeight,
  containerRef,
  renamingId,
  renamingValue,
  handleSave,
  handleSaved,
  switchTab,
  addTab,
  closeTab,
  startRename,
  commitRename,
  handleExecute,
  handleImportCurl,
  handleRestoreRecord,
  onDividerMouseDown,
  methodColor,
  handleAuxClick,
  tabTitle,
  HISTORY_TAB_ID,
} = useDebugPage((e, id) => emit(e, id))
</script>

<template>
  <div ref="containerRef" class="debug-page">
    <div class="debug-page__tabbar">
      <div class="debug-page__tabs">
        <div
          v-for="tab in tabs"
          :key="tab.id"
          class="debug-tab"
          :class="{ 'is-active': !showHistory && tab.id === activeTabId }"
          @click="switchTab(tab.id)"
          @dblclick="startRename(tab)"
          @auxclick="handleAuxClick($event, tab)"
        >
          <span v-if="renamingId !== tab.id" class="debug-tab__method" :style="{ background: methodColor(tab.method) }">
            {{ tab.method }}
          </span>
          <span v-if="renamingId !== tab.id" class="debug-tab__label">
            {{ tabTitle(tab) }}
          </span>
          <el-input
            v-else
            v-model="renamingValue"
            autofocus
            class="debug-tab__rename"
            @keyup.enter="commitRename(tab)"
            @blur="commitRename(tab)"
          />
          <el-button
            v-if="renamingId !== tab.id"
            class="debug-tab__close"
            link
            aria-label="关闭标签"
            @click.stop="closeTab(tab)"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 历史记录为页面级固定页签：位于会话页签之后、新建之前（交互设计 1.7），不可关闭 -->
      <div class="debug-tab" :class="{ 'is-active': showHistory }" @click="switchTab(HISTORY_TAB_ID)">
        <el-icon><Clock /></el-icon>
        <span>历史记录</span>
      </div>

      <el-button link class="debug-tabbar__add" :disabled="!canAddTab" @click="addTab">
        <el-icon><Plus /></el-icon>
      </el-button>

      <div class="debug-tabbar__right">
        <el-button link type="primary" size="small" @click="curlVisible = true">
          <el-icon><Download /></el-icon>
          <span>导入 cURL</span>
        </el-button>
      </div>
    </div>

    <template v-if="!showHistory && activeTab">
      <div class="debug-page__body" :style="{ '--req-h': requestHeight + '%' }">
        <DebugRequestPanel
          v-model:tab="activeTab"
          v-model:environment-id="debugEnvironmentId"
          class="debug-page__request"
          :executing="executing"
          :can-save="canSave"
          @execute="handleExecute($event)"
          @save="handleSave"
        />
        <div class="debug-page__divider" @mousedown="onDividerMouseDown">
          <div class="debug-page__divider-line" />
        </div>
        <DebugResponseViewer class="debug-page__response" :response="activeTab.response" />
      </div>
    </template>

    <DebugHistoryView v-else class="debug-page__history" @restore="handleRestoreRecord" />

    <el-dialog v-model="curlVisible" title="导入 cURL" width="560">
      <p class="debug-page__curl-tip">粘贴 Chrome / Charles / Fiddler 导出的 cURL 命令，仅解析不执行</p>
      <el-input
        v-model="curlText"
        type="textarea"
        :rows="7"
        placeholder="curl -X POST https://example.com/api -H 'Content-Type: application/json' -d '{...}'"
      />
      <template #footer>
        <el-button @click="curlVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!curlText.trim()" @click="handleImportCurl">
          解析并回填
        </el-button>
      </template>
    </el-dialog>

    <SaveInterfaceDialog
      :visible="saveVisible"
      :record-id="saveRecordId"
      :tab="activeTab"
      :environment-id="debugEnvironmentId"
      @update:visible="saveVisible = $event"
      @saved="handleSaved"
    />
  </div>
</template>

<style lang="scss" scoped>
.debug-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  // 主内容白卡直接承载页签条与内容区（对齐示例的透明容器，无内层卡片边框）
  min-height: 0;
  overflow: hidden;

  // 条底边线是下划线式页签的公共基线，激活下划线（.debug-tab.is-active::after）压线绘制
  &__tabbar {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    padding: 4px 6px 0;
    border-bottom: 1px solid var(--color-neutral-200);
  }

  // 页签区不定宽：页签少时收缩为内容宽（历史记录、新建紧随其后），超宽时压缩并横向滚动
  &__tabs {
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

  &__body {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
  }

  &__request {
    height: var(--req-h, 50%);
    min-height: 80px;
    overflow: auto;
    border-bottom: none;
  }

  &__divider {
    height: 6px;
    cursor: row-resize;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    position: relative;
    z-index: 1;

    &:hover .debug-page__divider-line,
    &:active .debug-page__divider-line {
      background: var(--color-primary-400);
    }
  }

  &__divider-line {
    width: 75%;
    height: 2px;
    border-radius: 1px;
    background: var(--color-neutral-200);
    transition: background 0.15s;
  }

  &__response {
    flex: 1;
    min-height: 80px;
    overflow: auto;
  }

  &__history {
    flex: 1;
    min-height: 0;
    // 视图内边距对齐示例的 .debug__history（space-md × space-sm）
    padding: var(--space-md) var(--space-sm);
  }

  &__curl-tip {
    margin: 0 0 var(--space-sm);
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }
}

.debug-tab {
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

  &__method {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 34px;
    padding: 1px 5px;
    border-radius: 3px;
    color: var(--color-neutral-0);
    font-size: 10px;
    font-weight: 700;
    font-family: var(--font-mono);
    letter-spacing: 0.5px;
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
  .debug-tab__close {
    color: var(--color-neutral-400);
    font-size: 14px;
    flex-shrink: 0;
    height: auto;
    padding: 0 3px;
  }

  .debug-tab__close:not(:disabled):hover {
    color: var(--color-neutral-700);
    background: var(--color-neutral-100);
    border-radius: 50%;
  }

  &__rename {
    width: 120px;
  }
}

.debug-tabbar__add {
  padding: 2px 4px;
  color: var(--color-neutral-400);
  flex-shrink: 0;

  &:hover:not(:disabled) {
    color: var(--color-primary-500);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.3;
  }
}

.debug-tabbar__right {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-shrink: 0;
  margin-left: auto;
  padding-left: var(--space-xs);
}
</style>
