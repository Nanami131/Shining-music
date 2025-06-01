package org.L2.common.minio.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.L2.common.minio.MinioProperties;
import org.springframework.web.multipart.MultipartFile;

public class SimpleMinioService extends AbstractMinioService{

    public SimpleMinioService (MinioClient minioClient, MinioProperties properties) {
        super(minioClient, properties);
    }

    // 普通上传
    public boolean uploadFile(MultipartFile file) {
        try {
            ensureBucketExists();
            String objectName = file.getOriginalFilename();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return true;
        } catch (Exception e) {
            throw new RuntimeException("上传文件失败", e);
        }
    }
}