# MiaotongDoc Editor

企业级在线文档编辑器，支持 Word、Excel、PowerPoint 在线编辑与协作。

## 特性

- 在线编辑 Word、Excel、PowerPoint 文档
- 实时多人协作编辑
- 自定义品牌（完全定制化）
- 集成签署功能插件
- JWT 安全认证
- 无并发连接限制

## 快速开始

### 构建镜像

```bash
docker build -t miaotongdoc/editor:latest .
```

### 运行容器

```bash
docker run -d \
  --name miaotongdoc-editor \
  -p 8080:80 \
  -e JWT_ENABLED=true \
  -e JWT_SECRET=your_jwt_secret \
  -e JWT_IN_BODY=true \
  miaotongdoc/editor:latest
```

### 配置参数

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| JWT_ENABLED | 启用 JWT 认证 | true |
| JWT_SECRET | JWT 密钥 | - |
| JWT_IN_BODY | 从请求体获取 JWT | true |
| DB_HOST | 数据库主机 | postgres |
| DB_PORT | 数据库端口 | 5432 |
| DB_NAME | 数据库名称 | miaotongdocdb |
| DB_USER | 数据库用户 | miaotong |
| DB_PASSWORD | 数据库密码 | - |

## 目录结构

```
MiaotongDoc-Editor/
├── Dockerfile              # 镜像构建文件
├── README.md               # 说明文档
├── branding/               # 品牌资源（logo等）
├── config/
│   └── local.json          # 配置文件模板
├── plugins/
│   └── signing/            # 签署插件
└── scripts/
    ├── remove-license-limit.sh  # 并发限制解除脚本
    └── replace-branding.sh      # 品牌替换脚本
```

## 自定义品牌

1. 将 logo 文件放入 `branding/logos/` 目录：
   - `logo.png` - 主 logo
   - `favicon.ico` - 网站图标
   - `logo_embedded.png` - 嵌入式 logo

2. 重新构建镜像

## 签署插件

签署插件位于 `plugins/signing/`，提供以下功能：

- 在编辑器工具栏显示"提交签署"按钮
- 弹出签署对话框，填写签署信息
- 将签署请求发送到 MiaotongDoc 后端

## 并发限制

此镜像已解除并发连接限制，支持大规模并发编辑。

### 实现方式

1. **许可证检查修改** - 修改 `license.js` 中的连接数限制
2. **补丁模块** - Node.js 补丁覆盖许可证检查函数
3. **环境变量覆盖** - 设置 `LICENSE_CONNECTIONS_LIMIT=999999`
4. **许可证文件** - 创建企业版许可证文件

### 验证方法

```bash
# 启动容器
docker run -d -p 8080:80 miaotongdoc/editor:latest

# 查看日志，确认无限制警告
docker logs miaotongdoc-editor 2>&1 | grep -i "license\|limit"

# 测试并发 - 使用多个浏览器标签页同时打开不同文档
```

## 技术架构

- 基于开源编辑器引擎定制
- 支持 OOXML 格式（docx、xlsx、pptx）
- 使用 PostgreSQL 存储文档元数据
- 使用 Redis 缓存和会话管理
- WebSocket 实现实时协作

## 注意事项

- 适用于内部部署场景
- 生产环境建议配置 HTTPS
- 建议使用 Docker Compose 进行编排
- 升级版本时需要重新构建镜像
