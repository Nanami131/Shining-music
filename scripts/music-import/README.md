# 批量音乐导入 Agent 指南

本文档记录了将外部音乐文件批量导入 Shining-music 系统的完整流程、API 调用方式和已知的坑。  
适用场景：Agent 自主完成"下载 → 建歌手 → 建歌曲 → 传音频 → 传封面 → 传歌词 → 同步 ES"全链路。

---

## 0. 前置准备

### 0.1 系统架构概要

```
客户端 → gateway-service (:8080) → music-service (:8082) / user-service (:8081)
                                         ↓
                              MySQL / MinIO / ES / RabbitMQ (Docker)
```

- **Gateway 路由规则**：`/api/music/**` → music-service（StripPrefix=1，即去掉 `/api`），`/api/user/**` → user-service
- **所有外部 API 调用统一走 gateway**: `http://localhost:8080/api/...`
- **JDK 版本**：必须使用 JDK 21（路径：`~/.local/java/jdk-21.0.10+7/bin/java`）

### 0.2 认证

所有 `/api/music/**` 接口均需 JWT（`/api/user/login` 和 `/api/user/register` 除外）。

```
POST http://localhost:8080/api/user/login
Content-Type: application/json
Body: { "username": "admin", "password": "xxx" }

→ 成功响应:
{
  "code": 200,
  "message": "登录成功",
  "type": "RESULT_SUCCESS",
  "passed": true,
  "data": { "token": "eyJhbGci...", ... }
}
```

后续所有请求携带 `Authorization: Bearer <token>`。

**统一响应格式**（所有 API 返回此结构）：

```json
{
  "code": 200,          // 200=成功, 500=错误
  "message": "描述信息",
  "type": "RESULT_SUCCESS",
  "passed": true,       // 关键字段：判断操作是否成功
  "data": { ... }       // 业务数据，可能为 null
}
```

### 0.3 服务确认

确保以下服务正在运行，否则先启动：

| 服务 | 端口 | 检查方式 |
|------|------|----------|
| gateway-service | 8080 | `curl localhost:8080/api/music/test` |
| music-service | 8082 | 日志或 Nacos 注册 |
| user-service | 8081 | Nacos 注册 |
| MySQL | 3306 | `docker ps \| grep shining-mysql` |
| MinIO | 9000 | `curl localhost:9000/minio/health/live` |
| Elasticsearch | 9200 | `curl localhost:9200` |
| RabbitMQ | 5672 | `docker ps \| grep shining-rabbitmq` |
| Nacos | 8848 | `curl localhost:8848/nacos` |

**启动脚本**: `scripts/docker/start-all.sh`

如果启动失败的常见原因：
- **Address already in use**: 用 `lsof -i :端口` 找到旧进程并 kill
- **music-service 启动报 UnsupportedClassVersionError**: 没用 JDK 21
- **common 模块找不到**: 先在项目根目录执行 `cd common && mvn install -DskipTests`
- **ES 启动报 AccessDeniedException**: `chmod 777 docker-data/elasticsearch/`

### 0.4 数据库信息

| 数据库 | 说明 |
|--------|------|
| `shining-music` | 歌曲、歌手、歌词、歌单 |
| `shining-user` | 用户 |
| `shining-community` | 社区 |
| `shining-statistics` | 统计 |

连接方式：`docker exec shining-mysql mysql -u root -ppassword "shining-music"`  
（密码在 Nacos `secret.yaml` 中配置，默认 `password`）

关键表结构：
- `songs`: id, title, artist_id, album_id, file_url, cover_url, status
- `singers`: id, name, sex, profile, genre, country, status, avatar_url
- `lyrics`: id, song_id, language_msg, content

### 0.5 依赖工具

| 工具 | 用途 | 安装方式 |
|------|------|----------|
| Python 3.8+ | 脚本运行 | 系统自带或 `apt install python3` |
| requests | HTTP 请求 | `pip install requests` |
| yt-dlp | Bilibili 视频下载 | `pip install yt-dlp` |
| ffmpeg | 音频提取/转码 | `apt install ffmpeg` 或通过 imageio-ffmpeg |
| mutagen | MP3 元数据读取 | `pip install mutagen` |

---

## A. 获取音频文件

系统提供两套下载脚本，按优先级使用：

### A.1 Bilibili 下载（推荐）— `scripts/music-import/bili-music-download.py`

从 Bilibili 搜索歌手 MV 视频，提取高质量音频。

