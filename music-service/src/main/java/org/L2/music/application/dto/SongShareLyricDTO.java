package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SongShareLyricDTO {
    private String languageMsg;
    private String content;
}
