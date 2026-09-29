-- 现有 statistics 数据库升级：执行前需确认不存在相同 user_id / play_session_id 的重复非空会话。
-- 旧记录的 play_session_id 为 NULL，MySQL 允许唯一键中存在多条 NULL。
ALTER TABLE `shining-statistics`.user_song_play_record
    ADD UNIQUE KEY uq_user_play_session (user_id, play_session_id);