```bash
# 下载周杰伦的 20 首歌
python3 scripts/music-import/bili-music-download.py "周杰伦" --count 20

# 下载自定义歌曲列表
python3 scripts/music-import/bili-music-download.py "周杰伦" --songs my_songs.txt --count 30

# 下载其他歌手（自动通过酷我发现歌曲列表）
python3 scripts/music-import/bili-music-download.py "五月天" --count 15
```

**输出目录**: `scripts/download/{歌手名}_bili/`  
**文件命名**: `歌手名 - 歌曲名.mp3`

#### 工作原理

1. 使用内置热门歌曲列表（周杰伦）或通过酷我 API 自动发现歌曲
2. 对每首歌在 Bilibili 搜索多种关键词组合：`{artist} {song} MV`、`{artist} {song} 官方`
3. 对每个搜索结果评分（标题匹配 +50、歌手匹配 +20、官方/高清 +15、播放量加成、时长合理性）
4. 自动过滤：现场版、翻唱、教学、伴奏、鬼畜、合唱等
5. 选取最高分视频，用 yt-dlp 下载后 ffmpeg 提取 MP3

#### 已知问题

| 问题 | 解决方案 |
|------|----------|
| Bilibili 412 限流 | 脚本内置重试（3 次，间隔递增），每首歌之间固定 2 秒间隔 |
| 下载结果不佳（翻唱/不完整） | 调整 `EXCLUDE_TITLE_RE` 正则、提高 `score` 阈值（默认 30） |
| 非周杰伦歌手没有内置歌曲列表 | 传 `--songs` 参数指定文件，或让脚本自动走酷我发现 |
| yt-dlp 版本过旧导致下载失败 | `pip install --upgrade yt-dlp` |
| 通用歌名（如"青春""宿敌"）搜索匹配错误 | 搜索结果可能匹配到无关视频。Agent 必须验证视频标题确实包含目标歌曲名和歌手名 |
| 下载了 MV 版而非录音室版 | MV 视频通常有前后空白段（5:30 vs 4:28）。优先搜索"无损音质"或"完整版"而非"MV"关键词 |
| 歌曲全名与简称不一致 | 如"天龙八部之宿敌"简称"宿敌"。导入时使用完整名称，搜索时也要用完整名称 |

### A.2 酷我下载（备选）— `scripts/music-import/music-download.py`

从酷我音乐搜索并直接下载 MP3。适用于 Bilibili 搜不到的冷门歌曲。

```bash
python3 scripts/music-import/music-download.py "周杰伦" --studio-only --pages 3
```

#### 已知限制

- 部分歌曲只有 30 秒试听片段（付费限制）
- 需要 `--studio-only` 过滤现场版/合唱版
- 音质不如 Bilibili MV 提取

### A.3 音频文件质量检查

下载完成后需要检查：

```python
from mutagen.mp3 import MP3
audio = MP3(filepath)
duration = audio.info.length
bitrate = audio.info.bitrate // 1000
filesize = os.path.getsize(filepath)

if duration < 60:
    print(f"WARNING: {filename} 时长仅 {duration:.0f}s，可能是试听片段")
if bitrate < 128:
    print(f"WARNING: {filename} 比特率仅 {bitrate}kbps，音质较低")
```

---

## B. 获取歌词

### B.1 NetEase 歌词获取（推荐）

NetEase 是最可靠的 LRC 歌词来源，大多数中文歌曲都有带时间戳的歌词和翻译。

#### 步骤 1：获取 NetEase song_id

```python
r = requests.get("https://music.163.com/api/search/get",
    params={"s": f"{artist} {title}", "type": 1, "limit": 5},
    headers={"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"})
songs = r.json().get("result", {}).get("songs", [])
# 遍历 songs，匹配 artists[].name 和 name 字段
```

**注意**：搜索结果可能不返回原版歌曲（被 remix/cover 淹没），需要验证歌手名匹配。

#### 步骤 2：获取歌词内容

```python
r = requests.get(f"https://music.163.com/api/song/lyric?id={netease_id}&lv=1&kv=1&tv=-1",
    headers={"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"})
data = r.json()

original_lrc = data.get("lrc", {}).get("lyric", "")       # 原文歌词
translation  = data.get("tlyric", {}).get("lyric", "")     # 翻译歌词（如有）
```

#### 步骤 3：歌词格式验证

下载后**必须**检查歌词质量，因为以下格式不可用：

