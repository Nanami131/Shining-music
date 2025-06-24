-- 歌单表，存储用户创建的歌曲集合
CREATE TABLE playlists (
                           id BIGINT PRIMARY KEY AUTO_INCREMENT, -- 歌单ID
                           NAME VARCHAR(255) NOT NULL, -- 歌单名称
                           description VARCHAR(1000), -- 歌单描述
                           user_id BIGINT, -- 关联用户ID
                           TYPE TINYINT NOT NULL, -- 歌单类型（普通、专辑、收藏等）
                           visibility TINYINT NOT NULL, -- 可见性
                           cover_url VARCHAR(500), -- 歌单封面图片URL
                           created_at DATETIME, -- 创建时间
                           updated_at DATETIME -- 更新时间
);

-- 歌曲表，存储音乐系统中的单首歌曲
CREATE TABLE songs (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT, -- 歌曲ID
                       title VARCHAR(255) NOT NULL, -- 歌曲标题
                       artist_id BIGINT, -- 关联歌手ID
                       album_id BIGINT, -- 关联专辑ID
                       file_url VARCHAR(500) NOT NULL, -- 歌曲文件URL
                       cover_url VARCHAR(500), -- 封面URL
                       STATUS TINYINT NOT NULL, -- 歌曲状态
                       created_at DATETIME, -- 创建时间
                       updated_at DATETIME -- 更新时间
);

-- 歌手表，存储音乐系统中的歌手信息
CREATE TABLE singers (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT, -- 歌手ID
                         NAME VARCHAR(255) NOT NULL, -- 歌手名称
                         user_id BIGINT, -- 可为空，关联用户服务中的用户ID
                         profile VARCHAR(1000), -- 歌手简介
                         avatar_url VARCHAR(500), -- 歌手头像URL
                         genre VARCHAR(100), -- 音乐流派
                         country VARCHAR(100), -- 国家或地区
                         STATUS TINYINT NOT NULL, -- 歌手状态（如活跃、退役等）
                         sex TINYINT, -- 性别
                         created_at DATETIME, -- 创建时间
                         updated_at DATETIME -- 更新时间
);

-- 歌词表
CREATE TABLE lyrics (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT, -- 歌词ID
                        song_id BIGINT NOT NULL, -- 关联歌曲ID
                        language_msg VARCHAR(50) NOT NULL, -- 歌词语言描述信息
                        content TEXT NOT NULL, -- 歌词内容
                        created_at DATETIME, -- 创建时间
                        updated_at DATETIME -- 更新时间
);