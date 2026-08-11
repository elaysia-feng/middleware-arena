package com.mware.runner.biz.docker;

import com.mware.runner.biz.config.RunnerProperties;

import java.util.Map;

/**
 * 实验类型 → 容器拓扑描述。对应 experiment-side 模板的 {@code middlewareType}
 * （redis / rabbitmq / elasticsearch / seata）。
 * <p>
 * {@code run()} 按类型只启动本实验需要的组件（对齐"实验环境按任务临时启动"）：
 * <pre>
 *   REDIS        : mysql + redis          + product-SUT
 *   RABBITMQ     : mysql + rabbitmq       + order-SUT
 *   ELASTICSEARCH: elasticsearch          + search-SUT
 *   SEATA        : mysql + seata          + order/storage/account 三 SUT（TODO 拆多 SUT 角色）
 * </pre>
 * 镜像不在本类硬编码：{@code middlewareImages} 的 value 是 {@link RunnerProperties.Images} 的字段名，
 * 由配置决定实际 tag（镜像常驻磁盘、按任务临时起容器）。
 */
public enum ExperimentType {

    REDIS("product", Map.of("mysql", "mysql", "redis", "redis"), "/product/1", "GET"),
    RABBITMQ("order", Map.of("mysql", "mysql", "rabbitmq", "rabbitmq"), "/order/create", "POST"),
    ELASTICSEARCH("search", Map.of("elasticsearch", "elasticsearch"), "/search?keyword=benchmark", "GET"),
    SEATA("order", Map.of("mysql", "mysql", "seata", "seata"), "/order/create", "POST"),
    /** 未登记的中间件类型 → run() 判 UNKNOWN 走告警 / 不建环境 */
    UNKNOWN("sut", Map.of(), "", "GET");

    /** SUT 容器内监听端口（不打到宿主机，实验网络内容器名直连） */
    public static final int SUT_PORT = 8080;

    private final String sutRole;
    /** 中间件角色 → RunnerProperties.images 字段名 */
    private final Map<String, String> middlewareImages;
    private final String k6Path;
    private final String httpMethod;

    ExperimentType(String sutRole, Map<String, String> middlewareImages, String k6Path, String httpMethod) {
        this.sutRole = sutRole;
        this.middlewareImages = Map.copyOf(middlewareImages);
        this.k6Path = k6Path;
        this.httpMethod = httpMethod;
    }

    /** SUT 容器角色名（product / order / search） */
    public String sutRole() {
        return sutRole;
    }

    /** 中间件角色 → 配置字段名，数量 = 本实验需临时启动的组件数 */
    public Map<String, String> middlewareImages() {
        return middlewareImages;
    }

    /** k6 压测路径（拼到 SUT 基址后面） */
    public String k6Path() {
        return k6Path;
    }

    public String httpMethod() {
        return httpMethod;
    }

    /** 按 middlewareType 字符串解析，未知回退 UNKNOWN */
    public static ExperimentType from(String middlewareType) {
        if (middlewareType != null) {
            try {
                return ExperimentType.valueOf(middlewareType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 未登记的实验类型 → UNKNOWN
            }
        }
        return UNKNOWN;
    }

    /**
     * baseline 预构建镜像名：{prefix}{name}{suffix} → ma-redis-baseline:v1。
     * baseline 不现场编译，镜像提前构建常驻磁盘（见 SutBuilder）。
     */
    public String baselineImage(RunnerProperties.Images images) {
        return images.getBaselinePrefix() + name().toLowerCase() + images.getBaselineSuffix();
    }
}
