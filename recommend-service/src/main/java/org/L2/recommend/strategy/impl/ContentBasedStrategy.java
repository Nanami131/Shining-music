package org.L2.recommend.strategy.impl;

import org.L2.recommend.infrastructure.SongTagMapper;
import org.L2.recommend.service.TagVectorService;
import org.L2.recommend.service.UserPreferenceService;
import org.L2.recommend.strategy.RecommendationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContentBasedStrategy implements RecommendationStrategy {

    @Autowired
    private UserPreferenceService userPreferenceService;

    @Autowired
    private TagVectorService tagVectorService;

    @Autowired
    private SongTagMapper songTagMapper;

    @Override
    public String name() {
        return "content-based";
    }

    @Override
    public boolean isAvailable(Long userId) {
        return userPreferenceService.getPreferenceVector(userId) != null;
    }

    @Override
    public List<Map<String, Object>> recommend(Long userId, int limit, Set<Long> playedSongIds) {
        float[] userVector = userPreferenceService.getPreferenceVector(userId);
        if (userVector == null) return Collections.emptyList();

        int[] langDims = tagVectorService.getLanguageDimIndices();
        Map<Integer, Double> userLangDist = extractLanguageDistribution(userVector, langDims);

        List<Long> allSongIds = songTagMapper.selectAllDistinctSongIds();

        float[] weights = tagVectorService.getDimensionWeights();
        int[] nonLangDims = tagVectorService.getNonLanguageDimIndices();

        List<float[]> allVectors = new ArrayList<>();
        List<Long> vectorSongIds = new ArrayList<>();
        for (Long songId : allSongIds) {
            float[] sv = tagVectorService.getVector(songId);
            if (sv != null) {
                allVectors.add(sv);
                vectorSongIds.add(songId);
            }
        }

        float[] globalMean = computeGlobalMean(allVectors, userVector.length);

        Map<Integer, List<ScoredSong>> langBuckets = new HashMap<>();
        for (int langDim : langDims) {
            langBuckets.put(langDim, new ArrayList<>());
        }
        langBuckets.put(-1, new ArrayList<>());

        for (int idx = 0; idx < vectorSongIds.size(); idx++) {
            Long songId = vectorSongIds.get(idx);
            if (playedSongIds.contains(songId)) continue;

            float[] songVector = allVectors.get(idx);
            int langDim = tagVectorService.getPrimaryLanguageDim(songVector);
            double similarity = adjustedWeightedCosine(userVector, songVector, globalMean, weights, nonLangDims);

            langBuckets.computeIfAbsent(langDim, k -> new ArrayList<>())
                    .add(new ScoredSong(songId, similarity));
        }

        for (List<ScoredSong> bucket : langBuckets.values()) {
            bucket.sort(Comparator.comparingDouble(s -> -s.similarity));
        }

        List<Map<String, Object>> result = allocateByLanguage(langBuckets, userLangDist, langDims, limit);
        result.sort(Comparator.comparingDouble(m -> -((Number) m.get("similarity")).doubleValue()));
        return result;
    }

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
            Set<Long> alreadyPicked = result.stream()
                    .map(m -> ((Number) m.get("songId")).longValue())
                    .collect(Collectors.toSet());
            List<ScoredSong> all = new ArrayList<>();
            for (List<ScoredSong> bucket : langBuckets.values()) {
                for (ScoredSong s : bucket) {
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

        return result;
    }

    private float[] computeGlobalMean(List<float[]> vectors, int dims) {
        float[] mean = new float[dims];
        if (vectors.isEmpty()) return mean;
        for (float[] v : vectors) {
            for (int i = 0; i < Math.min(v.length, dims); i++) {
                mean[i] += v[i];
            }
        }
        for (int i = 0; i < dims; i++) {
            mean[i] /= vectors.size();
        }
        return mean;
    }

    /**
     * Adjusted (mean-centered) weighted cosine similarity.
     * Subtracting the global mean removes shared biases (e.g. all songs having
     * similar Mood or Vocal values), focusing on dimensions where songs actually
     * deviate from the library average.
     */
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
