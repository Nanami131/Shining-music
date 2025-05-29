package org.L2.music.controller;

import org.L2.common.R;
import org.L2.music.application.dto.*;
import org.L2.music.application.service.MusicAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/music")
public class MusicController {

    @Autowired
    private MusicAppService musicAppService;

    /**
     * 上传歌曲
     * @param file 歌曲文件
     * @param songRequest 歌曲元数据
     * @return 上传结果
     */
    @PostMapping("/upload")
    public R uploadSong(@RequestPart("file") MultipartFile file,
                        @RequestPart("metadata") SongRequest songRequest) {
        return musicAppService.uploadSong(file, songRequest);
    }

    /**
     * 下载歌曲
     * @param songId 歌曲ID
     * @return 歌曲文件流
     */
    @GetMapping("/download/{songId}")
    public ResponseEntity<Resource> downloadSong(@PathVariable Long songId) {
        return musicAppService.downloadSong(songId);
    }

    /**
     * 获取歌曲详情
     * @param songId 歌曲ID
     * @return 歌曲详细信息
     */
    @GetMapping("/info/{songId}")
    public R getSongInfo(@PathVariable Long songId) {
        return musicAppService.getSongInfo(songId);
    }

    /**
     * 管理收藏（添加或移除歌曲到用户收藏歌单）
     * @param favoriteRequest 收藏请求
     * @return 收藏操作结果
     */
    @PostMapping("/favorite")
    public R manageFavorite(@RequestBody FavoriteRequest favoriteRequest) {
        return musicAppService.manageFavorite(favoriteRequest);
    }

    /**
     * 创建歌单
     * @param playlistRequest 歌单信息
     * @return 创建结果
     */
    @PostMapping("/playlist")
    public R createPlaylist(@RequestBody PlaylistRequest playlistRequest) {
        return musicAppService.createPlaylist(playlistRequest);
    }

    /**
     * 删除歌单
     * @param playlistId 歌单ID
     * @return 删除结果
     */
    @DeleteMapping("/playlist/{playlistId}")
    public R deletePlaylist(@PathVariable Long playlistId) {
        return musicAppService.deletePlaylist(playlistId);
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
     * 获取歌单详情
     * @param playlistId 歌单ID
     * @return 歌单详细信息
     */
    @GetMapping("/playlist/{playlistId}")
    public R getPlaylistInfo(@PathVariable Long playlistId) {
        return musicAppService.getPlaylistInfo(playlistId);
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
}