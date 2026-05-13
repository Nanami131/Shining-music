#!/usr/bin/env python3
"""Fix lyrics for a single song: download from NetEase and insert correctly into MySQL."""
import sys, subprocess, urllib.request, json

def mysql_exec(sql):
    """Execute SQL via docker exec, piping through stdin to avoid shell escaping issues."""
    proc = subprocess.run(
        ['docker', 'exec', '-i', 'shining-mysql', 'mysql', '-u', 'root', '-ppassword',
         '--default-character-set=utf8mb4', 'shining-music'],
        input=sql, capture_output=True, text=True
    )
    if proc.returncode != 0:
        print(f"MySQL error: {proc.stderr}", file=sys.stderr)
    return proc.stdout

def fetch_lyrics(netease_id):
    """Fetch lyrics from NetEase Music API."""
    url = f'https://music.163.com/api/song/lyric?id={netease_id}&lv=1&kv=1&tv=1'
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0',
        'Referer': 'https://music.163.com'
    })
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode('utf-8'))
    jp = data.get('lrc', {}).get('lyric', '')
    cn = data.get('tlyric', {}).get('lyric', '')
    return jp, cn

def escape_sql(s):
    """Escape string for MySQL single-quoted literal."""
    return s.replace('\\', '\\\\').replace("'", "\\'")

def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <song_id> <netease_id>", file=sys.stderr)
        sys.exit(1)
    
    song_id = int(sys.argv[1])
    netease_id = int(sys.argv[2])
    
    jp, cn = fetch_lyrics(netease_id)
    
    jp_lines = len([l for l in jp.split('\n') if l.strip()])
    cn_lines = len([l for l in cn.split('\n') if l.strip()])
    print(f"Song {song_id}: JP={jp_lines} lines, CN={cn_lines} lines")
    
    if jp_lines < 5:
        print(f"WARNING: JP lyrics too short ({jp_lines} lines)", file=sys.stderr)
    
    # Delete existing broken lyrics
    mysql_exec(f"DELETE FROM lyrics WHERE song_id = {song_id};")
    
    # Insert Japanese lyrics
    jp_escaped = escape_sql(jp)
    sql_jp = f"INSERT INTO lyrics (song_id, language_msg, content, created_at, updated_at) VALUES ({song_id}, 'ja', '{jp_escaped}', NOW(), NOW());"
    mysql_exec(sql_jp)
    
    # Insert Chinese lyrics
    if cn_lines > 0:
        cn_escaped = escape_sql(cn)
        sql_cn = f"INSERT INTO lyrics (song_id, language_msg, content, created_at, updated_at) VALUES ({song_id}, 'zh', '{cn_escaped}', NOW(), NOW());"
        mysql_exec(sql_cn)
    
    # Verify
    result = mysql_exec(f"SELECT song_id, language_msg, CHAR_LENGTH(content) AS chars, LENGTH(content) AS bytes FROM lyrics WHERE song_id = {song_id};")
    print(result)
    
    # Quick content check - first actual lyric line
    check = mysql_exec(f"SELECT LEFT(content, 100) FROM lyrics WHERE song_id = {song_id} AND language_msg = 'ja' LIMIT 1;")
    first_line = check.strip().split('\n')[-1] if check.strip() else ''
    has_unicode_escape = 'u3' in first_line or 'u4' in first_line or 'u5' in first_line or 'u6' in first_line or 'u7' in first_line or 'u8' in first_line or 'u9' in first_line
    if has_unicode_escape and not any(ord(c) > 127 for c in first_line):
        print(f"ERROR: Lyrics still contain unicode escapes!", file=sys.stderr)
        sys.exit(1)
    else:
        print(f"OK: Content contains actual unicode characters")

if __name__ == '__main__':
    main()
