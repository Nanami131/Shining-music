package org.L2.music.application.service;

import org.L2.common.R;
import org.L2.common.annotation.PermissionCheck;
import org.L2.common.context.UserContext;
import org.L2.common.mq.PlayRecordProducer;
import org.L2.common.rpc.UserClient;
import org.L2.common.util.Md5Util;
import org.L2.music.application.dto.*;
import org.L2.music.application.request.*;
import org.L2.music.constant.Constants;
import org.L2.music.domain.model.Lyrics;
import org.L2.music.domain.model.Playlist;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.model.Song;
import org.L2.music.domain.model.Video;
import org.L2.music.domain.service.LyricsService;
import org.L2.music.domain.service.PlaylistService;
import org.L2.music.domain.service.SearchService;
import org.L2.music.domain.service.SearchSyncService;
import org.L2.music.domain.service.SingerService;
import org.L2.music.domain.service.SongService;
import org.L2.music.domain.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

@Service
public class MusicAppService {

    private static final Logger log = LoggerFactory.getLogger(MusicAppService.class);

    @Autowired
    private SongService songService;
    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private SingerService singerService;
    @Autowired
    private LyricsService lyricsService;
    @Autowired
    private UserClient userClient;
    @Autowired
    private PlayRecordProducer playRecordProducer;
    @Autowired
    private VideoService videoService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private SearchSyncService searchSyncService;

    /*
     * 歌曲模块
     */
    public R createSong(SongCreateRequest songCreateRequest) {
        Song song = new Song();
        BeanUtils.copyProperties(songCreateRequest, song);
        R result = songService.createSong(song);
        if (result.getPassed() && result.getData() instanceof Song) {
            Long newId = ((Song) result.getData()).getId();
            if (newId != null) {
                searchSyncService.syncSong(newId);
            }
        }
        return result;
    }

    public R getSongBaseInfo(Long songId, Long userId) {
        R result = songService.getSongInfo(songId);
        if (!result.getPassed()) {
            return result;
        }
        SongBaseDTO songBaseDTO = new SongBaseDTO();
        BeanUtils.copyProperties(result.getData(), songBaseDTO);
        songBaseDTO.setFavorite(resolveFavoriteFlag(userId, songId));
        return R.success("获取成功", songBaseDTO);
    }

    public R getSongDetailsInfo(Long songId, Long userId) {
        R result = songService.getSongInfo(songId);
        if (!result.getPassed()) {
            return result;
        }
        SongDetailsDTO songDetailsDTO = new SongDetailsDTO();
        BeanUtils.copyProperties(result.getData(), songDetailsDTO);
        songDetailsDTO.setFavorite(resolveFavoriteFlag(userId, songId));
        @SuppressWarnings("unchecked")
        ArrayList<Lyrics> allLyrics =
                (ArrayList<Lyrics>) lyricsService.getAllLyricsBySongId(songId).getData();
        songDetailsDTO.setAllLyrics(allLyrics);
        return R.success("获取成功", songDetailsDTO);
    }

    public R getSongShareInfo(Long songId) {
        if (songId == null || songId <= 0) {
            return R.error("歌曲ID无效");
        }

        R songResult = songService.getSongInfo(songId);
        if (!songResult.getPassed()) {
            return songResult;
        }
        if (!(songResult.getData() instanceof Song song)) {
            return R.error("歌曲信息无效");
        }
        if (!Byte.valueOf((byte) 1).equals(song.getStatus())) {
            return R.error("歌曲暂不可用");
        }

        String artistName = null;
        if (song.getArtistId() != null) {
            R singerResult = singerService.getSingerInfo(song.getArtistId());
            if (singerResult.getPassed() && singerResult.getData() instanceof Singer singer) {
                artistName = singer.getName();
            }
        }

        List<SongShareLyricDTO> lyricDTOs = new ArrayList<>();
        R lyricsResult = lyricsService.getAllLyricsBySongId(songId);
        if (lyricsResult.getPassed() && lyricsResult.getData() instanceof List<?> lyricsList) {
            for (Object item : lyricsList) {
                if (item instanceof Lyrics lyrics) {
                    lyricDTOs.add(new SongShareLyricDTO()
                            .setLanguageMsg(lyrics.getLanguageMsg())
                            .setContent(lyrics.getContent()));
                }
            }
        }

        SongShareDTO shareDTO = new SongShareDTO()
                .setId(song.getId())
                .setTitle(song.getTitle())
                .setArtistId(song.getArtistId())
                .setArtistName(artistName)
                .setCoverUrl(song.getCoverUrl())
                .setDuration(song.getDuration())
                .setLyrics(lyricDTOs);
        return R.success("获取歌曲分享信息成功", shareDTO);
    }

