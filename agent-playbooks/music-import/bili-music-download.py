#!/usr/bin/env python3
"""
Bilibili music extractor — smart search + download + extract audio.
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! 硬性限制：一次只能下载一首歌。--count 参数已被移除。              !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!                                                                !!
!! 原因：2026-04 批量导入 83 首歌导致：                              !!
!!   - 3 首完全错歌（搜索匹配到无关视频）                           !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!   - 5 首时长异常（TV Size / 翻唱 / 音游截取版）                  !!
!!   - 21 首语言误标                                                !!
!!   - 7 维标签缺失                                                 !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!                                                                !!
!! 正确流程（每首歌必须完整走完以下步骤）：                           !!
!!   1. 下载一首歌                                                  !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!   2. Agent 自主验证：                                            !!
!!      a. 用 mutagen 检查 MP3 时长，与预期值对比（偏差 >30s 报警） !!
!!      b. 用 ffprobe 确认音频流完整（无截断/无静音尾巴）            !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!      c. 比对 Bilibili 视频标题是否包含正确歌手名+歌曲名          !!
!!      d. 检查文件大小是否合理（128kbps×duration ≈ filesize）       !!
!!   3. 验证全部通过 → 上传 → 更新 DB duration → 下一首             !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!   4. 任何一项验证失败 → 停止，换搜索关键词重新下载               !!
!!                                                                !!
!! Agent 禁止跳过验证步骤。"下载成功"≠"音频正确"。                   !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!! 如需批量导入，请使用 external_discover.py (MAX_BATCH=1)。        !!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
Usage:
    python3 bili-music-download.py "周杰伦" --song "晴天"
"""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

import os
import re
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import json
import time
import argparse
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import subprocess
import requests

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
FFMPEG = os.path.expanduser(
    "~/.local/lib/python3.8/site-packages/imageio_ffmpeg/binaries/ffmpeg-linux64-v4.2.2"
)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
YTDLP = os.path.expanduser("~/.local/bin/yt-dlp")

HEADERS = {
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://search.bilibili.com/",
}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

