# Shining Music 项目数据迁移 Playbook

## Goal

Agent 读完此文档后，应能自主完成 Shining Music 项目从一台机器到另一台机器的完整数据迁移。

## 系统概况（截至 2026-04）

6 个 Spring Boot 微服务 + Vue 3 前端，依赖 MySQL 8 + Redis 7 + Elasticsearch 8 + MinIO + RabbitMQ + Nacos 2。

当前数据规模：305 首歌、46 位歌手、383 条歌词、8,279 条标签关联、22,852 条播放记录、299 篇 ES 索引文档、11 个用户、21 个歌单。

## Scope

### 需要迁移的数据

**MySQL（5 个库）**

| 库 | 表 | 说明 |
|----|-----|------|
| `nacos` | config_info 等 13 张系统表 | Nacos 配置存储，恢复后 Nacos 自动提供服务配置 |
| `shining-user` | users, password_history | 用户账号与密码变更历史 |
| `shining-music` | songs, singers, playlists, lyrics, videos, tag_definitions, song_tags, user_preferences | 歌曲/歌手/歌单/歌词/视频元数据 + 28 维标签体系 + 用户偏好向量 |
| `shining-community` | posts, post_comments, post_likes, user_follows | 社区帖子/评论/点赞/关注关系 |
| `shining-statistics` | user_song_play_record, user_event_log, user_profile | 播放记录/用户事件/用户画像 |

**Elasticsearch**

| 索引 | 文档数 | 说明 |
|------|--------|------|
| `music_search` | ~299 | 歌曲搜索索引（songId、name、singerName、language 等字段），支持中日文分词 |

**MinIO（`shining` bucket）**

| 路径 | 内容 |
|------|------|
| `songs/audio/` | 音频文件（MP3/FLAC） |
| `songs/cover/` | 歌曲封面 |
| `songs/lyrics/` | LRC 歌词文件 |
| `singer/avator/` | 歌手头像 |
| `playlist/cover/` | 歌单封面 |
| `user/avator/` | 用户头像 |
| `community/images/` | 社区帖子图片 |
| `community/files/` | 社区帖子附件 |
| `video/file/` | 视频文件 |

**Redis（DB 4）**

| Key 模式 | 说明 | 是否迁移 |
|----------|------|---------|
| `song:vector:*` | 歌曲 28 维标签向量（CB 推荐） | 是（不迁移则需重建，`/api/recommend/tags/rebuild-vectors`） |
| `itemcf:sim:*` | Item-CF 物品相似度矩阵 | 是（不迁移则需重建，`/api/recommend/item-cf/rebuild`） |
| `itemcf:version` | Item-CF 矩阵版本号 | 是 |
| `user:preference:*` | 用户偏好向量 | 是（不迁移则需通过播放事件重新累积） |
| `user:songplay:*` | 用户-歌曲播放次数（Item-CF 输入） | 是 |
| `playlist:*` | 歌单歌曲关联（ZSET） | 是 |
| `recommend:daily:*` | 每日推荐缓存 | 否（自动重建） |
| `jwt:*` | JWT 登录态 | 否（恢复后必须清理） |
| `cache:userInfo:*` | 用户信息缓存 | 否（自动重建） |

### 不需要迁移

- RabbitMQ 历史消息（空实例启动即可，队列由服务自动声明）
- Redis 登录态和用户缓存（恢复后清理）
- MinIO 废弃 bucket（如 `user01`）

## 端口约束

| 组件 | 端口 |
|------|------|
| MySQL | 3306 |
| Redis | 6379 |
| RabbitMQ | 5672 / 15672 |
| MinIO | 9000 / 9090 |
| Nacos | 8848 |
| Gateway | 8080 |
| User Service | 8081 |
| Music Service | 8082 |
| Community Service | 8083 |
| Statistics Service | 8084 |
| Recommend Service | 8085 |
| Frontend | 5173 |

端口变更需同步修改 Nacos 中对应服务的配置。

## 默认凭据

| 组件 | 用户名 | 密码 |
|------|--------|------|
| MySQL | root | password |
| Redis | — | 空 |
| RabbitMQ | guest | guest |
| MinIO | minioadmin | minioadmin |

JWT secret 存储在 Nacos `secret.yaml`，通过 MySQL `nacos` 库自动恢复。若新机器凭据不同，需在 Nacos 管理界面修改。

## 导出流程（在旧机器执行）

### 使用 export-data.sh

```bash
bash agent-playbooks/migration/export-data.sh
```

脚本行为：
1. `mysqldump` 导出 5 个库到 `sql/shining_full.sql`（含 DDL + 数据 + 存储过程 + 触发器）
2. 通过 ES REST API 导出 `music_search` 索引的 mapping + 全部文档到 `sql/elasticsearch_music_search.json`
3. 打印导出摘要（歌曲数、歌手数、歌词数、ES 文档数）

**前提**：MySQL 和 ES 在 Docker 容器中运行（容器名 `shining-mysql`）。如果是原生安装，需手动执行等价的 `mysqldump` 和 ES API 调用。

### 手动导出（原生安装环境）

```bash
# MySQL
mysqldump -uroot -ppassword \
  --databases nacos shining-user shining-music shining-community shining-statistics \
  --add-drop-database --add-drop-table --complete-insert \
  --routines --triggers --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 > sql/shining_full.sql

# Elasticsearch（使用 restore-es.py 的导出模式，或手动 curl）
# 手动导出参见 export-data.sh 中的 Python 片段

# Redis
redis-cli -n 4 BGSAVE
# 拷贝 Redis 工作目录下的 dump.rdb

# MinIO
# 方式 A：文件系统级拷贝（MinIO data root 目录）
# 方式 B：mc mirror minio/shining ./minio-backup/
```

