#!/usr/bin/env python3
"""
Music downloader — Kuwo source.

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! Agent 使用须知：                                              !!
!!   - 仅适用于中文歌曲，日文歌曲不可用（酷我日文库覆盖率极低）  !!
!!   - 下载后 Agent 必须自主验证：                               !!
!!     1. mutagen 检查时长，与预期对比（偏差 >30s 必须换源）     !!
!!     2. 确认不是 30s 试听片段（付费限制）                      !!
!!     3. 确认文件 bitrate ≥ 128kbps                            !!
!!   - 验证不通过不许上传到系统                                   !!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

Usage:
    python3 music-download.py "周杰伦" --studio-only
"""

import os
import re
import json
import time
import argparse
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed


# ────── filtering ──────

EXCLUDE_TITLE_PATTERNS = re.compile(
    r"(?i)"
    r"(\blive\b|演唱会|现场|concert|巡演|巡回|小巨蛋|工体|鸟巢|饭拍)"
    r"|(伴奏|instrumental|karaoke|kara|off vocal)"
    r"|(纯音乐|bgm\b|钢琴版)"
    r"|(\bcover\b|翻唱|翻弹)"
    r"|(\bdemo\b|试听)"
    r"|(串烧|medley)"
    r"|(铃声|铃声版|玲声)"
    r"|(\bdj\b.*remix|remix\b.*dj)"
    r"|(\+.*\+)"
)

EXCLUDE_MULTI_ARTIST = re.compile(r"[&＆/、]")


def is_studio_track(song: dict, solo_artist: str) -> bool:
    """Return True if the song looks like a solo studio recording."""
    title = song.get("title", "")
    artist = song.get("artist", "")

    if EXCLUDE_TITLE_PATTERNS.search(title):
        return False

    if EXCLUDE_MULTI_ARTIST.search(artist):
        return False

    if solo_artist and solo_artist not in artist:
        return False

    return True


# ────── search ──────

