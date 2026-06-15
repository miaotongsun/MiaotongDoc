<template>
  <div class="home-page">
    <aside class="sidebar">
      <div class="logo">
        <h2>MiaotongDoc</h2>
        <p class="slogan">妙思互通，同心同步</p>
      </div>
      <ul class="nav-list">
        <li :class="{ active: activeTab === 'all' }" @click="switchTab('all')">
          <el-icon><Files /></el-icon>
          <span>全部文档</span>
        </li>
        <li :class="{ active: activeTab === 'recent' }" @click="switchTab('recent')">
          <el-icon><Clock /></el-icon>
          <span>最近访问</span>
        </li>
        <li class="nav-divider"></li>
        <li :class="{ active: activeTab === 'word' }" @click="switchTab('word')">
          <el-icon><Document /></el-icon>
          <span>MiaotongWord</span>
        </li>
        <li :class="{ active: activeTab === 'cell' }" @click="switchTab('cell')">
          <el-icon><Grid /></el-icon>
          <span>MiaotongSheet</span>
        </li>
        <li :class="{ active: activeTab === 'slide' }" @click="switchTab('slide')">
          <el-icon><Picture /></el-icon>
          <span>MiaotongPPT</span>
        </li>
        <li class="nav-divider"></li>
        <li :class="{ active: activeTab === 'shared' }" @click="switchTab('shared')">
          <el-icon><Share /></el-icon>
          <span>与我共享</span>
        </li>
        <li :class="{ active: activeTab === 'starred' }" @click="switchTab('starred')">
          <el-icon><Star /></el-icon>
          <span>收藏文档</span>
        </li>
        <li :class="{ active: activeTab === 'trash' }" @click="switchTab('trash')">
          <el-icon><Delete /></el-icon>
          <span>回收站</span>
        </li>
        <li class="nav-divider"></li>
        <!-- 文件夹 -->
        <li class="nav-section-header" @click="switchTab('folders')">
          <span class="nav-section-title" :class="{ active: activeTab === 'folders' }">文件夹</span>
          <div class="nav-section-actions" @click.stop>
            <el-button text size="small" @click.stop="collapseAllFolders" title="全部折叠">
              <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: middle">
                <path fill="currentColor" d="M128 256h768a42.667 42.667 0 0 0 0-85.333H128a42.667 42.667 0 1 0 0 85.333zm768 426.667H128a42.667 42.667 0 0 0 0 85.333h768a42.667 42.667 0 0 0 0-85.333zm-256-213.334H128a42.667 42.667 0 0 0 0 85.334h512a42.667 42.667 0 0 0 0-85.334z"/>
              </svg>
            </el-button>
            <el-button text size="small" @click.stop="showCreateFolder" title="新建文件夹">
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
        </li>
        <div class="folder-tree" style="position:relative">
          <div v-for="(folder, idx) in flatFolders" :key="folder.id" class="folder-item"
            :class="{ active: activeFolderId === folder.id, 'folder-child': folder.depth > 0, 'dragging': sidebarDragIdx === idx, 'drag-over': dragOverFolderId === folder.id }"
            :style="{ paddingLeft: (12 + folder.depth * 16) + 'px', transform: getSidebarTransform(idx) }"
            @click="onSidebarFolderClick(folder.id)"
            @dblclick="enterFolder(folder.id)"
            @dragover.prevent="onFolderDragOver(folder.id)"
            @dragleave="onFolderDragLeave"
            @drop.prevent="onFolderDrop($event, folder.id)">
            <span class="folder-drag-handle" @mousedown.left.stop="onSidebarDragStart($event, folder, idx)" @click.stop>
              <svg viewBox="0 0 1024 1024" width="12" height="12"><path fill="currentColor" d="M320 256a64 64 0 1 0 0-128 64 64 0 0 0 0 128zm0 256a64 64 0 1 0 0-128 64 64 0 0 0 0 128zm0 256a64 64 0 1 0 0-128 64 64 0 0 0 0 128zm384-512a64 64 0 1 0 0-128 64 64 0 0 0 0 128zm0 256a64 64 0 1 0 0-128 64 64 0 0 0 0 128zm0 256a64 64 0 1 0 0-128 64 64 0 0 0 0 128z"/></svg>
            </span>
            <el-icon v-if="folder.hasChildren" class="folder-toggle" @mousedown.stop @click.stop="toggleFolder(folder.id)">
              <ArrowRight v-if="!expandedFolders.has(folder.id)" />
              <ArrowDown v-else />
            </el-icon>
            <el-icon v-else class="folder-toggle-placeholder"></el-icon>
            <span class="folder-icon-wrapper" :style="{ color: folder.color || '#909399' }">
              <svg class="folder-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                <path d="M880 298.4H521L403.7 186.2c-1.5-1.4-3.5-2.2-5.5-2.2H144c-17.7 0-32 14.3-32 32v592c0 17.7 14.3 32 32 32h736c17.7 0 32-14.3 32-32V330.4c0-17.7-14.3-32-32-32z" :fill="folder.color || '#909399'" />
              </svg>
            </span>
            <span class="folder-name">{{ folder.name }}</span>
            <div class="folder-actions" @mousedown.stop @click.stop>
              <el-dropdown trigger="click" @command="handleFolderCommand($event, folder)">
                <el-icon class="folder-more"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="addSub">新建子文件夹</el-dropdown-item>
                    <el-dropdown-item command="download">下载全部</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <div v-if="folders.length === 0" class="folder-empty">
            暂无文件夹
          </div>
        </div>
        <li class="nav-divider"></li>
        <li :class="{ active: activeTab === 'contract' }" @click="switchTab('contract')">
          <el-icon><Notebook /></el-icon>
          <span>合同管理</span>
        </li>
        <li :class="{ active: activeTab === 'activity' }" @click="switchTab('activity')">
          <el-icon><Bell /></el-icon>
          <span>个人动态</span>
        </li>
        <li v-if="isAdmin" :class="{ active: activeTab === 'admin' }" @click="switchTab('admin')">
          <el-icon><Setting /></el-icon>
          <span>管理后台</span>
        </li>
      </ul>
    </aside>

    <div class="main-content">
      <header class="top-bar">
        <div class="top-bar-left">
          <el-button type="primary" @click="showCreate = true" v-if="isDocView">
            <el-icon><Plus /></el-icon>
            新建文档
          </el-button>
          <el-upload v-if="isDocView" :show-file-list="false" :before-upload="handleUpload" accept=".docx,.xlsx,.pptx,.pdf">
            <el-button>
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
          </el-upload>
          <el-button v-if="isDocView && activeFolderId === null" @click="handleAiOrganize">
            <el-icon><MagicStick /></el-icon>
            AI 整理
          </el-button>
          <h3 v-if="!isDocView" class="page-title">{{ activeTabLabel }}</h3>
        </div>
        <div v-if="isDocView" class="search-wrapper">
          <el-input v-model="searchKeyword" placeholder="搜索文档..." clearable
            :prefix-icon="Search" @input="handleSearchInput" @clear="handleSearchClear"
            @keydown.enter="handleSearchEnter" class="search-input" />
          <div v-if="suggestions.length > 0" class="search-suggestions">
            <div v-for="item in suggestions" :key="item.id" class="suggestion-item" @click="goToDocument(item.id)">
              <el-icon class="suggestion-icon"><Document /></el-icon>
              <div class="suggestion-content">
                <div class="suggestion-title">{{ item.title }}</div>
                <div v-if="item.snippet" class="suggestion-snippet" v-html="item.snippet"></div>
              </div>
              <el-tag size="small" :type="item.matchType === 'title' ? 'primary' : 'info'">
                {{ item.matchType === 'title' ? '标题' : '内容' }}
              </el-tag>
            </div>
            <div class="suggestion-footer">
              按回车搜索全部结果
            </div>
          </div>
        </div>
        <div class="top-bar-right">
          <NotificationBell />
          <ThemeSwitch />
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">{{ userName.charAt(0) }}</el-avatar>
              <span class="user-name">{{ userName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ employeeId }}</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- Document list view -->
      <main class="content-area doc-list-wrapper" v-if="isDocView">
        <div class="content-scroll">
        <div class="content-header">
          <div class="header-left">
            <div v-if="activeFolderId" class="folder-path">
              <span class="path-item root" @click="selectFolder(null)">文件夹</span>
              <span v-for="(crumb, idx) in folderBreadcrumbs" :key="crumb.id" class="path-segment">
                <span class="path-sep">/</span>
                <span class="path-item"
                  :class="{ active: idx === folderBreadcrumbs.length - 1 }"
                  @click="idx < folderBreadcrumbs.length - 1 && selectFolder(crumb.id)">{{ crumb.name }}</span>
              </span>
            </div>
            <h3 v-else>{{ activeTabLabel }}</h3>
            <span class="doc-count">{{ documents.length }} 个文档</span>
          </div>
          <div class="header-right">
            <el-select v-model="sortBy" size="small" style="width: 140px" @change="handleSortChange">
              <el-option label="最近更新" value="updatedAt" />
              <el-option label="最近创建" value="createdAt" />
              <el-option label="名称" value="title" />
              <el-option label="大小" value="fileSize" />
            </el-select>
            <el-radio-group v-model="viewMode" size="small" class="view-toggle">
              <el-radio-button value="grid">
                <el-icon><Grid /></el-icon>
              </el-radio-button>
              <el-radio-button value="list">
                <el-icon><List /></el-icon>
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- Batch action bar -->
        <transition name="slide-down">
          <div v-if="selectedIds.size > 0" class="batch-bar">
            <div class="batch-left">
              <el-button text @click="clearSelection">
                <el-icon><Close /></el-icon>
              </el-button>
              <span class="batch-count">已选择 {{ selectedIds.size }} 个文档</span>
              <el-button text size="small" @click="selectAll">全选</el-button>
            </div>
            <div class="batch-actions">
              <el-button size="small" @click="batchShare">
                <el-icon><Share /></el-icon> 批量共享
              </el-button>
              <el-button size="small" @click="batchExport">
                <el-icon><Download /></el-icon> 批量导出
              </el-button>
              <el-button size="small" type="danger" plain @click="batchDelete">
                <el-icon><Delete /></el-icon> 批量删除
              </el-button>
            </div>
          </div>
        </transition>

        <div class="doc-grid" v-if="viewMode === 'grid' && documents.length > 0">
          <DocCard v-for="doc in documents" :key="doc.id" :doc="doc"
            :selected="selectedIds.has(doc.id)" @toggle-select="toggleDocSelection(doc.id)"
            @click="openDocument(doc.id)" @delete="handleDelete(doc.id)"
            @share="openShareDialog" />
        </div>
        <el-empty v-else-if="viewMode === 'grid'" :description="emptyText" />

        <el-table v-else :data="filteredDocuments"
          class="doc-table" @selection-change="handleTableSelectionChange" row-key="id"
          :row-class-name="tableRowClassName" @row-dblclick="handleRowDblClick"
          :row-attributes="{ draggable: true }">
          <el-table-column type="selection" width="40" />
          <el-table-column label="文档" min-width="240" sortable :sort-method="sortByTitle">
            <template #default="{ row }">
              <div class="doc-name-cell" draggable="true"
                @dragstart="onDocDragStart($event, row)"
                @dragend="onDocDragEnd">
                <el-icon class="doc-type-icon" :style="{ color: docTypeColor(row.docType) }">
                  <Document v-if="row.docType === 'word'" />
                  <Grid v-else-if="row.docType === 'cell'" />
                  <Picture v-else-if="row.docType === 'slide'" />
                  <Files v-else />
                </el-icon>
                <el-icon v-if="row.signingLocked" class="lock-icon" color="#f56c6c"><Lock /></el-icon>
                <span class="doc-title-text">{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建人" width="100" prop="ownerName" sortable />
          <el-table-column label="部门" width="140">
            <template #header>
              <el-popover trigger="click" :width="220" :show-arrow="false" placement="bottom-start"
                @show="onDeptFilterShow" @hide="onDeptFilterHide">
                <template #reference>
                  <span class="dept-filter-trigger">
                    部门
                    <span class="dept-caret" :class="{ 'is-active': selectedDeptIds.size > 0 }">
                      <i class="sort-caret descending"></i>
                    </span>
                  </span>
                </template>
                <el-tree ref="deptTreeRef" :data="deptTreeData" show-checkbox node-key="id"
                  default-expand-all check-strictly highlight-current
                  @check="onDeptTreeCheck" />
              </el-popover>
            </template>
            <template #default="{ row }">
              {{ row.departmentName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160" sortable :sort-method="sortByCreatedAt">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="160" sortable :sort-method="sortByUpdatedAt">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="最近更新人" width="110" prop="updatedByName" />
          <el-table-column label="版本号" width="80" align="center">
            <template #default="{ row }">
              v{{ row.currentVersion }}
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90" sortable :sort-method="sortBySize" align="right">
            <template #default="{ row }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column label="模板" width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.templateName" class="template-path">{{ row.templateName }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="更多操作" width="80" fixed="right" align="center">
            <template #default="{ row }">
              <el-dropdown trigger="click" @command="handleTableCommand($event, row)">
                <el-icon class="more-icon"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="star">
                      <el-icon><Star /></el-icon>
                      {{ row.isStarred ? '取消收藏' : '收藏' }}
                    </el-dropdown-item>
                    <el-dropdown-item command="rename">
                      <el-icon><Edit /></el-icon>
                      重命名
                    </el-dropdown-item>
                    <el-dropdown-item command="move">
                      <el-icon><Folder /></el-icon>
                      移动到文件夹
                    </el-dropdown-item>
                    <el-dropdown-item command="share">
                      <el-icon><Share /></el-icon>
                      分享
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="emptyText" />
          </template>
        </el-table>
        </div>
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="documentStore.total"
            :page-size="documentStore.pageSize"
            :current-page="documentStore.page + 1"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </main>

      <!-- Inline contract view -->
      <main class="content-area" v-else-if="activeTab === 'contract'">
        <ContractList />
      </main>

      <!-- Inline activity view -->
      <main class="content-area" v-else-if="activeTab === 'activity'">
        <ActivityFeed />
      </main>

      <!-- Inline admin view -->
      <main class="content-area" v-else-if="activeTab === 'admin'">
        <AdminPanel />
      </main>

      <!-- Folder management view -->
      <main class="content-area" v-else-if="activeTab === 'folders'">
        <div class="folder-mgmt-header">
          <h3>文件夹管理</h3>
          <el-button type="primary" size="small" @click="showCreateFolder">
            <el-icon><Plus /></el-icon> 新建文件夹
          </el-button>
        </div>
        <div v-if="folders.length === 0" class="empty-state">
          <el-empty description="暂无文件夹，点击上方按钮创建" />
        </div>
        <div v-else class="folder-mgmt-list">
          <div v-for="(folder, idx) in flatFolders" :key="folder.id" class="folder-mgmt-item"
            :style="{
              paddingLeft: (16 + folder.depth * 24) + 'px',
              opacity: mgmtDragIdx === idx ? 0.3 : 1,
              transform: getMgmtTransform(idx)
            }"
            @mousedown.left="onMgmtMouseDown($event, folder, idx)"
            :class="{ 'dragging': mgmtDragIdx === idx }">
            <div class="folder-mgmt-info">
              <el-icon v-if="folder.hasChildren" class="folder-toggle" @mousedown.stop @click="toggleFolder(folder.id)">
                <ArrowRight v-if="!expandedFolders.has(folder.id)" />
                <ArrowDown v-else />
              </el-icon>
              <el-icon v-else class="folder-toggle-placeholder"></el-icon>
              <span class="folder-icon-wrapper" :style="{ color: folder.color || '#909399' }">
                <svg class="folder-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="18" height="18">
                  <path d="M880 298.4H521L403.7 186.2c-1.5-1.4-3.5-2.2-5.5-2.2H144c-17.7 0-32 14.3-32 32v592c0 17.7 14.3 32 32 32h736c17.7 0 32-14.3 32-32V330.4c0-17.7-14.3-32-32-32z" :fill="folder.color || '#909399'" />
                </svg>
              </span>
              <span class="folder-mgmt-name">{{ folder.name }}</span>
              <span class="folder-mgmt-meta">{{ folder.hasChildren ? '含子文件夹' : '空文件夹' }}</span>
            </div>
            <div class="folder-mgmt-actions" @mousedown.stop>
              <el-button size="small" @click="showEditFolder(folder)">编辑</el-button>
              <el-button size="small" @click="showAddSubFolder(folder)">新建子文件夹</el-button>
              <el-button size="small" @click="enterFolder(folder.id)">查看文档</el-button>
              <el-button size="small" type="danger" plain @click="handleDeleteFolder(folder)">删除</el-button>
            </div>
          </div>
        </div>
      </main>

      <!-- Trash view -->
      <main class="content-area" v-else-if="activeTab === 'trash'">
        <div class="trash-header">
          <h3>回收站</h3>
          <p class="trash-tip">文档删除后保留 30 天，之后将永久删除</p>
          <el-input v-model="trashSearch" placeholder="搜索回收站..." clearable style="width: 240px" />
          <el-button v-if="trashDocuments.length > 0" type="danger" plain size="small" @click="handleEmptyTrash">
            清空回收站
          </el-button>
        </div>
        <div v-if="filteredTrashDocuments.length === 0" class="empty-state">
          <el-empty :description="trashSearch ? '未找到匹配文档' : '回收站为空'" />
        </div>
        <div v-else class="trash-list">
          <div v-for="doc in filteredTrashDocuments" :key="doc.id" class="trash-item">
            <div class="trash-item-info">
              <el-icon class="trash-icon"><Delete /></el-icon>
              <div class="trash-item-details">
                <span class="trash-title">{{ doc.title }}</span>
                <span class="trash-meta">
                  创建人：{{ doc.ownerName || '-' }} · 删除人：{{ doc.updatedByName || '-' }} · 删除于 {{ formatDate(doc.updatedAt) }}
                </span>
              </div>
            </div>
            <div class="trash-item-actions">
              <el-button size="small" @click="handleRestore(doc.id)">恢复</el-button>
              <el-button size="small" type="danger" plain @click="handlePermanentDelete(doc.id)">永久删除</el-button>
            </div>
          </div>
        </div>
      </main>
    </div>

    <CreateDocDialog v-model="showCreate" @created="handleCreated" />
    <ShareDialog v-model="showShareDialog" :doc-id="shareDocId" :doc-ids="shareDocIds" />

    <!-- 移动到文件夹弹窗 -->
    <el-dialog v-model="moveDialogVisible" title="移动到文件夹" width="400px">
      <el-select v-model="moveTargetFolder" placeholder="选择目标文件夹" style="width: 100%" clearable>
        <el-option label="根目录（无文件夹）" :value="null" />
        <el-option v-for="folder in folders" :key="folder.id" :label="folder.name" :value="folder.id" />
      </el-select>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleMoveToFolder">移动</el-button>
      </template>
    </el-dialog>

    <!-- 创建文件夹弹窗 -->
    <el-dialog v-model="createFolderVisible" title="新建文件夹" width="480px">
      <el-form :model="{ name: newFolderName, color: newFolderColor, templateId: newFolderTemplateId }" label-width="90px">
        <el-form-item label="文件夹名称">
          <el-input v-model="newFolderName" placeholder="请输入文件夹名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="颜色标记">
          <div class="color-picker">
            <div v-for="c in folderColors" :key="c.value" class="color-option"
              :class="{ selected: newFolderColor === c.value }"
              :style="{ background: c.value }"
              @click="newFolderColor = c.value">
              <el-icon v-if="newFolderColor === c.value" :size="12" color="white"><Check /></el-icon>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="上级文件夹">
          <el-select v-model="newFolderParentId" placeholder="根目录（无上级）" clearable style="width: 100%">
            <el-option label="根目录（无上级）" :value="null" />
            <el-option v-for="f in folders" :key="f.id" :label="f.name" :value="f.id" />
          </el-select>
          <div v-if="activeFolderId && !newFolderParentId" class="form-tip">
            当前位于「{{ folders.find(f => f.id === activeFolderId)?.name }}」内，新文件夹将创建在该目录下
          </div>
        </el-form-item>
        <el-form-item label="从模板创建">
          <el-select v-model="newFolderTemplateId" placeholder="不使用模板" clearable style="width: 100%">
            <el-option label="不使用模板" :value="0" />
            <el-option v-for="tpl in folderTemplates" :key="tpl.id" :label="tpl.name" :value="tpl.id" />
          </el-select>
          <div class="form-tip">选择模板将自动创建子文件夹结构</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createFolderVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateFolder">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑文件夹弹窗 -->
    <el-dialog v-model="editFolderVisible" title="编辑文件夹" width="480px">
      <el-form label-width="90px">
        <el-form-item label="文件夹名称">
          <el-input v-model="editFolderName" placeholder="请输入文件夹名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="颜色标记">
          <div class="color-picker">
            <div v-for="c in folderColors" :key="c.value" class="color-option"
              :class="{ selected: editFolderColor === c.value }"
              :style="{ background: c.value }"
              @click="editFolderColor = c.value">
              <el-icon v-if="editFolderColor === c.value" :size="12" color="white"><Check /></el-icon>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="上级文件夹">
          <el-select v-model="editFolderParentId" placeholder="根目录（无上级）" clearable style="width: 100%">
            <el-option label="根目录（无上级）" :value="null" />
            <el-option v-for="f in availableParentFolders" :key="f.id" :label="f.name" :value="f.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editFolderVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateFolder">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDocumentStore } from '@/stores/document'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import { Search, List, MoreFilled, Lock, Document, Delete, Download, Folder, Plus, MagicStick } from '@element-plus/icons-vue'
import { documentApi } from '@/api/document'
import { departmentApi, type Department } from '@/api/department'
import { folderApi, type Folder as FolderType } from '@/api/folder'
import { folderTemplateApi } from '@/api/folderTemplate'
import DocCard from '@/components/DocCard.vue'
import CreateDocDialog from '@/components/CreateDocDialog.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import ThemeSwitch from '@/components/ThemeSwitch.vue'
import ActivityFeed from '@/views/ActivityFeed.vue'
import AdminPanel from '@/views/Admin.vue'
import ContractList from '@/views/ContractList.vue'

const router = useRouter()
const documentStore = useDocumentStore()
const userStore = useUserStore()

const activeTab = ref('all')
const searchKeyword = ref('')
const showCreate = ref(false)
const departments = ref<Department[]>([])
const selectedDeptIds = ref<Set<number>>(new Set())
const showShareDialog = ref(false)
const shareDocId = ref(0)
const shareDocIds = ref<number[]>([])
const selectedIds = ref<Set<number>>(new Set())
const viewMode = ref<'grid' | 'list'>((localStorage.getItem('viewMode') as 'grid' | 'list') || 'grid')
const suggestions = ref<any[]>([])
const trashDocuments = ref<any[]>([])
const trashSearch = ref('')

const filteredTrashDocuments = computed(() => {
  if (!trashSearch.value.trim()) return trashDocuments.value
  const keyword = trashSearch.value.trim().toLowerCase()
  return trashDocuments.value.filter(doc =>
    doc.title?.toLowerCase().includes(keyword) ||
    doc.ownerName?.toLowerCase().includes(keyword) ||
    doc.updatedByName?.toLowerCase().includes(keyword)
  )
})
watch(viewMode, (val) => localStorage.setItem('viewMode', val))
const deptTreeRef = ref<InstanceType<typeof ElTree>>()
const sortBy = ref('updatedAt')

// 列表拖拽
function onDocDragStart(e: DragEvent, doc: any) {
  e.dataTransfer?.setData('text/plain', String(doc.id))
  e.dataTransfer!.effectAllowed = 'move'
  // 创建小预览
  const ghost = document.createElement('div')
  ghost.className = 'drag-ghost'
  ghost.textContent = doc.title
  ghost.style.cssText = 'position:absolute;top:-1000px;padding:6px 12px;background:white;border:1px solid #409eff;border-radius:4px;font-size:12px;color:#303133;box-shadow:0 2px 6px rgba(0,0,0,0.15);max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;'
  document.body.appendChild(ghost)
  e.dataTransfer!.setDragImage(ghost, 0, 0)
  setTimeout(() => document.body.removeChild(ghost), 0)
}

function onDocDragEnd() {
  // no-op
}

// 文件夹
const folders = ref<FolderType[]>([])
const activeFolderId = ref<number | null>(null)
const dragOverFolderId = ref<number | null>(null)
const expandedFolders = ref<Set<number>>(new Set())

function toggleFolder(id: number) {
  const newSet = new Set(expandedFolders.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  expandedFolders.value = newSet
}

function collapseAllFolders() {
  expandedFolders.value = new Set()
}

// 侧边栏文件夹拖拽排序
const sidebarDragIdx = ref(-1)
const sidebarInsertIdx = ref(-1)
const SHIFT_PX = 36
let sidebarOrigRects: DOMRect[] = []

function getSidebarTransform(idx: number): string {
  if (sidebarInsertIdx.value < 0) return ''
  if (idx === sidebarDragIdx.value) return ''
  const ins = sidebarInsertIdx.value
  const srcIdx = sidebarDragIdx.value
  // 只对同级的文件夹应用位移
  const src = sidebarDragFolder
  if (!src) return ''
  const folder = flatFolders.value[idx]
  if (!folder || (folder.parentId ?? null) !== (src.parentId ?? null)) return ''
  if (srcIdx < ins && idx >= ins) return `translateY(${SHIFT_PX}px)`
  if (srcIdx > ins && idx >= ins && idx < srcIdx) return `translateY(${SHIFT_PX}px)`
  return ''
}
let sidebarDragFolder: FolderType | null = null
let sidebarStartY = 0
let sidebarMoved = false
let sidebarGhost: HTMLElement | null = null

function onSidebarFolderClick(id: number) {
  // 拖拽结束后不触发 click
  if (sidebarMoved) return
  selectFolder(id)
}

function onSidebarDragStart(e: MouseEvent, folder: FolderType, idx: number) {
  sidebarDragFolder = folder
  sidebarDragIdx.value = idx
  sidebarInsertIdx.value = -1
  sidebarStartY = e.clientY
  sidebarMoved = false
  // 缓存原始位置
  const items = document.querySelectorAll('.folder-tree .folder-item')
  sidebarOrigRects = Array.from(items).map(el => el.getBoundingClientRect())
  // 创建简化的幽灵元素
  const el = (e.target as HTMLElement).closest('.folder-item') as HTMLElement
  const rect = el.getBoundingClientRect()
  sidebarGhost = document.createElement('div')
  sidebarGhost.textContent = folder.name
  sidebarGhost.style.cssText = [
    'position:fixed',
    `left:${rect.left}px`,
    `top:${rect.top}px`,
    `width:${rect.width}px`,
    `height:${rect.height}px`,
    'display:flex',
    'align-items:center',
    'padding:6px 12px',
    'opacity:0.9',
    'pointer-events:none',
    'z-index:9999',
    'box-shadow:0 4px 16px rgba(0,0,0,0.2)',
    'border-radius:6px',
    'background:#fff',
    'border:1px solid var(--el-color-primary)',
    'font-size:13px',
    'color:#303133',
    'cursor:grabbing'
  ].join(';')
  document.body.appendChild(sidebarGhost)
  document.addEventListener('mousemove', onSidebarMouseMove)
  document.addEventListener('mouseup', onSidebarMouseUp)
}

function onSidebarMouseMove(e: MouseEvent) {
  if (!sidebarDragFolder) return
  if (Math.abs(e.clientY - sidebarStartY) < 5 && !sidebarMoved) return
  sidebarMoved = true
  // 幽灵垂直居中于鼠标
  if (sidebarGhost) {
    const h = sidebarGhost.offsetHeight || 32
    sidebarGhost.style.top = (e.clientY - h / 2) + 'px'
  }
  // 用缓存的原始位置做检测（transform不影响缓存）
  const items = document.querySelectorAll('.folder-tree .folder-item')
  if (sidebarOrigRects.length === 0) {
    sidebarOrigRects = Array.from(items).map(el => el.getBoundingClientRect())
  }
  let newInsert = -1
  for (let i = 0; i < sidebarOrigRects.length; i++) {
    if (i === sidebarDragIdx.value) continue
    const rect = sidebarOrigRects[i]
    if (e.clientY >= rect.top && e.clientY <= rect.bottom) {
      const relY = (e.clientY - rect.top) / rect.height
      if (relY < 0.4) {
        newInsert = i
      } else if (relY > 0.6) {
        newInsert = i + 1
      } else {
        newInsert = sidebarInsertIdx.value
      }
      break
    }
  }
  if (newInsert < 0 && sidebarOrigRects.length > 0) {
    const lastRect = sidebarOrigRects[sidebarOrigRects.length - 1]
    if (e.clientY > lastRect.bottom) newInsert = sidebarOrigRects.length
  }
  if (newInsert >= 0) {
    const src = sidebarDragFolder
    // 找到同级文件夹在 flatFolders 中的索引
    const siblingIndices: number[] = []
    flatFolders.value.forEach((f, i) => {
      if ((f.parentId ?? null) === (src.parentId ?? null) && i !== sidebarDragIdx.value) {
        siblingIndices.push(i)
      }
    })
    if (siblingIndices.length > 0) {
      const minIdx = siblingIndices[0]
      const maxIdx = siblingIndices[siblingIndices.length - 1] + 1
      if (newInsert >= minIdx && newInsert <= maxIdx) {
        sidebarInsertIdx.value = newInsert
      } else {
        sidebarInsertIdx.value = -1
      }
    } else {
      sidebarInsertIdx.value = -1
    }
  } else {
    sidebarInsertIdx.value = -1
  }
}

async function onSidebarMouseUp() {
  document.removeEventListener('mousemove', onSidebarMouseMove)
  document.removeEventListener('mouseup', onSidebarMouseUp)
  if (sidebarGhost) { sidebarGhost.remove(); sidebarGhost = null }
  const src = sidebarDragFolder
  const insertIdx = sidebarInsertIdx.value
  sidebarDragFolder = null
  sidebarDragIdx.value = -1
  sidebarInsertIdx.value = -1
  sidebarOrigRects = []
  if (!src || !sidebarMoved || insertIdx < 0) return
  sidebarMoved = false
  try {
    const siblings = flatFolders.value.filter(f => f.parentId === src.parentId).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    const ids = siblings.map(f => f.id)
    const fromIdx = ids.indexOf(src.id)
    ids.splice(fromIdx, 1)
    const adj = insertIdx > fromIdx ? insertIdx - 1 : insertIdx
    ids.splice(adj, 0, src.id)
    await folderApi.reorder(ids)
    loadFolders()
  } catch {}
}

// 计算扁平化的文件夹列表（支持任意层级嵌套）
const flatFolders = computed(() => {
  const result: (FolderType & { depth: number; hasChildren: boolean })[] = []
  function addChildren(parentId: number | null | undefined, depth: number) {
    const children = folders.value.filter(f => {
      if (parentId === null || parentId === undefined) {
        return !f.parentId
      }
      return f.parentId === parentId
    })
    for (const folder of children) {
      const childCount = folders.value.filter(f => f.parentId === folder.id).length
      const hasChildren = childCount > 0
      result.push({ ...folder, depth, hasChildren })
      if (hasChildren && expandedFolders.value.has(folder.id)) {
        addChildren(folder.id, depth + 1)
      }
    }
  }
  addChildren(null, 0)
  return result
})

// 面包屑导航
const folderBreadcrumbs = computed(() => {
  if (!activeFolderId.value) return []
  const crumbs: { id: number; name: string }[] = []
  let currentId: number | null = activeFolderId.value
  while (currentId) {
    const folder = folders.value.find(f => f.id === currentId)
    if (folder) {
      crumbs.unshift({ id: folder.id, name: folder.name })
      currentId = folder.parentId || null
    } else {
      break
    }
  }
  return crumbs
})

function onFolderDragOver(folderId: number) {
  dragOverFolderId.value = folderId
}

function onFolderDragLeave() {
  dragOverFolderId.value = null
}

async function onFolderDrop(event: DragEvent, folderId: number) {
  dragOverFolderId.value = null
  const docId = event.dataTransfer?.getData('text/plain')
  if (docId) {
    try {
      await documentApi.moveToFolder(parseInt(docId), folderId)
      ElMessage.success('已移动到文件夹')
      documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
      loadFolders()
    } catch {
      ElMessage.error('移动失败')
    }
  }
}

async function loadFolders() {
  try {
    folders.value = await folderApi.getAll()
  } catch {}
}

function selectFolder(id: number | null) {
  activeFolderId.value = activeFolderId.value === id ? null : id
  // 自动展开选中的文件夹及其父级
  if (activeFolderId.value) {
    const newExpanded = new Set(expandedFolders.value)
    let currentId: number | null = activeFolderId.value
    while (currentId) {
      newExpanded.add(currentId)
      const folder = folders.value.find(f => f.id === currentId)
      currentId = folder?.parentId || null
    }
    expandedFolders.value = newExpanded
  }
  documentStore.fetchDocuments({ sort: sortBy.value, size: 10, ...(activeFolderId.value ? { folderId: activeFolderId.value } : {}) })
}

function enterFolder(id: number) {
  activeFolderId.value = id
  activeTab.value = 'all'
  // 自动展开
  const newExpanded = new Set(expandedFolders.value)
  let currentId: number | null = id
  while (currentId) {
    newExpanded.add(currentId)
    const folder = folders.value.find(f => f.id === currentId)
    currentId = folder?.parentId || null
  }
  expandedFolders.value = newExpanded
  documentStore.fetchDocuments({ sort: sortBy.value, size: 10, ...(id ? { folderId: id } : {}) } as any)
}

async function handleFolderCommand(cmd: string, folder: any) {
  if (cmd === 'edit') {
    showEditFolder(folder)
  } else if (cmd === 'addSub') {
    showAddSubFolder(folder)
  } else if (cmd === 'download') {
    try {
      ElMessage.info('正在打包下载...')
      const blob = await folderApi.download(folder.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${folder.name}.zip`
      a.click()
      URL.revokeObjectURL(url)
      ElMessage.success('下载完成')
    } catch {
      ElMessage.error('下载失败')
    }
  } else if (cmd === 'delete') {
    handleDeleteFolder(folder)
  }
}

async function handleDeleteFolder(folder: any) {
  const parentName = folder.parentId
    ? folders.value.find(f => f.id === folder.parentId)?.name || '上级文件夹'
    : '根目录'
  try {
    await ElMessageBox.confirm(
      `删除文件夹后，其中的文档将移至「${parentName}」。确定删除吗？`,
      '删除文件夹',
      { type: 'warning' }
    )
    await folderApi.delete(folder.id, folder.parentId)
    ElMessage.success('文件夹已删除')
    if (activeFolderId.value === folder.id) {
      activeFolderId.value = null
      documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
    }
    loadFolders()
  } catch {}
}

// AI 智能整理
async function handleAiOrganize() {
  try {
    await ElMessageBox.confirm(
      'AI 将根据文档标题自动分类到对应文件夹。确定继续吗？',
      'AI 智能整理',
      { confirmButtonText: '开始整理', cancelButtonText: '取消' }
    )
    ElMessage.info('AI 正在整理文档...')
    const allDocs = documentStore.documents
    const allFolders = folders.value
    if (allFolders.length === 0) {
      ElMessage.warning('请先创建文件夹')
      return
    }
    const folderNames = allFolders.map(f => f.name)

    // 从后端获取 AI 配置
    let llmUrl = 'http://192.24.129.1:31000'
    let llmKey = ''
    let model = 'qwen3-coder'
    try {
      const configRes = await fetch('/api/ai/config')
      const config = await configRes.json()
      const provider = config.providers?.OpenAI || {}
      llmUrl = provider.url?.replace('/v1', '') || llmUrl
      llmKey = provider.key || llmKey
      model = config.actions?.Chat?.model || model
    } catch {}

    let moved = 0
    for (const doc of allDocs) {
      if (doc.folderId) continue
      try {
        const result = await fetch('/api/ai/proxy', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            target: llmUrl + '/v1/chat/completions',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + llmKey },
            data: JSON.stringify({
              model: model,
              messages: [{ role: 'user', content: `根据文档标题"${doc.title}"，从以下文件夹中选择最匹配的一个，只回答文件夹名称，不要其他文字：${folderNames.join('、')}` }],
              max_tokens: 50,
              stream: false
            })
          })
        })
        const data = await result.json()
        const content = data.choices?.[0]?.message?.content?.trim() || ''
        const matchedFolder = allFolders.find(f => content.includes(f.name))
        if (matchedFolder) {
          await documentApi.moveToFolder(doc.id, matchedFolder.id)
          moved++
        }
      } catch {}
    }
    if (moved > 0) {
      ElMessage.success(`AI 已整理 ${moved} 个文档到对应文件夹`)
      documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
      loadFolders()
    } else {
      ElMessage.info('没有需要整理的文档')
    }
  } catch {}
}
const documents = computed(() => documentStore.documents)
const userName = computed(() => sessionStorage.getItem('name') || '用户')
const employeeId = computed(() => sessionStorage.getItem('employeeId') || '')
const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin')

const isDocView = computed(() => {
  if (activeFolderId.value) return true
  return !['activity', 'admin', 'contract', 'trash', 'folders'].includes(activeTab.value)
})

const tabLabels: Record<string, string> = {
  all: '全部文档',
  recent: '最近访问',
  word: 'Word',
  cell: 'Sheet',
  slide: 'PPT',
  shared: '与我共享',
  starred: '收藏文档',
  trash: '回收站',
  activity: '个人动态',
  admin: '管理后台',
  contract: '合同管理',
  folders: '文件夹管理'
}

const activeTabLabel = computed(() => {
  if (activeFolderId.value) {
    const folder = folders.value.find((f: any) => f.id === activeFolderId.value)
    return folder ? folder.name : '文件夹'
  }
  return tabLabels[activeTab.value] || '全部文档'
})

const emptyText = computed(() => {
  if (searchKeyword.value) return '未找到匹配的文档'
  if (activeTab.value === 'shared') return '暂无他人共享的文档'
  if (activeTab.value === 'starred') return '暂无收藏文档'
  if (selectedDeptIds.value.size > 0) return '该部门暂无文档'
  return '暂无文档，点击"新建文档"开始创建'
})

onMounted(async () => {
  documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
  loadFolders()
  try {
    departments.value = await departmentApi.getAll()
  } catch {
    ElMessage.warning('部门列表加载失败')
  }
})

function switchTab(tab: string) {
  activeTab.value = tab
  activeFolderId.value = null  // 切换标签时清除文件夹选择
  selectedIds.value = new Set()
  if (['activity', 'admin', 'contract', 'folders'].includes(tab)) return
  if (tab === 'trash') {
    loadTrash()
    return
  }
  if (tab === 'recent') {
    documentStore.fetchDocuments({ sort: 'updatedAt', size: 50 })
    return
  }

  selectedDeptIds.value = new Set()
  searchKeyword.value = ''

  const params: any = { page: 0, size: 10, sort: sortBy.value }
  if (tab !== 'all') {
    params.type = tab
  }
  documentStore.fetchDocuments(params)
}

let searchTimer: ReturnType<typeof setTimeout> | null = null

function handleSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  const keyword = searchKeyword.value.trim()

  if (!keyword) {
    suggestions.value = []
    documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
    return
  }

  // 防抖获取搜索建议
  searchTimer = setTimeout(async () => {
    try {
      const result = await documentApi.suggest(keyword)
      suggestions.value = result.suggestions || []
    } catch {
      suggestions.value = []
    }
  }, 300)
}

function handleSearchEnter() {
  suggestions.value = []
  if (!searchKeyword.value.trim()) return
  activeTab.value = 'all'
  selectedDeptIds.value = new Set()
  documentStore.fetchDocuments({ keyword: searchKeyword.value.trim(), sort: sortBy.value, size: 10 })
}

function handleSearchClear() {
  suggestions.value = []
  documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
}

function goToDocument(docId: number) {
  suggestions.value = []
  router.push(`/editor/${docId}`)
}

function handlePageChange(newPage: number) {
  const params: any = { page: newPage - 1, size: documentStore.pageSize, sort: sortBy.value }
  if (activeTab.value !== 'all') params.type = activeTab.value
  if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
  documentStore.fetchDocuments(params)
}

function handleSizeChange(newSize: number) {
  const params: any = { page: 0, size: newSize, sort: sortBy.value }
  if (activeTab.value !== 'all') params.type = activeTab.value
  if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
  documentStore.fetchDocuments(params)
}

function handleSortChange() {
  documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
}

async function handleUpload(file: File) {
  try {
    await documentApi.upload(file)
    ElMessage.success('上传成功')
    documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
  } catch {
    ElMessage.error('上传失败')
  }
  return false
}

function openDocument(id: number) {
  router.push(`/editor/${id}`)
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除这个文档吗？', '确认删除', { type: 'warning' })
    await documentStore.deleteDocument(id)
    ElMessage.success('删除成功')
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function handleCreated() {
  documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
}

function openShareDialog(docId: number) {
  shareDocId.value = docId
  shareDocIds.value = []
  showShareDialog.value = true
}

function toggleDocSelection(id: number) {
  const newSet = new Set(selectedIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedIds.value = newSet
}

function selectAll() {
  selectedIds.value = new Set(documents.value.map((d: any) => d.id))
}

function clearSelection() {
  selectedIds.value = new Set()
}

async function batchDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.size} 个文档吗？`, '批量删除', { type: 'warning' })
    const ids = Array.from(selectedIds.value)
    await documentApi.batchDelete(ids)
    ElMessage.success(`成功删除 ${ids.length} 个文档`)
    selectedIds.value = new Set()
    documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

function batchShare() {
  shareDocIds.value = Array.from(selectedIds.value)
  shareDocId.value = 0
  showShareDialog.value = true
}

async function batchExport() {
  try {
    const ids = Array.from(selectedIds.value)
    ElMessage.info(`正在导出 ${ids.length} 个文档...`)
    const blob = await documentApi.exportZip(ids)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `documents_${new Date().toISOString().slice(0, 10)}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

function docTypeColor(type: string) {
  const map: Record<string, string> = { word: '#2b579a', cell: '#217346', slide: '#d24726' }
  return map[type] || '#909399'
}

function formatFileSize(bytes?: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(time?: string) {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDate(time?: string) {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// 回收站功能
async function loadTrash() {
  try {
    trashDocuments.value = await documentApi.getTrash()
  } catch {
    ElMessage.error('加载回收站失败')
  }
}

async function handleRestore(id: number) {
  try {
    await documentApi.restoreFromTrash(id)
    ElMessage.success('文档已恢复')
    loadTrash()
  } catch {
    ElMessage.error('恢复失败')
  }
}

async function handlePermanentDelete(id: number) {
  try {
    await ElMessageBox.confirm('永久删除后无法恢复，确定要删除吗？', '确认永久删除', { type: 'warning' })
    await documentApi.permanentDelete(id)
    ElMessage.success('文档已永久删除')
    loadTrash()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleEmptyTrash() {
  try {
    await ElMessageBox.confirm('确定要清空回收站吗？所有文档将被永久删除。', '确认清空', { type: 'warning' })
    const result = await documentApi.emptyTrash()
    ElMessage.success(`已清空 ${result.deleted} 个文档`)
    loadTrash()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('清空失败')
  }
}

function handleTableSelectionChange(rows: any[]) {
  selectedIds.value = new Set(rows.map(r => r.id))
}

function tableRowClassName({ row }: { row: any }) {
  return selectedIds.value.has(row.id) ? 'selected-row' : ''
}

function handleRowDblClick(row: any) {
  openDocument(row.id)
}

// Sort methods for el-table
function sortByTitle(a: any, b: any) {
  return (a.title || '').localeCompare(b.title || '')
}

function sortByCreatedAt(a: any, b: any) {
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
}

function sortByUpdatedAt(a: any, b: any) {
  return new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime()
}

function sortBySize(a: any, b: any) {
  return (a.fileSize || 0) - (b.fileSize || 0)
}

// Department tree filter
const deptTreeData = computed(() => {
  const list = departments.value
  const roots: any[] = []
  const map = new Map<number, any>()
  list.forEach(d => map.set(d.id, { id: d.id, label: d.name, children: [] }))
  list.forEach(d => {
    const node = map.get(d.id)!
    if (d.parentId && map.has(d.parentId)) {
      map.get(d.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
})

// Filtered documents with client-side dept filter
const filteredDocuments = computed(() => {
  if (selectedDeptIds.value.size === 0) return documents.value
  const deptMap = new Map(departments.value.map(d => [d.id, d.name]))
  return documents.value.filter((d: any) => {
    // For new documents with departmentId set
    if (d.departmentId && selectedDeptIds.value.has(d.departmentId)) return true
    // Fallback for old documents: match by departmentName from owner
    if (d.departmentName) {
      for (const id of selectedDeptIds.value) {
        if (deptMap.get(id) === d.departmentName) return true
      }
    }
    return false
  })
})

function onDeptTreeCheck() {
  const tree = deptTreeRef.value
  if (!tree) return
  const checkedKeys = tree.getCheckedKeys(false) as number[]
  selectedDeptIds.value = new Set(checkedKeys)
}

function onDeptFilterShow() {
  if (!deptTreeRef.value) return
  nextTick(() => {
    deptTreeRef.value!.setCheckedKeys(Array.from(selectedDeptIds.value), false)
  })
}

function onDeptFilterHide() {
  // no-op, tree state persists
}

// 移动到文件夹
const moveDialogVisible = ref(false)
const moveTargetFolder = ref<number | null>(null)
const moveDocId = ref(0)
const createFolderVisible = ref(false)
const newFolderName = ref('')
const newFolderColor = ref('#409eff')
const newFolderTemplateId = ref(0)
const newFolderParentId = ref<number | null>(null)
const folderTemplates = ref<any[]>([])

// 编辑文件夹
const editFolderVisible = ref(false)
const editingFolder = ref<FolderType | null>(null)
const editFolderName = ref('')
const editFolderColor = ref('#409eff')
const editFolderParentId = ref<number | null>(null)

// 文件夹管理页拖拽（鼠标事件实现）
const mgmtDragFolder = ref<FolderType | null>(null)
const mgmtDragIdx = ref(-1)
const mgmtInsertIdx = ref(-1)
let mgmtOrigRects: DOMRect[] = []

function getMgmtTransform(idx: number): string {
  if (mgmtInsertIdx.value < 0) return ''
  if (idx === mgmtDragIdx.value) return ''
  const src = mgmtDragFolder.value
  if (!src) return ''
  const folder = flatFolders.value[idx]
  if (!folder || (folder.parentId ?? null) !== (src.parentId ?? null)) return ''
  const ins = mgmtInsertIdx.value
  const srcIdx = mgmtDragIdx.value
  if (srcIdx < ins && idx >= ins) return `translateY(${SHIFT_PX}px)`
  if (srcIdx > ins && idx >= ins && idx < srcIdx) return `translateY(${SHIFT_PX}px)`
  return ''
}
let mgmtStartY = 0
let mgmtMoved = false
let mgmtGhost: HTMLElement | null = null

function onMgmtMouseDown(e: MouseEvent, folder: FolderType, idx: number) {
  mgmtStartY = e.clientY
  mgmtMoved = false
  mgmtDragFolder.value = folder
  mgmtDragIdx.value = idx
  mgmtInsertIdx.value = -1

  // 缓存原始位置
  const items = document.querySelectorAll('.folder-mgmt-item')
  mgmtOrigRects = Array.from(items).map(el => el.getBoundingClientRect())

  // 创建简化的幽灵元素
  const el = (e.currentTarget as HTMLElement)
  const rect = el.getBoundingClientRect()
  mgmtGhost = document.createElement('div')
  mgmtGhost.textContent = folder.name
  mgmtGhost.style.cssText = [
    'position:fixed',
    `left:${rect.left}px`,
    `top:${rect.top}px`,
    `width:${rect.width}px`,
    `height:${rect.height}px`,
    'display:flex',
    'align-items:center',
    'padding:12px 16px',
    'opacity:0.9',
    'pointer-events:none',
    'z-index:9999',
    'box-shadow:0 4px 16px rgba(0,0,0,0.2)',
    'border-radius:8px',
    'background:#fff',
    'border:1px solid var(--el-color-primary)',
    'font-size:14px',
    'color:#303133',
    'cursor:grabbing'
  ].join(';')
  document.body.appendChild(mgmtGhost)

  document.addEventListener('mousemove', onMgmtMouseMove)
  document.addEventListener('mouseup', onMgmtMouseUp)
}

function onMgmtMouseMove(e: MouseEvent) {
  if (!mgmtDragFolder.value) return
  if (Math.abs(e.clientY - mgmtStartY) < 5 && !mgmtMoved) return
  mgmtMoved = true

  // 幽灵跟随鼠标
  if (mgmtGhost) {
    const h = mgmtGhost.offsetHeight || 48
    mgmtGhost.style.top = (e.clientY - h / 2) + 'px'
  }

  // 用缓存的原始位置做检测
  const items = document.querySelectorAll('.folder-mgmt-item')
  if (mgmtOrigRects.length === 0) {
    mgmtOrigRects = Array.from(items).map(el => el.getBoundingClientRect())
  }
  let newInsert = -1
  for (let i = 0; i < mgmtOrigRects.length; i++) {
    if (i === mgmtDragIdx.value) continue
    const rect = mgmtOrigRects[i]
    if (e.clientY >= rect.top && e.clientY <= rect.bottom) {
      const relY = (e.clientY - rect.top) / rect.height
      if (relY < 0.4) {
        newInsert = i
      } else if (relY > 0.6) {
        newInsert = i + 1
      } else {
        newInsert = mgmtInsertIdx.value
      }
      break
    }
  }
  if (newInsert < 0 && mgmtOrigRects.length > 0) {
    const lastRect = mgmtOrigRects[mgmtOrigRects.length - 1]
    if (e.clientY > lastRect.bottom) newInsert = mgmtOrigRects.length
  }

  if (newInsert >= 0) {
    const src = mgmtDragFolder.value
    const siblingIndices: number[] = []
    flatFolders.value.forEach((f, i) => {
      if ((f.parentId ?? null) === (src.parentId ?? null) && i !== mgmtDragIdx.value) {
        siblingIndices.push(i)
      }
    })
    if (siblingIndices.length > 0) {
      const minIdx = siblingIndices[0]
      const maxIdx = siblingIndices[siblingIndices.length - 1] + 1
      if (newInsert >= minIdx && newInsert <= maxIdx) {
        mgmtInsertIdx.value = newInsert
      } else {
        mgmtInsertIdx.value = -1
      }
    } else {
      mgmtInsertIdx.value = -1
    }
  } else {
    mgmtInsertIdx.value = -1
  }
}

async function onMgmtMouseUp() {
  document.removeEventListener('mousemove', onMgmtMouseMove)
  document.removeEventListener('mouseup', onMgmtMouseUp)

  // 移除幽灵
  if (mgmtGhost) {
    mgmtGhost.remove()
    mgmtGhost = null
  }

  const src = mgmtDragFolder.value
  const insertIdx = mgmtInsertIdx.value

  mgmtDragFolder.value = null
  mgmtDragIdx.value = -1
  mgmtInsertIdx.value = -1
  mgmtOrigRects = []

  if (!src || !mgmtMoved || insertIdx < 0) return
  mgmtMoved = false

  try {
    const siblings = flatFolders.value
      .filter(f => f.parentId === src.parentId)
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    const ids = siblings.map(f => f.id)
    const fromIdx = ids.indexOf(src.id)
    ids.splice(fromIdx, 1)
    const adjusted = insertIdx > fromIdx ? insertIdx - 1 : insertIdx
    ids.splice(adjusted, 0, src.id)
    await folderApi.reorder(ids)
    ElMessage.success('排序已更新')
    loadFolders()
  } catch {
    ElMessage.error('操作失败')
  }
}

const folderColors = [
  { value: '#409eff', name: '蓝色' },
  { value: '#67c23a', name: '绿色' },
  { value: '#e6a23c', name: '橙色' },
  { value: '#f56c6c', name: '红色' },
  { value: '#909399', name: '灰色' },
  { value: '#9b59b6', name: '紫色' },
  { value: '#1abc9c', name: '青色' },
  { value: '#e91e63', name: '粉色' }
]

function showCreateFolder() {
  newFolderName.value = ''
  newFolderColor.value = '#409eff'
  newFolderTemplateId.value = 0
  newFolderParentId.value = null
  loadFolderTemplates()
  createFolderVisible.value = true
}

async function loadFolderTemplates() {
  try {
    folderTemplates.value = await folderTemplateApi.getAll()
  } catch {}
}

async function handleCreateFolder() {
  if (!newFolderName.value.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  try {
    const parentId = newFolderParentId.value || activeFolderId.value || undefined
    const folder = await folderApi.create({ name: newFolderName.value.trim(), color: newFolderColor.value, parentId })
    ElMessage.success('文件夹已创建')
    createFolderVisible.value = false
    // 如果选择了模板，创建子文件夹
    if (newFolderTemplateId.value) {
      const tpl = folderTemplates.value.find((t: any) => t.id === newFolderTemplateId.value)
      if (tpl && tpl.structure) {
        const structure = typeof tpl.structure === 'string' ? JSON.parse(tpl.structure) : tpl.structure
        if (Array.isArray(structure)) {
          for (const item of structure) {
            const childFolder = await folderApi.create({ name: item.name, parentId: folder.id })
            // 如果有子文件夹，递归创建
            if (item.children && Array.isArray(item.children)) {
              for (const childName of item.children) {
                await folderApi.create({ name: childName, parentId: childFolder.id })
              }
            }
          }
          ElMessage.success('已从模板创建子文件夹')
        }
      }
    }
    loadFolders()
  } catch {
    ElMessage.error('创建失败')
  }
}

// 编辑文件夹：排除当前文件夹及其子文件夹作为可选上级
const availableParentFolders = computed(() => {
  if (!editingFolder.value) return folders.value
  const excluded = new Set<number>()
  function collectDescendants(id: number) {
    excluded.add(id)
    folders.value.filter(f => f.parentId === id).forEach(f => collectDescendants(f.id))
  }
  collectDescendants(editingFolder.value.id)
  return folders.value.filter(f => !excluded.has(f.id))
})

function showEditFolder(folder: FolderType) {
  editingFolder.value = folder
  editFolderName.value = folder.name
  editFolderColor.value = folder.color || '#409eff'
  editFolderParentId.value = folder.parentId ?? null
  editFolderVisible.value = true
}

function showAddSubFolder(parentFolder: FolderType) {
  newFolderName.value = ''
  newFolderColor.value = '#409eff'
  newFolderTemplateId.value = 0
  newFolderParentId.value = parentFolder.id
  createFolderVisible.value = true
}

async function handleUpdateFolder() {
  if (!editFolderName.value.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  if (!editingFolder.value) return
  try {
    await folderApi.update(editingFolder.value.id, {
      name: editFolderName.value.trim(),
      color: editFolderColor.value,
      parentId: editFolderParentId.value
    })
    ElMessage.success('文件夹已更新')
    editFolderVisible.value = false
    loadFolders()
  } catch {
    ElMessage.error('更新失败')
  }
}

function showMoveDialog(doc: any) {
  moveDocId.value = doc.id
  moveTargetFolder.value = null
  moveDialogVisible.value = true
}

async function handleMoveToFolder() {
  try {
    await documentApi.moveToFolder(moveDocId.value, moveTargetFolder.value)
    ElMessage.success('已移动到文件夹')
    moveDialogVisible.value = false
    documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
  } catch {
    ElMessage.error('移动失败')
  }
}

async function handleTableCommand(cmd: string, row: any) {
  if (cmd === 'star') {
    try {
      await documentApi.toggleStar(row.id)
      documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
    } catch {
      ElMessage.error('操作失败')
    }
  } else if (cmd === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
        inputValue: row.title,
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })
      if (value && value.trim()) {
        await documentApi.rename(row.id, value.trim())
        documentStore.fetchDocuments({ sort: sortBy.value, size: 10 })
      }
    } catch {
      // cancelled
    }
  } else if (cmd === 'move') {
    showMoveDialog(row)
  } else if (cmd === 'share') {
    openShareDialog(row.id)
  } else if (cmd === 'delete') {
    handleDelete(row.id)
  }
}
</script>

<style scoped>
.home-page {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

/* Sidebar */
.sidebar {
  width: 220px;
  background: #fff;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
}

.logo {
  padding: 20px;
  border-bottom: 1px solid #e8ecf4;
}

.logo h2 {
  font-size: 18px;
  font-weight: 700;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0 0 4px;
}

.logo .slogan {
  font-size: 12px;
  color: #909399;
}

.nav-list {
  list-style: none;
  padding: 12px 0;
  margin: 0;
  flex: 1;
  overflow-y: auto;
}

.nav-divider {
  height: 1px;
  background: #e8ecf4;
  margin: 8px 16px;
}

.nav-list li:not(.nav-divider) {
  padding: 10px 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s;
}

.nav-list li:not(.nav-divider):hover {
  background: var(--hover-bg);
  color: var(--el-color-primary);
}

.nav-list li:not(.nav-divider).active {
  background: var(--active-bg);
  color: var(--el-color-primary);
  font-weight: 500;
}

.nav-list li:not(.nav-divider) .el-icon {
  font-size: 18px;
}

/* Main content */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.top-bar-left {
  display: flex;
  gap: 12px;
}

.top-bar-left :deep(.el-button--primary) {
  background: var(--primary-gradient);
  border: none;
}

.top-bar-left :deep(.el-button--primary:hover) {
  opacity: 0.9;
  filter: brightness(1.1);
}

.top-bar-left .el-button {
  border-radius: 6px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
  line-height: 1;
}

.pagination-bar {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 0;
  border-top: 1px solid #ebeef5;
  background: #f5f7fa;
}


.search-input {
  width: 500px;
  margin-left: auto;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 20px;
}

.search-wrapper {
  position: relative;
  margin-left: auto;
}

.search-wrapper .search-input {
  margin-left: 0;
}

.folder-breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
  font-size: 13px;
}

.breadcrumb-item {
  color: #909399;
  cursor: pointer;
  transition: color 0.2s;
}

.breadcrumb-item:hover {
  color: #409eff;
}

.breadcrumb-item.active {
  color: #303133;
  font-weight: 500;
  cursor: default;
}

.breadcrumb-segment {
  display: flex;
  align-items: center;
  gap: 4px;
}

.breadcrumb-segment .el-icon {
  font-size: 12px;
  color: #c0c4cc;
}

/* 文件夹路径面包屑 */
.folder-path {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 18px;
  font-weight: 600;
}

.path-item {
  color: #909399;
  cursor: pointer;
  transition: color 0.2s;
}

.path-item:hover {
  color: var(--el-color-primary);
}

.path-item.active {
  color: #303133;
  cursor: default;
}

.path-item.root {
  color: #606266;
}

.path-sep {
  color: #c0c4cc;
  margin: 0 2px;
  font-weight: 400;
}

.search-suggestions {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
  max-height: 400px;
  overflow-y: auto;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.suggestion-item:hover {
  background: #f5f7fa;
}

.suggestion-icon {
  color: #909399;
  flex-shrink: 0;
}

.suggestion-content {
  flex: 1;
  min-width: 0;
}

.suggestion-title {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suggestion-snippet {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  max-height: 40px;
  overflow: hidden;
  line-height: 1.4;
}

.suggestion-snippet :deep(mark) {
  background: #fef08a;
  color: #303133;
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 500;
}

.suggestion-footer {
  padding: 8px 14px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #f0f0f0;
  text-align: center;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  padding: 6px 12px;
  border-radius: 20px;
  transition: all 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.user-avatar {
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Content area */
.content-area {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.content-area.doc-list-wrapper {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.content-scroll {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.view-toggle :deep(.el-radio-button__inner) {
  padding: 6px 10px;
}

.view-toggle :deep(.el-radio-button__inner .el-icon) {
  font-size: 14px;
}

.content-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.doc-count {
  font-size: 14px;
  color: #909399;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

/* Batch action bar */
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
}

.batch-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.batch-count {
  font-size: 14px;
  color: var(--el-color-primary);
  font-weight: 500;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

/* List / table view */
.doc-table {
  border-radius: 8px;
  overflow: hidden;
}

.doc-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.doc-table :deep(.el-table__row) {
  cursor: pointer;
  transition: background 0.2s;
}

.doc-table :deep(.el-table__row:hover > td) {
  background: var(--hover-bg) !important;
}

.doc-table :deep(.selected-row) {
  background: var(--el-color-primary-light-9);
}

.doc-table :deep(.selected-row:hover > td) {
  background: var(--el-color-primary-light-8) !important;
}

/* Department filter in column header */
.doc-table :deep(.el-dropdown) {
  line-height: inherit;
}

.dept-filter-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
  width: 100%;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.dept-caret {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  height: 14px;
  width: 10px;
  vertical-align: middle;
  cursor: pointer;
  overflow: initial;
  position: relative;
}

.dept-caret .sort-caret {
  display: inline-block;
  width: 0;
  height: 0;
  border: 5px solid transparent;
  position: absolute;
  left: 0;
}

.dept-caret .sort-caret.descending {
  top: 4px;
  border-top-color: #c0c4cc;
}

.dept-caret.is-active .sort-caret.descending {
  border-top-color: var(--el-color-primary);
}

/* More action icon */
.more-icon {
  font-size: 18px;
  cursor: pointer;
  color: #909399;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.more-icon:hover {
  color: var(--el-color-primary);
  background: var(--hover-bg);
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-type-icon {
  font-size: 10px;
  flex-shrink: 0;
}

.doc-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 回收站样式 */
.trash-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.trash-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.trash-tip {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.trash-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trash-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.trash-item:hover {
  border-color: #d0d0d0;
}

.trash-item-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trash-icon {
  color: #909399;
}

.trash-item-details {
  display: flex;
  flex-direction: column;
}

.trash-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.trash-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.trash-item-actions {
  display: flex;
  gap: 8px;
}

.empty-state {
  padding: 60px 0;
}

.template-path {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-muted {
  color: #c0c4cc;
  font-size: 12px;
}

.nav-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
}

.nav-section-title {
  cursor: pointer;
  transition: color 0.2s;
}

.nav-section-title:hover,
.nav-section-title.active {
  color: var(--el-color-primary);
}

.nav-section-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-section-actions .el-button {
  padding: 4px;
  color: #909399;
}

.nav-section-actions .el-button:hover {
  color: var(--el-color-primary);
}

.folder-tree {
  padding: 0 8px;
}

.folder-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  color: #606266;
  font-size: 13px;
  user-select: none;
  -webkit-user-select: none;
  will-change: transform;
}

.folder-item.dragging {
  opacity: 0.3;
}

.folder-item:hover {
  background: #f5f7fa;
}

.folder-item.active {
  background: var(--active-bg, #ecf5ff);
  color: var(--el-color-primary);
}

.folder-item.drag-over {
  background: var(--el-color-primary-light-9);
  border: 1px dashed var(--el-color-primary);
  color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.folder-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  cursor: grab;
  color: #c0c4cc;
  flex-shrink: 0;
  margin-right: 2px;
  border-radius: 3px;
  transition: color 0.2s;
}

.folder-drag-handle:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.folder-drag-handle:active {
  cursor: grabbing;
}

.folder-icon-wrapper {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
}

.folder-icon {
  width: 16px;
  height: 16px;
}

.folder-toggle {
  flex-shrink: 0;
  cursor: pointer;
  color: #909399;
  font-size: 16px;
  padding: 4px;
  border-radius: 4px;
  transition: color 0.2s, background 0.2s;
}

.folder-toggle:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.folder-toggle-placeholder {
  flex-shrink: 0;
  width: 16px;
}

.folder-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-actions {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.folder-item:hover .folder-actions {
  opacity: 1;
}

.folder-more {
  cursor: pointer;
  color: #909399;
  padding: 2px;
  border-radius: 4px;
}

.folder-more:hover {
  background: #e4e7ed;
  color: #606266;
}

.color-picker {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.color-option {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.color-option:hover {
  transform: scale(1.1);
}

.color-option.selected {
  border-color: #303133;
  box-shadow: 0 0 0 2px white, 0 0 0 4px #303133;
}

.folder-empty {
  padding: 8px 12px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

/* 弹窗表单标签不换行 */
:deep(.el-form-item__label) {
  white-space: nowrap;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

/* 文件夹管理页面 */
.folder-mgmt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.folder-mgmt-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.folder-mgmt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.folder-mgmt-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  transition: transform 0.2s ease, opacity 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
}

.folder-mgmt-item:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.folder-mgmt-item:active {
  cursor: grabbing;
}

.folder-mgmt-item.dragging {
  opacity: 0.4;
  transform: scale(0.98) !important;
  border: 1px dashed var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.folder-mgmt-item.drag-target {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.insert-indicator {
  height: 3px;
  background: var(--el-color-primary);
  border-radius: 2px;
  margin: 4px 0;
  box-shadow: 0 0 6px var(--el-color-primary-light-5);
}

.folder-mgmt-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.folder-mgmt-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-mgmt-meta {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.folder-mgmt-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

</style>
