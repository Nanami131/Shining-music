package org.L2.music.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VideoUpdateRequest {
    private Long id;
    private Long singerId;
    private String title;
    private String coverUrl;
}

