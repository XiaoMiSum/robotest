<script setup lang="ts">
import { computed } from 'vue'
import { useEnvironmentDetailState } from '@/composables/project/api-testing/environment/useEnvironmentDetailState'
import KeyValueTable from '@/components/project/api-testing/KeyValueTable.vue'
import ExtractorAssetPicker from '@/components/project/api-testing/ExtractorAssetPicker.vue'
import EnvironmentProcessorPane from './EnvironmentProcessorPane.vue'
import { DRIVER_OPTIONS } from '@/composables/project/api-testing/environment/environmentsModel'

const props = defineProps<{ environmentId: string; canEdit: boolean }>()
const emit = defineEmits<{ changed: [] }>()

const {
  loading, loadError, detail, saving, dirty, configForms, dsForms, variableRows, activeTab,
  activeConfigId, activeConfig, orderedConfigForms, selectConfig, addHttpConfig, removeHttpConfig,
  testingHttpId, runHttpTest, httpConnResult,
  activeDsId, activeDs, orderedDsForms, selectDs, selectedDsDriverOption, handleDsDriverChange,
  addDataSource, removeDataSource, testingDsId, runDsTest, dsConnResult,
  activeProcId, selectedProcessor, preProcCount, postProcCount,
  procList, selectProcessor, addProcessor, removeProcessor,
  moveProcessor, copyProcessor, procTestclass, procHttpRefOptions, procDsRefOptions,
  procHttpRef, procDsRef, procTags, procDisplayName,
  variableCount, load, saveAll,
  extractorPickerVisible, extractorPickerLoading, extractorPickerItems, extractorPickerKeyword,
  openExtractorPicker, handleExtractorPicked, loadExtractorAssets,
  procAssetPickerVisible, procAssetPickerLoading, procAssetPickerItems, procAssetPickerKeyword,
  openProcessorAssetPicker, handleProcessorAssetPicked, loadProcAssets,
} = useEnvironmentDetailState(props, emit)

// 自定义页签：el-tabs 的头与内容无法拆进同一个吸顶容器（docs34 §1.1）
const tabs = computed(() => [
  { key: 'http' as const, label: 'HTTP', count: configForms.value.length },
  { key: 'variables' as const, label: '变量', count: variableCount.value },
  { key: 'datasources' as const, label: '数据源', count: dsForms.value.length },
  { key: 'preprocessors' as const, label: '前置处理器', count: preProcCount.value },
  { key: 'postprocessors' as const, label: '后置处理器', count: postProcCount.value },
])
</script>

