package org.L2.music.domain.service;

import org.L2.common.R;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SongService {
    @Autowired
    private SongMapper songMapper;

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
}
