# T09: Hermes Agent 记忆与自进化 Skill

> **[← 教程目录](README.md) | 工具: Hermes Agent | 时长: ~20min**

---

## Goal

配置 Hermes Agent 的持久记忆，让它**从交互中自动学习**并创建可复用的 Skill。

## 前置条件

```bash
# 安装 Hermes
curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash

# 验证
hermes --version

# 首次启动
hermes
```

## Step 1: 查看当前记忆状态

在 Hermes CLI 中：

```
/memory list
```

输出类似：

```
MEMORY (your personal notes) [32% — 704/2,200 chars]
§
No entries yet.

USER PROFILE [0% — 0/1,375 chars]  
§
No entries yet.
```

## Step 2: 手动添加记忆

```
# 添加环境信息
memory(action="add", target="memory", content="项目位于 ~/code/order-service，Spring Boot 3 + MyBatis-Plus + Redis + RocketMQ")

# 添加编码约定
memory(action="add", target="memory", content="项目约定: Controller 不写业务逻辑，Service ≤30行/方法，DTO ≠ Entity，不使用 Lombok")

# 添加用户偏好
memory(action="add", target="user", content="用户是资深 Java 后端工程师，偏好简洁回复，不需要基础概念解释")
```

验证：

```
/memory list
```

```
MEMORY (your personal notes) [45% — 990/2,200 chars]
§
项目位于 ~/code/order-service，Spring Boot 3 + MyBatis-Plus + Redis + RocketMQ
§
项目约定: Controller 不写业务逻辑，Service ≤30行/方法，DTO ≠ Entity，不使用 Lombok

USER PROFILE [28% — 385/1,375 chars]
§
用户是资深 Java 后端工程师，偏好简洁回复，不需要基础解释
```

## Step 3: 交互中自动学习

正常与 Hermes 对话。当它发现新信息时会自动保存：

```
用户: 我们项目的 Redis 用的是 Redisson，锁的 key 格式是 lock:{service}:{id}
（Hermes 自动保存到 MEMORY.md）

用户: 生产环境的 MySQL 在 10.0.1.50:3306
（Hermes 自动保存到 MEMORY.md）

用户: 我们团队用 Conventional Commits: feat/fix/refactor/docs
（Hermes 自动保存到 MEMORY.md）
```

## Step 4: 触发自进化 Skill

执行一个复杂任务（5+ 工具调用），Hermes 会自动提取模式：

```
帮我做一个完整的 Spring Boot API Review：
1. 找到所有 Controller
2. 检查参数校验
3. 检查异常处理
4. 检查日志规范
5. 检查 API 文档
6. 输出审查报告
```

任务完成后，Hermes 会自动创建 Skill：

```
💾 Skill 'springboot-api-review' created
```

查看已创建的 Skill：

```
/skills list
```

## Step 5: 查看和编辑自进化 Skill

```bash
# 查看 Skill 内容
hermes journey list

# 查看特定 Skill
cat ~/.hermes/skills/springboot-api-review/SKILL.md

# 编辑 Skill
hermes journey edit springboot-api-review
```

生成的 Skill 类似：

```markdown
---
name: springboot-api-review
description: Spring Boot REST API 代码审查
created: from experience
---

# Spring Boot API Review

## 检查项
1. 所有 @RequestParam/@PathVariable 有 @Valid
2. @RestControllerAdvice 覆盖所有异常
3. 每个 Controller 方法有 @Operation 注解
4. 关键操作有 log.info，异常有 log.error
5. 分页查询有 @ParameterObject Pageable

## 工具调用顺序
1. search("*Controller.java")
2. read(每个 Controller)
3. check(@Valid, @Operation, log., @ExceptionHandler)
4. generate report
```

## Step 6: 配置记忆写入审批

```yaml
# ~/.hermes/config.yaml
memory:
  write_approval: true  # 开启审批
```

之后每次记忆写入都会等待审批：

```
/memory pending     # 查看待审批
/memory approve 1   # 批准
/memory reject 2    # 拒绝
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 记忆满了（2,200 字符） | `/memory list` → 合并相似条目 → `memory(action="remove", ...)` |
| Skill 没有自动创建 | 确保任务涉及 5+ 工具调用 |
| 记忆内容不准确 | `memory(action="replace", target="memory", old_text="错误内容", content="正确内容")` |
| 想禁用自动记忆 | 设置 `memory.write_approval: true` |

## 延伸

- → [T10: 定时工作流](T10-Hermes定时工作流.md)
- → [05-Hermes 详解](../05-Hermes.md)
