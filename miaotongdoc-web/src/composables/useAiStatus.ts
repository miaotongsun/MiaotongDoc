/**
 * useAiStatus —— AI 服务配置状态
 *
 * 调用 /api/ai/status 查询当前各 AI Provider 是否配置:
 * - llm: 大语言模型(MD/PDF AI)
 * - vision: 视觉问答(PDF VLM)
 * - ocrPaddle: PaddleOCR 服务
 * - docling: 文档解析
 *
 * 用于:
 * 1. 编辑器启动时缓存状态
 * 2. AI 按钮点击前判断是否可用
 * 3. 未配置时引导用户去管理后台
 */
import { ref, computed, onMounted } from 'vue'

export interface AiStatusDetail {
  configured: boolean
  name?: string
  defaultModel?: string
  baseUrl?: boolean
  available?: boolean
}

export interface AiStatus {
  llm: AiStatusDetail
  vision: AiStatusDetail
  ocrPaddle: AiStatusDetail
  docling: AiStatusDetail
  anyAvailable: boolean
}

let _cache: { value: AiStatus | null; ts: number } = { value: null, ts: 0 }
const CACHE_TTL_MS = 30_000 // 30s 缓存

export function useAiStatus() {
  const status = ref<AiStatus | null>(_cache.value)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function refresh(force = false) {
    if (!force && _cache.value && Date.now() - _cache.ts < CACHE_TTL_MS) {
      status.value = _cache.value
      return
    }
    loading.value = true
    error.value = null
    try {
      const token = sessionStorage.getItem('token') || ''
      const r = await fetch('/api/ai/status', {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!r.ok) {
        throw new Error(`HTTP ${r.status}`)
      }
      const data = await r.json()
      const norm: AiStatus = {
        llm: data.llm || { configured: false },
        vision: data.vision || { configured: false },
        ocrPaddle: data.ocrPaddle || { configured: false },
        docling: data.docling || { configured: false },
        anyAvailable: !!data.anyAvailable,
      }
      _cache = { value: norm, ts: Date.now() }
      status.value = norm
    } catch (e: any) {
      error.value = e?.message || '查询 AI 状态失败'
    } finally {
      loading.value = false
    }
  }

  function isAvailable(type: 'llm' | 'vision' | 'ocrPaddle' | 'docling'): boolean {
    if (!status.value) return false
    return status.value[type]?.configured === true
  }

  function isAnyAvailable(): boolean {
    return status.value?.anyAvailable === true
  }

  function reset() {
    _cache = { value: null, ts: 0 }
    status.value = null
  }

  const llmConfigured = computed(() => isAvailable('llm'))
  const visionConfigured = computed(() => isAvailable('vision'))

  return {
    status,
    loading,
    error,
    refresh,
    reset,
    isAvailable,
    isAnyAvailable,
    llmConfigured,
    visionConfigured,
  }
}