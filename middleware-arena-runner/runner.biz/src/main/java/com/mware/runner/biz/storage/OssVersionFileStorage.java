package com.mware.runner.biz.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.mware.runner.dto.RunnerTaskMessage;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Runner 下载并校验实验版本文件。 */
@Component
public class OssVersionFileStorage {

    private final String bucket;
    private final long maxFilesBytes;
    private final OSS ossClient;

    public OssVersionFileStorage(
            @Value("${ma.oss.endpoint}") String endpoint,
            @Value("${ma.oss.bucket}") String bucket,
            @Value("${ma.oss.access-key-id}") String accessKeyId,
            @Value("${ma.oss.access-key-secret}") String accessKeySecret,
            @Value("${ma.oss.max-files-bytes:10485760}") long maxFilesBytes) {
        if (accessKeyId == null || accessKeyId.isBlank()
                || accessKeySecret == null || accessKeySecret.isBlank()) {
            throw new IllegalStateException("缺少 OSS_ACCESS_KEY_ID 或 OSS_ACCESS_KEY_SECRET");
        }
        this.bucket = bucket;
        this.maxFilesBytes = maxFilesBytes;
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    public String download(RunnerTaskMessage message) {
        // 1. 兼容历史消息；新消息只携带 OSS 引用。
        if (message.getFilesJson() != null && !message.getFilesJson().isBlank()) {
            return message.getFilesJson();
        }
        String objectKey = message.getFilesObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("任务消息缺少 OSS 对象 Key，taskId=" + message.getTaskId());
        }

        // 2. 下载压缩对象，并限制读取大小。
        byte[] objectBytes;
        try {
            OSSObject object = ossClient.getObject(bucket, objectKey);
            try (var input = object.getObjectContent()) {
                objectBytes = input.readNBytes(Math.toIntExact(maxFilesBytes + 1025));
            }
        } catch (OSSException | ClientException | IOException e) {
            throw new IllegalStateException("从 OSS 下载版本文件失败，objectKey=" + objectKey, e);
        }

        // 3. 校验大小和 SHA-256 后解压，损坏内容不得进入编译目录。
        if (message.getFilesSize() != null && message.getFilesSize() != objectBytes.length) {
            throw new IllegalStateException("OSS 版本文件大小校验失败，objectKey=" + objectKey);
        }
        if (message.getFilesSha256() != null
                && !message.getFilesSha256().equalsIgnoreCase(sha256(objectBytes))) {
            throw new IllegalStateException("OSS 版本文件 SHA-256 校验失败，objectKey=" + objectKey);
        }
        return new String(gunzip(objectBytes), StandardCharsets.UTF_8);
    }

    private byte[] gunzip(byte[] objectBytes) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(objectBytes))) {
            byte[] sourceBytes = gzip.readNBytes(Math.toIntExact(maxFilesBytes + 1));
            if (sourceBytes.length > maxFilesBytes) {
                throw new IllegalStateException("解压后的版本文件超过大小限制");
            }
            return sourceBytes;
        } catch (IOException e) {
            throw new IllegalStateException("解压版本文件失败", e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    @PreDestroy
    public void close() {
        ossClient.shutdown();
    }
}
