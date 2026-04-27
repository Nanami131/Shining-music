package org.L2.recommend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.L2.recommend.strategy.impl.ContentBasedStrategy;
import org.L2.recommend.strategy.impl.ItemCFStrategy;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService 单元测试")
class RecommendationServiceTest {

    @InjectMocks
    private RecommendationService recommendationService;

    @Mock
    private ContentBasedStrategy contentBasedStrategy;

    @Mock
    private ItemCFStrategy itemCFStrategy;

    @Mock
    private UserPreferenceService userPreferenceService;

    @Mock
    private StatisticsClient statisticsClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void wireRedis() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("recommendContentBased()")
    class ContentBasedTests {

        @Test
        @DisplayName("缓存命中 — 直接返回缓存数据")
        void cacheHit() throws Exception {
            List<Map<String, Object>> cached = List.of(Map.of("songId", 1L, "similarity", 0.9));
            String json = objectMapper.writeValueAsString(cached);
            when(valueOperations.get(startsWith("recommend:daily:cb:"))).thenReturn(json);

            R result = recommendationService.recommendContentBased(1L, 10, false);
            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("今日缓存"));
            verify(contentBasedStrategy, never()).recommend(anyLong(), anyInt(), anySet());
        }

        @Test
        @DisplayName("缓存为空 — 忽略缓存并降级到热门歌曲")
        void emptyCacheFallback() throws Exception {
            when(valueOperations.get(startsWith("recommend:daily:cb:"))).thenReturn("[]");
            when(contentBasedStrategy.isAvailable(1L)).thenReturn(false);
            when(statisticsClient.getGlobalTopSongs(10)).thenReturn(
                    R.success("ok", List.of(Map.of("songId", 11, "playCount", 100))));

            R result = recommendationService.recommendContentBased(1L, 10, false);

            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("热门歌曲"));
            verify(stringRedisTemplate).delete("recommend:daily:cb:1");
        }

        @Test
        @DisplayName("无缓存且策略可用 — 调用策略并写缓存")
        void noCacheStrategyAvailable() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(contentBasedStrategy.isAvailable(1L)).thenReturn(true);
            when(userPreferenceService.persistSnapshot(1L)).thenReturn(true);
            when(statisticsClient.getPlayedSongIds(1L)).thenReturn(R.success("ok", Collections.emptyList()));
            when(contentBasedStrategy.recommend(eq(1L), eq(10), anySet()))
                    .thenReturn(List.of(Map.of("songId", 42L, "similarity", 0.8)));

            R result = recommendationService.recommendContentBased(1L, 10, false);
            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("内容推荐"));
            verify(valueOperations).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("策略不可用 — 降级到热门歌曲")
        void strategyUnavailableFallback() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(contentBasedStrategy.isAvailable(1L)).thenReturn(false);
            when(statisticsClient.getGlobalTopSongs(10)).thenReturn(
                    R.success("ok", List.of(Map.of("songId", 11, "playCount", 100))));

            R result = recommendationService.recommendContentBased(1L, 10, false);
            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("热门歌曲"));
        }

        @Test
        @DisplayName("策略可用但推荐为空 — 降级到热门歌曲")
        void emptyResultsFallback() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(contentBasedStrategy.isAvailable(1L)).thenReturn(true);
            when(userPreferenceService.persistSnapshot(1L)).thenReturn(true);
            when(statisticsClient.getPlayedSongIds(1L)).thenReturn(R.success("ok", Collections.emptyList()));
            when(contentBasedStrategy.recommend(eq(1L), eq(10), anySet())).thenReturn(Collections.emptyList());
            when(statisticsClient.getGlobalTopSongs(10)).thenReturn(
                    R.success("ok", List.of(Map.of("songId", 11, "playCount", 100))));

            R result = recommendationService.recommendContentBased(1L, 10, false);

            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("热门歌曲"));
            verify(valueOperations, never()).set(anyString(), eq("[]"), any());
        }
    }

    @Nested
    @DisplayName("recommendItemCF()")
    class ItemCFTests {

        @Test
        @DisplayName("缓存命中 — 直接返回")
        void cacheHit() throws Exception {
            List<Map<String, Object>> cached = List.of(Map.of("songId", 5L, "similarity", 0.7));
            String json = objectMapper.writeValueAsString(cached);
            when(valueOperations.get(startsWith("recommend:daily:cf:"))).thenReturn(json);

            R result = recommendationService.recommendItemCF(1L, 10, false);
            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("今日缓存"));
        }

        @Test
        @DisplayName("矩阵不可用 — 返回错误提示")
        void matrixUnavailable() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(itemCFStrategy.isAvailable(1L)).thenReturn(false);

            R result = recommendationService.recommendItemCF(1L, 10, false);
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("矩阵"));
        }

        @Test
        @DisplayName("矩阵可用但推荐为空 — 降级到热门")
        void emptyResultsFallback() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(itemCFStrategy.isAvailable(1L)).thenReturn(true);
            when(statisticsClient.getPlayedSongIds(1L)).thenReturn(R.success("ok", Collections.emptyList()));
            when(itemCFStrategy.recommend(eq(1L), eq(10), anySet())).thenReturn(Collections.emptyList());
            when(statisticsClient.getGlobalTopSongs(10)).thenReturn(
                    R.success("ok", List.of(Map.of("songId", 99, "playCount", 50))));

            R result = recommendationService.recommendItemCF(1L, 10, false);
            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("热门歌曲"));
        }

        @Test
        @DisplayName("矩阵可用且有推荐 — 正常返回")
        void normalRecommendation() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(itemCFStrategy.isAvailable(1L)).thenReturn(true);
            when(statisticsClient.getPlayedSongIds(1L)).thenReturn(R.success("ok", Collections.emptyList()));
            when(itemCFStrategy.recommend(eq(1L), eq(10), anySet()))
                    .thenReturn(List.of(Map.of("songId", 77L, "similarity", 0.6)));

            R result = recommendationService.recommendItemCF(1L, 10, false);
            assertTrue(result.getPassed());
            assertTrue(result.getMessage().contains("协同过滤"));
        }
    }

    @Nested
    @DisplayName("rebuildItemCFMatrix()")
    class RebuildTests {

        @Test
        @DisplayName("委托 ItemCFStrategy 重建并返回结果")
        @SuppressWarnings("unchecked")
        void delegatesToStrategy() {
            when(itemCFStrategy.rebuildSimilarityMatrix()).thenReturn(42);
            R result = recommendationService.rebuildItemCFMatrix();
            assertTrue(result.getPassed());
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertEquals(42, data.get("songsWithSimilarity"));
        }
    }

    @Nested
    @DisplayName("recommend() 入口方法")
    class RecommendEntryTests {

        @Test
        @DisplayName("recommend() 委托给 recommendContentBased()")
        void delegatesToContentBased() {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(contentBasedStrategy.isAvailable(1L)).thenReturn(false);
            when(statisticsClient.getGlobalTopSongs(10)).thenReturn(
                    R.success("ok", Collections.emptyList()));

            R result = recommendationService.recommend(1L, 10, false);
            assertTrue(result.getPassed());
            verify(contentBasedStrategy).isAvailable(1L);
        }
    }
}
