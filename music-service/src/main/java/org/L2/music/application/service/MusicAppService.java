package org.L2.music.application.service;


import org.L2.common.R;
import org.L2.music.application.dto.*;
import org.L2.music.application.request.PlaylistCreateRequest;
import org.L2.music.application.request.PlaylistSongRequest;
import org.L2.music.application.request.SingerCreateRequest;
import org.L2.music.application.request.SingerUpdateRequest;
import org.L2.music.domain.model.Playlist;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.service.PlaylistService;
import org.L2.music.domain.service.SingerService;
import org.L2.music.domain.service.SongService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class MusicAppService {

    @Autowired
    private SongService songService;
    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private SingerService singerService;

    /*
     * 歌曲相关
     */
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

    public R uploadSong(Long id, MultipartFile file, String md5){
        return songService.uploadSong(id,file);
    }
    /*
     * 歌单相关
     */
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

    public R getPlaylistBaseInfo(Long playlistId) {
        R result = playlistService.getPlaylistInfo(playlistId);
        if(!result.getPassed()){
            return result;
        }
        PlaylistBaseDTO playlistBaseDTO = new PlaylistBaseDTO();
        BeanUtils.copyProperties(result.getData(), playlistBaseDTO);
        return R.success("获取成功",playlistBaseDTO);
    }

    public R getPlaylistDetailsInfo(Long playlistId) {
        R result = playlistService.getPlaylistInfo(playlistId);
        if(!result.getPassed()){
            return result;
        }
        PlaylistDetailsDTO playlistDetailsDTO = new PlaylistDetailsDTO();
        BeanUtils.copyProperties(result.getData(), playlistDetailsDTO);
        R songs = songService.getPlaylistSongs(playlistId);
        playlistDetailsDTO.setSongs((List<SongBaseDTO>)songs.getData());

        return R.success("获取成功",playlistDetailsDTO);
    }

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

    /*
     * 歌手相关
     */
    public R getSingerBaseInfo(Long singerId) {
        R result = singerService.getSingerInfo(singerId);
        if(!result.getPassed()){
            return result;
        }
        SingerBaseDTO singerBaseDTO = new SingerBaseDTO();
        BeanUtils.copyProperties(result.getData(), singerBaseDTO);
        return R.success("获取成功",singerBaseDTO);
    }

    public R getSingerDetailsInfo(Long singerId) {
        R result = singerService.getSingerInfo(singerId);
        if(!result.getPassed()){
            return result;
        }
        SingerDetailsDTO singerDetailsDTO = new SingerDetailsDTO();
        BeanUtils.copyProperties(result.getData(), singerDetailsDTO);
        R songs = songService.getSingerSongs(singerId);
        singerDetailsDTO.setSongs((List<SongBaseDTO>)songs.getData());
        return R.success("获取成功",singerDetailsDTO);
    }

    public R createSinger(SingerCreateRequest singerCreateRequest) {
        Singer singer = new Singer();
        BeanUtils.copyProperties(singerCreateRequest, singer);
        return singerService.createSinger(singer);
    }

    public R deleteSinger(Long singerId) {
        try {
            singerService.deleteSinger(singerId);
        }catch (Exception e) {
            return R.error("删除失败"+e.getMessage());
        }
        return R.success("删除成功");
    }


    public R updateSingerProfile(SingerUpdateRequest singerUpdateRequest){
        Singer singer = new Singer();
        BeanUtils.copyProperties(singerUpdateRequest, singer);
        return singerService.updateSinger(singer);
    }

    public R updateSingerAvatar(Long id, MultipartFile avatarFile, String md5){
        return singerService.updateSingerAvatar(id,avatarFile);
    }
}
