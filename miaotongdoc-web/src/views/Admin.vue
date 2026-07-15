<template>
  <div class="admin-page">
    <div class="admin-body">
      <el-tabs v-model="activeTab" class="admin-tabs" @tab-change="handleTabChange">

        <!-- 用户管理 -->
        <el-tab-pane label="用户管理" name="users">
          <div class="tab-header">
            <el-input v-model="userSearch" placeholder="搜索工号、姓名、用户名..." style="width: 280px" clearable @clear="loadUsers" @keyup.enter="loadUsers" />
            <div style="flex:1"></div>
            <el-button type="primary" @click="openUserDialog()">
              <el-icon><Plus /></el-icon> 新增用户
            </el-button>
          </div>
          <el-table :data="users" stripe>
            <el-table-column prop="employeeId" label="工号" show-overflow-tooltip />
            <el-table-column prop="realName" label="姓名" show-overflow-tooltip />
            <el-table-column prop="username" label="用户名" show-overflow-tooltip />
            <el-table-column prop="email" label="邮箱" show-overflow-tooltip />
            <el-table-column label="部门" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getDeptName(row.departmentId) }}
              </template>
            </el-table-column>
            <el-table-column label="角色" align="center">
              <template #default="{ row }">
                <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">{{ row.role === 'admin' ? '管理员' : '普通用户' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'success' : 'danger'" size="small" effect="plain">{{ row.isActive ? '正常' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button size="small" text @click="openUserDialog(row)">编辑</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text @click="handleResetPwd(row)">重置密码</el-button>
                  <el-divider direction="vertical" />
                  <el-dropdown trigger="click">
                    <el-button size="small" text type="primary">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click="toggleRole(row)">
                          {{ row.role === 'admin' ? '设为普通用户' : '设为管理员' }}
                        </el-dropdown-item>
                        <el-dropdown-item :divided="true" @click="toggleStatus(row)">
                          <span :style="{ color: row.isActive ? 'var(--el-color-danger)' : 'var(--el-color-success)' }">
                            {{ row.isActive ? '禁用账号' : '启用账号' }}
                          </span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-bar" v-if="userTotal > 0">
            <el-pagination background :total="userTotal" :page-size="userPageSize"
              :current-page="userPage + 1" :page-sizes="[10, 20, 50, 100]"
              @current-change="handleUserPageChange" @size-change="handleUserSizeChange"
              layout="total, sizes, prev, pager, next" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="部门管理" name="departments">
          <div class="tab-header">
            <div style="flex:1"></div>
            <el-button type="primary" @click="openDeptDialog()">
              <el-icon><Plus /></el-icon> 新增部门
            </el-button>
          </div>
          <el-table :data="deptTree" stripe row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
            <el-table-column prop="code" label="部门编码" show-overflow-tooltip />
            <el-table-column prop="name" label="部门名称" show-overflow-tooltip />
            <el-table-column prop="level" label="层级" align="center" />
            <el-table-column prop="sortOrder" label="排序" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'success' : 'danger'" size="small" effect="plain">{{ row.isActive ? '正常' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button size="small" text @click="openDeptDialog(row)">编辑</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text type="primary" @click="openDeptDialog(null, row.id)">新增子部门</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text type="danger" @click="handleDeactivateDept(row)">停用</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 审计日志 -->
        <el-tab-pane label="操作日志" name="audit">
          <div class="tab-header">
            <el-date-picker v-model="auditDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
            <el-select v-model="auditActionFilter" placeholder="操作类型" clearable style="width: 140px">
              <el-option v-for="(label, key) in actionLabels" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button type="primary" @click="handleAuditSearch">
              <el-icon><Search /></el-icon> 查询
            </el-button>
            <el-button @click="handleAuditReset">重置</el-button>
          </div>
          <el-table :data="auditLogs" stripe>
            <el-table-column label="时间" show-overflow-tooltip>
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="userName" label="操作用户" show-overflow-tooltip />
            <el-table-column label="操作类型">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ actionLabels[row.action] || row.action }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="resourceType" label="资源类型" show-overflow-tooltip />
            <el-table-column prop="ipAddress" label="IP 地址" show-overflow-tooltip />
          </el-table>
          <div class="pagination-bar" v-if="auditTotal > 0">
            <el-pagination background :total="auditTotal" :page-size="20"
              :current-page="auditPage + 1" @current-change="handleAuditPageChange"
              layout="total, prev, pager, next" />
          </div>
        </el-tab-pane>

        <!-- 水印配置 -->
        <el-tab-pane label="水印配置" name="watermark">
          <div class="watermark-config">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>文档水印设置</span>
                  <el-switch v-model="watermarkConfig.isEnabled" @change="saveWatermarkConfig" active-text="启用" inactive-text="禁用" />
                </div>
              </template>
              <el-form :model="watermarkConfig" label-width="100px" class="watermark-form">
                <el-form-item label="水印文字">
                  <el-input v-model="watermarkConfig.textTemplate" placeholder="{username} {datetime}" @change="saveWatermarkConfig" />
                  <div class="form-tip">支持变量：{username} 用户名、{datetime} 日期时间、{date} 日期</div>
                </el-form-item>
                <el-form-item label="字号">
                  <el-slider v-model="watermarkConfig.fontSize" :min="10" :max="60" @change="saveWatermarkConfig" />
                </el-form-item>
                <el-form-item label="颜色">
                  <el-color-picker v-model="watermarkConfig.fontColor" @change="saveWatermarkConfig" />
                </el-form-item>
                <el-form-item label="透明度">
                  <el-slider v-model="watermarkConfig.opacity" :min="0.1" :max="1" :step="0.1" @change="saveWatermarkConfig" />
                </el-form-item>
                <el-form-item label="旋转角度">
                  <el-slider v-model="watermarkConfig.rotation" :min="-90" :max="90" @change="saveWatermarkConfig" />
                </el-form-item>
                <el-form-item label="位置">
                  <el-radio-group v-model="watermarkConfig.position" @change="saveWatermarkConfig">
                    <el-radio value="center">居中</el-radio>
                    <el-radio value="tiled">平铺</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-form>
              <div class="watermark-preview">
                <div class="preview-label">预览效果</div>
                <div v-if="watermarkConfig.position === 'center'" class="preview-box">
                  <div class="watermark-center" :style="watermarkPreviewStyle">
                    {{ watermarkPreviewText }}
                  </div>
                </div>
                <div v-else class="preview-box-tiled">
                  <div v-for="i in 9" :key="i" class="watermark-tile" :style="watermarkPreviewStyle">
                    {{ watermarkPreviewText }}
                  </div>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <!-- 文档模板 -->
        <el-tab-pane label="文档模板" name="templates">
          <div class="template-management">
            <!-- 顶部操作栏 -->
            <div class="template-toolbar">
              <div class="toolbar-left">
                <el-input v-model="templateSearch" placeholder="搜索模板名称..." clearable style="width: 220px" />
                <el-select v-model="templateTypeFilter" placeholder="文档类型" clearable style="width: 120px">
                  <el-option label="全部类型" value="" />
                  <el-option label="Word" value="word" />
                  <el-option label="Excel" value="cell" />
                  <el-option label="PPT" value="slide" />
                </el-select>
                <el-select v-model="templateCategoryFilter" placeholder="分类" clearable style="width: 120px">
                  <el-option label="全部分类" value="" />
                  <el-option v-for="cat in templateCategories" :key="cat" :label="cat" :value="cat" />
                </el-select>
              </div>
              <div class="toolbar-right">
                <el-button @click="addTemplateCategory">
                  <el-icon><Plus /></el-icon> 新建分类
                </el-button>
                <el-button type="primary" @click="openTemplateDialog()">
                  <el-icon><Plus /></el-icon> 上传模板
                </el-button>
              </div>
            </div>

            <!-- 分类标签 -->
            <div class="category-tabs">
              <div class="category-tab" :class="{ active: !selectedAdminCategory }" @click="selectedAdminCategory = ''">
                全部
              </div>
              <div v-for="cat in templateCategories" :key="cat"
                class="category-tab" :class="{ active: selectedAdminCategory === cat }"
                @click="selectedAdminCategory = cat">
                {{ cat }}
                <el-icon class="tab-delete" @click.stop="deleteTemplateCategory(cat)"><Close /></el-icon>
              </div>
            </div>

            <!-- 模板网格 -->
            <div class="template-grid">
              <div v-for="tpl in pagedTemplates" :key="tpl.id" class="template-card-item">
                <div class="template-card-header">
                  <el-icon class="template-type-icon" :style="{ color: docTypeColor(tpl.docType) }">
                    <Document v-if="tpl.docType === 'word'" />
                    <Grid v-else-if="tpl.docType === 'cell'" />
                    <Picture v-else />
                  </el-icon>
                  <el-tag size="small" :type="tpl.isActive ? 'success' : 'info'">
                    {{ tpl.isActive ? '启用' : '禁用' }}
                  </el-tag>
                </div>
                <div class="template-card-body">
                  <div class="template-card-name">{{ tpl.name }}</div>
                  <div class="template-card-meta">
                    <span>{{ docTypeLabel(tpl.docType) }}</span>
                    <span v-if="tpl.category">· {{ tpl.category }}</span>
                  </div>
                </div>
                <div class="template-card-actions">
                  <el-select v-model="tpl.category" size="small" placeholder="分类" clearable
                    @change="updateTemplateCategory(tpl)" style="width: 100px">
                    <el-option v-for="cat in templateCategories" :key="cat" :label="cat" :value="cat" />
                  </el-select>
                  <el-button size="small" text @click="toggleTemplateStatus(tpl)">
                    {{ tpl.isActive ? '禁用' : '启用' }}
                  </el-button>
                  <el-button v-if="!tpl.isSystem" size="small" text type="danger" @click="deleteTemplate(tpl.id)">删除</el-button>
                </div>
              </div>
              <el-empty v-if="filteredTemplates.length === 0" description="暂无模板" />
            </div>
            <div class="pagination-bar" v-if="filteredTemplates.length > templatePageSize">
              <el-pagination background :total="filteredTemplates.length" :page-size="templatePageSize"
                :current-page="templatePage + 1" :page-sizes="[12, 24, 48]"
                @current-change="handleTemplatePageChange" @size-change="handleTemplateSizeChange"
                layout="total, sizes, prev, pager, next" />
            </div>
          </div>
        </el-tab-pane>

        <!-- AI 配置 (v2.7 多 Provider 管理) — 现代 SaaS 风格 -->
        <el-tab-pane label="AI 配置" name="ai-config">
          <div class="ai-saas-wrap">
            <!-- 顶部统计 / 标题 -->
            <div class="ai-saas-header">
              <div class="ai-saas-header-info">
                <h2 class="ai-saas-title">{{ typeLabel(aiActiveType) }}</h2>
                <p class="ai-saas-subtitle">管理 {{ typeLabel(aiActiveType) }} Provider · 切换 / 增删改 / 设为默认</p>
              </div>
              <div class="ai-saas-header-actions">
                <el-button class="ai-saas-btn-secondary" @click="loadAiConfig" :loading="loadingAi">
                  <el-icon><Refresh /></el-icon>
                  <span>刷新</span>
                </el-button>
                <el-button class="ai-saas-btn-primary" @click="openProviderDialog()">
                  <el-icon><Plus /></el-icon>
                  <span>添加 Provider</span>
                </el-button>
              </div>
            </div>

            <div class="ai-saas-body">
              <!-- 左侧：type 切换 -->
              <aside class="ai-saas-sidebar">
                <div class="ai-saas-sidebar-section">
                  <div class="ai-saas-sidebar-title">类型</div>
                  <nav class="ai-saas-type-list">
                    <button
                      v-for="t in providerTypes"
                      :key="t.value"
                      :class="['ai-saas-type-item', { active: aiActiveType === t.value }]"
                      @click="onAiTypeSelect(t.value)"
                    >
                      <span class="ai-saas-type-icon" :style="{ background: t.color + '20', color: t.color }">
                        <el-icon><component :is="t.icon" /></el-icon>
                      </span>
                      <span class="ai-saas-type-name">{{ t.label }}</span>
                      <span v-if="providerCounts[t.value] > 0" class="ai-saas-type-badge" :style="{ background: t.color }">
                        {{ providerCounts[t.value] }}
                      </span>
                    </button>
                  </nav>
                </div>

                <div class="ai-saas-sidebar-section">
                  <div class="ai-saas-sidebar-title">说明</div>
                  <ul class="ai-saas-tip-list">
                    <li>每个 type 可添加多个 Provider</li>
                    <li>带<span class="ai-saas-dot success"></span>默认 标记的会被系统优先调用</li>
                    <li>禁用后系统不会调用（保留配置）</li>
                    <li>修改后<strong>立即生效</strong>，无需重启</li>
                  </ul>
                </div>
              </aside>

              <!-- 右侧：Provider 列表 -->
              <main class="ai-saas-main">
                <div v-if="loadingAi && currentProviders.length === 0" class="ai-saas-loading">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>加载中…</span>
                </div>

                <div v-else-if="currentProviders.length === 0" class="ai-saas-empty">
                  <div class="ai-saas-empty-icon">
                    <el-icon><ChatLineRound /></el-icon>
                  </div>
                  <h3 class="ai-saas-empty-title">还没有 {{ typeLabel(aiActiveType) }} Provider</h3>
                  <p class="ai-saas-empty-desc">添加第一个 Provider 来配置 {{ typeLabel(aiActiveType) }} 服务</p>
                  <el-button class="ai-saas-btn-primary" @click="openProviderDialog()">
                    <el-icon><Plus /></el-icon>
                    <span>添加 Provider</span>
                  </el-button>
                </div>

                <div v-else class="ai-saas-grid">
                  <article
                    v-for="p in currentProviders"
                    :key="p.id"
                    class="ai-saas-card"
                    :class="{ 'is-default': p.isDefault, 'is-disabled': !p.enabled }"
                  >
                    <header class="ai-saas-card-header">
                      <div class="ai-saas-card-title-row">
                        <div class="ai-saas-card-name">
                          <h3 class="ai-saas-card-name-text">{{ p.name }}</h3>
                          <span v-if="p.isDefault" class="ai-saas-badge default">
                            <el-icon><Check /></el-icon>
                            默认
                          </span>
                          <span v-else-if="!p.enabled" class="ai-saas-badge disabled">已禁用</span>
                        </div>
                        <el-switch
                          v-model="p.enabled"
                          @change="toggleProvider(p)"
                          inline-prompt
                        />
                      </div>
                      <p v-if="p.defaultModel" class="ai-saas-card-model">
                        <el-icon><Cpu /></el-icon>
                        {{ p.defaultModel }}
                      </p>
                    </header>

                    <div class="ai-saas-card-fields">
                      <div class="ai-saas-field">
                        <span class="ai-saas-field-label">Base URL</span>
                        <code class="ai-saas-field-value">{{ p.baseUrl || '—' }}</code>
                      </div>
                      <div class="ai-saas-field">
                        <span class="ai-saas-field-label">API Key</span>
                        <div class="ai-saas-key-row">
                          <code class="ai-saas-field-value">
                            {{ revealedKeys[p.id || 0] ?? p.apiKeyMask ?? '—' }}
                          </code>
                          <el-button
                            v-if="p.hasKey"
                            link
                            size="small"
                            :loading="revealingId === p.id"
                            @click="toggleKeyReveal(p.id || 0)"
                            :title="revealedKeys[p.id || 0] ? '隐藏 API Key' : '查看完整 API Key'"
                          >
                            <el-icon><component :is="revealedKeys[p.id || 0] ? 'View' : 'Hide'" /></el-icon>
                          </el-button>
                          <el-button
                            v-if="p.hasKey"
                            link
                            size="small"
                            @click="copyToClipboard(revealedKeys[p.id || 0] || '')"
                            :disabled="!revealedKeys[p.id || 0]"
                            title="先查看再复制"
                          >
                            <el-icon><DocumentCopy /></el-icon>
                          </el-button>
                        </div>
                      </div>
                      <div class="ai-saas-field-row">
                        <div class="ai-saas-field">
                          <span class="ai-saas-field-label">超时</span>
                          <span class="ai-saas-field-value">{{ p.timeout || 300 }}s</span>
                        </div>
                        <div class="ai-saas-field">
                          <span class="ai-saas-field-label">更新</span>
                          <span class="ai-saas-field-value">{{ formatTime(p.updatedAt) }}</span>
                        </div>
                      </div>
                      <p v-if="p.remark" class="ai-saas-card-remark">{{ p.remark }}</p>
                    </div>

                    <footer class="ai-saas-card-footer">
                      <el-button
                        v-if="!p.isDefault && p.enabled"
                        link
                        type="primary"
                        size="small"
                        @click="setAsDefault(p)"
                      >
                        设为默认
                      </el-button>
                      <el-button link size="small" @click="testProvider(p)" :loading="testingId === p.id">
                        测试
                      </el-button>
                      <el-button link size="small" @click="openProviderDialog(p)">
                        编辑
                      </el-button>
                      <el-popconfirm
                        :title="`确定删除 Provider '${p.name}'?`"
                        @confirm="deleteProvider(p)"
                      >
                        <template #reference>
                          <el-button link type="danger" size="small">删除</el-button>
                        </template>
                      </el-popconfirm>
                    </footer>
                  </article>
                </div>
              </main>
            </div>
          </div>

          <!-- Provider 编辑对话框 — 现代风格 -->
          <el-dialog
            v-model="providerDialogVisible"
            :title="editingProvider?.id ? '编辑 Provider' : '添加 Provider'"
            width="640px"
            :show-close="false"
            class="ai-saas-dialog"
            @closed="resetProviderForm"
          >
            <template #header>
              <div class="ai-saas-dialog-header">
                <div class="ai-saas-dialog-title">
                  <el-icon class="ai-saas-dialog-icon"><component :is="editingProvider?.id ? 'EditPen' : 'Plus'" /></el-icon>
                  {{ editingProvider?.id ? '编辑 Provider' : '添加 Provider' }}
                </div>
                <el-button link @click="providerDialogVisible = false">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </template>

            <el-form
              v-if="editingProvider"
              :model="editingProvider"
              label-position="top"
              class="ai-saas-form"
            >
              <div class="ai-saas-form-row">
                <el-form-item label="类型">
                  <el-select v-model="editingProvider.type" :disabled="!!editingProvider.id" style="width:100%">
                    <el-option
                      v-for="t in providerTypes"
                      :key="t.value"
                      :label="t.label"
                      :value="t.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="名称" required>
                  <el-input v-model="editingProvider.name" placeholder="如：default / openai-main" />
                </el-form-item>
              </div>

              <el-form-item label="服务地址 (Base URL)" required>
                <el-input
                  v-model="editingProvider.baseUrl"
                  placeholder="https://api.openai.com/v1"
                />
                <div class="ai-saas-form-hint">OpenAI 兼容地址，必须以 <code>/v1</code> 结尾</div>
              </el-form-item>

              <el-form-item label="API 密钥">
                <div class="ai-saas-key-row">
                  <el-input
                    v-model="editingProvider.apiKey"
                    :type="showDialogApiKey ? 'text' : 'password'"
                    :placeholder="dialogKeyPlaceholder"
                  />
                  <el-button
                    :icon="showDialogApiKey ? 'Hide' : 'View'"
                    :loading="dialogRevealing"
                    plain
                    @click="toggleDialogKeyReveal"
                  />
                </div>
                <div class="ai-saas-form-hint">{{ dialogKeyHint }}</div>
              </el-form-item>

              <el-form-item label="默认模型">
                <div class="ai-saas-model-row">
                  <el-select
                    v-model="editingProvider.defaultModel"
                    filterable
                    clearable
                    allow-create
                    placeholder="选择模型（也可手动输入）"
                    class="ai-saas-model-select"
                  >
                    <el-option
                      v-for="m in availableModels"
                      :key="m"
                      :label="m"
                      :value="m"
                    />
                  </el-select>
                  <el-button
                    :loading="fetchingModels"
                    :disabled="!editingProvider.baseUrl"
                    @click="fetchProviderModels"
                    plain
                  >
                    <el-icon><Refresh /></el-icon>
                    <span>获取模型</span>
                  </el-button>
                  <el-button
                    :loading="testingId === 'dialog'"
                    :disabled="!editingProvider.baseUrl"
                    @click="testDialogConnection"
                    plain
                    type="success"
                  >
                    <el-icon><Connection /></el-icon>
                    <span>测试连接</span>
                  </el-button>
                </div>
                <div
                  v-if="testResult"
                  :class="['ai-saas-test-result', testResult.status === 'ok' ? 'success' : 'error']"
                >
                  <el-icon><component :is="testResult.status === 'ok' ? 'Check' : 'CircleClose'" /></el-icon>
                  <span>{{ testResult.message }}</span>
                  <span v-if="testResult.latencyMs !== undefined" class="ai-saas-test-latency">
                    {{ testResult.latencyMs }}ms
                  </span>
                </div>
                <div v-else-if="availableModels.length > 0" class="ai-saas-form-hint success">
                  <el-icon><Check /></el-icon>
                  已获取 {{ availableModels.length }} 个模型
                </div>
                <div v-else class="ai-saas-form-hint">先填服务地址，点"获取模型"自动拉取</div>
              </el-form-item>

              <div class="ai-saas-form-row">
                <el-form-item label="超时(秒)">
                  <el-input-number v-model="editingProvider.timeout" :min="10" :max="3600" :step="30" style="width:100%" />
                </el-form-item>
                <el-form-item label="启用">
                  <el-switch v-model="editingProvider.enabled" />
                </el-form-item>
                <el-form-item label="设为默认">
                  <el-switch v-model="editingProvider.isDefault" />
                </el-form-item>
              </div>

              <el-form-item label="备注">
                <el-input v-model="editingProvider.remark" type="textarea" :rows="2" placeholder="可选" />
              </el-form-item>
            </el-form>

            <template #footer>
              <div class="ai-saas-dialog-footer">
                <el-button @click="providerDialogVisible = false">取消</el-button>
                <el-button class="ai-saas-btn-primary" @click="saveProvider" :loading="providerSaving">
                  <el-icon><Check /></el-icon>
                  <span>{{ editingProvider?.id ? '保存修改' : '创建 Provider' }}</span>
                </el-button>
              </div>
            </template>
          </el-dialog>
        </el-tab-pane>
        <!-- 文件夹模板 -->
        <el-tab-pane label="文件夹模板" name="folder-templates">
          <div class="tab-header">
            <div style="flex:1"></div>
            <el-button type="primary" @click="openFolderTemplateDialog()">
              <el-icon><Plus /></el-icon> 新建模板
            </el-button>
          </div>
          <el-table :data="folderTemplates" stripe row-key="id"
            @row-dragstart="onTplDragStart" @row-dragover="onTplDragOver" @row-drop="onTplDrop"
            :row-attributes="{ draggable: true }">
            <el-table-column width="40" align="center">
              <template #default>
                <el-icon class="drag-handle" style="cursor: grab; color: #c0c4cc"><Rank /></el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="模板名称" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="子文件夹结构" min-width="200">
              <template #default="{ row }">
                <span class="template-path">{{ formatFolderStructure(row.structure) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'success' : 'info'" size="small" effect="plain">{{ row.isActive ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button size="small" text @click="openFolderTemplateDialog(row)">编辑</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text @click="toggleFolderTemplateStatus(row)">{{ row.isActive ? '禁用' : '启用' }}</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text type="danger" @click="deleteFolderTemplate(row.id)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 用户弹窗 -->
    <el-dialog v-model="showUserDialog" :title="editingUser ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form :model="userForm" label-width="90px" class="dialog-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" required>
              <el-input v-model="userForm.employeeId" :disabled="!!editingUser" maxlength="8" placeholder="如 10001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户名" required>
              <el-input v-model="userForm.username" :disabled="!!editingUser" placeholder="登录账号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item v-if="!editingUser" label="初始密码" required>
              <el-input v-model="userForm.password" type="password" show-password placeholder="默认 123456" />
            </el-form-item>
            <el-form-item v-else label="姓名" required>
              <el-input v-model="userForm.realName" placeholder="真实姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="!editingUser" label="姓名" required>
              <el-input v-model="userForm.realName" placeholder="真实姓名" />
            </el-form-item>
            <el-form-item v-else label="邮箱">
              <el-input v-model="userForm.email" placeholder="name@company.com" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16" v-if="!editingUser">
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="userForm.email" placeholder="name@company.com" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="userForm.phone" placeholder="手机号码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16" v-else>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="userForm.phone" placeholder="手机号码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职位">
              <el-input v-model="userForm.position" placeholder="岗位/职位" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="部门">
          <el-select v-model="userForm.departmentId" placeholder="选择所属部门" clearable style="width:100%">
            <el-option v-for="d in allDepts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingUser" label="职位">
          <el-input v-model="userForm.position" placeholder="岗位/职位" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="角色">
          <el-select v-model="userForm.role" style="width:100%">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUser" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 部门弹窗 -->
    <el-dialog v-model="showDeptDialog" :title="editingDept ? '编辑部门' : '新增部门'" width="460px" destroy-on-close>
      <el-form :model="deptForm" label-width="90px" class="dialog-form">
        <el-form-item label="部门编码" required>
          <el-input v-model="deptForm.code" :disabled="!!editingDept" maxlength="20" placeholder="如 HR、FIN、IT" />
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="deptForm.name" placeholder="部门全称" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentId" placeholder="无（顶级部门）" clearable style="width:100%">
            <el-option v-for="d in allDepts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="deptForm.sortOrder" :min="0" :max="999" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeptDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDept" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文件夹模板弹窗 -->
    <el-dialog v-model="showFolderTemplateDialog" :title="editingFolderTemplate ? '编辑模板' : '新建模板'" width="560px" destroy-on-close>
      <el-form :model="folderTemplateForm" label-width="90px" class="dialog-form">
        <el-form-item label="模板名称" required>
          <el-input v-model="folderTemplateForm.name" placeholder="如：项目文档模板" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="folderTemplateForm.description" placeholder="模板用途说明" />
        </el-form-item>
        <el-form-item label="子文件夹">
          <div class="folder-structure-editor">
            <div v-for="(item, idx) in folderTemplateForm.structure" :key="idx" class="structure-row">
              <el-input v-model="item.name" placeholder="文件夹名称" style="width: 180px" />
              <el-input v-model="item.childrenStr" placeholder="子目录（逗号分隔）" style="flex:1; margin: 0 8px" />
              <el-button text type="danger" @click="folderTemplateForm.structure.splice(idx, 1)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="folderTemplateForm.structure.push({ name: '', childrenStr: '' })">
              <el-icon><Plus /></el-icon> 添加文件夹
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="folderTemplateForm.isActive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFolderTemplateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveFolderTemplate" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Close, Document, Grid, Picture, Rank, Cpu } from '@element-plus/icons-vue'
import api from '@/api/index'
import { userApi, type UserItem } from '@/api/user'
import { departmentApi, type Department } from '@/api/department'
import { auditApi, type AuditLogItem } from '@/api/audit'
import { watermarkApi, type WatermarkConfig } from '@/api/watermark'
import { templateApi, type DocumentTemplate } from '@/api/template'
import { folderTemplateApi, type FolderTemplate } from '@/api/folderTemplate'

const activeTab = ref('users')
const saving = ref(false)

// --- 用户管理 ---
const userSearch = ref('')
const users = ref<UserItem[]>([])
const userTotal = ref(0)
const userPage = ref(0)
const userPageSize = ref(20)
const showUserDialog = ref(false)
const editingUser = ref<UserItem | null>(null)
const userForm = ref<any>({})

// --- 部门管理 ---
const allDepts = ref<Department[]>([])
const showDeptDialog = ref(false)
const editingDept = ref<Department | null>(null)
const deptForm = ref<any>({})
const allDepartmentsFlat = ref<Department[]>([])

// --- 审计日志 ---
const auditLogs = ref<AuditLogItem[]>([])
const auditTotal = ref(0)
const auditPage = ref(0)
const auditDateRange = ref<string[] | null>(null)
const auditActionFilter = ref('')

const actionLabels: Record<string, string> = {
  CREATE: '创建文档', UPLOAD: '上传文档', RENAME: '重命名', DELETE: '删除',
  RESTORE: '恢复', RESTORE_VERSION: '恢复版本', SHARE: '共享',
  SIGN_INIT: '发起签署', SIGN_CANCEL: '取消签署', EDIT: '编辑文档',
  SAVE_VERSION: '保存版本', COMMENT: '评论', RESOLVE: '解决评论'
}

// 水印配置
const watermarkConfig = ref<WatermarkConfig>({
  id: 0, name: 'default', isEnabled: false,
  textTemplate: '{username} {datetime}', fontSize: 30,
  fontColor: '#CCCCCC', rotation: -45, opacity: 0.3, position: 'center'
})

// AI 配置
// ========== AI Provider 多 Provider 管理（v2.7） ==========
import { aiProvidersApi, PROVIDER_TYPE_LABELS } from '../api/aiProviders'
import type { ProviderType, AiProvider } from '../api/aiProviders'

const aiActiveType = ref<ProviderType>('LLM')
const aiProviders = ref<AiProvider[]>([])
const providerDialogVisible = ref(false)
const editingProvider = ref<AiProvider | null>(null)
const providerSaving = ref(false)
const testingId = ref<number | 'dialog' | null>(null)
const fetchingModels = ref(false)
const loadingAi = ref(false)
const togglingId = ref<number | null>(null)
const availableModels = ref<string[]>([])
const testResult = ref<{
  status: 'ok' | 'fail'
  url: string
  httpCode: number
  latencyMs: number
  model?: string
  modelCount?: number
  message: string
  error?: string
  detail?: string
} | null>(null)
const visibleKeyIds = ref<Set<number>>(new Set())
// v2.7.3：缓存已解密的明文 key（id -> 明文），仅 admin 内存常驻，关闭弹窗/刷新页面即丢
const revealedKeys = ref<Record<number, string>>({})
const revealingId = ref<number | null>(null)
const showDialogApiKey = ref(false)
const dialogRevealing = ref(false)

const providerTypes: Array<{ value: ProviderType; label: string; icon: string; color: string; tagType: 'primary'|'success'|'warning'|'info'|'danger' }> = [
  { value: 'LLM',           label: PROVIDER_TYPE_LABELS.LLM,           icon: 'MagicStick', color: '#409EFF', tagType: 'primary' },
  { value: 'VISION',        label: PROVIDER_TYPE_LABELS.VISION,        icon: 'View',       color: '#7C3AED', tagType: 'primary' },
  { value: 'OCR_PADDLE',    label: PROVIDER_TYPE_LABELS.OCR_PADDLE,    icon: 'Reading',    color: '#10B981', tagType: 'success' },
  { value: 'DOCLING',       label: PROVIDER_TYPE_LABELS.DOCLING,       icon: 'Files',      color: '#F59E0B', tagType: 'warning' },
  { value: 'OCR_TESSERACT', label: PROVIDER_TYPE_LABELS.OCR_TESSERACT, icon: 'Document',   color: '#909399', tagType: 'info' },
]

const currentProviders = computed(() => {
  return aiProviders.value
    .filter((p: any) => p.type === aiActiveType.value)
    .sort((a: any, b: any) => {
      if (a.isDefault !== b.isDefault) return a.isDefault ? -1 : 1
      return (a.name || '').localeCompare(b.name || '')
    })
})

const providerCounts = computed(() => {
  const counts: Record<string, number> = {}
  for (const t of providerTypes) counts[t.value] = 0
  for (const p of aiProviders.value) {
    if (counts[p.type] !== undefined) counts[p.type]++
  }
  return counts
})

function typeLabel(t: ProviderType): string {
  return PROVIDER_TYPE_LABELS[t] || t
}

async function loadAiConfig() {
  try {
    aiProviders.value = await aiProvidersApi.list()
  } catch (e: any) {
    ElMessage.error('加载 Provider 列表失败：' + (e?.message || '未知错误'))
  }
}

async function handleTabChange(tabName: string) {
  if (tabName === 'ai-config') {
    await loadAiConfig()
  }
}

function onAiTypeSelect(index: string) {
  aiActiveType.value = index as ProviderType
}

async function openProviderDialog(p?: AiProvider) {
  if (p) {
    // v2.7.3：编辑时不直接预填明文 key，只携带掩码显示；用户点眼睛再 reveal
    editingProvider.value = {
      ...p,
      apiKey: '',
      apiKeyChanged: false,
    } as any
  } else {
    editingProvider.value = {
      type: aiActiveType.value,
      name: '',
      baseUrl: '',
      apiKey: '',
      defaultModel: '',
      timeout: 300,
      enabled: true,
      isDefault: currentProviders.value.length === 0,
      remark: '',
      apiKeyChanged: true,
    } as any
  }
  showDialogApiKey.value = false
  providerDialogVisible.value = true
}

function resetProviderForm() {
  editingProvider.value = null
  availableModels.value = []
  testResult.value = null
  showDialogApiKey.value = false
  dialogRevealing.value = false
}

async function toggleKeyReveal(id: number) {
  if (revealedKeys.value[id]) {
    delete revealedKeys.value[id]
    revealedKeys.value = { ...revealedKeys.value }
    return
  }
  revealingId.value = id
  try {
    const res = await aiProvidersApi.revealKey(id)
    revealedKeys.value = { ...revealedKeys.value, [id]: res.apiKey || '' }
  } catch (e: any) {
    ElMessage.error(`查看失败：${e?.message || '未知错误'}`)
  } finally {
    revealingId.value = null
  }
}

// v2.7.3 编辑弹窗里的密钥占位提示（显示后端掩码，不直接预填明文）
const dialogKeyPlaceholder = computed(() => {
  const p: any = editingProvider.value
  if (!p) return ''
  if (p.id) {
    if (p.apiKey) return '已查看明文，可直接修改后保存'
    if (p.apiKeyMask) return `当前：${p.apiKeyMask}（点眼睛查看完整）`
    return '点眼睛查看完整密钥'
  }
  return 'sk-...'
})

const dialogKeyHint = computed(() => {
  const p: any = editingProvider.value
  if (!p) return ''
  return p.id ? '编辑后保存即覆盖；不修改请留空' : '不填则用环境变量兜底'
})

async function toggleDialogKeyReveal() {
  const p: any = editingProvider.value
  if (!p) return
  if (showDialogApiKey.value) {
    // 切回密码态：清空已 reveal 的明文，避免常驻内存
    showDialogApiKey.value = false
    if (p.apiKey) editingProvider.value = { ...p, apiKey: '' }
    return
  }
  if (!p.id) {
    ElMessage.warning('新增 Provider 直接在输入框填写即可')
    showDialogApiKey.value = true
    return
  }
  if (p.apiKey) {
    // 内存里已有明文，直接切到显示态
    showDialogApiKey.value = true
    return
  }
  dialogRevealing.value = true
  try {
    const res = await aiProvidersApi.revealKey(p.id)
    editingProvider.value = { ...p, apiKey: res.apiKey || '' }
    showDialogApiKey.value = true
  } catch (e: any) {
    ElMessage.error(`查看失败：${e?.message || '未知错误'}`)
  } finally {
    dialogRevealing.value = false
  }
}

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败')
    }
    document.body.removeChild(textarea)
  }
}

