package org.L2.music.application.service;

import org.L2.common.R;
import org.L2.common.annotation.PermissionCheck;
import org.L2.common.context.UserContext;
import org.L2.common.mq.PlayRecordProducer;
import org.L2.common.rpc.UserClient;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<Song> songs = songService.listSongs();
        List<SongBaseDTO> dtoList = new ArrayList<>();
        for (Song song : songs) {
            SongBaseDTO dto = new SongBaseDTO();
            BeanUtils.copyProperties(song, dto);
            dto.setFavorite(resolveFavoriteFlag(userId, song.getId()));
            dtoList.add(dto);
        }
        return R.success("获取歌曲列表成功", dtoList);
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

    public R updateSongDuration(Long songId, Integer duration) {
        if (songId == null || duration == null || duration <= 0) {
            return R.error("参数无效");
        }
        return songService.updateDuration(songId, duration);
    }

    public R reportPlayEnd(java.util.Map<String, Object> body) {
        Long userId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        Long songId = body.get("songId") != null ? ((Number) body.get("songId")).longValue() : null;
        if (userId == null || songId == null) {
            return R.error("userId and songId are required");
        }

        Integer durationSec = body.get("duration") != null ? ((Number) body.get("duration")).intValue() : null;
        Integer totalDuration = body.get("totalDuration") != null ? ((Number) body.get("totalDuration")).intValue() : null;
        Boolean completed = body.get("completed") != null ? (Boolean) body.get("completed") : false;
        String source = (String) body.get("source");

        String playSessionId = (String) body.get("playSessionId");

        try {
            org.L2.common.event.PlaybackInfo playbackInfo = new org.L2.common.event.PlaybackInfo()
                    .setSongId(songId)
                    .setPlaySessionId(playSessionId)
                    .setDurationSec(durationSec)
                    .setTotalDurationSec(totalDuration)
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
        R result = songService.uploadSong(id, file);
        if (result.getPassed()) {
            searchSyncService.syncSong(id);
        }
        return result;
    }

    public R uploadSongAvatar(Long id, MultipartFile avatarFile, String md5) {
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
        R result = playlistService.getPlaylistInfo(playlistId);
        if (!result.getPassed()) {
            return result;
        }
        PlaylistDetailsDTO playlistDetailsDTO = new PlaylistDetailsDTO();
        BeanUtils.copyProperties(result.getData(), playlistDetailsDTO);
        R songs = songService.getPlaylistSongs(playlistId);
        @SuppressWarnings("unchecked")
        List<SongBaseDTO> songList = (List<SongBaseDTO>) songs.getData();
        playlistDetailsDTO.setSongs(songList);
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
        return playlistService.uploadPlaylistAvatar(id, avatarFile);
    }

    public R clearUserCurrentPlaylist(Long userId) {
        return playlistService.clearUserCurrentPlaylist(userId);
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
        List<Playlist> playlists = playlistService.listOfficialAndMine(userId);
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

    /*
     * 视频模块
     */
    public R uploadVideo(Long singerId, String title, MultipartFile file, String md5) {
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
        R result = singerService.getSingerInfo(singerId);
        if (!result.getPassed()) {
            return result;
        }
        SingerDetailsDTO singerDetailsDTO = new SingerDetailsDTO();
        BeanUtils.copyProperties(result.getData(), singerDetailsDTO);
        R songs = songService.getSingerSongs(singerId);
        @SuppressWarnings("unchecked")
        List<SongBaseDTO> songList = (List<SongBaseDTO>) songs.getData();
        singerDetailsDTO.setSongs(songList);
        return R.success("获取成功", singerDetailsDTO);
    }

    public R listSingers() {
        List<Singer> singers = singerService.listSingers();
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

    public R fullSyncToEs() {
        try {
            searchSyncService.fullSync();
            return R.success("全量同步完成");
        } catch (Exception e) {
            log.error("Full sync to ES failed", e);
            return R.error("同步失败: " + e.getMessage());
        }
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
