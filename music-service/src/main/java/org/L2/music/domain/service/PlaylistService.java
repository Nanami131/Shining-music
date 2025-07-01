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
import org.L2.music.domain.model.Singer;
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
        if(playlistId==null || songId==null){
            return R.error("歌单或歌曲不存在");
        }
        if(songMapper.selectById(songId)==null){
            return R.error("歌曲不存在");
        }
        if(playlistMapper.selectById(playlistId)==null){
            return R.error("歌单不存在");
        }
        boolean flag= stringRedisTemplate.opsForSet().isMember("playlist:"+playlistId,songId);
        if(flag){
            stringRedisTemplate.opsForSet().remove("playlist:" + playlistId, songId);
        }
        else{
            if(stringRedisTemplate.opsForSet().size("playlist:" + playlistId)>= Constants.MAX_PLAYLIST_SIZE){
                return R.error("歌单歌曲数量已达上限");
            }
           stringRedisTemplate.opsForSet().add("playlist:" + playlistId, String.valueOf(songId));
        }
        return R.success("歌单歌曲修改成功");
    }


    @AutoFill(OperationType.INSERT)
    public R createPlaylist(Playlist playlist) {
        if(playlist.getType()==null||playlist.getVisibility()==null){
            return R.error("歌单信息不全");
        }
        if(playlist.getType()== Constants.USER_FAVORITE||playlist.getType()== Constants.CURRENT_PLAYLIST){
            List<Playlist> query = playlistMapper.query(new Playlist().setUserId(playlist.getUserId())
                    .setType(playlist.getType()));
            if(query!=null && !query.isEmpty()){
                return R.error("非法的请求！");
            }
            playlist.setName("收藏歌单"+playlist.getUserId());
        }
        if(playlist.getName()==null||playlist.getName().isEmpty()){
            return R.error("歌单名称不能为空");
        }
        try {
            playlistMapper.insert(playlist);
        } catch (Exception e) {
            return R.error("创建歌单失败"+e.getMessage());
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
        }catch (Exception e) {
            return R.error("获取歌单信息失败"+e.getMessage());
        }
    }

    public R uploadPlaylistAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName= FileNameGenerateService.defineNamePath(originalFilename,"/playlist/cover/",id,5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Playlist playlist = new Playlist().
                setId(id).
                setUpdatedAt(LocalDateTime.now()).
                setCoverUrl(avatarUrl);
        String s = simpleMinioService.uploadFile(file,fileName);
        if(!s.equals("上传成功")){
            return R.error(s);
        }
        try {
            playlistMapper.update(playlist);
        } catch (Exception e) {
            return R.error("数据库操作失败："+e.getMessage());
        }

        return R.success("头像修改成功",avatarUrl);
    }
}