async function saveProvider() {
  if (!editingProvider.value) return
  const p: any = editingProvider.value
  if (!p.name || !p.baseUrl) {
    ElMessage.warning('名称和服务地址必填')
    return
  }
  providerSaving.value = true
  try {
    const payload: any = {
      type: p.type,
      name: p.name,
      baseUrl: p.baseUrl,
      defaultModel: p.defaultModel || null,
      timeout: p.timeout || 300,
      enabled: p.enabled,
      isDefault: p.isDefault,
      remark: p.remark || '',
    }
    if (p.id) {
      if (p.apiKey) payload.apiKey = p.apiKey
      await aiProvidersApi.update(p.id, payload)
      ElMessage.success('已保存')
    } else {
      if (!p.apiKey) {
        ElMessage.warning('新增 Provider 必须填 API Key')
        providerSaving.value = false
        return
      }
      payload.apiKey = p.apiKey
      await aiProvidersApi.create(payload)
      ElMessage.success('已创建')
    }
    providerDialogVisible.value = false
    await loadAiConfig()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.error || e?.message || '保存失败')
  } finally {
    providerSaving.value = false
  }
}

async function setAsDefault(p: AiProvider) {
  if (!p.id) return
  try {
    await aiProvidersApi.setDefault(p.id)
    ElMessage.success(`已设为默认：${p.name}`)
    await loadAiConfig()
  } catch (e: any) {
    ElMessage.error('设置失败：' + (e?.response?.data?.error || e?.message))
  }
}

