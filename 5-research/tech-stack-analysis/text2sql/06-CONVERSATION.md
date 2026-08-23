# 06 · 对话管理与上下文压缩：多轮 Text2SQL 交互

> 用户不会只问一次。"刚才那个查询改成按时间倒序"——多轮对话需要上下文。看项目如何管理对话历史、压缩上下文、支持连续 Text2SQL 交互。
>
> **对应项目：** `text2sql/text2sql-web`

---

## 一、基础概念

### 1.1 多轮 Text2SQL 的挑战

```
用户: "查询本月销售额前10的商品"
系统: [生成 SQL + 执行结果]

用户: "改成按时间倒序"  ← 需要知道"改成"什么
系统: [需要理解"上一个查询，排序改为时间倒序"]

用户: "只看手机品类"    ← 在上一个基础上加过滤条件
系统: [需要理解"在上一查询基础上加 WHERE category='手机'"]
```

**无上下文：** 每次调用都是独立对话，用户需要完整描述需求。
**有上下文：** 系统记住历史 NL→SQL→结果，新查询自动关联。

---

## 二、进阶机制

### 2.1 对话会话管理

```java
// 会话实体
@Data
public class ConversationSession {
    private String sessionId;
    private String userId;
    private LocalDateTime createTime;
    private LocalDateTime lastAccessTime;
    private String businessDomain;
    private int turnCount;
    private boolean active;
}

// 对话轮次
@Data
public class ConversationTurn {
    private Long id;
    private String sessionId;
    private String naturalLanguage;  // 用户自然语言
    private String generatedSQL;     // 生成的 SQL
    private String executionResult;  // 执行结果
    private LocalDateTime createTime;
    private boolean successful;
}

// 会话仓库
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {}
public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, Long> {
    List<ConversationTurn> findBySessionIdOrderByCreateTimeDesc(String sessionId);
}
```

### 2.2 上下文压缩

```java
@Service
@RequiredArgsConstructor
public class CompressedContext {

    public String buildContext(List<ConversationTurn> recentTurns) {
        // 只保留最近 N 轮对话
        List<ConversationTurn> context = recentTurns.size() > MAX_TURNS
            ? recentTurns.subList(0, MAX_TURNS)
            : recentTurns;

        StringBuilder sb = new StringBuilder();
        sb.append("历史对话:\n");
        for (ConversationTurn turn : context) {
            sb.append("Q: ").append(turn.getNaturalLanguage()).append("\n");
            sb.append("SQL: ").append(turn.getGeneratedSQL()).append("\n");
        }
        return sb.toString();
    }
}
```

### 2.3 错误修复上下文

```java
@Service
@RequiredArgsConstructor
public class ErrorFixService {
    private final LLMClient llmClient;

    public ErrorFixResult fixWithContext(String originalQuery, String sql, String errorMsg) {
        String prompt = """
            原始查询: %s
            生成的SQL: %s
            执行错误: %s
            请修正SQL。
            """.formatted(originalQuery, sql, errorMsg);

        String fixedSql = llmClient.generate("You are an expert SQL developer.", prompt);
        return new ErrorFixResult(true, fixedSql);
    }
}
```

---

## 三、面试要点

### Q1: 多轮对话的上下文怎么管理？会不会太长？

**回答思路：** 项目使用**滑动窗口**——只保留最近 N 轮 (如 5 轮) 对话。超过限制时丢弃最早的轮次。同时压缩每轮对话的字段——只保留"自然语言 + SQL"对，不保留完整执行结果。上下文长度可控，不会超出 LLM 的上下文窗口。

### Q2: 对话管理的设计模式叫什么？

**回答思路：** 类似 LangGraph 的 Checkpointer + thread_id 模式。每个 sessionId 对应一个"对话线程"，相关操作都在这个线程内。与 mall-ai-search 中的 InMemorySaver + thread_id 设计思路一致，区别在于这里用数据库持久化（ConversationTurnRepository）而非内存。

---

> **下一篇：** [07-ARCHITECTURE.md —— 架构复盘与面试题集：三个项目横向对比](./07-ARCHITECTURE.md)
>
> 全链路复盘，三个项目的技术栈横向对比，以及 Java + AI 融合面试题。