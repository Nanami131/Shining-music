package org.L2.music.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SingerUpdateRequest {
    private Long id;
    private String name;
    private Long userId;
    private String profile;
    private String genre;
    private String country;
    private Byte status;
    private Byte sex;
}