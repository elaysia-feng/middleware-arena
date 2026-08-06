package com.mware.common.jwt;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * jwt 共享库自动装配：注册 {@link JwtProperties} 与 {@link JwtUtil}。
 * <p>
 * 服务依赖 base.jwt 即自动生效，无需扫描配置；
 * 通过 {@link ConditionalOnMissingBean} 允许服务自行覆盖 JwtUtil 实现。
 * TODO[双 token 登录]：接入刷新/黑名单后，此配置可扩展 refresh token Bean。
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtil jwtUtil(JwtProperties props) {
        return new JwtUtil(props);
    }
}
