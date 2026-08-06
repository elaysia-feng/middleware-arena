package com.mware.runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * runner 服务启动类。
 * 共享库 Bean（全局异常处理）由 base 共享库自动装配注入，无需 scanBasePackages。
 */
@SpringBootApplication
public class RunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunnerApplication.class, args);
    }
}
