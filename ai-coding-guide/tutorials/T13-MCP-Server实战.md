# T13: MCP Server Spring Boot 实现

> **[← 教程目录](README.md) | 工具: Spring Boot + 任意 AI 工具 | 时长: ~30min**

---

## Goal

用 Spring Boot 构建一个**自定义 MCP Server**，让 Claude Code / Cursor / Codex 都能直接调用你的业务 API。

## 前置条件

```bash
# Java 21 + Maven 3.9+
java --version
mvn --version

# 创建项目
mvn archetype:generate \
  -DgroupId=com.example \
  -DartifactId=mcp-order-server \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

## Step 1: 添加 Spring AI MCP 依赖

```xml
<!-- pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Step 2: 实现 MCP Tool

```java
package com.example.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.Param;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderMcpTools {

    private final OrderService orderService;

    public OrderMcpTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @Tool(description = "根据订单ID查询订单详情，包含状态、金额、商品信息")
    public OrderDTO getOrderDetail(
        @Param(description = "订单ID") String orderId
    ) {
        return orderService.getOrderDetail(orderId);
    }

    @Tool(description = "查询用户的所有订单，支持按状态筛选")
    public List<OrderDTO> getUserOrders(
        @Param(description = "用户ID") String userId,
        @Param(description = "订单状态筛选: CREATED/PAID/SHIPPED/COMPLETED/CANCELLED，留空查全部") String status
    ) {
        return orderService.getUserOrders(userId, status);
    }

    @Tool(description = "取消未支付的订单，会释放库存和退还优惠券")
    public CancelResult cancelOrder(
        @Param(description = "订单ID") String orderId,
        @Param(description = "取消原因") String reason
    ) {
        return orderService.cancelOrder(orderId, reason);
    }

    @Tool(description = "查询订单状态变更历史")
    public List<OrderStatusLog> getOrderStatusHistory(
        @Param(description = "订单ID") String orderId
    ) {
        return orderService.getStatusHistory(orderId);
    }
}
```

## Step 3: 配置 MCP Server

```yaml
# application.yml
spring:
  ai:
    mcp:
      server:
        name: order-mcp-server
        version: 1.0.0
        type: SYNC
        # SSE 模式（HTTP 传输）
        sse-message-endpoint: /mcp/messages
```

## Step 4: 启动并验证

```bash
mvn spring-boot:run
# MCP Server 启动在 http://localhost:8080
```

在 Claude Code 中配置：

```json
// .claude/settings.json
{
  "mcpServers": {
    "order-service": {
      "command": "curl",
      "args": ["-s", "http://localhost:8080/mcp/sse"]
    }
  }
}
```

## Step 5: 在 Claude Code 中使用

```
帮我查一下用户 10086 的所有订单。

使用 order-service MCP 工具查询。
```

Claude Code 会：
1. 发现 order-service MCP Server
2. 调用 `getUserOrders(userId="10086", status="")`
3. 返回订单列表

## Step 6: 扩展——添加更复杂的 Tool

```java
@Tool(description = "分析订单数据，生成销售报表")
public SalesReport generateSalesReport(
    @Param(description = "开始日期，格式 yyyy-MM-dd") String startDate,
    @Param(description = "结束日期，格式 yyyy-MM-dd") String endDate,
    @Param(description = "维度: daily/weekly/monthly") String granularity
) {
    return orderService.generateSalesReport(startDate, endDate, granularity);
}

@Tool(description = "查找异常订单（超时未支付、重复扣款等）")
public List<AnomalyOrder> findAnomalyOrders(
    @Param(description = "异常类型: TIMEOUT/DOUBLE_PAY/STUCK") String type
) {
    return orderService.findAnomalyOrders(type);
}
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| MCP Server 启动失败 | 检查 Spring AI BOM 版本，确保 ≥ 1.0.0 |
| Claude Code 连不上 | 检查 SSE endpoint: `curl http://localhost:8080/mcp/sse` |
| Tool 没有被发现 | 确保 @Tool 注解在 Spring Bean 上（@RestController/@Service） |
| 想用 WebSocket 传输 | 配置 `spring.ai.mcp.server.type=ASYNC` |

## 延伸

- → [08-MCP 与 Skills](../08-MCP与Skills.md)
- → [T14: 五工具协作](T14-五工具协作全流程.md)
