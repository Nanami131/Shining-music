package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class SongShareDTO {
    private Long id;
    private String title;
    private Long artistId;
    private String artistName;
    private String coverUrl;
    private Integer duration;
    private List<SongShareLyricDTO> lyrics;
}
