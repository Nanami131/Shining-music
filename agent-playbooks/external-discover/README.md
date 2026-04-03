# 外部歌曲发现与推荐导入 Agent 指南

本文档描述从外部平台发现歌曲、计算与用户偏好的匹配度、择优导入系统并完成标签打标的完整流程。

**核心链路**：发现候选歌曲 → 计算粗略匹配度 → 下载音频 → 导入系统 → 完整打标 → 推荐验证

---

## ⚠️ 强制依赖文档（必须认真遵守）

本流程涉及歌曲导入和标签打标，**必须**严格遵守以下两个文档的全部规范：

| 文档 | 路径 | 涉及步骤 |
|------|------|----------|
| **批量音乐导入指南** | [`agent-playbooks/music-import/README.md`](../music-import/README.md) | 创建歌手、创建歌曲、上传音频/封面/歌词、同步 ES |
| **歌曲标签打标指南** | [`agent-playbooks/song-tagging/README.md`](../song-tagging/README.md) | 28 维标签打标（Language/Source/Mood/Vocal/Audio/Era） |

**违反后果**：上一轮实践中因未严格遵守导入和打标规范，产生了以下严重问题：
- 10 首歌中 Source（6维）和 Era（1维）标签完全缺失（28维只写了21维）
- 语种标签误判（日文歌标成英文）
- 音频下载了音游视频、舞蹈翻跳、英文版（3/10 首需要重新下载）
- 歌手重复创建（YOASOBI 有两个记录）
- genre/release_year/duration 字段全部为 NULL
- ES 未同步、歌手无头像

---

## 0. 前置条件

### 0.1 系统服务

| 服务 | 端口 | 作用 |
|------|------|------|
| gateway-service | 8080 | API 网关 |
| music-service | 8082 | 歌曲/歌手 CRUD |
| recommend-service | 8085 | 标签管理、向量重建、推荐 |
| MySQL | 3306 | 数据存储 |
| MinIO | 9000 | 音频/封面对象存储 |
| Elasticsearch | 9200 | 全文搜索 |
| Redis | 6379 | 用户偏好向量、推荐缓存 |

### 0.2 依赖工具

```bash
pip install requests yt-dlp mutagen jieba essentia
```

### 0.3 认证

```python
import requests
r = requests.post("http://localhost:8080/api/user/login",
    json={"username": "1", "password": "1"})
TOKEN = r.json()['data']['token']
HEADERS = {"Authorization": f"Bearer {TOKEN}"}
```

---

## 1. 获取用户偏好向量

从 recommend-service 获取目标用户的 28 维偏好向量。

```bash
curl -s "http://localhost:8085/recommend/preference?userId=27"
```

偏好向量维度对应关系：

| 维度范围 | 类别 | 具体含义 |
|----------|------|----------|
| 0-3 | Language | ja, zh, en, instrumental |
| 4-9 | Source | anime, game, vocaloid, original, cover, idol |
| 10-17 | Mood | valence, arousal, dominance, joy, anger, sadness, fear, disgust |
| 18-20 | Vocal | male, female, synth |
| 21-26 | Audio | tempo, energy, danceability, acousticness, valence, speechiness |
| 27 | Era | era_normalized |

通过偏好向量分析用户倾向（例如 dim[0]=0.9 表示强烈偏好日语歌曲）。

---

## 2. 发现候选歌曲

### 2.1 发现源选择

| 来源 | 适用场景 | 不适用 |
|------|----------|--------|
| **Bilibili** | 日文歌、动漫歌、热门流行 | 冷门歌曲 |
| **NetEase** | 所有语种、曲库最全 | 部分版权限制 |
| **酷我** | 中文歌 | **⚠️ 日文/外文歌完全不可用**（搜索结果大量混入翻唱/KTV/错误匹配） |

### 2.2 动态发现策略

系统不依赖固定歌手列表，而是通过三个外部 API **动态发现**新歌手：

