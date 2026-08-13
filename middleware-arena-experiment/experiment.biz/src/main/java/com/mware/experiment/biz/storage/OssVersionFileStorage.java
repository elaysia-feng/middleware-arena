package com.mware.experiment.biz.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** 实验版本文件的 OSS 存储。 */
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

    /** 上传压缩后的文件快照，返回数据库需要保存的元数据。 */
    public StoredFile upload(Long templateId, String filesJson) {
        // 1. 限制原始正文大小，避免普通用户上传过大的版本快照。
        byte[] sourceBytes = filesJson.getBytes(StandardCharsets.UTF_8);
        if (sourceBytes.length > maxFilesBytes) {
            throw new IllegalArgumentException("版本文件不能超过 " + maxFilesBytes + " 字节");
        }

        // 2. 使用 GZIP 压缩并计算实际 OSS 对象的 SHA-256。
        byte[] objectBytes = gzip(sourceBytes);
        String sha256 = sha256(objectBytes);
        String objectKey = "experiments/" + templateId + "/versions/" + UUID.randomUUID() + ".json.gz";

        // 3. 上传私有对象；数据库和 MQ 只保存对象引用，不保存正文。
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");
        metadata.setContentEncoding("gzip");
        metadata.setContentLength(objectBytes.length);
        try {
            ossClient.putObject(bucket, objectKey, new ByteArrayInputStream(objectBytes), metadata);
            return new StoredFile(objectKey, sha256, (long) objectBytes.length);
        } catch (OSSException | ClientException e) {
            throw new IllegalStateException("上传版本文件到 OSS 失败，objectKey=" + objectKey, e);
        }
    }

    /** 下载并校验版本文件；filesJson 不为空时表示历史数据，直接兼容读取。 */
    public String download(String filesJson, String objectKey, String expectedSha256, Long expectedSize) {
        if (filesJson != null && !filesJson.isBlank()) {
            return filesJson;
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("版本文件缺少 OSS 对象 Key");
        }

        // 1. 下载压缩对象，并限制读取大小。
        byte[] objectBytes;
        try {
            OSSObject object = ossClient.getObject(bucket, objectKey);
            try (var input = object.getObjectContent()) {
                objectBytes = input.readNBytes(Math.toIntExact(maxFilesBytes + 1025));
            }
        } catch (OSSException | ClientException | IOException e) {
            throw new IllegalStateException("从 OSS 下载版本文件失败，objectKey=" + objectKey, e);
        }

        // 2. 校验对象大小和 SHA-256，防止下载内容损坏或引用错误。
        if (expectedSize != null && expectedSize != objectBytes.length) {
            throw new IllegalStateException("OSS 版本文件大小校验失败，objectKey=" + objectKey);
        }
        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(sha256(objectBytes))) {
            throw new IllegalStateException("OSS 版本文件 SHA-256 校验失败，objectKey=" + objectKey);
        }

        // 3. 解压并再次限制原始正文大小。
        return new String(gunzip(objectBytes), StandardCharsets.UTF_8);
    }

    /** 数据库写入失败时删除刚上传的孤立对象。 */
    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            ossClient.deleteObject(bucket, objectKey);
        } catch (RuntimeException ignored) {
            // 清理失败不能覆盖原始数据库异常，OSS 生命周期规则可继续兜底清理。
        }
    }

    private byte[] gzip(byte[] sourceBytes) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(sourceBytes);
            gzip.finish();
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("压缩版本文件失败", e);
        }
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

    public record StoredFile(String objectKey, String sha256, long size) {
    }
}
