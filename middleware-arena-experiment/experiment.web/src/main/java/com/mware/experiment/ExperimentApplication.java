package com.mware.experiment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * experiment 服务启动类。
 * 共享库 Bean（全局异常处理）由 base 共享库自动装配注入，无需 scanBasePackages。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.mware.experiment.biz.client")
public class ExperimentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExperimentApplication.class, args);
    }
}
