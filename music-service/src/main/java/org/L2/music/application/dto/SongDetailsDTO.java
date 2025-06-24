package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.L2.music.domain.model.Lyrics;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class SongDetailsDTO {
    private Long id;
    private String title;
    private Long artistId;
    private Long albumId;
    private String fileUrl;
    private String coverUrl;
    private Byte status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<Lyrics> allLyrics;

}