async function toggleProvider(p: AiProvider) {
  if (!p.id) return
  if (!p.enabled && p.isDefault) {
    ElMessage.warning('不能禁用在用的默认 Provider，请先将其他 Provider 设为默认')
    ;(p as any).enabled = true
    return
  }
  try {
    await aiProvidersApi.setEnabled(p.id, p.enabled)
    ElMessage.success(p.enabled ? '已启用' : '已禁用')
  } catch {
    ;(p as any).enabled = !p.enabled
    ElMessage.error('操作失败')
  }
}

async function deleteProvider(p: AiProvider) {
  if (!p.id) return
  if (p.isDefault) {
    ElMessage.warning('默认 Provider 不能删除，请先将其他 Provider 设为默认')
    return
  }
  try {
    await aiProvidersApi.remove(p.id)
    ElMessage.success('已删除')
    await loadAiConfig()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.response?.data?.error || e?.message))
  }
}

async function testProvider(p: AiProvider) {
  if (!p.id) return
  testingId.value = p.id
  try {
    const result: any = await aiProvidersApi.testConnection({ id: p.id })
    if (result.status === 'ok') {
      ElMessage.success(`✓ ${p.name} 连接成功（${result.latencyMs}ms），发现 ${result.modelCount} 个模型`)
    } else {
      ElMessage.error(`✗ ${p.name} ${result.message || '连接失败'}`)
    }
  } catch (e: any) {
    ElMessage.error(`✗ ${p.name} ${e?.response?.data?.message || e?.message || '连接失败'}`)
  } finally {
    testingId.value = null
  }
}

