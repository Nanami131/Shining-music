package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlaylistBaseDTO {
    private Long id;
    private Long userId;
    private String name;
    private Byte type;
    private Byte visibility;
    private String coverUrl;

}
