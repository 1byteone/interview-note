# 04 · Prompt 工程与 Schema 增强：M-Schema、Few-shot、SQL 生成

> 检索到相关表之后，如何让 LLM 生成正确的 SQL？Prompt 工程是桥梁——将 Schema 结构、Few-shot 示例、用户查询组合为高质量的 Prompt。
>
> **对应项目：** `text2sql/text2sql-ai` + `text2sql/text2sql-schema`

---

## 一、基础概念

### 1.1 M-Schema 是什么

M-Schema（Markdown Schema）是一种用 Markdown 格式描述数据库 Schema 的方式，比纯文本更结构化，让 LLM 更容易理解。

```markdown
## 表: orders
| 字段名 | 类型 | 约束 | 注释 |
|--------|------|------|------|
| id | BIGINT | PK | 订单ID |
| user_id | BIGINT | FK→users(id) | 用户ID |
| total_amount | DECIMAL(10,2) | NOT NULL | 订单总金额 |
| status | VARCHAR(20) | DEFAULT 'pending' | 订单状态 |
| created_at | TIMESTAMP | INDEX | 创建时间 |
```

---

## 二、进阶机制

### 2.1 PromptBuilder —— 构建高质量 Prompt

```java
@Component
public class PromptBuilder {

    public String buildSQLGenerationPrompt(NaturalLanguageQuery nlQuery,
                                           List<MSchema> schemas,
                                           List<SQLExample> examples) {
        StringBuilder prompt = new StringBuilder();

        // 1. 系统角色定义
        prompt.append("You are an expert SQL developer...");

        // 2. Schema 信息
        prompt.append(buildSchemaSection(schemas));

        // 3. Few-shot 示例
        if (examples != null && !examples.isEmpty()) {
            prompt.append(buildExamplesSection(examples));
        }

        // 4. 用户查询
        prompt.append("用户查询: ").append(nlQuery.getQuery());

        // 5. 输出格式约束
        prompt.append("只输出 SQL 语句，不要解释。");

        return prompt.toString();
    }
}
```

### 2.2 SchemaEnhancer —— Schema 增强

```java
@Service
public class SchemaEnhancer {
    public MSchema generateMSchema(String tableName) {
        // 从数据库元数据提取表结构
        // 提取字段名、类型、默认值、注释
        // 提取主键、外键、索引
        // 生成 M-Schema 格式
    }
}
```

---

## 三、面试要点

### Q1: Text2SQL 的 Prompt 工程和通用 Chat Prompt 有什么不同？

**回答思路：** Text2SQL Prompt 需要精确的 Schema 描述和严格的输出格式约束。Schema 部分必须准确（字段名、类型、关系），否则 LLM 生成的 SQL 执行就会报错。输出约束要求只输出 SQL 语句（不加解释），否则解析会失败。此外 Few-shot 示例对 Text2SQL 特别有效——给 2-3 个 NL→SQL 对，LLM 就能准确理解输出格式。

### Q2: Schema 增强要解决什么问题？

**回答思路：** 数据库中的表名和字段名可能是缩写（如 `usr` → `user`），注释可能是乱码或不完整。Schema 增强要做：补全字段注释、推断外键关系、标注字段的业务含义（如 `status in ('pending','paid','cancelled')`），让 LLM 能够理解字段的业务语义。

---

> **下一篇：** [05-SQL-VALIDATOR.md —— SQL 验证器四层防护：语法 → 安全 → 语义 → 性能](./05-SQL-VALIDATOR.md)
>
> AI 生成的 SQL 可能语法错误、包含危险操作、表名不存在。看四层验证器如何为 SQL 安全保驾护航。