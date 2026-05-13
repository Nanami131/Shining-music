#!/usr/bin/env python3
"""
外部歌曲发现 + 预筛 + 自动导入（Bilibili 源）
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! 注意：此文件是 external-discover/external_discover.py 的副本。  !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!! 规范入口在 agent-playbooks/external-discover/external_discover.py !!
!! Agent 使用时请以规范入口为准。                                    !!
!!                                                                  !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!! Agent 自主验证要求同 external-discover/external_discover.py。     !!
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
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import json
import time
import hashlib
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
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
    'vocal': 0.6901, 'audio': 0.5798, 'era': 0.5215,
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
        'YOASOBI', 'ヨルシカ', 'ずっと真夜中でいいのに。', '花譜',
        'EGOIST', 'ClariS', 'GARNiDELiA', 'fripSide', 'Kalafina',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'ナナヲアカリ', 'ぼっちぼろまる', 'いよわ', '鹿乃',
        "May'n", 'JUNNA', 'TRUE', 'TrySail', 'スピラ・スピカ', 'halca',
        '上坂すみれ', '水瀬いのり', '鈴木このみ',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'Aoi Eir (藍井エイル)', 'LISA (ASCA)', 'SawanoHiroyuki[nZk]',
        'やなぎなぎ', '茅原実里', '高橋李依', 'Machico',
        'ZAQ', 'ChouCho', '南條愛乃', 'Ray', 'TRUE',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'Minami (美波)', 'Co shu Nie', 'MYTH & ROID',
        'Konomi Suzuki (鈴木このみ)', 'Eir Aoi (藍井エイル)',
        'Cö shu Nie', 'ASCA', 'ReoNa', 'Aimer', 'miwa',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'いきものがかり', 'Superfly', '宇多田ヒカル', 'MISIA',
        '椎名林檎', 'aiko', '倉木麻衣', 'KOKIA', '手嶌葵',
        '新しい学校のリーダーズ', 'ATARASHII GAKKO!', 'YOASOBI',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'imase', 'iri', 'Vaundy', '緑黄色社会',
    ],
    'male': [
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'Eve', '米津玄師', 'King Gnu', '藤井 風', 'Official髭男dism',
        'Mrs. GREEN APPLE', 'back number', 'Vaundy',
        'BUMP OF CHICKEN', 'UVERworld', 'MAN WITH A MISSION',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'ONE OK ROCK', 'RADWIMPS', 'Linked Horizon', 'MY FIRST STORY',
        'FLOW', 'SPYAIR', 'T.M.Revolution', 'OLDCODEX',
        'Kenshi Yonezu (米津玄師)', 'TK from 凛として時雨',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'ASIAN KUNG-FU GENERATION', 'UNISON SQUARE GARDEN',
        'Survive Said The Prophet', 'Nano', 'MAN WITH A MISSION',
        'THE ORAL CIGARETTES', 'SID', 'flumpool', 'GRANRODEO',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'Fear, and Loathing in Las Vegas', 'PENGUIN RESEARCH',
        'Aimer', '須田景凪', 'Nulbarich', 'SIRUP',
        'Fujii Kaze (藤井 風)', 'Gen Hoshino (星野源)', 'Saucy Dog',
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    ],
}

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
EXCLUDE_RE = re.compile(
    r"(?i)(live|演唱会|现场|concert|翻唱|cover|教学|教程|钢琴版|钢琴改编|吉他|伴奏"
    r"|instrumental|karaoke|反应|reaction|混剪|合集|AI|鬼畜|搞笑|舞蹈|choreography"
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


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
# ── User preference ──

def get_user_preference(user_id):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    raw = redis_get(f"user:preference:{user_id}")
    if not raw: return None
    return json.loads(raw).get('vector', [])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


# ── Bilibili search + download ──
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def search_bilibili(keyword, retries=3):
    for attempt in range(retries):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        try:
            r = requests.get("https://api.bilibili.com/x/web-interface/search/type",
                params={"search_type": "video", "keyword": keyword, "page": 1, "pagesize": 10},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                headers=BILI_HEADERS, timeout=15)
            if r.status_code == 412:
                time.sleep((attempt + 1) * 5)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                continue
            return r.json().get("data", {}).get("result", []) or []
        except Exception:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            time.sleep(2)
    return []

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def parse_duration(dur_str):
    if not dur_str: return 0
    parts = str(dur_str).split(":")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if len(parts) == 2: return int(parts[0]) * 60 + int(parts[1])
    if len(parts) == 3: return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    return 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def score_video(video, song_title, artist):
    title = clean_html(video.get("title", ""))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    play = video.get("play", 0)
    if isinstance(play, str): play = int(play) if play.isdigit() else 0
    dur = parse_duration(video.get("duration"))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if EXCLUDE_RE.search(title): return -1

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    score = 0
    if song_title in title: score += 50
    if artist in title: score += 20
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for kw in ["MV", "官方", "高音質", "HD", "HQ", "高清"]:
        if kw.lower() in title.lower(): score += 15
    if 90 <= dur <= 420: score += 30
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    elif 60 <= dur <= 600: score += 10
    else: score -= 20
    if play > 1000000: score += 40
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    elif play > 100000: score += 25
    elif play > 10000: score += 10
    if re.search(r"[&＆×x]|feat|合唱|合作", title): score -= 30
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return score

def find_best_video(song_title, artist):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    queries = [f"{artist} {song_title} MV", f"{artist} {song_title} 官方", f"{artist} {song_title}"]
    best, best_score = None, -1
    for q in queries:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        results = search_bilibili(q)
        for v in results:
            s = score_video(v, song_title, artist)
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
    results = search_bilibili(f"{artist} MV 官方")
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
    cmd = [YTDLP, "--ffmpeg-location", FFMPEG, "-x", "--audio-format", "mp3",
           "--audio-quality", "0", "--user-agent", BILI_HEADERS["User-Agent"],
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
           "--referer", "https://www.bilibili.com/", "-o", out_path, "--no-playlist", url]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    return r.returncode == 0, r.stderr
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
    BILI_QUERIES = {
        'ja': ["日语新歌 MV 2025", "日本音乐 热门", "J-Pop MV 新曲",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
               "アニメ 主題歌 最新", "日语女声 推荐", "Vocaloid 热门 新作",
               "日语歌 百万播放", "日本歌手 MV 高音质", "ACG音乐 推荐"],
        'zh': ["华语新歌 MV", "中文热门", "国语流行 MV"],
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'en': ["English pop MV", "trending music", "indie MV"],
    }
    queries = BILI_QUERIES.get(primary_lang, BILI_QUERIES.get('ja', []))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    random.shuffle(queries)
    for q in queries[:3]:
        results = search_bilibili(q)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for v in results:
            vtitle = clean_html(v.get("title", ""))
            if EXCLUDE_RE.search(vtitle): continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            author = v.get("author", "").strip()
            if author and len(author) <= 20:
                add_artist(author)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for delim in ['「', '『', '-', '—', '/', '×']:
                if delim in vtitle:
                    parts = vtitle.split(delim)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    for p in parts:
                        p = re.sub(r'【.*?】|\(.*?\)|MV|官方|PV|中[日文]|字幕|高清|4K|Hi-Res', '', p).strip()
                        if 2 <= len(p) <= 20:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                            add_artist(p)
                    break
        info(f"  [bilibili] '{q}': {len(results)} videos")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        time.sleep(3)

    # ── Source 5: NetEase new album/song discovery ──
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    NEW_QUERIES = {
        'ja': ["新曲 日本", "新作 アニメ 主題歌", "日本 シングル 2024"],
        'zh': ["新歌 华语", "新专辑"],
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    }
    nq = NEW_QUERIES.get(primary_lang, NEW_QUERIES.get('ja', []))
    random.shuffle(nq)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for q in nq[:2]:
        try:
            r = requests.get("https://music.163.com/api/search/get",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                params={"s": q, "type": 1, "limit": 30}, headers=UA, timeout=10)
            songs_result = r.json().get('result', {}).get('songs', [])
            random.shuffle(songs_result)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for song in songs_result[:15]:
                for ar in song.get('artists', []):
                    add_artist(ar.get('name', ''))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            info(f"  [new songs] '{q}': {len(songs_result)} songs")
        except Exception as e:
            warn(f"  [new songs] '{q}' failed: {e}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        time.sleep(1)

    random.shuffle(discovered)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    info(f"Dynamic discovery total: {len(discovered)} unique new artists from 5 sources")
    return discovered[:target_count]

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

# ── Pre-screening ──

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def compute_rough_vector(artist, title):
    vec = [0.0] * 28
    has_jp = any(('\u3040' <= c <= '\u309f') or ('\u30a0' <= c <= '\u30ff') for c in title)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    has_cjk = any('\u4e00' <= c <= '\u9fff' for c in title)
    has_latin = any('a' <= c.lower() <= 'z' for c in title)
    is_known_jp = any(a.lower() in artist.lower() for a in KNOWN_FEMALE_ARTISTS | KNOWN_MALE_ARTISTS)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if is_known_jp or has_jp:
        vec[0] = 1.0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    elif has_cjk and not has_latin:
        vec[1] = 1.0
    elif has_latin:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        vec[0] = 0.7; vec[2] = 0.3

    is_female = any(a.lower() in artist.lower() for a in KNOWN_FEMALE_ARTISTS)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    is_male = any(a.lower() in artist.lower() for a in KNOWN_MALE_ARTISTS)
    vec[18] = 1.0 if is_male else 0.0
    vec[19] = 1.0 if is_female else 0.5
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    vec[20] = 0.0

    for i in range(4, 10): vec[i] = 0.3
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for i in range(10, 18): vec[i] = 0.5
    for i in range(21, 27): vec[i] = 0.5
    vec[27] = 0.7
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return vec

def compute_weights():
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    w = [0.0] * 28
    for cat, (s, e) in DIM_RANGES.items():
        cw = CATEGORY_WEIGHTS[cat]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        n = e - s
        for i in range(s, e): w[i] = cw / n
    return w
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def weighted_cosine(a, b, w):
    dot = na = nb = 0.0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for i in range(4, min(len(a), len(b))):
        wi = w[i] if i < len(w) else 0
        dot += wi * a[i] * b[i]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        na += wi * a[i] * a[i]
        nb += wi * b[i] * b[i]
    return dot / (na**0.5 * nb**0.5) if na > 0 and nb > 0 else 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


# ── System import ──
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def login():
    for u, p in [("admin", "admin123"), ("1", "1")]:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        try:
            r = requests.post(f"{GATEWAY}/api/user/login", json={"username": u, "password": p}, timeout=10)
            d = r.json()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if d.get('passed'): return d['data']['token']
        except: pass
    return None
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def get_netease_lyrics(artist, title):
    try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        r = requests.get("https://music.163.com/api/search/get",
            params={"s": f"{artist} {title}", "type": 1, "limit": 5},
            headers={"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"}, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        songs = r.json().get('result', {}).get('songs', [])
        if not songs: return None, None
        nid = songs[0]['id']
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        r = requests.get(f"https://music.163.com/api/song/lyric?id={nid}&lv=1&kv=1&tv=-1",
            headers={"User-Agent": "Mozilla/5.0", "Referer": "https://music.163.com/"}, timeout=10)
        data = r.json()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        orig = data.get('lrc', {}).get('lyric', '')
        trans = data.get('tlyric', {}).get('lyric', '')
        if '<' in orig and '>' in orig: return None, None
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        valid = [l for l in orig.split('\n') if re.match(r'\[\d{2}:\d{2}', l)
                 and not any(k in l.lower() for k in ['作词','作曲','编曲','ar:','ti:','al:'])]
        if len(valid) < 5: return None, None
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return orig, trans
    except: return None, None

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

# ── Main ──

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def main():
    parser = argparse.ArgumentParser(description="External song discovery (Bilibili)")
    parser.add_argument("--user-id", type=int, required=True)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser.add_argument("--artists", default=None, help="Comma-separated artist names")
    parser.add_argument("--songs", default=None, help="JSON file with [{artist, title}, ...] targets")
    parser.add_argument("--limit", type=int, default=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser.add_argument("--threshold", type=float, default=0.6,
                        help="Rough similarity threshold for pre-screening (default: 0.6)")
    parser.add_argument("--final-threshold", type=float, default=0.80,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        help="Final similarity threshold after full tagging; songs below this are removed (default: 0.80)")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--out", default=None)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    args = parser.parse_args()

    user_vec = get_user_preference(args.user_id)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not user_vec:
        err(f"No preference for userId={args.user_id}"); sys.exit(1)
    info(f"User vector loaded: JP={user_vec[0]:.2f} Female={user_vec[19]:.2f}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    weights = compute_weights()

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    existing = set()
    rows = db_query("SELECT s.title, si.name FROM songs s LEFT JOIN singers si ON s.artist_id = si.id WHERE s.status=1;")
    for r in rows:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if len(r) >= 2: existing.add((r[0].strip(), r[1].strip()))

    singers_db = {}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    rows = db_query("SELECT id, name FROM singers WHERE status=1;")
    for r in rows:
        if len(r) >= 2: singers_db[r[1].strip()] = int(r[0])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    # Build target list
    targets = []
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if args.songs:
        with open(args.songs) as f: targets = json.load(f)
    elif args.artists:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        artists = [a.strip() for a in args.artists.split(',')]
        for artist in artists:
            found = discover_songs_bilibili(artist, max_songs=5)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            for s in found:
                targets.append({"artist": s["artist"], "title": s["title"]})
            info(f"  {artist}: discovered {len(found)} songs")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            time.sleep(3)
    else:
        discovered_artists = discover_artists_dynamically(user_vec, existing, args.limit * 3)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for artist in discovered_artists:
            found = discover_songs_bilibili(artist, max_songs=3)
            for s in found:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                targets.append({"artist": s["artist"], "title": s["title"]})
            info(f"  {artist}: {len(found)} songs")
            time.sleep(3)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if len(targets) >= args.limit * 2:
                break

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    # Pre-screen
    scored = []
    for t in targets:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        artist, title = t['artist'], t['title']
        if (title, artist) in existing: continue
        vec = compute_rough_vector(artist, title)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        sim = weighted_cosine(user_vec, vec, weights)
        is_jp = vec[0] > 0.5
        is_female = vec[19] > 0.5
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        final = sim + (0.2 if is_jp else 0) + (0.1 if is_female else 0)
        scored.append({**t, 'sim': sim, 'score': final, 'jp': is_jp, 'female': is_female})

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    scored.sort(key=lambda s: -s['score'])
    above = [s for s in scored if s['score'] >= args.threshold][:args.limit]

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    info(f"Candidates: {len(scored)}, above threshold: {len(above)}")
    for i, s in enumerate(above):
        jp = "JP" if s['jp'] else "  "
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        fe = "F" if s['female'] else "M"
        print(f"  {i+1:3d}. [{jp}|{fe}] {s['score']:.3f} {s['artist']} - {s['title']}")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if args.dry_run:
        info("Dry run, done."); return

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    # Import
    out_dir = args.out or os.path.join(SCRIPT_DIR, 'download', 'discover')
    os.makedirs(out_dir, exist_ok=True)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    token = login()
    if not token: err("Login failed"); sys.exit(1)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    imported_ids = []
    results = []

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for entry in above:
        artist, title = entry['artist'], entry['title']
        info(f"Importing: {artist} - {title}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        video, score = find_best_video(title, artist)
        if not video or score < 20:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            warn(f"  No video (score={score})"); continue

        bvid = video.get("bvid")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        info(f"  Video: [{bvid}] (score={score})")

        fname = f"{safe_name(artist)} - {safe_name(title)}"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        out_template = os.path.join(out_dir, f"{fname}.%(ext)s")
        out_final = os.path.join(out_dir, f"{fname}.mp3")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if not os.path.exists(out_final):
            dl_ok, dl_err = download_audio(bvid, out_template)
            if not dl_ok or not os.path.exists(out_final):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                warn(f"  Download failed"); continue
        ok(f"  Audio: {os.path.getsize(out_final)//1024}KB")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        singer_id = singers_db.get(artist)
        if not singer_id:
            # sex = 按歌曲中主唱人声的性别: 0=男声, 1=女声, 2=男女合唱（非组合方式）
            sex = 1 if entry.get('female') else 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            r = requests.post(f"{GATEWAY}/api/music/singer",
                json={"name": artist, "sex": sex, "status": 1},
                headers={"Authorization": f"Bearer {token}"}, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            d = r.json()
            if d.get('passed') and d.get('data'):
                singer_id = d['data']['id']
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                singers_db[artist] = singer_id

        if not singer_id: warn(f"  No singer"); continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        r = requests.post(f"{GATEWAY}/api/music/song",
            json={"title": title, "artistId": singer_id, "status": 1},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            headers={"Authorization": f"Bearer {token}"}, timeout=10)
        d = r.json()
        if not (d.get('passed') and d.get('data')): warn(f"  Create song fail"); continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        song_id = d['data']['id']

        md5 = file_md5(out_final)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        with open(out_final, 'rb') as f:
            requests.post(f"{GATEWAY}/api/music/upload",
                files={"avatarFile": (os.path.basename(out_final), f, "audio/mpeg")},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                data={"id": song_id, "md5": md5},
                headers={"Authorization": f"Bearer {token}"}, timeout=60)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        pic = video.get('pic', '')
        if pic and not pic.startswith('http'): pic = 'https:' + pic
        cover_ok = False
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if pic:
            try:
                img = requests.get(pic, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                if len(img.content) > 5000:
                    with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as tf:
                        tf.write(img.content); tmp = tf.name
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    with open(tmp, 'rb') as f:
                        r = requests.post(f"{GATEWAY}/api/music/cover/song",
                            files={"avatarFile": ("cover.jpg", f, "image/jpeg")},
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                            data={"id": song_id, "md5": file_md5(tmp)},
                            headers={"Authorization": f"Bearer {token}"}, timeout=30)
                    cover_ok = r.json().get('passed', False)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    os.remove(tmp)
            except: pass

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        orig, trans = get_netease_lyrics(artist, title)
        lyrics_ok = False
        if orig:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            with tempfile.NamedTemporaryFile(mode='w', suffix='.lrc', delete=False, encoding='utf-8') as f:
                f.write(orig); tmp = f.name
            with open(tmp, 'rb') as f:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                r = requests.post(f"{GATEWAY}/api/music/lyrics/{song_id}",
                    files={"lyricsFile": ("lyrics.lrc", f, "text/plain")},
                    data={"msg": "ja"}, headers={"Authorization": f"Bearer {token}"}, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            lyrics_ok = r.json().get('passed', False)
            os.remove(tmp)
            if trans:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                with tempfile.NamedTemporaryFile(mode='w', suffix='.lrc', delete=False, encoding='utf-8') as f:
                    f.write(trans); tmp = f.name
                with open(tmp, 'rb') as f:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    requests.post(f"{GATEWAY}/api/music/lyrics/{song_id}",
                        files={"lyricsFile": ("lyrics.lrc", f, "text/plain")},
                        data={"msg": "zh"}, headers={"Authorization": f"Bearer {token}"}, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                os.remove(tmp)

        imported_ids.append(song_id)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        results.append({"artist": artist, "title": title, "song_id": song_id,
                        "cover": cover_ok, "lyrics": lyrics_ok})
        ok(f"  Done: id={song_id} cover={'OK' if cover_ok else 'SKIP'} lyrics={'OK' if lyrics_ok else 'SKIP'}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        time.sleep(3)

    if imported_ids:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        requests.post(f"{GATEWAY}/api/music/search/sync",
            headers={"Authorization": f"Bearer {token}"}, timeout=30)
        ids_str = ','.join(str(i) for i in imported_ids)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        script = os.path.join(PROJECT_ROOT, 'agent-playbooks/song-tagging/batch_tag.py')
        subprocess.run(['python3', script, f'--song-ids={ids_str}'],
                       capture_output=True, text=True, cwd=PROJECT_ROOT, timeout=300)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        requests.post(f"{RECOMMEND_API}/songs/rebuild-all", timeout=30)

    # ── Post-import quality gate ──
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if imported_ids and not args.dry_run:
        info("Running post-import quality gate...")
        try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            rec_resp = requests.get(f"{RECOMMEND_API}/daily",
                params={"userId": args.user_id, "limit": 200}, timeout=15)
            rec_data = rec_resp.json().get('data', [])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            rec_map = {item['songId']: item['similarity'] for item in rec_data}
        except Exception as e:
            warn(f"  Could not fetch recommendations: {e}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            rec_map = {}

        kept, removed = [], []
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for r in results:
            sid = r['song_id']
            actual_sim = rec_map.get(sid, 0)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            r['actual_similarity'] = actual_sim
            if actual_sim < args.final_threshold:
                warn(f"  BELOW THRESHOLD: {r['artist']} - {r['title']} "
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                     f"(sim={actual_sim:.4f} < {args.final_threshold})")
                db_query(f"UPDATE songs SET status = 0 WHERE id = {sid};")
                removed.append(r)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            else:
                ok(f"  PASS: {r['artist']} - {r['title']} (sim={actual_sim:.4f})")
                kept.append(r)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        if removed:
            info(f"Removed {len(removed)} songs below final threshold ({args.final_threshold})")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    print(f"\n{'='*60}")
    kept_results = [r for r in results if r.get('actual_similarity', 0) >= args.final_threshold]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    removed_results = [r for r in results if r.get('actual_similarity', 0) < args.final_threshold]
    print(f"  Imported and kept: {len(kept_results)} songs")
    for r in kept_results:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"  ✓ {r['artist']} - {r['title']} (id={r['song_id']}, sim={r.get('actual_similarity',0):.4f})")
    if removed_results:
        print(f"  Removed (below {args.final_threshold} threshold): {len(removed_results)} songs")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for r in removed_results:
            print(f"  ✗ {r['artist']} - {r['title']} (id={r['song_id']}, sim={r.get('actual_similarity',0):.4f})")
    print(f"{'='*60}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    with open(os.path.join(out_dir, 'discover_results.json'), 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


if __name__ == "__main__":
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    main()
