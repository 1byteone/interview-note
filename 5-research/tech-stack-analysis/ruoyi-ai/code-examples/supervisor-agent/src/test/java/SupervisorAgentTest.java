package com.ruoyi.ai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SupervisorAgentTest - Supervisor 多智能体架构单元测试
 *
 * 测试重点：
 * 1. AgentTool 接口的契约验证
 * 2. 多智能体协作模式的正确性
 * 3. 工具注册和调用逻辑
 *
 * 注意：本测试不启动 Spring 容器，不调用实际 LLM
 * 仅测试 Agent 的结构设计和接口契约
 */
class SupervisorAgentTest {

    // ==================== AgentTool 接口契约测试 ====================

    /**
     * 自定义测试工具实现 - 用于验证 AgentTool 接口契约
     */
    static class TestTool implements AgentTool {
        private final String name;
        private final String description;

        TestTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String execute(String input) {
            return "Tool [" + name + "] executed with input: " + input;
        }
    }

    @Test
    @DisplayName("AgentTool 实现应正确返回工具名称")
    void shouldReturnCorrectToolName() {
        // 创建一个测试工具
        AgentTool tool = new TestTool("web_search", "搜索互联网");
        // 验证工具名称正确返回
        assertEquals("web_search", tool.getName());
    }

    @Test
    @DisplayName("AgentTool 实现应正确返回工具描述")
    void shouldReturnCorrectToolDescription() {
        AgentTool tool = new TestTool("code_gen", "生成代码");
        assertEquals("生成代码", tool.getDescription());
    }

    @Test
    @DisplayName("AgentTool execute 应返回执行结果")
    void shouldReturnExecutionResult() {
        AgentTool tool = new TestTool("test_tool", "测试工具");
        // 执行工具并验证返回结果
        String result = tool.execute("测试输入");
        assertNotNull(result);
        assertTrue(result.contains("test_tool"));
        assertTrue(result.contains("测试输入"));
    }

    // ==================== 多智能体协作模式测试 ====================

    @Test
    @DisplayName("SkillsAgent 系统消息应包含代码相关职责")
    void skillsAgentShouldHaveCodeRelatedDuties() {
        // 验证 SkillsAgent 的 @SystemMessage 注解包含关键字
        // 通过反射获取注解内容
        try {
            // 获取 SkillsAgent 接口的 SystemMessage 注解
            var systemMessageAnnotation = SkillsAgent.class
                    .getMethod("chatWithCodeExpert", String.class)
                    .getAnnotation(SystemMessage.class);

            assertNotNull(systemMessageAnnotation, "SkillsAgent 应有 @SystemMessage 注解");
            String message = systemMessageAnnotation.value();
            // 验证系统消息包含关键职责描述
            assertTrue(message.contains("代码"), "系统消息应包含代码相关职责");
            assertTrue(message.contains("生成"), "系统消息应包含代码生成功能");
        } catch (NoSuchMethodException e) {
            fail("SkillsAgent 接口方法签名错误: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("WebSearchAgent 系统消息应包含搜索相关职责")
    void webSearchAgentShouldHaveSearchRelatedDuties() {
        try {
            var systemMessageAnnotation = WebSearchAgent.class
                    .getMethod("chatWithSearchAssistant", String.class)
                    .getAnnotation(SystemMessage.class);

            assertNotNull(systemMessageAnnotation, "WebSearchAgent 应有 @SystemMessage 注解");
            String message = systemMessageAnnotation.value();
            assertTrue(message.contains("搜索"), "系统消息应包含搜索相关职责");
            assertTrue(message.contains("信息"), "系统消息应包含信息获取功能");
        } catch (NoSuchMethodException e) {
            fail("WebSearchAgent 接口方法签名错误: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SupervisorAgent 系统消息应定义调度规则")
    void supervisorAgentShouldHaveDispatchRules() {
        try {
            var systemMessageAnnotation = SupervisorAgent.class
                    .getMethod("dispatchTask", String.class)
                    .getAnnotation(SystemMessage.class);

            assertNotNull(systemMessageAnnotation, "SupervisorAgent 应有 @SystemMessage 注解");
            String message = systemMessageAnnotation.value();
            // 验证系统消息包含子 Agent 名称和调度规则
            assertTrue(message.contains("SkillsAgent"), "系统消息应提及 SkillsAgent");
            assertTrue(message.contains("WebSearchAgent"), "系统消息应提及 WebSearchAgent");
            assertTrue(message.contains("调度"), "系统消息应定义调度规则");
        } catch (NoSuchMethodException e) {
            fail("SupervisorAgent 接口方法签名错误: " + e.getMessage());
        }
    }

    // ==================== 工具注册逻辑测试 ====================

    @Test
    @DisplayName("多个工具应能正确注册到列表中")
    void shouldRegisterMultipleTools() {
        // 模拟工具注册场景
        List<AgentTool> tools = List.of(
                new TestTool("web_search", "网络搜索"),
                new TestTool("code_gen", "代码生成"),
                new TestTool("file_read", "文件读取")
        );

        // 验证工具数量正确
        assertEquals(3, tools.size());
        // 验证每个工具名称唯一
        List<String> toolNames = tools.stream()
                .map(AgentTool::getName)
                .toList();
        assertEquals(3, toolNames.stream().distinct().count(),
                "工具名称应该唯一");
    }

    @Test
    @DisplayName("工具按名称查找应返回正确工具")
    void shouldFindToolByName() {
        List<AgentTool> tools = List.of(
                new TestTool("web_search", "网络搜索"),
                new TestTool("code_gen", "代码生成")
        );

        // 按名称查找工具
        AgentTool found = tools.stream()
                .filter(t -> t.getName().equals("web_search"))
                .findFirst()
                .orElse(null);

        assertNotNull(found, "应能找到 web_search 工具");
        assertEquals("网络搜索", found.getDescription());
    }
}
