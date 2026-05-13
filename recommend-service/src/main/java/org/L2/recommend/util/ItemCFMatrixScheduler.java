package org.L2.recommend.util;

import org.L2.common.R;
import org.L2.recommend.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ItemCFMatrixScheduler {

    private static final Logger log = LoggerFactory.getLogger(ItemCFMatrixScheduler.class);

    private final RecommendationService recommendationService;

    public ItemCFMatrixScheduler(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Scheduled(
            initialDelayString = "${recommend.itemcf.rebuild.initial-delay-ms:60000}",
            fixedDelayString = "${recommend.itemcf.rebuild.fixed-delay-ms:86400000}"
    )
    public void rebuildItemCFMatrix() {
        try {
            R result = recommendationService.rebuildItemCFMatrix();
            log.info("Scheduled Item-CF rebuild finished: {}", result != null ? result.getData() : null);
        } catch (Exception e) {
            log.warn("Scheduled Item-CF rebuild failed", e);
        }
    }
}
