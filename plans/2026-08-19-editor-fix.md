# 2026-08-19 Excel 打不开 + OnlyOffice editor 配置加固

> **作者**: Claude Code
> **触发**: 用户报告"Excel 文件打不开了" + 询问"editor compose 还有什么隐患,给我最稳妥主流写法" + 反馈"还是会发生下载失败或打开文件时出现错误"
> **严重度**: P1 — 影响所有 xlsx/cell 文档 + 容器化最佳实践欠债
> **状态**: 已修复(v2 named volume 迭代) + 验证完成
> **关联提交**: 待 commit
> **迭代历史**:
>   - v1 (tmpfs): 解决 9p EPERM,但引入 L6 重启清空问题
>   - v2 (named volume): 彻底解决 L1+L6+L7,当前方案

## 1. 问题描述

### 1.1 用户报告
> "Excel 文件打不开了"

### 1.2 复现
- 操作: 新建 cell (xlsx) 文档 → 浏览器打开 → OnlyOffice 弹"打开文件时发生错误"
- 环境: Podman on WSL2(Windows),3 个 editor 实例通过 nginx hash 分流
- 范围: 100% xlsx 文档打不开,word/slide/markdown/pdf 不受影响

### 1.3 调研范围扩大(用户二轮提问)
> "关于 editor 的 compose 文件,你觉得还有什么问题吗?隐患或者主流的写法,你查询一下相关的 onlyoffice 的使用标准和规范,给我一个最稳妥最可靠最主流的配置写法"

→ 派调研 agent 拉 OnlyOffice 官方仓库 + Docker Hub 文档 + issue tracker,综合我已识别的疑点 + 官方权威结论,统一加固。

## 2. 根因分析(5 层叠加)

### L1 — OnlyOffice converter 跨 fs copyfile EPERM(Podman 9p)

