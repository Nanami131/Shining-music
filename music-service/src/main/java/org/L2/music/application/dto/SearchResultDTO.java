package org.L2.music.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class SearchResultDTO {
    private Long songId;
    private String title;
    private Long singerId;
    private String singerName;
    private String coverUrl;
    private Float score;
    /** 高亮片段：key 为字段名（title / singerName / lyricsZh 等），value 为命中片段列表 */
    private Map<String, List<String>> highlights;
}