<template>
  <div v-loading="loading" class="env-detail">
    <div v-if="loadError" class="env-detail__empty">
      <p>环境详情加载失败</p>
      <el-button @click="load">重试</el-button>
    </div>

    <template v-else-if="detail">
      <!-- 吸顶区：环境标识 + 未保存标记 + 保存全部，其下为计数页签 -->
      <div class="env-detail__top">
        <div class="env-detail__head">
          <div class="env-detail__id">
            <span class="env-detail__name">{{ detail.name }}</span>
            <el-tag v-if="detail.isDefault" size="small" type="warning" effect="light">默认</el-tag>
          </div>
          <div class="env-detail__head-actions">
            <el-tag v-if="dirty" size="small" type="warning" effect="light">未保存</el-tag>
            <el-button v-if="canEdit" type="primary" size="small" :loading="saving" @click="saveAll">保存全部</el-button>
          </div>
        </div>

        <div class="etabs" role="tablist">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="etab"
            :class="{ 'is-active': activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
            <span class="etab__count">{{ tab.count }}</span>
          </button>
        </div>
      </div>

      <!-- ============ HTTP 默认配置 ============ -->
      <div v-show="activeTab === 'http'" class="epanel">
        <div class="cfg-split">
          <div class="cfg-panel">
            <ul class="cfg-list">
              <li
                v-for="form in orderedConfigForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeConfigId }"
                @click="selectConfig(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit" class="cfg-add" @click="addHttpConfig">＋ 新增配置</li>
            </ul>
          </div>

          <div v-if="activeConfig" class="cfg-form">
            <el-form label-position="top" :disabled="!canEdit">
              <div class="cfg-form__grid">
                <el-form-item label="名称" required>
                  <el-input v-model="activeConfig.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名" required>
                  <el-input v-model="activeConfig.refName" placeholder="场景中通过该名引用此配置" />
                </el-form-item>
                <el-form-item label="Base URL" required>
                  <el-input v-model="activeConfig.baseUrl" placeholder="https://api.example.com" />
                </el-form-item>
                <el-form-item label="设为默认">
                  <el-switch v-model="activeConfig.isDefault" />
                  <span class="env-detail__hint">同一环境内至多一个默认</span>
                </el-form-item>
              </div>
            </el-form>

            <div class="env-detail__headers">
              <div class="env-detail__section-title">请求头</div>
              <KeyValueTable v-model:entries="activeConfig.headers" placeholder-key="Header" :disabled="!canEdit" />
            </div>

            <div class="env-detail__actions">
              <el-button :loading="testingHttpId === activeConfig.id" @click="runHttpTest(activeConfig, props.environmentId)">
                连接测试
              </el-button>
              <span
                v-if="httpConnResult"
                class="conn-result"
                :class="httpConnResult.ok ? 'conn-result--ok' : 'conn-result--fail'"
              >
                {{ httpConnResult.text }}
              </span>
              <el-button
                v-if="canEdit"
                class="env-detail__actions-remove"
                type="danger"
                plain
                @click="removeHttpConfig(activeConfig)"
              >
                删除配置
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 全局变量 ============ -->
      <div v-show="activeTab === 'variables'" class="epanel">
        <KeyValueTable
          v-model:entries="variableRows"
          placeholder-key="变量名"
          show-description
          :show-enabled="false"
          :disabled="!canEdit"
          header-add
        />
        <p class="env-detail__syntax-tip">
          引用语法：<code>${变量名}</code>，如 <code>${BASE_URL}</code>
        </p>
      </div>

      <!-- ============ 数据源（与 HTTP 同构：左列表 + 右内联表单） ============ -->
      <div v-show="activeTab === 'datasources'" class="epanel">
        <div class="cfg-split">
          <div class="cfg-panel">
            <ul class="cfg-list">
              <li
                v-for="form in orderedDsForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeDsId }"
                @click="selectDs(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit" class="cfg-add" @click="addDataSource">＋ 新增数据源</li>
            </ul>
          </div>

          <div v-if="activeDs" class="cfg-form">
            <el-form label-position="top" :disabled="!canEdit">
              <div class="cfg-form__grid">
                <el-form-item label="名称" required>
                  <el-input v-model="activeDs.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名" required>
                  <el-input v-model="activeDs.refName" placeholder="场景中通过该名引用此数据源" />
                </el-form-item>
                <el-form-item label="驱动" required>
                  <el-select v-model="activeDs.driver" @change="handleDsDriverChange">
                    <el-option
                      v-for="option in DRIVER_OPTIONS"
                      :key="option.label"
                      :label="option.label"
                      :value="option.driver"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="设为默认">
                  <el-switch v-model="activeDs.isDefault" />
                  <span class="env-detail__hint">同一环境内至多一个默认</span>
                </el-form-item>
                <el-form-item class="cfg-form__grid-full" label="URL" required>
                  <el-input
                    v-model="activeDs.url"
                    type="textarea"
                    :rows="2"
                    :placeholder="selectedDsDriverOption?.urlExample"
                  />
                  <span class="env-detail__hint env-detail__hint--block">用户名/密码通过 URL 设置</span>
                </el-form-item>
              </div>
            </el-form>

            <div class="env-detail__actions">
              <el-button :loading="testingDsId === activeDs.id" @click="runDsTest(activeDs, props.environmentId)">
                连接测试
              </el-button>
              <span
                v-if="dsConnResult"
                class="conn-result"
                :class="dsConnResult.ok ? 'conn-result--ok' : 'conn-result--fail'"
              >
                {{ dsConnResult.text }}
              </span>
              <el-button
                v-if="canEdit"
                class="env-detail__actions-remove"
                type="danger"
                plain
                @click="removeDataSource(activeDs)"
              >
                删除数据源
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 前置处理器 ============ -->
      <div v-show="activeTab === 'preprocessors'" class="epanel">
        <EnvironmentProcessorPane
          processor-type="preprocessor"
          title="前置处理器"
          empty-description="暂无前置处理器"
          :count="preProcCount"
          :can-edit="canEdit"
          :processors="procList('preprocessor')"
          :active-proc-id="activeProcId"
          :selected-processor="selectedProcessor"
          :proc-testclass="procTestclass"
          :proc-http-ref="procHttpRef"
          :proc-ds-ref="procDsRef"
          :proc-http-ref-options="procHttpRefOptions"
          :proc-ds-ref-options="procDsRefOptions"
          :config-forms="configForms"
          :ds-forms="dsForms"
          :proc-tags="procTags"
          :proc-display-name="procDisplayName"
          @select="selectProcessor"
          @add="addProcessor('preprocessor')"
          @remove="removeProcessor"
          @move="(i, d) => moveProcessor('preprocessor', i, d)"
          @copy="copyProcessor"
          @import="openProcessorAssetPicker('preprocessor')"
          @import-extractors="openExtractorPicker"
        />
      </div>

      <!-- ============ 后置处理器 ============ -->
      <div v-show="activeTab === 'postprocessors'" class="epanel">
        <EnvironmentProcessorPane
          processor-type="postprocessor"
          title="后置处理器"
          empty-description="暂无后置处理器"
          :count="postProcCount"
          :can-edit="canEdit"
          :processors="procList('postprocessor')"
          :active-proc-id="activeProcId"
          :selected-processor="selectedProcessor"
          :proc-testclass="procTestclass"
          :proc-http-ref="procHttpRef"
          :proc-ds-ref="procDsRef"
          :proc-http-ref-options="procHttpRefOptions"
          :proc-ds-ref-options="procDsRefOptions"
          :config-forms="configForms"
          :ds-forms="dsForms"
          :proc-tags="procTags"
          :proc-display-name="procDisplayName"
          @select="selectProcessor"
          @add="addProcessor('postprocessor')"
          @remove="removeProcessor"
          @move="(i, d) => moveProcessor('postprocessor', i, d)"
          @copy="copyProcessor"
          @import="openProcessorAssetPicker('postprocessor')"
          @import-extractors="openExtractorPicker"
        />
      </div>
    </template>

    <!-- 从公共组件引入处理器 -->
    <ExtractorAssetPicker
      v-model="procAssetPickerVisible"
      :loading="procAssetPickerLoading"
      :items="procAssetPickerItems"
      :keyword="procAssetPickerKeyword"
      title="从公共组件引入处理器"
      tip="仅展示启用的处理器资产；引入为复制，得到独立副本，与源资产无关联。"
      empty-text="暂无可用处理器"
      search-placeholder="搜索处理器名称..."
      @update:keyword="procAssetPickerKeyword = $event"
      @search="loadProcAssets"
      @confirm="handleProcessorAssetPicked"
    />

    <!-- 从公共组件引入提取器 -->
    <ExtractorAssetPicker
      v-model="extractorPickerVisible"
      :loading="extractorPickerLoading"
      :items="extractorPickerItems"
      :keyword="extractorPickerKeyword"
      @update:keyword="extractorPickerKeyword = $event"
      @search="loadExtractorAssets"
      @confirm="handleExtractorPicked"
    />
  </div>
