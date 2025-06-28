package org.L2.music.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;


@Data
@Accessors(chain = true)
public class SongCreateRequest {
    private Long id;
    private String title;
    private Long artistId;
    private Long albumId;
    private Byte status;
}
