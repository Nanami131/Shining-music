package org.L2.music.domain;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class Song {
    private Long id;
    private Long singerId;
    private String title;
    private String imageUrl;
    private String contentUrl;
    private String lyricsUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}