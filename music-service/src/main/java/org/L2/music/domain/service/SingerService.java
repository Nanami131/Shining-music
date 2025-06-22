package org.L2.music.domain.service;


import org.L2.common.R;
import org.L2.music.domain.model.Singer;
import org.L2.music.infrastructure.SingerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SingerService {
    @Autowired
    private SingerMapper singerMapper;

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
}
