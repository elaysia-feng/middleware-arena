package com.mware.order.controller;

import com.mware.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO[Seata 分布式事务]：
 *   1. POST /order/create 创建订单 → Feign 调 storage-service 扣库存 → Feign 调 account-service 扣余额
 *   2. 使用 @GlobalTransactional 注解实现 Seata AT 模式
 *   3. Redis 缓存实验：订单详情缓存（Cache-Aside 模式）
 *   4. RabbitMQ 异步下单实验：下单请求写入 MQ 队列，异步消费处理
 */
@Tag(name = "订单")
@RestController
@RequestMapping("/order")
public class OrderController {

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
