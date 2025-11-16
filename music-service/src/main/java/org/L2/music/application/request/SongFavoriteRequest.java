package org.L2.music.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SongFavoriteRequest {
    private Long userId;
    private Long songId;
}

