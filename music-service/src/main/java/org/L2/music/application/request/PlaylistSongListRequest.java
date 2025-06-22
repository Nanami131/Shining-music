package org.L2.music.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class PlaylistSongListRequest{
    private Long Id;
    private Long playlistId;
    private List<Long> songIds;
}
