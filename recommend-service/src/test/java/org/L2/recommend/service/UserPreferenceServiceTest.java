package org.L2.recommend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.L2.recommend.domain.model.UserPreference;
import org.L2.recommend.infrastructure.UserPreferenceMapper;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPreferenceService 单元测试")
class UserPreferenceServiceTest {

    @InjectMocks
    private UserPreferenceService userPreferenceService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TagVectorService tagVectorService;

    @Mock
    private UserPreferenceMapper userPreferenceMapper;

    @Mock
    private StatisticsClient statisticsClient;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUpRedis() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("calculateWeight() — 通过反射测试权重算法")
    class CalculateWeightTests {

        private Method calcMethod;

        @BeforeEach
        void setUp() throws Exception {
            calcMethod = UserPreferenceService.class.getDeclaredMethod(
                    "calculateWeight", Integer.class, Integer.class, Boolean.class);
            calcMethod.setAccessible(true);
        }

        @Test
        @DisplayName("完整播放(completed=true) — 权重1.0")
        void completedPlay() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, null, null, true);
            assertEquals(1.0, w, 1e-6);
        }

        @Test
        @DisplayName("未完成且无时长信息 — 权重0.3")
        void incompleteNoInfo() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, null, null, false);
            assertEquals(0.3, w, 1e-6);
        }

        @Test
        @DisplayName("totalDuration=0 — 按completed判断")
        void zeroDuration() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, 100, 0, null);
            assertEquals(0.3, w, 1e-6);
        }

        @Test
        @DisplayName("听了不到10% — 权重为0")
        void lessThan10Percent() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, 5, 200, null);
            assertEquals(0.0, w, 1e-6);
        }

        @Test
        @DisplayName("听了恰好10% — 权重为0（边界值）")
        void exactly10Percent() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, 20, 200, null);
            assertEquals(0.0, w, 1e-6);
        }

        @Test
        @DisplayName("听了50% — 权重在0和1之间")
        void halfListened() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, 100, 200, null);
            assertTrue(w > 0.0 && w < 1.0);
            double expected = (0.5 - 0.1) / (0.8 - 0.1);
            assertEquals(expected, w, 1e-6);
        }

        @Test
        @DisplayName("听了80%以上 — 权重为1.0")
        void over80Percent() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, 180, 200, null);
            assertEquals(1.0, w, 1e-6);
        }

        @Test
        @DisplayName("听了100% — 权重为1.0")
        void fullListened() throws Exception {
            double w = (double) calcMethod.invoke(userPreferenceService, 200, 200, null);
            assertEquals(1.0, w, 1e-6);
        }
    }

    @Nested
    @DisplayName("getPreferenceVector()")
    class GetPreferenceTests {

        @Test
        @DisplayName("Redis有缓存 — 直接返回向量")
        void fromRedis() throws Exception {
            String json = objectMapper.writeValueAsString(
                    new UserPreferenceService.UserPrefData(new float[]{0.5f, 0.5f}, 10));
            when(valueOperations.get("user:preference:1")).thenReturn(json);

            float[] result = userPreferenceService.getPreferenceVector(1L);
            assertNotNull(result);
            assertEquals(2, result.length);
            assertEquals(0.5f, result[0], 1e-6);
        }

        @Test
        @DisplayName("Redis无缓存但MySQL有快照 — 从MySQL恢复")
        void fromMySQL() throws Exception {
            when(valueOperations.get("user:preference:1")).thenReturn(null);

            UserPreference snapshot = new UserPreference();
            snapshot.setUserId(1L);
            snapshot.setVectorJson("[0.3,0.7]");
            snapshot.setPlayCount(5.0);
            when(userPreferenceMapper.selectByUserId(1L)).thenReturn(snapshot);

            float[] result = userPreferenceService.getPreferenceVector(1L);
            assertNotNull(result);
            assertEquals(2, result.length);
            assertEquals(0.3f, result[0], 1e-4);
        }

        @Test
        @DisplayName("Redis和MySQL都无数据 — 返回null")
        void noData() {
            when(valueOperations.get("user:preference:1")).thenReturn(null);
            when(userPreferenceMapper.selectByUserId(1L)).thenReturn(null);

            float[] result = userPreferenceService.getPreferenceVector(1L);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("incrementalUpdate()")
    class IncrementalUpdateTests {

        @Test
        @DisplayName("歌曲向量不存在 — 跳过更新")
        void songVectorMissing() {
            when(tagVectorService.getVector(99L)).thenReturn(null);
            userPreferenceService.incrementalUpdate(1L, 99L, 1.0);
            verify(valueOperations, never()).set(anyString(), anyString());
        }

        @Test
        @DisplayName("首次播放 — 偏好等于歌曲向量")
        void firstPlay() throws Exception {
            float[] songVec = {0.5f, 0.8f};
            when(tagVectorService.getVector(10L)).thenReturn(songVec);
            when(valueOperations.get("user:preference:1")).thenReturn(null);

            userPreferenceService.incrementalUpdate(1L, 10L, 1.0);
            verify(valueOperations).set(eq("user:preference:1"), anyString());
        }
    }

    @Nested
    @DisplayName("rebuildFromHistory()")
    class RebuildTests {

        @Test
        @DisplayName("StatisticsClient不可用 — 返回0")
        void noClient() throws Exception {
            var field = UserPreferenceService.class.getDeclaredField("statisticsClient");
            field.setAccessible(true);
            Object original = field.get(userPreferenceService);
            field.set(userPreferenceService, null);

            int count = userPreferenceService.rebuildFromHistory(1L);
            assertEquals(0, count);

            field.set(userPreferenceService, original);
        }

        @Test
        @DisplayName("历史为空 — 返回0")
        void emptyHistory() {
            R historyResult = R.success("ok", List.of());
            when(statisticsClient.getPlayHistory(1L, 50)).thenReturn(historyResult);

            int count = userPreferenceService.rebuildFromHistory(1L);
            assertEquals(0, count);
        }
    }
}
