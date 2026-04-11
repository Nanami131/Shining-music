# Shining Music 数据迁移包

## 这是什么

`shining-data-export.tar.gz` 是 Shining Music 项目**全部运行时数据**的打包。代码用 git 管理，这个包只包含数据。

解压后目录结构：

```
shining-data-export/
├── mysql/
│   └── all_databases.sql       # 5 个库的完整 dump
├── redis/
│   └── redis_db4.json          # 不可重建的 Redis 键
├── minio/
│   ├── song/                   # 音频文件 + 封面
│   │   ├── *.mp3 / *.flac
│   │   └── cover/
│   ├── singer/avator/          # 歌手头像
│   ├── playlist/cover/         # 歌单封面
│   ├── user/avator/            # 用户头像
│   ├── community/images/       # 社区图片
│   └── video/
│       ├── file/               # MV 视频
│       └── cover/              # 视频封面
└── es/
    └── music_search.json       # ES 索引（可选恢复，也可从 MySQL 重建）
```

## 数据清单

### MySQL（`mysql/all_databases.sql`，~4 MB）

| 库 | 关键表 | 说明 |
|----|--------|------|
| `nacos` | `config_info` | **最关键**。所有微服务的配置：数据库连接、JWT secret、MinIO 凭据、网关路由。丢了等于白搭 |
| `shining-user` | `users`, `password_history` | 用户账号和密码历史 |
| `shining-music` | `songs`, `singers`, `lyrics`, `playlists`, `song_tags`, `tag_definitions`, `videos`, `user_preferences` | 歌曲库核心数据 |
| `shining-community` | `posts`, `post_comments`, `post_likes`, `user_follows` | 社区数据 |
| `shining-statistics` | `user_song_play_record`, `user_event_log`, `user_profile` | 播放记录和用户画像 |

### MinIO（`minio/` 目录，~2.7 GB）

788 个文件。恢复后必须放入名为 `shining` 的 bucket 中，保持原路径结构。

### Redis（`redis/redis_db4.json`，~12 KB）

| Key 模式 | 数量 | 说明 |
|----------|------|------|
| `user:preference:*` | 3 | 用户偏好向量，不可重建 |
| `user:songplay:*` | 92 | 用户播放计数，不可重建 |
| `playlist:*` | 5 | 歌单缓存 |
| `user:playback_state:*` | 4 | 用户播放状态 |

不在此文件中的 Redis 键（`song:vector:*`、`itemcf:*`、`jwt:*`、`cache:*`、`recommend:daily:*`）可通过 API 重建，不需要迁移。

### Elasticsearch（`es/music_search.json`，~1 MB，可选）

`music_search` 索引，包含 mapping 和全部文档。也可以跳过此文件，恢复 MySQL 后通过 `POST /api/music/search/sync` 从数据库重建。

## 新设备恢复目标

在 Windows 机器上恢复后，应达到以下效果：

1. **前端可正常访问**，登录后能看到所有歌曲、歌手、歌单
2. **音乐可播放**，封面图片正常显示
3. **MV 视频可播放**
4. **社区帖子可浏览**
5. **推荐功能可用**（需恢复后执行向量重建 API）
6. **年度报告页面可用**（依赖播放记录数据）

## 恢复步骤（给 Agent 的操作指引）

### 前提

新设备需要安装：MySQL 8、Redis 7、Elasticsearch 8（带 IK 分词插件）、MinIO、RabbitMQ、Nacos 2、JDK 17、Node.js 18+、Maven。安装方式不限（Docker / 原生 / 其他），只要端口能通。

### 默认端口

| MySQL | Redis | RabbitMQ | MinIO API | MinIO Console | Nacos | ES | Gateway | Frontend |
|-------|-------|----------|-----------|---------------|-------|----|---------|----------|
| 3306 | 6379 | 5672 | 9000 | 9090 | 8848 | 9200 | 8080 | 5173 |

### 默认凭据

MySQL `root/password`，MinIO `minioadmin/minioadmin`，RabbitMQ `guest/guest`，Redis 无密码。

### 1. 恢复 MySQL

```bash
mysql -u root -p < mysql/all_databases.sql
```

恢复后验证：

```sql
SELECT 'nacos' db, COUNT(*) FROM nacos.config_info
UNION ALL SELECT 'songs', COUNT(*) FROM `shining-music`.songs
UNION ALL SELECT 'users', COUNT(*) FROM `shining-user`.users;
```

