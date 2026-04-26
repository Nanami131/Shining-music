package org.L2.recommend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.L2.recommend.infrastructure.SongTagMapper;
import org.L2.recommend.strategy.impl.ContentBasedStrategy;
import org.L2.recommend.strategy.impl.ItemCFStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String CACHE_PREFIX_CB = "recommend:daily:cb:";
    private static final String CACHE_PREFIX_CF = "recommend:daily:cf:";
    static final String SIMILAR_CACHE_PREFIX = "song:similar:";
    private static final int SIMILAR_CACHE_DAYS = 30;

    @Autowired
    private ContentBasedStrategy contentBasedStrategy;

    @Autowired
    private ItemCFStrategy itemCFStrategy;

    @Autowired
    private UserPreferenceService userPreferenceService;

    @Autowired
    private TagVectorService tagVectorService;

    @Autowired
    private SongTagMapper songTagMapper;

    @Autowired(required = false)
    private StatisticsClient statisticsClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public R recommend(Long userId, int limit) {
        return recommendContentBased(userId, limit);
    }

    public R recommendContentBased(Long userId, int limit) {
        String cacheKey = CACHE_PREFIX_CB + userId;
        R cached = tryReadCache(cacheKey, "内容推荐");
        if (cached != null) return cached;

        if (!contentBasedStrategy.isAvailable(userId)) {
            return fallbackToHotSongs(limit);
        }
        userPreferenceService.persistSnapshot(userId);
        Set<Long> played = getPlayedSongIds(userId);
        List<Map<String, Object>> results = contentBasedStrategy.recommend(userId, limit, played);
        if (results.isEmpty()) {
            return fallbackToHotSongs(limit);
        }
        writeCache(cacheKey, results);
        return R.success("推荐成功（内容推荐）", results);
    }

    public R recommendItemCF(Long userId, int limit) {
        String cacheKey = CACHE_PREFIX_CF + userId;
        R cached = tryReadCache(cacheKey, "协同过滤");
        if (cached != null) return cached;

        if (!itemCFStrategy.isAvailable(userId)) {
            return R.error("Item-CF 相似度矩阵尚未构建，请先调用 /recommend/itemcf/rebuild");
        }
        Set<Long> played = getPlayedSongIds(userId);
        List<Map<String, Object>> results = itemCFStrategy.recommend(userId, limit, played);
        if (results.isEmpty()) {
            return fallbackToHotSongs(limit);
        }
        writeCache(cacheKey, results);
        return R.success("推荐成功（协同过滤）", results);
    }

    public R rebuildItemCFMatrix() {
        int count = itemCFStrategy.rebuildSimilarityMatrix();
        return R.success("Item-CF 相似度矩阵构建完成", Map.of("songsWithSimilarity", count));
    }

    private static final double CROSS_LANG_PENALTY = 0.30;

    public R findSimilarSongs(Long songId, int limit) {
        String cacheKey = SIMILAR_CACHE_PREFIX + songId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<Map<String, Object>> list = objectMapper.readValue(cached,
                        new TypeReference<List<Map<String, Object>>>() {});
                if (!list.isEmpty()) {
                    List<Map<String, Object>> trimmed = list.size() > limit ? list.subList(0, limit) : list;
                    return R.success("查询成功（缓存）", trimmed);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read similar-songs cache for songId={}", songId, e);
        }

        float[] targetVector = tagVectorService.getVector(songId);
        if (targetVector == null) {
            return R.error("该歌曲暂无标签向量，无法计算相似度");
        }

        int targetLangDim = tagVectorService.getPrimaryLanguageDim(targetVector);

        List<Long> allSongIds = songTagMapper.selectAllDistinctSongIds();
        float[] weights = tagVectorService.getDimensionWeights();
        int[] nonLangDims = tagVectorService.getNonLanguageDimIndices();

        List<float[]> allVectors = new ArrayList<>();
        List<Long> vectorSongIds = new ArrayList<>();
        for (Long sid : allSongIds) {
            if (sid.equals(songId)) continue;
            float[] v = tagVectorService.getVector(sid);
            if (v != null) {
                allVectors.add(v);
                vectorSongIds.add(sid);
            }
        }

        float[] globalMean = computeGlobalMean(allVectors, targetVector, targetVector.length);

        List<Map<String, Object>> scored = new ArrayList<>();
        for (int i = 0; i < vectorSongIds.size(); i++) {
            float[] candidateVector = allVectors.get(i);
            double sim = adjustedWeightedCosine(targetVector, candidateVector, globalMean, weights, nonLangDims);
            if (sim > 0) {
                boolean sameLang = targetLangDim >= 0
                        && tagVectorService.getPrimaryLanguageDim(candidateVector) == targetLangDim;
                if (!sameLang) {
                    sim *= (1.0 - CROSS_LANG_PENALTY);
                }
                Map<String, Object> m = new HashMap<>();
                m.put("songId", vectorSongIds.get(i));
                m.put("similarity", Math.round(sim * 10000.0) / 10000.0);
                scored.add(m);
            }
        }
        scored.sort(Comparator.comparingDouble(m -> -((Number) m.get("similarity")).doubleValue()));

        int cacheSize = Math.max(limit, 20);
        List<Map<String, Object>> toCache = scored.size() > cacheSize ? scored.subList(0, cacheSize) : scored;
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(toCache), SIMILAR_CACHE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to write similar-songs cache for songId={}", songId, e);
        }

        List<Map<String, Object>> result = scored.size() > limit ? scored.subList(0, limit) : scored;
        return R.success("查询成功", result);
    }

    private float[] computeGlobalMean(List<float[]> vectors, float[] extra, int dims) {
        float[] mean = new float[dims];
        int total = vectors.size() + 1;
        for (int i = 0; i < Math.min(extra.length, dims); i++) {
            mean[i] += extra[i];
        }
        for (float[] v : vectors) {
            for (int i = 0; i < Math.min(v.length, dims); i++) {
                mean[i] += v[i];
            }
        }
        for (int i = 0; i < dims; i++) {
            mean[i] /= total;
        }
        return mean;
    }

    private double adjustedWeightedCosine(float[] a, float[] b, float[] mean,
                                          float[] weights, int[] dimIndices) {
        double dot = 0, normA = 0, normB = 0;
        for (int i : dimIndices) {
            if (i >= a.length || i >= b.length) continue;
            double w = weights[i];
            double ai = a[i] - mean[i];
            double bi = b[i] - mean[i];
            dot   += w * ai * bi;
            normA += w * ai * ai;
            normB += w * bi * bi;
        }
        if (normA == 0 || normB == 0) return 0;
        return Math.max(0, dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private R tryReadCache(String key, String strategyName) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                List<Map<String, Object>> cached = objectMapper.readValue(json,
                        new TypeReference<List<Map<String, Object>>>() {});
                if (cached.isEmpty()) {
                    stringRedisTemplate.delete(key);
                    log.info("Ignore empty recommendation cache key={}", key);
                    return null;
                }
                log.debug("Cache hit for {} key={}", strategyName, key);
                return R.success("推荐成功（" + strategyName + "，今日缓存）", cached);
            }
        } catch (Exception e) {
            log.warn("Failed to read recommendation cache key={}", key, e);
        }
        return null;
    }

    private void writeCache(String key, List<Map<String, Object>> results) {
        try {
            Duration ttl = Duration.between(LocalDateTime.now(),
                    LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT));
            if (ttl.isNegative() || ttl.isZero()) ttl = Duration.ofHours(1);
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(results), ttl);
        } catch (Exception e) {
            log.warn("Failed to write recommendation cache key={}", key, e);
        }
    }

    @SuppressWarnings("unchecked")
    private R fallbackToHotSongs(int limit) {
        try {
            if (statisticsClient == null) {
                return R.success("暂无偏好数据，返回热门歌曲", Collections.emptyList());
            }
            R topResult = statisticsClient.getGlobalTopSongs(limit);
            if (topResult != null && topResult.getPassed() != null && topResult.getPassed()) {
                Object data = topResult.getData();
                if (data instanceof List<?> list && !list.isEmpty()) {
                    long maxCount = 1;
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Number pc = (Number) m.get("playCount");
                            if (pc != null && pc.longValue() > maxCount) maxCount = pc.longValue();
                        }
                    }
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Number songIdNum = (Number) m.get("songId");
                            Number playCount = (Number) m.get("playCount");
                            if (songIdNum != null) {
                                Map<String, Object> rec = new HashMap<>();
                                rec.put("songId", songIdNum.longValue());
                                double sim = playCount != null
                                        ? Math.round(((double) playCount.longValue() / maxCount) * 10000.0) / 10000.0
                                        : 0.5;
                                rec.put("similarity", sim);
                                items.add(rec);
                            }
                        }
                    }
                    if (!items.isEmpty()) {
                        log.info("Fallback to {} hot songs for new user", items.size());
                        return R.success("暂无偏好数据，返回热门歌曲", items);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch hot songs fallback", e);
        }
        return R.success("暂无偏好数据，返回热门歌曲", Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private Set<Long> getPlayedSongIds(Long userId) {
        Set<Long> played = new HashSet<>();
        try {
            if (statisticsClient == null) return played;
            R result = statisticsClient.getPlayedSongIds(userId);
            if (result != null && result.getPassed() != null && result.getPassed()) {
                Object data = result.getData();
                if (data instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Number num) {
                            played.add(num.longValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch played song IDs for userId={}, skipping filter", userId, e);
        }
        return played;
    }
}