    public R uploadLyrics(Long songId, MultipartFile file, String msg) {
        R result = lyricsService.uploadLyrics(songId, file, msg);
        if (result.getPassed()) {
            searchSyncService.syncSong(songId);
        }
        return result;
    }

    public R getAllLyricsBySongId(Long songId) {
        return lyricsService.getAllLyricsBySongId(songId);
    }

    public R getLyrics(Long lyricsId) {
        return lyricsService.getLyrics(lyricsId);
    }

    public R listSongs(Long userId) {
        return songListResult(songService.listSongs(), userId);
    }

    public R listSongsPage(Long userId, long offset, int size) {
        return songListResult(songService.listSongsPage(offset, size), userId);
    }

    public long countActiveSongs() { return songService.countActiveSongs(); }

    private R songListResult(List<Song> songs, Long userId) {
        Set<Long> favoriteSongIds = userId == null ? Set.of() : playlistService.getFavoriteSongIds(userId);
        if (favoriteSongIds == null) {
            favoriteSongIds = Set.of();
        }
        List<SongBaseDTO> dtoList = new ArrayList<>();
        for (Song song : songs) {
            SongBaseDTO dto = new SongBaseDTO();
            BeanUtils.copyProperties(song, dto);
            dto.setFavorite(song.getId() != null && favoriteSongIds.contains(song.getId()));
            dtoList.add(dto);
        }
        return R.success("获取歌曲列表成功", dtoList);
    }

    public R randomSongs(int limit) {
        List<Song> all = new ArrayList<>(songService.listRandomEnabledSongs());
        Collections.shuffle(all);
        List<SongBaseDTO> result = all.stream()
                .limit(Math.min(limit, 20))
                .map(song -> {
                    SongBaseDTO dto = new SongBaseDTO();
                    BeanUtils.copyProperties(song, dto);
                    return dto;
                })
                .collect(Collectors.toList());
        return R.success("随机推荐成功", result);
    }

    /**
     * 前端点击播放歌曲时的统一入口：记录播放事件并返回歌曲详情。
     */
    public R playSong(Long songId, Long userId) {
        log.info("Received playSong request, songId={}, userId={}", songId, userId);
        String playSessionId = java.util.UUID.randomUUID().toString();
        if (userId != null) {
            try {
                playRecordProducer.sendPlayRecord(userId, songId, playSessionId);
                log.info("Play record message sent via RabbitMQ, userId={}, songId={}, sessionId={}", userId, songId, playSessionId);
            } catch (Exception e) {
                log.error("Failed to send play record message to RabbitMQ, userId={}, songId={}", userId, songId, e);
            }
        } else {
            log.warn("playSong called without userId, skip MQ message");
        }
        R result = getSongDetailsInfo(songId, userId);
        if (result.getPassed() && result.getData() instanceof SongDetailsDTO) {
            ((SongDetailsDTO) result.getData()).setPlaySessionId(playSessionId);
        }
        return result;
    }

