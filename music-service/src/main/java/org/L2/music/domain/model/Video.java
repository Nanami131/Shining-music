package org.L2.music.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class Video {
    private Long id;
    private Long singerId;
    private String title;
    private String fileUrl;
    private String coverUrl;
    private String md5;
    private Long sizeBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

