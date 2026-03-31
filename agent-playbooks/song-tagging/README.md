# 歌曲标签打标 Agent 指南

本 Playbook 指导 Agent 为 Shining-music 系统中的所有歌曲写入标签向量。

**核心原则：逐首分析，不偷懒，不套模板，每首歌单独判断。**

---

## ⚠️ 强制要求：必须调用分析脚本

**禁止手动估算 Mood 和 Audio 维度的值。** 必须使用以下脚本获取客观数据：

| 维度 | 脚本路径 | 工具 | 违反后果 |
|------|---------|------|---------|
| **Mood (8维)** | `agent-playbooks/song-tagging/analyze_mood.py` | MEmoLon 词库 + jieba | 手动估值与词库结果差异可达 10 倍 |
| **Audio (6维)** | `agent-playbooks/song-tagging/analyze_audio.py` | Essentia | 手动估值 BPM 误差可达 50% |

### 调用方式

```bash
# Mood 分析 — 传入中文歌词内容
python3 agent-playbooks/song-tagging/analyze_mood.py "<歌词文本>"

# Audio 分析 — 传入音频文件路径
curl -s -o /tmp/song_N.mp3 "<file_url>"
python3 agent-playbooks/song-tagging/analyze_audio.py /tmp/song_N.mp3
rm /tmp/song_N.mp3
```

**脚本输出 JSON 格式，直接使用 `scores` 字段的值写入 `song_tags`。**

不要因为"我觉得这首歌听起来很悲伤"就把 sadness 标为 0.85。词库分析可能只给 0.06。这是词袋模型的特性——它反映的是词汇级情感色调，不是你的主观感受。两者都是有效数据，但本系统采用词库客观值。

---

## 前置条件

1. **数据库可访问**：MySQL `shining-music` 库（root/password）
2. **recommend-service 已启动**（:8085）— 用于写入标签和 rebuildAll
3. **Python 3 + jieba + essentia**：Mood 和 Audio 分析的运行时依赖
4. **MEmoLon 词典已下载**（见下方「词典准备」章节）
5. **MinIO 可访问**（localhost:9000）— 用于下载音频文件

```bash
# 检查依赖
pip3 show jieba essentia
python3 -c "import jieba; import essentia.standard; print('OK')"
```

## 标签体系概览

| 类别 | 维度数 | dim_index | 打标方式 |
|------|--------|-----------|----------|
| Language | 4 | 0~3 | **上网搜索歌曲资料**（禁止看标题字符集） |
| Source | 6 | 4~9 | 数据库 + 上网查询 |
| Mood | 8 | 10~17 | **`analyze_mood.py`**（MEmoLon + jieba） |
| Vocal | 3 | 18~20 | 读 singers.sex |
| Audio | 6 | 21~26 | **`analyze_audio.py`**（Essentia） |
| Era | 1 | 27 | 读 songs.release_year |

数据库编码约定：
- `singers.sex`：**0=男，1=女，2=其他**（代码见 `SingerManage.vue`）

---

## Step 1: Language（4 维）

**数据源**：**上网搜索歌曲资料**

**⚠️ 重要：Language 标签反映歌曲的原始演唱语言，不是歌词翻译的可用性。**

本系统中，几乎每首歌都有中文翻译歌词。不能因为数据库里有 `language_msg='zh'` 的歌词记录就标 `lang_zh=1`。

**⚠️ 禁止使用以下方法判断语言**（已在实践中被证明不可靠）：
- ❌ 看标题字符集（大量日语歌用英文标题：WAVE、heart.beat、Stella-rium、Real or Fake 等）
- ❌ 看歌词 `language_msg` 字段（每首歌都有中文翻译版）
- ❌ 靠歌手国籍猜测（陈昕桐是中国歌手但有日语歌）

**正确做法：Agent 必须逐首上网搜索确认**：
1. 搜索 `"歌名" "歌手名"` 查找歌曲资料页面（网易云音乐、Apple Music、Wikipedia 等）
2. 确认该歌曲的**原始演唱语言**
3. 纯音乐（无人声）→ `instrumental = 1`，其他语言标签全 0

