package org.L2.recommend.service;

import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.L2.recommend.infrastructure.SongTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService 单元测试")
class RecommendationServiceTest {

    @InjectMocks
    private RecommendationService recommendationService;

    @Mock
    private UserPreferenceService userPreferenceService;

    @Mock
    private TagVectorService tagVectorService;

    @Mock
    private SongTagMapper songTagMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StatisticsClient statisticsClient;

    @Nested
    @DisplayName("weightedCosineSimilarity() — 通过反射测试私有方法")
    class CosineSimilarityTests {

        private Method cosineMethod;

        @BeforeEach
        void setUp() throws Exception {
            cosineMethod = RecommendationService.class.getDeclaredMethod(
                    "weightedCosineSimilarity", float[].class, float[].class, float[].class, int[].class);
            cosineMethod.setAccessible(true);
        }

        @Test
        @DisplayName("相同向量 — 相似度为1.0")
        void identicalVectors() throws Exception {
            float[] a = {0, 0.8f, 0.6f, 0.3f};
            float[] weights = {0, 0.5f, 0.5f, 0.5f};
            int[] dims = {1, 2, 3};

            double result = (double) cosineMethod.invoke(recommendationService, a, a, weights, dims);
            assertEquals(1.0, result, 1e-6);
        }

        @Test
        @DisplayName("正交向量 — 相似度为0")
        void orthogonalVectors() throws Exception {
            float[] a = {0, 1.0f, 0, 0};
            float[] b = {0, 0, 1.0f, 0};
            float[] weights = {0, 0.5f, 0.5f, 0.5f};
            int[] dims = {1, 2, 3};

            double result = (double) cosineMethod.invoke(recommendationService, a, b, weights, dims);
            assertEquals(0.0, result, 1e-6);
        }

        @Test
        @DisplayName("零向量 — 相似度为0（避免除零）")
        void zeroVector() throws Exception {
            float[] a = {0, 0, 0};
            float[] b = {0, 1.0f, 0.5f};
            float[] weights = {0.5f, 0.5f, 0.5f};
            int[] dims = {0, 1, 2};

            double result = (double) cosineMethod.invoke(recommendationService, a, b, weights, dims);
            assertEquals(0.0, result, 1e-6);
        }

        @Test
        @DisplayName("权重为零的维度不参与计算")
        void zeroWeightDimension() throws Exception {
            float[] a = {1.0f, 0.8f};
            float[] b = {0.0f, 0.8f};
            float[] weights = {0.0f, 1.0f};
            int[] dims = {0, 1};

            double result = (double) cosineMethod.invoke(recommendationService, a, b, weights, dims);
            assertEquals(1.0, result, 1e-6);
        }

        @Test
        @DisplayName("不同权重 — 正确加权")
        void differentWeights() throws Exception {
            float[] a = {0.5f, 1.0f};
            float[] b = {1.0f, 0.5f};
            float[] weights = {1.0f, 1.0f};
            int[] dims = {0, 1};

            double result = (double) cosineMethod.invoke(recommendationService, a, b, weights, dims);
            assertTrue(result > 0.0 && result < 1.0);
        }
    }

    @Nested
    @DisplayName("extractLanguageDistribution() — 通过反射测试")
    class LanguageDistributionTests {

        private Method extractMethod;

        @BeforeEach
        void setUp() throws Exception {
            extractMethod = RecommendationService.class.getDeclaredMethod(
                    "extractLanguageDistribution", float[].class, int[].class);
            extractMethod.setAccessible(true);
        }

        @Test
        @DisplayName("有偏好 — 归一化到sum=1")
        @SuppressWarnings("unchecked")
        void normalDistribution() throws Exception {
            float[] userVector = {0.6f, 0.3f, 0.1f, 0, 0, 0};
            int[] langDims = {0, 1, 2};

            Map<Integer, Double> dist = (Map<Integer, Double>)
                    extractMethod.invoke(recommendationService, userVector, langDims);

            double sum = dist.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 1e-6);
            assertTrue(dist.get(0) > dist.get(1));
            assertTrue(dist.get(1) > dist.get(2));
        }

        @Test
        @DisplayName("全零偏好 — 均匀分配")
        @SuppressWarnings("unchecked")
        void zeroPreference() throws Exception {
            float[] userVector = {0, 0, 0, 0.5f, 0.5f};
            int[] langDims = {0, 1, 2};

            Map<Integer, Double> dist = (Map<Integer, Double>)
                    extractMethod.invoke(recommendationService, userVector, langDims);

            double expected = 1.0 / 3;
            for (double v : dist.values()) {
                assertEquals(expected, v, 1e-6);
            }
        }

