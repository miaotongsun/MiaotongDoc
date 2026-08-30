/**
 * MindmapHelpDialog.vue —— MindElixir 5.15 + 自定义快捷键总提示（2026-08-17）
 *
 * 列出所有可用的快捷键 + 工具栏按钮功能。
 * 用户角度：所有功能 + 触发方式一目了然。
 */
<template>
  <el-dialog v-model="visible" title="思维导图快捷键与小技巧" width="640px" :close-on-click-modal="false">
    <div class="help-content">
      <el-collapse v-model="activeNames" accordion>
        <!-- 节点操作 -->
        <el-collapse-item title="节点操作" name="nodes">
          <table class="help-table">
            <thead>
              <tr><th>功能</th><th>快捷键 / 操作</th></tr>
            </thead>
            <tbody>
              <tr><td>添加子节点</td><td><kbd>Tab</kbd> / 工具栏「+子节点」</td></tr>
              <tr><td>插入兄弟节点</td><td><kbd>Enter</kbd> / 工具栏「兄弟」</td></tr>
              <tr><td>插入父节点</td><td><kbd>Ctrl</kbd>+<kbd>Enter</kbd> / 工具栏「父节点」</td></tr>
              <tr><td>复制节点</td><td><kbd>Ctrl</kbd>+<kbd>C</kbd> / 工具栏「复制」</td></tr>
              <tr><td>删除节点</td><td><kbd>Delete</kbd> / 工具栏「删除」</td></tr>
              <tr><td>编辑节点文本</td><td>双击 / <kbd>F2</kbd></td></tr>
              <tr><td>节点上移 / 下移</td><td><kbd>Alt</kbd>+<kbd>↑</kbd> / <kbd>Alt</kbd>+<kbd>↓</kbd></td></tr>
              <tr><td>展开/折叠当前节点</td><td>点击节点前「+/-」号</td></tr>
              <tr><td>展开/折叠全部</td><td>工具栏「全部展开/全部折叠」</td></tr>
            </tbody>
          </table>
        </el-collapse-item>

        <!-- 视图操作 -->
        <el-collapse-item title="视图操作" name="view">
          <table class="help-table">
            <thead><tr><th>功能</th><th>快捷键 / 操作</th></tr></thead>
            <tbody>
              <tr><td>放大</td><td>滚轮向上 / <kbd>Ctrl</kbd>+<kbd>+</kbd> / 工具栏「+」</td></tr>
              <tr><td>缩小</td><td>滚轮向下 / <kbd>Ctrl</kbd>+<kbd>-</kbd> / 工具栏「-」</td></tr>
              <tr><td>缩放到 100%</td><td>工具栏「100%」</td></tr>
              <tr><td>适应窗口</td><td>工具栏「适应」</td></tr>
              <tr><td>居中</td><td><kbd>F1</kbd> / 工具栏「居中」</td></tr>
              <tr><td>切换布局方向</td><td>工具栏「方向」(双向/左/右/下)</td></tr>
              <tr><td>切换主题</td><td>工具栏「主题」(默认/活力/暗色/多彩)</td></tr>
              <tr><td>搜索节点</td><td>工具栏「搜索」</td></tr>
              <tr><td>切换紧凑模式</td><td>工具栏「紧凑」</td></tr>
            </tbody>
          </table>
        </el-collapse-item>

        <!-- 历史 / 撤销 -->
        <el-collapse-item title="历史记录" name="history">
          <table class="help-table">
            <thead><tr><th>功能</th><th>快捷键 / 操作</th></tr></thead>
            <tbody>
              <tr><td>撤销</td><td><kbd>Ctrl</kbd>+<kbd>Z</kbd> / 工具栏「撤销」</td></tr>
              <tr><td>重做</td><td><kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>Z</kbd> / 工具栏「重做」</td></tr>
            </tbody>
          </table>
        </el-collapse-item>

        <!-- 关联与摘要 -->
        <el-collapse-item title="关联与摘要" name="link">
          <table class="help-table">
            <thead><tr><th>功能</th><th>快捷键 / 操作</th></tr></thead>
            <tbody>
              <tr><td>创建关联线</td><td>选中两节点 → 工具栏「关联」</td></tr>
              <tr><td>创建摘要/概要</td><td>选中节点 → 工具栏「摘要」</td></tr>
              <tr><td>专注模式（聚焦一个节点）</td><td>选中节点 → 工具栏「专注」</td></tr>
            </tbody>
          </table>
        </el-collapse-item>

        <!-- 导入导出 -->
        <el-collapse-item title="导入导出" name="io">
          <table class="help-table">
            <thead><tr><th>功能</th><th>操作</th></tr></thead>
            <tbody>
              <tr><td>导出 JSON</td><td>工具栏「JSON」</td></tr>
              <tr><td>导出 PNG</td><td>工具栏「PNG」</td></tr>
              <tr><td>导出 SVG</td><td>工具栏「SVG」</td></tr>
              <tr><td>导入 JSON</td><td>工具栏「导入」</td></tr>
            </tbody>
          </table>
        </el-collapse-item>

        <!-- AI 助手 -->
        <el-collapse-item title="AI 助手（4 能力）" name="ai">
          <table class="help-table">
            <thead><tr><th>功能</th><th>操作</th></tr></thead>
            <tbody>
              <tr><td>AI 生成思维导图</td><td>工具栏「AI 生成」→ 输入主题</td></tr>
              <tr><td>AI 节点扩写</td><td>选中节点 → 工具栏「AI 扩写」</td></tr>
              <tr><td>AI 总结</td><td>工具栏「AI 总结」</td></tr>
              <tr><td>AI 智能图标</td><td>生成/扩写时自动打 8 选 1-2 图标</td></tr>
            </tbody>
          </table>
        </el-collapse-item>

        <!-- 协同 -->
        <el-collapse-item title="实时协同" name="collab">
          <table class="help-table">
            <thead><tr><th>功能</th><th>说明</th></tr></thead>
            <tbody>
              <tr><td>多人同时编辑</td><td>同一 URL 多个 tab 自动同步</td></tr>
              <tr><td>在线用户显示</td><td>底部协同栏显示当前在线用户头像</td></tr>
              <tr><td>连接状态</td><td>底部左侧绿点=已连接，红点=连接中</td></tr>
            </tbody>
          </table>
        </el-collapse-item>
      </el-collapse>

      <div class="help-tip">
        💡 提示：右键节点查看更多操作（插入子/兄弟/删除/上下移/摘要/关联等）。
      </div>
    </div>
    <template #footer>
      <el-button type="primary" @click="visible = false">知道了</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const visible = ref(false)
const activeNames = ref('nodes')

function show() {
  activeNames.value = 'nodes'
  visible.value = true
}
function hide() {
  visible.value = false
}

defineExpose({ show, hide })
</script>

<style scoped>
.help-content {
  max-height: 60vh;
  overflow-y: auto;
}

.help-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.help-table th,
.help-table td {
  text-align: left;
  padding: 6px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.help-table th {
  background: var(--mindmap-primary-50, #FFFBEB);
  color: var(--mindmap-primary-dark, #B45309);
  font-weight: 600;
  font-size: 12px;
}

.help-table td:first-child {
  font-weight: 500;
  color: #303133;
  width: 30%;
}

.help-table kbd {
  display: inline-block;
  padding: 2px 6px;
  font-size: 11px;
  font-family: monospace;
  color: #fff;
  background: #4a4a4a;
  border-radius: 3px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.2);
  margin: 0 2px;
}

.help-tip {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--mindmap-primary-50, #FFFBEB);
  border-left: 3px solid var(--mindmap-primary, #F59E0B);
  border-radius: 4px;
  font-size: 13px;
  color: #78350F;
}
</style>