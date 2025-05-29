package org.L2.music.domain.model;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 歌曲实体类，代表音乐系统中的单首歌曲
 */
@Data
@Accessors(chain = true)
public class Song {
    private Long id;
    private String title;
    private Long artistId;
    private Long albumId;
    private String fileUrl;
    private String coverUrl;
    private Byte status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}