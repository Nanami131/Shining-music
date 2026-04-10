package org.L2.music.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.common.context.UserContext;
import org.L2.music.constant.Constants;
import org.L2.music.domain.model.Playlist;
import org.L2.music.infrastructure.PlaylistMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.redis.connection.DataType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private void migrateSetToZSetIfNeeded(String key) {
        DataType type = stringRedisTemplate.type(key);
        if (type == DataType.SET) {
            Set<String> members = stringRedisTemplate.opsForSet().members(key);
            stringRedisTemplate.delete(key);
            if (members != null && !members.isEmpty()) {
                double score = System.currentTimeMillis();
                for (String member : members) {
                    stringRedisTemplate.opsForZSet().add(key, member, score++);
                }
            }
        }
    }

    public R managePlaylistSong(Long playlistId, Long songId) throws Exception {
        return managePlaylistSong(playlistId, songId, null);
    }

    public R managePlaylistSong(Long playlistId, Long songId, String action) throws Exception {
        if (playlistId == null || songId == null) {
            return R.error("歌单或歌曲不能为空");
        }
        if (songMapper.selectById(songId) == null) {
            return R.error("歌曲不存在");
        }
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null) {
            return R.error("歌单不存在");
        }
        R ownerCheck = checkPlaylistOwnership(playlist);
        if (ownerCheck != null) {
            return ownerCheck;
        }
        String key = "playlist:" + playlistId;
        migrateSetToZSetIfNeeded(key);
        String songKey = String.valueOf(songId);
        Double score = stringRedisTemplate.opsForZSet().score(key, songKey);
        boolean exists = score != null;
        if ("add".equals(action)) {
            if (exists) return R.success("歌曲已在歌单中");
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            if (size != null && size >= Constants.MAX_PLAYLIST_SIZE) {
                return R.error("歌单歌曲数量已达上限");
            }
            stringRedisTemplate.opsForZSet().add(key, songKey, System.currentTimeMillis());
            return R.success("添加成功");
        } else if ("remove".equals(action)) {
            if (!exists) return R.success("歌曲不在歌单中");
            stringRedisTemplate.opsForZSet().remove(key, songKey);
            return R.success("移除成功");
        } else {
            if (exists) {
                stringRedisTemplate.opsForZSet().remove(key, songKey);
            } else {
                Long size = stringRedisTemplate.opsForZSet().zCard(key);
                if (size != null && size >= Constants.MAX_PLAYLIST_SIZE) {
                    return R.error("歌单歌曲数量已达上限");
                }
                stringRedisTemplate.opsForZSet().add(key, songKey, System.currentTimeMillis());
            }
            return R.success("歌单歌曲修改成功");
        }
    }

    public boolean isSongFavorite(Long userId, Long songId) {
        if (userId == null || songId == null) {
            return false;
        }
        Playlist favorite = findUserPlaylist(userId, Constants.USER_FAVORITE);
        if (favorite == null) {
            return false;
        }
        String key = "playlist:" + favorite.getId();
        migrateSetToZSetIfNeeded(key);
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(songId));
        return score != null;
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
        migrateSetToZSetIfNeeded(key);
        String songKey = String.valueOf(songId);
        Double score = stringRedisTemplate.opsForZSet().score(key, songKey);
        boolean favoriteNow;
        if (score != null) {
            stringRedisTemplate.opsForZSet().remove(key, songKey);
            favoriteNow = false;
        } else {
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            if (size != null && size >= Constants.MAX_PLAYLIST_SIZE) {
                return R.error("收藏歌单已满");
            }
            stringRedisTemplate.opsForZSet().add(key, songKey, System.currentTimeMillis());
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

    public List<Playlist> listAll() {
        return playlistMapper.query(new Playlist());
    }

    /**
     * 歌单页展示：返回官方歌单 + 当前用户自己的歌单 + 其他用户公开歌单。
     * 说明：
     * - 官方约定为 user_id = -1
     * - 仅展示普通歌单与专辑（Constants.PLAYLIST / Constants.ALBUM）
     */
    public List<Playlist> listOfficialAndMine(Long currentUserId) {
        List<Playlist> all = playlistMapper.query(new Playlist());
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<Playlist> result = new ArrayList<>();
        for (Playlist playlist : all) {
            if (!isDisplayType(playlist.getType())) {
                continue;
            }
            Long ownerId = playlist.getUserId();
            Byte visibility = playlist.getVisibility();
            boolean isOfficial = ownerId != null && ownerId == -1L;
            boolean isPublic = visibility != null && visibility == 0;
            // 私密歌单不参与任何展示（官方歌单除外）
            if (isOfficial || isPublic) {
                result.add(playlist);
            }
        }
        return result;
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
            if (playlist == null) {
                return R.error("歌单不存在");
            }
            boolean isPrivate = playlist.getVisibility() != null && playlist.getVisibility() == 1;
            if (isPrivate) {
                Long currentUserId = UserContext.getUserId();
                boolean isOwner = currentUserId != null && currentUserId.equals(playlist.getUserId());
                if (!isOwner) {
                    return R.error("该歌单为私密歌单，无权访问");
                }
            }
            return R.success("获取歌单信息成功", playlist);
        } catch (Exception e) {
            return R.error("获取歌单信息失败" + e.getMessage());
        }
    }

    public R updatePlaylist(Playlist playlist, Long operatorUserId) {
        if (playlist == null || playlist.getId() == null) {
            return R.error("歌单ID不能为空");
        }
        if (operatorUserId == null) {
            return R.error("用户不能为空");
        }
        Playlist dbPlaylist = playlistMapper.selectById(playlist.getId());
        if (dbPlaylist == null) {
            return R.error("歌单不存在");
        }
        if (dbPlaylist.getUserId() == null || !dbPlaylist.getUserId().equals(operatorUserId)) {
            return R.error("无权修改该歌单");
        }
        if (playlist.getName() != null) {
            String trimmedName = playlist.getName().trim();
            if (trimmedName.isEmpty()) {
                return R.error("歌单名称不能为空");
            }
            playlist.setName(trimmedName);
        }
        playlist.setUpdatedAt(LocalDateTime.now());
        try {
            int rows = playlistMapper.update(playlist);
            if (rows == 0) {
                return R.error("歌单更新失败，可能已被删除");
            }
            return R.success("歌单更新成功");
        } catch (Exception e) {
            return R.error("歌单更新失败" + e.getMessage());
        }
    }

    public R uploadPlaylistAvatar(Long id, MultipartFile file) {
        if (playlistMapper.selectById(id) == null) {
            return R.error("歌单不存在");
        }
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
            int rows = playlistMapper.update(playlist);
            if (rows == 0) {
                try { simpleMinioService.deleteFile(fileName); } catch (Exception ignored) {}
                return R.error("数据库更新0行，歌单可能已被删除");
            }
        } catch (Exception e) {
            try { simpleMinioService.deleteFile(fileName); } catch (Exception ignored) {}
            return R.error("数据库更新失败" + e.getMessage());
        }

        return R.success("封面修改成功", avatarUrl);
    }

    public R clearUserCurrentPlaylist(Long userId) {
        try {
            Playlist playlist = ensureCurrentPlaylist(userId);
            if (playlist == null || playlist.getId() == null) {
                return R.error("初始化当前列表失败");
            }
            stringRedisTemplate.delete("playlist:" + playlist.getId());
            return R.success("清除成功");
        } catch (Exception e) {
            return R.error("清除失败" + e.getMessage());
        }
    }

    public R clearPlaylistSongs(Long playlistId, Long userId) {
        try {
            Playlist playlist = playlistMapper.selectById(playlistId);
            if (playlist == null) {
                return R.error("歌单不存在");
            }
            if (!playlist.getUserId().equals(userId)) {
                return R.error("无权操作该歌单");
            }
            stringRedisTemplate.delete("playlist:" + playlistId);
            return R.success("歌曲已全部清除");
        } catch (Exception e) {
            return R.error("清除失败：" + e.getMessage());
        }
    }

    /**
     * 发现更多歌单：只返回普通歌单与专辑，排除其他用户的私人歌单。
     *
     * @param currentUserId 当前用户 ID，可以为 null
     */
    public R discoverPlaylists(Long currentUserId) {
        try {
            List<Playlist> all = playlistMapper.query(new Playlist());
            if (all == null || all.isEmpty()) {
                return R.success("获取歌单列表成功", List.of());
            }

            List<Playlist> result = new ArrayList<>();
            for (Playlist playlist : all) {
                if (!isDisplayType(playlist.getType())) {
                    continue;
                }
                Byte visibility = playlist.getVisibility();
                Long ownerId = playlist.getUserId();
                boolean isOfficial = ownerId != null && ownerId == -1L;

                boolean isPrivate = visibility != null && Byte.valueOf((byte) 1).equals(visibility);
                boolean isOwner = currentUserId != null && currentUserId.equals(ownerId);
                if (isPrivate && !isOfficial && !isOwner) {
                    continue;
                }
                result.add(playlist);
            }
            return R.success("获取歌单列表成功", result);
        } catch (Exception e) {
            return R.error("获取歌单列表失败" + e.getMessage());
        }
    }

    private boolean isDisplayType(Byte type) {
        return type != null && (type == Constants.PLAYLIST || type == Constants.ALBUM);
    }

    /**
     * 校验当前登录用户是否拥有该歌单。
     * 返回 null 表示通过，返回 R 表示拒绝。
     */
    private R checkPlaylistOwnership(Playlist playlist) {
        Long currentUserId = UserContext.getUserId();
        if (playlist.getUserId() == null) {
            return null;
        }
        if (currentUserId == null) {
            return R.error("用户未登录");
        }
        if (!currentUserId.equals(playlist.getUserId())) {
            return R.error("无权操作他人歌单");
        }
        return null;
    }


    public R deletePlaylist(Long playlistId) {
        try {
            Playlist playlist = playlistMapper.selectById(playlistId);
            if (playlist == null) {
                return R.error("歌单不存在");
            }
            R ownerCheck = checkPlaylistOwnership(playlist);
            if (ownerCheck != null) {
                return ownerCheck;
            }
            playlistMapper.deleteById(playlistId);
            stringRedisTemplate.delete("playlist:" + playlistId);
            return R.success("删除歌单成功");
        } catch (Exception e) {
            return R.error("删除歌单失败" + e.getMessage());
        }
    }

    private static final String PLAYBACK_STATE_KEY_PREFIX = "user:playback_state:";

    public R getPlaybackState(Long userId) {
        try {
            String key = PLAYBACK_STATE_KEY_PREFIX + userId;
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) {
                return R.success("无播放状态", Map.of());
            }
            return R.success("获取播放状态成功", entries);
        } catch (Exception e) {
            return R.success("获取播放状态失败，返回默认值", Map.of());
        }
    }

    public R savePlaybackState(Long userId, Map<String, String> state) {
        try {
            String key = PLAYBACK_STATE_KEY_PREFIX + userId;
            if (state.containsKey("playMode")) {
                stringRedisTemplate.opsForHash().put(key, "playMode", state.get("playMode"));
            }
            if (state.containsKey("lastSongId")) {
                stringRedisTemplate.opsForHash().put(key, "lastSongId", state.get("lastSongId"));
            }
            if (state.containsKey("lastPosition")) {
                stringRedisTemplate.opsForHash().put(key, "lastPosition", state.get("lastPosition"));
            }
            if (state.containsKey("volume")) {
                stringRedisTemplate.opsForHash().put(key, "volume", state.get("volume"));
            }
            return R.success("保存播放状态成功");
        } catch (Exception e) {
            return R.error("保存播放状态失败: " + e.getMessage());
        }
    }
}
