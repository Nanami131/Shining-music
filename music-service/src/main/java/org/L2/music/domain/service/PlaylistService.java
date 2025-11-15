package org.L2.music.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.music.constant.Constants;
import org.L2.music.domain.model.Playlist;
import org.L2.music.infrastructure.PlaylistMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlaylistService {
    @Autowired
    private PlaylistMapper playlistMapper;
    @Autowired
    private SongMapper songMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private SimpleMinioService simpleMinioService;

    public R managePlaylistSong(Long playlistId, Long songId) throws Exception {
        if (playlistId == null || songId == null) {
            return R.error("歌单或歌曲不能为空");
        }
        if (songMapper.selectById(songId) == null) {
            return R.error("歌曲不存在");
        }
        if (playlistMapper.selectById(playlistId) == null) {
            return R.error("歌单不存在");
        }
        boolean flag = stringRedisTemplate.opsForSet()
                .isMember("playlist:" + playlistId, String.valueOf(songId));
        if (flag) {
            stringRedisTemplate.opsForSet().remove("playlist:" + playlistId, String.valueOf(songId));
        } else {
            if (stringRedisTemplate.opsForSet().size("playlist:" + playlistId) >= Constants.MAX_PLAYLIST_SIZE) {
                return R.error("歌单歌曲数量已达上限");
            }
            stringRedisTemplate.opsForSet().add("playlist:" + playlistId, String.valueOf(songId));
        }
        return R.success("歌单歌曲修改成功");
    }

    @AutoFill(OperationType.INSERT)
    public R createPlaylist(Playlist playlist) {
        if (playlist.getType() == null) {
            return R.error("歌单信息不全");
        }
        if (playlist.getType() == Constants.USER_FAVORITE
                || playlist.getType() == Constants.CURRENT_PLAYLIST) {
            List<Playlist> query = playlistMapper.query(new Playlist()
                    .setUserId(playlist.getUserId())
                    .setType(playlist.getType()));
            if (query != null && !query.isEmpty()) {
                return R.error("非法重复创建");
            }
            playlist.setName("收藏歌单" + playlist.getUserId());
        }
        if (playlist.getName() == null || playlist.getName().isEmpty()) {
            return R.error("歌单名称不能为空");
        }
        playlist.setVisibility((byte) 1);
        try {
            playlistMapper.insert(playlist);
        } catch (Exception e) {
            return R.error("创建歌单失败" + e.getMessage());
        }
        return R.success("创建歌单成功");
    }

    public R getPlaylistInfo(Long playlistId) {
        try {
            Playlist playlist = playlistMapper.selectById(playlistId);
            // TODO: 需要鉴权；还要考虑可见性的情况
            if (playlist == null) {
                return R.error("歌单不存在");
            }
            return R.success("获取歌单信息成功", playlist);
        } catch (Exception e) {
            return R.error("获取歌单信息失败" + e.getMessage());
        }
    }

    public R uploadPlaylistAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName = FileNameGenerateService.defineNamePath(originalFilename, "/playlist/cover/", id, 5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Playlist playlist = new Playlist()
                .setId(id)
                .setUpdatedAt(LocalDateTime.now())
                .setCoverUrl(avatarUrl);
        String result = simpleMinioService.uploadFile(file, fileName);
        if (!"上传成功".equals(result)) {
            return R.error(result);
        }
        try {
            playlistMapper.update(playlist);
        } catch (Exception e) {
            return R.error("数据库更新失败" + e.getMessage());
        }

        return R.success("封面修改成功", avatarUrl);
    }

    public R clearUserCurrentPlaylist(Playlist playlist) {
        try {
            List<Playlist> query = playlistMapper.query(playlist);
            if (query == null || query.isEmpty()) {
                createPlaylist(playlist.setName("当前列表" + playlist.getUserId()));
                return R.error("当前用户没有播放列表");
            }
            stringRedisTemplate.delete("playlist:" + query.get(0).getId());
            return R.success("清空成功");
        } catch (Exception e) {
            return R.error("清空失败" + e.getMessage());
        }
    }

    public R deletePlaylist(Long playlistId) {
        try {
            Playlist playlist = playlistMapper.selectById(playlistId);
            if (playlist == null) {
                return R.error("歌单不存在");
            }
            playlistMapper.deleteById(playlistId);
            stringRedisTemplate.delete("playlist:" + playlistId);
            return R.success("删除歌单成功");
        } catch (Exception e) {
            return R.error("删除歌单失败" + e.getMessage());
        }
    }
}
