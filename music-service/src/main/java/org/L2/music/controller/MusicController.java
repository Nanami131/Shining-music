package org.L2.music.controller;

import org.L2.common.R;
import org.L2.common.context.UserContext;
import org.L2.music.application.request.*;
import org.L2.music.application.service.MusicAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/music")
public class MusicController {

    private static final Logger log = LoggerFactory.getLogger(MusicController.class);

    @Autowired
    private MusicAppService musicAppService;

    /*
     * 歌曲相关
     */

    @PostMapping("/song")
    public R createSong(@RequestBody SongCreateRequest songCreateRequest) {
        return musicAppService.createSong(songCreateRequest);
    }


    /**
     * 单次上传歌曲
     * @param file 歌曲文件
     * @param md5 歌曲校验值
     * @return 上传结果
     */
    @PostMapping("/upload")
    public R uploadSong(@RequestParam("id") Long id,
                        @RequestParam("avatarFile") MultipartFile file,
                        @RequestParam("md5") String md5) {
        return musicAppService.uploadSong(id, file, md5);
    }

    /**
     * 上传歌词
     * @param songId 对应歌曲ID
     * @param lyricsFile 歌词文件
     * @return
     */
    @PostMapping("/lyrics/{songId}")
    public R uploadLyrics(@PathVariable("songId") Long songId,
                          @RequestParam("lyricsFile") MultipartFile lyricsFile,
                          @RequestParam("msg") String msg
                          ) {
        return musicAppService.uploadLyrics(songId, lyricsFile,msg);
    }

    /**
     * 上传/更新歌曲封面
     * @param avatarFile 封面文件
     * @param md5 封面文件校验值
     * @return
     */
    @PostMapping("/cover/song")
    public R uploadSongAvatar(@RequestParam("id") Long id,
                              @RequestParam("avatarFile") MultipartFile avatarFile,
                              @RequestParam("md5") String md5) {
        return musicAppService.uploadSongAvatar(id, avatarFile, md5);
    }

    public R listSongs(Long userId) { return listSongs(userId, null, null); }

    @GetMapping("/songs")
    public R listSongs(@RequestParam(value = "userId", required = false) Long userId,
                       @RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "size", required = false) Integer size) {
        if (page != null && size != null && page > 0 && size > 0) {
            R pageResult = musicAppService.listSongsPage(userId, ((long) page - 1L) * size, size);
            return org.L2.common.ListPagination.fromPage(pageResult, musicAppService.countActiveSongs(), page, size);
        }
        return org.L2.common.ListPagination.apply(musicAppService.listSongs(userId), page, size);
    }

    @GetMapping("/songs/random")
    public R randomSongs(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return musicAppService.randomSongs(limit);
    }
    /**
     * 下载歌曲
     * @param songId 歌曲ID
     * @return 歌曲文件流
     */
