package org.L2.music.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlaylistCreateRequest {
    private Long id; //用户id
    private String name;
    private String description;
    private Byte type; //歌单类型（普通、专辑、收藏等等）
    private Byte visibility;
}
