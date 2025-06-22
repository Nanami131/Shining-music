package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class SingerDetailsDTO {
    private Long id;
    private String name;
    private Long userId;
    private String profile;
    private String avatarUrl;
    private String genre;
    private String country;
    private Byte status;
    private Byte sex;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SongBaseDTO> songs; // 歌手的歌曲列表
}