# Jay Chou's well-known studio songs — used as a starting point for search
# Expand as needed for other artists
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
JAY_CHOU_SONGS = [
    "晴天", "七里香", "稻香", "青花瓷", "菊花台", "东风破", "夜曲",
    "以父之名", "双截棍", "龙卷风", "简单爱", "安静", "开不了口",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    "半岛铁盒", "退后", "搁浅", "发如雪", "霍元甲", "黑色毛衣",
    "花海", "蜗牛", "听妈妈的话", "本草纲目", "红尘客栈", "告白气球",
    "Mojito", "一路向北", "珊瑚海", "蒲公英的约定", "说了再见",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    "不能说的秘密", "烟花易冷", "我的地盘", "威廉古堡", "三年二班",
    "止战之殇", "忍者", "外婆", "爱在西元前", "可爱女人",
    "等你下课", "最长的电影", "说好的幸福呢", "给我一首歌的时间",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    "兰亭序", "甜甜的", "星晴", "暗号", "黄金甲", "千里之外",
]

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
EXCLUDE_TITLE_RE = re.compile(
    r"(?i)"
    r"(\blive\b|演唱会|现场|concert|巡演|小巨蛋|翻唱|cover|教学|教程|钢琴版"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    r"|吉他|鼓谱|指弹|伴奏|instrumental|karaoke|反应|reaction|混剪|合集"
    r"|AI|鬼畜|恶搞|搞笑|模仿|手势舞|舞蹈|choreography)"
)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def search_bilibili(keyword, page=1, pagesize=10, retries=3):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    url = "https://api.bilibili.com/x/web-interface/search/type"
    params = {
        "search_type": "video",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        "keyword": keyword,
        "page": page,
        "pagesize": pagesize,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        "order": "totalrank",
    }
    for attempt in range(retries):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        try:
            r = requests.get(url, params=params, headers=HEADERS, timeout=15)
            if r.status_code == 412:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                time.sleep(3 + attempt * 2)
                continue
            data = r.json()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            if data.get("code") != 0:
                return []
            return data.get("data", {}).get("result", []) or []
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except Exception:
            time.sleep(2 + attempt * 2)
    return []
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def parse_duration(dur_str):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    """Parse "4:29" to seconds."""
    if not dur_str:
        return 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parts = str(dur_str).split(":")
    if len(parts) == 2:
        return int(parts[0]) * 60 + int(parts[1])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if len(parts) == 3:
        return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
    return 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def clean_html(s):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return re.sub(r"<[^>]+>", "", s or "")


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def score_video(video, song_title, artist):
    """Score a Bilibili video for relevance. Higher = better."""
    title = clean_html(video.get("title", ""))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    author = video.get("author", "")
    play = video.get("play", 0)
    dur = parse_duration(video.get("duration"))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if EXCLUDE_TITLE_RE.search(title):
        return -1
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    score = 0

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if song_title in title:
        score += 50

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if artist in title:
        score += 20

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    official_keywords = ["MV", "官方", "高音质", "HD", "HQ", "高清"]
    for kw in official_keywords:
        if kw.lower() in title.lower():
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            score += 15

    if 120 <= dur <= 420:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        score += 30
    elif 60 <= dur <= 600:
        score += 10
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    else:
        score -= 20

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if play > 1000000:
        score += 40
    elif play > 100000:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        score += 25
    elif play > 10000:
        score += 10
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    collab_re = re.compile(r"[&＆×x]|feat|合唱|合作")
    if collab_re.search(title):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        score -= 30

    return score
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def find_best_video(song_title, artist):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    """Search Bilibili and return the best matching video."""
    queries = [
        f"{artist} {song_title} MV",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        f"{artist} {song_title} 官方",
        f"{artist} {song_title}",
    ]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    best = None
    best_score = -1
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    for q in queries:
        results = search_bilibili(q, pagesize=10)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        for v in results:
            s = score_video(v, song_title, artist)
            if s > best_score:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                best_score = s
                best = v
        if best_score >= 80:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            break
        time.sleep(1.5)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    return best, best_score


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def download_audio(bvid, out_path):
    """Download video from Bilibili and extract audio as MP3."""
    url = f"https://www.bilibili.com/video/{bvid}"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    cmd = [
        YTDLP,
        "--ffmpeg-location", FFMPEG,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        "-x", "--audio-format", "mp3", "--audio-quality", "0",
        "--user-agent", HEADERS["User-Agent"],
        "--referer", "https://www.bilibili.com/",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        "-o", out_path,
        "--no-playlist",
        url,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    return result.returncode == 0, result.stderr
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def safe_name(name, max_len=140):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    name = re.sub(r'[\\/:*?"<>|]', "_", (name or "").strip())
    name = re.sub(r"\s+", " ", name)
    return name[:max_len].rstrip(" .") or "unknown"
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


def main():
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser = argparse.ArgumentParser(description="Bilibili music extractor (单首模式)")
    parser.add_argument("artist", help="歌手名")
    parser.add_argument("--song", required=True, help="歌曲名（必填，一次只能指定一首）")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    parser.add_argument("--out", default=None, help="输出目录")
    parser.add_argument("--expected-duration", type=int, default=0,
                        help="预期时长（秒），用于验证下载结果")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    args = parser.parse_args()

    base_dir = os.path.dirname(os.path.abspath(__file__))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    out_dir = args.out or os.path.join(base_dir, "download", safe_name(args.artist) + "_bili")
    os.makedirs(out_dir, exist_ok=True)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    song_list = [args.song]

    print(f"Artist: {args.artist}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    print(f"Song: {args.song}")
    print(f"Output: {out_dir}")
    if args.expected_duration:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"Expected duration: ~{args.expected_duration}s")
    print()

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    downloaded = 0
    skipped = 0
    failed = 0
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    results = []

    for song in song_list:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        fname = f"{safe_name(args.artist)} - {safe_name(song)}.mp3"
        out_path_template = os.path.join(out_dir, f"{safe_name(args.artist)} - {safe_name(song)}.%(ext)s")
        out_path_final = os.path.join(out_dir, fname)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        if os.path.exists(out_path_final):
            print(f"  SKIP_EXISTS: {fname}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            skipped += 1
            downloaded += 1
            continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        print(f"  Searching: {args.artist} - {song}...", end=" ", flush=True)
        video, score = find_best_video(song, args.artist)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        if not video or score < 30:
            print(f"NO_MATCH (score={score})")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            failed += 1
            results.append({"song": song, "status": "NO_MATCH", "score": score})
            continue
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        bvid = video.get("bvid")
        vtitle = clean_html(video.get("title", ""))
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        vplay = video.get("play", 0)
        vdur = video.get("duration", "?")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"found [{bvid}] \"{vtitle}\" ({vdur}, {vplay} plays, score={score})")

        ok, err = download_audio(bvid, out_path_template)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if ok and os.path.exists(out_path_final):
            from mutagen.mp3 import MP3
            audio = MP3(out_path_final)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            dur = audio.info.length
            br = audio.info.bitrate // 1000
            fsize = os.path.getsize(out_path_final)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            print(f"    -> OK: {fsize // 1024}KB, {dur:.0f}s, {br}kbps")

            if args.expected_duration > 0:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                diff = abs(dur - args.expected_duration)
                if diff > 30:
                    print(f"    !! 时长偏差 {diff:.0f}s（预期 ~{args.expected_duration}s，实际 {dur:.0f}s）")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    print(f"    !! 可能下载了错误版本（TV Size / 翻唱 / 音游截取），请人工确认")
                else:
                    print(f"    ✓ 时长验证通过（偏差 {diff:.0f}s）")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

            downloaded += 1
            results.append({
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                "song": song, "status": "OK", "bvid": bvid,
                "file": fname, "duration": dur, "bitrate": br, "size": fsize,
            })
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        else:
            print(f"    -> DOWNLOAD_FAIL")
            if err:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                for line in err.strip().split("\n")[-3:]:
                    print(f"       {line}")
            failed += 1
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            results.append({"song": song, "status": "FAIL", "bvid": bvid})

        time.sleep(2)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    with open(os.path.join(out_dir, "download_log.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    print(f"\n{'='*60}")
    print(f"  Downloaded: {downloaded}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    print(f"  Skipped:    {skipped}")
    print(f"  Failed:     {failed}")
    print(f"  Output:     {out_dir}")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    print(f"{'='*60}")


# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def discover_songs_kuwo(artist, max_songs=50):
    """Use Kuwo keyword search to discover song titles for an artist."""
    import json as _json
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    songs = []
    seen = set()
    for page in range(5):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        try:
            r = requests.get("https://search.kuwo.cn/r.s", params={
                "all": artist, "ft": "music", "rformat": "json", "encoding": "utf8",
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                "pn": page, "rn": 30,
            }, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
            text = r.text.replace("'", '"')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            data = _json.loads(text)
            for item in data.get("abslist", []):
                name = item.get("NAME", "").replace("&nbsp;", " ").strip()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                a = item.get("ARTIST", "")
                if artist in a and name and name not in seen and "+" not in name:
                    seen.add(name)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
                    songs.append(name)
                    if len(songs) >= max_songs:
                        return songs
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        except Exception:
            pass
    return songs
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!


if __name__ == "__main__":
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    main()
