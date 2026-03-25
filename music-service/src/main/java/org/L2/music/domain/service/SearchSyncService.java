package org.L2.music.domain.service;

import org.L2.music.domain.model.Lyrics;
import org.L2.music.domain.model.MusicSearchDoc;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.LyricsMapper;
import org.L2.music.infrastructure.SingerMapper;
import org.L2.music.infrastructure.SongMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncService.class);

    @Autowired
    private SearchService searchService;
    @Autowired
    private SongMapper songMapper;
    @Autowired
    private SingerMapper singerMapper;
    @Autowired
    private LyricsMapper lyricsMapper;

    private static final Set<String> KNOWN_LANGS = Set.of("zh", "ja", "en");

    /**
     * 应用启动后自动执行全量同步。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            searchService.createIndexIfNotExists();
            fullSync();
        } catch (Exception e) {
            log.error("ES startup sync failed — search service may be unavailable", e);
        }
    }

    /**
     * 全量同步：从 MySQL 拉取所有歌曲、歌手、歌词，构建文档并批量索引。
     */
    public void fullSync() {
        log.info("Starting full sync to ES...");
        List<Song> allSongs = songMapper.query(new Song());
        if (allSongs.isEmpty()) {
            log.info("No songs found, skip sync");
            return;
        }

        Map<Long, Singer> singerCache = singerMapper.query(new Singer()).stream()
                .collect(Collectors.toMap(Singer::getId, s -> s, (a, b) -> a));

        List<Lyrics> allLyrics = lyricsMapper.selectAll();
        Map<Long, List<Lyrics>> lyricsBySong = allLyrics.stream()
                .collect(Collectors.groupingBy(Lyrics::getSongId));

        List<MusicSearchDoc> docs = new ArrayList<>();
        for (Song song : allSongs) {
            docs.add(buildDoc(song, singerCache.get(song.getArtistId()), lyricsBySong.get(song.getId())));
        }

        Set<Long> mySqlIds = allSongs.stream().map(Song::getId).collect(Collectors.toSet());

        try {
            searchService.bulkIndex(docs);
            log.info("Full sync completed, indexed {} documents", docs.size());

            Set<Long> esIds = searchService.getAllIndexedIds();
            esIds.removeAll(mySqlIds);
            for (Long staleId : esIds) {
                searchService.deleteDoc(staleId);
                log.info("Deleted stale ES doc songId={}", staleId);
            }
            if (!esIds.isEmpty()) {
                log.info("Cleaned {} stale documents from ES", esIds.size());
            }
        } catch (Exception e) {
            log.error("Bulk index failed during full sync", e);
        }
    }

    /**
     * 同步某歌手下所有歌曲的搜索索引（歌手名称/头像变更后调用）。
     */
    public void syncSongsBySinger(Long singerId) {
        List<Song> songs = songMapper.query(new Song().setArtistId(singerId));
        if (songs == null || songs.isEmpty()) return;
        for (Song s : songs) {
            syncSong(s.getId());
        }
    }

    /**
     * 增量同步单首歌曲（新增/更新歌曲或歌词后调用）。
     */
    public void syncSong(Long songId) {
        Song song = songMapper.selectById(songId);
        if (song == null) {
            searchService.deleteDoc(songId);
            return;
        }
        Singer singer = song.getArtistId() != null ? singerMapper.selectById(song.getArtistId()) : null;
        List<Lyrics> lyrics = lyricsMapper.selectBySongId(songId);
        try {
            searchService.indexDoc(buildDoc(song, singer, lyrics));
            log.info("Incremental sync completed for songId={}", songId);
        } catch (Exception e) {
            log.error("Incremental sync failed for songId={}", songId, e);
        }
    }

    private MusicSearchDoc buildDoc(Song song, Singer singer, List<Lyrics> lyricsList) {
        MusicSearchDoc doc = new MusicSearchDoc()
                .setSongId(song.getId())
                .setTitle(song.getTitle())
                .setSingerId(song.getArtistId())
                .setCoverUrl(song.getCoverUrl());

        if (singer != null) {
            doc.setSingerName(singer.getName());
        }

        if (lyricsList != null && !lyricsList.isEmpty()) {
            Map<String, StringBuilder> langText = new HashMap<>();
            for (Lyrics lyr : lyricsList) {
                String lang = normalizeLang(lyr.getLanguageMsg());
                String plainText = LrcParser.toPlainText(lyr.getContent());
                if (!plainText.isEmpty()) {
                    langText.computeIfAbsent(lang, k -> new StringBuilder()).append(plainText).append("\n");
                }
            }
            doc.setLyricsZh(langText.getOrDefault("zh", new StringBuilder()).toString().trim());
            doc.setLyricsJa(langText.getOrDefault("ja", new StringBuilder()).toString().trim());
            doc.setLyricsEn(langText.getOrDefault("en", new StringBuilder()).toString().trim());
        }

        return doc;
    }

    private String normalizeLang(String languageMsg) {
        if (languageMsg == null || languageMsg.isBlank()) return "zh";
        String lower = languageMsg.trim().toLowerCase();
        if (KNOWN_LANGS.contains(lower)) return lower;
        if (lower.contains("中") || lower.contains("chinese") || lower.contains("mandarin")) return "zh";
        if (lower.contains("日") || lower.contains("japanese")) return "ja";
        if (lower.contains("英") || lower.contains("english")) return "en";
        return "zh";
    }
}
