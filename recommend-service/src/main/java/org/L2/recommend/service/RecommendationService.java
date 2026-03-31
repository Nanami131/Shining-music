package org.L2.recommend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.L2.recommend.infrastructure.SongTagMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    @Autowired
    private UserPreferenceService userPreferenceService;

    @Autowired
    private TagVectorService tagVectorService;

    @Autowired
    private SongTagMapper songTagMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private StatisticsClient statisticsClient;

    /**
     * 推荐流程（Liu 2018 + Ribecky 2021 架构）：
     * 1. Language 一级分流 — 按用户历史语种比例分配推荐名额
     * 2. 五维加权余弦相似度 — Source/Mood/Vocal/Audio/Era 使用 Ribecky 权重
     * 3. 已听歌曲过滤
     */
    public R recommend(Long userId, int limit) {
        float[] userVector = userPreferenceService.getPreferenceVector(userId);
        if (userVector == null) {
            return R.success("暂无偏好数据，返回热门歌曲", Collections.emptyList());
        }

        userPreferenceService.persistSnapshot(userId);

        Set<Long> playedSongIds = getPlayedSongIds(userId);
        int[] langDims = tagVectorService.getLanguageDimIndices();
        Map<Integer, Double> userLangDist = extractLanguageDistribution(userVector, langDims);

        List<Long> allSongIds = songTagMapper.selectAllDistinctSongIds();

        Map<Integer, List<ScoredSong>> langBuckets = new HashMap<>();
        for (int langDim : langDims) {
            langBuckets.put(langDim, new ArrayList<>());
        }
        langBuckets.put(-1, new ArrayList<>());

        float[] weights = tagVectorService.getDimensionWeights();
        int[] nonLangDims = tagVectorService.getNonLanguageDimIndices();

        for (Long songId : allSongIds) {
            if (playedSongIds.contains(songId)) continue;

            float[] songVector = tagVectorService.getVector(songId);
            if (songVector == null) continue;

            int langDim = tagVectorService.getPrimaryLanguageDim(songVector);
            double similarity = weightedCosineSimilarity(userVector, songVector, weights, nonLangDims);

            langBuckets.computeIfAbsent(langDim, k -> new ArrayList<>())
                    .add(new ScoredSong(songId, similarity));
        }

        for (List<ScoredSong> bucket : langBuckets.values()) {
            bucket.sort(Comparator.comparingDouble(s -> -s.similarity));
        }

        List<Map<String, Object>> result = allocateByLanguage(langBuckets, userLangDist, langDims, limit);

        log.info("Generated {} recommendations for userId={} (filtered {} played songs, {} languages)",
                result.size(), userId, playedSongIds.size(), userLangDist.size());
        return R.success("推荐成功", result);
    }

    /**
     * 从用户偏好向量中提取语种分布。
     * 返回 langDimIndex -> proportion（归一化到 sum=1）。
     */
    private Map<Integer, Double> extractLanguageDistribution(float[] userVector, int[] langDims) {
        Map<Integer, Double> dist = new LinkedHashMap<>();
        double total = 0;
        for (int dim : langDims) {
            double v = dim < userVector.length ? Math.max(0, userVector[dim]) : 0;
            dist.put(dim, v);
            total += v;
        }
        if (total > 0) {
            for (Map.Entry<Integer, Double> e : dist.entrySet()) {
                e.setValue(e.getValue() / total);
            }
        } else {
            double equal = 1.0 / langDims.length;
            for (int dim : langDims) {
                dist.put(dim, equal);
            }
        }
        return dist;
    }

    /**
     * 按语种比例分配推荐名额，每个语种从其 bucket 中取 top-N。
     * 如某语种名额不足，剩余名额分配给其他语种。
     */
    private List<Map<String, Object>> allocateByLanguage(
            Map<Integer, List<ScoredSong>> langBuckets,
            Map<Integer, Double> userLangDist,
            int[] langDims,
            int limit) {

        Map<Integer, Integer> quotas = new LinkedHashMap<>();
        int assigned = 0;
        for (int dim : langDims) {
            double proportion = userLangDist.getOrDefault(dim, 0.0);
            int quota = (int) Math.round(proportion * limit);
            quotas.put(dim, quota);
            assigned += quota;
        }

        if (assigned < limit) {
            int bestDim = langDims[0];
            double bestProp = 0;
            for (int dim : langDims) {
                if (userLangDist.getOrDefault(dim, 0.0) > bestProp) {
                    bestProp = userLangDist.getOrDefault(dim, 0.0);
                    bestDim = dim;
                }
            }
            quotas.put(bestDim, quotas.get(bestDim) + (limit - assigned));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int remaining = 0;

        for (int dim : langDims) {
            int quota = quotas.getOrDefault(dim, 0);
            List<ScoredSong> bucket = langBuckets.getOrDefault(dim, Collections.emptyList());
            int take = Math.min(quota, bucket.size());
            for (int i = 0; i < take; i++) {
                result.add(bucket.get(i).toMap());
            }
            remaining += (quota - take);
        }

        if (remaining > 0) {
            List<ScoredSong> all = new ArrayList<>();
            for (Map.Entry<Integer, List<ScoredSong>> entry : langBuckets.entrySet()) {
                Set<Long> alreadyPicked = result.stream()
                        .map(m -> ((Number) m.get("songId")).longValue())
                        .collect(java.util.stream.Collectors.toSet());
                for (ScoredSong s : entry.getValue()) {
                    if (!alreadyPicked.contains(s.songId)) {
                        all.add(s);
                    }
                }
            }
            all.sort(Comparator.comparingDouble(s -> -s.similarity));
            for (int i = 0; i < Math.min(remaining, all.size()); i++) {
                result.add(all.get(i).toMap());
            }
        }

        result.sort(Comparator.comparingDouble(m -> -((Number) m.get("similarity")).doubleValue()));
        return result;
    }

    /**
     * Ribecky (2021) 加权余弦相似度。
     * 每个维度乘以 category_score / num_dims_in_category，Language 维度权重=0。
     */
    private double weightedCosineSimilarity(float[] a, float[] b, float[] weights, int[] dimIndices) {
        double dot = 0, normA = 0, normB = 0;
        for (int i : dimIndices) {
            if (i >= a.length || i >= b.length) continue;
            double w = weights[i];
            dot   += w * a[i] * b[i];
            normA += w * a[i] * a[i];
            normB += w * b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
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

    private static class ScoredSong {
        final long songId;
        final double similarity;

        ScoredSong(long songId, double similarity) {
            this.songId = songId;
            this.similarity = similarity;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("songId", songId);
            m.put("similarity", Math.round(similarity * 10000.0) / 10000.0);
            return m;
        }
    }
}
