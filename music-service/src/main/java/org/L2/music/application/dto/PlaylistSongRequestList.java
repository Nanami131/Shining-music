package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class PlaylistSongRequestList {
    private Long Id;
    private Long playlistId;
    private List<Long> songIds;
}