    /** 独立上报开始事件，复用既有 MQ 发送链路。 */
    public R reportPlayStart(Long userId, PlayStartRequest body) {
        if (userId == null) {
            return R.error("用户未登录");
        }
        if (body == null || body.getSongId() == null || body.getSongId() <= 0
                || !validPlaySessionId(body.getPlaySessionId())) {
            return R.error("songId 或 playSessionId 无效");
        }
        LocalDateTime playedAt;
        try {
            playedAt = convertPlayedAt(body.getPlayedAt());
        } catch (RuntimeException e) {
            return R.error("playedAt 无效");
        }
        try {
            playRecordProducer.sendPlayRecord(userId, body.getSongId(), body.getPlaySessionId(), playedAt);
            return R.success("播放开始事件已发送", body.getPlaySessionId());
        } catch (Exception e) {
            log.error("Failed to send play start record, userId={}, songId={}", userId, body.getSongId(), e);
            return R.error("播放开始事件发送失败");
        }
    }

    private boolean validPlaySessionId(String playSessionId) {
        return playSessionId != null && !playSessionId.isBlank() && playSessionId.length() <= 36;
    }

    private LocalDateTime convertPlayedAt(Long timestamp) {
        return timestamp == null ? null : LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }

    public R updateSongDuration(Long songId, Integer duration) {
        if (songId == null || duration == null || duration <= 0) {
            return R.error("参数无效");
        }
        return songService.updateDuration(songId, duration);
    }

    public R updateSongStatus(Long songId, Byte status) {
        if (songId == null || status == null || (status != 0 && status != 1)) {
            return R.error("参数无效，status 只能为 0（禁用）或 1（启用）");
        }
        R result = songService.updateSongStatus(songId, status);
        if (result.getPassed()) {
            searchSyncService.syncSong(songId);
        }
        return result;
    }

    public R updateSongRandomEnabled(Long songId, Boolean enabled) {
        if (songId == null || enabled == null) {
            return R.error("参数无效");
        }
        return songService.updateRandomEnabled(songId, enabled);
    }

    public R reportPlayEnd(java.util.Map<String, Object> body) {
        Long userId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        Long songId = body.get("songId") != null ? ((Number) body.get("songId")).longValue() : null;
        if (userId == null || songId == null) {
            return R.error("userId and songId are required");
        }

        Integer durationSec = body.get("duration") != null ? ((Number) body.get("duration")).intValue() : null;
        Integer totalDuration = body.get("totalDuration") != null ? ((Number) body.get("totalDuration")).intValue() : null;
        Integer actualListenedTime = body.get("actualListenedTime") != null ? ((Number) body.get("actualListenedTime")).intValue() : null;
        Boolean completed = body.get("completed") != null ? (Boolean) body.get("completed") : false;
        String source = (String) body.get("source");

        String playSessionId = (String) body.get("playSessionId");

        // 旧客户端没有会话 ID 或开始时间，保持原有请求格式兼容。
        if (playSessionId != null && !validPlaySessionId(playSessionId)) {
            return R.error("playSessionId 无效");
        }
        LocalDateTime playedAt;
        try {
            Object timestamp = body.get("playedAt");
            if (timestamp != null && !(timestamp instanceof Number)) {
                return R.error("playedAt 无效");
            }
            playedAt = convertPlayedAt(timestamp == null ? null : ((Number) timestamp).longValue());
        } catch (RuntimeException e) {
            return R.error("playedAt 无效");
        }

        try {
            org.L2.common.event.PlaybackInfo playbackInfo = new org.L2.common.event.PlaybackInfo()
                    .setSongId(songId)
                    .setPlaySessionId(playSessionId)
                    .setPlayedAt(playedAt)
                    .setDurationSec(durationSec)
                    .setTotalDurationSec(totalDuration)
                    .setActualListenedTime(actualListenedTime)
                    .setCompleted(completed)
                    .setSource(source);
            playRecordProducer.sendPlayEndRecord(userId, songId, playbackInfo);
        } catch (Exception e) {
            log.error("Failed to send play end record, userId={}, songId={}", userId, songId, e);
            return R.error("播放结束事件记录失败");
        }
        return R.success("播放结束事件已记录");
    }

