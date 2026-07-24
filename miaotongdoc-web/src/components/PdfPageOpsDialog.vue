<!--
  PdfPageOpsDialog.vue —— Phase 11 页面操作对话框

  4 种操作:
  - insertBlank: 插入空白页(afterPage: 当前页之后)
  - crop: 裁剪(x/y/width/height + 选中页)
  - watermark: 文字水印(text/opacity/rotation/pages)
  - headerFooter: 页眉页脚(position/content/fontSize/pages)

  UI 用 el-tabs 切换不同操作,共用同一对话框
-->
<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="520px"
    :close-on-click-modal="false"
    custom-class="pdf-dialog page-ops-dialog"
    @close="onClose"
  >
    <el-tabs v-model="activeTab" class="pdf-page-ops-tabs">
      <!-- 插入空白页 -->
      <el-tab-pane label="插入空白页" name="insertBlank">
        <el-form label-width="100px">
          <el-form-item label="插入位置">
            <el-radio-group v-model="insertAfter">
              <el-radio :value="0">末尾</el-radio>
              <el-radio :value="1">第 {{ currentPage }} 页之后</el-radio>
            </el-radio-group>
          </el-form-item>
          <div class="pdf-ops-hint">空白页将基于原文档首页尺寸创建</div>
        </el-form>
      </el-tab-pane>

      <!-- 裁剪 -->
      <el-tab-pane label="裁剪" name="crop">
        <el-form label-width="100px">
          <el-form-item label="裁剪范围">
            <el-radio-group v-model="cropTarget">
              <el-radio :value="0">当前页({{ currentPage }})</el-radio>
              <el-radio :value="1">全部页面</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="X (左)">
            <el-input-number v-model="cropBox.x" :min="0" :step="10" />
          </el-form-item>
          <el-form-item label="Y (下)">
            <el-input-number v-model="cropBox.y" :min="0" :step="10" />
          </el-form-item>
          <el-form-item label="宽度">
            <el-input-number v-model="cropBox.width" :min="1" :step="10" />
          </el-form-item>
          <el-form-item label="高度">
            <el-input-number v-model="cropBox.height" :min="1" :step="10" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 水印(Phase 14.U4:重设计) -->
      <el-tab-pane label="水印" name="watermark">
        <el-form label-width="100px">
          <el-form-item label="水印文字">
            <el-input v-model="watermark.text" placeholder="CONFIDENTIAL / 公司机密 / 仅供..." maxlength="32" />
          </el-form-item>
          <el-form-item label="不透明度">
            <el-slider v-model="watermark.opacity" :min="0.05" :max="1" :step="0.05" show-input :show-input-controls="false" />
          </el-form-item>
          <el-form-item label="位置">
            <el-select v-model="watermark.position" style="width: 100%">
              <el-option label="对角(经典)" value="diagonal" />
              <el-option label="页眉" value="header" />
              <el-option label="页脚" value="footer" />
              <el-option label="居中" value="center" />
              <el-option label="平铺(防截图)" value="tile" />
            </el-select>
          </el-form-item>
          <el-form-item label="旋转角度" v-if="watermark.position !== 'header' && watermark.position !== 'footer'">
            <el-slider v-model="watermark.rotation" :min="0" :max="90" :step="15" show-input :show-input-controls="false" />
            <div style="font-size:11px;color:var(--color-foreground-3);margin-top:2px">0/15/30/45/60/75/90 度,灵活水平</div>
          </el-form-item>
          <el-form-item label="字号">
            <el-input-number v-model="watermark.fontSize" :min="20" :max="200" :step="10" />
            <span style="margin-left:8px;font-size:11px;color:var(--color-foreground-3)">默认自动</span>
          </el-form-item>
          <el-form-item label="应用范围">
            <el-radio-group v-model="watermark.target">
              <el-radio :value="0">全部</el-radio>
              <el-radio :value="1">当前页({{ currentPage }})</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="watermark.clearExisting">
              覆盖已有水印(重复操作时勾选,避免叠加图层)
            </el-checkbox>
          </el-form-item>
          <el-divider style="margin: 12px 0" />
          <div style="display:flex;gap:8px">
            <el-button type="warning" :loading="loading" @click="onRemoveWatermark" style="flex:1">
              <span style="font-weight:600">⚡ 一键去水印</span>
              <div style="font-size:11px;margin-top:2px">覆盖所有页面水印,不可撤销</div>
            </el-button>
          </div>
        </el-form>
      </el-tab-pane>

      <!-- 页眉页脚(Phase 14.U2:加 clearExisting) -->
      <el-tab-pane label="页眉页脚" name="headerFooter">
        <el-form label-width="100px">
          <el-form-item label="位置">
            <el-radio-group v-model="hf.position">
              <el-radio value="header">页眉</el-radio>
              <el-radio value="footer">页脚</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="文字">
            <el-input v-model="hf.content" placeholder="支持 {page} 和 {total} 占位符" />
          </el-form-item>
          <el-form-item label="字号">
            <el-input-number v-model="hf.fontSize" :min="6" :max="48" :step="1" />
          </el-form-item>
          <el-form-item label="应用范围">
            <el-radio-group v-model="hf.target">
              <el-radio :value="0">全部</el-radio>
              <el-radio :value="1">当前页({{ currentPage }})</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="hf.clearExisting">
              覆盖已有页眉/页脚(默认勾选,可重复操作)
            </el-checkbox>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="onConfirm">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pdfApi } from '@/api/pdf'