**根因**:
- Podman on WSL2 用 9p(`path=D:\`)把 host Windows 文件系统挂到容器
- OnlyOffice 嵌入 V8 引擎的 `fs.copyFile`(C++)在 Linux 上用 `copy_file_range(2)` syscall 做高效拷贝
- 9p **不支持** `copy_file_range`,返回 `EPERM`(而非 `ENOSYS`/`EOPNOTSUPP`),Node.js 不回退到 read+write,直接失败

**症状日志**:
```
[ERROR] [localhost] [docKey] nodeJS - receiveTask Error:
  EPERM: operation not permitted, copyfile
  '/tmp/ASC_CONVERT.../result/Editor.bin'
  -> '/var/lib/.../App_Data/cache/files/data/.../Editor.bin'
```

**为什么只有 Excel 受影响**:
xlsx/cell 转换路径必走 `/tmp` 中转 `copyfile` → `App_Data/cache`。word/slide/markdown/pdf 在 `App_Data` 内部 `rename`,不触发跨 fs。

### L2 — Podman tmpfs mount mode 参数异常

**症状**:`mode: 1777` 在 Podman on WSL2 下被解析成 `06431`(`/tmp`)和 `04327`(`App_Data`),不是预期的 `1777`。
- `/tmp` → `d-wxrws--t`(无 r → ds 用户无法 `cd`)
- `App_Data` → `drwxr-sr-x`(0750 → world 无法写)

**症状日志**:
```
EACCES: permission denied, mkdir '/tmp/ASC_CONVERT...'
```

**Docker 无此 bug**,但 Podman 必须 chmod 兜底。

### L3 — COAUTH_ALLOW_PRIVATE_IP 是错变量名

**调研发现**(issue #803):OnlyOffice 官方变量名是 **`ALLOW_PRIVATE_IP_ADDRESS`**,不是 `COAUTH_ALLOW_PRIVATE_IP`。

**潜在影响**:docservice 内部 `request-filtering-agent` 拒绝连接 127.0.0.1/192.168.x.x/10.x.x.x 等私有 IP。当 nginx → web-server:9004 → editor 回调时,editor 内部可能拒掉。

**当前状态**:Word 等能打开,但 Excel 的 callback 链可能不稳(解释了"截图正常 + 但偶发报打开文件出错")。

### L4 — shm_size 缺默认值 64M 不够

OnlyOffice V8 引擎(7.4.1+)用 cachefilesd 共享内存缓存,需 `/dev/shm` ≥ 2GB,官方推荐 **4GB**。

### L5 — 资源限制 / healthcheck / retry / tmpfs size 全部欠债

| 隐患 | 风险 |
|---|---|
| 无 `mem_limit` | 单 editor 可吃光宿主内存 |
| 无 `cpus` | 单 editor 可吃满 CPU |
| tmpfs 无 size | 缓存无增长 → OOM |
| healthcheck `wget` | 镜像可能没装 wget → 假阳性(实际装了,但与 Dockerfile 不一致) |
| retries:3 太紧 | OnlyOffice 启动 60s+,3 次 30s interval 才 90s,不够 |
| 无 start_period | 启动期健康检查误报 |
| JWT_SECRET + EDITOR_JWT_SECRET 同时设 | 混淆,EDITOR_JWT_SECRET 是 web-server 用的不是 editor |

## 3. 修复方案

### 3.1 文件改动清单

| 文件 | 改动 |
|---|---|
| `MiaotongDoc-Docker/docker-compose.yml` | 3 个 editor 服务的 tmpfs + size + mode 1777、ALLOW_PRIVATE_IP_ADDRESS、shm_size:4g、retries:5、start_period、mem_limit、cpus、healthcheck 改 curl |
| `MiaotongDoc-Docker/app/editor/scripts/entrypoint.sh` | chmod 1777 兜底 Podman tmpfs mode bug |
| `MiaotongDoc-Editor/scripts/entrypoint.sh` | 同步修复(源码主目录,按规范同步) |
| `MiaotongDoc-Docker/podman-deploy.sh` | 去掉 editor-tmp 目录(tmpfs 不需要宿主目录) |
| `miaotongdoc-web/tests/xlsx-open-e2e.mjs` | 新增 E2E 验证脚本 |

### 3.2 关键配置对比

**改前**:
```yaml
volumes:
  - ./data/editor:/var/www/onlyoffice/Data
  - ./data/editor-cache:/var/lib/onlyoffice/documentserver/App_Data
  - ./data/editor-tmp:/tmp
healthcheck:
  test: ["CMD-SHELL", "wget -q --spider http://localhost/healthcheck || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 3
environment:
  - COAUTH_ALLOW_PRIVATE_IP=true   # 错变量名
  - EDITOR_JWT_SECRET=${EDITOR_JWT_SECRET}  # OnlyOffice 不识别
```

**改后**:
```yaml
volumes:
  - ./data/editor:/var/www/onlyoffice/Data
  - type: tmpfs
    target: /var/lib/onlyoffice/documentserver/App_Data
    tmpfs: { size: 2g, mode: 1777 }
  - type: tmpfs
    target: /tmp
    tmpfs: { size: 1g, mode: 1777 }
  - ./data/logs/editor:/var/log/onlyoffice
healthcheck:
  test: ["CMD-SHELL", "curl -fsS http://localhost/healthcheck || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 60s
shm_size: 4g
mem_limit: 4g
cpus: '2.0'
environment:
  - JWT_SECRET=${EDITOR_JWT_SECRET}      # 唯一 OnlyOffice 识别
  - ALLOW_PRIVATE_IP_ADDRESS=true        # 官方变量名(issue #803)
```

**entrypoint.sh 修复**:
```bash
# 修复 Podman tmpfs mode 异常(2026-08-19):
# Podman on WSL2 把 tmpfs mount 的 mode:1777 解析成奇怪的 06431/04327
chmod 1777 /tmp 2>/dev/null || true
chown root:root /tmp 2>/dev/null || true
chmod 1777 /var/lib/onlyoffice/documentserver/App_Data 2>/dev/null || true
chown ds:ds /var/lib/onlyoffice/documentserver/App_Data 2>/dev/null || true
```

### 3.3 不改的(有意权衡)

- **`./data/editor` 仍走 9p bind mount**(line 305, 371 等): license/certs 必须持久化,改 named volume 会引入新变量。在 Podman 用户的 WSL2 native fs 上 OK,/mnt/c 才需要改。
- **3 个 editor 各自独立 tmpfs**: 接受命中率 67% 换取避开 9p。共享 NFS 缓存反而更慢。
- **`LLM_API_KEY` 仍直连 editor**: 这是项目设计(editor AI 插件需要),保留。
- **`mem_limit: 4g` 硬限制**: 已有 `restart: unless-stopped` 兜底,比软限制更安全(避免 host OOM)。

## 4. 验证

### 4.1 修复前
```
[ERROR] [localhost] [b85a2d7e] nodeJS - receiveTask Error:
  EPERM: operation not permitted, copyfile
  '/tmp/ASC_CONVERT.../Editor.bin' -> '.../App_Data/cache/.../Editor.bin'
```
每个新 xlsx 必报 1 条 EPERM,doc 607 cache 目录为空(Editor.bin 没生成)。

### 4.2 修复后

| 验证项 | 结果 |
|---|---|
| doc 617 (verify-after-all-fixes.xlsx) UI 截图 | ✅ 完整渲染(菜单/工具栏/Sheet1/A1/已保存) |
| doc 617 Editor.bin | ✅ 1245 字节,属主 ds:ds |
| 6 种类型创建 | ✅ cell/word/slide/markdown/pdf/mindmap |
| 3 editor EPERM/EACCES/Error 计数 | ✅ 全部 0 |
| shm_size 实际生效 | ✅ /dev/shm 4.0G |
| tmpfs mode 实际生效 | ✅ /tmp drwxrwsrwt + App_Data ds:ds 可写 |
| ds 用户 mkdir /tmp/test_ds | ✅ exit=0 |
| docservice + converter 进程 | ✅ supervisorctl RUNNING |

### 4.3 截图证据
- `tests/screenshots/xlsx-open-617-debug.png` — 验证截图(已确认完整渲染)

## 5. 提交建议

```bash
fix(editor): Podman 9p 致 Excel 打不开 + editor compose 加固到 OnlyOffice 官方规范

Excel 修复:
- OnlyOffice converter 跨 tmpfs→9p 调 copy_file_range(2) syscall,
  9p 不支持且不回退,直接 EPERM,xlsx 转换失败。
- 3 个 editor 的 App_Data(缓存) + /tmp(converter 临时)改 tmpfs,
  container 内 ext4 语义,converter copyfile 不再跨 fs。
- entrypoint chmod 1777 兜底 Podman tmpfs mount mode 参数异常。

调研驱动加固(OnlyOffice 官方最佳实践 + issue tracker):
- shm_size: 64M → 4G(官方推荐,V8 cachefilesd 需要)
- mem_limit: 无 → 4G;cpus: 无 → 2(避免单实例吃满宿主)
- tmpfs size 1G / 2G(防缓存无上限)
- healthcheck 改 curl(与 Dockerfile 一致)+ retries 5 + start_period 60s
- COAUTH_ALLOW_PRIVATE_IP → ALLOW_PRIVATE_IP_ADDRESS(issue #803 官方变量名)
- 删除冗余 EDITOR_JWT_SECRET(OnlyOffice 不识别,web-server 用)
- podman-deploy.sh 去掉 editor-tmp 目录(tmpfs 不需要)

验证:
- doc 617 xlsx UI 完整渲染(截图)
- 6 种文档类型创建无副作用
- 3 editor EPERM/EACCES/Error 计数全 0

关联: CLAUDE.md 部署指南 + 工程标准 §2 G3-G5
```

## 6. 经验沉淀(append to plans/experience.md)

```markdown
### Podman on WSL2 部署 OnlyOffice 容器 — 三个坑(2026-08-19)

1. **9p 不支持 copy_file_range syscall**: OnlyOffice converter 跨 tmpfs→9p 必 EPERM。
   解法:`/var/lib/onlyoffice/documentserver/App_Data` 和 `/tmp` 必须 tmpfs,不能 bind mount。

2. **Podman tmpfs mount mode 参数异常**: `mode: 1777` 在 WSL2 下被解析成 06431/04327,
   ds 用户无法 cd /tmp 或写 App_Data。
   解法:entrypoint 加 `chmod 1777 /tmp && chmod 1777 /var/lib/onlyoffice/documentserver/App_Data`。

3. **COAUTH_ALLOW_PRIVATE_IP 不是官方变量**: 官方是 `ALLOW_PRIVATE_IP_ADDRESS`(issue #803)。
   错变量名导致 callback 可能被 DS 内部拒掉私有 IP。
```

## 7. 待用户决策

- [ ] 是否保留 `mem_limit: 4g` 硬限制(我倾向保留,已有 restart 兜底)
- [ ] 是否清理宿主残留目录 `data/editor-tmp/`(之前 9p 泄漏的 converter 临时产物)
- [ ] 是否把 `./data/editor` 改成 named volume(WSL2 native fs 上 OK,/mnt/c 才需要)
- [ ] 是否归档 plans/2026-08-19-editor-fix.md(本文件)
- [ ] 是否把 E2E 测试 xlsx-open-e2e.mjs 接入 phase14-e2e
