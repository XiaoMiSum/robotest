<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { ProposalRow } from '@/composables/project/ai/useAiProposalReview'
import { reviewProfile } from '@/composables/project/ai/reviewProfiles'
import ProposalCompareView from './ProposalCompareView.vue'

const props = defineProps<{
  row: ProposalRow
  taskType: string
}>()

const router = useRouter()

const isGeneration = computed(() => reviewProfile(props.taskType).panel === 'tree')
const content = computed(() => props.row.content)
const parsed = computed(() => props.row.parsed)

const PRIORITY_LABEL: Record<string, string> = {
  high: 'P0·高',
  medium: 'P1·中',
  low: 'P2·低',
}

function priorityLabel(priority: string): string {
  return PRIORITY_LABEL[priority] ?? priority
}

const hasDetail = computed(() => {
  if (isGeneration.value) {
    const item = content.value
    return Boolean(
      item.description ||
      item.priority ||
      item.attributes.precondition ||
      item.attributes.tags.length > 0 ||
      item.attributes.steps.length > 0 ||
      item.sourceRefs.length > 0 ||
      item.suspectedDuplicateOf,
    )
  }
  const item = parsed.value
  return Boolean(
    item.description ||
    item.fields.length > 0 ||
    item.compare.length > 0 ||
    item.rankItems.length > 0 ||
    item.sourceRefs.length > 0 ||
    item.warnings.length > 0,
  )
})

function goRequirement(requirementId: string): void {
  if (requirementId) void router.push(`/workspace/projects/requirements/${requirementId}`)
}
</script>

<template>
  <div class="adoption-detail">
    <p v-if="!hasDetail" class="adoption-detail__empty">暂无明细</p>

    <template v-else-if="isGeneration">
      <p v-if="content.description" class="adoption-detail__desc">{{ content.description }}</p>
      <dl
        v-if="
          content.priority || content.attributes.precondition || content.attributes.tags.length > 0
        "
        class="adoption-detail__fields"
      >
        <div v-if="content.priority" class="adoption-detail__field">
          <dt>优先级</dt>
          <dd>{{ priorityLabel(content.priority) }}</dd>
        </div>
        <div v-if="content.attributes.precondition" class="adoption-detail__field">
          <dt>前置条件</dt>
          <dd>{{ content.attributes.precondition }}</dd>
        </div>
        <div v-if="content.attributes.tags.length > 0" class="adoption-detail__field">
          <dt>标签</dt>
          <dd>{{ content.attributes.tags.join('、') }}</dd>
        </div>
      </dl>
      <ol v-if="content.attributes.steps.length > 0" class="adoption-detail__steps">
        <li
          v-for="(step, index) in content.attributes.steps"
          :key="index"
          class="adoption-detail__step"
        >
          <span class="adoption-detail__label">步骤 {{ index + 1 }}</span>
          <span>{{ step }}</span>
          <span class="adoption-detail__expected">
            预期：{{ content.attributes.expected[index] || '—' }}
          </span>
        </li>
      </ol>
      <div v-if="content.sourceRefs.length > 0" class="adoption-detail__sources">
        <p class="adoption-detail__label">来源需求</p>
        <div
          v-for="(source, index) in content.sourceRefs"
          :key="`${source.requirementId}-${index}`"
          class="adoption-detail__source"
        >
          <el-link type="primary" underline="never" @click="goRequirement(source.requirementId)">
            {{ source.quote || '查看需求原文' }}
          </el-link>
          <el-tag v-if="source.changed" size="small" type="warning" effect="light">已变更</el-tag>
        </div>
      </div>
      <el-alert
        v-if="content.suspectedDuplicateOf"
        type="warning"
        title="疑似与既有数据重复"
        show-icon
        :closable="false"
        class="adoption-detail__alert"
      />
      <el-alert
        v-if="content.sourceRefs.some((source) => source.changed)"
        type="warning"
        title="来源需求已变更，请核对原文"
        show-icon
        :closable="false"
        class="adoption-detail__alert"
      />
    </template>

    <template v-else>
      <p v-if="parsed.description || content.description" class="adoption-detail__desc">
        {{ parsed.description || content.description }}
      </p>
      <ProposalCompareView :items="parsed.compare" />
      <dl v-if="parsed.fields.length > 0" class="adoption-detail__fields">
        <div
          v-for="(item, index) in parsed.fields"
          :key="`${item.label}-${index}`"
          class="adoption-detail__field"
        >
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
      <ol v-if="parsed.rankItems.length > 0" class="adoption-detail__rank">
        <li v-for="item in parsed.rankItems" :key="item.nodeId">
          <span>{{ item.rank }}. {{ item.title }}</span>
          <span v-if="item.reason" class="adoption-detail__expected">{{ item.reason }}</span>
        </li>
      </ol>
      <div v-if="parsed.sourceRefs.length > 0" class="adoption-detail__sources">
        <p class="adoption-detail__label">来源引用</p>
        <div
          v-for="(source, index) in parsed.sourceRefs"
          :key="`${source.id}-${index}`"
          class="adoption-detail__source"
        >
          <el-link type="primary" underline="never" @click="goRequirement(source.id)">
            {{ source.title || source.id || '查看来源原文' }}
          </el-link>
          <span class="adoption-detail__expected">{{ source.quote }}</span>
          <el-tag v-if="source.changed" size="small" type="warning" effect="light">已变更</el-tag>
        </div>
      </div>
      <el-alert
        v-for="(warning, index) in parsed.warnings"
        :key="index"
        type="warning"
        :title="warning"
        show-icon
        :closable="false"
        class="adoption-detail__alert"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
.adoption-detail {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}

.adoption-detail__empty,
.adoption-detail__desc {
  margin: 0 0 var(--space-sm);
}

.adoption-detail__empty {
  color: var(--color-neutral-500);
}

.adoption-detail__label {
  margin-right: var(--space-xs);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.adoption-detail__fields {
  margin: 0 0 var(--space-sm);
}

.adoption-detail__field {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;

  dt {
    flex: 0 0 72px;
    color: var(--color-neutral-500);
  }

  dd {
    flex: 1;
    margin: 0;
  }
}

.adoption-detail__steps,
.adoption-detail__rank {
  margin: 0 0 var(--space-sm);
  padding-left: var(--space-md);
}

.adoption-detail__step,
.adoption-detail__rank li {
  margin-bottom: var(--space-xs);
  line-height: 1.6;
}

.adoption-detail__expected {
  display: block;
  color: var(--color-neutral-500);
}

.adoption-detail__sources {
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.adoption-detail__source {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-xs) 0;
}

.adoption-detail__alert {
  margin-top: var(--space-sm);
}
</style>
