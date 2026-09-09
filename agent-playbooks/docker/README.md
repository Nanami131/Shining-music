# Docker 环境管理

本目录包含 Shining-music 项目的 Docker 中间件和全服务生命周期管理脚本。

---

## 脚本说明

| 脚本 | 用途 |
|------|------|
| `init-docker.sh` | 从迁移包初始化 Docker 环境（MySQL / Redis / MinIO / Nacos / ES / RabbitMQ） |
| `start-all.sh` | 构建并一键启动全部 Docker 服务（中间件、后端、前端、Cloudflare Tunnel） |
| `stop-all.sh` | 一键停止全部 Docker 服务，保留 `docker-data/` 数据 |

## 前置要求

- Docker & Docker Compose
- 项目根目录下已有 `docker-compose.yml`

## 使用方式

### 首次初始化（新环境）

```bash
# 需要迁移包路径作为参数
bash agent-playbooks/docker/init-docker.sh ~/下载/migration-package
```

迁移包目录结构要求：

```
migration-package/
├── data/
│   ├── mysql/shining_full.sql
│   ├── redis/dump.rdb
│   ├── minio-root/shining/
│   ├── minio-root/.minio.sys/
│   └── elasticsearch/elasticsearch_music_search.json  (可选)
```

### 日常启停

```bash
# 启动所有服务
bash agent-playbooks/docker/start-all.sh

# 停止所有服务
bash agent-playbooks/docker/stop-all.sh
```

## 服务端口

| 服务 | 端口 |
|------|------|
| Frontend (Vite) | 5173 |
| Gateway | 8080 |
| User Service | 8081 |
| Music Service | 8082 |
| Community Service | 8083 |
| Statistics Service | 8084 |
| Recommend Service | 8085 |
| Nacos | 8848 |
| MinIO Console | 9090 |
| RabbitMQ Management | 15672 |
| Elasticsearch | 9200 |

前端、后端和 Cloudflare 运行在容器内。浏览器只通过前端的 `5173` 端口访问应用；后端服务端口只在 Docker 网络内开放。`dnsproxy` 只为 Cloudflare Tunnel 提供 DNS-over-HTTPS 解析，不发布宿主机端口。

Cloudflare Quick Tunnel 的公网地址会由 `start-all.sh` 输出，网站继续使用项目原有登录。

## 日志

使用 `docker compose logs -f` 查看全部服务日志，或使用 `docker compose logs -f <服务名>` 查看单个服务。