| # | 发现源 | 方法 | 优势 |
|---|--------|------|------|
| 1 | **NetEase 相似歌手** | 从用户已喜欢的歌手出发，调用 `/api/artist/similar` 获取关联歌手 | 口味相近，推荐精准 |
| 2 | **NetEase 排行榜** | 拉取 Oricon 周榜、日语歌曲排行榜等 toplist，提取歌手 | 覆盖主流热门 |
| 3 | **NetEase 风格歌单** | 搜索"日语新歌""动漫主题曲""日语R&B"等 14 种关键词的歌单，从中提取歌手 | 发现小众和跨风格歌手 |
| 4 | **Bilibili 趋势搜索** | 搜索"日语新歌 MV""アニメ 主題歌 最新"等 9 种热词，从视频标题和 UP 主名提取歌手 | 覆盖最新热门和 B 站独有内容 |
| 5 | **NetEase 新歌搜索** | 搜索"新曲 日本""新作 アニメ 主題歌"等关键词的歌曲结果，提取歌手 | 发现最新发行的歌手 |

每次运行：
1. 5 个源各自独立获取歌手名，每个源内部**随机采样**
2. 过滤掉系统中已有的歌手
3. 全部结果随机打乱后取前 N 个进入搜索阶段
4. 每个歌手最多发现 3 首歌

因为每个源内部都做了 `random.shuffle`，所以**每次运行发现的歌手组合都不同**。

**不指定 `--artists` 时自动使用动态发现。** 指定 `--artists` 时跳过动态发现，直接搜索指定歌手。

### 2.3 Bilibili 搜索

```python
import requests, re, time

BILI_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://search.bilibili.com/",
}

# ⚠️ 必须包含足够多的排除关键词
EXCLUDE_RE = re.compile(
    r"(?i)(live|演唱会|现场|concert|翻唱|cover|教学|教程|钢琴版|钢琴改编|吉他|伴奏"
    r"|instrumental|karaoke|反应|reaction|混剪|合集|\bAI\b|鬼畜|搞笑|舞蹈|choreography"
    r"|弹幕|切片|手势舞|指弹|鼓谱|鼓|drum|bass|TAB|谱"
    r"|踊ってみた|歌ってみた|叩いてみた|弾いてみた"
    r"|舞萌|maimai|音游|Phigros|Arcaea|osu|rhythm|beatmania"
    r"|English\s*Ver|英文版|英语版|粤语版|国语版"
    r"|Nightcore|bootleg|sped up|slowed|8D|ASMR|耳机|录音棚"
    r"|编舞|原创编舞|口琴|尤克里里|ukulele|单簧管|黑管)")

def search_bilibili(keyword, retries=3, tids=3):
    """tids=3 限定音乐分区，避免匹配到影视剪辑、游戏、舞蹈等无关内容"""
    for attempt in range(retries):
        try:
            params = {"search_type": "video", "keyword": keyword, "page": 1, "pagesize": 20}
            if tids:
                params["tids"] = tids
            r = requests.get("https://api.bilibili.com/x/web-interface/search/type",
                params=params, headers=BILI_HEADERS, timeout=15)
            if r.status_code == 412:
                time.sleep((attempt + 1) * 5)
                continue
            return r.json().get("data", {}).get("result", []) or []
        except:
            time.sleep(2)
    return []
```

### 2.4 视频评分算法

对每个搜索结果评分，选取最高分视频：

```python
def score_video(video, song_title, artist, expected_duration=None):
    vtitle = re.sub(r"<[^>]+>", "", video.get("title", ""))

    # 排除不合格视频
    if EXCLUDE_RE.search(vtitle):
        return -1

    # ⚠️ 硬性要求：视频标题必须包含歌曲名或歌手名
    has_song_title = song_title.lower() in vtitle.lower()
    has_artist = artist.lower() in vtitle.lower()
    if not has_song_title and not has_artist:
        return -1  # 两者都不包含 → 大概率不是目标歌曲

    # ⚠️ 通用英文歌名（everything/aurora/love等）必须同时匹配歌名+歌手
    COMMON_ENGLISH_TITLES = {'everything','love','dream','aurora','friends','alone','forever',...}
    if song_title.lower().strip() in COMMON_ENGLISH_TITLES:
        if not (has_song_title and has_artist):
            return -1

    # ⚠️ 时长偏差>60s直接拒绝
    if expected_duration and abs(duration - expected_duration) > 60:
        return -1

    score = 0
    if has_song_title: score += 50
    if has_artist: score += 20

    # 官方/MV 加分
    for kw in ["MV", "官方", "Official", "Music Video", "高音質", "HD"]:
        if kw.lower() in vtitle.lower():
            score += 15

    # 时长合理性
    duration = parse_duration(video.get("duration", "0"))
    if expected_duration and abs(duration - expected_duration) <= 30:
        score += 30
    elif 90 <= duration <= 420:
        score += 15
    elif 60 <= duration <= 600:
        score += 5
    else:
        score -= 20

    # 播放量
    play = video.get("play", 0) or 0
    if isinstance(play, str):
        play = int(play) if play.isdigit() else 0
    if play > 1000000:
        score += 40
    elif play > 100000:
        score += 25
    elif play > 10000:
        score += 10

    return score
```

