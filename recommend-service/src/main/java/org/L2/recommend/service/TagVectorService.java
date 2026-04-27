package org.L2.recommend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.recommend.domain.model.SongTag;
import org.L2.recommend.domain.model.TagDefinition;
import org.L2.recommend.infrastructure.SongTagMapper;
import org.L2.recommend.infrastructure.TagDefinitionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TagVectorService {

    private static final Logger log = LoggerFactory.getLogger(TagVectorService.class);
    private static final String VECTOR_KEY_PREFIX = "song:vector:";
    private static final String SIMILAR_CACHE_PREFIX = "song:similar:";

    /**
     * Per-dimension weights for weighted cosine similarity.
     * Language dims are 0 (handled as a pre-filter outside similarity).
     */
    private static final Map<String, Double> DIMENSION_WEIGHTS = Map.ofEntries(
            Map.entry("lang_ja",         0.00),
            Map.entry("lang_zh",         0.00),
            Map.entry("lang_en",         0.00),
            Map.entry("instrumental",    0.00),
            Map.entry("src_anime",       0.14),
            Map.entry("src_game",        0.07),
            Map.entry("src_vocaloid",    0.21),
            Map.entry("src_original",    0.02),
            Map.entry("src_cover",       0.06),
            Map.entry("src_idol",        0.10),
            Map.entry("mood_valence",    0.24),
            Map.entry("mood_arousal",    0.54),
            Map.entry("mood_dominance",  0.18),
            Map.entry("mood_joy",        0.28),
            Map.entry("mood_anger",      0.08),
            Map.entry("mood_sadness",    0.36),
            Map.entry("mood_fear",       0.04),
            Map.entry("mood_disgust",    0.03),
            Map.entry("vocal_male",      1.00),
            Map.entry("vocal_female",    1.00),
            Map.entry("vocal_synth",     0.32),
            Map.entry("tempo",           0.62),
            Map.entry("energy",          0.72),
            Map.entry("danceability",    0.47),
            Map.entry("acousticness",    0.41),
            Map.entry("valence",         0.16),
            Map.entry("speechiness",     0.12),
            Map.entry("era_normalized",  0.05)
    );

    @Autowired
    private TagDefinitionMapper tagDefinitionMapper;

    @Autowired
    private SongTagMapper songTagMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private volatile List<TagDefinition> dimensions;
    private volatile float[] dimensionWeights;
    private volatile int[] languageDimIndices;
    private volatile int[] nonLanguageDimIndices;

    @PostConstruct
    public void init() {
        reloadDimensions();
    }

    public void reloadDimensions() {
        this.dimensions = tagDefinitionMapper.selectAllOrderByDimIndex();
        validateDimensions();
        buildDimensionWeights();
        log.info("Loaded {} tag dimensions, {} language dims, {} similarity dims",
                dimensions.size(), languageDimIndices.length, nonLanguageDimIndices.length);
    }

    private void buildDimensionWeights() {
        int n = dimensions.size();
        float[] weights = new float[n];
        List<Integer> langDims = new ArrayList<>();
        List<Integer> nonLangDims = new ArrayList<>();

        for (TagDefinition dim : dimensions) {
            double w = DIMENSION_WEIGHTS.getOrDefault(dim.getName(), 0.1);
            weights[dim.getDimIndex()] = (float) w;
            if ("language".equals(dim.getCategory())) {
                langDims.add(dim.getDimIndex());
            } else {
                nonLangDims.add(dim.getDimIndex());
            }
        }

        this.dimensionWeights = weights;
        this.languageDimIndices = langDims.stream().mapToInt(Integer::intValue).toArray();
        this.nonLanguageDimIndices = nonLangDims.stream().mapToInt(Integer::intValue).toArray();
    }

    private void validateDimensions() {
        for (int i = 0; i < dimensions.size(); i++) {
            if (dimensions.get(i).getDimIndex() != i) {
                log.error("dim_index discontinuity: expected {} but got {} for tag '{}'",
                        i, dimensions.get(i).getDimIndex(), dimensions.get(i).getName());
                throw new IllegalStateException(
                        "tag_definitions dim_index 不连续：期望 " + i +
                        " 但实际为 " + dimensions.get(i).getDimIndex() +
                        "（标签: " + dimensions.get(i).getName() + "）");
            }
        }
    }

    public int getDimensionCount() {
        return dimensions.size();
    }

    public List<TagDefinition> getDimensions() {
        return dimensions;
    }

    public void rebuildVector(Long songId) {
        List<TagDefinition> dims = this.dimensions;
        int n = dims.size();
        float[] vector = new float[n];

        Map<Long, Float> tagValues = new HashMap<>();
        List<SongTag> tags = songTagMapper.selectBySongId(songId);
        for (SongTag st : tags) {
            tagValues.put(st.getTagId(), st.getValue());
        }

        for (TagDefinition dim : dims) {
            Float val = tagValues.get(dim.getId());
            vector[dim.getDimIndex()] = (val != null) ? val : 0.0f;
        }

        try {
            String json = objectMapper.writeValueAsString(vector);
            stringRedisTemplate.opsForValue().set(VECTOR_KEY_PREFIX + songId, json, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Failed to store vector for song {}", songId, e);
            throw new RuntimeException("向量写入 Redis 失败: " + e.getMessage(), e);
        }

        try {
            stringRedisTemplate.delete(SIMILAR_CACHE_PREFIX + songId);
        } catch (Exception e) {
            log.warn("Failed to invalidate similar-songs cache for songId={}", songId, e);
        }
    }

    public R rebuildAll() {
        try {
            List<Long> songIds = songTagMapper.selectAllDistinctSongIds();
            int count = 0;
            for (Long songId : songIds) {
                rebuildVector(songId);
                count++;
            }
            log.info("Rebuilt vectors for {} songs", count);
            return R.success("向量全量重建完成，共 " + count + " 首歌");
        } catch (Exception e) {
            log.error("rebuild-all failed", e);
            return R.error("向量全量重建失败: " + e.getMessage());
        }
    }

    public float[] getVector(Long songId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(VECTOR_KEY_PREFIX + songId);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            log.error("Failed to read vector for song {}", songId, e);
            return null;
        }
    }

    /**
     * Batch-fetch vectors for multiple songs using Redis multiGet (single round-trip).
     * Returns a map from songId to its vector; songs without vectors are omitted.
     */
    public Map<Long, float[]> getVectors(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) return Collections.emptyMap();
        List<String> keys = songIds.stream()
                .map(id -> VECTOR_KEY_PREFIX + id)
                .collect(Collectors.toList());
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<Long, float[]> result = new HashMap<>();
        if (values == null) return result;
        for (int i = 0; i < songIds.size(); i++) {
            String json = values.get(i);
            if (json == null) continue;
            try {
                result.put(songIds.get(i), objectMapper.readValue(json, float[].class));
            } catch (Exception e) {
                log.warn("Failed to parse vector for songId={}", songIds.get(i), e);
            }
        }
        return result;
    }

    public float[] getDimensionWeights() {
        return dimensionWeights;
    }

    public int[] getLanguageDimIndices() {
        return languageDimIndices;
    }

    public int[] getNonLanguageDimIndices() {
        return nonLanguageDimIndices;
    }

    /**
     * Extract the primary language index from a song vector.
     * Returns the language dim index with the highest value, or -1 if all zero.
     */
    public int getPrimaryLanguageDim(float[] vector) {
        int bestDim = -1;
        float bestVal = 0f;
        for (int idx : languageDimIndices) {
            if (idx < vector.length && vector[idx] > bestVal) {
                bestVal = vector[idx];
                bestDim = idx;
            }
        }
        return bestDim;
    }
}
