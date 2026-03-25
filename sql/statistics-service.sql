-- 用户听歌播放记录
CREATE TABLE user_song_play_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    song_id BIGINT NOT NULL COMMENT '歌曲ID',
    played_at DATETIME NOT NULL COMMENT '播放时间',
    duration_sec INT DEFAULT NULL COMMENT '实际播放秒数',
    total_duration INT DEFAULT NULL COMMENT '歌曲总时长秒数',
    completed TINYINT(1) DEFAULT 0 COMMENT '是否播完',
    source VARCHAR(32) DEFAULT NULL COMMENT '播放来源',
    INDEX idx_user_time (user_id, played_at),
    INDEX idx_song_time (song_id, played_at)
) COMMENT '用户听歌播放记录';

-- 用户行为事件日志
CREATE TABLE user_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'SEARCH/FAVORITE/BROWSE/LYRIC_SWITCH',
    target_type VARCHAR(32) DEFAULT NULL COMMENT 'song/singer/playlist',
    target_id BIGINT DEFAULT NULL,
    extra_data JSON DEFAULT NULL COMMENT '附加数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_id, event_type),
    INDEX idx_created (created_at)
) COMMENT '用户行为事件日志';

-- 用户画像快照
CREATE TABLE user_profile (
    user_id BIGINT NOT NULL PRIMARY KEY,
    total_play_count INT DEFAULT 0 COMMENT '总播放次数',
    total_play_duration INT DEFAULT 0 COMMENT '总播放秒数',
    avg_completion_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '平均完播率',
    fav_language VARCHAR(8) DEFAULT NULL COMMENT '偏好语言',
    top_singer_id BIGINT DEFAULT NULL COMMENT '最爱歌手ID',
    top_singer_name VARCHAR(64) DEFAULT NULL COMMENT '最爱歌手名称',
    active_hour TINYINT DEFAULT NULL COMMENT '最活跃时段0-23',
    daily_avg_plays DECIMAL(5,1) DEFAULT 0.0 COMMENT '日均播放量',
    last_play_at DATETIME DEFAULT NULL COMMENT '最后播放时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_updated (updated_at)
) COMMENT '用户画像快照';
