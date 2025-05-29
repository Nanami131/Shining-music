package org.L2.music.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.PlaylistMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
           stringRedisTemplate.opsForSet().add("playlist:" + playlistId, String.valueOf(songId));
        }
        return R.success("歌单歌曲修改成功");
    }


}