**⚠️ 时长比对是核心质量关卡**。Agent 必须预先知道每首歌的原版时长（从 NetEase 获取），与 Bilibili 视频时长比较。偏差超过 30 秒的视频不应使用。

**⚠️ 每首歌导入后必须立即验证以下全部字段**（脚本内置，不可跳过）：
- audio_url 非空
- cover_url 非空
- lyrics 已上传
- duration 偏差 ≤ 30s
- bitrate ≥ 96kbps
- song_tags = 28 条
验证不通过的歌曲必须当场处理，不能"先导入后面再修"。

---

## 3. 粗略匹配度计算

在下载之前，先用已知信息计算粗略向量，评估是否值得导入。

### 3.1 可预判维度

| 维度 | 判断方式 |
|------|----------|
| Language (4维) | 搜索确认歌曲语种 |
| Vocal (3维) | 查歌手性别 |
| Source (6维) | 查歌曲来源（动漫OP/游戏OST/原创等） |
| Era (1维) | 查发行年份 |

这 14 维无需下载音频即可确定。

### 3.2 双重阈值筛选

系统采用**两道质量关卡**，确保只有高匹配度歌曲才会被保留：

| 阶段 | 阈值 | 时机 | 动作 |
|------|------|------|------|
| **粗略筛选** | `--threshold` (默认 0.6) | 下载前，基于元数据预估 | 低于阈值的不下载 |
| **精确校验** | `--final-threshold` (默认 0.80) | 导入并打完 28 维标签后 | 低于阈值的歌曲 `status` 设为 0（歌曲禁用） |

**⚠️ status 含义不统一**：歌手 `status=0` = 活跃，`status=1` = 停更；歌曲 `status=1` = 正常，`status=0` = 禁用。两者含义相反。

```bash
# 使用默认阈值
python3 external_discover.py --user-id 27 --artists "Aimer,LiSA"

# 自定义阈值（更严格）
python3 external_discover.py --user-id 27 --artists "Aimer" --threshold 0.7 --final-threshold 0.9
```

精确校验阶段会调用推荐引擎获取新歌与用户的实际相似度。低于 `final-threshold` 的歌曲会被自动禁用，不会出现在推荐列表中。

---

## 4. 下载与导入

**⚠️ 以下所有步骤必须严格遵守 [`music-import/README.md`](../music-import/README.md) 的规范。**

### 4.1 流程概要

```
对每首通过粗略筛选的歌曲：
  1. 查找/创建歌手（⚠️ 必须先搜索已有歌手，防止重复创建）
  2. 补全歌手信息：POST /api/music/update-profile（profile, genre, country）+ 上传头像
  3. 创建歌曲记录
  4. 下载音频（Bilibili yt-dlp + ffmpeg）
  5. 验证音频质量（时长、比特率、是否原版）
  6. 上传音频
  7. 搜索并上传封面（MV 缩略图）
  8. 获取并上传歌词（NetEase → 原文 + 翻译）
  9. 补全元数据（genre, release_year, duration）
```

### 4.2 歌手去重（硬性要求）

```python
# 获取所有已有歌手
r = requests.get(f"{GATEWAY}/api/music/singers", headers=HEADERS)
existing_singers = {s['name']: s['id'] for s in r.json()['data']}

# 匹配时注意大小写
artist_name = "YOASOBI"
singer_id = existing_singers.get(artist_name)
if singer_id is None:
    # 创建新歌手 + 上传头像 + 更新 profile
    ...
```

### 4.3 音频验证（不可跳过）

