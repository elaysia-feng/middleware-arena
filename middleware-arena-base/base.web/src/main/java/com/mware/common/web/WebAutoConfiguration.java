package com.mware.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * web 共享库自动装配：注册全局异常处理器。
 * <p>
 * 通过 <code>META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports</code>
 * 被 Spring Boot 发现；服务只需依赖 base.web，无需任何扫描配置。
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class WebAutoConfiguration {
}
