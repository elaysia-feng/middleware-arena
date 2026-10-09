package com.mware.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * account 服务启动类。
 * 共享库 Bean（全局异常处理 / 统一返回体）由 base 共享库自动装配注入，无需 scanBasePackages。
 */
@SpringBootApplication
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
