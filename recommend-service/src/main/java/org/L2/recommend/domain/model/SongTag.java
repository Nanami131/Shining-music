package org.L2.recommend.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class SongTag {
    private Long songId;
    private Long tagId;
    private Float value;
    private Float confidence;
    private String source;
    private String reviewStatus;
    private String evidenceJson;
    private LocalDateTime createdAt;
}