```python
# 检测 QRC/KRC 逐字格式（会导致中文乱码）
if "<" in lrc_content and ">" in lrc_content:
    print("BAD: QRC/KRC karaoke format detected, discard and refetch")

# 检测乱码
if lrc_content.count("?") > len(lrc_content) * 0.3:
    print("BAD: Garbled content detected")

# 检测有效行数
import re
valid_lines = [l for l in lrc_content.split('\n')
    if re.match(r'\[\d{2}:\d{2}', l)
    and not any(kw in l.lower() for kw in ['作词', '作曲', '编曲', '制作人', 'op ', 'isrc', 'ar:', 'ti:', 'al:'])]
if len(valid_lines) < 10:
    print(f"BAD: Only {len(valid_lines)} valid lines")
```

### B.2 lrclib.net（备选）

适用于 NetEase 上找不到歌词的情况（尤其是外文歌曲）。

```python
r = requests.get("https://lrclib.net/api/get",
    params={"artist_name": artist, "track_name": title},
    headers={"User-Agent": "ShiningMusic/1.0"})
if r.status_code == 200:
    data = r.json()
    synced_lrc = data.get("syncedLyrics", "")   # 带时间戳
    plain_lrc  = data.get("plainLyrics", "")     # 纯文本（无时间戳，不推荐）
```

### B.3 歌词语言标识规范

上传歌词时 `msg` 参数的语言标识：

| 语言 | msg 值 | 说明 |
|------|--------|------|
| 中文 | `zh` | 包括简体/繁体 |
| 日语 | `ja` | |
| 英语 | `en` | |
| 韩语 | `ko` | |
| 翻译（中→其他） | 目标语言代码 | 如日文歌的中文翻译用 `zh` |

---

## 1. 创建/查找歌手

### API

```
POST /api/music/singer
Body: { "name": "周杰伦", "sex": 0, "status": 1 }
→ data.id
```

### 已知问题与注意事项

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| `Column 'status' cannot be null` | 后端 createSinger 曾不设默认值 | **已修复**：status 默认 1，sex 默认 0。但建议仍显式传值 |
| 歌手重复创建 | 同一歌手可能有多种名称（真名/艺名/外文名） | 创建前先 `GET /api/music/singers` 搜索已有列表 |
| 歌手信息不全 | 仅传了名字，没有性别/简介/头像 | 必须调研歌手资料后完整填写 |

### 更新歌手详细信息

```
POST /api/music/update-profile
Body: { "id": <singerId>, "sex": 0, "profile": "...", "genre": "流行", "country": "中国" }
```

### 上传歌手头像

```
POST /api/music/update-avatar
Form: id=<singerId>, avatarFile=<file>, md5=<file_md5>
```

**头像来源**: NetEase 歌手详情页 → `artist.img1v1Url` 或 `artist.picUrl`。

---

## 2. 创建歌曲记录

### API

```
POST /api/music/song
Body: { "title": "七里香", "artistId": <singerId>, "status": 1 }
→ data.id （新创建的 song_id）
```

### 注意

- `title` 从文件名提取：去掉扩展名，按 ` - ` 分割取后半部分
- `status: 1` 表示正常可用
- 返回的 `song_id` 后续所有操作都会用到，务必保存

---

## 3. 上传音频文件

### API

```
POST /api/music/upload
Form: id=<songId>, avatarFile=<audio_file>, md5=<file_md5>
```

### 注意

- 参数名是 `avatarFile`（历史命名，实际传音频文件）
- `md5` 是音频文件的 MD5 校验值
- Content-Type 用 `audio/mpeg`

---

## 4. 上传歌曲封面（重点难点）

### API

```
POST /api/music/cover/song
Form: id=<songId>, avatarFile=<image_file>, md5=<file_md5>
```

### 封面获取策略（按优先级排序）

**核心原则：每首歌的封面必须是独一无二的图片，绝不能多首歌共用同一张图。**

| 优先级 | 来源 | 方法 | 独特性 |
|--------|------|------|--------|
| 1 | **Bilibili MV 缩略图** | 搜索 `{artist} {title} MV` → 取视频 `pic` 字段 | ✅ 每个 MV 视频封面天然不同 |
| 2 | **NetEase MV 封面** | `GET /api/search/get?type=1004&s={artist}+{title}` → `mvs[].cover` | ✅ MV 封面独特 |
| 3 | **QQ Music MV 封面** | `search_type=12` 搜 MV → `mv_vid` → `T015R300x300M000{vid}.jpg` | ✅ MV 封面独特 |
| ❌ | ~~专辑封面~~ | 无论来自 NetEase/QQ Music/Apple Music | ❌ 同专辑歌曲会共用 |

