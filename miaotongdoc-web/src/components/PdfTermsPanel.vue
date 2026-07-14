<!--
  PdfTermsPanel —— 关键条款抽取结果面板
  显示合同 PDF 的关键字段（金额、日期、甲方乙方、违约责任等）
-->
<template>
  <div class="terms-panel" :class="{ 'is-empty': !hasAnyValue }">
    <!-- 头部 -->
    <div class="terms-header">
      <div class="terms-title">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="9" y1="13" x2="15" y2="13" />
          <line x1="9" y1="17" x2="13" y2="17" />
        </svg>
        <span>关键条款</span>
        <span v-if="extractor.model.value" class="terms-engine">{{ extractor.model.value }}</span>
      </div>
      <button
        class="terms-refresh"
        :disabled="extractor.status.value === 'streaming'"
        @click="onExtract"
      >
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" :class="{ 'is-spinning': extractor.status.value === 'streaming' }" aria-hidden="true">
          <polyline points="23 4 23 10 17 10" />
          <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
        </svg>
        {{ extractor.status.value === 'streaming' ? '抽取中...' : '刷新' }}
      </button>
    </div>

    <!-- 错误状态 -->
    <div v-if="extractor.error.value" class="terms-error">
      <span>⚠️ {{ extractor.error.value }}</span>
      <button class="terms-retry" @click="onExtract">重试</button>
    </div>

    <!-- 加载骨架 -->
    <div v-else-if="extractor.status.value === 'streaming'" class="terms-loading">
      <div v-for="i in 4" :key="i" class="term-row">
        <div class="skeleton-label" />
        <div class="skeleton-value" />
      </div>
      <div class="terms-streaming-hint">
        <span class="spinner" aria-hidden="true" />
        LLM 正在阅读合同并抽取关键条款...
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!hasAnyValue" class="terms-empty">
      <div class="terms-empty-icon">📋</div>
      <div class="terms-empty-text">尚未抽取关键条款</div>
      <button class="terms-extract-btn" @click="onExtract">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 2L2 7l10 5 10-5-10-5z" />
          <polyline points="2 17 12 22 22 17" />
          <polyline points="2 12 12 17 22 12" />
        </svg>
        一键抽取
      </button>
    </div>

    <!-- 结果字段列表 -->
    <div v-else class="terms-list">
      <div
        v-for="(value, key) in extractor.terms.value"
        :key="key"
        class="term-row"
        :class="{ 'is-empty': !value || value === '未提及' }"
      >
        <div class="term-label">
          {{ TERM_FIELD_LABELS[key] || key }}
        </div>
        <div class="term-value" :title="value || '未提及'">
          {{ value || '—' }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { usePdfExtractTerms, TERM_FIELD_LABELS } from '@/composables/pdf/usePdfExtractTerms'

const props = defineProps<{
  docId: number
}>()

const extractor = usePdfExtractTerms({ docId: props.docId })

const hasAnyValue = computed(() => {
  return Object.values(extractor.terms.value).some(
    v => v && v !== '未提及' && v !== 'null',
  )
})

async function onExtract() {
  await extractor.extract()
}

onMounted(() => {
  // 组件挂载时如果有 term 数据就不重新抽，否则自动抽取
  if (!hasAnyValue.value) {
    onExtract()
  }
})
</script>

<style scoped>
.terms-panel {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--pdf-border);
  background: var(--pdf-bg);
  font-size: 12px;
  max-height: 320px;
  overflow: hidden;
}

.terms-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--pdf-border);
  background: var(--pdf-bg-surface);
  flex-shrink: 0;
}

.terms-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--pdf-text);
  font-size: 12px;
}

.terms-engine {
  font-size: 10px;
  color: var(--pdf-text-subtle);
  font-weight: 400;
  padding: 1px 6px;
  background: var(--pdf-bg);
  border-radius: 8px;
}

.terms-refresh {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid var(--pdf-border);
  border-radius: 6px;
  background: var(--pdf-bg-surface);
  font-size: 11px;
  color: var(--pdf-primary);
  cursor: pointer;
  transition: all 0.15s;
}
.terms-refresh:hover:not(:disabled) {
  background: var(--pdf-primary-soft);
  border-color: var(--pdf-primary);
}
.terms-refresh:disabled { opacity: 0.6; cursor: not-allowed; }
.terms-refresh svg.is-spinning { animation: spin 0.8s linear infinite; }

.terms-list,
.terms-loading {
  overflow-y: auto;
  padding: 6px 0;
}

.term-row {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--pdf-border);
  align-items: center;
  transition: background 0.15s;
}
.term-row:last-child { border-bottom: none; }
.term-row:hover { background: var(--pdf-bg-surface); }

.term-label {
  font-size: 11px;
  color: var(--pdf-text-muted);
  font-weight: 500;
  white-space: nowrap;
}
.term-value {
  font-size: 12px;
  color: var(--pdf-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.term-row.is-empty .term-value {
  color: var(--pdf-text-subtle);
  font-style: italic;
}

/* 骨架 */
.skeleton-label,
.skeleton-value {
  height: 12px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--pdf-border) 0%, #F5F7FA 50%, var(--pdf-border) 100%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}
.skeleton-label { width: 60px; }
.skeleton-value { width: 80%; }

.terms-streaming-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 11px;
  color: var(--pdf-primary);
}
.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--pdf-primary-soft);
  border-top-color: var(--pdf-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* 空状态 */
.terms-empty {
  padding: 24px 16px;
  text-align: center;
  color: var(--pdf-text-muted);
}
.terms-empty-icon { font-size: 28px; margin-bottom: 6px; }
.terms-empty-text { font-size: 12px; margin-bottom: 12px; }
.terms-extract-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: var(--pdf-primary);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.terms-extract-btn:hover {
  filter: brightness(1.08);
  transform: translateY(-1px);
}

/* 错误 */
.terms-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #FEF0F0;
  color: var(--pdf-danger);
  font-size: 11px;
}
.terms-retry {
  padding: 2px 8px;
  border: 1px solid var(--pdf-danger);
  border-radius: 4px;
  background: transparent;
  color: var(--pdf-danger);
  cursor: pointer;
  font-size: 11px;
}

@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
@keyframes spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .skeleton-label, .skeleton-value, .terms-refresh svg.is-spinning, .spinner {
    animation: none !important;
  }
}
</style>