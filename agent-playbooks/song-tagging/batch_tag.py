#!/usr/bin/env python3
"""
批量打标脚本：Language + Vocal + Mood + Audio
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! Agent 使用须知：                                              !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!   - 脚本输出 "OK" 不等于标签正确。Agent 必须自主验证：       !!
!!     1. SQL 查每首歌 tag 数量 = 28（6类 28 维全部填充）        !!
!!     2. Language 标签需要 Agent 上网搜索确认，不要信标题猜测    !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!     3. Mood/Audio 标签由分析器生成，Agent 需抽查合理性        !!
!!     4. Source 和 Era 标签不能全为 0                            !!
!!   - 打标完成后必须调用 /recommend/songs/rebuild-all 重建向量  !!
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
!!   - 重建后 Agent 必须 API 查询偏好向量确认非全零              !!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
用法: python3 batch_tag.py [--skip-audio] [--song-ids 3,4,5]
"""
import sys
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import os
import json
import subprocess
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
import urllib.request
import tempfile

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
API_BASE = "http://10.24.97.9:8085/recommend/songs"
DB_CMD = ['docker', 'exec', 'shining-mysql', 'mysql', '-u', 'root', '-ppassword',
          'shining-music', '--default-character-set=utf8mb4', '-N', '-e']
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.join(SCRIPT_DIR, '..', '..')

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def db_query(sql):
    result = subprocess.run(DB_CMD + [sql], capture_output=True, text=True)
    if result.returncode != 0:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return []
    lines = [l for l in result.stdout.strip().split('\n') if l]
    return [l.split('\t') for l in lines]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def put_tag(song_id, tag_id, value, confidence, source, evidence="{}"):
    url = f"{API_BASE}/{song_id}/tags/{tag_id}"
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
    except Exception as e:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"  ERROR: song={song_id} tag={tag_id}: {e}", file=sys.stderr)

def tag_language(song_id, title, singer_name):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    """基于标题字符集和歌手判断原始语言"""
    has_hiragana = any('\u3040' <= c <= '\u309f' for c in title)
    has_katakana = any('\u30a0' <= c <= '\u30ff' for c in title)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    has_cjk = any('\u4e00' <= c <= '\u9fff' for c in title)
    has_latin = any('a' <= c.lower() <= 'z' for c in title)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    zh_singers = {'周杰伦', '许嵩', '陈昕桐'}
    instrumental_singers = {'Christopher Larkin'}

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if singer_name in instrumental_singers:
        put_tag(song_id, 1, 0.0, 1.0, "metadata", '{"reason":"instrumental"}')
        put_tag(song_id, 2, 0.0, 1.0, "metadata", '{"reason":"instrumental"}')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 3, 0.0, 1.0, "metadata", '{"reason":"instrumental"}')
        put_tag(song_id, 4, 1.0, 1.0, "metadata", '{"reason":"Christopher Larkin game OST"}')
        return
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if singer_name in zh_singers:
        lang = 'zh'
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    elif has_hiragana or has_katakana:
        lang = 'ja'
    elif has_cjk and not has_latin:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        # CJK chars without kana - check singer
        if singer_name in zh_singers:
            lang = 'zh'
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        else:
            lang = 'ja'  # default for JP singers with kanji-only titles
    elif has_latin and not has_cjk:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        lang = 'en'
    else:
        lang = 'ja'  # default for mixed content from JP singers
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    put_tag(song_id, 1, 1.0 if lang == 'ja' else 0.0, 1.0, "title_charset")
    put_tag(song_id, 2, 1.0 if lang == 'zh' else 0.0, 1.0, "title_charset")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    put_tag(song_id, 3, 1.0 if lang == 'en' else 0.0, 1.0, "title_charset")
    put_tag(song_id, 4, 0.0, 1.0, "title_charset")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