const props = defineProps<{
  modelValue: boolean
  /** 文档 ID(用于调用页面操作 API) */
  docId: number
  /** 当前页码 */
  currentPage: number
  /** 当前总页数 */
  totalPages: number
  /** 打开时默认 tab */
  initialTab?: 'insertBlank' | 'crop' | 'watermark' | 'headerFooter'
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success', op: string): void
}>()

const visible = ref(props.modelValue)
const activeTab = ref(props.initialTab || 'insertBlank')
const loading = ref(false)

const insertAfter = ref(0)
const cropTarget = ref(0)
const cropBox = ref({ x: 0, y: 0, width: 400, height: 500 })
const watermark = ref({
  text: 'CONFIDENTIAL',
  opacity: 0.3,
  rotation: 45,
  position: 'diagonal' as 'diagonal' | 'header' | 'footer' | 'center' | 'tile',
  fontSize: 0,
  target: 0,
  clearExisting: true,
})
const hf = ref({
  position: 'footer' as 'header' | 'footer',
  content: 'Page {page} of {total}',
  fontSize: 10,
  target: 0,
  clearExisting: true,
})

const title = computed(() => {
  const map: Record<string, string> = {
    insertBlank: '插入空白页',
    crop: '裁剪页面',
    watermark: '添加水印',
    headerFooter: '页眉页脚',
  }
  return map[activeTab.value] || '页面操作'
})

watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v && props.initialTab) activeTab.value = props.initialTab
})
watch(visible, (v) => emit('update:modelValue', v))

function onClose() {
  visible.value = false
}

async function onConfirm() {
  loading.value = true
  try {
    if (activeTab.value === 'insertBlank') {
      await pdfApi.insertBlankPage(props.docId, insertAfter.value)
      ElMessage.success('已插入空白页')
      emit('success', 'insert-blank')
    } else if (activeTab.value === 'crop') {
      const pages = cropTarget.value === 1 ? [props.currentPage] : allPages()
      await pdfApi.cropPages(props.docId, pages, cropBox.value)
      ElMessage.success(`已裁剪 ${pages.length} 页`)
      emit('success', 'crop')
    } else if (activeTab.value === 'watermark') {
      const pages = watermark.value.target === 1 ? [props.currentPage] : allPages()
      await pdfApi.addWatermark(props.docId, {
        text: watermark.value.text,
        opacity: watermark.value.opacity,
        rotation: watermark.value.rotation,
        position: watermark.value.position,
        fontSize: watermark.value.fontSize,
        clearExisting: watermark.value.clearExisting,
        pages,
      })
      ElMessage.success(watermark.value.clearExisting ? '已替换水印' : '已追加水印')
      emit('success', 'watermark')
    } else if (activeTab.value === 'headerFooter') {
      const pages = hf.value.target === 1 ? [props.currentPage] : allPages()
      await pdfApi.addHeaderFooter(props.docId, {
        position: hf.value.position,
        content: hf.value.content,
        fontSize: hf.value.fontSize,
        clearExisting: hf.value.clearExisting,
        pages,
      })
      ElMessage.success(`${hf.value.clearExisting ? '已替换' : '已追加'}${hf.value.position === 'header' ? '页眉' : '页脚'}`)
      emit('success', 'header-footer')
    }
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    loading.value = false
  }
}

/** Phase 14.U4: 一键去水印 */
async function onRemoveWatermark() {
  try {
    await ElMessageBox.confirm(
      '将清除文档所有水印(自家+自带)。白矩形覆盖整页可能影响背景图。',
      '一键去水印',
      { type: 'warning', confirmButtonText: '确认清除', cancelButtonText: '取消' }
    )
  } catch { return }
  loading.value = true
  try {
    await pdfApi.removeWatermark(props.docId, 'all')
    ElMessage.success('去水印完成')
    emit('success', 'watermark-remove')
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '去水印失败')
  } finally {
    loading.value = false
  }
}

function allPages(): number[] {
  return Array.from({ length: props.totalPages }, (_, i) => i + 1)
}
</script>

<style scoped>
.pdf-page-ops-tabs {
  min-height: 220px;
}
.pdf-ops-hint {
  margin-top: 4px;
  padding-left: 100px;
  font-size: 12px;
  color: var(--color-foreground-3, #909399);
  line-height: 1.5;
}

</style>