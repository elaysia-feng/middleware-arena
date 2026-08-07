package com.mware.common.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关-下游内部通信鉴权配置：ma.internal-auth.*
 * <p>
 * secret 必须与网关一致（生产用环境变量注入，勿硬编码）；下游拦截器用它验签，
 * 验签失败拒绝请求，从而杜绝绕过网关直连服务端口伪造用户身份。
 */
@Data
@ConfigurationProperties(prefix = "ma.internal-auth")
public class InternalAuthProperties {

    /** 共享密钥，生产环境务必通过环境变量覆盖 */
    private String secret;

    /** 允许的时间戳偏移（毫秒），防止重放攻击；默认 5 分钟 */
    private long replayWindowMs = 5 * 60 * 1000L;
}
