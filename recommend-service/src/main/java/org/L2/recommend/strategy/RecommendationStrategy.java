package org.L2.recommend.strategy;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RecommendationStrategy {

    List<Map<String, Object>> recommend(Long userId, int limit, Set<Long> playedSongIds);

    String name();

    boolean isAvailable(Long userId);
}
