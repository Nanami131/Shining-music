package org.L2.recommend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.recommend.domain.model.TagDefinition;
import org.L2.recommend.infrastructure.SongTagMapper;
import org.L2.recommend.infrastructure.TagDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagVectorService 单元测试")
class TagVectorServiceTest {

    @InjectMocks
    private TagVectorService tagVectorService;

    @Mock
    private TagDefinitionMapper tagDefinitionMapper;

    @Mock
    private SongTagMapper songTagMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ValueOperations<String, String> valueOperations;

    private List<TagDefinition> buildDimensions() {
        return List.of(
                new TagDefinition().setId(1L).setName("japanese").setCategory("language").setDimIndex(0),
                new TagDefinition().setId(2L).setName("chinese").setCategory("language").setDimIndex(1),
                new TagDefinition().setId(3L).setName("english").setCategory("language").setDimIndex(2),
                new TagDefinition().setId(4L).setName("korean").setCategory("language").setDimIndex(3),
                new TagDefinition().setId(5L).setName("anime").setCategory("source").setDimIndex(4),
                new TagDefinition().setId(6L).setName("game").setCategory("source").setDimIndex(5),
                new TagDefinition().setId(7L).setName("jpop").setCategory("source").setDimIndex(6),
                new TagDefinition().setId(8L).setName("vocaloid").setCategory("source").setDimIndex(7),
                new TagDefinition().setId(9L).setName("indie").setCategory("source").setDimIndex(8),
                new TagDefinition().setId(10L).setName("other_source").setCategory("source").setDimIndex(9),
                new TagDefinition().setId(11L).setName("happy").setCategory("mood").setDimIndex(10),
                new TagDefinition().setId(12L).setName("sad").setCategory("mood").setDimIndex(11),
                new TagDefinition().setId(13L).setName("energetic").setCategory("mood").setDimIndex(12),
                new TagDefinition().setId(14L).setName("calm").setCategory("mood").setDimIndex(13),
                new TagDefinition().setId(15L).setName("romantic").setCategory("mood").setDimIndex(14),
                new TagDefinition().setId(16L).setName("dark").setCategory("mood").setDimIndex(15),
                new TagDefinition().setId(17L).setName("female").setCategory("vocal").setDimIndex(16),
                new TagDefinition().setId(18L).setName("male").setCategory("vocal").setDimIndex(17),
                new TagDefinition().setId(19L).setName("duet").setCategory("vocal").setDimIndex(18),
                new TagDefinition().setId(20L).setName("chorus").setCategory("vocal").setDimIndex(19),
                new TagDefinition().setId(21L).setName("instrumental").setCategory("vocal").setDimIndex(20),
                new TagDefinition().setId(22L).setName("pop").setCategory("audio").setDimIndex(21),
                new TagDefinition().setId(23L).setName("rock").setCategory("audio").setDimIndex(22),
                new TagDefinition().setId(24L).setName("electronic").setCategory("audio").setDimIndex(23),
                new TagDefinition().setId(25L).setName("ballad").setCategory("audio").setDimIndex(24),
                new TagDefinition().setId(26L).setName("acoustic").setCategory("audio").setDimIndex(25),
                new TagDefinition().setId(27L).setName("classical").setCategory("audio").setDimIndex(26),
                new TagDefinition().setId(28L).setName("era_recent").setCategory("era").setDimIndex(27))
                ;
    }

    @Nested
    @DisplayName("reloadDimensions()")
    class ReloadTests {

        @Test
        @DisplayName("28维正常加载 — 权重和维度数正确")
        void normalLoad() {
            when(tagDefinitionMapper.selectAllOrderByDimIndex()).thenReturn(buildDimensions());

            tagVectorService.reloadDimensions();

            assertEquals(28, tagVectorService.getDimensionCount());
            assertEquals(28, tagVectorService.getDimensions().size());

            float[] weights = tagVectorService.getDimensionWeights();
            assertEquals(28, weights.length);
            assertEquals(0.0f, weights[0], 1e-6, "language维度权重应为0");
            assertTrue(weights[4] > 0, "source维度权重应大于0");
        }

        @Test
        @DisplayName("语言维度索引正确分离")
        void languageDimsSeparated() {
            when(tagDefinitionMapper.selectAllOrderByDimIndex()).thenReturn(buildDimensions());
            tagVectorService.reloadDimensions();

            int[] langDims = tagVectorService.getLanguageDimIndices();
            int[] nonLangDims = tagVectorService.getNonLanguageDimIndices();
            assertEquals(4, langDims.length);
            assertEquals(24, nonLangDims.length);
        }

