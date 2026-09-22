<script setup lang="ts">
import type { ApiDebugExecuteResp } from '@/types'
import JsonResponseView from './JsonResponseView.vue'
import { useDebugResponse } from '@/composables/project/api-testing/debug/useDebugResponse'

const props = defineProps<{
  response: ApiDebugExecuteResp | null
}>()

const {
  activeTab,
  bodyMode,
  langOverride,
  statusConfig,
  statusCode,
  statusTooltip,
  bodyText,
  lang,
  parsedJson,
  headerEntries,
  cookieEntries,
  formatSize,
  searchKeyword,
  searchExpanded,
  searchInputRef,
  highlightResult,
  currentMatchIndex,
  searchCountInfo,
  nextMatch,
  prevMatch,
  toggleSearch,
  copying,
  handleCopy,
} = useDebugResponse(() => props.response)
</script>

<template>
  <div class="resp-view">
    <template v-if="!response">
      <div class="resp-view__empty">
        <el-icon class="resp-view__empty-icon"><Position /></el-icon>
        <p>点击「发送」查看响应结果</p>
      </div>
    </template>

    <template v-else>
      <!-- Status Bar -->
      <div class="resp-view__status-bar">
        <el-tooltip v-if="statusTooltip" :content="statusTooltip" placement="bottom">
          <span
            class="resp-view__status-badge"
            :style="{ color: statusConfig.color, background: statusConfig.bg }"
          >
            {{ statusCode }}
          </span>
        </el-tooltip>
        <span
          v-else
          class="resp-view__status-badge"
          :style="{ color: statusConfig.color, background: statusConfig.bg }"
        >
          {{ statusCode }}
        </span>
        <span class="resp-view__meta-item">
          <span class="resp-view__meta-label">Time</span>
          <span class="resp-view__meta-value">{{ response.durationMs != null ? `${response.durationMs} ms` : '—' }}</span>
        </span>
        <span class="resp-view__meta-item">
          <span class="resp-view__meta-label">Size</span>
          <span class="resp-view__meta-value">{{ formatSize(response.size) }}</span>
        </span>
        <span v-if="response.errorMessage" class="resp-view__error" :title="response.errorMessage">
          {{ response.errorMessage }}
        </span>
      </div>

      <!-- Response Tabs -->
      <div class="resp-view__tabs">
        <div class="resp-view__tab-group">
          <button
            v-for="item in (['body', 'headers', 'cookies'] as const)"
            :key="item"
            class="resp-view__tab"
            :class="{ 'is-active': activeTab === item }"
            @click="activeTab = item"
          >
            {{
              item === 'body'
                ? 'Body'
                : item === 'headers'
                  ? `Headers (${headerEntries.length})`
                  : `Cookies (${cookieEntries.length})`
            }}
          </button>
        </div>
        <div class="resp-view__tab-actions">
          <template v-if="searchExpanded && activeTab === 'body'">
            <el-input
              ref="searchInputRef"
              v-model="searchKeyword"
              placeholder="搜索响应体"
              clearable
              class="resp-view__search-input"
              @keyup.enter="nextMatch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <span v-if="searchCountInfo" class="resp-view__search-count">{{ searchCountInfo }}</span>
            <button class="resp-view__icon-btn" @click="prevMatch">
              <el-icon><ArrowUp /></el-icon>
            </button>
            <button class="resp-view__icon-btn" @click="nextMatch">
              <el-icon><ArrowDown /></el-icon>
            </button>
          </template>
          <el-tooltip content="搜索响应体 (Ctrl+F)" placement="top">
            <button class="resp-view__icon-btn" :class="{ 'is-active': searchExpanded }" @click="toggleSearch">
              <el-icon><Search /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip :content="copying ? '已复制' : '复制响应体'" placement="top">
            <button class="resp-view__icon-btn" @click="handleCopy">
              <el-icon v-if="copying"><Check /></el-icon>
              <el-icon v-else><CopyDocument /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </div>

      <!-- Body 子工具栏（Pretty/Raw/Preview + 类型） -->
      <div v-if="activeTab === 'body'" class="resp-view__mode-bar">
        <div class="resp-view__mode-group">
          <button
            v-for="mode in (['pretty', 'raw', 'preview'] as const)"
            :key="mode"
            class="resp-view__mode"
            :class="{ 'is-active': bodyMode === mode }"
            @click="bodyMode = mode"
          >
            {{ mode.charAt(0).toUpperCase() + mode.slice(1) }}
          </button>
        </div>
        <div class="resp-view__mode-right">
          <span class="resp-view__mode-label">类型</span>
          <el-select
            :model-value="lang"
            class="resp-view__lang-select"
            @update:model-value="langOverride = $event"
          >
            <el-option v-for="l in (['text', 'json', 'xml', 'html', 'javascript'] as const)" :key="l" :label="l" :value="l" />
          </el-select>
        </div>
      </div>

      <!-- Content -->
      <div class="resp-view__content">
        <template v-if="activeTab === 'body'">
          <template v-if="bodyMode === 'pretty' && lang === 'json' && parsedJson && !searchKeyword.trim()">
            <div class="resp-view__tree">
              <JsonResponseView :value="parsedJson" path="root" :depth="0" />
            </div>
          </template>
          <template v-else-if="bodyMode === 'preview' && lang === 'html'">
            <iframe class="resp-view__preview" :srcdoc="bodyText" sandbox="allow-scripts" title="响应预览" />
          </template>
          <pre v-else class="resp-view__body">
            <template v-if="highlightResult">
              <template v-for="(seg, i) in highlightResult.segments" :key="i">
                <mark
                  v-if="seg.highlight"
                  class="resp-view__mark"
                  :class="{ 'is-current': i === currentMatchIndex }"
                >{{ seg.text }}</mark><template v-else>{{ seg.text }}</template>
              </template>
            </template>
            <template v-else>{{ bodyText || '（空响应体）' }}</template>
          </pre>
        </template>

        <table v-else-if="activeTab === 'headers'" class="resp-view__headers">
          <thead>
            <tr>
              <th>Name</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="[name, value] in headerEntries" :key="name + value">
              <td class="resp-view__header-name">{{ name }}</td>
              <td class="resp-view__header-value">{{ value }}</td>
            </tr>
            <tr v-if="!headerEntries.length">
              <td colspan="2" class="resp-view__empty-row">无响应头</td>
            </tr>
          </tbody>
        </table>

        <table v-else class="resp-view__headers resp-view__cookies">
          <thead>
            <tr>
              <th>Name</th>
              <th>Value</th>
              <th>Attributes</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cookie in cookieEntries" :key="cookie.name + cookie.value">
              <td class="resp-view__cookie-name">{{ cookie.name }}</td>
              <td class="resp-view__cookie-value">{{ cookie.value }}</td>
              <td class="resp-view__cookie-attrs">{{ cookie.attributes.join('; ') || '—' }}</td>
            </tr>
            <tr v-if="!cookieEntries.length">
              <td colspan="3" class="resp-view__empty-row">响应头中无 Set-Cookie</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.resp-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  // ==================== Empty State ====================
  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--color-neutral-300, #c0c4cc);
    gap: 8px;

    &-icon {
      font-size: 32px;
      opacity: 0.4;
    }

    p {
      font-size: 13px;
      margin: 0;
    }
  }

  // ==================== Status Bar ====================
  &__status-bar {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 8px 10px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    background: var(--color-neutral-50, #fafafa);
    flex-shrink: 0;
  }

  &__status-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 10px;
    border-radius: 4px;
    font-size: 13px;
    font-weight: 700;
    font-family: ui-monospace, SFMono-Regular, monospace;
    cursor: help;
  }

  &__meta-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__meta-label {
    font-size: 11px;
    color: var(--color-neutral-400, #909399);
    text-transform: uppercase;
  }

  &__meta-value {
    font-size: 12px;
    font-weight: 500;
    color: var(--color-neutral-700, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
  }

  &__error {
    margin-left: auto;
    font-size: 12px;
    color: var(--color-danger-500, #f56c6c);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 300px;
  }

  // ==================== Tabs ====================
  &__tabs {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 10px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    flex-shrink: 0;
  }

  &__tab-group {
    display: flex;
  }

  &__tab {
    padding: 8px 14px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-neutral-500, #909399);
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: color 0.15s, border-color 0.15s;
    white-space: nowrap;

    &:hover {
      color: var(--color-neutral-700, #606266);
    }

    &.is-active {
      color: var(--color-primary-500, #409eff);
      border-bottom-color: var(--color-primary-500, #409eff);
    }
  }

  &__tab-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__icon-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border: none;
    background: none;
    border-radius: 4px;
    color: var(--color-neutral-400, #909399);
    cursor: pointer;
    font-size: 14px;
    transition: color 0.15s, background 0.15s;

    &:hover {
      color: var(--color-neutral-700, #606266);
      background: var(--color-neutral-100, #e8e8e8);
    }

    &.is-active {
      color: var(--color-primary-500, #409eff);
      background: var(--color-primary-50, #ecf5ff);
    }
  }

  &__search-input {
    width: 160px;
  }

  &__search-count {
    font-size: 12px;
    color: var(--color-neutral-400, #909399);
    white-space: nowrap;
  }

  // ==================== Body Mode Bar ====================
  &__mode-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 10px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    flex-shrink: 0;
  }

  &__mode-group {
    display: flex;
    gap: 2px;
  }

  &__mode {
    padding: 4px 10px;
    font-size: 12px;
    border: none;
    background: none;
    border-radius: 4px;
    cursor: pointer;
    color: var(--color-neutral-500, #909399);
    transition: all 0.15s;

    &:hover {
      color: var(--color-neutral-700, #606266);
    }

    &.is-active {
      background: var(--color-neutral-100, #e8e8e8);
      color: var(--color-neutral-800, #303133);
      font-weight: 500;
    }
  }

  &__mode-right {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__mode-label {
    font-size: 11px;
    color: var(--color-neutral-400, #909399);
    text-transform: uppercase;
  }

  &__lang-select {
    width: 110px;
  }

  // ==================== Content ====================
  &__content {
    flex: 1;
    overflow: auto;
    min-height: 0;
  }

  // ==================== Body ====================
  &__body {
    margin: 0;
    padding: 12px 10px;
    font-family: ui-monospace, SFMono-Regular, monospace;
    font-size: 12px;
    line-height: 1.6;
    color: #d4d4d4;
    background: #1e1e1e;
    min-height: 100%;
    white-space: pre-wrap;
    word-break: break-all;
  }

  &__tree {
    min-height: 100%;
    padding: 8px 10px;
    background: #1e1e1e;
  }

  &__preview {
    width: 100%;
    height: 100%;
    border: none;
    background: #fff;
  }

  &__mark {
    background: #ffd54d;
    color: #1e1e1e;
    border-radius: 2px;
    padding: 0 1px;

    &.is-current {
      background: #ff9800;
      color: #fff;
      outline: 1px solid #e65100;
    }
  }

  // ==================== Headers / Cookies ====================
  &__headers {
    width: 100%;
    border-collapse: collapse;

    th {
      position: sticky;
      top: 0;
      background: var(--color-neutral-50, #fafafa);
      text-align: left;
      font-weight: 500;
      font-size: 11px;
      color: var(--color-neutral-400, #909399);
      text-transform: uppercase;
      padding: 8px 10px;
      border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    }

    td {
      padding: 6px 10px;
      font-size: 12px;
      vertical-align: top;
      border-bottom: 1px solid var(--color-neutral-50, #fafafa);
      word-break: break-all;
    }

    tr:hover td {
      background: var(--color-neutral-50, #fafafa);
    }
  }

  &__header-name {
    font-weight: 500;
    color: var(--color-neutral-700, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
    white-space: nowrap;
  }

  &__header-value {
    color: var(--color-neutral-600, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
  }

  &__cookie-name {
    font-weight: 500;
    color: var(--color-neutral-700, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
    white-space: nowrap;
  }

  &__cookie-value {
    color: var(--color-neutral-600, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
  }

  &__cookie-attrs {
    color: var(--color-neutral-400, #909399);
    font-size: 11px;
  }

  &__empty-row {
    text-align: center;
    color: var(--color-neutral-300, #c0c4cc);
    padding: 24px 0 !important;
    font-size: 13px;
  }
}
</style>