</template>

<style scoped lang="scss">
/* 右：详情（头部与页签吸顶，滚动时上下文不丢失） */
.env-detail {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: 0 var(--space-lg) var(--space-lg);
  min-height: 320px;
}

.env-detail__top {
  position: sticky;
  top: 0;
  z-index: 2;
  margin: 0 calc(0px - var(--space-lg));
  padding: var(--space-md) var(--space-lg) 0;
  background: var(--color-neutral-0);
  border-bottom: 1px solid var(--color-neutral-100);
}

.env-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.env-detail__id {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.env-detail__name {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-neutral-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.env-detail__head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.etabs {
  display: flex;
  align-items: center;
  gap: 2px;
}

.etab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  margin-bottom: -1px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-500);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--transition-fast), background var(--transition-fast);

  &:hover {
    color: var(--color-neutral-700);
    background: var(--color-neutral-50);
  }

  &.is-active {
    color: var(--color-primary-500);
    font-weight: 600;
    background: none;
    border-bottom-color: var(--color-primary-500);
  }
}

.etab__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--color-neutral-100);
  color: var(--color-neutral-500);
  font-size: var(--font-size-2xs);
  font-weight: 600;
}

.etab.is-active .etab__count {
  background: var(--color-primary-50);
  color: var(--color-primary-500);
}

.epanel {
  padding-top: var(--space-md);
}

/* 配置区（HTTP / 数据源同构）：左右各一张白底描边卡片，靠并列盒子区分列表与表单 */
.cfg-split {
  display: flex;
  gap: var(--space-lg);
  align-items: flex-start;
}

.cfg-panel {
  width: 216px;
  flex-shrink: 0;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
  padding: 6px;
}

.cfg-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;

  li {
    padding: 7px 10px;
    border: 1px solid transparent;
    border-radius: var(--radius-md);
    font-size: var(--font-size-sm);
    color: var(--color-neutral-600);
    cursor: pointer;

    &:hover {
      background: var(--color-neutral-50);
    }

    &.is-active {
      background: var(--color-primary-50);
      border-color: var(--color-primary-300);
      color: var(--color-primary-600);
      font-weight: 600;
    }
  }

  .cfg-add {
    color: var(--color-primary-500);

    &:hover {
      color: var(--color-primary-600);
    }
  }
}

/* 窄窗口下详情列不足时保持卡片完整可读，溢出由详情列横向滚动承载 */
.cfg-form {
  flex: 1 1 auto;
  min-width: 340px;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
  padding: var(--space-md);
}

.cfg-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--space-lg);
}

.cfg-form__grid-full {
  grid-column: 1 / -1;
}

.env-detail__headers {
  margin-top: var(--space-md);
}

.env-detail__section-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  margin-bottom: var(--space-sm);
}

/* 测试连接：结果内联显示在按钮旁，失败可重试（Toast 同步提示，docs34 §1.5） */
.env-detail__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-100);
  flex-wrap: wrap;
}

.env-detail__actions-remove {
  margin-left: auto;
}

.conn-result {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);

  &--ok {
    color: var(--color-success-strong);
  }

  &--fail {
    color: var(--color-danger-strong);
  }
}

.env-detail__hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);

  &--block {
    flex-basis: 100%;
    margin-left: 0;
    margin-top: 4px;
  }
}

.env-detail__empty {
  text-align: center;
  padding: var(--space-xl) 0;
  color: var(--color-neutral-400);

  p {
    margin-bottom: var(--space-sm);
  }
}

.env-detail__syntax-tip {
  margin: var(--space-sm) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);

  code {
    font-family: var(--font-mono);
    background: var(--color-neutral-50);
    padding: 0 4px;
    border-radius: var(--radius-sm, 3px);
  }
}
</style>
