package com.vidego.common.config;

import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@Data
public class MinioConfig {

    @Value("${vidego.minio.endpoint}")
    private String endpoint;

    @Value("${vidego.minio.access-key}")
    private String accessKey;

    @Value("${vidego.minio.secret-key}")
    private String secretKey;

    @Value("${vidego.minio.bucket-video}")
    private String bucketVideo;

    @Value("${vidego.minio.bucket-avatar}")
    public String bucketAvatar;

    @Value("${vidego.minio.bucket-cover}")
    public String bucketCover;


    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        try {
            initBucket(client, bucketVideo);
            initBucket(client, bucketAvatar);
            initBucket(client, bucketCover);
        } catch (Exception e) {
            log.error("Failed to initialize MinIO buckets", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "MinIO initialization failed: " + e.getMessage());
        }

        log.info("MinIO client initialized: endpoint={}", endpoint);
        return client;
    }

    public String getBucketVideo() {
        return bucketVideo;
    }

    public String getBucketCover() {
        return bucketCover;
    }

    public String getBucketAvatar() {
        return bucketAvatar;
    }

    /**
     * 创建桶（如果不存在）并设置公开读权限。
     * 公开读允许通过 Nginx/Vite 代理直接访问视频和封面文件。
     */
    private void initBucket(MinioClient client, String bucketName) throws Exception {
        boolean exists = client.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Created MinIO bucket: {}", bucketName);
        }

        // 设置公开读权限（s3:GetObject）
        String policy = String.format(
                "{\"Version\":\"2012-10-17\",\"Statement\":[{" +
                "\"Effect\":\"Allow\"," +
                "\"Principal\":{\"AWS\":[\"*\"]}," +
                "\"Action\":[\"s3:GetObject\"]," +
                "\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}", bucketName);

        client.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                        .bucket(bucketName)
                        .config(policy)
                        .build());

        log.info("Set public-read policy for bucket: {}", bucketName);
    }
}
