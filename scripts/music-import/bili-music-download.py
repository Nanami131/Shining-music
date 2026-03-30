#!/usr/bin/env python3
"""
Bilibili music extractor — smart search + download + extract audio.

Usage:
    python3 bili-music-download.py "周杰伦"
    python3 bili-music-download.py "五月天" --count 10
"""

import os
import re
import json
import time
import argparse
import subprocess
import requests

FFMPEG = os.path.expanduser(
    "~/.local/lib/python3.8/site-packages/imageio_ffmpeg/binaries/ffmpeg-linux64-v4.2.2"
)
YTDLP = os.path.expanduser("~/.local/bin/yt-dlp")

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://search.bilibili.com/",
}

# Jay Chou's well-known studio songs — used as a starting point for search
# Expand as needed for other artists
JAY_CHOU_SONGS = [
    "晴天", "七里香", "稻香", "青花瓷", "菊花台", "东风破", "夜曲",
    "以父之名", "双截棍", "龙卷风", "简单爱", "安静", "开不了口",
    "半岛铁盒", "退后", "搁浅", "发如雪", "霍元甲", "黑色毛衣",
    "花海", "蜗牛", "听妈妈的话", "本草纲目", "红尘客栈", "告白气球",
    "Mojito", "一路向北", "珊瑚海", "蒲公英的约定", "说了再见",
    "不能说的秘密", "烟花易冷", "我的地盘", "威廉古堡", "三年二班",
    "止战之殇", "忍者", "外婆", "爱在西元前", "可爱女人",
    "等你下课", "最长的电影", "说好的幸福呢", "给我一首歌的时间",
    "兰亭序", "甜甜的", "星晴", "暗号", "黄金甲", "千里之外",
]

EXCLUDE_TITLE_RE = re.compile(
    r"(?i)"
    r"(\blive\b|演唱会|现场|concert|巡演|小巨蛋|翻唱|cover|教学|教程|钢琴版"
    r"|吉他|鼓谱|指弹|伴奏|instrumental|karaoke|反应|reaction|混剪|合集"
    r"|AI|鬼畜|恶搞|搞笑|模仿|手势舞|舞蹈|choreography)"
)


def search_bilibili(keyword, page=1, pagesize=10, retries=3):
    url = "https://api.bilibili.com/x/web-interface/search/type"
    params = {
        "search_type": "video",
        "keyword": keyword,
        "page": page,
        "pagesize": pagesize,
        "order": "totalrank",
    }
    for attempt in range(retries):
        try:
            r = requests.get(url, params=params, headers=HEADERS, timeout=15)
            if r.status_code == 412:
                time.sleep(3 + attempt * 2)
                continue
            data = r.json()
            if data.get("code") != 0:
                return []
            return data.get("data", {}).get("result", []) or []
        except Exception:
            time.sleep(2 + attempt * 2)
    return []


def parse_duration(dur_str):
    """Parse "4:29" to seconds."""
    if not dur_str:
        return 0
    parts = str(dur_str).split(":")
    if len(parts) == 2:
        return int(parts[0]) * 60 + int(parts[1])
    if len(parts) == 3:
        return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    return 0


def clean_html(s):
    return re.sub(r"<[^>]+>", "", s or "")


def score_video(video, song_title, artist):
    """Score a Bilibili video for relevance. Higher = better."""
    title = clean_html(video.get("title", ""))
    author = video.get("author", "")
    play = video.get("play", 0)
    dur = parse_duration(video.get("duration"))

    if EXCLUDE_TITLE_RE.search(title):
        return -1

    score = 0

    if song_title in title:
        score += 50

    if artist in title:
        score += 20

    official_keywords = ["MV", "官方", "高音质", "HD", "HQ", "高清"]
    for kw in official_keywords:
        if kw.lower() in title.lower():
            score += 15

    if 120 <= dur <= 420:
        score += 30
    elif 60 <= dur <= 600:
        score += 10
    else:
        score -= 20

    if play > 1000000:
        score += 40
    elif play > 100000:
        score += 25
    elif play > 10000:
        score += 10

    collab_re = re.compile(r"[&＆×x]|feat|合唱|合作")
    if collab_re.search(title):
        score -= 30

    return score


def find_best_video(song_title, artist):
    """Search Bilibili and return the best matching video."""
    queries = [
        f"{artist} {song_title} MV",
        f"{artist} {song_title} 官方",
        f"{artist} {song_title}",
    ]

    best = None
    best_score = -1

    for q in queries:
        results = search_bilibili(q, pagesize=10)
        for v in results:
            s = score_video(v, song_title, artist)
            if s > best_score:
                best_score = s
                best = v
        if best_score >= 80:
            break
        time.sleep(1.5)

    return best, best_score


