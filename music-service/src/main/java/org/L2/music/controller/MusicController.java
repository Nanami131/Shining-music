package org.L2.music.controller;

import org.L2.common.R;
import org.L2.music.application.dto.*;
import org.L2.music.application.service.MusicAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
//    @PostMapping("/upload")
//    public R uploadSong(@RequestPart("file") MultipartFile file,
//                        @RequestPart("metadata") SongRequest songRequest) {
//        return musicAppService.uploadSong(file, songRequest);
//    }

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
    public R getSongBaseInfo(@PathVariable Long songId) {
        return musicAppService.getSongBaseInfo(songId);
    }

    /**
     * 获取歌曲详情
     * @param songId 歌曲ID
     * @return 歌曲详细信息
     */
    @GetMapping("/details/song/{songId}")
    public R getSongDetailsInfo(@PathVariable Long songId) {
        return musicAppService.getSongDetailsInfo(songId);
    }

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
     * 向歌单批量添加或移除歌曲
     //* @param playlistSongRequestList 歌单歌曲批量请求
     * @return 操作结果
     */
//    @PostMapping("/playlist/songs")
//    public R managePlaylistSong(@RequestBody PlaylistSongRequestList playlistSongRequestList) {
//        return musicAppService.managePlaylistSongList(playlistSongRequestList);
//    }

    @GetMapping("/info/playlist/{playlistId}")
    public R getPlaylistBaseInfo(Long playlistId) {
        return musicAppService.getPlaylistBaseInfo(playlistId);
    }

    /**
     * 获取歌单详情
     * @param playlistId 歌单ID
     * @return 歌单详细信息
     */
    @GetMapping("/details/playlist/{playlistId}")
    public R getPlaylistDetailsInfo(@PathVariable Long playlistId) {
        return musicAppService.getPlaylistDetailsInfo(playlistId);
    }

    /**
     * 获取歌手基本信息
     * @param singerId 歌手ID
     * @return 歌手基本信息
     */
    @GetMapping("/info/player/{singerId}")
    public R getSingerBaseInfo(@PathVariable Long singerId) {
        return musicAppService.getSingerBaseInfo(singerId);
    }

    /**
     * 获取歌手详情
     * @param singerId 歌手ID
     * @return 歌手详细信息
     */
    @GetMapping("/details/player/{singerId}")
    public R getSingerDetailsInfo(@PathVariable Long singerId) {
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
}