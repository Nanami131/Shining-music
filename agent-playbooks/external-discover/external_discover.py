#!/usr/bin/env python3
"""
外部歌曲发现 + 预筛 + 自动导入（Bilibili 源）
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! Agent 自主验证要求（不可跳过）：                                 !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!   - MAX_BATCH=1，一次只处理一首歌                               !!
!!   - 每首歌导入后 Agent 必须独立验证：                            !!
!!     1. SQL 查 songs 表确认记录存在且字段完整                     !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!     2. curl MinIO 确认音频文件可达、时长匹配                     !!
!!     3. SQL 查 lyrics 表确认歌词行数 ≥ 10                        !!
!!     4. SQL 查 song_tags 确认 28 维标签全部写入                   !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!     5. API 查推荐结果确认新歌被推荐引擎拾取                      !!
!!   - 脚本内置的验证是第一道关，Agent 验证是第二道关               !!
!!   - batch_tag.py 以 capture_output 模式运行，Agent 必须检查     !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!     其退出码和日志，不要假设它成功了                              !!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
流程：
  1. 读取用户偏好向量
  2. 在 Bilibili 搜索候选歌曲
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
  3. 基于元数据（语种、声线）预筛
  4. 下载高匹配歌曲的音频
  5. 导入系统（创建歌手/歌曲、上传音频/封面/歌词）
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
  6. 运行 batch_tag.py 生成精确向量
  7. 验证推荐引擎能拾取新歌

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
用法:
  python3 external_discover.py --user-id 27 --limit 10
  python3 external_discover.py --user-id 27 --artists "Aimer,LiSA,Ado" --limit 5
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
  python3 external_discover.py --user-id 27 --dry-run
"""

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import os
import re
import sys
import unicodedata
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import json
import time
import hashlib
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import glob
import argparse
import tempfile
import subprocess
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

import requests

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.join(SCRIPT_DIR, '..', '..')
GATEWAY = "http://localhost:8080"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
RECOMMEND_API = "http://localhost:8085/recommend"

DB_CMD = ['docker', 'exec', 'shining-mysql', 'mysql', '-u', 'root', '-ppassword',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
          'shining-music', '--default-character-set=utf8mb4', '-N', '-e']
REDIS_CMD = ['docker', 'exec', 'shining-redis', 'redis-cli', '-n', '4']

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
FFMPEG = os.path.expanduser(
    "~/.local/lib/python3.8/site-packages/imageio_ffmpeg/binaries/ffmpeg-linux64-v4.2.2")
YTDLP = os.path.expanduser("~/.local/bin/yt-dlp")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

BILI_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    "Referer": "https://search.bilibili.com/",
}

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
CATEGORY_WEIGHTS = {
    'language': 0.0, 'source': 0.7535, 'mood': 0.7535,
    'vocal': 0.6901, 'audio': 0.5798, 'era': 0.7559,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
}
DIM_RANGES = {
    'language': (0, 4), 'source': (4, 10), 'mood': (10, 18),
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    'vocal': (18, 21), 'audio': (21, 27), 'era': (27, 28),
}

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
KNOWN_FEMALE_ARTISTS = {
    'Aimer', 'LiSA', 'Ado', 'Reol', 'milet', 'Uru', 'YOASOBI', 'ヨルシカ',
    'ずっと真夜中でいいのに。', '花譜', '鹿乃', '96猫', 'あよ',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    'めらみぽっぷ', '藍月なくる', '三月のパンタシア', '高嶺のなでしこ',
    "μ's", 'V.W.P', "May'n", 'EGOIST', 'ClariS', 'fripSide',
    'Kalafina', 'GARNiDELiA', 'ReoNa', 'JUNNA', 'TRUE', '上坂すみれ',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    '鈴木このみ', '水瀬いのり', 'TrySail', 'スピラ・スピカ',
    'halca', 'ぼっちぼろまる', 'いよわ', 'ナナヲアカリ',
}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
KNOWN_MALE_ARTISTS = {
    'Eve', '米津玄師', 'King Gnu', '藤井 風', 'Official髭男dism',
    'Mrs. GREEN APPLE', 'back number', 'Vaundy', 'BUMP OF CHICKEN',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    'UVERworld', 'MAN WITH A MISSION', 'ONE OK ROCK',
    'RADWIMPS', 'Linked Horizon', 'MY FIRST STORY',
}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

