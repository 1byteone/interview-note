# 05 · SQL 验证器四层防护：语法 → 安全 → 语义 → 性能

> AI 生成的 SQL 可能语法错误、包含 DELETE/DROP 等危险操作、表名不存在、或者全表扫描。四层验证器为 SQL 执行提供安全护栏。
>
> **对应项目：** `text2sql/text2sql-validator`

---

## 一、基础概念

### 1.1 为什么需要 SQL 验证

LLM 生成的 SQL 可能存在的问题：

| 问题类型 | 风险 | 示例 |
|---------|------|------|
| **语法错误** | 执行直接报错 | `SELEC * FORM users` |
| **安全风险** | 数据破坏 | `DROP TABLE users` |
| **语义错误** | 结果不准确 | `JOIN` 条件错误 |
| **性能问题** | 数据库打爆 | `SELECT * FROM orders` (全表扫描) |

---

## 二、进阶机制

### 2.1 四层验证架构

```java
@Service
@RequiredArgsConstructor
public class SQLValidatorService {

    private final SyntaxValidator syntaxValidator;
    private final SecurityValidator securityValidator;
    private final SemanticValidator semanticValidator;
    private final PerformanceEstimator performanceEstimator;

    public ValidationResult validate(String sql) {
        // 第 1 层: 语法检查
        ValidationResult syntaxResult = syntaxValidator.validate(sql);
        if (!syntaxResult.isValid()) return syntaxResult;

        // 第 2 层: 安全检查
        ValidationResult securityResult = securityValidator.validate(sql);
        if (!securityResult.isValid()) return securityResult;

        // 第 3 层: 语义检查
        ValidationResult semanticResult = semanticValidator.validate(sql);
        if (!semanticResult.isValid()) return semanticResult;

        // 第 4 层: 性能预估
        ValidationResult perfResult = performanceEstimator.estimate(sql);
        if (!perfResult.isValid()) return perfResult;

        return ValidationResult.valid();
    }
}
```

### 2.2 各层验证器

```java
// 第 1 层: 语法验证器
public class SyntaxValidator {
    public ValidationResult validate(String sql) {
        try {
            // 使用 SQL 解析器解析为 AST
            // 检查语法树是否合法
            CCJSqlParserUtil.parse(sql);
            return ValidationResult.valid();
        } catch (JSQLParserException e) {
            return ValidationResult.invalid("SQL语法错误: " + e.getMessage());
        }
    }
}

// 第 2 层: 安全验证器
public class SecurityValidator {
    private static final List<String> DANGEROUS_KEYWORDS =
        List.of("DROP", "TRUNCATE", "DELETE", "ALTER", "UPDATE");

    public ValidationResult validate(String sql) {
        String upper = sql.toUpperCase();
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upper.contains(keyword)) {
                return ValidationResult.invalid("包含危险操作: " + keyword);
            }
        }
        // 只允许 SELECT
        if (!upper.trim().startsWith("SELECT")) {
            return ValidationResult.invalid("只允许SELECT查询");
        }
        return ValidationResult.valid();
    }
}

// 第 3 层: 语义验证器
public class SemanticValidator {
    public ValidationResult validate(String sql) {
        // 检查表名是否存在
        // 检查字段名是否存在
        // 检查 JOIN 条件是否正确
        // 检查类型是否匹配
    }
}

// 第 4 层: 性能预估
public class PerformanceEstimator {
    public ValidationResult estimate(String sql) {
        // 检查是否有 WHERE 条件
        // 检查是否有 LIMIT
        // 检查 JOIN 数量
        // 预估扫描行数
    }
}
```

### 2.3 SQL 修正

```java
@Service
@RequiredArgsConstructor
public class SQLCorrectionService {
    private final SQLValidatorService sqlValidatorService;

    public CorrectionResult correct(String sql, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            ValidationResult result = sqlValidatorService.validate(sql);
            if (result.isValid()) {
                return CorrectionResult.success(sql);
            }
            // 根据错误类型，提示 LLM 修正
            sql = askLLMToFix(sql, result.getError());
        }
        return CorrectionResult.failed("超过最大修正次数");
    }
}
```

---

## 三、面试要点

### Q1: SQL 验证器的四层防护分别解决什么问题？

**回答思路：** 1) **语法层**——SQL 解析器检查语法是否正确，避免执行报错；2) **安全层**——拦截 DROP/TRUNCATE/DELETE 等危险操作，只允许 SELECT；3) **语义层**——检查表名、字段名是否存在，JOIN 条件是否正确；4) **性能层**——预估执行成本，防止全表扫描打爆数据库。四层从"能不能执行"→"能不能安全执行"→"执行结果对不对"→"执行会不会太慢"逐层递进。

### Q2: 如果验证失败，项目怎么处理？

**回答思路：** 调用 `SQLCorrectionService` 自动修正——将错误信息反馈给 LLM，让 LLM 重新生成 SQL，最多重试 3 次。如果仍然失败，返回错误信息给用户。这是"AI 生成 → 验证 → 修正 → 再验证"的闭环。

---

> **下一篇：** [06-CONVERSATION.md —— 对话管理与上下文压缩：多轮 Text2SQL 交互](./06-CONVERSATION.md)
>
> 用户不会只问一次。看对话管理如何保存历史、压缩上下文、支持多轮 Text2SQL 交互。