```python
from mutagen.mp3 import MP3

audio = MP3(filepath)
actual_dur = audio.info.length
expected_dur = get_expected_duration(title, artist)  # 从 NetEase 获取

if abs(actual_dur - expected_dur) > 30:
    print(f"⚠️ 时长异常: {actual_dur:.0f}s vs 预期 {expected_dur}s，可能不是原版")
    # 必须重新下载或选择其他源
if audio.info.bitrate // 1000 < 128:
    print(f"⚠️ 比特率过低: {audio.info.bitrate // 1000}kbps")
```

---

## 5. 完整标签打标

**⚠️ 必须严格遵守 [`song-tagging/README.md`](../song-tagging/README.md) 的全部规范。**

### 5.1 28 维标签清单

导入后必须确保每首歌拥有完整的 28 维标签：

| 类别 | 维度数 | tag_id | 打标方式 | 常见错误 |
|------|--------|--------|----------|----------|
| Language | 4 | 1-4 | 搜索确认原始语种 | ❌ 靠标题字符集判断（"inside you"不是英文歌） |
| Source | 6 | 5-10 | 查询歌曲来源 | ❌ 完全不打（batch_tag.py 不含此维度） |
| Vocal | 3 | 17-19 | 查 singers.sex | ❌ 组合歌手性别判断错误 |
| Audio | 6 | 20-25 | `analyze_audio.py` | ❌ 跳过不跑（--skip-audio） |
| Era | 1 | 26 | 查 release_year | ❌ 完全不打（batch_tag.py 不含此维度） |
| Mood | 8 | 27-34 | `analyze_mood.py` 或专家标注 | ❌ 中文词典对日文歌词无效，结果同质化 |

### 5.2 Mood 标签的日文歌特殊处理

`analyze_mood.py` 使用 MEmoLon 中文词典，对日文歌词（即使用中文翻译版）的区分度极差。表现为所有歌的 mood_valence 集中在 0.52±0.03。

**解决方案**：对日文歌曲，使用专家标注替代词典分析。Agent 需要基于对歌曲内容的理解手动赋值。

标注参考：

| 歌曲特征 | valence | arousal | dominance | joy | anger | sadness | fear |
|----------|---------|---------|-----------|-----|-------|---------|------|
| 热血战斗 OP | 0.4-0.5 | 0.7-0.9 | 0.7-0.8 | 低 | 中 | 低 | 低 |
| 甜蜜恋爱 | 0.7-0.9 | 0.2-0.4 | 0.4-0.5 | 高 | 极低 | 低 | 极低 |
| 悲伤离别 | 0.2-0.4 | 0.3-0.5 | 0.3-0.5 | 低 | 低 | 高 | 中 |
| 愤怒反叛 | 0.2-0.3 | 0.8-0.9 | 0.6-0.7 | 极低 | 高 | 低 | 低 |
| 欢快舞曲 | 0.7-0.8 | 0.8-0.9 | 0.6-0.7 | 高 | 低 | 极低 | 极低 |

source 字段填 `expert_annotation`。

### 5.3 标签写入 API

```python
import json, urllib.request

def put_tag(song_id, tag_id, value, confidence, source, evidence="{}"):
    url = f"http://localhost:8085/recommend/songs/{song_id}/tags/{tag_id}"
    data = json.dumps({
        "value": value, "confidence": confidence,
        "source": source, "evidenceJson": evidence
    }).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='PUT',
        headers={'Content-Type': 'application/json'})
    urllib.request.urlopen(req)
```

### 5.4 向量重建

所有标签写入后，必须重建向量：

```bash
curl -s -X POST "http://localhost:8085/recommend/songs/rebuild-all"
```

---

## 6. 推荐验证

### 6.1 获取推荐结果

```bash
curl -s "http://localhost:8085/recommend/daily?userId=27&limit=20"
```

### 6.2 验证标准

| 检查项 | 合格标准 | 不合格表现 |
|--------|----------|------------|
| 分数分层 | 同语种歌曲分数应有分层（0.01+ 差异） | 全部 0.715 → 标签同质化 |
| 语种分界 | 偏好语种 vs 非偏好语种分数应有明显差距 | 中文日文分数一样 → Language 标签错误 |
| 新歌出现 | 新导入歌曲应出现在推荐列表中 | 不出现 → 标签缺失或向量未重建 |

---

## 7. 完整 Checklist

