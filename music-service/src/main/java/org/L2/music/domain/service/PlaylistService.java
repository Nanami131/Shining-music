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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        boolean exists = Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet()
                        .isMember("playlist:" + playlistId, String.valueOf(songId))
        );
        if (exists) {
            stringRedisTemplate.opsForSet().remove("playlist:" + playlistId, String.valueOf(songId));
        } else {
            Long size = stringRedisTemplate.opsForSet().size("playlist:" + playlistId);
            if (size != null && size >= Constants.MAX_PLAYLIST_SIZE) {
                return R.error("歌单歌曲数量已达上限");
            }
            stringRedisTemplate.opsForSet().add("playlist:" + playlistId, String.valueOf(songId));
        }
        return R.success("歌单歌曲修改成功");
    }

    public boolean isSongFavorite(Long userId, Long songId) {
        if (userId == null || songId == null) {
            return false;
        }
        Playlist favorite = findUserPlaylist(userId, Constants.USER_FAVORITE);
        if (favorite == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                .isMember("playlist:" + favorite.getId(), String.valueOf(songId)));
    }

    public R toggleFavoriteSong(Long userId, Long songId) {
        if (userId == null || songId == null) {
            return R.error("用户或歌曲不能为空");
        }
        if (songMapper.selectById(songId) == null) {
            return R.error("歌曲不存在");
        }
        Playlist favorite = ensureFavoritePlaylist(userId);
        if (favorite == null) {
            return R.error("初始化收藏歌单失败");
        }
        String key = "playlist:" + favorite.getId();
        String songKey = String.valueOf(songId);
        boolean exists = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, songKey));
        boolean favoriteNow;
        if (exists) {
            stringRedisTemplate.opsForSet().remove(key, songKey);
            favoriteNow = false;
        } else {
            Long size = stringRedisTemplate.opsForSet().size(key);
            if (size != null && size >= Constants.MAX_PLAYLIST_SIZE) {
                return R.error("收藏歌单已满");
            }
            stringRedisTemplate.opsForSet().add(key, songKey);
            favoriteNow = true;
        }
        return R.success("收藏状态更新成功", Map.of(
                "favorite", favoriteNow,
                "playlistId", favorite.getId()
        ));
    }

    public Playlist ensureFavoritePlaylist(Long userId) {
        if (userId == null) {
            return null;
        }
        return ensureUserPlaylist(userId, Constants.USER_FAVORITE);
    }

    public Playlist ensureCurrentPlaylist(Long userId) {
        if (userId == null) {
            return null;
        }
        return ensureUserPlaylist(userId, Constants.CURRENT_PLAYLIST);
    }

    private Playlist ensureUserPlaylist(Long userId, byte type) {
        Playlist playlist = findUserPlaylist(userId, type);
        if (playlist != null) {
            return playlist;
        }
        Playlist toCreate = new Playlist()
                .setUserId(userId)
                .setType(type)
                .setVisibility((byte) 1)
                .setName(defaultNameForType(type, userId))
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        playlistMapper.insert(toCreate);
        return toCreate;
    }

    private Playlist findUserPlaylist(Long userId, byte type) {
        if (userId == null) {
            return null;
        }
        List<Playlist> query = playlistMapper.query(new Playlist()
                .setUserId(userId)
                .setType(type));
        if (query == null || query.isEmpty()) {
            return null;
        }
        return query.get(0);
    }

    private String defaultNameForType(byte type, Long userId) {
        if (type == Constants.USER_FAVORITE) {
            return "收藏歌单" + userId;
        }
        if (type == Constants.CURRENT_PLAYLIST) {
            return "当前列表" + userId;
        }
        return "歌单" + userId;
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
            playlist.setName(defaultNameForType(playlist.getType(), playlist.getUserId()));
        }
        if (playlist.getName() == null || playlist.getName().isEmpty()) {
            return R.error("歌单名称不能为空");
        }
        if (playlist.getVisibility() == null) {
            // 默认设为私有
            playlist.setVisibility((byte) 1);
        }
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
            // TODO: 这里后续可以加上基于 visibility 的权限校验
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
            return R.success("清除成功");
        } catch (Exception e) {
            return R.error("清除失败" + e.getMessage());
        }
    }

    /**
     * 发现更多歌单：只返回普通歌单类型，排除其他用户的私人歌单。
     *
     * @param currentUserId 当前用户 ID，可以为 null
     */
    public R discoverPlaylists(Long currentUserId) {
        try {
            Playlist condition = new Playlist().setType(Constants.PLAYLIST);
            List<Playlist> all = playlistMapper.query(condition);
            if (all == null || all.isEmpty()) {
                return R.success("获取歌单列表成功", List.of());
            }

            List<Playlist> result = new ArrayList<>();
            for (Playlist playlist : all) {
                Byte visibility = playlist.getVisibility();
                Long ownerId = playlist.getUserId();

                boolean isPrivate = visibility != null && Byte.valueOf((byte) 1).equals(visibility);
                boolean isOwner = currentUserId != null && ownerId != null && ownerId.equals(currentUserId);

                // 别人的私人歌单不展示
                if (isPrivate && !isOwner) {
                    continue;
                }
                result.add(playlist);
            }
            return R.success("获取歌单列表成功", result);
        } catch (Exception e) {
            return R.error("获取歌单列表失败" + e.getMessage());
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