### 已踩的坑

| 错误做法 | 后果 | 正确做法 |
|----------|------|----------|
| 用 NetEase 搜索 API 的 `album.picUrl` | 同专辑歌曲共享同一封面，用户不满意 | 用 MV 封面 |
| 用 QQ Music 的 `album.mid` 构造封面 URL | 同上 | 搜 MV（type=12）而非歌曲（type=0） |
| 不检查封面是否重复 | 上传了重复图片浑然不知 | 维护 `seen_urls` 集合做去重检测 |
| Bilibili API 连续请求 | 触发 412 限流 | 每次请求间隔 2-3 秒，412 时指数退避 |
| 封面图片太小（< 5KB） | 可能是占位图/404 页面 | 检查下载文件大小，最低 5KB |

### Bilibili 搜索防限流模板

```python
def search_bilibili(keyword, retries=3):
    for attempt in range(retries):
        r = requests.get("https://api.bilibili.com/x/web-interface/search/type",
            params={"search_type": "video", "keyword": keyword, "page": "1"},
            headers={"User-Agent": "Mozilla/5.0", "Referer": "https://www.bilibili.com/"},
            timeout=15)
        if r.status_code == 412:
            time.sleep((attempt + 1) * 5)  # 5s, 10s, 15s
            continue
        return r.json().get("data", {}).get("result", [])
    return []
```

### 验证封面独特性

上传完成后，必须检查所有封面的文件大小是否都不同：

```python
for song_id in all_ids:
    r = requests.head(cover_url)
    size = int(r.headers.get('Content-Length', 0))
    # 记录 {song_id: size}，检查 size 集合长度 == 歌曲数
```

---

## 5. 上传歌词及验证（重点难点）

