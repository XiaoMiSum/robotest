<script setup lang="ts">
import { useDebugPage } from '@/composables/useDebugPage'
import DebugRequestPanel from './debug/DebugRequestPanel.vue'
import DebugResponseViewer from './debug/DebugResponseViewer.vue'
import DebugHistoryView from './debug/DebugHistoryView.vue'
import SaveInterfaceDialog from './debug/SaveInterfaceDialog.vue'

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
    <el-card shadow="never" class="debug-page__card">
      <template #header>
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

            <el-button link class="debug-tabbar__add" :disabled="!canAddTab" @click="addTab">
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>

          <div class="debug-tabbar__right">
            <div class="debug-tab debug-tab--history" @click="curlVisible = true">
              <el-icon><Download /></el-icon>
              <span>导入 cURL</span>
            </div>
            <div
              class="debug-tab debug-tab--history"
              :class="{ 'is-active': showHistory }"
              @click="switchTab(HISTORY_TAB_ID)"
            >
              <el-icon><Clock /></el-icon>
              <span>历史记录</span>
            </div>
          </div>
        </div>
      </template>

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
    </el-card>

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

  &__card {
    display: flex;
    flex-direction: column;
    height: 100%;
    border-radius: var(--radius-lg);

    :deep(.el-card__header) {
      padding: 0;
      border-bottom: none;
    }

    :deep(.el-card__body) {
      flex: 1;
      min-height: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
  }

  &__tabbar {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    padding: 2px 4px;
    min-height: 32px;
  }

  &__tabs {
    display: flex;
    align-items: center;
    gap: 2px;
    flex: 1;
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
      background: var(--color-primary-300, #a0cfff);
    }
  }

  &__divider-line {
    width: 75%;
    height: 2px;
    border-radius: 1px;
    background: var(--color-neutral-200, #dcdfe6);
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
  }

  &__curl-tip {
    margin: 0 0 var(--space-sm);
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }
}

.debug-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  max-width: 200px;
  font-size: 12px;
  color: var(--color-neutral-500, #909399);
  transition: color 0.15s, border-color 0.15s;
  white-space: nowrap;
  user-select: none;

  &:hover {
    color: var(--color-primary-500, #409eff);
  }

  &.is-active {
    color: var(--color-primary-500, #409eff);
    font-weight: 500;
    border-bottom-color: var(--color-primary-500, #409eff);
  }

  &--history {
    color: var(--color-neutral-500, #909399);

    &:hover {
      color: var(--color-primary-500, #409eff);
    }

    &.is-active {
      color: var(--color-primary-500, #409eff);
      font-weight: 500;
      border-bottom-color: var(--color-primary-500, #409eff);
    }
  }

  &__method {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 3px;
    color: #fff;
    font-size: 10px;
    font-weight: 700;
    font-family: ui-monospace, SFMono-Regular, monospace;
    letter-spacing: 0.5px;
    padding: 1px 5px;
    flex-shrink: 0;
    min-width: 30px;
  }

  &__label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__close {
    opacity: 0;
    font-size: 12px;
    flex-shrink: 0;
    height: auto;
    padding: 0 2px;
    transition: opacity 0.15s;

    &:hover {
      color: var(--color-danger-500, #f56c6c);
    }
  }

  &:hover &__close {
    opacity: 0.6;
  }

  &__rename {
    width: 120px;
  }
}

.debug-tabbar__add {
  padding: 2px 4px;
  color: var(--color-neutral-400, #909399);
  flex-shrink: 0;

  &:hover:not(:disabled) {
    color: var(--color-primary-500, #409eff);
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
}

.mr-1 {
  margin-right: 4px;
}
</style>
