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

    /**
     * Ribecky, Abeßer & Lukashevich (2021) ISMIR — Triplet prediction accuracy per dimension.
     * Language is handled as a pre-filter, so it gets weight 0 in vector similarity.
     */
    private static final Map<String, Double> RIBECKY_CATEGORY_SCORES = Map.of(
            "language", 0.0,
            "source",   0.7535,
            "mood",     0.7535,
            "vocal",    0.6901,
            "audio",    0.5798,
            "era",      0.7559
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

        Map<String, List<TagDefinition>> byCategory = dimensions.stream()
                .collect(Collectors.groupingBy(TagDefinition::getCategory));

        for (Map.Entry<String, List<TagDefinition>> entry : byCategory.entrySet()) {
            String cat = entry.getKey();
            List<TagDefinition> dims = entry.getValue();
            double catScore = RIBECKY_CATEGORY_SCORES.getOrDefault(cat, 0.5);
            double perDimWeight = dims.isEmpty() ? 0 : catScore / dims.size();

            for (TagDefinition dim : dims) {
                weights[dim.getDimIndex()] = (float) perDimWeight;
                if ("language".equals(cat)) {
                    langDims.add(dim.getDimIndex());
                } else {
                    nonLangDims.add(dim.getDimIndex());
                }
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
