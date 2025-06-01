package org.L2.common.minio.service;

import io.minio.MinioClient;
import org.L2.common.minio.MinioProperties;

public class MultipartMinioService extends AbstractMinioService {

    public MultipartMinioService(MinioClient minioClient, MinioProperties properties) {
        super(minioClient, properties);
    }
    // TODO: 分片上传，暂时还没用到 到时候再写
    // 这个还比较麻烦，真正实现分片上传需要前端配合

    // 初始化分片上传
//    public boolean initiateMultipartUpload(String taskId) {
//        try {
//            ensureBucketExists();
//            String objectName = generateObjectName();
//            String uploadId = minioClient.initiateMultipartUpload(
//                    InitiateMultipartUploadArgs.builder()
//                            .bucket(properties.getBucketName())
//                            .object(objectName)
//                            .build()
//            ).result().uploadId();
//            // 假设 taskId、uploadId、objectName 存到 Redis，你自己处理
//            return true;
//        } catch (Exception e) {
//            throw new RuntimeException("初始化分片上传失败", e);
//        }
//    }
//
//    // 上传分片（带 MD5 校验）
//    public boolean uploadChunk(String taskId, InputStream chunk, int partNumber, long partSize, String md5) {
//        try {
//            // 从 Redis 获取 uploadId 和 objectName（你自己实现）
//            String uploadId = "your-redis-uploadId"; // 占位
//            String objectName = "your-redis-objectName"; // 占位
//
//            // MD5 校验
//            String calculatedMD5 = calculateMD5(chunk);
//            if (!calculatedMD5.equals(md5)) {
//                throw new RuntimeException("分片 MD5 校验失败: 分片 " + partNumber);
//            }
//
//            minioClient.uploadPart(
//                    UploadPartArgs.builder()
//                            .bucket(properties.getBucketName())
//                            .object(objectName)
//                            .uploadId(uploadId)
//                            .partNumber(partNumber)
//                            .stream(chunk, partSize, -1)
//                            .build()
//            );
//            // 更新 Redis 分片状态（你自己实现）
//            return true;
//        } catch (Exception e) {
//            throw new RuntimeException("上传分片失败: 分片 " + partNumber, e);
//        }
//    }
//
//    // 完成分片上传
//    public boolean completeMultipartUpload(String taskId) {
//        try {
//            // 从 Redis 获取 uploadId 和 objectName（你自己实现）
//            String uploadId = "your-redis-uploadId"; // 占位
//            String objectName = "your-redis-objectName"; // 占位
//
//            List<Part> parts = listUploadedParts(uploadId, objectName);
//            minioClient.completeMultipartUpload(
//                    CompleteMultipartUploadArgs.builder()
//                            .bucket(properties.getBucketName())
//                            .object(objectName)
//                            .uploadId(uploadId)
//                            .parts(parts.toArray(new Part[0]))
//                            .build()
//            );
//            // 删除 Redis 记录（你自己实现）
//            return true;
//        } catch (Exception e) {
//            throw new RuntimeException("完成分片上传失败", e);
//        }
//    }
//
//    // 查询已上传分片
//    public List<Integer> listUploadedParts(String taskId) {
//        try {
//            // 从 Redis 获取 uploadId 和 objectName（你自己实现）
//            String uploadId = "your-redis-uploadId"; // 占位
//            String objectName = "your-redis-objectName"; // 占位
//
//            ListPartsResponse response = minioClient.listParts(
//                    ListPartsArgs.builder()
//                            .bucket(properties.getBucketName())
//                            .object(objectName)
//                            .uploadId(uploadId)
//                            .build()
//            );
//            return response.result().partList().stream()
//                    .map(Part::partNumber)
//                    .collect(Collectors.toList());
//        } catch (Exception e) {
//            throw new RuntimeException("查询已上传分片失败", e);
//        }
//    }
}