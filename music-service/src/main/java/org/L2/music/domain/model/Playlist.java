package org.L2.music.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

/**
 * 歌单实体类，代表用户创建的歌曲集合
 */
@Data
@Accessors(chain = true)
public class Playlist {
    private Long id;
    private String name;
    private String description;
    private Long userId;
    private Byte type; //歌单类型（普通、专辑、收藏等等）
    private Byte visibility;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
