#!/usr/bin/env python3
"""
Music downloader — Kuwo source.
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! Agent 使用须知：                                              !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!   - 仅适用于中文歌曲，日文歌曲不可用（酷我日文库覆盖率极低）  !!
!!   - 下载后 Agent 必须自主验证：                               !!
!!     1. mutagen 检查时长，与预期对比（偏差 >30s 必须换源）     !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!     2. 确认不是 30s 试听片段（付费限制）                      !!
!!     3. 确认文件 bitrate ≥ 128kbps                            !!
!!   - 验证不通过不许上传到系统                                   !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

Usage:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    python3 music-download.py "周杰伦" --studio-only
"""

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import os
import re
import json
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import time
import argparse
import requests
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
from concurrent.futures import ThreadPoolExecutor, as_completed


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
# ────── filtering ──────

EXCLUDE_TITLE_PATTERNS = re.compile(
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r"(?i)"
    r"(\blive\b|演唱会|现场|concert|巡演|巡回|小巨蛋|工体|鸟巢|饭拍)"
    r"|(伴奏|instrumental|karaoke|kara|off vocal)"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r"|(纯音乐|bgm\b|钢琴版)"
    r"|(\bcover\b|翻唱|翻弹)"
    r"|(\bdemo\b|试听)"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r"|(串烧|medley)"
    r"|(铃声|铃声版|玲声)"
    r"|(\bdj\b.*remix|remix\b.*dj)"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r"|(\+.*\+)"
)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
EXCLUDE_MULTI_ARTIST = re.compile(r"[&＆/、]")


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def is_studio_track(song: dict, solo_artist: str) -> bool:
    """Return True if the song looks like a solo studio recording."""
    title = song.get("title", "")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    artist = song.get("artist", "")

    if EXCLUDE_TITLE_PATTERNS.search(title):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return False

    if EXCLUDE_MULTI_ARTIST.search(artist):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return False

    if solo_artist and solo_artist not in artist:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return False

    return True
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