> 歌词的**获取方法**见 [Section B](#b-获取歌词)，本节聚焦**上传 API 和质量验证**。

### API

```
POST /api/music/lyrics/{songId}
Form: lyricsFile=<lrc_file>, msg=<language_code>
```

- `msg` 参数是语言标识（如 `zh`、`ja`、`en`）
- 同一首歌同一语言重复上传会自动覆盖旧歌词

### 歌词获取来源

| 来源 | 方法 | 质量 |
|------|------|------|
| **NetEase（推荐）** | `GET /api/song/lyric?id={netease_id}&lv=1&kv=1&tv=-1` → `lrc.lyric` + `tlyric.lyric` | ✅ 标准 LRC，有翻译 |
| lrclib.net | `GET /api/get?artist_name={}&track_name={}&album_name={}` | 一般，可能缺少 |
| QQ Music | 需要特殊解密 | 不推荐 |

### 获取 NetEase song_id

```python
r = requests.get("https://music.163.com/api/search/get",
    params={"s": f"{artist} {title}", "type": 1, "limit": 5},
    headers={"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"})
# 从 result.songs 中匹配 artist + title
```

### 已踩的坑

| 错误做法 | 后果 | 正确做法 |
|----------|------|----------|
| 直接使用搜索结果的歌词而不检查 | 部分歌词是 QRC/KRC 逐字格式（`<timestamp>` 标签），中文变成 `?` | 下载后检查是否包含 `<` 标签和 `?` 乱码 |
| 歌词上传后不验证 | 3/20 首歌只有 3-4 行废数据 | 上传后必须审计：检查有效行数（排除元数据行） |
| 传参用 `lang` | 400 Bad Request | 参数名是 `msg` |
| 不获取翻译歌词 | 用户希望双语对照 | `tlyric.lyric` 是翻译，也要上传（msg 设为对应语言） |

### 歌词质量审计标准

```python
lines = content.split('\n')
valid = [l for l in lines if re.match(r'\[\d{2}:\d{2}', l)
         and not any(kw in l for kw in ['作词', '作曲', '编曲', '制作人', 'ar:', 'ti:', 'al:'])]
if len(valid) < 10:
    print(f"BAD: {title} 只有 {len(valid)} 行有效歌词")
```

### 系统支持的 LRC 格式

后端 `LrcParser.java` 支持：
- 标准格式：`[mm:ss.xx]歌词文本`
- 无毫秒格式：`[mm:ss]歌词文本`
- 扩展多语言格式：`[mm:ss.xx][lang]文本`

前端 `lrcParser.js` 额外支持：
- 空行/间奏标记：仅有时间戳无文本 → 显示为间奏
- 元数据过滤：自动忽略作词/作曲等元信息行
- 多语言合并：`mergeMultiLang(sources)` 合并不同语言记录

---

## 6. 同步 Elasticsearch

### API

```
POST /api/music/search/sync
→ {"message": "全量同步完成"}
```

### 注意

- 每次批量导入完成后**必须**调用此接口
- 否则新歌曲在搜索中不可见
- 同步包括歌曲标题、歌手名、歌词内容

---

## 7. 完整导入流程 Checklist

```
□ Step 0: 环境准备
  □ 所有 Docker 容器运行中（MySQL, MinIO, ES, RabbitMQ, Redis, Nacos）
  □ 后端服务运行中（gateway, music-service, user-service）
  □ 获取 JWT token: POST /api/user/login → data.token

□ Step A: 下载音频文件
  □ 运行 bili-music-download.py（或准备本地音频文件）
  □ 验证：每个 MP3 时长 > 60s、比特率 >= 128kbps、文件完整
  □ 文件名格式：「歌手名 - 歌曲名.mp3」

□ Step 1: 创建/查找歌手 → 得到 singer_id
  □ 先 GET /api/music/singers 检查是否已存在（注意艺名/别名）
  □ POST /api/music/singer 创建（显式传 name, sex, status）
  □ POST /api/music/update-profile 补全简介、风格、国籍
  □ POST /api/music/update-avatar 上传头像（来源：NetEase artist.picUrl）

□ Step 2: 遍历音频文件（每首歌）
  □ 2a: POST /api/music/song 创建歌曲记录 → 保存 song_id
  □ 2b: POST /api/music/upload 上传音频（参数名: avatarFile）
  □ 2c: 搜索并上传 MV 封面
    □ 优先 Bilibili MV 缩略图（搜 "{artist} {title} MV"）
    □ 备选 NetEase MV 封面（type=1004 搜索）
    □ 验证封面独特性（不能与已上传的重复）
    □ 验证封面文件大小 > 5KB
    □ POST /api/music/cover/song 上传
  □ 2d: 获取歌词
    □ NetEase 搜索 song_id → GET /api/song/lyric → 下载 LRC
    □ 检查不是 QRC/KRC 格式（无 <timestamp> 标签）
    □ 检查有效行数 >= 10
    □ POST /api/music/lyrics/{songId} 上传（参数: lyricsFile + msg=zh）
    □ 如有翻译歌词（tlyric），也上传（msg=对应语言）
  □ 每完成一首歌，追加到 import_results.json

□ Step 3: POST /api/music/search/sync 全量同步 ES

□ Step 4: 最终验证
  □ DB 验证：SELECT count(*) FROM songs WHERE artist_id = <singer_id>
  □ 封面验证：所有 cover_url 不同，HEAD 请求检查 Content-Length 各不相同
  □ 歌词验证：每首歌 lyrics 表有记录，content 有效行数 >= 10
  □ ES 验证：GET /api/music/search?keyword=<歌名> 能命中
  □ 前端验证：歌手详情页能看到所有歌曲，播放全部功能正常
```

### import_results.json 格式

每首歌完成后保存进度，便于故障恢复：

```json
[
  {
    "title": "七里香",
    "file": "周杰伦 - 七里香.mp3",
    "song_id": 50,
    "audio": true,
    "cover": true,
    "lyrics": true,
    "netease_id": 185821,
    "cover_source": "bilibili"
  }
]
```

---

## 8. 错误处理原则

1. **不要静默跳过失败**：每一步失败都要记录到结果 JSON，便于后续修复
2. **分阶段保存进度**：每完成一首歌就将 `{song_id, title, audio, cover, lyrics}` 追加到 `import_results.json`
3. **封面和歌词问题必须单独修复脚本**：批量导入时封面/歌词最容易出错，失败的部分要能单独重跑
4. **Agent 必须参与异常处理**：纯脚本无法处理所有边界情况（API 限流、歌词格式异常、封面重复等），Agent 需要在脚本执行过程中监控输出并介入

---

## 9. 外部 API 速率限制

| 平台 | 限制 | 建议间隔 |
|------|------|----------|
| NetEase Music API | 较宽松 | 0.3-0.5s |
| Bilibili Search API | 严格，易触发 412 | 2-3s，412 后指数退避 |
| QQ Music API | 中等 | 0.5-1s |
| 本地 Gateway API | 无限制 | 0.1s |