```
□ Phase 0: 准备
  □ 所有服务运行中
  □ 获取 JWT token
  □ 获取目标用户偏好向量
  □ 分析偏好（语种、来源、声线、情绪倾向）

□ Phase 1: 发现
  □ 根据偏好确定搜索方向（歌手列表、关键词）
  □ Bilibili/NetEase 搜索候选歌曲
  □ 粗略匹配度筛选（> 0.5）
  □ 确认歌曲列表（歌手、曲名、预期时长、语种、来源）

□ Phase 2: 导入（⚠️ 严格遵守 music-import/README.md）
  □ 对每首歌：
    □ 查找/创建歌手（必须先搜索已有歌手）
    □ 补全歌手信息: POST /api/music/update-profile（profile简介, genre风格, country国籍）
    □ 歌手头像上传（NetEase artist.picUrl）
    □ 创建歌曲记录
    □ 下载音频 + 验证时长/比特率
    □ 上传音频
    □ 搜索并上传 MV 封面
    □ 获取并上传歌词（原文 + 翻译）
    □ 补全 genre, release_year, duration

□ Phase 3: 标签打标（⚠️ 严格遵守 song-tagging/README.md）
  □ Language (4维): 搜索确认原始语种
  □ Source (6维): 查询歌曲来源
  □ Vocal (3维): 查 singers.sex
  □ Audio (6维): analyze_audio.py
  □ Mood (8维): analyze_mood.py 或专家标注
  □ Era (1维): 查 release_year
  □ 验证每首歌 28 条 song_tags 记录

□ Phase 4: 系统同步
  □ POST /api/music/search/sync (ES 全量同步)
  □ POST /recommend/songs/rebuild-all (向量重建)

□ Phase 5: 验证（逐首检查）
  □ 音频: 时长偏差 < 30s，比特率 >= 128kbps
  □ 封面: cover_url 非空且各不相同
  □ 歌词: lyrics 表有 >= 2 条记录（原文 + 翻译）
  □ 标签: song_tags 有 28 条记录，Source 和 Era 非空
  □ 歌手: avatar_url 非空，sex 正确，profile/genre/country 非空，无重复
  □ ES: music_search 索引中可查到
  □ 推荐: 新歌出现在推荐列表中，分数有分层
```

---

## 8. 已知陷阱与教训

### 8.1 Bilibili 视频筛选

| 陷阱 | 事故 | 防范 |
|------|------|------|
| 音游视频 | 残響散歌下载了「舞萌(maimai)」通关视频 | EXCLUDE_RE 加入 maimai/Phigros/音游/rhythm |
| 舞蹈翻跳 | カタオモイ下载了「踊ってみた」视频，时长从5:40缩到3:48 | EXCLUDE_RE 加入 踊ってみた/编舞 |
| 错误语言版本 | アイドル下载了 English Ver. 而非日文原版 | EXCLUDE_RE 加入 English Ver/英文版 |
| 二创加工 | 「录音棚大声听」视频可能有额外音频处理 | EXCLUDE_RE 加入 录音棚 |

### 8.2 标签打标

| 陷阱 | 事故 | 防范 |
|------|------|------|
| batch_tag.py 维度不全 | 只打了 21/28 维（缺 Source 和 Era） | 打标后必须验证 tag_count=28 |
| 标题字符集判断语种 | "inside you"(milet) 被标为英文 | 必须搜索确认，不能靠标题字符集 |
| Mood 词典跨语言失效 | 10 首日文歌的 mood 值几乎相同 (0.52±0.03) | 日文歌用专家标注，不用 memolon |
| --skip-audio | 跳过音频分析导致相似度全部 71.5% | 永远不要 --skip-audio |

### 8.3 导入流程

