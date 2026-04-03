package org.L2.statistics.application.service;

import org.L2.common.R;
import org.L2.common.event.EventInfo;
import org.L2.common.event.PlaybackEventMessage;
import org.L2.common.event.PlaybackInfo;
import org.L2.common.event.UserInfo;
import org.L2.statistics.domain.model.UserDailyPlayCount;
import org.L2.statistics.domain.model.UserSongPlayRecord;
import org.L2.statistics.domain.model.UserTopSong;
import org.L2.statistics.domain.service.UserPlayRecordDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPlayStatisticsService 单元测试")
class UserPlayStatisticsServiceTest {

    @InjectMocks
    private UserPlayStatisticsService service;

    @Mock
    private UserPlayRecordDomainService domainService;

    @Nested
    @DisplayName("saveFromEvent()")
    class SaveFromEventTests {

        @Test
        @DisplayName("null消息 — 忽略不报错")
        void nullMessage() {
            assertDoesNotThrow(() -> service.saveFromEvent(null));
            verifyNoInteractions(domainService);
        }

        @Test
        @DisplayName("user或playback为空 — 忽略")
        void incompleteMessage() {
            PlaybackEventMessage msg = new PlaybackEventMessage().setUser(null).setPlayback(null);
            assertDoesNotThrow(() -> service.saveFromEvent(msg));
            verifyNoInteractions(domainService);
        }

        @Test
        @DisplayName("userId为null — 忽略")
        void nullUserId() {
            PlaybackEventMessage msg = new PlaybackEventMessage()
                    .setUser(new UserInfo().setUserId(null))
                    .setPlayback(new PlaybackInfo().setSongId(1L));
            assertDoesNotThrow(() -> service.saveFromEvent(msg));
            verifyNoInteractions(domainService);
        }

        @Test
        @DisplayName("SONG_PLAY_END事件 — 调用updatePlayEndRecord")
        void playEndEvent() {
            PlaybackEventMessage msg = new PlaybackEventMessage()
                    .setEvent(new EventInfo().setEventName("SONG_PLAY_END"))
                    .setUser(new UserInfo().setUserId(1L))
                    .setPlayback(new PlaybackInfo()
                            .setSongId(10L)
                            .setPlaySessionId("sess-1")
                            .setDurationSec(180)
                            .setTotalDurationSec(240)
                            .setCompleted(false)
                            .setSource("recommend"));

            service.saveFromEvent(msg);

            verify(domainService).updatePlayEndRecord(
                    eq(1L), eq(10L), eq("sess-1"), eq(180), eq(240), eq(false), eq("recommend"));
            verify(domainService, never()).saveFullRecord(any());
        }

        @Test
        @DisplayName("SONG_PLAY_START事件 — 调用saveFullRecord")
        void playStartEvent() {
            LocalDateTime now = LocalDateTime.now();
            PlaybackEventMessage msg = new PlaybackEventMessage()
                    .setEvent(new EventInfo().setEventName("SONG_PLAY_START").setOccurredAt(now))
                    .setUser(new UserInfo().setUserId(1L))
                    .setPlayback(new PlaybackInfo().setSongId(10L).setPlaySessionId("sess-2"));

            service.saveFromEvent(msg);

            ArgumentCaptor<UserSongPlayRecord> captor = ArgumentCaptor.forClass(UserSongPlayRecord.class);
            verify(domainService).saveFullRecord(captor.capture());
            UserSongPlayRecord saved = captor.getValue();
            assertEquals(1L, saved.getUserId());
            assertEquals(10L, saved.getSongId());
            assertEquals("sess-2", saved.getPlaySessionId());
        }

        @Test
        @DisplayName("event.occurredAt为null — 使用LocalDateTime.now()")
        void nullOccurredAt() {
            PlaybackEventMessage msg = new PlaybackEventMessage()
                    .setEvent(new EventInfo().setEventName("SONG_PLAY_START").setOccurredAt(null))
                    .setUser(new UserInfo().setUserId(1L))
                    .setPlayback(new PlaybackInfo().setSongId(10L));

            service.saveFromEvent(msg);

            ArgumentCaptor<UserSongPlayRecord> captor = ArgumentCaptor.forClass(UserSongPlayRecord.class);
            verify(domainService).saveFullRecord(captor.capture());
            assertNotNull(captor.getValue().getPlayedAt());
        }
    }

    @Nested
    @DisplayName("getUserPlayCount()")
    class PlayCountTests {

        @Test
        @DisplayName("userId为null — 返回错误")
        void nullUserId() {
            R result = service.getUserPlayCount(null, null, null);
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("用户ID不能为空"));
        }

        @Test
        @DisplayName("正常查询 — 返回统计值")
        void normalCount() {
            when(domainService.countByUserAndRange(eq(1L), any(), any())).thenReturn(42L);
            R result = service.getUserPlayCount(1L, null, null);
            assertTrue(result.getPassed());
            assertEquals(42L, result.getData());
        }
    }

    @Nested
    @DisplayName("getUserTopSongs()")
    class TopSongsTests {

        @Test
        @DisplayName("userId为null — 返回错误")
        void nullUserId() {
            R result = service.getUserTopSongs(null, "WEEK", 10);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("limit为null — 使用默认值10")
        void nullLimit() {
            when(domainService.listTopSongsByUser(eq(1L), any(), any(), eq(10)))
                    .thenReturn(Collections.emptyList());

            R result = service.getUserTopSongs(1L, "WEEK", null);
            assertTrue(result.getPassed());
            verify(domainService).listTopSongsByUser(eq(1L), any(), any(), eq(10));
        }

        @Test
        @DisplayName("limit超过20 — 截断为20")
        void limitCapped() {
            when(domainService.listTopSongsByUser(eq(1L), any(), any(), eq(20)))
                    .thenReturn(Collections.emptyList());

            R result = service.getUserTopSongs(1L, "WEEK", 100);
            assertTrue(result.getPassed());
            verify(domainService).listTopSongsByUser(eq(1L), any(), any(), eq(20));
        }

        @Test
        @DisplayName("dimension为非法值 — 降级为WEEK")
        void invalidDimension() {
            when(domainService.listTopSongsByUser(eq(1L), any(), any(), eq(5)))
                    .thenReturn(Collections.emptyList());

            R result = service.getUserTopSongs(1L, "INVALID", 5);
            assertTrue(result.getPassed());
        }
    }

    @Nested
    @DisplayName("getUserTopSingers()")
    class TopSingersTests {

        @Test
        @DisplayName("userId为null — 返回错误")
        void nullUserId() {
            R result = service.getUserTopSingers(null, 10);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("limit小于1 — 修正为1")
        void limitFloor() {
            when(domainService.listTopSingersByUser(eq(1L), eq(1)))
                    .thenReturn(Collections.emptyList());

            R result = service.getUserTopSingers(1L, -5);
            assertTrue(result.getPassed());
            verify(domainService).listTopSingersByUser(1L, 1);
        }
    }

    @Nested
    @DisplayName("getDistinctPlayedSongIds()")
    class DistinctSongIdsTests {

        @Test
        @DisplayName("userId为null — 返回错误")
        void nullUserId() {
            R result = service.getDistinctPlayedSongIds(null);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("正常查询 — 返回去重ID列表")
        void normalQuery() {
            when(domainService.distinctSongIdsByUser(1L)).thenReturn(List.of(10L, 20L, 30L));
            R result = service.getDistinctPlayedSongIds(1L);
            assertTrue(result.getPassed());
            assertEquals(List.of(10L, 20L, 30L), result.getData());
        }
    }
}
