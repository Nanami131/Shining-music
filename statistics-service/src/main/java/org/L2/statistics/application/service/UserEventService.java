package org.L2.statistics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.statistics.domain.model.UserEventLog;
import org.L2.statistics.infrastructure.mapper.UserEventLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventService {

    private final UserEventLogMapper userEventLogMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public R saveEvent(Long userId, String eventType, String targetType,
                       Long targetId, Map<String, Object> extraData) {
        if (userId == null || eventType == null) {
            return R.error("userId and eventType are required");
        }

        String extraJson = null;
        if (extraData != null && !extraData.isEmpty()) {
            try {
                extraJson = objectMapper.writeValueAsString(extraData);
            } catch (Exception e) {
                log.warn("Failed to serialize extraData, ignore", e);
            }
        }

        UserEventLog record = new UserEventLog()
                .setUserId(userId)
                .setEventType(eventType)
                .setTargetType(targetType)
                .setTargetId(targetId)
                .setExtraData(extraJson)
                .setCreatedAt(LocalDateTime.now());

        userEventLogMapper.insert(record);
        return R.success("事件记录成功");
    }

    public R getRecentEvents(Long userId, String eventType, int limit) {
        if (userId == null) {
            return R.error("userId is required");
        }
        List<UserEventLog> events = userEventLogMapper.selectByUser(userId, eventType,
                Math.min(limit, 100));
        return R.success("获取成功", events);
    }

    public R getTopSearchKeywords(Long userId, int limit) {
        if (userId == null) {
            return R.error("userId is required");
        }
        List<Map<String, Object>> keywords = userEventLogMapper.topSearchKeywords(userId,
                Math.min(limit, 20));
        return R.success("获取成功", keywords);
    }
}
