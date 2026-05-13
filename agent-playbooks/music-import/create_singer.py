#!/usr/bin/env python3
"""
歌手创建辅助脚本 — 强制所有必填字段，防止创建残缺歌手记录。

用法：
    python3 create_singer.py --name "Eve" --sex 1 \
        --profile "日本男性歌手" --genre "J-Pop" --country "日本" \
        --netease-id 1075075

流程：
    1. 检查歌手是否已存在（去重）
    2. 通过 API 创建歌手（status=0 活跃）
    3. 通过 API 补全 profile/genre/country
    4. 从 NetEase 获取头像并上传
    5. 验证所有字段完整性
    6. 输出 singer_id

所有步骤必须全部通过，任何一步失败则报错退出。
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import urllib.request

GATEWAY = "http://localhost:8080"


def get_token():
    data = json.dumps({"username": "1", "password": "1"}).encode()
    req = urllib.request.Request(
        f"{GATEWAY}/api/user/login",
        data=data,
        headers={"Content-Type": "application/json"},
    )
    resp = json.loads(urllib.request.urlopen(req).read())
    if not resp.get("passed"):
        raise RuntimeError(f"Login failed: {resp}")
    return resp["data"]["token"]


def check_existing(name, token):
    """检查歌手是否已存在，返回 singer_id 或 None"""
    result = subprocess.run(
        [
            "docker", "exec", "-i", "shining-mysql", "mysql",
            "-u", "root", "-ppassword",
            "--default-character-set=utf8mb4", "shining-music",
            "-N", "-e", f"SELECT id FROM singers WHERE name='{name}';",
        ],
        capture_output=True, text=True,
    )
    sid = result.stdout.strip()
    return int(sid) if sid else None


def create_singer(name, sex, token):
    """通过 API 创建歌手，返回 singer_id"""
    data = json.dumps({"name": name, "sex": sex, "status": 0}).encode()
    req = urllib.request.Request(
        f"{GATEWAY}/api/music/singer",
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
    )
    resp = json.loads(urllib.request.urlopen(req).read())
    if not resp.get("passed"):
        raise RuntimeError(f"Create singer failed: {resp}")
    return resp["data"]["id"]


def update_profile(singer_id, sex, profile, genre, country, token):
    data = json.dumps({
        "id": singer_id,
        "sex": sex,
        "profile": profile,
        "genre": genre,
        "country": country,
    }).encode()
    req = urllib.request.Request(
        f"{GATEWAY}/api/music/update-profile",
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
    )
    resp = json.loads(urllib.request.urlopen(req).read())
    if not resp.get("passed"):
        raise RuntimeError(f"Update profile failed: {resp}")


def fetch_netease_avatar(netease_artist_id):
    """从 NetEase 获取歌手头像 URL"""
    url = f"https://music.163.com/api/artist/{netease_artist_id}"
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0",
        "Referer": "https://music.163.com",
    })
    resp = json.loads(urllib.request.urlopen(req).read())
    artist = resp.get("artist", {})
    return artist.get("img1v1Url") or artist.get("picUrl")


def upload_avatar(singer_id, image_path, token):
    """通过 curl 上传头像（multipart form 在 Python 3.8 标准库中较难处理）"""
    md5 = hashlib.md5(open(image_path, "rb").read()).hexdigest()
    result = subprocess.run(
        [
            "curl", "-s", "-X", "POST",
            f"{GATEWAY}/api/music/update-avatar",
            "-H", f"Authorization: Bearer {token}",
            "-F", f"id={singer_id}",
            "-F", f"avatarFile=@{image_path}",
            "-F", f"md5={md5}",
        ],
        capture_output=True, text=True,
        env={**os.environ, "no_proxy": "*"},
    )
    resp = json.loads(result.stdout)
    if not resp.get("passed"):
        raise RuntimeError(f"Upload avatar failed: {resp}")
    return resp["data"]


def validate_singer(singer_id):
    """验证歌手所有必填字段非空"""
    result = subprocess.run(
        [
            "docker", "exec", "-i", "shining-mysql", "mysql",
            "-u", "root", "-ppassword",
            "--default-character-set=utf8mb4", "shining-music",
            "-N", "-e",
            f"SELECT name, avatar_url, profile, genre, country, status, sex "
            f"FROM singers WHERE id={singer_id};",
        ],
        capture_output=True, text=True,
    )
    row = result.stdout.strip()
    if not row:
        raise RuntimeError(f"Singer {singer_id} not found in DB")

    fields = row.split("\t")
    field_names = ["name", "avatar_url", "profile", "genre", "country", "status", "sex"]
    errors = []
    for i, (fname, val) in enumerate(zip(field_names, fields)):
        if val in ("NULL", "", "None"):
            errors.append(f"{fname} is NULL/empty")

    if fields[5] != "0":
        errors.append(f"status={fields[5]}, should be 0 (活跃)")

    if errors:
        raise RuntimeError(
            f"Singer {singer_id} validation FAILED:\n" +
            "\n".join(f"  - {e}" for e in errors)
        )

    print(f"✅ Singer {singer_id} validation passed:")
    for fname, val in zip(field_names, fields):
        display = val[:60] + "..." if len(val) > 60 else val
        print(f"  {fname}: {display}")


def main():
    parser = argparse.ArgumentParser(description="创建/补全歌手（强制全字段）")
    parser.add_argument("--name", required=True, help="歌手名")
    parser.add_argument("--sex", type=int, required=True, help="0=男声主唱, 1=女声主唱, 2=男女合唱（按歌曲中主唱人声性别，非组合方式）")
    parser.add_argument("--profile", required=True, help="歌手简介")
    parser.add_argument("--genre", required=True, help="音乐风格")
    parser.add_argument("--country", required=True, help="国籍（用中文，如：日本）")
    parser.add_argument("--netease-id", type=int, help="NetEase 歌手 ID（用于获取头像）")
    parser.add_argument("--avatar-url", help="直接指定头像 URL（不用 NetEase）")
    args = parser.parse_args()

    token = get_token()

    existing_id = check_existing(args.name, token)
    if existing_id:
        print(f"歌手「{args.name}」已存在，id={existing_id}，跳过创建，补全信息")
        singer_id = existing_id
    else:
        singer_id = create_singer(args.name, args.sex, token)
        print(f"歌手「{args.name}」创建成功，id={singer_id}")

    update_profile(singer_id, args.sex, args.profile, args.genre, args.country, token)
    print(f"Profile 已更新")

    avatar_url = args.avatar_url
    if not avatar_url and args.netease_id:
        avatar_url = fetch_netease_avatar(args.netease_id)
        print(f"NetEase 头像: {avatar_url}")

    if avatar_url:
        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as f:
            tmp_path = f.name
        try:
            req = urllib.request.Request(avatar_url, headers={"User-Agent": "Mozilla/5.0"})
            data = urllib.request.urlopen(req).read()
            if len(data) < 5000:
                raise RuntimeError(f"Avatar too small ({len(data)} bytes), likely placeholder")
            with open(tmp_path, "wb") as f:
                f.write(data)
            result_url = upload_avatar(singer_id, tmp_path, token)
            print(f"头像上传成功: {result_url}")
        finally:
            os.unlink(tmp_path)
    else:
        raise RuntimeError("必须提供 --netease-id 或 --avatar-url 来获取头像")

    validate_singer(singer_id)
    print(f"\n{'='*40}")
    print(f"singer_id={singer_id}")


if __name__ == "__main__":
    main()