# ────── search ──────
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def search_kuwo(keyword: str, page: int = 0, page_size: int = 30) -> list:
    url = "https://search.kuwo.cn/r.s"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    params = {
        "all": keyword,
        "ft": "music",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        "rformat": "json",
        "encoding": "utf8",
        "pn": page,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        "rn": page_size,
    }
    r = requests.get(url, params=params, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r.raise_for_status()
    text = r.text.replace("'", '"')
    data = json.loads(text)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    raw_list = data.get("abslist", [])
    songs = []
    for item in raw_list:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        rid = (item.get("MUSICRID") or "").replace("MUSIC_", "")
        if not rid:
            continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        songs.append({
            "rid": rid,
            "title": _clean_html(item.get("NAME", "")),
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            "artist": _clean_html(item.get("ARTIST", "")),
            "album": _clean_html(item.get("ALBUM", "")),
            "duration": int(item.get("DURATION", 0)),
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        })
    return songs

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def _clean_html(s: str) -> str:
    s = s.replace("\\u0026", "&").replace("&nbsp;", " ").replace("&amp;", "&")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    s = re.sub(r"&[a-zA-Z]+;|&#\d+;", " ", s)
    return s.strip()

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

# ────── resolve download URL (with Content-Length pre-check) ──────

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
MIN_FULL_SIZE = 500 * 1024  # 500KB — anything below is a trial snippet

def get_download_url(rid: str):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    api = f"https://antiserver.kuwo.cn/anti.s?type=convert_url3&rid={rid}&format=mp3"
    try:
        r = requests.get(api, headers={"User-Agent": "okhttp/3.10.0"}, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        data = r.json()
        dl = data.get("url", "")
        if not dl or not dl.startswith("http"):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            return None, 0

        head = requests.head(dl, headers={"User-Agent": "Mozilla/5.0"}, timeout=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        cl = int(head.headers.get("Content-Length", 0))
        return dl, cl
    except Exception:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return None, 0


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
# ────── file helpers ──────

def safe_name(name: str, max_len: int = 140) -> str:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    name = re.sub(r'[\\/:*?"<>|]', "_", (name or "").strip())
    name = re.sub(r"\s+", " ", name)
    return name[:max_len].rstrip(" .") or "unknown"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def choose_path(out_dir: str, title: str, artist: str, rid: str) -> str:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    stem = safe_name(f"{artist} - {title}" if artist else title)
    p = os.path.join(out_dir, f"{stem}.mp3")
    if not os.path.exists(p):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return p
    return os.path.join(out_dir, f"{stem} ({rid}).mp3")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def is_mp3_header(b: bytes) -> bool:
    if len(b) < 3:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return False
    if b.startswith(b"ID3"):
        return True
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return b[0] == 0xFF and (b[1] & 0xE0) == 0xE0


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
# ────── download ──────

def download_one(song: dict, out_dir: str, overwrite: bool = False, retries: int = 3):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    rid = song["rid"]
    title = song["title"]
    artist = song["artist"]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    tag = f"[{rid}] {artist} - {title}"

    dl_url, content_length = get_download_url(rid)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not dl_url:
        return tag, "NO_URL", None

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if content_length < MIN_FULL_SIZE:
        return tag, f"SKIP_TRIAL ({content_length // 1024}KB)", None

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    path = choose_path(out_dir, title, artist, rid)
    if os.path.exists(path) and not overwrite:
        return tag, "SKIP_EXISTS", None
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    tmp = path + ".tmp"
    headers = {"User-Agent": "Mozilla/5.0", "Accept": "*/*"}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    last_err = None

    for attempt in range(1, retries + 1):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        try:
            if os.path.exists(tmp):
                os.remove(tmp)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

            with requests.get(dl_url, headers=headers, stream=True, timeout=30, allow_redirects=True) as r:
                if r.status_code != 200:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    raise RuntimeError(f"HTTP_{r.status_code}")

                first = r.raw.read(4096)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                if not is_mp3_header(first):
                    ct = (r.headers.get("Content-Type") or "").lower()
                    raise RuntimeError(f"NOT_MP3 content-type={ct}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

                wrote = len(first)
                with open(tmp, "wb") as f:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    f.write(first)
                    for chunk in r.iter_content(chunk_size=256 * 1024):
                        if chunk:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                            f.write(chunk)
                            wrote += len(chunk)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            os.replace(tmp, path)
            return tag, f"OK {wrote / 1024 / 1024:.2f}MB", None

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except Exception as e:
            last_err = e
            time.sleep(0.5 * attempt)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if os.path.exists(tmp):
        try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            os.remove(tmp)
        except OSError:
            pass
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    return tag, f"FAIL {last_err}", dl_url

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

# ────── main ──────

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def main():
    parser = argparse.ArgumentParser(description="Music downloader (Kuwo source)")
    parser.add_argument("keyword", nargs="?", default="周杰伦", help="搜索关键词")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser.add_argument("--pages", type=int, default=3, help="搜索页数 (default: 3)")
    parser.add_argument("--per-page", type=int, default=30, help="每页数量 (default: 30)")
    parser.add_argument("--workers", type=int, default=4, help="并发下载线程数 (default: 4)")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser.add_argument("--overwrite", action="store_true", help="覆盖已存在的文件")
    parser.add_argument("--out", default=None, help="输出目录 (default: ./download/<keyword>)")
    parser.add_argument("--studio-only", action="store_true",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                        help="仅保留录音室版本（过滤 Live/合唱/伴奏/纯音乐/翻唱）")
    args = parser.parse_args()

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    base_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir = args.out or os.path.join(base_dir, "download", safe_name(args.keyword))

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    all_songs = []
    seen_rids = set()
    seen_titles = set()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    for page in range(args.pages):
        songs = search_kuwo(args.keyword, page=page, page_size=args.per_page)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"page {page + 1}: {len(songs)} songs")
        if not songs:
            break
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for s in songs:
            if s["rid"] in seen_rids:
                continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            seen_rids.add(s["rid"])

            if args.studio_only and not is_studio_track(s, args.keyword):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                print(f"  [filter] {s['artist']} - {s['title']}")
                continue

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            title_key = s["title"].lower().strip()
            if title_key in seen_titles:
                print(f"  [dedup]  {s['artist']} - {s['title']}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                continue
            seen_titles.add(title_key)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            all_songs.append(s)

    os.makedirs(out_dir, exist_ok=True)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    with open(os.path.join(out_dir, "songs_meta.json"), "w", encoding="utf-8") as f:
        json.dump(all_songs, f, ensure_ascii=False, indent=2)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    print(f"\nafter filter: {len(all_songs)} unique studio tracks")
    print(f"download dir: {out_dir}\n")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    stats = {"OK": 0, "SKIP_EXISTS": 0, "SKIP_TRIAL": 0, "NO_URL": 0, "FAIL": 0}

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        futures = [ex.submit(download_one, s, out_dir, args.overwrite) for s in all_songs]
        for fu in as_completed(futures):
            tag, status, fail_url = fu.result()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            bucket = status.split()[0]
            stats[bucket] = stats.get(bucket, 0) + 1
            line = f"  {tag} -> {status}"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if fail_url:
                line += f" | {fail_url}"
            print(line)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    print(f"\n{'='*50}")
    print(f"  Total candidates: {len(all_songs)}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for k, v in stats.items():
        if v > 0:
            print(f"  {k:14s}: {v}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    print(f"{'='*50}")


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
if __name__ == "__main__":
    main()
