package com.mware.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * product 服务启动类。
 * 共享库 Bean（全局异常处理 / 统一返回体）由 base 共享库自动装配注入，无需 scanBasePackages。
 */
@SpringBootApplication
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