async function fetchProviderModels() {
  if (!editingProvider.value?.baseUrl) {
    ElMessage.warning('请先填服务地址')
    return
  }
  fetchingModels.value = true
  availableModels.value = []
  try {
    const data: any = {
      baseUrl: editingProvider.value.baseUrl,
      apiKey: editingProvider.value.apiKey || '',
    }
    if ((editingProvider.value as any).id) data.id = (editingProvider.value as any).id
    const result: any = await aiProvidersApi.fetchModels(data)
    if (result.status === 'ok') {
      availableModels.value = result.models || []
      ElMessage.success(`✓ 获取 ${availableModels.value.length} 个模型`)
    } else {
      ElMessage.warning('获取模型失败：' + (result.error || '未知错误'))
    }
  } catch (e: any) {
    ElMessage.error('获取模型失败：' + (e?.response?.data?.error || e?.message))
  } finally {
    fetchingModels.value = false
  }
}

async function testDialogConnection() {
  if (!editingProvider.value?.baseUrl) {
    ElMessage.warning('请先填服务地址')
    return
  }
  testingId.value = 'dialog'
  testResult.value = null
  try {
    const data: any = {
      baseUrl: editingProvider.value.baseUrl,
      apiKey: editingProvider.value.apiKey || '',
      model: editingProvider.value.defaultModel || '',
    }
    if ((editingProvider.value as any).id) data.id = (editingProvider.value as any).id
    testResult.value = await aiProvidersApi.testConnection(data)
  } catch (e: any) {
    testResult.value = {
      status: 'fail',
      url: editingProvider.value.baseUrl,
      httpCode: 0,
      latencyMs: 0,
      message: '请求失败：' + (e?.response?.data?.message || e?.message || '网络错误'),
    }
  } finally {
    testingId.value = null
  }
}
const templates = ref<DocumentTemplate[]>([])
const templateCategories = ref<string[]>([])
const templateTypeFilter = ref('')
const templateSearch = ref('')
const templateCategoryFilter = ref('')
const selectedAdminCategory = ref('')
const templatePage = ref(0)
const templatePageSize = ref(12)

