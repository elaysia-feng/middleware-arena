package com.mware.runner.biz.config;

import java.util.List;
import java.util.Map;

/**
 * 实验类型 → 容器拓扑描述。对应 experiment-side 模板的 {@code middlewareType}
 * （redis / rabbitmq / elasticsearch / seata）。
 * <p>
 * {@code run()} 按类型只启动本实验需要的组件（对齐"实验环境按任务临时启动"）：
 *
 * <pre>
 *   REDIS        : mysql + redis          + order-SUT
 *   RABBITMQ     : mysql + rabbitmq       + order-SUT
 *   ELASTICSEARCH: elasticsearch          + search-SUT
 *   SEATA        : mysql + seata          + order/storage/account 三 SUT
 * </pre>
 *
 * 镜像不在本类硬编码：{@code middlewareImages} 的 value 是 {@link RunnerProperties.Images}
 * 的字段名，由配置决定实际 tag（镜像常驻磁盘、按任务临时起容器）。
 */
public enum ExperimentType {

    REDIS("order",
            List.of(new SutSpec("order", 0.5, 512)),
            Map.of(
                    "mysql", new ContainerSpec("mysql", 0.5, 512),
                    "redis", new ContainerSpec("redis", 0.25, 256)),
            "/order/1", "GET"),
    RABBITMQ("order",
            List.of(new SutSpec("order", 0.75, 768)),
            Map.of(
                    "mysql", new ContainerSpec("mysql", 0.5, 512),
                    "rabbitmq", new ContainerSpec("rabbitmq", 0.75, 768)),
            "/order/create", "POST"),
    ELASTICSEARCH("search",
            List.of(new SutSpec("search", 0.75, 768)),
            Map.of("elasticsearch", new ContainerSpec("elasticsearch", 1.5, 2048)),
            "/search?keyword=benchmark", "GET"),
    // 三个 SUT 各 0.4 核：运行期合计 0.5(mysql)+1.0(seata)+1.2(SUT)+0.5(k6)=3.2，
    // 加 perTask 0.25 = 3.45 ≤ 平台可调度上限 3.5 核，保证默认配置下 SEATA 实验可运行。
    SEATA("order",
            List.of(
                    new SutSpec("order", 0.4, 384),
                    new SutSpec("storage", 0.4, 384),
                    new SutSpec("account", 0.4, 384)),
            Map.of(
                    "mysql", new ContainerSpec("mysql", 0.5, 512),
                    "seata", new ContainerSpec("seata", 1.0, 1024)),
            "/order/create", "POST"),
    /** 未登记的中间件类型 → run() 判 UNKNOWN 走告警 / 不建环境 */
    UNKNOWN("sut", List.of(), Map.of(), "", "GET");

    /** SUT 容器内监听端口（不打到宿主机，实验网络内容器名直连） */
    public static final int SUT_PORT = 8080;

    /** 单个 SUT 容器规格：角色名（容器名后缀）+ CPU / 内存硬限制。 */
    public record SutSpec(String role, double cpus, long memoryMb) {
    }

    /** k6 压测目标 SUT 角色（多 SUT 实验固定打主业务链路入口，如 SEATA 的 order） */
    private final String sutRole;
    /** 本实验全部 SUT 规格（多 SUT 实验 order+storage+account，单 SUT 实验只有一个元素） */
    private final List<SutSpec> sutSpecs;
    /** 中间件角色 → 镜像配置和容器资源需求。 */
    private final Map<String, ContainerSpec> middlewareImages;
    private final String k6Path;
    private final String httpMethod;

    ExperimentType(String sutRole, List<SutSpec> sutSpecs,
            Map<String, ContainerSpec> middlewareImages,
            String k6Path, String httpMethod) {
        this.sutRole = sutRole;
        this.sutSpecs = List.copyOf(sutSpecs);
        this.middlewareImages = Map.copyOf(middlewareImages);
        this.k6Path = k6Path;
        this.httpMethod = httpMethod;
    }

    /** k6 压测目标 SUT 角色名（多 SUT 实验的主链路入口，如 SEATA 的 order） */
    public String sutRole() {
        return sutRole;
    }

    /** 本实验全部 SUT 规格（SEATA = order + storage + account 三容器，其余单 SUT） */
    public List<SutSpec> sutSpecs() {
        return sutSpecs;
    }

    /** 中间件角色 → 镜像配置和资源需求，数量就是本实验需启动的中间件数量。 */
    public Map<String, ContainerSpec> middlewareImages() {
        return middlewareImages;
    }

    /**
     * 调度器需要预留的 CPU 峰值，单位为 0.01 核。
     * 构建和运行压测是顺序阶段，因此取两者最大值，再加每任务开销。
     */
    public long requiredCpuUnits(RunnerProperties.WorkloadResource workload) {
        long runtimeCpuUnits = middlewareImages.values().stream()
                .mapToLong(spec -> Math.round(spec.cpus() * 100))
                .sum()
                + sutSpecs.stream().mapToLong(spec -> Math.round(spec.cpus() * 100)).sum()
                + Math.round(workload.getK6Cpus() * 100);
        long buildCpuUnits = Math.round(workload.getBuildCpus() * 100);
        return Math.max(buildCpuUnits, runtimeCpuUnits)
                + Math.round(workload.getPerTaskCpus() * 100);
    }

    /** 构建阶段和运行压测阶段取内存峰值，再加每任务上下文、日志和指标开销。 */
    public long requiredMemoryMb(RunnerProperties.WorkloadResource workload) {
        long runtimeMemoryMb = middlewareImages.values().stream()
                .mapToLong(ContainerSpec::memoryMb)
                .sum()
                + sutSpecs.stream().mapToLong(SutSpec::memoryMb).sum()
                + workload.getK6MemoryMb();
        return Math.max(workload.getBuildMemoryMb(), runtimeMemoryMb)
                + workload.getPerTaskMemoryMb();
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

    public record ContainerSpec(String imageConfigKey, double cpus, long memoryMb) {
    }
}