//    @GetMapping("/download/{songId}")
//    public ResponseEntity<Resource> downloadSong(@PathVariable Long songId) {
//        return musicAppService.downloadSong(songId);
//    }

    /**
     * 获取歌曲基本信息
     * @param songId 歌曲ID
     * @return 歌曲基本信息
     */
    @GetMapping("/info/song/{songId}")
    public R getSongBaseInfo(@PathVariable("songId") Long songId,
                             @RequestParam(value = "userId", required = false) Long userId) {
        return musicAppService.getSongBaseInfo(songId, userId);
    }

    /**
     * 获取歌曲详情
     * @param songId 歌曲ID
     * @return 歌曲详细信息
     */
    @GetMapping("/details/song/{songId}")
    public R getSongDetailsInfo(@PathVariable("songId") Long songId,
                                @RequestParam(value = "userId", required = false) Long userId) {
        return musicAppService.getSongDetailsInfo(songId, userId);
    }

    @GetMapping("/share/song/{songId}")
    public R getSongShareInfo(@PathVariable("songId") Long songId) {
        return musicAppService.getSongShareInfo(songId);
    }

    /**
     * 用户播放歌曲：
     * 1. 发送一条播放记录到 RabbitMQ（目前只包含 userId）
     * 2. 返回与 getSongDetailsInfo 相同的 SongDetailsDTO
     */
    @GetMapping("/play/song/{songId}")
    public R playSong(@PathVariable("songId") Long songId,
                      @RequestParam(value = "userId", required = false) Long userId) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            userId = trustedUserId;
        }
        return musicAppService.playSong(songId, userId);
    }

    /** 客户端独立上报播放开始，不查询歌曲详情。 */
    @PostMapping("/play/start")
    public R reportPlayStart(@RequestBody PlayStartRequest body) {
        return musicAppService.reportPlayStart(UserContext.getUserId(), body);
    }

    @PostMapping("/song/{songId}/duration")
    public R updateSongDuration(@PathVariable("songId") Long songId,
                                @RequestParam("duration") Integer duration) {
        return musicAppService.updateSongDuration(songId, duration);
    }

    @PostMapping("/song/{songId}/status")
    public R updateSongStatus(@PathVariable("songId") Long songId,
                              @RequestParam("status") Byte status) {
        return musicAppService.updateSongStatus(songId, status);
    }

    @PostMapping("/song/{songId}/random-enabled")
    public R updateSongRandomEnabled(@PathVariable("songId") Long songId,
                                     @RequestParam("enabled") Boolean enabled) {
        return musicAppService.updateSongRandomEnabled(songId, enabled);
    }

    @PostMapping("/play/end")
    public R reportPlayEnd(@RequestBody java.util.Map<String, Object> body) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            body.put("userId", trustedUserId);
        }
        return musicAppService.reportPlayEnd(body);
    }

   
    /**
     * 获取用户收藏歌曲
     */
    public R getUserFavoriteSongs(Long userId) { return getUserFavoriteSongs(userId, null, null); }

    @GetMapping("/user/favorite/songs")
    public R getUserFavoriteSongs(@RequestParam("userId") Long userId,
                                  @RequestParam(value = "page", required = false) Integer page,
                                  @RequestParam(value = "size", required = false) Integer size) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            userId = trustedUserId;
        }
        if (page != null && size != null && page > 0 && size > 0) {
            R favorites = musicAppService.getUserFavoriteSongsPage(userId, ((long) page - 1L) * size, size);
            if (!Boolean.TRUE.equals(favorites.getPassed())) return favorites;
            return org.L2.common.ListPagination.fromPage(favorites,
                    musicAppService.countUserFavoriteSongs(userId), page, size);
        }
        return org.L2.common.ListPagination.apply(musicAppService.getUserFavoriteSongs(userId), page, size);
    }

    /**
     * 获取歌曲所有歌词
     * @param songId
     * @return
     */
    @GetMapping("/lyrics/all/{songId}")
    public R getAllLyrics(@PathVariable("songId") Long songId) {
        return musicAppService.getAllLyricsBySongId(songId);
    }

    @GetMapping("/lyrics/{lyricsId}")
    public R getLyrics(@PathVariable("lyricsId") Long lyricsId) {
        return musicAppService.getLyrics(lyricsId);
    }
    /*
     * 歌单相关
     */

    /**
     * 创建歌单
     * @param playlistCreateRequest 歌单信息
     * @return 创建结果
     */
    @PostMapping("/playlist")
    public R createPlaylist(@RequestBody PlaylistCreateRequest playlistCreateRequest) {
        return musicAppService.createPlaylist(playlistCreateRequest);
    }

    @PostMapping("/playlist/update")
    public R updatePlaylist(@RequestBody PlaylistUpdateRequest playlistUpdateRequest) {
        return musicAppService.updatePlaylist(playlistUpdateRequest);
    }

    /**
     * 生成用户播放列表
     * @param userId 用户ID
     * @return
     */
    @PostMapping("/curPlaylist")
    public R createUserCurrentPlaylist(@RequestBody Long userId) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            userId = trustedUserId;
        }
        return musicAppService.createUserCurrentPlaylist(userId);
    }

    /**
     * 上传/更新歌单封面
     * @param avatarFile 封面文件
     * @param md5 封面文件校验值
     * @return
     */
    @PostMapping("/cover/playlist")
    public R uploadPlaylistAvatar(@RequestParam("id") Long id,
                                  @RequestParam("avatarFile") MultipartFile avatarFile,
                                  @RequestParam("md5") String md5) {
        return musicAppService.uploadPlaylistAvatar(id, avatarFile, md5);
    }

    /**
     * 删除歌单
     * @param playlistId 歌单ID
     * @return 删除结果
     */
    @DeleteMapping("/playlist")
    public R deletePlaylist(@RequestParam("playlistId") Long playlistId) {
        return musicAppService.deletePlaylist(playlistId);
    }

    /**
     * 清空用户播放列表
     * @param userId 用户ID
     * @return
     */
    @PostMapping("/playlist/clear")
    public R clearUserCurrentPlaylist(@RequestParam("userId") Long userId) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            userId = trustedUserId;
        }
        return musicAppService.clearUserCurrentPlaylist(userId);
    }

    @PostMapping("/playlist/current/replace")
    public R replaceCurrentPlaylistSongs(@RequestBody PlaylistSongListRequest request) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            request.setUserId(trustedUserId);
        }
        return musicAppService.replaceCurrentPlaylistSongs(request);
    }

    @PostMapping("/playlist/clear-songs")
    public R clearPlaylistSongs(@RequestParam("playlistId") Long playlistId) {
        return musicAppService.clearPlaylistSongs(playlistId);
    }

    /**
     * 向歌单添加或移除歌曲
     * @param playlistSongRequest 歌单歌曲请求
     * @return 操作结果
     */
    @PostMapping("/playlist/song")
    public R managePlaylistSong(@RequestBody PlaylistSongRequest playlistSongRequest) {
        return musicAppService.managePlaylistSong(playlistSongRequest);
    }

    /**
     * 向歌单批量添加或移除歌曲
     //* @param playlistSongRequestList 歌单歌曲批量请求
     * @return 操作结果
     */