def search_kuwo(keyword: str, page: int = 0, page_size: int = 30) -> list:
    url = "https://search.kuwo.cn/r.s"
    params = {
        "all": keyword,
        "ft": "music",
        "rformat": "json",
        "encoding": "utf8",
        "pn": page,
        "rn": page_size,
    }
    r = requests.get(url, params=params, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
    r.raise_for_status()
    text = r.text.replace("'", '"')
    data = json.loads(text)
    raw_list = data.get("abslist", [])
    songs = []
    for item in raw_list:
        rid = (item.get("MUSICRID") or "").replace("MUSIC_", "")
        if not rid:
            continue
        songs.append({
            "rid": rid,
            "title": _clean_html(item.get("NAME", "")),
            "artist": _clean_html(item.get("ARTIST", "")),
            "album": _clean_html(item.get("ALBUM", "")),
            "duration": int(item.get("DURATION", 0)),
        })
    return songs


def _clean_html(s: str) -> str:
    s = s.replace("\\u0026", "&").replace("&nbsp;", " ").replace("&amp;", "&")
    s = re.sub(r"&[a-zA-Z]+;|&#\d+;", " ", s)
    return s.strip()


# ────── resolve download URL (with Content-Length pre-check) ──────

MIN_FULL_SIZE = 500 * 1024  # 500KB — anything below is a trial snippet

def get_download_url(rid: str):
    api = f"https://antiserver.kuwo.cn/anti.s?type=convert_url3&rid={rid}&format=mp3"
    try:
        r = requests.get(api, headers={"User-Agent": "okhttp/3.10.0"}, timeout=10)
        data = r.json()
        dl = data.get("url", "")
        if not dl or not dl.startswith("http"):
            return None, 0

        head = requests.head(dl, headers={"User-Agent": "Mozilla/5.0"}, timeout=10)
        cl = int(head.headers.get("Content-Length", 0))
        return dl, cl
    except Exception:
        return None, 0


# ────── file helpers ──────

def safe_name(name: str, max_len: int = 140) -> str:
    name = re.sub(r'[\\/:*?"<>|]', "_", (name or "").strip())
    name = re.sub(r"\s+", " ", name)
    return name[:max_len].rstrip(" .") or "unknown"


def choose_path(out_dir: str, title: str, artist: str, rid: str) -> str:
    stem = safe_name(f"{artist} - {title}" if artist else title)
    p = os.path.join(out_dir, f"{stem}.mp3")
    if not os.path.exists(p):
        return p
    return os.path.join(out_dir, f"{stem} ({rid}).mp3")


def is_mp3_header(b: bytes) -> bool:
    if len(b) < 3:
        return False
    if b.startswith(b"ID3"):
        return True
    return b[0] == 0xFF and (b[1] & 0xE0) == 0xE0


# ────── download ──────

def download_one(song: dict, out_dir: str, overwrite: bool = False, retries: int = 3):
    rid = song["rid"]
    title = song["title"]
    artist = song["artist"]
    tag = f"[{rid}] {artist} - {title}"

    dl_url, content_length = get_download_url(rid)
    if not dl_url:
        return tag, "NO_URL", None

    if content_length < MIN_FULL_SIZE:
        return tag, f"SKIP_TRIAL ({content_length // 1024}KB)", None

    path = choose_path(out_dir, title, artist, rid)
    if os.path.exists(path) and not overwrite:
        return tag, "SKIP_EXISTS", None

    tmp = path + ".tmp"
    headers = {"User-Agent": "Mozilla/5.0", "Accept": "*/*"}
    last_err = None

    for attempt in range(1, retries + 1):
        try:
            if os.path.exists(tmp):
                os.remove(tmp)

            with requests.get(dl_url, headers=headers, stream=True, timeout=30, allow_redirects=True) as r:
                if r.status_code != 200:
                    raise RuntimeError(f"HTTP_{r.status_code}")

                first = r.raw.read(4096)
                if not is_mp3_header(first):
                    ct = (r.headers.get("Content-Type") or "").lower()
                    raise RuntimeError(f"NOT_MP3 content-type={ct}")

                wrote = len(first)
                with open(tmp, "wb") as f:
                    f.write(first)
                    for chunk in r.iter_content(chunk_size=256 * 1024):
                        if chunk:
                            f.write(chunk)
                            wrote += len(chunk)

            os.replace(tmp, path)
            return tag, f"OK {wrote / 1024 / 1024:.2f}MB", None

        except Exception as e:
            last_err = e
            time.sleep(0.5 * attempt)

    if os.path.exists(tmp):
        try:
            os.remove(tmp)
        except OSError:
            pass

    return tag, f"FAIL {last_err}", dl_url


# ────── main ──────

def main():
    parser = argparse.ArgumentParser(description="Music downloader (Kuwo source)")
    parser.add_argument("keyword", nargs="?", default="周杰伦", help="搜索关键词")
    parser.add_argument("--pages", type=int, default=3, help="搜索页数 (default: 3)")
    parser.add_argument("--per-page", type=int, default=30, help="每页数量 (default: 30)")
    parser.add_argument("--workers", type=int, default=4, help="并发下载线程数 (default: 4)")
    parser.add_argument("--overwrite", action="store_true", help="覆盖已存在的文件")
    parser.add_argument("--out", default=None, help="输出目录 (default: ./download/<keyword>)")
    parser.add_argument("--studio-only", action="store_true",
                        help="仅保留录音室版本（过滤 Live/合唱/伴奏/纯音乐/翻唱）")
    args = parser.parse_args()

    base_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir = args.out or os.path.join(base_dir, "download", safe_name(args.keyword))

    all_songs = []
    seen_rids = set()
    seen_titles = set()

    for page in range(args.pages):
        songs = search_kuwo(args.keyword, page=page, page_size=args.per_page)
        print(f"page {page + 1}: {len(songs)} songs")
        if not songs:
            break
        for s in songs:
            if s["rid"] in seen_rids:
                continue
            seen_rids.add(s["rid"])

            if args.studio_only and not is_studio_track(s, args.keyword):
                print(f"  [filter] {s['artist']} - {s['title']}")
                continue

            title_key = s["title"].lower().strip()
            if title_key in seen_titles:
                print(f"  [dedup]  {s['artist']} - {s['title']}")
                continue
            seen_titles.add(title_key)

            all_songs.append(s)

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "songs_meta.json"), "w", encoding="utf-8") as f:
        json.dump(all_songs, f, ensure_ascii=False, indent=2)

    print(f"\nafter filter: {len(all_songs)} unique studio tracks")
    print(f"download dir: {out_dir}\n")

    stats = {"OK": 0, "SKIP_EXISTS": 0, "SKIP_TRIAL": 0, "NO_URL": 0, "FAIL": 0}

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = [ex.submit(download_one, s, out_dir, args.overwrite) for s in all_songs]
        for fu in as_completed(futures):
            tag, status, fail_url = fu.result()
            bucket = status.split()[0]
            stats[bucket] = stats.get(bucket, 0) + 1
            line = f"  {tag} -> {status}"
            if fail_url:
                line += f" | {fail_url}"
            print(line)

    print(f"\n{'='*50}")
    print(f"  Total candidates: {len(all_songs)}")
    for k, v in stats.items():
        if v > 0:
            print(f"  {k:14s}: {v}")
    print(f"{'='*50}")


if __name__ == "__main__":
    main()
