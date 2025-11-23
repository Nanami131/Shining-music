-- 统计表，存放相关数据
CREATE TABLE user_song_play_record (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id   BIGINT      NOT NULL COMMENT '用户ID',
    song_id   BIGINT      NOT NULL COMMENT '歌曲ID',
    played_at DATETIME    NOT NULL COMMENT '播放时间',
    INDEX idx_user_time (user_id, played_at),
    INDEX idx_song_time (song_id, played_at)
) COMMENT '用户听歌播放记录';