    public R uploadSong(Long id, MultipartFile file, String md5) {
        R md5Check = verifyMd5(file, md5);
        if (md5Check != null) return md5Check;
        R result = songService.uploadSong(id, file);
        if (result.getPassed()) {
            searchSyncService.syncSong(id);
        }
        return result;
    }

    public R uploadSongAvatar(Long id, MultipartFile avatarFile, String md5) {
        R md5Check = verifyMd5(avatarFile, md5);
        if (md5Check != null) return md5Check;
        R result = songService.uploadSongAvatar(id, avatarFile);
        if (result.getPassed()) {
            searchSyncService.syncSong(id);
        }
        return result;
    }

    /*
     * 歌单模块
     */
    public R deletePlaylist(Long playlistId) {
        return playlistService.deletePlaylist(playlistId);
    }

    @PermissionCheck(fieldName = "id")
    public R createPlaylist(PlaylistCreateRequest playlistCreateRequest) {
        Playlist playlist = new Playlist();
        BeanUtils.copyProperties(playlistCreateRequest, playlist);
        // 前端传入的是用户 id 字段
        playlist.setUserId(playlistCreateRequest.getId());
        playlist.setId(null);
        return playlistService.createPlaylist(playlist);
    }

    @PermissionCheck
    public R updatePlaylist(PlaylistUpdateRequest playlistUpdateRequest) {
        Playlist playlist = new Playlist();
        playlist.setId(playlistUpdateRequest.getId());
        playlist.setName(playlistUpdateRequest.getName());
        playlist.setDescription(playlistUpdateRequest.getDescription());
        if (playlistUpdateRequest.getVisibility() != null) {
            playlist.setVisibility(playlistUpdateRequest.getVisibility());
        }
        return playlistService.updatePlaylist(playlist, playlistUpdateRequest.getUserId());
    }

    public R createUserCurrentPlaylist(Long userId) {
        Playlist playlist = new Playlist()
                .setUserId(userId)
                .setType(Constants.CURRENT_PLAYLIST)
                .setName("播放列表" + userId);
        return playlistService.createPlaylist(playlist);
    }

    public R getPlaylistBaseInfo(Long playlistId) {
        R result = playlistService.getPlaylistInfo(playlistId);
        if (!result.getPassed()) {
            return result;
        }
        PlaylistBaseDTO playlistBaseDTO = new PlaylistBaseDTO();
        BeanUtils.copyProperties(result.getData(), playlistBaseDTO);
        playlistBaseDTO.setNickName(resolveNickname(playlistBaseDTO.getUserId(), null));
        return R.success("获取成功", playlistBaseDTO);
    }

    public R getPlaylistDetailsInfo(Long playlistId) {
        return getPlaylistDetailsInfo(playlistId, 1, 0);
    }

    public R getPlaylistDetailsInfo(Long playlistId, int page, int size) {
        R result = playlistService.getPlaylistInfo(playlistId);
        if (!result.getPassed()) {
            return result;
        }
        PlaylistDetailsDTO playlistDetailsDTO = new PlaylistDetailsDTO();
        BeanUtils.copyProperties(result.getData(), playlistDetailsDTO);
        R songs = size > 0 ? songService.getPlaylistSongsPage(playlistId, ((long) page - 1L) * size, size)
                : songService.getPlaylistSongs(playlistId);
        if (!Boolean.TRUE.equals(songs.getPassed())) return songs;
        @SuppressWarnings("unchecked")
        List<SongBaseDTO> songList = (List<SongBaseDTO>) songs.getData();
        playlistDetailsDTO.setSongs(songList);
        playlistDetailsDTO.setSongsTotal(size > 0 ? songService.countPlaylistSongs(playlistId) : (long) songList.size());
        playlistDetailsDTO.setNickName(resolveNickname(playlistDetailsDTO.getUserId(), null));
        return R.success("获取成功", playlistDetailsDTO);
    }

    public R getCurrentPlaylist(Long userId) {
        if (userId == null) {
            return R.error("用户ID不能为空");
        }
        Playlist playlist = playlistService.ensureCurrentPlaylist(userId);
        if (playlist == null) {
            return R.error("初始化当前列表失败");
        }
        return getPlaylistDetailsInfo(playlist.getId());
    }

