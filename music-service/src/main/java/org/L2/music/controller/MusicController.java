package org.L2.music.controller;

import org.L2.common.R;
import org.L2.common.mq.PlayRecordProducer;
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

    @Autowired
    private PlayRecordProducer playRecordProducer;

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

    /**
     * 用户播放歌曲：
     * 1. 发送一条播放记录到 RabbitMQ（目前只包含 userId）
     * 2. 返回与 getSongDetailsInfo 相同的 SongDetailsDTO
     */
    @GetMapping("/play/song/{songId}")
    public R playSong(@PathVariable("songId") Long songId,
                      @RequestParam(value = "userId", required = false) Long userId) {
        log.info("Received playSong request, songId={}, userId={}", songId, userId);
        if (userId != null) {
            try {
                playRecordProducer.sendPlayRecord(userId,songId);
                log.info("Play record message sent via RabbitMQ, userId={}", userId);
            } catch (Exception e) {
                // 不因为 MQ 失败而影响播放本身
                log.error("Failed to send play record message to RabbitMQ, userId={}", userId, e);
            }
        } else {
            log.warn("playSong called without userId, skip MQ message");
        }
        return musicAppService.getSongDetailsInfo(songId, userId);
    }

   
    /**
     * 获取用户收藏歌曲
     */
    @GetMapping("/user/favorite/songs")
    public R getUserFavoriteSongs(@RequestParam("userId") Long userId) {
        return musicAppService.getUserFavoriteSongs(userId);
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

    /**
     * 生成用户播放列表
     * @param userId 用户ID
     * @return
     */
    @PostMapping("/curPlaylist")
    public R createUserCurrentPlaylist(@RequestBody Long userId) {
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
        return musicAppService.clearUserCurrentPlaylist(userId);
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
    @GetMapping("/details/playlist/{playlistId}")
    public R getPlaylistDetailsInfo(@PathVariable("playlistId") Long playlistId) {
        return musicAppService.getPlaylistDetailsInfo(playlistId);
    }

    /**
     * 发现更多歌单
     * @param userId 当前用户 ID，可选
     * @return 符合条件的歌单列表
     */
    @GetMapping("/discover/playlists")
    public R discoverPlaylists(@RequestParam(value = "userId", required = false) Long userId) {
        return musicAppService.discoverPlaylists(userId);
    }

    /**
     * 获取用户当前播放列表
     * @param userId 用户ID
     * @return 当前列表信息
     */
    @GetMapping("/playlist/current")
    public R getCurrentPlaylist(@RequestParam("userId") Long userId) {
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
    @GetMapping("/details/player/{singerId}")
    public R getSingerDetailsInfo(@PathVariable("singerId") Long singerId) {
        return musicAppService.getSingerDetailsInfo(singerId);
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
        return musicAppService.toggleFavoriteSong(
                songFavoriteRequest.getUserId(),
                songFavoriteRequest.getSongId()
        );
    }
}

