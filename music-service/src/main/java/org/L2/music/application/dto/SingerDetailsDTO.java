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
    private Long userId; // 可为空，关联用户服务中的用户ID
    private String profile;
    private String avatarUrl;
    private String genre; // 音乐流派
    private String country; // 国家或地区
    private Byte status; // 歌手状态（如活跃、退役等）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SongBaseDTO> songs; // 歌手的歌曲列表
}
