package org.L2.music.application.service;


import org.L2.common.R;
import org.L2.music.application.dto.PlaylistCreateRequest;
import org.L2.music.application.dto.PlaylistSongRequest;
import org.L2.music.application.dto.SongBaseDTO;
import org.L2.music.domain.model.Playlist;
import org.L2.music.domain.service.PlaylistService;
import org.L2.music.domain.service.SongService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MusicAppService {

    @Autowired
    private SongService songService;
    @Autowired
    private PlaylistService playlistService;


    public R managePlaylistSong(PlaylistSongRequest playlistSongRequest) {
        Long playlistId = playlistSongRequest.getPlaylistId();
        Long songId = playlistSongRequest.getSongId();
        try {
            return playlistService.managePlaylistSong(playlistId, songId);
        } catch (Exception e) {
            return R.error("操作失败"+e.getMessage());
        }
    }

//    public R managePlaylistSongList(PlaylistSongRequestList playlistSongRequestlist) {
//        Long playlistId = playlistSongRequestlist.getPlaylistId();
//        for(Long songId: playlistSongRequestlist.getSongIds()){
//            playlistService.managePlaylistSong(playlistId, songId);
//        }
//    }

    public R getSongBaseInfo(Long songId) {
        R result = songService.getSongInfo(songId);
        if(!result.getPassed()){
            return result;
        }
        SongBaseDTO songBaseDTO = new SongBaseDTO();
        BeanUtils.copyProperties(result.getData(), songBaseDTO);
        return R.success("获取成功",songBaseDTO);
    }
    public R getSongDetailsInfo(Long songId) {
        // 歌曲这里没有什么需要DTO的，直接返回
        return songService.getSongInfo(songId);
    }

    public R deletePlaylist(Long playlistId) {
        return null;
    }

    public R createPlaylist(PlaylistCreateRequest playlistCreateRequest) {
        Playlist playlist = new Playlist();
        BeanUtils.copyProperties(playlistCreateRequest, playlist);
        playlist.setUserId(playlistCreateRequest.getId());
        playlist.setId(null);
        return playlistService.createPlaylist(playlist);
    }


    public R getPlaylistDetailsInfo(Long playlistId) {
        // TODO:除了歌单 还有歌曲信息
        return null;
    }

    public R getSingerBaseInfo(Long singerId) {

    }

    public R getSingerDetailsInfo(Long singerId) {

    }
}
