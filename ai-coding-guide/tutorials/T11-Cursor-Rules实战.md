# T11: Cursor .mdc Rules 全流程

> **[← 教程目录](README.md) | 工具: Cursor | 时长: ~15min**

---

## Goal

为 Java Spring Boot 项目配置 Cursor Rules，让 Agent Mode **自动遵循团队编码规范**。

## 前置条件

- 安装 Cursor IDE
- 打开一个 Java 项目

## Step 1: 创建规则目录

```bash
mkdir -p .cursor/rules
```

## Step 2: 编写核心 Java 规则

```markdown
<!-- .cursor/rules/java.mdc -->
---
description: Java Spring Boot 编码规范
globs: **/*.java
alwaysApply: false
---

# Spring Boot 编码规范

## 分层约束
- Controller: 只做参数校验（@Valid）和响应封装，禁止写业务逻辑
- Service: 负责业务编排，单方法不超过 30 行
- Repository: 只做数据访问，不包含业务判断
- DTO: 与 Entity 严格分离，禁止直接暴露 Entity

## 命名规范
- Entity: PascalCase，如 UserOrder
- DTO: XxxDTO，如 UserCreateDTO
- Service 接口: XxxService，实现类: XxxServiceImpl
- Controller: XxxController
- Mapper: XxxMapper

## 异常处理
- 业务异常: 抛出 BusinessException(message, code)
- 统一异常处理: @RestControllerAdvice + @ExceptionHandler
- 禁止 catch (Exception e) 后不处理
- 不允许向客户端暴露堆栈信息

## 日志规范
- 使用 @Slf4j（Lombok）或 private static final Logger log = LoggerFactory.getLogger(...)
- 关键业务操作: log.info
- 异常: log.error("操作失败", e)
- 调试信息: log.debug
- 禁止在循环中打印日志
```

## Step 3: 编写安全规则

```markdown
<!-- .cursor/rules/security.mdc -->
---
description: 安全编码规范
globs: **/*.java
alwaysApply: true
---

# 安全编码规范

## 必须遵守
- 禁止硬编码密码、密钥、Token
- 所有 SQL 使用参数化查询（MyBatis #{}, JPA @Query）
- 所有外部输入必须校验（@Valid + Bean Validation）
- 敏感信息在日志中脱敏（手机号中间4位用 * 替代）
- 密码必须使用 BCrypt 加密存储

## 禁止操作
- 禁止 System.out.println
- 禁止在代码中写死 API Key / Secret
- 禁止 SQL 字符串拼接
- 禁止向客户端返回完整异常堆栈
```

## Step 4: 编写数据库规则

```markdown
<!-- .cursor/rules/database.mdc -->
---
description: 数据库操作规范
globs: "**/*Mapper.xml,**/*Repository.java,**/*Mapper.java"
alwaysApply: false
---

# 数据库操作规范

## MyBatis-Plus
- 分页查询使用 Page<T> + PageHelper
- 批量插入使用 insertBatchSomeColumn
- 逻辑删除字段: deleted (0=未删, 1=已删)
- 乐观锁: @Version 注解

## 索引
- 查询条件字段必须有索引
- 联合索引遵循最左匹配原则
- 禁止 SELECT *

## 事务
- 写操作使用 @Transactional
- 异常回滚: @Transactional(rollbackFor = Exception.class)
- 只读查询: @Transactional(readOnly = true)
```

## Step 5: 在 Cursor 中验证

1. 打开 Cursor → 打开一个 .java 文件
2. 按 `Ctrl+Shift+P` → 输入 "Cursor Rules"
3. 确认规则已加载
4. 切换到 **Agent Mode**
5. 输入：

```
创建一个 OrderController，包含：
- POST /api/orders 创建订单
- GET /api/orders/{id} 查询订单
- DELETE /api/orders/{id} 取消订单

遵循项目的编码规范。
```

Agent 会自动：
- 分离 Controller/Service/DTO
- 使用 @Valid 校验
- 加上 @Tag + @Operation 文档
- 异常用 BusinessException

## Step 6: 查看规则命中

Agent Mode 右侧面板会显示 "Attached files"，可以看到哪些 .mdc 文件被自动加载。

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 规则没生效 | 检查 glob 是否匹配当前文件类型 |
| 规则太多导致 Token 浪费 | 用 `alwaysApply: false`，让 Agent 按需加载 |
| 想手动激活某规则 | 在对话中输入 `@rule-name` |
| Agent 忽略了某规则 | 在规则前加 `CRITICAL:` 或 `必须` |

## 延伸

- → [T12: Agent 多文件重构](T12-Cursor-Agent多文件重构.md)
- → [06-Cursor 详解](../06-Cursor.md)