    public R managePlaylistSong(PlaylistSongRequest playlistSongRequest) {
        Long playlistId = playlistSongRequest.getPlaylistId();
        Long songId = playlistSongRequest.getSongId();
        String action = playlistSongRequest.getAction();
        try {
            return playlistService.managePlaylistSong(playlistId, songId, action);
        } catch (Exception e) {
            return R.error("操作失败" + e.getMessage());
        }
    }

    public R uploadPlaylistAvatar(Long id, MultipartFile avatarFile, String md5) {
        R md5Check = verifyMd5(avatarFile, md5);
        if (md5Check != null) return md5Check;
        return playlistService.uploadPlaylistAvatar(id, avatarFile);
    }

    public R clearUserCurrentPlaylist(Long userId) {
        return playlistService.clearUserCurrentPlaylist(userId);
    }

    public R replaceCurrentPlaylistSongs(PlaylistSongListRequest request) {
        if (request == null) {
            return R.error("请求不能为空");
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            userId = request.getUserId();
        }
        return playlistService.replaceCurrentPlaylistSongs(userId, request.getSongIds());
    }

    public R clearPlaylistSongs(Long playlistId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.error("未登录");
        }
        return playlistService.clearPlaylistSongs(playlistId, userId);
    }

    /**
     * 发现更多歌单（用于“更多歌单”板块）
     *
     * 只返回：
     * - 类型为普通歌单（Constants.PLAYLIST）的记录
     * - 当前用户自己的歌单（无论公私）
     * - 其他用户的公开歌单
     *
     * @param userId 当前用户 ID，可为空
     */
    public R discoverPlaylists(Long userId) {
        R result = playlistService.discoverPlaylists(userId);
        if (!result.getPassed()) {
            return result;
        }
        @SuppressWarnings("unchecked")
        List<Playlist> playlists = (List<Playlist>) result.getData();
        return playlistListResult(playlists);
    }

    public R discoverPlaylistsPage(Long userId, long offset, int size) {
        return playlistListResult(playlistService.listVisiblePage(userId, true, offset, size));
    }

    public long countDiscoverPlaylists(Long userId) {
        return playlistService.countVisible(userId, true);
    }

    public R discoverOwnPlaylists(Long userId) {
        return playlistListResult(playlistService.listOwn(userId));
    }

    public R discoverOwnPlaylistsPage(Long userId, long offset, int size) {
        return playlistListResult(playlistService.listOwnPage(userId, offset, size));
    }

    public long countOwnPlaylists(Long userId) {
        return playlistService.countOwn(userId);
    }

    public R publicPlaylistsByCreator(Long creatorId, long offset, Integer size) {
        return playlistListResult(playlistService.listPublicOwnerPage(creatorId, offset, size));
    }

    public long countPublicPlaylistsByCreator(Long creatorId) {
        return playlistService.countPublicOwner(creatorId);
    }

    private R playlistListResult(List<Playlist> playlists) {
        List<PlaylistBaseDTO> dtoList = new ArrayList<>();
        Map<Long, String> nicknameCache = new HashMap<>();
        for (Playlist playlist : playlists) {
            PlaylistBaseDTO dto = new PlaylistBaseDTO();
            BeanUtils.copyProperties(playlist, dto);
            dto.setNickName(resolveNickname(playlist.getUserId(), nicknameCache));
            dtoList.add(dto);
        }
        return R.success("获取歌单列表成功", dtoList);
    }

    public R listPlaylists(Long userId) {
        return playlistListResult(playlistService.listOfficialAndMine(userId));
    }

    public R listPlaylistsPage(Long userId, long offset, int size) {
        return playlistListResult(playlistService.listVisiblePage(userId, false, offset, size));
    }

    public long countPlaylists(Long userId) {
        return playlistService.countVisible(userId, false);
    }

    public R searchPlaylists(Long userId, String search, long offset, Integer size) {
        return playlistListResult(playlistService.searchVisible(userId, search, offset, size));
    }

    public long countPlaylists(Long userId, String search) {
        return playlistService.countVisible(userId, search);
    }

    /*
     * 视频模块
     */
    public R uploadVideo(Long singerId, String title, MultipartFile file, String md5) {
        R md5Check = verifyMd5(file, md5);
        if (md5Check != null) return md5Check;
        return videoService.uploadVideo(singerId, title, file, md5);
    }

    public R updateVideoMeta(VideoUpdateRequest request) {
        Video video = new Video()
                .setId(request.getId())
                .setSingerId(request.getSingerId())
                .setTitle(request.getTitle())
                .setCoverUrl(request.getCoverUrl());
        return videoService.updateVideoMeta(video);
    }

    public R getVideoInfo(Long id) {
        return videoService.getVideoInfo(id);
    }

    public R listVideos() {
        return videoService.listVideos();
    }

    public R deleteVideo(Long id) {
        return videoService.deleteVideo(id);
    }

    /*
     * 歌手模块
     */
    public R getSingerBaseInfo(Long singerId) {
        R result = singerService.getSingerInfo(singerId);
        if (!result.getPassed()) {
            return result;
        }
        SingerBaseDTO singerBaseDTO = new SingerBaseDTO();
        BeanUtils.copyProperties(result.getData(), singerBaseDTO);
        return R.success("获取成功", singerBaseDTO);
    }

    public R getSingerDetailsInfo(Long singerId) {
        return getSingerDetailsInfo(singerId, 1, 0);
    }

    public R getSingerDetailsInfo(Long singerId, int page, int size) {
        R result = singerService.getSingerInfo(singerId);
        if (!result.getPassed()) {
            return result;
        }
        SingerDetailsDTO singerDetailsDTO = new SingerDetailsDTO();
        BeanUtils.copyProperties(result.getData(), singerDetailsDTO);
        R songs = size > 0 ? songService.getSingerSongsPage(singerId, ((long) page - 1L) * size, size)
                : songService.getSingerSongs(singerId);
        if (!Boolean.TRUE.equals(songs.getPassed())) return songs;
        @SuppressWarnings("unchecked")
        List<SongBaseDTO> songList = (List<SongBaseDTO>) songs.getData();
        singerDetailsDTO.setSongs(songList);
        singerDetailsDTO.setSongsTotal(size > 0 ? songService.countSingerSongs(singerId) : (long) songList.size());
        return R.success("获取成功", singerDetailsDTO);
    }

    public R listSingers() {
        return singerListResult(singerService.listSingers());
    }

    public R listSingersPage(long offset, int size) {
        return singerListResult(singerService.listSingersPage(offset, size));
    }

    public long countSingers() { return singerService.countSingers(); }

    public R searchSingers(String search, long offset, Integer size) {
        return singerListResult(singerService.searchSingers(offset, size, search));
    }

    public long countSingers(String search) { return singerService.countSingers(search); }

    private R singerListResult(List<Singer> singers) {
        List<SingerBaseDTO> dtoList = new ArrayList<>();
        for (Singer singer : singers) {
            SingerBaseDTO dto = new SingerBaseDTO();
            BeanUtils.copyProperties(singer, dto);
            dtoList.add(dto);
        }
        return R.success("获取歌手列表成功", dtoList);
    }

    public R createSinger(SingerCreateRequest singerCreateRequest) {
        Singer singer = new Singer();
        BeanUtils.copyProperties(singerCreateRequest, singer);
        return singerService.createSinger(singer);
    }

    @SuppressWarnings("unchecked")
    public R deleteSinger(Long singerId) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return R.error("用户未登录");
        }
        Singer existing = singerService.getSingerById(singerId);
        if (existing == null) {
            return R.error("歌手不存在");
        }
        if (existing.getUserId() != null && !currentUserId.equals(existing.getUserId())) {
            return R.error("无权删除他人创建的歌手");
        }
        List<Long> songIds = new ArrayList<>();
        R songsResult = songService.getSingerSongs(singerId);
        if (songsResult.getPassed() && songsResult.getData() instanceof List) {
            for (Song s : (List<Song>) songsResult.getData()) {
                songIds.add(s.getId());
            }
        }
        try {
            singerService.deleteSinger(singerId);
            searchSyncService.deleteFromES(songIds);
        } catch (Exception e) {
            return R.error("删除失败" + e.getMessage());
        }
        return R.success("删除成功");
    }

    public R updateSingerProfile(SingerFieldsUpdateRequest singerFieldsUpdateRequest) {
        R ownerCheck = checkSingerOwnership(singerFieldsUpdateRequest.getId());
        if (ownerCheck != null) return ownerCheck;
        Singer singer = new Singer();
        BeanUtils.copyProperties(singerFieldsUpdateRequest, singer);
        R result = singerService.updateSinger(singer);
        if (result.getPassed() && singerFieldsUpdateRequest.getId() != null) {
            searchSyncService.syncSongsBySinger(singerFieldsUpdateRequest.getId());
        }
        return result;
    }

    public R updateSingerAvatar(Long id, MultipartFile avatarFile, String md5) {
        R md5Check = verifyMd5(avatarFile, md5);
        if (md5Check != null) return md5Check;
        R ownerCheck = checkSingerOwnership(id);
        if (ownerCheck != null) return ownerCheck;
        R result = singerService.updateSingerAvatar(id, avatarFile);
        if (result.getPassed()) {
            searchSyncService.syncSongsBySinger(id);
        }
        return result;
    }

    private R checkSingerOwnership(Long singerId) {
        if (singerId == null) return R.error("歌手ID不能为空");
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return R.error("用户未登录");
        Singer singer = singerService.getSingerById(singerId);
        if (singer == null) return R.error("歌手不存在");
        if (singer.getUserId() != null && !currentUserId.equals(singer.getUserId())) {
            return R.error("无权修改他人创建的歌手");
        }
        return null;
    }

    public R toggleFavoriteSong(Long userId, Long songId) {
        return playlistService.toggleFavoriteSong(userId, songId);
    }

    public R getUserFavoriteSongs(Long userId) {
        if (userId == null) {
            return R.error("用户不能为空");
        }
        Playlist favorite = playlistService.ensureFavoritePlaylist(userId);
        if (favorite == null) {
            return R.error("初始化收藏歌单失败");
        }
        R songsResult = songService.getPlaylistSongs(favorite.getId());
        if (!songsResult.getPassed()) {
            return songsResult;
        }
        @SuppressWarnings("unchecked")
        List<Song> songs = (List<Song>) songsResult.getData();
        List<SongBaseDTO> dtoList = new ArrayList<>();
        if (songs != null) {
            for (Song song : songs) {
                SongBaseDTO dto = new SongBaseDTO();
                BeanUtils.copyProperties(song, dto);
                dto.setFavorite(true);
                dtoList.add(dto);
            }
        }
        return R.success("获取收藏歌曲成功", dtoList);
    }

    public R getUserFavoriteSongsPage(Long userId, long offset, int size) {
        if (userId == null) return R.error("用户不能为空");
        Playlist favorite = playlistService.ensureFavoritePlaylist(userId);
        if (favorite == null) return R.error("初始化收藏歌单失败");
        R result = songService.getPlaylistSongsPage(favorite.getId(), offset, size);
        if (!result.getPassed()) return result;
        @SuppressWarnings("unchecked")
        List<Song> songs = (List<Song>) result.getData();
        List<SongBaseDTO> dtoList = new ArrayList<>();
        for (Song song : songs) {
            SongBaseDTO dto = new SongBaseDTO();
            BeanUtils.copyProperties(song, dto);
            dto.setFavorite(true);
            dtoList.add(dto);
        }
        return R.success("获取收藏歌曲成功", dtoList);
    }

    public long countUserFavoriteSongs(Long userId) {
        if (userId == null) return 0L;
        Playlist favorite = playlistService.ensureFavoritePlaylist(userId);
        return favorite == null ? 0L : songService.countPlaylistSongs(favorite.getId());
    }

    /*
     * 搜索模块
     */
    public R search(String keyword, int page, int size) {
        try {
            int from = Math.max(page, 0) * size;
            var results = searchService.search(keyword, from, size);
            return R.success("搜索成功", results);
        } catch (Exception e) {
            log.error("Search failed for keyword={}", keyword, e);
            return R.error("搜索失败: " + e.getMessage());
        }
    }

    public R searchPage(String keyword, int offset, int size, int page) {
        try {
            var results = searchService.search(keyword, offset, size);
            long total = searchService.countMatches(keyword);
            return org.L2.common.ListPagination.fromPage(R.success("搜索成功", results), total, page, size);
        } catch (Exception e) {
            log.error("Search page failed for keyword={}", keyword, e);
            return R.error("搜索失败: " + e.getMessage());
        }
    }


    public R searchAll(String keyword) {
        try {
            return R.success("搜索成功", searchService.searchAll(keyword));
        } catch (Exception e) {
            log.error("Search failed for keyword={}", keyword, e);
            return R.error("搜索失败: " + e.getMessage());
        }
    }

    public R fullSyncToEs() {
        try {
            searchSyncService.fullSync();
            return R.success("全量同步完成");
        } catch (Exception e) {
            log.error("Full sync to ES failed", e);
            return R.error("同步失败: " + e.getMessage());
        }
    }

    public R recalcVolumeGain() {
        int rows = songService.recalcVolumeGain();
        if (rows < 0) {
            return R.error("库中无 LUFS 数据，请先上传歌曲");
        }
        return R.success("重算完成，更新 " + rows + " 首歌曲", rows);
    }

    private R verifyMd5(MultipartFile file, String md5) {
        if (md5 == null || md5.isBlank()) {
            return null;
        }
        try {
            if (!Md5Util.verify(file, md5)) {
                return R.error("文件MD5校验失败，文件可能在传输过程中损坏");
            }
        } catch (Exception e) {
            log.warn("MD5 verification failed due to IO error", e);
            return R.error("MD5校验异常: " + e.getMessage());
        }
        return null;
    }

    private boolean resolveFavoriteFlag(Long userId, Long songId) {
        if (userId == null || songId == null) {
            return false;
        }
        return playlistService.isSongFavorite(userId, songId);
    }

    private String resolveNickname(Long userId, Map<Long, String> cache) {
        if (userId == null) {
            return null;
        }
        if (Long.valueOf(-1L).equals(userId)) {
            if (cache != null) {
                cache.put(userId, "官方");
            }
            return "官方";
        }
        if (cache != null) {
            if (cache.containsKey(userId)) {
                return cache.get(userId);
            }
            String nickname = requestNickname(userId);
            cache.put(userId, nickname);
            return nickname;
        }
        return requestNickname(userId);
    }

    private String requestNickname(Long userId) {
        if (userId == null) {
            return null;
        }
        if (Long.valueOf(-1L).equals(userId)) {
            return "官方";
        }
        try {
            R response = userClient.getUserBaseInfo(userId);
            if (response != null && Boolean.TRUE.equals(response.getPassed())) {
                Object data = response.getData();
                if (data instanceof Map<?, ?> userData) {
                    Object nickName = userData.get("nickName");
                    return nickName != null ? String.valueOf(nickName) : null;
                }
            }
        } catch (Exception ignored) {
            // 忽略 RPC 异常，避免影响歌单查询
        }
        return null;
    }

    public R getPlaybackState(Long userId) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            userId = trustedUserId;
        }
        if (userId == null) {
            return R.error("用户未登录");
        }
        return playlistService.getPlaybackState(userId);
    }

    public R savePlaybackState(Long userId, java.util.Map<String, String> state) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            userId = trustedUserId;
        }
        if (userId == null) {
            return R.error("用户未登录");
        }
        return playlistService.savePlaybackState(userId, state);
    }
}
