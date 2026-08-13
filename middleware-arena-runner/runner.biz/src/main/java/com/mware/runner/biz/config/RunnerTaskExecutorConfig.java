package com.mware.runner.biz.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Runner 任务消费侧自定义线程池（{@code runnerTaskExecutor}）。
 * <p>
 * 用来消费 {@code runner.task.queue}，每条消息对应一次
 * build→run→benchmark→collect→cleanup 流水线（IO + 子进程混合型长任务）。
 * <p>
 * 三个核心参数（core / max / queue）的取值依赖：
 * <ul>
 *   <li>稳态到达率 λ：实验场景约 1 条 / 5min ≈ 0.0033 msg/s</li>
 *   <li>平均服务时间 E[S]：build(~60s) + run(~10s) + k6(~300s)
 *       + collect(~10s) + cleanup(~5s) ≈ 385s</li>
 *   <li>消息只携带 OSS 文件引用与运行参数，不传输大文件正文</li>
 * </ul>
 * 具体推导见每个 setter 行内的注释。
 *
 * @see RunnerRabbitConfig#rabbitListenerContainerFactory 监听容器工厂通过
 *      {@code @Qualifier("runnerTaskExecutor")} 注入本 Bean。
 */
@Configuration
@Slf4j
public class RunnerTaskExecutorConfig {

    /** Bean 名称，监听容器工厂按此名引用 */
    public static final String BEAN_NAME = "runnerTaskExecutor";

    @Bean(name = BEAN_NAME)
    public ThreadPoolTaskExecutor runnerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // —— corePoolSize = 4 ——
        // Little 定律：稳态所需并发 L ≥ λ × E[S] = 0.0033 × 385 ≈ 1.27，
        // 取整后 ×3 安全系数（重试 / k6 偶发抖动）≈ 4；
        // 同时留 1~2 个核给 Spring Web / Nacos 心跳 / 系统进程，
        // 4 线程远低于单机常用 4~16 核，几乎不会与宿主抢调度。
        executor.setCorePoolSize(4);

        // —— maxPoolSize = 16 ——
        // 突发场景 5 用户几乎同时点提交：λ_peak ≈ 0.167 msg/s，
        // 所需并发 L = 0.167 × 385 ≈ 64 —— 但 64 个线程同跑
        // Docker build / k6 子进程会耗尽宿主 fd / pid / 内存，
        // 工程收紧到 core × 4 = 16；超出走 queue → 满后 CallerRunsPolicy 反压。
        executor.setMaxPoolSize(16);

        // —— queueCapacity = 100 ——
        // 必须有界：RabbitMQ broker 已持久化消息，本地无界堆积无意义且会 OOM。
        // 取值：maxPoolSize × (峰值/稳态 × 安全系数) = 16 × 6 ≈ 100；
        // 内存预算：最坏 100 条 × 1MB = 100MB < 容器堆 1/4，留余量给 JVM / GC。
        executor.setQueueCapacity(100);

        // —— keepAliveSeconds = 60 ——
        // 配合 setAllowCoreThreadTimeOut(true)（下方），core 线程空闲 60s 后回收，
        // 长尾业务在低峰期把线程交还系统；突发来临时再临时扩容到 max。
        executor.setKeepAliveSeconds(60);

        // —— threadNamePrefix = "runner-task-" ——
        // 日志 / jstack grep runner-task- 直达消费线程栈，便于排查。
        executor.setThreadNamePrefix("runner-task-");

        // —— rejectedExecutionHandler = CallerRunsPolicy ——
        // 队列 + max 都满后：由调用方线程（这里是 RMQ 拉取线程）直接执行任务，
        // 慢 ACK 把反压传到 broker flow control；不丢消息、不进 DLQ 重投，
        // 比 AbortPolicy / DiscardPolicy "扔了重投" 更稳。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // —— allowCoreThreadTimeOut = true ——
        // 默认 ThreadPoolExecutor 只回收超出 maxPoolSize 的空闲线程；
        // 开启后 core 线程空闲超过 keepAliveSeconds 也会回收，
        // 低峰期把线程交回系统，避免长尾业务的常驻浪费。
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();
        log.info("{} 初始化完成: corePoolSize=4, maxPoolSize=16, queueCapacity=100, "
                + "queue 满则 CallerRunsPolicy 回灌反压", BEAN_NAME);
        return executor;
    }
}
