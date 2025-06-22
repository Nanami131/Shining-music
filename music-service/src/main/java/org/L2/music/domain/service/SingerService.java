package org.L2.music.domain.service;


import org.L2.common.R;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.SingerMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SingerService {
    @Autowired
    private SingerMapper singerMapper;
    @Autowired
    private SongMapper songMapper;

    public R getSingerInfo(Long singerId) {
        try {
            Singer singer = singerMapper.selectById(singerId);
            if (singer == null) {
                return R.error("歌手不存在");
            }
            return R.success("获取歌手信息成功", singer);
        }catch (Exception e) {
            return R.error("获取歌手信息失败"+e.getMessage());
        }
    }

    public R createSinger(Singer singer) {
        if(singer.getName() == null || singer.getName().isBlank()){
            return R.error("歌手名称不能为空");
        }
        try {
            if(singer.getUserId() != null){
                if(singerMapper.query(new Singer().setUserId(singer.getUserId()))!=null){
                    return R.error("该用户已绑定歌手");
                }
            }
            singerMapper.insert(singer);
            return R.success("创建歌手成功");
        }catch (Exception e) {
            return R.error("创建歌手失败"+e.getMessage());
        }

    }

    public R updateSinger(Singer singer) {
        if(singer.getId() == null){
            return R.error("非法的请求");
        }
        // TODO: 感觉需要校验？
        try {
            singerMapper.update(singer);
            return R.success("更新歌手成功");
        }catch (Exception e) {
            return R.error("更新歌手失败"+e.getMessage());
        }
    }

    @Transactional
    public void deleteSinger(Long singerId) {
        singerMapper.deleteById(singerId);
        songMapper.deleteBySingerId(singerId);
        // TODO: 级联删除评论部分
        // 歌单存储在redis中不便于直接遍历删除
    }
}
