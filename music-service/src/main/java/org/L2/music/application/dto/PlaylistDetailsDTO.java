package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class PlaylistDetailsDTO {
    private Long id;
    private String name;
    private String description;
    private Long userId;
    private String nickName;
    private Byte type;
    private Byte visibility;
    private String coverUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SongBaseDTO> songs; // 歌单的歌曲列表
}