const filteredTemplates = computed(() => {
  let list = templates.value
  if (templateTypeFilter.value) {
    list = list.filter(t => t.docType === templateTypeFilter.value)
  }
  if (templateCategoryFilter.value) {
    list = list.filter(t => t.category === templateCategoryFilter.value)
  }
  if (selectedAdminCategory.value) {
    list = list.filter(t => t.category === selectedAdminCategory.value)
  }
  if (templateSearch.value) {
    const search = templateSearch.value.toLowerCase()
    list = list.filter(t => t.name.toLowerCase().includes(search) || t.category?.toLowerCase().includes(search))
  }
  return list
})

const pagedTemplates = computed(() => {
  const start = templatePage.value * templatePageSize.value
  return filteredTemplates.value.slice(start, start + templatePageSize.value)
})

async function loadTemplateCategories() {
  try {
    templateCategories.value = await templateApi.getCategories()
  } catch {}
}

async function addTemplateCategory() {
  try {
    const { value } = await ElMessageBox.prompt('请输入新分类名称', '添加分类', {
      confirmButtonText: '添加',
      cancelButtonText: '取消',
      inputPattern: /^.{1,50}$/,
      inputErrorMessage: '分类名称长度1-50个字符'
    })
    if (value && value.trim()) {
      await templateApi.addCategory(value.trim())
      ElMessage.success('分类已添加')
      loadTemplateCategories()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.error || '添加失败')
    }
  }
}
const templateForm = ref({ name: '', description: '', docType: 'word', category: '' })

