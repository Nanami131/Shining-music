package org.L2.common.minio.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.L2.common.minio.MinioProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SimpleMinioService extends AbstractMinioService{

    public SimpleMinioService(MinioClient minioClient, MinioProperties properties) {
        super(minioClient, properties);
    }

    // 普通上传
    public String uploadFile(MultipartFile file,String name) {
        try {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(name)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return "上传成功";
        } catch (Exception e) {
            return "上传失败"+e.getMessage();
        }
    }
}
