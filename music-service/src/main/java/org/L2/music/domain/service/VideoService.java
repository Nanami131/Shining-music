package org.L2.music.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.music.domain.model.Video;
import org.L2.music.infrastructure.SingerMapper;
import org.L2.music.infrastructure.VideoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class VideoService {

    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private SingerMapper singerMapper;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private SimpleMinioService simpleMinioService;

    public R uploadVideo(Long singerId, String title, MultipartFile file, String md5) {
        if (title == null || title.trim().isEmpty()) {
            return R.error("视频标题不能为空");
        }
        if (file == null || file.isEmpty()) {
            return R.error("视频文件不能为空");
        }
        if (md5 == null || md5.trim().isEmpty()) {
            return R.error("md5不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty() || !originalFilename.contains(".")) {
            return R.error("文件名非法");
        }

        if (singerId != null && singerMapper.selectById(singerId) == null) {
            return R.error("歌手不存在");
        }
        long identity = singerId == null ? 0L : singerId;
        String fileName = FileNameGenerateService.defineNamePath(originalFilename, "/video/file/", identity, 6);
        String fileUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        String uploadResult = simpleMinioService.uploadFile(file, fileName);
        if (!"上传成功".equals(uploadResult)) {
            return R.error(uploadResult);
        }

        Video video = new Video()
                .setSingerId(singerId)
                .setTitle(title.trim())
                .setFileUrl(fileUrl)
                .setMd5(md5)
                .setSizeBytes(file.getSize())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        try {
            videoMapper.insert(video);
            return R.success("视频上传成功", video);
        } catch (Exception e) {
            try {
                simpleMinioService.deleteFile(fileName);
            } catch (Exception cleanupEx) {
                log.error("Failed to cleanup MinIO file {} after DB insert failure", fileName, cleanupEx);
            }
            return R.error("视频保存失败" + e.getMessage());
        }
    }

    public R updateVideoMeta(Video video) {
        if (video == null || video.getId() == null) {
            return R.error("视频ID不能为空");
        }
        Video exists = videoMapper.selectById(video.getId());
        if (exists == null) {
            return R.error("视频不存在");
        }
        if (video.getTitle() != null && video.getTitle().trim().isEmpty()) {
            return R.error("视频标题不能为空");
        }
        if (video.getTitle() != null) {
            video.setTitle(video.getTitle().trim());
        }
        video.setUpdatedAt(LocalDateTime.now());
        try {
            videoMapper.update(video);
            return R.success("视频信息更新成功");
        } catch (Exception e) {
            return R.error("视频信息更新失败" + e.getMessage());
        }
    }

    public R getVideoInfo(Long id) {
        if (id == null) {
            return R.error("视频ID不能为空");
        }
        Video video = videoMapper.selectById(id);
        if (video == null) {
            return R.error("视频不存在");
        }
        return R.success("获取视频成功", video);
    }

    public R listVideos() {
        List<Video> list = videoMapper.query(new Video());
        return R.success("获取视频列表成功", list);
    }

    public R deleteVideo(Long id) {
        if (id == null) {
            return R.error("视频ID不能为空");
        }
        Video video = videoMapper.selectById(id);
        if (video == null) {
            return R.error("视频不存在");
        }
        try {
            if (video.getFileUrl() != null && !video.getFileUrl().isBlank()) {
                String bucketPrefix = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName();
                if (video.getFileUrl().startsWith(bucketPrefix)) {
                    String objectName = video.getFileUrl().substring(bucketPrefix.length());
                    simpleMinioService.deleteFile(objectName);
                }
            }
        } catch (Exception e) {
            log.warn("删除 MinIO 视频文件失败 videoId={}", id, e);
        }
        videoMapper.deleteById(id);
        return R.success("视频删除成功");
    }
}

