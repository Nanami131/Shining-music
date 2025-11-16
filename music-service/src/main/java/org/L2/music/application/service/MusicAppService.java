package org.L2.music.application.service;

import org.L2.common.R;
import org.L2.common.rpc.UserClient;
import org.L2.music.application.dto.*;
import org.L2.music.application.request.*;
import org.L2.music.constant.Constants;
import org.L2.music.domain.model.Lyrics;
import org.L2.music.domain.model.Playlist;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.model.Song;
import org.L2.music.domain.service.LyricsService;
import org.L2.music.domain.service.PlaylistService;
import org.L2.music.domain.service.SingerService;
import org.L2.music.domain.service.SongService;
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

    /*
     * 歌曲模块
     */
    public R createSong(SongCreateRequest songCreateRequest) {
        Song song = new Song();
        BeanUtils.copyProperties(songCreateRequest, song);
        return songService.createSong(song);
    }

    public R getSongBaseInfo(Long songId) {
        R result = songService.getSongInfo(songId);
        if (!result.getPassed()) {
            return result;
        }
        SongBaseDTO songBaseDTO = new SongBaseDTO();
        BeanUtils.copyProperties(result.getData(), songBaseDTO);
        return R.success("获取成功", songBaseDTO);
    }

    public R getSongDetailsInfo(Long songId) {
        R result = songService.getSongInfo(songId);
        if (!result.getPassed()) {
            return result;
        }
        SongDetailsDTO songDetailsDTO = new SongDetailsDTO();
        BeanUtils.copyProperties(result.getData(), songDetailsDTO);
        @SuppressWarnings("unchecked")
        ArrayList<Lyrics> allLyrics =
                (ArrayList<Lyrics>) lyricsService.getAllLyricsBySongId(songId).getData();
        songDetailsDTO.setAllLyrics(allLyrics);
        return R.success("获取成功", songDetailsDTO);
    }

    public R uploadLyrics(Long songId, MultipartFile file, String msg) {
        return lyricsService.uploadLyrics(songId, file, msg);
    }

    public R getAllLyricsBySongId(Long songId) {
        return lyricsService.getAllLyricsBySongId(songId);
    }

    public R getLyrics(Long lyricsId) {
        return lyricsService.getLyrics(lyricsId);
    }

    public R uploadSong(Long id, MultipartFile file, String md5) {
        return songService.uploadSong(id, file);
    }

    public R uploadSongAvatar(Long id, MultipartFile avatarFile, String md5) {
        return songService.uploadSongAvatar(id, avatarFile);
    }

    /*
     * 歌单模块
     */
    public R deletePlaylist(Long playlistId) {
        return playlistService.deletePlaylist(playlistId);
    }

    public R createPlaylist(PlaylistCreateRequest playlistCreateRequest) {
        Playlist playlist = new Playlist();
        BeanUtils.copyProperties(playlistCreateRequest, playlist);
        // 前端传入的是用户 id 字段
        playlist.setUserId(playlistCreateRequest.getId());
        playlist.setId(null);
        return playlistService.createPlaylist(playlist);
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

    public R managePlaylistSong(PlaylistSongRequest playlistSongRequest) {
        Long playlistId = playlistSongRequest.getPlaylistId();
        Long songId = playlistSongRequest.getSongId();
        try {
            return playlistService.managePlaylistSong(playlistId, songId);
        } catch (Exception e) {
            return R.error("操作失败" + e.getMessage());
        }
    }

    public R uploadPlaylistAvatar(Long id, MultipartFile avatarFile, String md5) {
        return playlistService.uploadPlaylistAvatar(id, avatarFile);
    }

    public R clearUserCurrentPlaylist(Long userId) {
        Playlist playlist = new Playlist()
                .setUserId(userId)
                .setType(Constants.CURRENT_PLAYLIST);
        return playlistService.clearUserCurrentPlaylist(playlist);
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

    public R createSinger(SingerCreateRequest singerCreateRequest) {
        Singer singer = new Singer();
        BeanUtils.copyProperties(singerCreateRequest, singer);
        return singerService.createSinger(singer);
    }

    public R deleteSinger(Long singerId) {
        try {
            singerService.deleteSinger(singerId);
        } catch (Exception e) {
            return R.error("删除失败" + e.getMessage());
        }
        return R.success("删除成功");
    }

    public R updateSingerProfile(SingerFieldsUpdateRequest singerFieldsUpdateRequest) {
        Singer singer = new Singer();
        BeanUtils.copyProperties(singerFieldsUpdateRequest, singer);
        return singerService.updateSinger(singer);
    }

    public R updateSingerAvatar(Long id, MultipartFile avatarFile, String md5) {
        return singerService.updateSingerAvatar(id, avatarFile);
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

}