### 打包传输

```bash
tar czf shining-migration.tar.gz \
  sql/shining_full.sql \
  sql/elasticsearch_music_search.json \
  <minio-data-dir>/ \
  <redis-dump.rdb>
```

## 恢复流程（在新机器执行）

### 前置要求

以下组件需要安装（Docker 容器化或原生安装均可）：

- MySQL 8.x
- Redis 7.x
- RabbitMQ 3.x
- MinIO
- Nacos 2.x
- Elasticsearch 8.x
- JDK 21
- Maven 3.6+
- Node.js 18+

### 恢复步骤

**1. 启动 MySQL 并恢复数据**

```bash
mysql -uroot -ppassword < sql/shining_full.sql
```

验证：`mysql -uroot -ppassword -e "SELECT COUNT(*) FROM \`shining-music\`.songs;"` → 应为 305

**2. 启动 Nacos**

确保 Nacos 连接已恢复的 MySQL `nacos` 库。启动后 Nacos 从 `config_info` 表加载所有配置。

验证：`curl http://localhost:8848/nacos/` → 控制台可访问

**3. 恢复 Elasticsearch**

```bash
python3 agent-playbooks/migration/restore-es.py sql/elasticsearch_music_search.json
```

脚本行为：等待 ES 可用 → 删除旧索引 → 创建新索引（含 mapping + settings）→ bulk 导入 → 验证文档数。

验证：`curl http://localhost:9200/music_search/_count` → 应为 299

**4. 恢复 MinIO**

将 `shining` bucket 数据放到 MinIO data root 目录，启动 MinIO。注意文件系统级备份包含 `.minio.sys` 元数据和 `xl.meta` 分块格式，不是普通文件目录。

验证：通过浏览器访问 `http://localhost:9090` MinIO Console，确认 `shining` bucket 及文件存在。

**5. 恢复 Redis**

将 `dump.rdb` 放到 Redis 工作目录，启动 Redis 加载。然后清理过期数据：

```bash
redis-cli -n 4 KEYS "jwt:*" | xargs -r redis-cli -n 4 DEL
redis-cli -n 4 KEYS "cache:userInfo:*" | xargs -r redis-cli -n 4 DEL
```

验证：`redis-cli -n 4 DBSIZE` → 应 > 0；`redis-cli -n 4 KEYS "song:vector:*"` → 应有数据

**6. 启动 RabbitMQ**

空实例启动即可。确保 `guest/guest` 账号可用（或在 Nacos 修改凭据）。

**7. 编译并启动 6 个后端服务**

```bash
cd /path/to/Shining-music
mvn clean package -DskipTests

nohup java -jar gateway-service/target/*.jar > logs/gateway.log 2>&1 &
nohup java -jar user-service/target/*.jar > logs/user.log 2>&1 &
nohup java -jar music-service/target/*.jar > logs/music.log 2>&1 &
nohup java -jar community-service/target/*.jar > logs/community.log 2>&1 &
nohup java -jar statistics-service/target/*.jar > logs/statistics.log 2>&1 &
nohup java -jar recommend-service/target/*.jar > logs/recommend.log 2>&1 &
```

如果项目中有 `agent-playbooks/docker/start-all.sh`，可直接使用。

验证：Nacos 控制台 → 服务列表 → 6 个服务均已注册

**8. 启动前端**

```bash
cd shining-ui && npm install && npm run dev
```

验证：`http://localhost:5173` → 首页加载，歌曲列表和封面显示正常

**9. 可选：重建推荐数据**

如果 Redis 中的推荐相关数据没有迁移（或已过期），需要手动触发重建：

```bash
# 重建标签向量（CB 推荐）
curl -X POST http://localhost:8080/api/recommend/tags/rebuild-vectors

# 重建 Item-CF 相似度矩阵
curl -X POST http://localhost:8080/api/recommend/item-cf/rebuild
```

## 验证清单

- [ ] MySQL 歌曲数 305、歌手 46、歌词 383、标签关联 8,279、播放记录 22,852
- [ ] ES 文档数 299
- [ ] MinIO `shining` bucket 文件可访问（音频、封面、头像）
- [ ] Redis 歌单关联、标签向量、Item-CF 矩阵存在
- [ ] Nacos 6 个服务注册
- [ ] 前端首页歌曲列表 + 封面正常
- [ ] 用户登录 + 个人资料 + 歌单 + 播放历史正常
- [ ] CB 推荐和 Item-CF 推荐返回结果
- [ ] 搜索功能正常（中日文关键词）
- [ ] 社区帖子/评论/关注正常

## 从零部署（无迁移数据）

1. 安装全部中间件并启动
2. `sql/` 目录下各服务 DDL 文件创建空表
3. Nacos 手动配置或参考 `data/nacos-config-snapshot/`
4. 通过系统注册用户 → 导入歌曲（`agent-playbooks/music-import/`）→ 打标签（`agent-playbooks/song-tagging/`）→ 触发推荐矩阵重建

## 云服务器注意

| 事项 | 说明 |
|------|------|
| 防火墙 | 开放 5173、8080、9000，其他端口不对外 |
| 域名/IP | 前端 `axios.baseURL` 和 MinIO endpoint 默认 `localhost`，需改为公网地址 |
| HTTPS | 建议 Nginx 反向代理 + SSL |
| MinIO endpoint | Nacos `common.yaml` 的 `minio.endpoint` 需改为公网地址 |
| 内存 | 6 个 Java 服务 + 中间件至少需要 8GB RAM |