        @Test
        @DisplayName("负值被裁剪为0")
        @SuppressWarnings("unchecked")
        void negativeValuesClipped() throws Exception {
            float[] userVector = {0.5f, -0.3f, 0};
            int[] langDims = {0, 1, 2};

            Map<Integer, Double> dist = (Map<Integer, Double>)
                    extractMethod.invoke(recommendationService, userVector, langDims);

            assertEquals(0.0, dist.get(1), 1e-6);
            assertEquals(1.0, dist.get(0), 1e-6);
        }
    }

    @Nested
    @DisplayName("recommend() — 端到端推荐流程")
    class RecommendTests {

        @Test
        @DisplayName("无偏好数据 — 返回空列表")
        void noPreference() {
            when(userPreferenceService.getPreferenceVector(1L)).thenReturn(null);

            R result = recommendationService.recommend(1L, 10);
            assertTrue(result.getPassed());
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("有偏好数据 — 返回推荐列表")
        @SuppressWarnings("unchecked")
        void withPreference() {
            float[] userVec = {0.8f, 0.1f, 0.1f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
            when(userPreferenceService.getPreferenceVector(1L)).thenReturn(userVec);
            when(userPreferenceService.persistSnapshot(1L)).thenReturn(true);

            when(tagVectorService.getLanguageDimIndices()).thenReturn(new int[]{0, 1, 2});
            when(tagVectorService.getNonLanguageDimIndices()).thenReturn(new int[]{3, 4, 5, 6, 7});
            when(tagVectorService.getDimensionWeights()).thenReturn(
                    new float[]{0, 0, 0, 0.15f, 0.15f, 0.14f, 0.12f, 0.15f});

            when(songTagMapper.selectAllDistinctSongIds()).thenReturn(List.of(10L, 20L, 30L));

            float[] song10 = {0.9f, 0.05f, 0.05f, 0.6f, 0.4f, 0.5f, 0.5f, 0.5f};
            float[] song20 = {0.1f, 0.8f, 0.1f, 0.3f, 0.7f, 0.5f, 0.2f, 0.8f};
            float[] song30 = {0.8f, 0.1f, 0.1f, 0.4f, 0.6f, 0.3f, 0.6f, 0.4f};
            when(tagVectorService.getVector(10L)).thenReturn(song10);
            when(tagVectorService.getVector(20L)).thenReturn(song20);
            when(tagVectorService.getVector(30L)).thenReturn(song30);
            when(tagVectorService.getPrimaryLanguageDim(song10)).thenReturn(0);
            when(tagVectorService.getPrimaryLanguageDim(song20)).thenReturn(1);
            when(tagVectorService.getPrimaryLanguageDim(song30)).thenReturn(0);

            R playedResult = R.success("ok", Collections.emptyList());
            when(statisticsClient.getPlayedSongIds(1L)).thenReturn(playedResult);

            R result = recommendationService.recommend(1L, 3);
            assertTrue(result.getPassed());
            List<Map<String, Object>> recs = (List<Map<String, Object>>) result.getData();
            assertFalse(recs.isEmpty());
            assertTrue(recs.size() <= 3);
        }

        @Test
        @DisplayName("已播放歌曲被过滤")
        @SuppressWarnings("unchecked")
        void playedSongsFiltered() {
            float[] userVec = {0.5f, 0.5f, 0, 0.5f};
            when(userPreferenceService.getPreferenceVector(1L)).thenReturn(userVec);
            when(userPreferenceService.persistSnapshot(1L)).thenReturn(true);

            when(tagVectorService.getLanguageDimIndices()).thenReturn(new int[]{0, 1, 2});
            when(tagVectorService.getNonLanguageDimIndices()).thenReturn(new int[]{3});
            when(tagVectorService.getDimensionWeights()).thenReturn(new float[]{0, 0, 0, 1.0f});
            when(songTagMapper.selectAllDistinctSongIds()).thenReturn(List.of(10L, 20L));

            float[] songVec = {0.5f, 0.5f, 0, 0.5f};
            lenient().when(tagVectorService.getVector(10L)).thenReturn(songVec);
            when(tagVectorService.getVector(20L)).thenReturn(songVec);
            lenient().when(tagVectorService.getPrimaryLanguageDim(songVec)).thenReturn(0);

            R playedResult = R.success("ok", List.of(10));
            when(statisticsClient.getPlayedSongIds(1L)).thenReturn(playedResult);

            R result = recommendationService.recommend(1L, 10);
            assertTrue(result.getPassed());
            List<Map<String, Object>> recs = (List<Map<String, Object>>) result.getData();
            for (Map<String, Object> rec : recs) {
                assertNotEquals(10L, ((Number) rec.get("songId")).longValue());
            }
        }
    }
}
