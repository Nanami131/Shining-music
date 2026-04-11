package org.L2.recommend.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.L2.recommend.strategy.RecommendationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Item-based Collaborative Filtering (Sarwar et al., 2001).
 *
 * Core idea: items co-consumed by similar users are similar.
 * 1. Build a user-item interaction matrix from play records.
 * 2. Compute item-item cosine similarity on the user-rating vectors.
 * 3. For a target user, aggregate similarity scores from their played items
 *    to candidate items, weighted by implicit rating (log-scaled play count).
 */
@Service
public class ItemCFStrategy implements RecommendationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ItemCFStrategy.class);
    private static final String SIM_KEY_PREFIX = "itemcf:sim:";
    private static final String MATRIX_VERSION_KEY = "itemcf:version";
    private static final int TOP_K_SIMILAR = 30;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private StatisticsClient statisticsClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String name() {
        return "item-cf";
    }

    @Override
    public boolean isAvailable(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(MATRIX_VERSION_KEY));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> recommend(Long userId, int limit, Set<Long> playedSongIds) {
        if (!isAvailable(userId)) return Collections.emptyList();

        Map<Long, Double> userRatings = getUserRatings(userId);
        if (userRatings.isEmpty()) return Collections.emptyList();

        Map<Long, Double> candidateScores = new HashMap<>();
        Map<Long, Double> candidateNorm = new HashMap<>();

        for (Map.Entry<Long, Double> entry : userRatings.entrySet()) {
            Long playedSongId = entry.getKey();
            double rating = entry.getValue();

            Map<Long, Double> similarItems = getSimilarItems(playedSongId);
            for (Map.Entry<Long, Double> sim : similarItems.entrySet()) {
                Long candidateId = sim.getKey();
                if (playedSongIds.contains(candidateId)) continue;

                double similarity = sim.getValue();
                candidateScores.merge(candidateId, similarity * rating, Double::sum);
                candidateNorm.merge(candidateId, Math.abs(similarity), Double::sum);
            }
        }

        Map<Long, Double> normalizedScores = new HashMap<>();
        for (Map.Entry<Long, Double> entry : candidateScores.entrySet()) {
            Long cid = entry.getKey();
            double norm = candidateNorm.getOrDefault(cid, 1.0);
            if (norm > 0) {
                normalizedScores.put(cid, entry.getValue() / norm);
            }
        }

        List<Map.Entry<Long, Double>> sorted = normalizedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .toList();

        if (sorted.isEmpty()) return Collections.emptyList();

        double maxNormScore = sorted.stream()
                .mapToDouble(Map.Entry::getValue)
                .max().orElse(1.0);

        return sorted.stream()
                .map(entry -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("songId", entry.getKey());
                    double display = maxNormScore > 0
                            ? Math.min(1.0, entry.getValue() / maxNormScore)
                            : 0.0;
                    m.put("similarity", Math.round(display * 10000.0) / 10000.0);
                    return m;
                })
                .toList();
    }

    /**
     * Build the item-item similarity matrix from all user-song interactions.
     * Results are cached in Redis with a 7-day TTL.
     *
     * @return number of songs with computed similarities
     */
    @SuppressWarnings("unchecked")
    public int rebuildSimilarityMatrix() {
        if (statisticsClient == null) {
            log.warn("StatisticsClient not available, cannot build Item-CF matrix");
            return 0;
        }

        R result = statisticsClient.getAllUserSongPlayCounts();
        if (result == null || !Boolean.TRUE.equals(result.getPassed())) {
            log.warn("Failed to fetch user-song interactions");
            return 0;
        }

        List<Map<String, Object>> interactions = (List<Map<String, Object>>) result.getData();
        if (interactions == null || interactions.isEmpty()) {
            log.warn("No user-song interactions found");
            return 0;
        }

        Map<Long, Map<Long, Double>> songUsers = new HashMap<>();
        Set<Long> allSongIds = new HashSet<>();

        for (Map<String, Object> row : interactions) {
            Long userId = ((Number) row.get("userId")).longValue();
            Long songId = ((Number) row.get("songId")).longValue();
            double playCount = Math.log(1 + ((Number) row.get("playCount")).doubleValue());

            songUsers.computeIfAbsent(songId, k -> new HashMap<>()).put(userId, playCount);
            allSongIds.add(songId);
        }

        log.info("Building Item-CF matrix: {} songs, {} interactions",
                allSongIds.size(), interactions.size());

        List<Long> songList = new ArrayList<>(allSongIds);
        int count = 0;

        for (int i = 0; i < songList.size(); i++) {
            Long songA = songList.get(i);
            Map<Long, Double> usersA = songUsers.getOrDefault(songA, Collections.emptyMap());
            if (usersA.isEmpty()) continue;

            PriorityQueue<ScoredItem> topK = new PriorityQueue<>(
                    Comparator.comparingDouble(s -> s.similarity));

            for (int j = 0; j < songList.size(); j++) {
                if (i == j) continue;
                Long songB = songList.get(j);
                Map<Long, Double> usersB = songUsers.getOrDefault(songB, Collections.emptyMap());
                if (usersB.isEmpty()) continue;

                double sim = cosineSimilarity(usersA, usersB);
                if (sim <= 0) continue;

                topK.offer(new ScoredItem(songB, sim));
                if (topK.size() > TOP_K_SIMILAR) {
                    topK.poll();
                }
            }

            if (!topK.isEmpty()) {
                Map<Long, Double> simMap = new HashMap<>();
                for (ScoredItem item : topK) {
                    simMap.put(item.songId, Math.round(item.similarity * 10000.0) / 10000.0);
                }
                try {
                    String json = objectMapper.writeValueAsString(simMap);
                    stringRedisTemplate.opsForValue().set(
                            SIM_KEY_PREFIX + songA, json, 7, TimeUnit.DAYS);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to store similarity for song {}", songA, e);
                }
            }
        }

        stringRedisTemplate.opsForValue().set(MATRIX_VERSION_KEY,
                String.valueOf(System.currentTimeMillis()), 7, TimeUnit.DAYS);

        log.info("Item-CF similarity matrix built: {} songs with similarities", count);
        return count;
    }

    private static final int MIN_COMMON_USERS = 2;
    private static final double SHRINKAGE_LAMBDA = 10.0;

    private double cosineSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
        double dot = 0, normA = 0, normB = 0;
        int commonCount = 0;

        for (Map.Entry<Long, Double> ea : a.entrySet()) {
            normA += ea.getValue() * ea.getValue();
            Double bVal = b.get(ea.getKey());
            if (bVal != null) {
                dot += ea.getValue() * bVal;
                commonCount++;
            }
        }

        if (commonCount < MIN_COMMON_USERS) return 0;

        for (Double bv : b.values()) {
            normB += bv * bv;
        }

        if (normA == 0 || normB == 0) return 0;
        double rawSim = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        double confidence = commonCount / (commonCount + SHRINKAGE_LAMBDA);
        return confidence * rawSim;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Double> getUserRatings(Long userId) {
        Map<Long, Double> ratings = new HashMap<>();
        try {
            if (statisticsClient == null) return ratings;
            R result = statisticsClient.getUserSongPlayCounts(userId);
            if (result == null || !Boolean.TRUE.equals(result.getPassed())) return ratings;

            Object data = result.getData();
            if (!(data instanceof List<?> list)) return ratings;

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Number songIdNum = (Number) m.get("songId");
                Number countNum = (Number) m.get("playCount");
                if (songIdNum == null) continue;
                double playCount = countNum != null ? countNum.doubleValue() : 1.0;
                ratings.put(songIdNum.longValue(), Math.log(1 + playCount));
            }
        } catch (Exception e) {
            log.warn("Failed to get user ratings for userId={}", userId, e);
        }
        return ratings;
    }

    private Map<Long, Double> getSimilarItems(Long songId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(SIM_KEY_PREFIX + songId);
            if (json == null) return Collections.emptyMap();

            Map<String, Double> raw = objectMapper.readValue(json,
                    new TypeReference<Map<String, Double>>() {});
            Map<Long, Double> result = new HashMap<>();
            for (Map.Entry<String, Double> e : raw.entrySet()) {
                result.put(Long.parseLong(e.getKey()), e.getValue());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to read similarity for song {}", songId, e);
            return Collections.emptyMap();
        }
    }

    private static class ScoredItem {
        final long songId;
        final double similarity;

        ScoredItem(long songId, double similarity) {
            this.songId = songId;
            this.similarity = similarity;
        }
    }
}