def download_audio(bvid, out_path):
    """Download video from Bilibili and extract audio as MP3."""
    url = f"https://www.bilibili.com/video/{bvid}"
    cmd = [
        YTDLP,
        "--ffmpeg-location", FFMPEG,
        "-x", "--audio-format", "mp3", "--audio-quality", "0",
        "--user-agent", HEADERS["User-Agent"],
        "--referer", "https://www.bilibili.com/",
        "-o", out_path,
        "--no-playlist",
        url,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    return result.returncode == 0, result.stderr


def safe_name(name, max_len=140):
    name = re.sub(r'[\\/:*?"<>|]', "_", (name or "").strip())
    name = re.sub(r"\s+", " ", name)
    return name[:max_len].rstrip(" .") or "unknown"


def main():
    parser = argparse.ArgumentParser(description="Bilibili music extractor")
    parser.add_argument("artist", nargs="?", default="周杰伦", help="歌手名")
    parser.add_argument("--count", type=int, default=20, help="目标下载数量 (default: 20)")
    parser.add_argument("--out", default=None, help="输出目录")
    parser.add_argument("--songs", default=None, help="自定义歌曲列表文件（每行一首）")
    args = parser.parse_args()

    base_dir = os.path.dirname(os.path.abspath(__file__))
    out_dir = args.out or os.path.join(base_dir, "download", safe_name(args.artist) + "_bili")
    os.makedirs(out_dir, exist_ok=True)

    if args.songs:
        with open(args.songs, "r", encoding="utf-8") as f:
            song_list = [line.strip() for line in f if line.strip()]
    elif args.artist == "周杰伦":
        song_list = JAY_CHOU_SONGS
    else:
        print(f"No built-in song list for '{args.artist}'. Using Kuwo search to discover songs...")
        song_list = discover_songs_kuwo(args.artist, max_songs=args.count * 2)

    print(f"Artist: {args.artist}")
    print(f"Song candidates: {len(song_list)}")
    print(f"Target: {args.count} songs")
    print(f"Output: {out_dir}\n")

    downloaded = 0
    skipped = 0
    failed = 0
    results = []

    for song in song_list:
        if downloaded >= args.count:
            break

        fname = f"{safe_name(args.artist)} - {safe_name(song)}.mp3"
        out_path_template = os.path.join(out_dir, f"{safe_name(args.artist)} - {safe_name(song)}.%(ext)s")
        out_path_final = os.path.join(out_dir, fname)

        if os.path.exists(out_path_final):
            print(f"  SKIP_EXISTS: {fname}")
            skipped += 1
            downloaded += 1
            continue

        print(f"  Searching: {args.artist} - {song}...", end=" ", flush=True)
        video, score = find_best_video(song, args.artist)

        if not video or score < 30:
            print(f"NO_MATCH (score={score})")
            failed += 1
            results.append({"song": song, "status": "NO_MATCH", "score": score})
            continue

        bvid = video.get("bvid")
        vtitle = clean_html(video.get("title", ""))
        vplay = video.get("play", 0)
        vdur = video.get("duration", "?")

        print(f"found [{bvid}] \"{vtitle}\" ({vdur}, {vplay} plays, score={score})")

        ok, err = download_audio(bvid, out_path_template)
        if ok and os.path.exists(out_path_final):
            from mutagen.mp3 import MP3
            audio = MP3(out_path_final)
            dur = audio.info.length
            br = audio.info.bitrate // 1000
            fsize = os.path.getsize(out_path_final)
            print(f"    -> OK: {fsize // 1024}KB, {dur:.0f}s, {br}kbps")
            downloaded += 1
            results.append({
                "song": song, "status": "OK", "bvid": bvid,
                "file": fname, "duration": dur, "bitrate": br, "size": fsize,
            })
        else:
            print(f"    -> DOWNLOAD_FAIL")
            if err:
                for line in err.strip().split("\n")[-3:]:
                    print(f"       {line}")
            failed += 1
            results.append({"song": song, "status": "FAIL", "bvid": bvid})

        time.sleep(2)

    with open(os.path.join(out_dir, "download_log.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"\n{'='*60}")
    print(f"  Downloaded: {downloaded}")
    print(f"  Skipped:    {skipped}")
    print(f"  Failed:     {failed}")
    print(f"  Output:     {out_dir}")
    print(f"{'='*60}")


def discover_songs_kuwo(artist, max_songs=50):
    """Use Kuwo keyword search to discover song titles for an artist."""
    import json as _json
    songs = []
    seen = set()
    for page in range(5):
        try:
            r = requests.get("https://search.kuwo.cn/r.s", params={
                "all": artist, "ft": "music", "rformat": "json", "encoding": "utf8",
                "pn": page, "rn": 30,
            }, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
            text = r.text.replace("'", '"')
            data = _json.loads(text)
            for item in data.get("abslist", []):
                name = item.get("NAME", "").replace("&nbsp;", " ").strip()
                a = item.get("ARTIST", "")
                if artist in a and name and name not in seen and "+" not in name:
                    seen.add(name)
                    songs.append(name)
                    if len(songs) >= max_songs:
                        return songs
        except Exception:
            pass
    return songs


if __name__ == "__main__":
    main()
