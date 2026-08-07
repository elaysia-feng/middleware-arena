package com.mware.common.web;

import com.mware.common.jwt.InternalAuthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * web 共享库自动装配：注册全局异常处理器 + 用户上下文拦截器。
 * <p>
 * 通过 <code>META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports</code>
 * 被 Spring Boot 发现；服务只需依赖 base.web，无需任何扫描配置。
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class WebAutoConfiguration {

    /** 注册拦截器：读取网关透传的 X-User-Id / X-Username 填充 {@link UserContext}，可配 internal-auth 验签 */
    @Bean
    public WebMvcConfigurer authHeaderInterceptor(InternalAuthProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new AuthHeaderInterceptor(props)).addPathPatterns("/**");
            }
        };
    }
}
