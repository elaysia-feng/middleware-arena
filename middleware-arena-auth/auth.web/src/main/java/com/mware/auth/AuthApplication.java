package com.mware.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * auth 服务启动类。
 * 共享库 Bean（全局异常处理 / JWT 工具）由 base 共享库自动装配注入，无需 scanBasePackages。
 */
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
