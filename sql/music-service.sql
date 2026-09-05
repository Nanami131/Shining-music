-- 歌单表，存储用户创建的歌曲集合
CREATE TABLE playlists (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '歌单ID',
    name VARCHAR(255) NOT NULL COMMENT '歌单名称',
    description VARCHAR(1000) COMMENT '歌单描述',
    user_id BIGINT COMMENT '关联用户ID',
    type TINYINT NOT NULL COMMENT '歌单类型（普通、专辑、收藏等）',
    visibility TINYINT NOT NULL COMMENT '可见性',
    cover_url VARCHAR(500) COMMENT '歌单封面图片URL',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间'
) COMMENT '歌单表';

-- 歌曲表，存储音乐系统中的单首歌曲
CREATE TABLE songs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '歌曲ID',
    title VARCHAR(255) NOT NULL COMMENT '歌曲标题',
    artist_id BIGINT COMMENT '关联歌手ID',
    album_id BIGINT COMMENT '关联专辑ID',
    file_url VARCHAR(500) COMMENT '歌曲文件URL',
    cover_url VARCHAR(500) COMMENT '封面URL',
    duration INT DEFAULT NULL COMMENT '歌曲时长(秒)',
    status TINYINT NOT NULL COMMENT '歌曲状态',
    random_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用随机推荐：1启用，0禁用',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间'
) COMMENT '歌曲表';

-- 歌手表，存储音乐系统中的歌手信息
CREATE TABLE singers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '歌手ID',
    name VARCHAR(255) NOT NULL COMMENT '歌手名称',
    user_id BIGINT COMMENT '可为空，关联用户服务中的用户ID',
    profile VARCHAR(1000) COMMENT '歌手简介',
    avatar_url VARCHAR(500) COMMENT '歌手头像URL',
    genre VARCHAR(100) COMMENT '音乐流派',
    country VARCHAR(100) COMMENT '国家或地区',
    status TINYINT NOT NULL COMMENT '歌手状态（如活跃、退役等）',
    sex TINYINT COMMENT '性别',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间'
) COMMENT '歌手表';

-- 歌词表
CREATE TABLE lyrics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '歌词ID',
    song_id BIGINT NOT NULL COMMENT '关联歌曲ID',
    language_msg VARCHAR(50) NOT NULL COMMENT '歌词语言描述信息',
    content TEXT NOT NULL COMMENT '歌词内容',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间'
) COMMENT '歌词表';

-- 视频表
CREATE TABLE videos (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '视频ID',
    singer_id BIGINT NULL COMMENT '关联歌手ID，可为空',
    title VARCHAR(255) NOT NULL COMMENT '标题',
    file_url VARCHAR(500) NOT NULL COMMENT '视频地址(MinIO)',
    cover_url VARCHAR(500) NULL COMMENT '封面地址',
    md5 VARCHAR(64) NOT NULL COMMENT '整文件MD5',
    size_bytes BIGINT NOT NULL COMMENT '文件大小',
    created_at DATETIME NULL COMMENT '创建时间',
    updated_at DATETIME NULL COMMENT '更新时间',
    UNIQUE KEY uk_video_md5 (md5),
    INDEX idx_singer_created (singer_id, created_at)
) COMMENT '视频表';
