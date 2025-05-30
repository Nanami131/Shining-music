package org.L2.music.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.music.application.dto.PlaylistCreateRequest;
import org.L2.music.constant.Constant;
import org.L2.music.domain.model.Playlist;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.PlaylistMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
            if(stringRedisTemplate.opsForSet().size("playlist:" + playlistId)>=Constant.MAX_PLAYLIST_SIZE){
                return R.error("歌单歌曲数量已达上限");
            }
           stringRedisTemplate.opsForSet().add("playlist:" + playlistId, String.valueOf(songId));
        }
        return R.success("歌单歌曲修改成功");
    }


    public R createPlaylist(Playlist playlist) {
        if(playlist.getType()==null||playlist.getVisibility()==null){
            return R.error("歌单信息不全");
        }
        if(playlist.getType()== Constant.USER_FAVORITE){
            List<Playlist> query = playlistMapper.query(new Playlist().setUserId(playlist.getUserId())
                    .setType(Constant.USER_FAVORITE));
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
}
