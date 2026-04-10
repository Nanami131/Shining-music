package org.L2.recommend.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserPreference {
    private Long userId;
    private String vectorJson;
    private Double playCount;
    private LocalDateTime snapshotAt;
}
