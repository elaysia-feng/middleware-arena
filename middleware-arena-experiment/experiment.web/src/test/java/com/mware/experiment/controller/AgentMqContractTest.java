package com.mware.experiment.controller;

import com.mware.experiment.config.AgentRabbitConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Java / Python 两端 MQ 拓扑契约测试：防止 AgentRabbitConfig 常量与
 * Python 端 app/mq/constants.py 的字符串漂移。
 * <p>
 * Python 仓库与本仓库同级或位于父目录时自动比对；本地没有 Python 仓库时
 * 该用例跳过（assumeTrue），Java 常量自身仍会被断言守护。
 */
class AgentMqContractTest {

    /** Python 端拓扑常量文件的可能位置（从 experiment.web 模块工作目录起算） */
    private static final Path[] PYTHON_CONSTANTS_CANDIDATES = {
            Paths.get("..", "middleware-arena-agent", "app", "mq", "constants.py"),
            Paths.get("..", "..", "middleware-arena-agent", "app", "mq", "constants.py"),
    };

    @Test
    void javaTopologyConstantsMustStayStable() {
        // 这些字符串被 Python 端 hard-code，任何改动都是破坏性变更（需两端同时发版）
        assertEquals("agent.analysis.exchange", AgentRabbitConfig.EXCHANGE_ANALYSIS);
        assertEquals("agent.analysis.queue", AgentRabbitConfig.QUEUE_ANALYSIS);
        assertEquals("agent.analysis", AgentRabbitConfig.ROUTING_KEY_ANALYSIS);
        assertEquals("agent.analysis.status.exchange", AgentRabbitConfig.EXCHANGE_STATUS);
        assertEquals("agent.analysis.status.queue", AgentRabbitConfig.QUEUE_STATUS);
        assertEquals("agent.analysis.status", AgentRabbitConfig.ROUTING_KEY_STATUS);
        assertEquals("agent.analysis.dlx", AgentRabbitConfig.DLX_ANALYSIS);
        assertEquals("agent.analysis.dlq", AgentRabbitConfig.DLQ_ANALYSIS);
    }

    @Test
    void pythonConstantsMustMatchJavaTopology() throws Exception {
        Path pythonConstants = locatePythonConstants();
        assumeTrue(pythonConstants != null,
                "本地未找到 app/mq/constants.py，跳过跨仓库比对（CI 挂载两个仓库后会执行）");
        String python = Files.readString(pythonConstants);

        assertPythonContains(python, AgentRabbitConfig.EXCHANGE_ANALYSIS);
        assertPythonContains(python, AgentRabbitConfig.QUEUE_ANALYSIS);
        assertPythonContains(python, AgentRabbitConfig.ROUTING_KEY_ANALYSIS);
        assertPythonContains(python, AgentRabbitConfig.EXCHANGE_STATUS);
        assertPythonContains(python, AgentRabbitConfig.QUEUE_STATUS);
        assertPythonContains(python, AgentRabbitConfig.ROUTING_KEY_STATUS);
    }

    private Path locatePythonConstants() {
        for (Path candidate : PYTHON_CONSTANTS_CANDIDATES) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void assertPythonContains(String python, String expected) {
        assertTrue(python.contains(expected),
                "Python 端 constants.py 缺少拓扑字符串 [" + expected
                        + "]，两端 MQ 拓扑已漂移，请同步修改 app/mq/constants.py 与 AgentRabbitConfig");
    }
}
