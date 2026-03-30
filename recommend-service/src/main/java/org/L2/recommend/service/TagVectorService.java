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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TagVectorService {

    private static final Logger log = LoggerFactory.getLogger(TagVectorService.class);
    private static final String VECTOR_KEY_PREFIX = "song:vector:";

    @Autowired
    private TagDefinitionMapper tagDefinitionMapper;

    @Autowired
    private SongTagMapper songTagMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private volatile List<TagDefinition> dimensions;

    @PostConstruct
    public void init() {
        reloadDimensions();
    }

    public void reloadDimensions() {
        this.dimensions = tagDefinitionMapper.selectAllOrderByDimIndex();
        validateDimensions();
        log.info("Loaded {} tag dimensions", dimensions.size());
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
}