def tag_vocal(song_id, sex, singer_name):
    """基于 singers.sex 判断声线"""
    vocaloid_singers = {'初音ミク'}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    vocaloid_producers = {'ユリイ・カノン'}

    if singer_name in vocaloid_singers:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 17, 0.0, 1.0, "singer_metadata")
        put_tag(song_id, 18, 0.0, 1.0, "singer_metadata")
        put_tag(song_id, 19, 1.0, 1.0, "singer_metadata", '{"reason":"Vocaloid singer"}')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return

    if singer_name in vocaloid_producers:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 17, 0.0, 1.0, "singer_metadata")
        put_tag(song_id, 18, 0.0, 1.0, "singer_metadata")
        put_tag(song_id, 19, 1.0, 1.0, "singer_metadata", '{"reason":"Vocaloid producer, songs use synth voice"}')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return

    if singer_name == 'Christopher Larkin':
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 17, 0.0, 1.0, "singer_metadata", '{"reason":"instrumental"}')
        put_tag(song_id, 18, 0.0, 1.0, "singer_metadata", '{"reason":"instrumental"}')
        put_tag(song_id, 19, 0.0, 1.0, "singer_metadata", '{"reason":"instrumental"}')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return

    sex = int(sex) if sex else -1
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    # YOASOBI(sex=2), μ's(sex=2), etc: groups with female vocals
    female_groups = {'YOASOBI', "μ's", '高嶺のなでしこ', '三月のパンタシア', 'V.W.P'}
    mixed_groups = {'R-ORANGE'}
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    if singer_name in female_groups:
        put_tag(song_id, 17, 0.0, 1.0, "singer_metadata")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 18, 1.0, 1.0, "singer_metadata", f'{{"reason":"{singer_name} female vocals"}}')
        put_tag(song_id, 19, 0.0, 1.0, "singer_metadata")
    elif singer_name in mixed_groups:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 17, 0.5, 0.8, "singer_metadata")
        put_tag(song_id, 18, 0.5, 0.8, "singer_metadata")
        put_tag(song_id, 19, 0.0, 1.0, "singer_metadata")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    elif sex == 0:
        put_tag(song_id, 17, 1.0, 1.0, "singer_metadata")
        put_tag(song_id, 18, 0.0, 1.0, "singer_metadata")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 19, 0.0, 1.0, "singer_metadata")
    elif sex == 1:
        put_tag(song_id, 17, 0.0, 1.0, "singer_metadata")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 18, 1.0, 1.0, "singer_metadata")
        put_tag(song_id, 19, 0.0, 1.0, "singer_metadata")
    else:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        put_tag(song_id, 17, 0.5, 0.6, "singer_metadata", '{"reason":"unknown sex"}')
        put_tag(song_id, 18, 0.5, 0.6, "singer_metadata", '{"reason":"unknown sex"}')
        put_tag(song_id, 19, 0.0, 0.8, "singer_metadata")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def tag_mood(song_id):
    """调用 analyze_mood.py 分析中文歌词"""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    rows = db_query(f"SELECT content FROM lyrics WHERE song_id = {song_id} AND language_msg = 'zh' LIMIT 1;")
    if not rows or not rows[0][0]:
        rows = db_query(f"SELECT content FROM lyrics WHERE song_id = {song_id} LIMIT 1;")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not rows or not rows[0][0]:
        print(f"  [mood] song {song_id}: no lyrics, skipping", file=sys.stderr)
        return
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    lyrics = rows[0][0]
    script = os.path.join(SCRIPT_DIR, 'analyze_mood.py')
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    result = subprocess.run(
        ['python3', script, lyrics],
        capture_output=True, text=True, cwd=PROJECT_ROOT
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    )
    if result.returncode != 0:
        print(f"  [mood] song {song_id}: script failed: {result.stderr[:200]}", file=sys.stderr)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return

    try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        output = json.loads(result.stdout)
    except:
        print(f"  [mood] song {song_id}: invalid JSON output", file=sys.stderr)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return

    scores = output.get('scores', {})
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    hit_count = output.get('hit_count', 0)
    hit_rate = output.get('hit_rate', 0)
    top_words = output.get('top_words', [])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    tag_map = {
        'valence': 27, 'arousal': 28, 'dominance': 29,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'joy': 30, 'anger': 31, 'sadness': 32, 'fear': 33, 'disgust': 34
    }

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    evidence = json.dumps({
        "hit_count": hit_count, "hit_rate": f"{hit_rate}%",
        "top_words": top_words[:5]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    }, ensure_ascii=False)

    conf = 0.85 if hit_rate > 10 else 0.6
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    for dim, tag_id in tag_map.items():
        val = scores.get(dim, 0.5)
        put_tag(song_id, tag_id, val, conf, "memolon_lexicon", evidence)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def tag_audio(song_id):
    """调用 analyze_audio.py 分析音频"""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    rows = db_query(f"SELECT file_url FROM songs WHERE id = {song_id};")
    if not rows or not rows[0][0] or rows[0][0] == 'NULL':
        print(f"  [audio] song {song_id}: no file_url, skipping", file=sys.stderr)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        return

    file_url = rows[0][0]
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    tmp_path = f"/tmp/song_{song_id}.mp3"

    try:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        subprocess.run(['curl', '-s', '-o', tmp_path, file_url],
                       capture_output=True, timeout=30)
    except:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"  [audio] song {song_id}: download failed", file=sys.stderr)
        return

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not os.path.exists(tmp_path) or os.path.getsize(tmp_path) < 1000:
        print(f"  [audio] song {song_id}: file too small or missing", file=sys.stderr)
        return
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    script = os.path.join(SCRIPT_DIR, 'analyze_audio.py')
    result = subprocess.run(
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        ['python3', script, tmp_path],
        capture_output=True, text=True, cwd=PROJECT_ROOT
    )
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    os.remove(tmp_path)

    if result.returncode != 0:
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print(f"  [audio] song {song_id}: script failed: {result.stderr[:200]}", file=sys.stderr)
        return

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    stdout_clean = '\n'.join(l for l in result.stdout.split('\n') if not l.strip().startswith('['))
    try:
        output = json.loads(stdout_clean)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    except:
        print(f"  [audio] song {song_id}: invalid JSON", file=sys.stderr)
        return
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    tag_map = {
        'tempo': 20, 'energy': 21, 'danceability': 22,
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        'acousticness': 23, 'valence': 24, 'speechiness': 25
    }
    raw = output.get('raw', {})
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

    for dim, tag_id in tag_map.items():
        val = output.get(dim, 0.5)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        evidence = json.dumps({"raw": raw.get(dim.replace('ness',''), raw)}, ensure_ascii=False)
        conf = 0.95 if dim in ('tempo', 'energy', 'danceability') else 0.85
        put_tag(song_id, tag_id, val, conf, "essentia_analysis", evidence)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

