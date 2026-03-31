package org.L2.recommend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.L2.recommend.domain.model.UserPreference;
import org.L2.recommend.infrastructure.UserPreferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);
    private static final String PREF_KEY_PREFIX = "user:preference:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TagVectorService tagVectorService;

    @Autowired
    private UserPreferenceMapper userPreferenceMapper;

    @Autowired(required = false)
    private StatisticsClient statisticsClient;

    /**
     * 增量更新用户偏好向量。
     * 每次播放结束后调用：将歌曲向量以加权方式合入用户偏好。
     *
     * @param weight 播放权重（完整播放=1.0，未完成=0.3）
     */
    public void incrementalUpdate(Long userId, Long songId, double weight) {
        float[] songVector = tagVectorService.getVector(songId);
        if (songVector == null) {
            log.warn("Song vector not found for songId={}, skipping preference update", songId);
            return;
        }

        int dims = songVector.length;
        String key = PREF_KEY_PREFIX + userId;

        UserPrefData current = loadFromRedis(key, dims);

        double newCount = current.playCount + weight;
        float[] newVector = new float[dims];
        for (int i = 0; i < dims; i++) {
            newVector[i] = (float) ((current.vector[i] * current.playCount + songVector[i] * weight) / newCount);
        }

        saveToRedis(key, newVector, newCount);
        log.debug("Updated preference for userId={}, playCount={}", userId, newCount);
    }

    /**
     * 获取用户偏好向量。优先 Redis，其次 MySQL 快照，都没有返回 null。
     */
    public float[] getPreferenceVector(Long userId) {
        String key = PREF_KEY_PREFIX + userId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                UserPrefData data = objectMapper.readValue(json, UserPrefData.class);
                return data.vector;
            } catch (JsonProcessingException e) {
                log.error("Failed to parse preference from Redis for userId={}", userId, e);
            }
        }

        UserPreference snapshot = userPreferenceMapper.selectByUserId(userId);
        if (snapshot != null) {
            try {
                float[] vector = objectMapper.readValue(snapshot.getVectorJson(), float[].class);
                saveToRedis(key, vector, snapshot.getPlayCount());
                return vector;
            } catch (JsonProcessingException e) {
                log.error("Failed to parse preference from MySQL for userId={}", userId, e);
            }
        }

        return null;
    }

    /**
     * 持久化当前 Redis 中的偏好向量到 MySQL（懒加载时调用）。
     */
    public boolean persistSnapshot(Long userId) {
        String key = PREF_KEY_PREFIX + userId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return false;
        }

        try {
            UserPrefData data = objectMapper.readValue(json, UserPrefData.class);
            UserPreference pref = new UserPreference();
            pref.setUserId(userId);
            pref.setVectorJson(objectMapper.writeValueAsString(data.vector));
            pref.setPlayCount((int) data.playCount);
            userPreferenceMapper.insertOrUpdate(pref);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to persist preference for userId={}", userId, e);
            return false;
        }
    }

    private UserPrefData loadFromRedis(String key, int dims) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                return objectMapper.readValue(json, UserPrefData.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse preference from Redis, resetting", e);
            }
        }
        return new UserPrefData(new float[dims], 0);
    }

    private void saveToRedis(String key, float[] vector, double playCount) {
        try {
            UserPrefData data = new UserPrefData(vector, playCount);
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            log.error("Failed to save preference to Redis", e);
        }
    }

    /**
     * 从历史播放记录重建用户偏好向量。
     * 清除 Redis 中旧数据后，逐条应用新权重算法。
     */
    @SuppressWarnings("unchecked")
    public int rebuildFromHistory(Long userId) {
        String key = PREF_KEY_PREFIX + userId;
        stringRedisTemplate.delete(key);

        if (statisticsClient == null) {
            log.warn("StatisticsClient not available, cannot rebuild for userId={}", userId);
            return 0;
        }

        R historyResult = statisticsClient.getPlayHistory(userId, 50);
        if (historyResult == null || historyResult.getPassed() == null || !historyResult.getPassed()) {
            log.warn("Failed to get play history for userId={}", userId);
            return 0;
        }

        Object data = historyResult.getData();
        if (!(data instanceof List<?> list) || list.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Object sid = map.get("songId");
            if (!(sid instanceof Number songIdNum)) continue;

            long songId = songIdNum.longValue();
            Integer durationSec = map.get("durationSec") instanceof Number n ? n.intValue() : null;
            Integer totalDuration = map.get("totalDuration") instanceof Number n ? n.intValue() : null;
            Boolean completed = map.get("completed") instanceof Boolean b ? b : null;

            double weight = calculateWeight(durationSec, totalDuration, completed);
            if (weight <= 0) continue;

            incrementalUpdate(userId, songId, weight);
            count++;
        }

        log.info("Rebuilt preference for userId={} from {} play records", userId, count);
        return count;
    }

    private double calculateWeight(Integer durationSec, Integer totalDuration, Boolean completed) {
        if (totalDuration == null || totalDuration <= 0) {
            return (completed != null && completed) ? 1.0 : 0.3;
        }
        int listened = durationSec != null ? durationSec : 0;
        double ratio = (double) listened / totalDuration;

        if (ratio < 0.1) return 0;
        if (ratio >= 0.8) return 1.0;
        return (ratio - 0.1) / (0.8 - 0.1);
    }

    public static class UserPrefData {
        public float[] vector;
        public double playCount;

        public UserPrefData() {}

        public UserPrefData(float[] vector, double playCount) {
            this.vector = vector;
            this.playCount = playCount;
        }
    }
}