示例：
- 「嘘の火花」(96猫) → `lang_ja=1`（日语原唱），`lang_zh=0`（中文只是翻译）
- 「WAVE」(96猫) → `lang_ja=1`（日语原唱，虽然标题是英文）
- 「七里香」(周杰伦) → `lang_zh=1`（中文原唱），`lang_ja=0`
- 「Hollow Knight OST」→ `instrumental=1`，其他全 0
- 「Mysterious Night」(R-ORANGE) → `lang_ja=1`（日语原唱，虽然标题全英文）

---

## Step 2: Vocal（3 维）

**数据源**：`singers.sex`

```sql
SELECT s.id as song_id, si.sex, si.name
FROM songs s JOIN singers si ON si.id = s.artist_id;
```

映射规则：
- `sex = 0`（男）→ `vocal_male = 1`
- `sex = 1`（女）→ `vocal_female = 1`
- `sex = 2`（其他/组合）→ 需要逐个判断

特殊处理：
- 歌手是 Vocaloid（初音ミク等）→ `vocal_synth = 1`
- ユリイ・カノン：歌手本人是男性（`sex=0`），但歌曲使用 Vocaloid 演唱 → `vocal_synth = 1`
- 组合如 μ's（全女）→ `vocal_female = 1`
- Christopher Larkin：纯音乐 → 不打 vocal 标签

---

## Step 3: Source（6 维）

**数据源**：`singers.genre` + `songs.title` + **上网查询**

这是最需要 Agent 主动介入的维度。不要只靠规则，必须逐首确认：

1. 先用歌手 `genre` 关键词做初步判断
2. 对每首歌，根据标题和歌手上网搜索确认来源
3. 一首歌可以同时有多个来源标签（如 Vocaloid 原曲的翻唱版 → `src_vocaloid + src_cover`）

| 标签 | 匹配线索 |
|------|----------|
| `src_anime` | genre 含 "动画/アニメ/anime"，标题含作品名 |
| `src_game` | genre 含 "游戏/ゲーム/game"，标题含 "OST" |
| `src_vocaloid` | genre 含 "Vocaloid/UTAU"，歌手是虚拟歌手 |
| `src_original` | 歌手原创作品 |
| `src_cover` | genre 含 "翻唱/cover/歌ってみた" |
| `src_idol` | genre 含 "偶像/アイドル/idol" |

---

## Step 4: Era（1 维）

**数据源**：`songs.release_year`

```sql
SELECT id, title, release_year FROM songs;
```

- 若 `release_year` 为 NULL → 上网查询确认并更新 `songs` 表
- 归一化公式：`era_normalized = (release_year - 1950) / (2030 - 1950)`
- 截断到 `[0, 1]`

---

## Step 5: Mood（8 维）— 必须调用 `analyze_mood.py`

> **⚠️ 禁止手动赋值。必须执行 `python3 agent-playbooks/song-tagging/analyze_mood.py "<歌词>"` 获取 scores。**
> 脚本输出的 JSON `scores` 字段即为 8 维 Mood 值，直接写入 song_tags。
> source 字段统一填 `memolon_lexicon`。

### 词典准备（重要！不要跳过！）

**主词典：MEmoLon 中文词典 zh.tsv**

| 属性 | 值 |
|------|-----|
| 论文 | Buechel et al., ACL 2020: "Best Practices for Learning Domain-Specific Cross-Lingual Embeddings" |
| 下载源 | Zenodo: https://zenodo.org/record/3779901 |
| 文件 | `MTL_grouped.zip`（2.2GB）→ 解压后取 `zh.tsv` |
| 下载后文件大小 | **zh.tsv: 92MB**（1,042,279 行 × 8 维连续值） |
| 格式 | TSV: `word\tvalence\tarousal\tdominance\tjoy\tanger\tsadness\tfear\tdisgust` |
| 8 个维度 | valence, arousal, dominance, joy, anger, sadness, fear, disgust |
| 值域 | 每个维度 0~1 连续值 |

