package com.vidego.module.video;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vidego.common.config.MinioConfig;
import com.vidego.module.video.entity.Video;
import com.vidego.module.video.mapper.VideoMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverGenerationService {

    private final VideoMapper videoMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Transactional(rollbackFor = Exception.class)
    public boolean generateCoverAsync(Long videoId, String videoKey, Long userId) {
        String coverKey = generateCoverWithUrl(videoKey, userId);
        if (coverKey == null) {
            return false;
        }
        videoMapper.update(null,new LambdaUpdateWrapper<Video>()
                .set(Video::getCoverKey,coverKey)
                .eq(Video::getId, videoId));
        return true;
    }

    /**
     * 用 FFmpeg 通过 MinIO 预签名 URL 生成封面。
     * -ss 在 -i 之前（fast seek），MinIO 支持 HTTP Range 请求，无需下载整个文件。
     * 输出直接通过 image2pipe 读入内存，不落地磁盘。
     */
    public String generateCoverWithUrl(String videoKey, Long userId) {
        Process process = null;
        try {
            // 生成 10 分钟有效的预签名 GET URL（FFmpeg 处理通常只需几秒）
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketVideo())
                            .object(videoKey)
                            .expiry(10, TimeUnit.MINUTES)
                            .build());

            String coverKey = userId + "/" + UUID.randomUUID() + ".jpg";

            // -ss 在 -i 之前 → fast seek（MinIO 支持 HTTP Range 请求）
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-ss", "00:00:01",
                    "-i", presignedUrl,
                    "-vframes", "1",
                    "-vf", "scale=320:-1",
                    "-f", "image2pipe",
                    "-"
            );
            pb.redirectErrorStream(true); // stderr → stdout，统一处理
            process = pb.start();

            // 读 FFmpeg stdout（JPEG 数据 + 可能的日志）
            ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
            try (InputStream ffmpegOut = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = ffmpegOut.read(buf)) != -1) {
                    jpegBuf.write(buf, 0, n);
                }
            }

            int exitCode = process.waitFor(30, TimeUnit.SECONDS) ? process.exitValue() : -1;
            if (exitCode != 0) {
                process.destroyForcibly();
                // FFmpeg 正常退出但 exit code 非零，或超时
                String logMsg = exitCode == -1 ? "timed out" : ("exited with code " + exitCode);
                log.warn("FFmpeg {} for videoKey={}", logMsg, videoKey);
                return null;
            }

            // 从 stdout 提取 JPEG（跳过 FFmpeg 日志混杂问题，取末尾有效 JPEG 数据）
            if (jpegBuf.size() > 0) {
                byte[] jpegData = extractJpegFromBuffer(jpegBuf.toByteArray());
                if (jpegData == null) {
                    log.warn("No valid JPEG found in FFmpeg output for videoKey={}", videoKey);
                    return null;
                }

                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioConfig.getBucketCover())
                                .object(coverKey)
                                .stream(new ByteArrayInputStream(jpegData), jpegData.length, -1)
                                .contentType("image/jpeg")
                                .build());

                log.info("Cover generated via presigned URL: videoKey={}, coverKey={}",
                        videoKey, coverKey);
                return coverKey;
            }

        } catch (Exception e) {
            log.error("Cover generation failed for videoKey={}", videoKey, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return null;
    }

    /**
     * 从 FFmpeg stdout 的混合输出中提取 JPEG 数据。
     * redirectErrorStream(true) 会把日志混入 stdout，JPEG 以 FFD8 开头、FFD9 结尾。
     * 取最后一个 FFD8...FFD9 段（FFmpeg 先打印日志再输出图片）。
     */
    private byte[] extractJpegFromBuffer(byte[] data) {
        // 查找最后一个 JPEG SOI (0xFF 0xD8)
        int start = -1;
        for (int i = data.length - 2; i >= 0; i--) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8) {
                start = i;
                break;
            }
        }
        if (start < 0) return null;

        // 查找 JPEG EOI (0xFF 0xD9)
        int end = -1;
        for (int i = start + 2; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD9) {
                end = i + 1;
            }
        }
        if (end < 0) return null;

        byte[] jpeg = new byte[end - start + 1];
        System.arraycopy(data, start, jpeg, 0, jpeg.length);
        return jpeg;
    }
}
