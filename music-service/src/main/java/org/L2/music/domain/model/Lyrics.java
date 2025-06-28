package org.L2.music.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

/**
 * 歌词实体类，代表音乐系统中的歌词信息
 */
@Data
@Accessors(chain = true)
public class Lyrics {
    private Long id; // 歌词ID
    private Long songId; // 关联歌曲ID
    private String languageMsg; // 歌词语言描述信息
    private String content; // 歌词内容
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}