package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlaylistSongRequest {
    private Long Id;
    private Long playlistId;
    private Long songId;
}
