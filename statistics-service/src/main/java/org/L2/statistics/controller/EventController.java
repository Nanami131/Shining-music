package org.L2.statistics.controller;

import org.L2.common.R;
import org.L2.statistics.application.service.UserEventService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/statistics/events")
public class EventController {

    private final UserEventService userEventService;

    public EventController(UserEventService userEventService) {
        this.userEventService = userEventService;
    }

    @PostMapping
    public R reportEvent(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        String eventType = (String) body.get("eventType");
        String targetType = (String) body.get("targetType");
        Long targetId = body.get("targetId") != null ? ((Number) body.get("targetId")).longValue() : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> extraData = (Map<String, Object>) body.get("extraData");

        return userEventService.saveEvent(userId, eventType, targetType, targetId, extraData);
    }

    @GetMapping("/recent/{userId}")
    public R getRecentEvents(@PathVariable Long userId,
                             @RequestParam(value = "eventType", required = false) String eventType,
                             @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return userEventService.getRecentEvents(userId, eventType, limit);
    }

    @GetMapping("/search-keywords/{userId}")
    public R getTopSearchKeywords(@PathVariable Long userId,
                                  @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return userEventService.getTopSearchKeywords(userId, limit);
    }
}
