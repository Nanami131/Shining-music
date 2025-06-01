package org.L2.common.minio.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.L2.common.minio.MinioProperties;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.UUID;

public abstract class AbstractMinioService {
    protected final MinioClient minioClient;
    protected final MinioProperties properties;

    public AbstractMinioService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    // 确保存储桶存在
    protected void ensureBucketExists() {
        try {
            String bucketName = properties.getBucketName();
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("创建存储桶失败", e);
        }
    }

    // 计算 MD5
    protected String calculateMD5(InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算 MD5 失败", e);
        }
    }
}