//    @PostMapping("/playlist/songs")
//    public R managePlaylistSong(@RequestBody PlaylistSongRequestList playlistSongRequestList) {
//        return musicAppService.managePlaylistSongList(playlistSongRequestList);
//    }

    @GetMapping("/info/playlist/{playlistId}")
    public R getPlaylistBaseInfo(@PathVariable("playlistId") Long playlistId) {
        return musicAppService.getPlaylistBaseInfo(playlistId);
    }

    /**
     * 获取歌单详情
     * @param playlistId 歌单ID
     * @return 歌单详细信息
     */
    public R getPlaylistDetailsInfo(Long playlistId) { return getPlaylistDetailsInfo(playlistId, null, null); }

    @GetMapping("/details/playlist/{playlistId}")
    public R getPlaylistDetailsInfo(@PathVariable("playlistId") Long playlistId,
                                    @RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "size", required = false) Integer size) {
        if (page == null && size == null) return musicAppService.getPlaylistDetailsInfo(playlistId);
        if (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1))
            return R.error("分页参数无效");
        return musicAppService.getPlaylistDetailsInfo(playlistId, page, size);
    }

    /**
     * 发现更多歌单
     * @param userId 当前用户 ID，可选
     * @return 符合条件的歌单列表
     */
    public R discoverPlaylists(Long userId) { return discoverPlaylists(userId, null, null); }

    public R discoverPlaylists(Long userId, Integer page, Integer size) {
        return discoverPlaylists(userId, page, size, false);
    }

    @GetMapping("/discover/playlists")
    public R discoverPlaylists(@RequestParam(value = "userId", required = false) Long userId,
                               @RequestParam(value = "page", required = false) Integer page,
                               @RequestParam(value = "size", required = false) Integer size,
                               @RequestParam(value = "ownerOnly", defaultValue = "false") boolean ownerOnly) {
        Long trustedUserId = UserContext.getUserId();
        if (ownerOnly && (trustedUserId == null || !trustedUserId.equals(userId))) {
            return R.error("只能查看自己的私人歌单");
        }
        // A supplied userId must not grant access to another user's private playlists.
        userId = trustedUserId;
        if (page != null && size != null && page > 0 && size > 0) {
            if (ownerOnly) {
                R owned = musicAppService.discoverOwnPlaylistsPage(userId, ((long) page - 1L) * size, size);
                return org.L2.common.ListPagination.fromPage(owned, musicAppService.countOwnPlaylists(userId), page, size);
            }
            R pageResult = musicAppService.discoverPlaylistsPage(userId, ((long) page - 1L) * size, size);
            return org.L2.common.ListPagination.fromPage(pageResult, musicAppService.countDiscoverPlaylists(userId), page, size);
        }
        if (ownerOnly) return org.L2.common.ListPagination.apply(musicAppService.discoverOwnPlaylists(userId), page, size);
        return org.L2.common.ListPagination.apply(musicAppService.discoverPlaylists(userId), page, size);
    }

    public R listPlaylists(Long userId) { return listPlaylists(userId, null, null, null); }

    @GetMapping("/playlists")
    public R listPlaylists(@RequestParam(value = "userId", required = false) Long userId,
                           @RequestParam(value = "page", required = false) Integer page,
                           @RequestParam(value = "size", required = false) Integer size,
                           @RequestParam(value = "search", required = false) String search) {
        if (search != null && !search.isBlank()) {
            if (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1))
                return R.error("分页参数无效");
            String keyword = search.trim();
            R result = musicAppService.searchPlaylists(userId, keyword, size == 0 ? 0 : ((long) page - 1L) * size,
                    size == 0 ? null : size);
            return size == 0 ? org.L2.common.ListPagination.apply(result, page, size)
                    : org.L2.common.ListPagination.fromPage(result, musicAppService.countPlaylists(userId, keyword), page, size);
        }
        if (page != null && size != null && page > 0 && size > 0) {
            R pageResult = musicAppService.listPlaylistsPage(userId, ((long) page - 1L) * size, size);
            return org.L2.common.ListPagination.fromPage(pageResult, musicAppService.countPlaylists(userId), page, size);
        }
        return org.L2.common.ListPagination.apply(musicAppService.listPlaylists(userId), page, size);
    }

    @GetMapping("/user/{creatorId}/playlists/public")
    public R publicPlaylistsByCreator(@PathVariable("creatorId") Long creatorId,
                                      @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        if (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1))
            return R.error("分页参数无效");
        R result = musicAppService.publicPlaylistsByCreator(creatorId,
                size == 0 ? 0 : ((long) page - 1L) * size, size == 0 ? null : size);
        if (size == 0) return org.L2.common.ListPagination.apply(result, page, size);
        return org.L2.common.ListPagination.fromPage(result,
                musicAppService.countPublicPlaylistsByCreator(creatorId), page, size);
    }

    /**
     * 获取用户当前播放列表
     * @param userId 用户ID
     * @return 当前列表信息
     */
    @GetMapping("/playlist/current")
    public R getCurrentPlaylist(@RequestParam("userId") Long userId) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId != null) {
            userId = trustedUserId;
        }
        return musicAppService.getCurrentPlaylist(userId);
    }

    /*
     * 歌手相关
     */

    /**
     * 创建歌手
     * @param singerCreateRequest 歌手信息
     * @return 创建结果
     */
    @PostMapping("/singer")
    public R createSinger(@RequestBody SingerCreateRequest singerCreateRequest) {
        return musicAppService.createSinger(singerCreateRequest);
    }

    /**
     * 删除歌手
     * @param singerId
     * @return
     */
    @DeleteMapping("/singer")
    public R deleteSinger(@RequestParam("singerId") Long singerId) {
        return musicAppService.deleteSinger(singerId);
    }

    /**
     * 更改歌手资料
     * @param singerFieldsUpdateRequest 歌手资料信息
     * @return
     */
    @PostMapping("/update-profile")
    public R updateProfile(@RequestBody SingerFieldsUpdateRequest singerFieldsUpdateRequest) {
        return musicAppService.updateSingerProfile(singerFieldsUpdateRequest);
    }

    /**
     * 更新歌手头像
     * @param avatarFile 头像文件
     * @param md5 头像文件校验值
     * @return
     */
    @PostMapping("/update-avatar")
    public R updateAvatar(@RequestParam("id") Long id,
                          @RequestParam("avatarFile") MultipartFile avatarFile,
                          @RequestParam("md5") String md5) {
        return musicAppService.updateSingerAvatar(id, avatarFile, md5);
    }

    public R listSingers() { return listSingers(null, null, null); }

    @GetMapping("/singers")
    public R listSingers(@RequestParam(value = "page", required = false) Integer page,
                         @RequestParam(value = "size", required = false) Integer size,
                         @RequestParam(value = "search", required = false) String search) {
        if (search != null && !search.isBlank()) {
            if (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1))
                return R.error("分页参数无效");
            String keyword = search.trim();
            R result = musicAppService.searchSingers(keyword, size == 0 ? 0 : ((long) page - 1L) * size,
                    size == 0 ? null : size);
            return size == 0 ? org.L2.common.ListPagination.apply(result, page, size)
                    : org.L2.common.ListPagination.fromPage(result, musicAppService.countSingers(keyword), page, size);
        }
        if (page != null && size != null && page > 0 && size > 0) {
            R pageResult = musicAppService.listSingersPage(((long) page - 1L) * size, size);
            return org.L2.common.ListPagination.fromPage(pageResult, musicAppService.countSingers(), page, size);
        }
        return org.L2.common.ListPagination.apply(musicAppService.listSingers(), page, size);
    }

    /**
     * 获取歌手基本信息
     * @param singerId 歌手ID
     * @return 歌手基本信息
     */
    @GetMapping("/info/player/{singerId}")
    public R getSingerBaseInfo(@PathVariable("singerId") Long singerId) {
        return musicAppService.getSingerBaseInfo(singerId);
    }

    /**
     * 获取歌手详情
     * @param singerId 歌手ID
     * @return 歌手详细信息
     */
    public R getSingerDetailsInfo(Long singerId) { return getSingerDetailsInfo(singerId, null, null); }

    @GetMapping("/details/player/{singerId}")
    public R getSingerDetailsInfo(@PathVariable("singerId") Long singerId,
                                  @RequestParam(value = "page", required = false) Integer page,
                                  @RequestParam(value = "size", required = false) Integer size) {
        if (page == null && size == null) return musicAppService.getSingerDetailsInfo(singerId);
        if (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1))
            return R.error("分页参数无效");
        return musicAppService.getSingerDetailsInfo(singerId, page, size);
    }

    /*
     * 搜索相关
     */

    @GetMapping("/search")
    public R search(@RequestParam("keyword") String keyword,
                    @RequestParam(value = "page", defaultValue = "0") int page,
                    @RequestParam(value = "size", defaultValue = "10") int size) {
        if (page >= 1 && size >= 0) {
            if (size == 0) return org.L2.common.ListPagination.apply(musicAppService.searchAll(keyword), page, size);
            long end = (long) page * size;
            if (end > 10000L) return org.L2.common.ListPagination.apply(musicAppService.searchAll(keyword), page, size);
            return musicAppService.searchPage(keyword, (int) (((long) page - 1L) * size), size, page);
        }
        if (size > 50) size = 50;
        return musicAppService.search(keyword, page, size);
    }

    @PostMapping("/search/sync")
    public R fullSyncToEs() {
        return musicAppService.fullSyncToEs();
    }

    @PostMapping("/recalc-volume-gain")
    public R recalcVolumeGain() {
        return musicAppService.recalcVolumeGain();
    }

    /**
     * 测试接口，用于初期测试
     * @return 测试结果
     */
    @Deprecated
    @GetMapping("/test")
    public R test() {
        return R.success("Test successful", "Hello from music-service!");
    }

    @PostMapping("/song/favorite")
    public R toggleSongFavorite(@RequestBody SongFavoriteRequest songFavoriteRequest) {
        Long trustedUserId = UserContext.getUserId();
        if (trustedUserId == null) {
            return R.error("用户未登录");
        }
        return musicAppService.toggleFavoriteSong(trustedUserId, songFavoriteRequest.getSongId());
    }

    /*
     * 视频相关
     */
    @PostMapping("/video/upload")
    public R uploadVideo(@RequestParam(value = "singerId", required = false) Long singerId,
                         @RequestParam("title") String title,
                         @RequestParam("videoFile") MultipartFile videoFile,
                         @RequestParam("md5") String md5) {
        return musicAppService.uploadVideo(singerId, title, videoFile, md5);
    }

    @PostMapping("/video/update")
    public R updateVideoMeta(@RequestBody VideoUpdateRequest videoUpdateRequest) {
        return musicAppService.updateVideoMeta(videoUpdateRequest);
    }

    @GetMapping("/video/{id}")
    public R getVideoInfo(@PathVariable("id") Long id) {
        return musicAppService.getVideoInfo(id);
    }

    public R listVideos() { return listVideos(null, null); }

    @GetMapping("/videos")
    public R listVideos(@RequestParam(value = "page", required = false) Integer page,
                        @RequestParam(value = "size", required = false) Integer size) {
        return org.L2.common.ListPagination.apply(musicAppService.listVideos(), page, size);
    }

    @DeleteMapping("/video/{id}")
    public R deleteVideo(@PathVariable("id") Long id) {
        return musicAppService.deleteVideo(id);
    }

    @GetMapping("/playback-state")
    public R getPlaybackState(@RequestParam(value = "userId", required = false) Long userId) {
        return musicAppService.getPlaybackState(userId);
    }

    @PutMapping("/playback-state")
    public R savePlaybackState(@RequestParam(value = "userId", required = false) Long userId,
                               @RequestBody java.util.Map<String, String> state) {
        return musicAppService.savePlaybackState(userId, state);
    }
}