// --- 文件夹模板 ---
const folderTemplates = ref<FolderTemplate[]>([])
const showFolderTemplateDialog = ref(false)
const editingFolderTemplate = ref<FolderTemplate | null>(null)
const folderTemplateForm = ref<{ name: string; description: string; structure: { name: string; childrenStr: string }[]; isActive: boolean }>({
  name: '', description: '', structure: [], isActive: true
})

async function loadFolderTemplates() {
  try {
    folderTemplates.value = await folderTemplateApi.getAll()
  } catch {}
}

// 文件夹模板拖拽排序
let tplDragRow: FolderTemplate | null = null

function onTplDragStart(row: FolderTemplate) {
  tplDragRow = row
}

function onTplDragOver(_row: FolderTemplate) {
  // 允许拖拽
}

async function onTplDrop(targetRow: FolderTemplate) {
  if (!tplDragRow || tplDragRow.id === targetRow.id) return
  const ids = folderTemplates.value.map(t => t.id)
  const fromIdx = ids.indexOf(tplDragRow.id)
  const toIdx = ids.indexOf(targetRow.id)
  ids.splice(fromIdx, 1)
  ids.splice(toIdx, 0, tplDragRow.id)
  try {
    await folderTemplateApi.reorder(ids)
    loadFolderTemplates()
  } catch {
    ElMessage.error('排序失败')
  }
  tplDragRow = null
}

function formatFolderStructure(structure: any): string {
  if (!structure) return '-'
  try {
    const arr = typeof structure === 'string' ? JSON.parse(structure) : structure
    if (!Array.isArray(arr)) return '-'
    return arr.map((item: any) => {
      const children = item.children ? (Array.isArray(item.children) ? item.children.join('/') : item.children) : ''
      return children ? `${item.name}(${children})` : item.name
    }).join('、')
  } catch { return '-' }
}

function openFolderTemplateDialog(tpl?: FolderTemplate) {
  if (tpl) {
    editingFolderTemplate.value = tpl
    let structure: any[] = []
    try {
      const parsed = typeof tpl.structure === 'string' ? JSON.parse(tpl.structure) : tpl.structure
      if (Array.isArray(parsed)) {
        structure = parsed.map((item: any) => ({
          name: item.name || '',
          childrenStr: Array.isArray(item.children) ? item.children.join(',') : (item.children || '')
        }))
      }
    } catch {}
    folderTemplateForm.value = { name: tpl.name, description: tpl.description || '', structure, isActive: tpl.isActive ?? true }
  } else {
    editingFolderTemplate.value = null
    folderTemplateForm.value = { name: '', description: '', structure: [], isActive: true }
  }
  showFolderTemplateDialog.value = true
}