def main():
    skip_audio = '--skip-audio' in sys.argv
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    song_filter = None
    for arg in sys.argv[1:]:
        if arg.startswith('--song-ids='):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            song_filter = set(int(x) for x in arg.split('=')[1].split(','))
        elif arg.isdigit():
            song_filter = song_filter or set()
            song_filter.add(int(arg))

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not song_filter:
        print("错误：必须指定歌曲 ID！用法：python3 batch_tag.py <song_id> 或 --song-ids=1,2,3", file=sys.stderr)
        print("禁止不指定 ID 的全库扫描！", file=sys.stderr)
        sys.exit(1)

    rows = db_query("""
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        SELECT s.id, s.title, si.name, si.sex,
          (SELECT COUNT(*) FROM song_tags st WHERE st.song_id = s.id)
        FROM songs s LEFT JOIN singers si ON s.artist_id = si.id
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        WHERE s.status = 1 ORDER BY s.id;
    """)

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    songs = []
    for r in rows:
        sid = int(r[0])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        if sid not in song_filter:
            continue
        songs.append({
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            'id': sid, 'title': r[1], 'singer': r[2] or '', 'sex': r[3] or '0'
        })

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
    if not songs:
        print("未找到指定的歌曲，请检查 ID 是否正确", file=sys.stderr)
        sys.exit(1)

    print(f"处理 {len(songs)} 首指定歌曲")

    for i, s in enumerate(songs):
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        sid = s['id']
        print(f"\n[{i+1}/{len(songs)}] #{sid} {s['title']} ({s['singer']})")

# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
        print("  → Language + Vocal")
        tag_language(sid, s['title'], s['singer'])
        tag_vocal(sid, s['sex'], s['singer'])
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        print("  → Mood")
        tag_mood(sid)
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

        if not skip_audio:
            print("  → Audio")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
            tag_audio(sid)

    print(f"\n完成！共处理 {len(songs)} 首歌")
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!

if __name__ == "__main__":
    main()
# !!!!! 禁止一切批量操作！每首歌必须逐首手动处理并独立验证！禁止编写或运行任何批量循环脚本！ !!!!!