下载方法（zip 文件很大，建议只提取 zh.tsv）：

```bash
pip install remotezip
python3 -c "
from remotezip import RemoteZip
url = 'https://zenodo.org/record/3779901/files/MTL_grouped.zip'
with RemoteZip(url) as z:
    z.extract('zh.tsv', path='目标目录/')
"
```

验证下载完整性：

```bash
wc -l zh.tsv          # 预期：~2,001,800 行（含 header）
head -3 zh.tsv         # 预期：word valence arousal dominance joy anger sadness fear disgust
du -sh zh.tsv          # 预期：~92MB
```

**辅助词典（可选，用于交叉验证）**：

| 词典 | 大小 | 说明 |
|------|------|------|
| BosonNLP_sentiment_score.txt | 2.5MB / 114,766 词 | 中文情感极性连续值，覆盖口语 |
| DUTIR-emotion-ontology.csv | 1.5MB / 27,466 词 | 大连理工 7 大类 21 小类 |
| NRC-Emotion-Lexicon-Wordlevel-v0.92.txt | 2.5MB / 14,183 词 | 英文 8 情绪二值 |

### 分析流程

对每首歌的歌词：

1. **获取歌词**：从 `lyrics` 表取中文歌词内容（日文歌取中文翻译版）
2. **调用脚本**：
   ```bash
   LYRICS=$(docker exec shining-mysql mysql -u root -ppassword "shining-music" --default-character-set=utf8mb4 -N -e "SELECT content FROM lyrics WHERE song_id=N AND language_msg='zh';" 2>/dev/null)
   python3 agent-playbooks/song-tagging/analyze_mood.py "$LYRICS"
   ```
3. **读取输出**：JSON 中 `scores` 字段包含 8 维归一化值（0~1）
4. **写入**：将 8 个值通过 recommend-service API 写入 `song_tags`
5. **记录 evidence**：将 `hit_count`、`hit_rate`、`top_words` 写入 `evidence_json`

**不要偷懒**：
- **不要跳过脚本调用**。不要凭感觉赋值
- 不要用固定关键词表代替词典查询
- 不要对一批歌用同一个模板值
- 每首歌必须单独运行脚本分析
- 命中率太低时（<5% 词命中）标记 `needs_review`

---

## Step 6: Audio（6 维）— 必须调用 `analyze_audio.py`

> **⚠️ 禁止手动估算 BPM、能量等音频特征。必须执行分析脚本。**
> ```bash
> curl -s -o /tmp/song_N.mp3 "<file_url_from_songs_table>"
> python3 agent-playbooks/song-tagging/analyze_audio.py /tmp/song_N.mp3
> rm /tmp/song_N.mp3
> ```
> 脚本输出 JSON，直接用顶层 6 个字段（tempo/energy/danceability/acousticness/valence/speechiness）写入。
> source 字段统一填 `essentia_analysis`。

