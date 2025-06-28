package org.L2.music.domain.service;

import org.L2.common.R;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.music.application.dto.SongBaseDTO;
import org.L2.music.domain.model.Playlist;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.PlaylistMapper;
import org.L2.music.infrastructure.SingerMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.L2.music.constant.Constants;
import java.util.List;

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

    public R getSongInfo(Long songId) {
        //TODO: 后续把热歌存在缓存里
        try {
            Song song = songMapper.selectById(songId);
            if(song==null){
                return R.error("歌曲不存在");
            }
            return R.success("获取歌曲详情成功",song);
        } catch (Exception e) {
            return R.error("获取歌曲详情失败"+e.getMessage());
        }
    }

    public R getSingerSongs(Long singerId) {
        try {
            List<Song> songs = songMapper.query(new Song().setArtistId(singerId));
            return R.success("获取歌手歌曲成功",songs);
        }catch (Exception e){
            return R.error("获取歌手歌曲失败"+e.getMessage());
        }
    }

    public R getPlaylistSongs(Long playlistId) {
        try {
            List<Song> songs = songMapper.query(new Song().setArtistId(playlistId));
            return R.success("获取歌单歌曲成功",songs);
        }catch (Exception e){
            return R.error("获取歌单歌曲失败"+e.getMessage());
        }
    }

    public R uploadSong(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName= FileNameGenerateService.defineNamePath(originalFilename,"/song/",id,5);
        String fileUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Song song = new Song().setId(id).setFileUrl(fileUrl);
        String s = simpleMinioService.uploadFile(file,fileName);
        if(!s.equals("上传成功")){
            return R.error(s);
        }
        try {
            songMapper.update(song);
        } catch (Exception e) {
            return R.error("数据库操作失败："+e.getMessage());
        }

        return R.success("歌曲上传成功",fileUrl);
    }

    public R uploadSongAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName= FileNameGenerateService.defineNamePath(originalFilename,"/song/cover/",id,5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Song song = new Song().setId(id).setCoverUrl(avatarUrl);
        String s = simpleMinioService.uploadFile(file,fileName);
        if(!s.equals("上传成功")){
            return R.error(s);
        }
        try {
            songMapper.update(song);
        } catch (Exception e) {
            return R.error("数据库操作失败："+e.getMessage());
        }

        return R.success("头像修改成功",avatarUrl);
    }

    public R createSong(Song song) {
        if(song.getTitle() == null || song.getTitle().isBlank()){
            return R.error("歌曲名称不能为空");
        }
        try {
           if(song.getArtistId()!= null){
               Singer singer =singerMapper.selectById(song.getArtistId());
               if(singer == null){
                   return R.error("对应歌手不存在");
               }
           }
           if(song.getAlbumId() != null){
               Playlist playlist = playlistMapper.selectById(song.getAlbumId());
               if(playlist == null||playlist.getType() !=Constants.ALBUM){
                   return R.error("对应专辑不存在");
               }
           }
           songMapper.insert(song.setStatus((byte)1));
           return R.success("创建歌曲成功",song);
        } catch (Exception e) {
            return R.error("创建歌曲失败"+e.getMessage());
        }
    }
}
