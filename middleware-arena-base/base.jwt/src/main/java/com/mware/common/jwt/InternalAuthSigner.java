package com.mware.common.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 网关与下游服务之间的内部 HMAC 签名 / 验签工具。
 * <p>
 * 背景：下游拦截器从 X-User-Id / X-Username Header 识别当前用户，若有人绕过网关
 * 直接访问服务端口，伪造这两个 Header 即可冒充任意用户。
 * <p>
 * 对策：网关对 {@code userId & username & timestamp} 用共享密钥做 HMAC-SHA256 签名，
 * 追加 X-Timestamp / X-Sign Header；下游用同一密钥验签，验签通过才信任身份。
 * 签名内容带上时间戳，下游可校验窗口防止重放。
 * <p>
 * 密钥（ma.internal-auth.secret）必须在网关与所有服务间保持一致，生产用环境变量注入。
 */
public final class InternalAuthSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private InternalAuthSigner() {
    }

    /** 计算签名（十六进制小写）。userId / username / timestamp 为 null 时按空串参与计算。 */
    public static String sign(String secret, String userId, String username, String timestamp) {
        String raw = rawPayload(userId, username, timestamp);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return toHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("internal auth HMAC sign failed", e);
        }
    }

    /** 恒定时间比较验签，防时序侧信道。sign 为空直接返回 false。 */
    public static boolean verify(String secret, String userId, String username, String timestamp, String sign) {
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        String expected = sign(secret, userId, username, timestamp);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8));
    }

    /** 待签名明文：userId & username & timestamp。注意 username 不应包含 '&'。 */
    private static String rawPayload(String userId, String username, String timestamp) {
        return (userId == null ? "" : userId) + "&"
                + (username == null ? "" : username) + "&"
                + (timestamp == null ? "" : timestamp);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