### 2. 修改 Nacos 配置中的地址

启动 Nacos 后，登录 `http://localhost:8848/nacos`（nacos/nacos），进入 namespace=Shining, group=Shining。

必须修改 `common.yaml` 中的以下地址，改为新设备的实际地址：
- `spring.datasource.url` 中的 MySQL 主机
- `spring.redis.host`
- `spring.rabbitmq.host`
- `minio.endpoint`

如果 MinIO endpoint 变了（比如从 `http://旧IP:9000` 变成 `http://localhost:9000`），还需要批量替换数据库中所有文件 URL：

```sql
UPDATE `shining-music`.songs SET file_url = REPLACE(file_url, '旧地址', '新地址'), cover_url = REPLACE(cover_url, '旧地址', '新地址');
UPDATE `shining-music`.singers SET avatar_url = REPLACE(avatar_url, '旧地址', '新地址');
UPDATE `shining-music`.lyrics SET file_url = REPLACE(file_url, '旧地址', '新地址');
UPDATE `shining-music`.videos SET file_url = REPLACE(file_url, '旧地址', '新地址'), cover_url = REPLACE(cover_url, '旧地址', '新地址');
UPDATE `shining-music`.playlists SET cover_url = REPLACE(cover_url, '旧地址', '新地址');
UPDATE `shining-user`.users SET avatar_url = REPLACE(avatar_url, '旧地址', '新地址');
UPDATE `shining-community`.posts SET image_urls = REPLACE(image_urls, '旧地址', '新地址');
```

### 3. 恢复 MinIO

创建 `shining` bucket，然后把 `minio/` 目录下所有文件上传进去，保持路径结构。

可以用 `mc` 命令行工具：

```bash
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/shining --ignore-existing
mc cp --recursive minio/ local/shining/
```

也可以用 Python boto3 脚本遍历目录上传，方式不限。

恢复后验证：确认 `mc ls local/shining/song/` 能看到文件。

### 4. 恢复 Redis

```python
import json, redis

r = redis.Redis(host='localhost', port=6379, db=4)
data = json.load(open('redis/redis_db4.json'))

for key, info in data.items():
    if info['type'] == 'string':
        r.set(key, info['value'])
    elif info['type'] == 'hash':
        r.hset(key, mapping=info['value'])
    elif info['type'] == 'list':
        for v in info['value']:
            r.rpush(key, v)
    elif info['type'] == 'set':
        for v in info['value']:
            r.sadd(key, v)
    elif info['type'] == 'zset':
        for member, score in info['value']:
            r.zadd(key, {member: score})

print(f'Restored {len(data)} keys')
```

验证：`redis-cli -n 4 DBSIZE` 应返回 >= 104。

### 5. 恢复 Elasticsearch（可选）

可以从导出的 JSON 恢复：先创建索引（用 `music_search.json` 中的 mapping 和 settings），再 bulk 导入文档。

也可以跳过，等后端服务全部启动后执行：

```
POST http://localhost:8080/api/music/search/sync
```

从 MySQL 自动重建索引。

### 6. 构建并启动后端服务

```bash
cd common && mvn install -DskipTests
cd ../gateway && mvn package -DskipTests && java -jar target/*.jar &
cd ../user-service && mvn package -DskipTests && java -jar target/*.jar &
cd ../music-service && mvn package -DskipTests && java -jar target/*.jar &
cd ../community-service && mvn package -DskipTests && java -jar target/*.jar &
cd ../statistics-service && mvn package -DskipTests && java -jar target/*.jar &
cd ../recommend-service && mvn package -DskipTests && java -jar target/*.jar &
```

### 7. 重建可重建的数据

```
POST http://localhost:8080/api/recommend/tags/rebuild-vectors
POST http://localhost:8080/api/recommend/item-cf/rebuild
POST http://localhost:8080/api/music/search/sync  (如果第 5 步跳过了)
```

### 8. 启动前端

```bash
cd shining-ui && npm install && npm run dev
```

### 9. 端到端验证

- 浏览器访问 `http://localhost:5173`，登录
- 播放任意一首歌，确认音频正常
- 查看歌手页面，确认头像显示
- 播放一个 MV，确认视频正常
- 查看推荐页面，确认有推荐结果
- 查看年度报告，确认有数据
