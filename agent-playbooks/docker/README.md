# Docker 环境管理

本目录包含 Shining-music 项目的 Docker 中间件和全服务生命周期管理脚本。

---

## 脚本说明

| 脚本 | 用途 |
|------|------|
| `init-docker.sh` | 从迁移包初始化 Docker 环境（MySQL / Redis / MinIO / Nacos / ES / RabbitMQ） |
| `start-all.sh` | 一键启动全部服务（Docker 中间件 → 后端 Java 服务 → 前端） |
| `stop-all.sh` | 一键停止全部服务（前端 → 后端 → Docker） |

## 前置要求

- Docker & Docker Compose
- JDK 18（默认路径 `~/.local/java/jdk-18`，可通过 `JDK18_HOME` 环境变量覆盖；脚本仍兼容旧的 `JDK21_HOME`）
- Node.js & npm（前端构建）
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

## 日志

所有服务日志输出到 `项目根目录/logs/` 下，每个服务一个 `.log` 和 `.pid` 文件。