async function saveFolderTemplate() {
  if (!folderTemplateForm.value.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  const structure = folderTemplateForm.value.structure
    .filter(item => item.name.trim())
    .map(item => ({
      name: item.name.trim(),
      children: item.childrenStr ? item.childrenStr.split(',').map(s => s.trim()).filter(Boolean) : []
    }))
  const data = {
    name: folderTemplateForm.value.name.trim(),
    description: folderTemplateForm.value.description.trim(),
    structure,
    isActive: folderTemplateForm.value.isActive
  }
  try {
    if (editingFolderTemplate.value) {
      await folderTemplateApi.update(editingFolderTemplate.value.id, data)
      ElMessage.success('模板已更新')
    } else {
      await folderTemplateApi.create(data)
      ElMessage.success('模板已创建')
    }
    showFolderTemplateDialog.value = false
    loadFolderTemplates()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function toggleFolderTemplateStatus(tpl: FolderTemplate) {
  try {
    await folderTemplateApi.update(tpl.id, { isActive: !tpl.isActive })
    ElMessage.success(tpl.isActive ? '已禁用' : '已启用')
    loadFolderTemplates()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function deleteFolderTemplate(id: number) {
  try {
    await ElMessageBox.confirm('确定删除此文件夹模板吗？', '删除模板', { type: 'warning' })
    await folderTemplateApi.delete(id)
    ElMessage.success('模板已删除')
    loadFolderTemplates()
  } catch {}
}

function handleTemplatePageChange(page: number) {
  templatePage.value = page - 1
}

function handleTemplateSizeChange(size: number) {
  templatePageSize.value = size
  templatePage.value = 0
}

const watermarkPreviewText = computed(() => {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const datetime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
  return watermarkConfig.value.textTemplate
    .replace('{username}', '张三')
    .replace('{datetime}', datetime)
    .replace('{date}', datetime.split(' ')[0])
})

const watermarkPreviewStyle = computed(() => ({
  fontSize: watermarkConfig.value.fontSize + 'px',
  color: watermarkConfig.value.fontColor,
  opacity: watermarkConfig.value.opacity,
  transform: `rotate(${watermarkConfig.value.rotation}deg)`,
  fontWeight: 'bold',
  whiteSpace: 'nowrap' as const
}))

async function loadWatermarkConfig() {
  try {
    const config = await watermarkApi.getConfig()
    watermarkConfig.value = config
  } catch {}
}

async function saveWatermarkConfig() {
  try {
    await watermarkApi.updateConfig(watermarkConfig.value)
    ElMessage.success('水印配置已保存')
  } catch {
    ElMessage.error('保存失败')
  }
}

// 模板管理
async function loadTemplates() {
  try {
    templates.value = await templateApi.getAll()
  } catch {
    ElMessage.error('加载模板列表失败')
  }
}

function openTemplateDialog() {
  templateForm.value = { name: '', description: '', docType: 'word', category: '' }
  // 创建文件选择器
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.docx,.xlsx,.pptx'
  input.onchange = async (e: any) => {
    const file = e.target.files[0]
    if (!file) return
    const name = file.name.replace(/\.(docx|xlsx|pptx)$/, '')
    const docType = file.name.endsWith('.xlsx') ? 'cell' : file.name.endsWith('.pptx') ? 'slide' : 'word'
    const formData = new FormData()
    formData.append('file', file)
    formData.append('name', name)
    formData.append('docType', docType)
    try {
      await templateApi.create(formData)
      ElMessage.success('模板添加成功')
      loadTemplates()
    } catch {
      ElMessage.error('添加失败')
    }
  }
  input.click()
}

async function toggleTemplateStatus(template: DocumentTemplate) {
  try {
    await templateApi.update(template.id, { isActive: !template.isActive })
    ElMessage.success(template.isActive ? '已禁用' : '已启用')
    loadTemplates()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function updateTemplateCategory(template: DocumentTemplate) {
  try {
    await templateApi.update(template.id, { category: template.category })
    ElMessage.success('分类已更新')
  } catch {
    ElMessage.error('更新失败')
  }
}

async function deleteTemplateCategory(name: string) {
  try {
    await ElMessageBox.confirm(`确定要删除分类"${name}"吗？`, '确认删除', { type: 'warning' })
    // Find category ID by name (we need to store category IDs)
    // For now, just remove from local list
    templateCategories.value = templateCategories.value.filter(c => c !== name)
    ElMessage.success('分类已删除')
  } catch {}
}

async function deleteTemplate(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除此模板吗？', '确认删除', { type: 'warning' })
    await templateApi.delete(id)
    ElMessage.success('模板已删除')
    loadTemplates()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function docTypeLabel(type: string) {
  const map: Record<string, string> = { word: 'Word', cell: 'Excel', slide: 'PPT' }
  return map[type] || type
}

function docTypeColor(type: string) {
  const map: Record<string, string> = { word: '#2b579a', cell: '#217346', slide: '#d24726' }
  return map[type] || '#909399'
}

// 部门树形结构
const deptTree = computed(() => {
  const map = new Map<number, any>()
  const roots: any[] = []
  for (const d of allDepartmentsFlat.value) {
    map.set(d.id, { ...d, children: [] })
  }
  for (const d of allDepartmentsFlat.value) {
    const node = map.get(d.id)!
    if (d.parentId && map.has(d.parentId)) {
      map.get(d.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
})

function getDeptName(id?: number) {
  if (!id) return '-'
  const d = allDepartmentsFlat.value.find(d => d.id === id)
  return d ? d.name : '-'
}

onMounted(() => {
  loadUsers()
  loadDepartments()
  loadAuditLogs()
  loadWatermarkConfig()
  loadTemplates()
  loadTemplateCategories()
  loadAiConfig()
  loadFolderTemplates()
})

async function loadUsers() {
  try {
    if (userSearch.value) {
      users.value = await api.get<any, any[]>('/admin/users/search', { params: { keyword: userSearch.value } })
      userTotal.value = users.value.length
    } else {
      const res = await api.get<any, any>('/admin/users', { params: { page: userPage.value, size: userPageSize.value } })
      users.value = res.content || res
      userTotal.value = res.totalElements ?? users.value.length
    }
  } catch {
    users.value = []
  }
}

function handleUserPageChange(page: number) {
  userPage.value = page - 1
  loadUsers()
}

function handleUserSizeChange(size: number) {
  userPageSize.value = size
  userPage.value = 0
  loadUsers()
}

async function loadDepartments() {
  try {
    allDepartmentsFlat.value = await departmentApi.getAll()
    allDepts.value = allDepartmentsFlat.value
  } catch {}
}

function openUserDialog(user?: UserItem) {
  if (user) {
    editingUser.value = user
    userForm.value = {
      employeeId: user.employeeId,
      username: user.username,
      realName: user.realName,
      email: user.email || '',
      phone: (user as any).phone || '',
      departmentId: user.departmentId || undefined,
      position: user.position || ''
    }
  } else {
    editingUser.value = null
    userForm.value = { employeeId: '', username: '', password: '123456', realName: '', email: '', phone: '', departmentId: undefined, position: '', role: 'user' }
  }
  showUserDialog.value = true
}

async function saveUser() {
  const f = userForm.value
  if (!f.employeeId || !f.username || !f.realName) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editingUser.value) {
      await userApi.update(editingUser.value.id, {
        realName: f.realName, email: f.email, phone: f.phone,
        departmentId: f.departmentId, position: f.position
      })
      ElMessage.success('用户更新成功')
    } else {
      if (!f.password) f.password = '123456'
      await userApi.create(f)
      ElMessage.success('用户创建成功')
    }
    showUserDialog.value = false
    loadUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleResetPwd(user: UserItem) {
  try {
    await ElMessageBox.confirm(`确定要重置 ${user.realName} 的密码为 123456 吗？`, '重置密码', { type: 'warning' })
    await userApi.resetPassword(user.id)
    ElMessage.success('密码已重置为 123456')
  } catch {}
}

async function toggleRole(user: UserItem) {
  const newRole = user.role === 'admin' ? 'user' : 'admin'
  try {
    await userApi.updateRole(user.id, newRole)
    user.role = newRole
    ElMessage.success('角色已更新')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function toggleStatus(user: UserItem) {
  try {
    await userApi.updateStatus(user.id)
    user.isActive = !user.isActive
    ElMessage.success(user.isActive ? '已启用' : '已禁用')
  } catch {
    ElMessage.error('操作失败')
  }
}

// --- 部门管理 ---
function openDeptDialog(dept?: Department | null, parentId?: number) {
  if (dept) {
    editingDept.value = dept
    deptForm.value = { code: dept.code, name: dept.name, parentId: dept.parentId || undefined, sortOrder: dept.sortOrder || 0 }
  } else {
    editingDept.value = null
    deptForm.value = { code: '', name: '', parentId: parentId || undefined, sortOrder: 0 }
  }
  showDeptDialog.value = true
}

async function saveDept() {
  const f = deptForm.value
  if (!f.code || !f.name) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editingDept.value) {
      await departmentApi.update(editingDept.value.id, { name: f.name, code: f.code, sortOrder: f.sortOrder })
      ElMessage.success('部门更新成功')
    } else {
      await departmentApi.create(f)
      ElMessage.success('部门创建成功')
    }
    showDeptDialog.value = false
    loadDepartments()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDeactivateDept(dept: Department) {
  try {
    await ElMessageBox.confirm(`确定要停用部门「${dept.name}」吗？`, '停用部门', { type: 'warning' })
    await departmentApi.deactivate(dept.id)
    ElMessage.success('部门已停用')
    loadDepartments()
  } catch {}
}

// --- 审计日志 ---
async function loadAuditLogs() {
  try {
    const params: any = { page: auditPage.value, size: 20 }
    if (auditDateRange.value && auditDateRange.value.length === 2) {
      const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      params.startDate = fmt(new Date(auditDateRange.value[0]))
      params.endDate = fmt(new Date(auditDateRange.value[1]))
    }
    if (auditActionFilter.value) {
      params.action = auditActionFilter.value
    }
    const res = await auditApi.getAllLogs(params)
    auditLogs.value = res.content || []
    auditTotal.value = res.totalElements || 0
  } catch {}
}

function handleAuditSearch() {
  auditPage.value = 0
  loadAuditLogs()
}

function handleAuditReset() {
  auditDateRange.value = null
  auditActionFilter.value = ''
  auditPage.value = 0
  loadAuditLogs()
}

function handleAuditPageChange(page: number) {
  auditPage.value = page - 1
  loadAuditLogs()
}

function formatTime(str?: string) {
  if (!str) return ''
  return new Date(str).toLocaleString('zh-CN')
}
</script>

<style scoped>
/* ============================================
   AI 配置 Tab — 现代 SaaS 风格
   设计 tokens: Plus Jakarta Sans + Book Brown 配色
   ============================================ */

.ai-saas-wrap {
  font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #0F172A;
  padding: 0;
}

/* 顶部 Header */
.ai-saas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  margin-bottom: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.ai-saas-header-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ai-saas-title {
  font-size: 22px;
  font-weight: 700;
  color: #0F172A;
  margin: 0;
  letter-spacing: -0.02em;
}
.ai-saas-subtitle {
  font-size: 13px;
  color: #78716C;
  margin: 0;
}
.ai-saas-header-actions {
  display: flex;
  gap: 8px;
}

/* 按钮 */
.ai-saas-btn-primary {
  background: #78716C !important;
  border-color: #78716C !important;
  color: #FFFFFF !important;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 150ms ease;
}
.ai-saas-btn-primary:hover {
  background: #57534E !important;
  border-color: #57534E !important;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(120, 113, 108, 0.25);
}
.ai-saas-btn-secondary {
  background: #FFFFFF !important;
  border: 1px solid #D4D4D4 !important;
  color: #0F172A !important;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 150ms ease;
}
.ai-saas-btn-secondary:hover {
  background: #FAFAFA !important;
  border-color: #A3A3A3 !important;
}

/* 主体两栏布局 */
.ai-saas-body {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 20px;
}

/* 左侧 Sidebar */
.ai-saas-sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.ai-saas-sidebar-section {
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.ai-saas-sidebar-title {
  font-size: 11px;
  font-weight: 600;
  color: #78716C;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 14px;
}

/* type 列表 */
.ai-saas-type-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ai-saas-type-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: all 150ms ease;
  text-align: left;
  font-family: inherit;
  font-size: 14px;
  color: #0F172A;
  width: 100%;
}
.ai-saas-type-item:hover {
  background: #FAFAFA;
}
.ai-saas-type-item.active {
  background: #FFFBEB;
  box-shadow: inset 0 0 0 1px #D97706;
}
.ai-saas-type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  font-size: 14px;
  flex-shrink: 0;
}
.ai-saas-type-name {
  flex: 1;
  font-weight: 500;
}
.ai-saas-type-badge {
  min-width: 22px;
  height: 20px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #FFFFFF;
  padding: 0 6px;
}

/* 说明列表 */
.ai-saas-tip-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-saas-tip-list li {
  font-size: 12px;
  color: #57534E;
  line-height: 1.6;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.ai-saas-tip-list li::before {
  content: '';
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #D97706;
  margin-top: 8px;
  flex-shrink: 0;
}
.ai-saas-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #A3A3A3;
  margin: 0 4px;
}
.ai-saas-dot.success {
  background: #16A34A;
}

/* 右侧 Main */
.ai-saas-main {
  min-height: 400px;
}
.ai-saas-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 80px 20px;
  color: #78716C;
  font-size: 14px;
}
.ai-saas-loading .is-loading {
  animation: ai-saas-spin 1s linear infinite;
}
@keyframes ai-saas-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 空状态 */
.ai-saas-empty {
  background: #FFFFFF;
  border: 2px dashed #E5E5E5;
  border-radius: 12px;
  padding: 64px 32px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.ai-saas-empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #FFFBEB;
  color: #D97706;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin-bottom: 8px;
}
.ai-saas-empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #0F172A;
  margin: 0;
}
.ai-saas-empty-desc {
  font-size: 14px;
  color: #78716C;
  margin: 0 0 16px 0;
}

/* Provider 卡片网格 */
.ai-saas-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
}

/* Provider 卡片 */
.ai-saas-card {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 200ms ease;
  position: relative;
  overflow: hidden;
}
.ai-saas-card:hover {
  border-color: #A8A29E;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}
.ai-saas-card.is-default {
  background: linear-gradient(135deg, #FFFBEB 0%, #FFFFFF 100%);
  border-color: #D97706;
}
.ai-saas-card.is-default::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: #D97706;
}
.ai-saas-card.is-disabled {
  opacity: 0.55;
}

.ai-saas-card-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ai-saas-card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ai-saas-card-name {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.ai-saas-card-name-text {
  font-size: 16px;
  font-weight: 700;
  color: #0F172A;
  margin: 0;
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-saas-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.ai-saas-badge.default {
  background: #16A34A;
  color: #FFFFFF;
}
.ai-saas-badge.disabled {
  background: #E5E5E5;
  color: #78716C;
}
.ai-saas-badge .el-icon {
  font-size: 10px;
}
.ai-saas-card-model {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #57534E;
  margin: 0;
  padding: 4px 0;
}
.ai-saas-card-model .el-icon {
  color: #78716C;
}

/* 字段 */
.ai-saas-card-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-saas-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.ai-saas-field-label {
  font-size: 11px;
  font-weight: 600;
  color: #78716C;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  min-width: 60px;
  flex-shrink: 0;
}
.ai-saas-field-value {
  font-size: 12px;
  color: #0F172A;
  font-family: 'SF Mono', 'Monaco', 'Cascadia Code', 'Consolas', monospace;
  background: #FAFAFA;
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid #F0F0F0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.ai-saas-field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.ai-saas-key-row {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.ai-saas-key-row .ai-saas-field-value {
  flex: 1;
}
.ai-saas-key-row .el-button {
  flex-shrink: 0;
}

.ai-saas-card-remark {
  font-size: 12px;
  color: #78716C;
  background: #FAFAFA;
  padding: 8px 12px;
  border-radius: 6px;
  margin: 0;
  border-left: 2px solid #D97706;
  line-height: 1.5;
}

/* 卡片 footer */
.ai-saas-card-footer {
  display: flex;
  gap: 4px;
  padding-top: 12px;
  border-top: 1px solid #F6F6F6;
  margin-top: auto;
}
.ai-saas-card-footer .el-button {
  font-size: 12px;
  padding: 4px 8px;
}

/* 编辑对话框 */
.ai-saas-dialog .el-dialog__header {
  padding: 0;
  margin-right: 0;
  border-bottom: 1px solid #F0F0F0;
}
.ai-saas-dialog .el-dialog__body {
  padding: 24px;
}
.ai-saas-dialog .el-dialog__footer {
  padding: 16px 24px;
  border-top: 1px solid #F0F0F0;
  background: #FAFAFA;
}
.ai-saas-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  width: 100%;
}
.ai-saas-dialog-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #0F172A;
}
.ai-saas-dialog-icon {
  color: #D97706;
  font-size: 18px;
}
.ai-saas-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 表单 */
.ai-saas-form {
  font-family: inherit;
}
.ai-saas-form .el-form-item__label {
  font-weight: 500;
  color: #0F172A;
  font-size: 13px;
  padding-bottom: 4px;
}
.ai-saas-form .el-input__wrapper {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #D4D4D4;
  padding: 4px 12px;
  transition: all 150ms ease;
}
.ai-saas-form .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #A3A3A3;
}
.ai-saas-form .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 2px #D97706;
}
.ai-saas-form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.ai-saas-form-row:has(.el-form-item:nth-child(3)) {
  grid-template-columns: 1fr 1fr 1fr;
}
.ai-saas-form-hint {
  font-size: 12px;
  color: #A8A29E;
  margin-top: 4px;
  line-height: 1.5;
  display: flex;
  align-items: center;
  gap: 4px;
  font-style: italic;
}
.ai-saas-form-hint code {
  background: #FAFAFA;
  padding: 1px 6px;
  border-radius: 3px;
  font-family: 'SF Mono', 'Monaco', 'Cascadia Code', monospace;
  font-size: 11px;
  color: #78716C;
  font-style: normal;
}
.ai-saas-form-hint.success {
  color: #16A34A;
  font-style: normal;
}
.ai-saas-model-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  width: 100%;
}
.ai-saas-model-row .ai-saas-model-select {
  flex: 1 1 280px;
  min-width: 280px;
}
.ai-saas-model-row .el-button {
  flex-shrink: 0;
}
.ai-saas-key-row {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}
.ai-saas-key-row .el-input {
  flex: 1;
}
.ai-saas-test-result {
  margin-top: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
.ai-saas-test-result.success {
  background: #DCFCE7;
  color: #166534;
}
.ai-saas-test-result.error {
  background: #FEE2E2;
  color: #991B1B;
}
.ai-saas-test-latency {
  margin-left: auto;
  font-family: 'SF Mono', monospace;
  font-size: 12px;
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 4px;
}

/* 暗色模式适配（element-plus 自带） */
@media (prefers-color-scheme: dark) {
  /* 暂不实现暗色，保持简单 */
}

/* 响应式 */
@media (max-width: 1024px) {
  .ai-saas-body {
    grid-template-columns: 200px 1fr;
  }
}
@media (max-width: 768px) {
  .ai-saas-body {
    grid-template-columns: 1fr;
  }
  .ai-saas-form-row {
    grid-template-columns: 1fr;
  }
  .ai-saas-field-row {
    grid-template-columns: 1fr;
  }
}
.admin-page {
  width: 100%;
}

.admin-body {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 24px 28px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.admin-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.admin-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  padding: 0 24px;
  height: 44px;
  line-height: 44px;
}

.tab-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  overflow: visible;
}

.tab-header :deep(.el-button--primary):hover {
  transform: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.admin-tabs :deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

.admin-tabs :deep(.el-table th.el-table__cell) {
  background: #f7f8fa;
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

.admin-tabs :deep(.el-table td.el-table__cell) {
  padding: 12px 0;
  font-size: 13px;
  color: #4a4a4a;
}

.admin-tabs :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: #fafbfc;
}

.admin-tabs :deep(.el-table .el-table__row:hover > td.el-table__cell) {
  background: #f0f5ff;
}

.action-btns {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.action-btns .el-button {
  padding: 4px 6px;
  font-size: 13px;
}

.action-btns .el-divider--vertical {
  margin: 0 4px;
  border-left-color: #dcdfe6;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dialog-form {
  padding: 0 8px;
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.dialog-form :deep(.el-input__wrapper),
.dialog-form :deep(.el-textarea__inner) {
  width: 100%;
}

/* 水印配置样式 */
.watermark-config {
  max-width: 600px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.watermark-form {
  margin-top: 16px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.watermark-preview {
  margin-top: 24px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.preview-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 16px;
}

.preview-box {
  background: white;
  padding: 40px;
  border-radius: 4px;
  text-align: center;
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e4e7ed;
  position: relative;
  overflow: hidden;
}

.watermark-center {
  position: relative;
  z-index: 1;
}

.preview-box-tiled {
  background: white;
  padding: 20px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  min-height: 160px;
}

.watermark-tile {
  text-align: center;
  padding: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 模板管理样式 */
.template-management {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.category-card {
  max-width: 500px;
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.category-name {
  font-size: 14px;
  color: #303133;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-config-layout {
  display: flex;
  gap: 20px;
}

.ai-config-left {
  flex: 1;
  min-width: 0;
}

.ai-config-right {
  width: 320px;
  flex-shrink: 0;
}

.ai-form {
  margin-top: 8px;
}

.ai-models-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s;
}

.ai-model-item:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.ai-model-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 500;
}

.ai-features-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  transition: border-color 0.2s;
}

.ai-feature-item:hover {
  border-color: var(--el-color-primary-light-5);
}

.ai-feature-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.ai-feature-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

/* 模板管理新样式 */
.template-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  gap: 8px;
  align-items: center;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.category-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.category-tab {
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  color: #606266;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  gap: 6px;
}

.category-tab:hover {
  background: #ecf5ff;
  color: #409eff;
}

.category-tab.active {
  background: #409eff;
  color: white;
}

.tab-delete {
  font-size: 12px;
  opacity: 0.6;
  cursor: pointer;
}

.tab-delete:hover {
  opacity: 1;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.template-card-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  transition: all 0.2s;
}

.template-card-item:hover {
  border-color: #c0c4cc;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.template-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.template-type-icon {
  font-size: 20px;
}

.template-card-body {
  margin-bottom: 10px;
}

.template-card-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-card-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.template-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #f0f0f0;
  padding-top: 10px;
}

.folder-structure-editor {
  width: 100%;
}

.structure-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
</style>
