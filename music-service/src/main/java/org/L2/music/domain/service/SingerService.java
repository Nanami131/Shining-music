package org.L2.music.domain.service;


import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.music.domain.model.Singer;
import org.L2.music.domain.model.Song;
import org.L2.music.infrastructure.SingerMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.L2.common.minio.MinioProperties;

import java.util.List;

@Service
public class SingerService {
    @Autowired
    private SingerMapper singerMapper;
    @Autowired
    private SongMapper songMapper;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private SimpleMinioService simpleMinioService;

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

    @AutoFill(OperationType.INSERT)
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

    @AutoFill(OperationType.UPDATE)
    public R updateSinger(Singer singer) {
        if(singer.getId() == null){
            return R.error("非法的请求");
        }
        // 这里希望图片更新失败不影响其他部分，因此不做成事务，干脆弄成两个请求
        singer.setAvatarUrl(null);
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

    @AutoFill(OperationType.UPDATE)
    public R updateSingerAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName= FileNameGenerateService.defineNamePath(originalFilename,"/singer/avator/",id,5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Singer singer = new Singer().setId(id).setAvatarUrl(avatarUrl);
        String s = simpleMinioService.uploadFile(file,fileName);
        if(!s.equals("上传成功")){
            return R.error(s);
        }
        try {
            singerMapper.update(singer);
        } catch (Exception e) {
            return R.error("数据库操作失败："+e.getMessage());
        }

        return R.success("头像修改成功",avatarUrl);
    }
}
