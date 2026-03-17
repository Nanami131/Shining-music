package org.L2.music.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * ES 搜索文档：以歌曲为单位，聚合歌手信息和多语言歌词。
 * 索引名: music_search
 */
@Data
@Accessors(chain = true)
public class MusicSearchDoc {
    private Long songId;
    private String title;
    private Long singerId;
    private String singerName;
    private String coverUrl;
    private String lyricsZh;
    private String lyricsJa;
    private String lyricsEn;
}