| 陷阱 | 事故 | 防范 |
|------|------|------|
| 歌手重复创建 | YOASOBI 被创建两次 (id=3 和 id=31) | 导入前必须搜索已有歌手列表 |
| 元数据缺失 | genre/release_year/duration 全部 NULL | 导入后立即补全 |
| ES 未同步 | 10 首歌搜不到 | 导入后必须调用 sync 接口 |
| 歌手无头像 | 6 个新歌手没有头像 | 创建歌手后立即上传头像 |
| 歌手只创建不补全 | profile/genre/country 全为 NULL | 创建后立即调用 update-profile 补全简介、风格、国籍 |
| 只验证推荐分数 | 分数正常但音频/标签/元数据全是错的 | 必须逐首逐字段验证 |
| EXCLUDE_RE `AI` 误杀 Aimer | case-insensitive `AI` 匹配 "Aimer" 中的 "Ai"，所有 Aimer 视频被排除 | 使用 `\bAI\b` 加 word boundary |
| Bilibili 搜索匹配错歌 | 搜 "SPARK-AGAIN" 却下载了 "Sign"；搜 "Black Bird" 匹配到 Billie Eilish | score_video 要求 song_title 或 artist 出现在视频标题；限定音乐分区 tids=3 |
| 歌手 status 反设 | 歌手 status=0=活跃, status=1=停更，与歌曲(1=正常, 0=禁用)含义相反。创建歌手误传 status=1 导致全标为"退役" | 创建歌手必须传 status=0 |
| 通用英文歌名匹配错歌手 | 搜 "everything" 匹配到 MISIA 版本而非 3L 版本；搜 "aurora" 匹配到高达 AGE OP | 通用英文歌名（everything/aurora/love 等）必须同时匹配歌名+歌手名才算命中（`COMMON_ENGLISH_TITLES` 集合） |
| 下载后不验证时长 | 下载了 2730s 的专辑评论当作单曲 | **下载后**用 mutagen 读取实际时长，与 NetEase 预期时长对比，偏差 > 60s 直接丢弃 |
| batch_tag.py 只打 21 维 | 导入 83 首歌后 Source/Era 全部缺失 | 脚本内置 `_complete_missing_tags()` 在 batch_tag.py 之后自动补全 Source(5-10)、Era(26)、Language 覆盖 |
| 语言标签靠字符集 | Latin 标题日语歌被标为英语 | `_complete_missing_tags()` 自动检测已知日本歌手，覆盖为日语 |
| 批量导入不逐首验证 | 83 首歌里混入错误下载 | 导入循环内必须内置质检（时长/码率/标签数），不是事后补查 |

### 8.4 执行纪律（根因防范）

| 失误模式 | 根因 | 防范机制 |
|----------|------|----------|
| MD 写了但没遵守 | 写代码时参考旧代码而非 MD | **约束编码进脚本**，不依赖 agent 记忆 |
| 先跑后查 | Phase 5 验证在 batch 结束后才做 | 验证逻辑内置于导入循环内（per-song 验证） |
| 一次性脚本放松标准 | 替补/修复脚本绕过主流程 | **禁止编写临时批量脚本**。所有导入必须走 `external_discover.py --songs=plan.json`。该脚本内置了全部质检逻辑，临时脚本不可能完整复制 |
| batch_tag.py 当黑盒 | 不检查输出就信任结果 | `_complete_missing_tags()` 自动补全 + 验证 tag_count=28 |

> **⛔ 铁律 1：禁止编写任何临时导入脚本（如 batch_expand.py、quick_import.py）。**
>
> 所有歌曲导入必须通过 `external_discover.py`。该脚本包含完整的质检链路（视频评分、时长验证、码率检查、通用歌名保护、28 维标签补全、post-import quality gate）。临时脚本必然遗漏其中某些检查。

> **⛔ 铁律 2：一次一首。导入 → 验证 → 下一首。**
>
> 脚本内置 `MAX_BATCH=1`。每次运行只导入 1 首歌，导入后自动验证全部字段。确认无误后再运行下一首。
>
> ```bash
> # 正确：一首一首来
> python3 external_discover.py --user-id 27 --songs /tmp/song1.json
> # → 验证通过
> python3 external_discover.py --user-id 27 --songs /tmp/song2.json
> # → 验证通过
>
> # 错误：任何形式的批量
> python3 external_discover.py --limit 10  # ← 会被 cap 到 1
> python3 batch_expand.py  # ← 禁止
> ```

---

## 9. 参考脚本

| 脚本 | 路径 | 用途 |
|------|------|------|
| `external_discover.py` | `agent-playbooks/external-discover/external_discover.py` | 外部发现 + 导入全链路 |
| `bili-music-download.py` | `agent-playbooks/music-import/bili-music-download.py` | Bilibili 音频下载 |
| `batch_tag.py` | `agent-playbooks/song-tagging/batch_tag.py` | 批量标签打标 |
| `analyze_mood.py` | `agent-playbooks/song-tagging/analyze_mood.py` | Mood 分析 |
| `analyze_audio.py` | `agent-playbooks/song-tagging/analyze_audio.py` | Audio 特征提取 |
