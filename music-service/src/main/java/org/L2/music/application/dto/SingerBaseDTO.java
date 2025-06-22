package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SingerBaseDTO {
    private Long id;
    private String name;
    private String avatarUrl;
}