        @Test
        @DisplayName("dimIndex不连续 — 抛出异常")
        void discontinuousDimIndex() {
            List<TagDefinition> dims = List.of(
                    new TagDefinition().setId(1L).setName("a").setCategory("language").setDimIndex(0),
                    new TagDefinition().setId(2L).setName("b").setCategory("language").setDimIndex(2)
            );
            when(tagDefinitionMapper.selectAllOrderByDimIndex()).thenReturn(dims);

            assertThrows(IllegalStateException.class, () -> tagVectorService.reloadDimensions());
        }
    }

    @Nested
    @DisplayName("getDimensionWeights() — Ribecky权重验证")
    class WeightTests {

        @BeforeEach
        void setUp() {
            when(tagDefinitionMapper.selectAllOrderByDimIndex()).thenReturn(buildDimensions());
            tagVectorService.reloadDimensions();
        }

        @Test
        @DisplayName("language权重全为0")
        void languageWeightsZero() {
            float[] weights = tagVectorService.getDimensionWeights();
            for (int i = 0; i < 4; i++) {
                assertEquals(0.0f, weights[i], 1e-6);
            }
        }

        @Test
        @DisplayName("source权重 = 0.7535 / 6")
        void sourceWeights() {
            float[] weights = tagVectorService.getDimensionWeights();
            float expected = (float) (0.7535 / 6.0);
            for (int i = 4; i <= 9; i++) {
                assertEquals(expected, weights[i], 1e-4);
            }
        }

        @Test
        @DisplayName("mood权重 = 0.7535 / 6")
        void moodWeights() {
            float[] weights = tagVectorService.getDimensionWeights();
            float expected = (float) (0.7535 / 6.0);
            for (int i = 10; i <= 15; i++) {
                assertEquals(expected, weights[i], 1e-4);
            }
        }

        @Test
        @DisplayName("vocal权重 = 0.6901 / 5")
        void vocalWeights() {
            float[] weights = tagVectorService.getDimensionWeights();
            float expected = (float) (0.6901 / 5.0);
            for (int i = 16; i <= 20; i++) {
                assertEquals(expected, weights[i], 1e-4);
            }
        }

        @Test
        @DisplayName("audio权重 = 0.5798 / 6")
        void audioWeights() {
            float[] weights = tagVectorService.getDimensionWeights();
            float expected = (float) (0.5798 / 6.0);
            for (int i = 21; i <= 26; i++) {
                assertEquals(expected, weights[i], 1e-4);
            }
        }

        @Test
        @DisplayName("era权重 = 0.7559 / 1")
        void eraWeights() {
            float[] weights = tagVectorService.getDimensionWeights();
            float expected = (float) 0.7559;
            assertEquals(expected, weights[27], 1e-4);
        }
    }

    @Nested
    @DisplayName("getPrimaryLanguageDim()")
    class PrimaryLanguageTests {

        @BeforeEach
        void setUp() {
            when(tagDefinitionMapper.selectAllOrderByDimIndex()).thenReturn(buildDimensions());
            tagVectorService.reloadDimensions();
        }

        @Test
        @DisplayName("日语最高 — 返回dimIndex=0")
        void japaneseHighest() {
            float[] vector = new float[28];
            vector[0] = 0.9f; // japanese
            vector[1] = 0.1f; // chinese
            assertEquals(0, tagVectorService.getPrimaryLanguageDim(vector));
        }

        @Test
        @DisplayName("中文最高 — 返回dimIndex=1")
        void chineseHighest() {
            float[] vector = new float[28];
            vector[0] = 0.2f;
            vector[1] = 0.8f;
            assertEquals(1, tagVectorService.getPrimaryLanguageDim(vector));
        }

        @Test
        @DisplayName("全零 — 返回-1")
        void allZero() {
            float[] vector = new float[28];
            assertEquals(-1, tagVectorService.getPrimaryLanguageDim(vector));
        }
    }

    @Nested
    @DisplayName("getVector() / rebuildVector()")
    class VectorCacheTests {

        @BeforeEach
        void setUp() {
            lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(tagDefinitionMapper.selectAllOrderByDimIndex()).thenReturn(buildDimensions());
            tagVectorService.reloadDimensions();
        }

        @Test
        @DisplayName("Redis有缓存 — 返回解析后的向量")
        void vectorFromRedis() {
            when(valueOperations.get("song:vector:10")).thenReturn("[0.5,0.3,0.2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]");

            float[] vec = tagVectorService.getVector(10L);
            assertNotNull(vec);
            assertEquals(28, vec.length);
            assertEquals(0.5f, vec[0], 1e-4);
        }

        @Test
        @DisplayName("Redis无缓存 — 返回null")
        void vectorMissing() {
            when(valueOperations.get("song:vector:99")).thenReturn(null);
            assertNull(tagVectorService.getVector(99L));
        }
    }
}
