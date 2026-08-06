package com.mware.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * notification 服务启动类。
 * 共享库 Bean（全局异常处理）由 base 共享库自动装配注入，无需 scanBasePackages。
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
