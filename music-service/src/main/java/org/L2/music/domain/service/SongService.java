package org.L2.music.domain.service;

import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.music.constant.Constants;
import org.L2.music.domain.model.Playlist;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.PlaylistMapper;
import org.L2.music.infrastructure.SingerMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SongService {

    @Autowired
    private SongMapper songMapper;
    @Autowired
    private SingerMapper singerMapper;
    @Autowired
    private PlaylistMapper playlistMapper;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private SimpleMinioService simpleMinioService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public R getSongInfo(Long songId) {
        // TODO: 可考虑增加热度统计和缓存
        try {
            Song song = songMapper.selectById(songId);
            if (song == null) {
                return R.error("歌曲不存在");
            }
            return R.success("获取歌曲信息成功", song);
        } catch (Exception e) {
            return R.error("获取歌曲信息失败" + e.getMessage());
        }
    }

    public R getSingerSongs(Long singerId) {
        try {
            List<Song> songs = songMapper.query(new Song().setArtistId(singerId));
            return R.success("获取歌手歌曲成功", songs);
        } catch (Exception e) {
            return R.error("获取歌手歌曲失败" + e.getMessage());
        }
    }

    /**
     * 获取歌单内的歌曲列表
     * 歌单歌曲关系存储在 Redis Set: key = playlist:{playlistId}, value = songId 字符串
     */
    public R getPlaylistSongs(Long playlistId) {
        try {
            Set<String> songIdSet = stringRedisTemplate.opsForSet().members("playlist:" + playlistId);
            List<Song> songs = new ArrayList<>();
            if (songIdSet != null && !songIdSet.isEmpty()) {
                for (String idStr : songIdSet) {
                    try {
                        Long id = Long.valueOf(idStr);
                        Song song = songMapper.selectById(id);
                        if (song != null) {
                            songs.add(song);
                        }
                    } catch (NumberFormatException ignored) {
                        // 忽略非法 ID
                    }
                }
            }
            return R.success("获取歌单歌曲成功", songs);
        } catch (Exception e) {
            return R.error("获取歌单歌曲失败" + e.getMessage());
        }
    }

    @AutoFill(OperationType.UPDATE)
    public R uploadSong(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName = FileNameGenerateService.defineNamePath(originalFilename, "/song/", id, 5);
        String fileUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Song song = new Song().setId(id).setFileUrl(fileUrl);
        String result = simpleMinioService.uploadFile(file, fileName);
        if (!"上传成功".equals(result)) {
            return R.error(result);
        }
        try {
            songMapper.update(song);
        } catch (Exception e) {
            return R.error("数据库更新失败" + e.getMessage());
        }

        return R.success("歌曲上传成功", fileUrl);
    }

    public R uploadSongAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName = FileNameGenerateService.defineNamePath(originalFilename, "/song/cover/", id, 5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Song song = new Song()
                .setId(id)
                .setUpdatedAt(LocalDateTime.now())
                .setCoverUrl(avatarUrl);
        String result = simpleMinioService.uploadFile(file, fileName);
        if (!"上传成功".equals(result)) {
            return R.error(result);
        }
        try {
            songMapper.update(song);
        } catch (Exception e) {
            return R.error("数据库更新失败" + e.getMessage());
        }

        return R.success("封面修改成功", avatarUrl);
    }

    @AutoFill(OperationType.INSERT)
    public R createSong(Song song) {
        if (song.getTitle() == null || song.getTitle().isBlank()) {
            return R.error("歌曲名称不能为空");
        }
        try {
            if (song.getArtistId() != null) {
                Singer singer = singerMapper.selectById(song.getArtistId());
                if (singer == null) {
                    return R.error("对应歌手不存在");
                }
            }
            if (song.getAlbumId() != null) {
                Playlist playlist = playlistMapper.selectById(song.getAlbumId());
                if (playlist == null || playlist.getType() != Constants.ALBUM) {
                    return R.error("对应专辑不存在");
                }
            }
            songMapper.insert(song.setStatus((byte) 1));
            return R.success("创建歌曲成功", song);
        } catch (Exception e) {
            return R.error("创建歌曲失败" + e.getMessage());
        }
    }
}

