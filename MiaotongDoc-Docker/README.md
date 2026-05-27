# MiaotongDoc 部署指南

## 目录结构

```
deploy/
├── docker-compose.yml      # Docker Compose 编排文件
├── .env.example            # 环境变量模板
├── nginx/
│   └── nginx.conf          # Nginx 配置（含 WebSocket 升级）
├── postgres/
│   └── init.sql            # PostgreSQL 初始化脚本
└── redis/
    └── redis.conf          # Redis 配置（安全加固）
```

## 快速部署

### 1. 准备环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，设置以下必填项：
# - DB_PASSWORD: 数据库密码
# - REDIS_PASSWORD: Redis 密码
# - APP_JWT_SECRET: 应用 JWT 密钥（至少32字符）
# - EDITOR_JWT_SECRET: 编辑器 JWT 密钥
```

### 2. 构建后端

```bash
cd ../miaotongdoc-server
mvn clean package -DskipTests
```

### 3. 构建前端

```bash
cd ../miaotongdoc-web
npm install
npm run build
```

### 4. 启动服务

```bash
cd ../deploy
docker-compose up -d
```

### 5. 访问系统

- 前端地址: http://localhost
- 后端 API: http://localhost/api
- 初始管理员: 工号 10000001，密码 Admin@123

## 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| nginx | 80 | 反向代理 + 前端静态资源 |
| postgres | 5432 | PostgreSQL 数据库 |
| redis | 6379 | Redis 缓存 |
| web-server | 9004 | Spring Boot 后端 |
| editor | 8080 | 文档编辑器 |

## 常用命令

```bash
# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f web-server

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 完全清理（包括数据卷）
docker-compose down -v
```

## SSO 配置（可选）

如需启用 SSO 单点登录，在 `.env` 中配置：

```bash
SSO_ENABLED=true
SSO_ISSUER_URI=https://sso.bank.com/realms/miaotong
SSO_CLIENT_ID=miaotongdoc
SSO_CLIENT_SECRET=your_client_secret
```
