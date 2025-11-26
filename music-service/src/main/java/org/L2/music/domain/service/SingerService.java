package org.L2.music.domain.service;


import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.music.domain.model.Singer;
import org.L2.music.infrastructure.SingerMapper;
import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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

    /**
     * 获取歌手信息
     */
    public R getSingerInfo(Long singerId) {
        try {
            Singer singer = singerMapper.selectById(singerId);
            if (singer == null) {
                return R.error("歌手不存在");
            }
            return R.success("获取歌手信息成功", singer);
        } catch (Exception e) {
            return R.error("获取歌手信息失败" + e.getMessage());
        }
    }

    /**
     * 创建歌手
     */
    @AutoFill(OperationType.INSERT)
    public R createSinger(Singer singer) {
        if (singer.getName() == null || singer.getName().isBlank()) {
            return R.error("歌手名称不能为空");
        }
        try {
            if (singer.getUserId() != null) {
                List<Singer> existing = singerMapper.query(new Singer().setUserId(singer.getUserId()));
                if (existing != null && !existing.isEmpty()) {
                    return R.error("该用户已绑定歌手");
                }
            }
            singerMapper.insert(singer);
            return R.success("创建歌手成功");
        } catch (Exception e) {
            return R.error("创建歌手失败" + e.getMessage());
        }
    }

    /**
     * 更新歌手资料（不包含头像）
     */
    @AutoFill(OperationType.UPDATE)
    public R updateSinger(Singer singer) {
        if (singer.getId() == null) {
            return R.error("非法请求");
        }
        // 不希望因为图片失败影响资料更新，这里不处理头像
        singer.setAvatarUrl(null);
        // TODO: 需要根据业务补充更严格的校验
        try {
            singerMapper.update(singer);
            return R.success("更新歌手成功");
        } catch (Exception e) {
            return R.error("更新歌手失败" + e.getMessage());
        }
    }

    /**
     * 删除歌手（级联删除其歌曲）
     */
    @Transactional
    public void deleteSinger(Long singerId) {
        singerMapper.deleteById(singerId);
        songMapper.deleteBySingerId(singerId);
        // TODO: 这里还可以补充删除评论、社区动态等关联数据
        // 歌单关系存储在 redis 中，不需要物理级联删除
    }

    /**
     * 更新歌手头像
     */
    public R updateSingerAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName = FileNameGenerateService.defineNamePath(originalFilename, "/singer/avator/", id, 5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        Singer singer = new Singer()
                .setId(id)
                .setUpdatedAt(LocalDateTime.now())
                .setAvatarUrl(avatarUrl);
        String result = simpleMinioService.uploadFile(file, fileName);
        if (!"上传成功".equals(result)) {
            return R.error(result);
        }
        try {
            singerMapper.update(singer);
        } catch (Exception e) {
            return R.error("数据库更新失败" + e.getMessage());
        }

        return R.success("头像修改成功", avatarUrl);
    }

    public List<Singer> listSingers() {
        return singerMapper.query(new Singer());
    }
}