使用 [Essentia](https://essentia.upf.edu/)（UPF 音乐技术组开源库）提取音频特征，比 librosa 手动组合更准确、更快。

### 安装

```bash
pip install essentia
# 或 conda install -c mtg essentia
```

验证安装：
```bash
python3 -c "import essentia.standard as es; print('Essentia OK')"
```

### 音频文件位置

所有音频存储在 MinIO，通过 HTTP 下载：

```sql
SELECT id, title, file_url FROM songs;
-- file_url 示例: http://localhost:9000/shining/song/xxx.mp3
```

### 各维度提取方法

使用 `MusicExtractor` 一次性提取所有特征：

```python
import essentia.standard as es

features, _ = es.MusicExtractor(
    lowlevelStats=['mean', 'stdev'],
    rhythmStats=['mean', 'stdev'],
    tonalStats=['mean', 'stdev']
)(audio_path)
```

从结果中取 6 个维度：

| 维度 | Essentia 特征键 | 归一化方式 |
|------|----------------|-----------|
| `tempo` | `rhythm.bpm` | `(bpm - 40) / (200 - 40)`, clip [0,1] |
| `energy` | `lowlevel.average_loudness` | 直接使用（已归一化） |
| `danceability` | `rhythm.danceability` | 直接使用（0~3 → 除以 3） |
| `acousticness` | 组合：`lowlevel.spectral_centroid.mean` + `lowlevel.zerocrossingrate.mean` + `lowlevel.spectral_flatness_db.mean` | 三者越低越原声，反向归一化 |
| `valence` | `tonal.key_krumhansl.key` + `tonal.key_krumhansl.strength` | major → 高 valence, minor → 低 valence |
| `speechiness` | `lowlevel.spectral_contrast_coeffs.mean` + 有声段占比 | 语音活动检测 |

### 提取原理简述

- **tempo**：onset 包络自相关分析检测节拍周期性
- **energy**：RMS 均方根振幅均值，衡量音量/力度
- **danceability**：Essentia 用 DFA（Detrended Fluctuation Analysis）分析节奏自相似性（Streich & Herrera 2005）
- **acousticness**：频谱质心（高频内容多少）+ 零交叉率 + 频谱平坦度，声学乐器三者均低
- **valence**：Chroma 特征与大调/小调模板相关性匹配（Krumhansl key profile），大调=积极、小调=消极
- **speechiness**：频谱对比度 + 能量阈值检测有声段占比

### 执行流程

```bash
# 对每首歌：
FILE_URL=$(docker exec shining-mysql mysql -u root -ppassword "shining-music" -N -e "SELECT file_url FROM songs WHERE id=N;" 2>/dev/null)
curl -s -o /tmp/song_N.mp3 "$FILE_URL"
python3 agent-playbooks/song-tagging/analyze_audio.py /tmp/song_N.mp3
# 读取 JSON 输出的 6 个顶层字段，通过 recommend-service API 写入
# evidence_json 中记录 raw 子对象的原始测量值
rm /tmp/song_N.mp3
```

### 注意事项

- `valence` 和 `speechiness` 准确度有限（音乐情绪判断本身就很主观），标记 `confidence = 0.7`
- `tempo`、`energy`、`danceability` 较可靠，标记 `confidence = 0.9`
- 87 首歌全量处理约 3~5 分钟
- 需要 `ffmpeg` 支持 MP3 解码

---

## Step 7: rebuildAll — 向量写入 Redis

所有标签写入 `song_tags` 表后，调用 recommend-service 重建向量缓存：

```sql
-- 验证标签数据
SELECT st.song_id, s.title, td.name, st.value, st.confidence
FROM song_tags st
JOIN tag_definitions td ON td.id = st.tag_id
JOIN songs s ON s.id = st.song_id
ORDER BY st.song_id, td.dim_index;
```

通过 recommend-service 的 rebuildAll 接口触发向量重建（具体接口见 `RecommendController.java`）。

---

## 写入规范

### SQL 模板

```sql
INSERT INTO song_tags (song_id, tag_id, value, confidence, source, review_status, evidence_json)
VALUES (
  {song_id},
  (SELECT id FROM tag_definitions WHERE name = '{tag_name}'),
  {value},
  {confidence},
  '{source}',
  '{review_status}',
  '{evidence_json}'
)
ON DUPLICATE KEY UPDATE
  value = VALUES(value),
  confidence = VALUES(confidence),
  source = VALUES(source),
  review_status = VALUES(review_status),
  evidence_json = VALUES(evidence_json);
```

### 置信度规则

| 置信度 | review_status | 说明 |
|--------|---------------|------|
| >= 0.85 | `accepted` | 自动通过 |
| 0.60 ~ 0.84 | `needs_review` | 写入但待复核 |
| < 0.60 | 不写入 | 证据不足 |

### evidence_json 示例

```json
{
  "rule": "lyrics_language_detection",
  "matched_langs": ["ja", "zh"],
  "lyrics_sample": "目を覚ましたら君の姿はどこにもない",
  "agent_note": "日语原曲，有中文翻译版歌词"
}
```
