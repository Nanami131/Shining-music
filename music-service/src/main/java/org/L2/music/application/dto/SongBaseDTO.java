package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class SongBaseDTO {
    private Long id;
    private String title;
    private Long artistId;
    private String coverUrl;
}
