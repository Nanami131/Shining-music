# Shining Music 数据迁移指南

## 概述

本项目的运行时数据分布在 5 个组件中。代码通过 git 管理，此文档只涉及数据迁移。

迁移包 `shining-data-export.tar.gz` 解压后结构：

```
shining-data-export/
├── mysql/
│   └── all_databases.sql
├── redis/
│   └── redis_db4.json
├── minio/
│   ├── song/           # 音频 + 封面
│   ├── singer/avator/  # 歌手头像
│   ├── playlist/cover/
│   ├── user/avator/
│   ├── community/images/
│   └── video/          # file/ + cover/
└── es/
    └── music_search.json
```

## 数据组件说明

### MySQL

| 库 | 职责 |
|----|------|
| `nacos` | 微服务配置（数据库连接、JWT secret、MinIO 凭据、网关路由）|
| `shining-user` | 用户账号 |
| `shining-music` | 歌曲、歌手、歌词、歌单、标签、视频、用户偏好 |
| `shining-community` | 社区帖子、评论、点赞、关注 |
| `shining-statistics` | 播放记录、用户事件、用户画像 |

### MinIO

`shining` bucket 内全部对象文件。恢复时必须保持原路径结构。

### Redis（DB 4）

只导出不可重建的键：

| 模式 | 说明 |
|------|------|
| `user:preference:*` | 用户偏好向量 |
| `user:songplay:*` | 播放计数 |
| `playlist:*` | 歌单缓存 |
| `user:playback_state:*` | 播放状态 |

以下键可通过 API 重建，不包含在导出中：`song:vector:*`、`itemcf:*`、`jwt:*`、`cache:*`、`recommend:daily:*`

### Elasticsearch

`music_search` 索引。可选恢复——也可以恢复 MySQL 后通过 API 从数据库重建。

## 恢复步骤

### 前提：中间件安装

需要的组件及默认端口：

| 组件 | 默认端口 |
|------|----------|
| MySQL 8 | 3306 |
| Redis 7 | 6379 |
| RabbitMQ | 5672 / 15672(管理) |
| MinIO | 9000(API) / 9090(控制台) |
| Nacos 2 | 8848 |
| Elasticsearch 8 + IK + kuromoji | 9200 |
| JDK 17+ | - |
| Node.js 18+ | - |
| Maven | - |

安装方式不限（Docker / 原生），端口可自定义但需同步修改 Nacos 配置。

### 1. 恢复 MySQL

```bash
mysql -u root -p < mysql/all_databases.sql
```

验证（数字以实际为准）：

```sql
SELECT 'nacos' db, COUNT(*) FROM nacos.config_info
UNION ALL SELECT 'songs', COUNT(*) FROM `shining-music`.songs
UNION ALL SELECT 'users', COUNT(*) FROM `shining-user`.users
UNION ALL SELECT 'play_records', COUNT(*) FROM `shining-statistics`.user_song_play_record;
```

### 2. 修改 Nacos 配置

启动 Nacos → 登录控制台 → namespace=`Shining`, group=`Shining` → 编辑 `common.yaml`：

需要改的地址项（改为目标机实际地址）：
- `spring.datasource.url`
- `spring.redis.host`
- `spring.rabbitmq.host`
- `minio.endpoint`

如果 MinIO endpoint 变了，还需批量替换数据库中存储的文件 URL：

```sql
-- 将 OLD_ENDPOINT 替换为实际旧地址，NEW_ENDPOINT 替换为新地址
SET @OLD = 'OLD_ENDPOINT';
SET @NEW = 'NEW_ENDPOINT';

UPDATE `shining-music`.songs SET file_url = REPLACE(file_url, @OLD, @NEW), cover_url = REPLACE(cover_url, @OLD, @NEW);
UPDATE `shining-music`.singers SET avatar_url = REPLACE(avatar_url, @OLD, @NEW);
UPDATE `shining-music`.lyrics SET file_url = REPLACE(file_url, @OLD, @NEW);
UPDATE `shining-music`.videos SET file_url = REPLACE(file_url, @OLD, @NEW), cover_url = REPLACE(cover_url, @OLD, @NEW);
UPDATE `shining-music`.playlists SET cover_url = REPLACE(cover_url, @OLD, @NEW);
UPDATE `shining-user`.users SET avatar_url = REPLACE(avatar_url, @OLD, @NEW);
UPDATE `shining-community`.posts SET image_urls = REPLACE(image_urls, @OLD, @NEW);
```

### 3. 恢复 MinIO

创建 bucket 并上传，保持路径结构：

```bash
# mc 命令行方式
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/shining --ignore-existing
mc cp --recursive minio/ local/shining/
```

验证：`mc ls --recursive local/shining/ | wc -l` 应与导出时文件数一致。

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

验证：`redis-cli -n 4 DBSIZE` 应 >= 导出的键数。

### 5. 恢复 Elasticsearch（可选）

方式 A：从 JSON 恢复（先用文件中的 mapping 创建索引，再 bulk 导入）。

方式 B：跳过，等后端启动后调 API 从 MySQL 重建：

```
POST http://localhost:8080/api/music/search/sync
```

### 6. 构建并启动后端

```bash
cd common && mvn install -DskipTests
```

然后依次启动各服务（顺序建议：gateway → user-service → music-service → community-service → statistics-service → recommend-service）：

```bash
cd <service-dir> && mvn package -DskipTests && java -jar target/*.jar
```

### 7. 重建可重建数据

```
POST http://localhost:8080/api/recommend/tags/rebuild-vectors
POST http://localhost:8080/api/recommend/item-cf/rebuild
POST http://localhost:8080/api/music/search/sync   # 如果第 5 步跳过
```

### 8. 启动前端

```bash
cd shining-ui && npm install && npm run dev
```

### 9. 端到端验证

- 浏览器登录 → 查看歌曲列表
- 播放歌曲 → 确认音频正常
- 歌手页面 → 确认头像显示
- MV 播放 → 确认视频正常
- 推荐页面 → 确认有推荐结果
- 年度报告 → 确认有播放数据

## 重新导出

如果需要更新迁移包，在源机器上执行：

```bash
# MySQL
docker exec <mysql-container> mysqldump -u root -p<password> \
  --databases nacos shining-user shining-music shining-community shining-statistics \
  --add-drop-database --routines --triggers --single-transaction \
  > shining-data-export/mysql/all_databases.sql

# MinIO
docker exec <minio-container> mc cp --recursive local/shining/ /tmp/export/
docker cp <minio-container>:/tmp/export/ shining-data-export/minio/

# Redis（参考上面的 Python 脚本反向操作：遍历 DB4 中需要保留的键模式并序列化为 JSON）

# ES
curl -s 'localhost:9200/music_search/_search?size=10000' > shining-data-export/es/music_search.json

# 打包
tar cf - shining-data-export/ | gzip -1 > shining-data-export.tar.gz
```
