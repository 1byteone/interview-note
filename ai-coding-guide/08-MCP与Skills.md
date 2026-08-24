> **[← 目录](README.md)** | 章节 08/12

# 第八部分 MCP 与 Skills：能力扩展层

## 8.1 MCP 协议：AI 工具的 USB-C

MCP（Model Context Protocol）是一个开放协议，标准化了 Agent 如何发现和调用外部工具：

```
LLM
 ↓
Agent
 ↓
MCP（标准化接口）
 ↓
External Tools
  ├── GitHub MCP（Issues, PRs, Actions）
  ├── PostgreSQL MCP（查询、Schema）
  ├── Redis MCP（缓存操作）
  ├── Docker MCP（容器管理）
  ├── Jira MCP（任务管理）
  └── 自定义 MCP Server
```

## 8.2 用 Spring Boot 构建 MCP Server

Spring AI 提供了 MCP Server Boot Starter，支持注解式开发：

```java
// 用 Spring Boot 构建自定义 MCP Server
@SpringBootApplication
public class OrderMcpServer {

    @Tool(description = "查询订单状态")
    public OrderDTO getOrderStatus(@Param("orderId") String orderId) {
        return orderService.getOrderStatus(orderId);
    }

    @Tool(description = "取消订单")
    public CancelResult cancelOrder(
        @Param("orderId") String orderId,
        @Param("reason") String reason
    ) {
        return orderService.cancelOrder(orderId, reason);
    }
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
</dependency>
```

构建完成后，任何支持 MCP 的 AI 工具（Claude Code、Cursor、Codex）都可以直接调用这些工具。

## 8.3 Skills 设计模式

### Skill 不是简单 Prompt

```
Skill
├── SKILL.md          # 指令文档
├── checklist.md      # 检查清单
├── examples/         # 示例
├── scripts/          # 辅助脚本
└── templates/        # 代码模板
```

### Java Code Review Skill 示例

```markdown
---
name: java-code-review
description: Java 代码审查，检查常见问题
metadata:
  triggers: code review, PR review, Java review
---

# Java Code Review Skill

## 检查清单

### 正确性
□ 无 NPE 风险（Optional 使用）
□ 无并发问题（线程安全）
□ 事务边界正确（@Transactional）
□ 异常处理完整

### 性能
□ 无 N+1 查询
□ 索引合理
□ Redis 缓存策略正确
□ 无内存泄漏

### 安全
□ 无 SQL 注入
□ 无 XSS 漏洞
□ 敏感信息未硬编码
□ 权限校验完整

### 可维护性
□ 命名清晰
□ 注释适当
□ 方法长度合理（≤30行）
□ 圈复杂度合理
```

## 8.4 Java 后端推荐的 20 个 Skills

```text
01 java-code-review         # Java 代码审查
02 springboot-development   # Spring Boot 开发
03 springcloud-development  # Spring Cloud 微服务
04 api-design               # API 设计
05 mysql-design             # MySQL 设计
06 mysql-performance        # MySQL 性能优化
07 redis-design             # Redis 设计
08 rocketmq-design          # RocketMQ 设计
09 elasticsearch-design     # ES 设计
10 distributed-system       # 分布式系统设计

11 concurrency-review       # 并发审查
12 security-review          # 安全审查
13 performance-review       # 性能审查
14 docker-deployment        # Docker 部署
15 nginx-deployment         # Nginx 部署

16 frontend-development     # 前端开发
17 ui-design                # UI 设计
18 ai-agent-development     # AI Agent 开发
19 rag-development          # RAG 开发
20 production-readiness     # 生产就绪审查
```

---

---

[← 上一章: 07-Context-Engineering](07-Context-Engineering.md) | [目录](README.md) | [下一章: 09-企业级案例与ROI(09-企业级案例与ROI.md)](09-企业级案例与ROI.md)
