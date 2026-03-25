package org.L2.statistics.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.statistics.domain.model.UserProfile;
import org.L2.statistics.domain.service.UserPlayRecordDomainService;
import org.L2.statistics.infrastructure.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserPlayRecordDomainService playRecordDomainService;
    private final UserProfileMapper userProfileMapper;

    public R getUserProfile(Long userId) {
        if (userId == null) {
            return R.error("userId is required");
        }
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null) {
            return R.success("暂无画像数据", null);
        }
        return R.success("获取成功", profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public R refreshUserProfile(Long userId) {
        if (userId == null) {
            return R.error("userId is required");
        }
        UserProfile profile = buildProfile(userId);
        userProfileMapper.insertOrUpdate(profile);
        return R.success("画像更新成功", profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public R refreshAllProfiles() {
        List<Long> userIds = playRecordDomainService.getAllDistinctUserIds();
        int count = 0;
        for (Long uid : userIds) {
            try {
                UserProfile profile = buildProfile(uid);
                userProfileMapper.insertOrUpdate(profile);
                count++;
            } catch (Exception e) {
                log.error("Failed to refresh profile for userId={}", uid, e);
            }
        }
        return R.success("批量更新完成，共 " + count + " 个用户", count);
    }

    private UserProfile buildProfile(Long userId) {
        long totalCount = playRecordDomainService.countByUserAndRange(userId, null, null);
        Long totalDuration = playRecordDomainService.getTotalDuration(userId);
        Double avgCompletion = playRecordDomainService.getAvgCompletion(userId);
        Map<String, Object> activeHourMap = playRecordDomainService.getActiveHour(userId);
        LocalDateTime firstPlay = playRecordDomainService.getFirstPlayTime(userId);
        LocalDateTime lastPlay = playRecordDomainService.getLastPlayTime(userId);
        List<Map<String, Object>> topSingers = playRecordDomainService.listTopSingersByUser(userId, 1);

        BigDecimal dailyAvg = BigDecimal.ZERO;
        if (firstPlay != null && totalCount > 0) {
            long days = Math.max(1, ChronoUnit.DAYS.between(firstPlay.toLocalDate(), LocalDateTime.now().toLocalDate()) + 1);
            dailyAvg = BigDecimal.valueOf(totalCount).divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP);
        }

        Integer activeHour = null;
        if (activeHourMap != null && activeHourMap.get("hour") != null) {
            activeHour = ((Number) activeHourMap.get("hour")).intValue();
        }

        Long topSingerId = null;
        String topSingerName = null;
        if (topSingers != null && !topSingers.isEmpty()) {
            Map<String, Object> top = topSingers.get(0);
            if (top.get("singerId") != null) {
                topSingerId = ((Number) top.get("singerId")).longValue();
            }
        }

        return new UserProfile()
                .setUserId(userId)
                .setTotalPlayCount((int) totalCount)
                .setTotalPlayDuration(totalDuration != null ? totalDuration.intValue() : 0)
                .setAvgCompletionRate(avgCompletion != null
                        ? BigDecimal.valueOf(avgCompletion).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .setTopSingerId(topSingerId)
                .setTopSingerName(topSingerName)
                .setActiveHour(activeHour)
                .setDailyAvgPlays(dailyAvg)
                .setLastPlayAt(lastPlay);
    }
}