JP_ARTISTS_POOL = {
    'female': [
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'Aimer', 'LiSA', 'Ado', 'Reol', 'milet', 'Uru', 'ReoNa',
        'ヨルシカ', 'ずっと真夜中でいいのに。', '花譜',
        'EGOIST', 'ClariS', 'GARNiDELiA', 'fripSide', 'Kalafina',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'ナナヲアカリ', 'ぼっちぼろまる', 'いよわ', '鹿乃',
        "May'n", 'JUNNA', 'TRUE', 'TrySail', 'スピラ・スピカ', 'halca',
        '上坂すみれ', '水瀬いのり', '鈴木このみ',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        '藍井エイル', 'ASCA', 'SawanoHiroyuki[nZk]',
        'やなぎなぎ', '茅原実里', '高橋李依', 'Machico',
        'ZAQ', 'ChouCho', '南條愛乃', 'Ray',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        '美波', 'Cö shu Nie', 'miwa',
        'いきものがかり', 'Superfly', '宇多田ヒカル', 'MISIA',
        '椎名林檎', 'aiko', '倉木麻衣', 'KOKIA', '手嶌葵',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        '新しい学校のリーダーズ', 'iri',
    ],
    'male': [
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'Eve', '米津玄師', 'King Gnu', '藤井 風', 'Official髭男dism',
        'Mrs. GREEN APPLE', 'back number', 'Vaundy',
        'BUMP OF CHICKEN', 'UVERworld', 'MAN WITH A MISSION',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'ONE OK ROCK', 'RADWIMPS', 'Linked Horizon', 'MY FIRST STORY',
        'FLOW', 'SPYAIR', 'T.M.Revolution', 'OLDCODEX',
        'TK from 凛として時雨',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'ASIAN KUNG-FU GENERATION', 'UNISON SQUARE GARDEN',
        'Survive Said The Prophet', 'Nano',
        'THE ORAL CIGARETTES', 'SID', 'flumpool', 'GRANRODEO',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'Fear, and Loathing in Las Vegas', 'PENGUIN RESEARCH',
        '須田景凪', 'Nulbarich', 'SIRUP', 'imase',
        '星野源', 'Saucy Dog', '緑黄色社会',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    ],
    'mixed': [
        'YOASOBI', 'MYTH & ROID', 'ATARASHII GAKKO!',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    ],
}

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
EXCLUDE_RE = re.compile(
    r"(?i)(live|演唱会|现场|concert|翻唱|cover|教学|教程|钢琴版|钢琴改编|吉他|伴奏"
    r"|instrumental|karaoke|反应|reaction|混剪|合集|\bAI\b|鬼畜|搞笑|舞蹈|choreography"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r"|弹幕|切片|手势舞|指弹|鼓谱|鼓|drum|bass|TAB|谱"
    r"|踊ってみた|歌ってみた|叩いてみた|弾いてみた"
    r"|舞萌|maimai|音游|Phigros|Arcaea|osu|rhythm|beatmania"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r"|English\s*Ver|英文版|英语版|粤语版|国语版"
    r"|Nightcore|bootleg|sped up|slowed|8D|ASMR|耳机|录音棚"
    r"|编舞|原创编舞|口琴|尤克里里|ukulele|单簧管|黑管)")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def info(m):  print(f"\033[1;34m[INFO]\033[0m  {m}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def ok(m):    print(f"\033[1;32m[OK]\033[0m    {m}")
def warn(m):  print(f"\033[1;33m[WARN]\033[0m  {m}", file=sys.stderr)
def err(m):   print(f"\033[1;31m[ERR]\033[0m   {m}", file=sys.stderr)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


# ── Helpers ──
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def redis_get(key):
    r = subprocess.run(REDIS_CMD + ['GET', key], capture_output=True, text=True)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    out = r.stdout.strip()
    return None if out in ('', '(nil)') else out

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def clear_recommend_cache(user_id):
    keys = [f"recommend:daily:cb:{user_id}", f"recommend:daily:cf:{user_id}"]
    subprocess.run(REDIS_CMD + ['DEL'] + keys, capture_output=True, text=True)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def db_query(sql):
    r = subprocess.run(DB_CMD + [sql], capture_output=True, text=True)
    if r.returncode != 0: return []
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return [l.split('\t') for l in r.stdout.strip().split('\n') if l]

def file_md5(path):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    h = hashlib.md5()
    with open(path, 'rb') as f:
        for c in iter(lambda: f.read(8192), b''): h.update(c)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return h.hexdigest()

def safe_name(n, ml=140):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return re.sub(r'[\\/:*?"<>|]', '_', (n or '').strip())[:ml].rstrip(' .') or 'unknown'

def clean_html(s):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return re.sub(r"<[^>]+>", "", s or "")

def normalize_match_text(s):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    s = unicodedata.normalize('NFKC', s or '').casefold()
    s = s.replace('〜', '~').replace('～', '~').replace('－', '-').replace('―', '-').replace('—', '-')
    s = re.sub(r'\s+', '', s)
    return re.sub(r'[\[\]【】「」『』"\'`.,，。!！?？:：;；()（）/\\_・·]', '', s)

def split_artist_names(artist):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return [a.strip() for a in re.split(r'[/／,，、&＆×]', artist or '') if a.strip()]

def score_netease_song(song, artist, title):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    target_title = normalize_match_text(title)
    song_title = normalize_match_text(song.get('name', ''))
    target_artists = [normalize_match_text(a) for a in split_artist_names(artist)]
    song_artists = [normalize_match_text(a.get('name', '')) for a in song.get('artists', [])]
    score = 0
    if song_title == target_title:
        score += 100
    elif target_title and (target_title in song_title or song_title in target_title):
        score += 60
    for target_artist in target_artists:
        if any(target_artist and (target_artist in song_artist or song_artist in target_artist)
               for song_artist in song_artists):
            score += 40
    return score

def choose_netease_song(songs, artist, title):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not songs:
        return None
    best = max(songs, key=lambda s: score_netease_song(s, artist, title))
    return best if score_netease_song(best, artist, title) >= 100 else None


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
# ── User preference ──

def get_user_preference(user_id):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    try:
        r = requests.get(f"{RECOMMEND_API}/preference", params={"userId": user_id}, timeout=10)
        data = r.json()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if data.get('passed') and isinstance(data.get('data'), list):
            return data['data']
    except Exception:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        pass
    raw = redis_get(f"user:preference:{user_id}")
    if not raw: return None
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parsed = json.loads(raw)
    vec = parsed.get('vector', [])
    if any(v != 0 for v in vec):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return vec
    return None

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

# ── Bilibili search + download ──

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def search_bilibili(keyword, retries=3, tids=3):
    """Search Bilibili videos. tids=3 restricts to music zone."""
    for attempt in range(retries):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        try:
            params = {"search_type": "video", "keyword": keyword, "page": 1, "pagesize": 20}
            if tids:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                params["tids"] = tids
            r = requests.get("https://api.bilibili.com/x/web-interface/search/type",
                params=params, headers=BILI_HEADERS, timeout=15)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if r.status_code == 412:
                time.sleep((attempt + 1) * 5)
                continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            return r.json().get("data", {}).get("result", []) or []
        except Exception:
            time.sleep(2)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return []

def parse_duration(dur_str):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not dur_str: return 0
    parts = str(dur_str).split(":")
    if len(parts) == 2: return int(parts[0]) * 60 + int(parts[1])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if len(parts) == 3: return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    return 0

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
COMMON_ENGLISH_TITLES = {
    'everything', 'love', 'dream', 'aurora', 'friends', 'alone', 'forever',
    'hello', 'goodbye', 'angel', 'fire', 'star', 'light', 'rain', 'blue',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    'run', 'stay', 'lost', 'home', 'hope', 'wild', 'cry', 'smile', 'magic',
}

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def score_video(video, song_title, artist, expected_dur=None):
    title = clean_html(video.get("title", ""))
    play = video.get("play", 0)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if isinstance(play, str): play = int(play) if play.isdigit() else 0
    dur = parse_duration(video.get("duration"))

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if EXCLUDE_RE.search(title): return -1

    has_song_title = song_title.lower() in title.lower()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    has_artist = artist.lower() in title.lower()
    if not has_song_title:
        return -1
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    is_generic_title = song_title.lower().strip() in COMMON_ENGLISH_TITLES
    if is_generic_title and not (has_song_title and has_artist):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return -1

    if expected_dur and dur > 0 and abs(dur - expected_dur) > 60:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return -1

    score = 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if has_song_title: score += 50
    if has_artist: score += 20
    for kw in ["MV", "官方", "高音質", "HD", "HQ", "高清"]:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if kw.lower() in title.lower(): score += 15
    if expected_dur and abs(dur - expected_dur) <= 30:
        score += 30
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    elif 90 <= dur <= 420:
        score += 15
    elif 60 <= dur <= 600:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        score += 5
    else:
        score -= 20
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if play > 1000000: score += 40
    elif play > 100000: score += 25
    elif play > 10000: score += 10
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if re.search(r"[&＆×x]|feat|合唱|合作", title): score -= 30
    return score

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def find_best_video(song_title, artist, expected_dur=None):
    queries = [f"{artist} {song_title} MV", f"{artist} {song_title} 官方", f"{artist} {song_title}"]
    best, best_score = None, -1
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for q in queries:
        results = search_bilibili(q)
        if not results:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            results = search_bilibili(q, tids=None)
        for v in results:
            s = score_video(v, song_title, artist, expected_dur)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if s > best_score:
                best_score = s
                best = v
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if best_score >= 80: break
        time.sleep(2)
    return best, best_score
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def discover_songs_bilibili(artist, max_songs=10):
    """Search Bilibili for an artist's songs and return a list of (title, video) pairs."""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    results = search_bilibili(f"{artist} MV 官方", tids=3)
    songs = []
    seen = set()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for v in results:
        title = clean_html(v.get("title", ""))
        if EXCLUDE_RE.search(title): continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        dur = parse_duration(v.get("duration"))
        if not (60 <= dur <= 600): continue
        play = v.get("play", 0) or 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if isinstance(play, str): play = int(play) if play.isdigit() else 0
        if play < 10000: continue
        extracted = re.sub(rf".*{re.escape(artist)}\s*[-「『]?\s*", "", title)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        extracted = re.sub(r"[」』].*$|【.*?】|MV|官方.*$|PV.*$", "", extracted).strip()
        if not extracted or len(extracted) > 50: continue
        if extracted in seen: continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        seen.add(extracted)
        songs.append({"title": extracted, "video": v, "artist": artist})
        if len(songs) >= max_songs: break
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return songs

def download_audio(bvid, out_path):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    url = f"https://www.bilibili.com/video/{bvid}"
    final_path = out_path.replace("%(ext)s", "mp3")
    for path in glob.glob(out_path.replace("%(ext)s", "*")):
        if path != final_path and not path.endswith((".part", ".ytdl")):
            try:
                os.remove(path)
            except OSError:
                pass
    cmd = [YTDLP, "-f", "bestaudio/best", "--user-agent", BILI_HEADERS["User-Agent"],
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
           "--referer", "https://www.bilibili.com/", "-o", out_path, "--no-playlist", url]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if r.returncode != 0:
        return False, r.stderr

    source_files = [
        p for p in glob.glob(out_path.replace("%(ext)s", "*"))
        if p != final_path and not p.endswith((".part", ".ytdl"))
    ]
    if not source_files:
        return False, "yt-dlp finished but no audio file was produced"

    source = max(source_files, key=os.path.getmtime)
    conv = [FFMPEG, "-y", "-i", source, "-vn", "-codec:a", "libmp3lame", "-q:a", "0", final_path]
    c = subprocess.run(conv, capture_output=True, text=True, timeout=120)
    if c.returncode != 0 or not os.path.exists(final_path):
        return False, c.stderr
    try:
        os.remove(source)
    except OSError:
        pass
    return True, ""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


# ── Dynamic artist discovery ──
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def discover_artists_dynamically(user_vec, existing_songs, target_count=30):
    """
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    Discover artists through 5 independent external sources.
    Each source is shuffled and sampled to maximize variety across runs.
    """
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    import random
    UA = {"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"}

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    existing_artists = set()
    for title, artist in existing_songs:
        existing_artists.add(artist)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    lang_prefs = {"ja": user_vec[0], "zh": user_vec[1], "en": user_vec[2]}
    primary_lang = max(lang_prefs, key=lang_prefs.get)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    discovered = []
    seen = set(existing_artists)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    def add_artist(name):
        name = name.strip()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if name and name not in seen and len(name) >= 2:
            seen.add(name)
            discovered.append(name)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    # ── Source 1: NetEase similar artists (from user's listened artists) ──
    seed_artists = list(existing_artists)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    random.shuffle(seed_artists)
    for seed in seed_artists[:5]:
        try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            r = requests.get("https://music.163.com/api/search/get",
                params={"s": seed, "type": 100, "limit": 1}, headers=UA, timeout=10)
            artists = r.json().get('result', {}).get('artists', [])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if not artists: continue
            aid = artists[0].get('id')
            if not aid: continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            r2 = requests.get(f"https://music.163.com/api/artist/similar?id={aid}",
                headers=UA, timeout=10)
            for s in r2.json().get('artists', []):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                add_artist(s.get('name', ''))
            info(f"  [similar] '{seed}' → {len(r2.json().get('artists', []))} related")
        except Exception as e:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            warn(f"  [similar] '{seed}' failed: {e}")
        time.sleep(1)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    # ── Source 2: NetEase charts (toplist) ──
    TOPLISTS = {
        'ja': [
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            3778678,    # 日语 oricon 周榜
            60131,      # 日语歌曲排行榜
            5059661515, # 日语新歌推荐
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        ],
        'zh': [19723756, 3779629],
        'en': [2809513713, 3812895],
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    }
    chart_ids = TOPLISTS.get(primary_lang, TOPLISTS.get('ja', []))
    random.shuffle(chart_ids)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for cid in chart_ids[:2]:
        try:
            r = requests.get(f"https://music.163.com/api/playlist/detail?id={cid}",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                headers=UA, timeout=10)
            tracks = r.json().get('result', {}).get('tracks', [])
            random.shuffle(tracks)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for tr in tracks[:20]:
                for ar in tr.get('artists', []):
                    add_artist(ar.get('name', ''))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            info(f"  [chart] id={cid}: {len(tracks)} tracks")
        except Exception as e:
            warn(f"  [chart] id={cid} failed: {e}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        time.sleep(1)

    # ── Source 3: NetEase genre/keyword playlists ──
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    PL_KEYWORDS = {
        'ja': ["日语新歌", "日系女声", "动漫主题曲", "ACG神曲", "日本流行",
               "治愈系日语", "日语摇滚", "日语电子", "Vocaloid", "日语R&B",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
               "日语抒情", "令和ヒット", "J-Pop 2024", "アニソン"],
        'zh': ["华语新歌", "国语流行", "民谣精选", "说唱", "电子音乐"],
        'en': ["English pop", "indie music", "R&B", "alternative"],
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    }
    keywords = PL_KEYWORDS.get(primary_lang, PL_KEYWORDS.get('ja', []))
    random.shuffle(keywords)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for kw in keywords[:4]:
        try:
            r = requests.get("https://music.163.com/api/search/get",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                params={"s": kw, "type": 1000, "limit": 5}, headers=UA, timeout=10)
            playlists = r.json().get('result', {}).get('playlists', [])
            random.shuffle(playlists)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for pl in playlists[:2]:
                pid = pl.get('id')
                if not pid: continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                r2 = requests.get(f"https://music.163.com/api/playlist/detail?id={pid}",
                    headers=UA, timeout=10)
                tracks = r2.json().get('result', {}).get('tracks', [])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                random.shuffle(tracks)
                for tr in tracks[:15]:
                    for ar in tr.get('artists', []):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        add_artist(ar.get('name', ''))
            info(f"  [playlist] '{kw}': {len(playlists)} playlists")
        except Exception as e:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            warn(f"  [playlist] '{kw}' failed: {e}")
        time.sleep(1)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    # ── Source 4: Bilibili trending music ──
    from datetime import datetime
    current_year = datetime.now().year
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    BILI_QUERIES = {
        'ja': [f"日语新歌 MV {current_year}", "日本音乐 热门", "J-Pop MV 新曲",
               "アニメ 主題歌 最新", "日语女声 推荐", "Vocaloid 热门 新作",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
               "日语歌 百万播放", "日本歌手 MV 高音质", "ACG音乐 推荐"],
        'zh': ["华语新歌 MV", "中文热门", "国语流行 MV"],
        'en': ["English pop MV", "trending music", "indie MV"],
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    }
    queries = BILI_QUERIES.get(primary_lang, BILI_QUERIES.get('ja', []))
    random.shuffle(queries)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for q in queries[:3]:
        results = search_bilibili(q)
        for v in results:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            vtitle = clean_html(v.get("title", ""))
            if EXCLUDE_RE.search(vtitle): continue
            author = v.get("author", "").strip()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if author and len(author) <= 20:
                add_artist(author)
            for delim in ['「', '『', '-', '—', '/', '×']:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                if delim in vtitle:
                    parts = vtitle.split(delim)
                    for p in parts:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        p = re.sub(r'【.*?】|\(.*?\)|MV|官方|PV|中[日文]|字幕|高清|4K|Hi-Res', '', p).strip()
                        if 2 <= len(p) <= 20:
                            add_artist(p)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    break
        info(f"  [bilibili] '{q}': {len(results)} videos")
        time.sleep(3)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    # ── Source 5: NetEase new album/song discovery ──
    NEW_QUERIES = {
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'ja': ["新曲 日本", "新作 アニメ 主題歌", f"日本 シングル {current_year}"],
        'zh': ["新歌 华语", "新专辑"],
    }
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    nq = NEW_QUERIES.get(primary_lang, NEW_QUERIES.get('ja', []))
    random.shuffle(nq)
    for q in nq[:2]:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        try:
            r = requests.get("https://music.163.com/api/search/get",
                params={"s": q, "type": 1, "limit": 30}, headers=UA, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            songs_result = r.json().get('result', {}).get('songs', [])
            random.shuffle(songs_result)
            for song in songs_result[:15]:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                for ar in song.get('artists', []):
                    add_artist(ar.get('name', ''))
            info(f"  [new songs] '{q}': {len(songs_result)} songs")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except Exception as e:
            warn(f"  [new songs] '{q}' failed: {e}")
        time.sleep(1)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    random.shuffle(discovered)
    info(f"Dynamic discovery total: {len(discovered)} unique new artists from 5 sources")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return discovered[:target_count]


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
# ── Pre-screening ──

def compute_rough_vector(artist, title):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    vec = [0.0] * 28
    has_jp = any(('\u3040' <= c <= '\u309f') or ('\u30a0' <= c <= '\u30ff') for c in title)
    has_cjk = any('\u4e00' <= c <= '\u9fff' for c in title)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    has_latin = any('a' <= c.lower() <= 'z' for c in title)
    is_known_jp = any(a.lower() in artist.lower() for a in KNOWN_FEMALE_ARTISTS | KNOWN_MALE_ARTISTS)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if is_known_jp or has_jp:
        vec[0] = 1.0
    elif has_cjk and not has_latin:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        vec[1] = 1.0
    elif has_latin:
        vec[0] = 0.7; vec[2] = 0.3
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    is_female = any(a.lower() in artist.lower() for a in KNOWN_FEMALE_ARTISTS)
    is_male = any(a.lower() in artist.lower() for a in KNOWN_MALE_ARTISTS)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    vec[18] = 1.0 if is_male else 0.0
    vec[19] = 1.0 if is_female else 0.5
    vec[20] = 0.0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    for i in range(4, 10): vec[i] = 0.3
    for i in range(10, 18): vec[i] = 0.5
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for i in range(21, 27): vec[i] = 0.5
    vec[27] = 0.7
    return vec
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def compute_weights():
    w = [0.0] * 28
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for cat, (s, e) in DIM_RANGES.items():
        cw = CATEGORY_WEIGHTS[cat]
        n = e - s
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for i in range(s, e): w[i] = cw / n
    return w

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def weighted_cosine(a, b, w):
    dot = na = nb = 0.0
    for i in range(4, min(len(a), len(b))):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        wi = w[i] if i < len(w) else 0
        dot += wi * a[i] * b[i]
        na += wi * a[i] * a[i]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        nb += wi * b[i] * b[i]
    return dot / (na**0.5 * nb**0.5) if na > 0 and nb > 0 else 0

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

# ── System import ──

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def login():
    for u, p in [("admin", "admin123"), ("1", "1")]:
        try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            r = requests.post(f"{GATEWAY}/api/user/login", json={"username": u, "password": p}, timeout=10)
            d = r.json()
            if d.get('passed'): return d['data']['token']
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except: pass
    return None

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def get_netease_lyrics(artist, title, netease_id=None):
    try:
        if netease_id:
            nid = netease_id
        else:
            r = requests.get("https://music.163.com/api/search/get",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                params={"s": f"{artist} {title}", "type": 1, "limit": 8},
                headers={"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"}, timeout=10)
            songs = r.json().get('result', {}).get('songs', [])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            song = choose_netease_song(songs, artist, title)
            if not song: return None, None
            nid = song['id']
        r = requests.get(f"https://music.163.com/api/song/lyric?id={nid}&lv=1&kv=1&tv=-1",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            headers={"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"}, timeout=10)
        data = r.json()
        orig = data.get('lrc', {}).get('lyric', '')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        trans = data.get('tlyric', {}).get('lyric', '')
        if '<' in orig and '>' in orig: return None, None
        valid = [l for l in orig.split('\n') if re.match(r'\[\d{2}:\d{2}', l)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                 and not any(k in l.lower() for k in ['作词','作曲','编曲','ar:','ti:','al:'])]
        if len(valid) < 5: return None, None
        return orig, trans
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    except: return None, None


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def _update_singer_profile(singer_id, artist, sex, token):
    """Update singer profile, genre, and country via API + upload avatar from NetEase."""
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    ne_headers = {"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"}
    profile_text, genre_text, pic_url = "", "", None
    try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        r = requests.get("https://music.163.com/api/search/get",
            params={"s": artist, "type": 100, "limit": 1}, headers=ne_headers, timeout=10)
        artists = r.json().get('result', {}).get('artists', [])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if artists:
            a = artists[0]
            profile_text = a.get('briefDesc', '') or ''
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            alias = a.get('alias', [])
            genre_text = alias[0] if alias else ''
            if not profile_text:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                aid = a.get('id')
                if aid:
                    try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        r2 = requests.get(f"https://music.163.com/api/artist/desc?id={aid}",
                            headers=ne_headers, timeout=10)
                        intro = r2.json().get('briefDesc', '')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        if intro:
                            profile_text = intro[:200]
                    except: pass
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            pic_url = a.get('picUrl', '') or a.get('img1v1Url', '')
            if pic_url: pic_url += "?param=300y300"
    except: pass
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    requests.post(f"{GATEWAY}/api/music/update-profile",
        json={"id": singer_id, "sex": sex, "profile": profile_text,
              "genre": genre_text, "country": "日本"},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        headers=headers, timeout=10)
    if pic_url:
        try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            img = requests.get(pic_url, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
            if len(img.content) > 5000:
                import tempfile
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as tf:
                    tf.write(img.content); tmp = tf.name
                with open(tmp, 'rb') as f:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    requests.post(f"{GATEWAY}/api/music/update-avatar",
                        files={"avatarFile": ("avatar.jpg", f, "image/jpeg")},
                        data={"id": singer_id, "md5": file_md5(tmp)},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        headers={"Authorization": f"Bearer {token}"}, timeout=30)
                os.remove(tmp)
                ok(f"  Singer {artist}: profile + avatar updated")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except: pass


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def _put_tag(song_id, tag_id, value, confidence, source, evidence="{}"):
    import urllib.request
    url = f"http://localhost:8085/recommend/songs/{song_id}/tags/{tag_id}"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    data = json.dumps({
        "value": value, "confidence": confidence,
        "source": source, "evidenceJson": evidence
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    }).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='PUT',
                                 headers={'Content-Type': 'application/json'})
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    try:
        urllib.request.urlopen(req)
    except Exception:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        pass


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def _complete_missing_tags(song_ids, results):
    """
    Post-batch_tag.py completion: fill in Source (5-10), Era (26), and
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    override Language for known Japanese artists.  batch_tag.py only writes
    21/28 tags; this function ensures all 28 are present.
    """
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    info("Completing Source/Era/Language tags...")
    for sid in song_ids:
        rows = db_query(f"""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            SELECT s.artist_id, si.name, s.release_year,
                   (SELECT value FROM song_tags WHERE song_id={sid} AND tag_id=1) as lang_ja
            FROM songs s LEFT JOIN singers si ON s.artist_id = si.id
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            WHERE s.id = {sid};
        """)
        if not rows:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            continue
        artist_id, artist_name, year_str, lang_ja = rows[0]
        artist_name = artist_name or ""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        is_jp_artist = any(
            a.lower() in artist_name.lower()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for a in KNOWN_FEMALE_ARTISTS | KNOWN_MALE_ARTISTS
        ) or any('\u3040' <= c <= '\u30ff' for c in artist_name)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if is_jp_artist and (not lang_ja or float(lang_ja or 0) < 0.5):
            _put_tag(sid, 1, 1.0, 1.0, "artist_override",
                     '{"reason":"known Japanese artist"}')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            _put_tag(sid, 2, 0.0, 1.0, "artist_override")
            _put_tag(sid, 3, 0.0, 1.0, "artist_override")
            _put_tag(sid, 4, 0.0, 1.0, "artist_override")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        existing_tags = db_query(
            f"SELECT tag_id FROM song_tags WHERE song_id={sid};")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        existing_set = {int(r[0]) for r in existing_tags}

        if not existing_set.intersection({5, 6, 7, 8, 9, 10}):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for tid in range(5, 11):
                _put_tag(sid, tid, 0.3, 0.5, "default")
            if is_jp_artist:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                _put_tag(sid, 8, 1.0, 0.7, "artist_genre")

        if 26 not in existing_set:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            year = int(year_str) if year_str and str(year_str).isdigit() else 0
            era = 0.9 if year >= 2020 else 0.7 if year >= 2015 else 0.5 if year >= 2010 else 0.3 if year > 0 else 0.5
            _put_tag(sid, 26, era, 0.8, "release_year",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                     json.dumps({"year": year}))

    for sid in song_ids:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        rows = db_query(
            f"SELECT COUNT(*) FROM song_tags WHERE song_id={sid};")
        tc = int(rows[0][0]) if rows else 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if tc < 28:
            warn(f"  Song {sid}: only {tc}/28 tags after completion!")
        else:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            ok(f"  Song {sid}: {tc} tags OK")


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
# ── Main ──

def main():
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser = argparse.ArgumentParser(description="External song discovery (Bilibili)")
    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument("--artists", default=None, help="Comma-separated artist names")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser.add_argument("--songs", default=None, help="JSON file with [{artist, title}, ...] targets")
    parser.add_argument("--limit", type=int, default=10,
                        help="Max songs per run. Hard cap at 10 to ensure quality.")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser.add_argument("--threshold", type=float, default=0.6,
                        help="Rough similarity threshold for pre-screening (default: 0.6)")
    parser.add_argument("--final-threshold", type=float, default=0.35,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        help="Final similarity threshold after full tagging; songs below this are removed (default: 0.35, adjusted for mean-centered cosine)")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--out", default=None)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    args = parser.parse_args()

    MAX_BATCH = 1
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if args.limit > MAX_BATCH:
        warn(f"--limit capped to {MAX_BATCH}. One song at a time, verify before next.")
        args.limit = MAX_BATCH
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    user_vec = get_user_preference(args.user_id)
    if not user_vec:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        err(f"No preference for userId={args.user_id}"); sys.exit(1)
    info(f"User vector loaded: JP={user_vec[0]:.2f} Female={user_vec[19]:.2f}")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    weights = compute_weights()

    existing = set()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    rows = db_query("SELECT s.title, si.name FROM songs s LEFT JOIN singers si ON s.artist_id = si.id WHERE s.status=1;")
    for r in rows:
        if len(r) >= 2: existing.add((r[0].strip(), r[1].strip()))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    singers_db = {}
    rows = db_query("SELECT id, name FROM singers WHERE status=0;")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for r in rows:
        if len(r) >= 2: singers_db[r[1].strip()] = int(r[0])

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    # Build target list
    # --songs JSON format: [{"artist":"名前","title":"曲名","ne_dur":123}, ...]
    # ne_dur (optional) = expected duration in seconds from NetEase, used for validation
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    targets = []
    if args.songs:
        with open(args.songs) as f: targets = json.load(f)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    elif args.artists:
        artists = [a.strip() for a in args.artists.split(',')]
        for artist in artists:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            found = discover_songs_bilibili(artist, max_songs=5)
            for s in found:
                targets.append({"artist": s["artist"], "title": s["title"]})
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            info(f"  {artist}: discovered {len(found)} songs")
            time.sleep(3)
    else:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        discovered_artists = discover_artists_dynamically(user_vec, existing, args.limit * 3)
        for artist in discovered_artists:
            found = discover_songs_bilibili(artist, max_songs=3)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for s in found:
                targets.append({"artist": s["artist"], "title": s["title"]})
            info(f"  {artist}: {len(found)} songs")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            time.sleep(3)
            if len(targets) >= args.limit * 2:
                break
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    # Pre-screen
    scored = []
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for t in targets:
        artist, title = t['artist'], t['title']
        if (title, artist) in existing: continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        vec = compute_rough_vector(artist, title)
        sim = weighted_cosine(user_vec, vec, weights)
        is_jp = vec[0] > 0.5
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        is_female = vec[19] > 0.5
        final = sim + (0.2 if is_jp else 0) + (0.1 if is_female else 0)
        scored.append({**t, 'sim': sim, 'score': final, 'jp': is_jp, 'female': is_female})
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    scored.sort(key=lambda s: -s['score'])
    above = [s for s in scored if s['score'] >= args.threshold][:args.limit]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    info(f"Candidates: {len(scored)}, above threshold: {len(above)}")
    for i, s in enumerate(above):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        jp = "JP" if s['jp'] else "  "
        fe = "F" if s['female'] else "M"
        print(f"  {i+1:3d}. [{jp}|{fe}] {s['score']:.3f} {s['artist']} - {s['title']}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if args.dry_run:
        info("Dry run, done."); return
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    # Import
    out_dir = args.out or os.path.join(SCRIPT_DIR, 'download', 'discover')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    os.makedirs(out_dir, exist_ok=True)
    token = login()
    if not token: err("Login failed"); sys.exit(1)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    imported_ids = []
    results = []
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    for entry in above:
        artist, title = entry['artist'], entry['title']
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        ne_dur = entry.get('ne_dur', 0)
        info(f"Importing: {artist} - {title}" + (f" (expected {ne_dur}s)" if ne_dur else ""))

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        video, score = find_best_video(title, artist, expected_dur=ne_dur or None)
        if not video or score < 20:
            warn(f"  No video (score={score})"); continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        bvid = video.get("bvid")
        info(f"  Video: [{bvid}] (score={score})")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        fname = f"{safe_name(artist)} - {safe_name(title)}"
        out_template = os.path.join(out_dir, f"{fname}.%(ext)s")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        out_final = os.path.join(out_dir, f"{fname}.mp3")

        if not os.path.exists(out_final):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            dl_ok, dl_err = download_audio(bvid, out_template)
            if not dl_ok or not os.path.exists(out_final):
                warn(f"  Download failed"); continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        try:
            from mutagen.mp3 import MP3
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            audio_info = MP3(out_final)
            actual_dur = int(audio_info.info.length)
            actual_bitrate = audio_info.info.bitrate // 1000
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if actual_bitrate < 96:
                warn(f"  Low bitrate: {actual_bitrate}kbps, skip"); continue
            ne_dur = entry.get('ne_dur', 0)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if ne_dur and abs(actual_dur - ne_dur) > 60:
                warn(f"  Duration mismatch: actual={actual_dur}s expected={ne_dur}s, skip"); continue
            ok(f"  Audio: {actual_dur}s, {actual_bitrate}kbps, {os.path.getsize(out_final)//1024}KB")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except Exception as e:
            warn(f"  Audio read failed: {e}"); continue

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        singer_id = singers_db.get(artist)
        if not singer_id:
            sex = 1 if entry.get('female') else 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            r = requests.post(f"{GATEWAY}/api/music/singer",
                json={"name": artist, "sex": sex, "status": 0},
                headers={"Authorization": f"Bearer {token}"}, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            d = r.json()
            if d.get('passed') and d.get('data'):
                singer_id = d['data']['id']
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                singers_db[artist] = singer_id
                _update_singer_profile(singer_id, artist, sex, token)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if not singer_id: warn(f"  No singer"); continue

        r = requests.post(f"{GATEWAY}/api/music/song",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            json={"title": title, "artistId": singer_id, "status": 1},
            headers={"Authorization": f"Bearer {token}"}, timeout=10)
        d = r.json()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if not (d.get('passed') and d.get('data')): warn(f"  Create song fail"); continue
        song_id = d['data']['id']

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        md5 = file_md5(out_final)
        with open(out_final, 'rb') as f:
            requests.post(f"{GATEWAY}/api/music/upload",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                files={"avatarFile": (os.path.basename(out_final), f, "audio/mpeg")},
                data={"id": song_id, "md5": md5},
                headers={"Authorization": f"Bearer {token}"}, timeout=60)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        pic = video.get('pic', '')
        if pic and not pic.startswith('http'): pic = 'https:' + pic
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        cover_ok = False
        if pic:
            try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                img = requests.get(pic, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
                if len(img.content) > 5000:
                    with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as tf:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        tf.write(img.content); tmp = tf.name
                    with open(tmp, 'rb') as f:
                        r = requests.post(f"{GATEWAY}/api/music/cover/song",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                            files={"avatarFile": ("cover.jpg", f, "image/jpeg")},
                            data={"id": song_id, "md5": file_md5(tmp)},
                            headers={"Authorization": f"Bearer {token}"}, timeout=30)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    cover_ok = r.json().get('passed', False)
                    os.remove(tmp)
            except: pass
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        lyric_lang = entry.get('lang', 'ja')
        orig, trans = get_netease_lyrics(artist, title, entry.get('ne_id') or entry.get('netease_id'))
        lyrics_ok = False
        translation_ok = lyric_lang == 'zh'
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if orig:
            with tempfile.NamedTemporaryFile(mode='w', suffix='.lrc', delete=False, encoding='utf-8') as f:
                f.write(orig); tmp = f.name
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            with open(tmp, 'rb') as f:
                r = requests.post(f"{GATEWAY}/api/music/lyrics/{song_id}",
                    files={"lyricsFile": ("lyrics.lrc", f, "text/plain")},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    data={"msg": lyric_lang}, headers={"Authorization": f"Bearer {token}"}, timeout=10)
            lyrics_ok = r.json().get('passed', False)
            os.remove(tmp)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if trans and lyric_lang != 'zh':
                with tempfile.NamedTemporaryFile(mode='w', suffix='.lrc', delete=False, encoding='utf-8') as f:
                    f.write(trans); tmp = f.name
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                with open(tmp, 'rb') as f:
                    tr_resp = requests.post(f"{GATEWAY}/api/music/lyrics/{song_id}",
                        files={"lyricsFile": ("lyrics.lrc", f, "text/plain")},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        data={"msg": "zh"}, headers={"Authorization": f"Bearer {token}"}, timeout=10)
                translation_ok = tr_resp.json().get('passed', False)
                os.remove(tmp)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        ne_headers = {"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"}
        genre_val, year_val = "J-Pop", None
        try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            r_ne = requests.get("https://music.163.com/api/search/get",
                params={"s": f"{artist} {title}", "type": 1, "limit": 3},
                headers=ne_headers, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            ne_songs = r_ne.json().get('result', {}).get('songs', [])
            if ne_songs:
                album = ne_songs[0].get('album', {})
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                year_str = album.get('publishTime')
                if year_str and isinstance(year_str, (int, float)) and year_str > 0:
                    from datetime import datetime as dt
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    year_val = dt.fromtimestamp(year_str / 1000).year
        except: pass
        duration_val = actual_dur
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        parts = []
        if genre_val: parts.append(f"genre='{genre_val}'")
        if year_val: parts.append(f"release_year={year_val}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if duration_val > 0: parts.append(f"duration={duration_val}")
        if parts:
            db_query(f"UPDATE songs SET {', '.join(parts)} WHERE id={song_id};")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        imported_ids.append(song_id)
        results.append({"artist": artist, "title": title, "song_id": song_id,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        "cover": cover_ok, "lyrics": lyrics_ok})

        print(f"\n  {'─'*50}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"  ▸ 验证 id={song_id} {artist} - {title}")
        checks = {}
        rows = db_query(f"SELECT file_url, cover_url, duration FROM songs WHERE id={song_id};")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        checks['audio_url'] = bool(rows and rows[0][0] and rows[0][0] != 'NULL')
        checks['cover_url'] = bool(rows and rows[0][1] and rows[0][1] != 'NULL')
        checks['cover_upload'] = cover_ok
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        checks[f'lyrics_{lyric_lang}'] = lyrics_ok
        if lyric_lang != 'zh':
            checks['lyrics_zh'] = translation_ok
        checks['duration_match'] = not ne_dur or abs(actual_dur - ne_dur) <= 30
        dur_diff = abs(actual_dur - ne_dur) if ne_dur else 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        checks['bitrate_ok'] = actual_bitrate >= 96

        for k, v in checks.items():
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            status = "✓" if v else "✗"
            extra = f" (diff={dur_diff}s)" if k == 'duration_match' and ne_dur else ""
            extra = f" ({actual_bitrate}kbps)" if k == 'bitrate_ok' else extra
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            print(f"    {status} {k}{extra}")

        failed = [k for k, v in checks.items() if not v]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if failed:
            err(f"  FAILED: {', '.join(failed)}")
            db_query(f"UPDATE songs SET status = 0 WHERE id = {song_id};")
            imported_ids.remove(song_id)
            results = [r for r in results if r.get('song_id') != song_id]
            try:
                requests.post(f"{GATEWAY}/api/music/search/sync",
                    headers={"Authorization": f"Bearer {token}"}, timeout=30)
            except Exception:
                pass
            print(f"  {'─'*50}")
            continue
        else:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            ok(f"  ALL CHECKS PASSED")
        print(f"  {'─'*50}")
        time.sleep(3)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if imported_ids:
        requests.post(f"{GATEWAY}/api/music/search/sync",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            headers={"Authorization": f"Bearer {token}"}, timeout=30)
        ids_str = ','.join(str(i) for i in imported_ids)
        script = os.path.join(PROJECT_ROOT, 'agent-playbooks/song-tagging/batch_tag.py')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        subprocess.run(['python3', script, f'--song-ids={ids_str}'],
                       capture_output=True, text=True, cwd=PROJECT_ROOT, timeout=300)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        _complete_missing_tags(imported_ids, results)

        info("Final tag count verification:")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for sid in imported_ids:
            rows = db_query(f"SELECT COUNT(*) FROM song_tags WHERE song_id={sid};")
            tc = int(rows[0][0]) if rows else 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if tc < 28:
                err(f"  Song {sid}: {tc}/28 tags — INCOMPLETE")
            else:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                ok(f"  Song {sid}: {tc}/28 tags")

        requests.post(f"{RECOMMEND_API}/songs/rebuild-all", timeout=30)
        clear_recommend_cache(args.user_id)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    # ── Post-import quality gate ──
    if imported_ids and not args.dry_run:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        info("Running post-import quality gate...")
        try:
            rec_resp = requests.get(f"{RECOMMEND_API}/daily",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                params={"userId": args.user_id, "limit": 200}, timeout=15)
            rec_data = rec_resp.json().get('data', [])
            rec_map = {item['songId']: item['similarity'] for item in rec_data}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except Exception as e:
            warn(f"  Could not fetch recommendations: {e}")
            rec_map = {}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        kept, removed = [], []
        for r in results:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            sid = r['song_id']
            actual_sim = rec_map.get(sid, 0)
            r['actual_similarity'] = actual_sim
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if actual_sim < args.final_threshold:
                warn(f"  BELOW THRESHOLD: {r['artist']} - {r['title']} "
                     f"(sim={actual_sim:.4f} < {args.final_threshold})")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                db_query(f"UPDATE songs SET status = 0 WHERE id = {sid};")
                removed.append(r)
            else:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                ok(f"  PASS: {r['artist']} - {r['title']} (sim={actual_sim:.4f})")
                kept.append(r)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if removed:
            info(f"Removed {len(removed)} songs below final threshold ({args.final_threshold})")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    print(f"\n{'='*60}")
    kept_results = [r for r in results if r.get('actual_similarity', 0) >= args.final_threshold]
    removed_results = [r for r in results if r.get('actual_similarity', 0) < args.final_threshold]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    print(f"  Imported and kept: {len(kept_results)} songs")
    for r in kept_results:
        print(f"  ✓ {r['artist']} - {r['title']} (id={r['song_id']}, sim={r.get('actual_similarity',0):.4f})")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if removed_results:
        print(f"  Removed (below {args.final_threshold} threshold): {len(removed_results)} songs")
        for r in removed_results:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            print(f"  ✗ {r['artist']} - {r['title']} (id={r['song_id']}, sim={r.get('actual_similarity',0):.4f})")
    print(f"{'='*60}")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    with open(os.path.join(out_dir, 'discover_results.json'), 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

if __name__ == "__main__":
    main()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
