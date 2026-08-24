# T05: Codex AGENTS.md 自定义 Review 规则

> **[← 教程目录](README.md) | 工具: Codex App / CLI | 时长: ~20min**

---

## Goal

在 AGENTS.md 中定义**团队专属的 Code Review 规则**，让 Codex 自动审查 PR 时能捕获业务层面的问题。

## 前置条件

- GitHub 仓库已开启 Codex Code Review
- 有 AGENTS.md 文件

## Step 1: 编写 Review 规则

```bash
cat > AGENTS.md << 'ENDOFFILE'
# AGENTS.md

## 项目概述
电商微服务后端，Spring Boot 3 + MySQL + Redis + RocketMQ。

## Code Review 规则

### 破坏性变更
- 禁止修改 REST API 的请求/响应字段名（除非有版本迁移计划）
- 禁止删除或重命名 FeignClient 接口方法
- MQ 消息体字段变更必须保持向后兼容

### 安全边界
- 禁止在日志中打印用户密码、Token、手机号明文
- 所有外部输入必须经过 @Valid 校验
- SQL 查询必须使用参数化，禁止字符串拼接
- 敏感配置（密钥、密码）禁止硬编码，必须从 Nacos 读取

### 数据一致性
- 涉及库存扣减的操作必须在事务内完成
- Redis 缓存更新必须考虑缓存与 DB 一致性
- MQ 消费者必须处理重复消费（幂等）

### 性能
- 禁止在循环中调用外部服务
- 分页查询必须有 LIMIT，禁止全表扫描
- Redis 批量操作使用 MGET/MSET，禁止循环 GET

### 日志规范
- Service 层必须打印关键操作日志
- 异常必须记录完整堆栈，禁止只打印 message
- 日志级别: DEBUG 用于开发，INFO 用于业务关键点，WARN 用于异常但可恢复，ERROR 用于不可恢复
ENDOFFILE
```

## Step 2: 提交并触发 Review

```bash
git add AGENTS.md
git commit -m "docs: add AGENTS.md with team review rules"
git push origin main
```

## Step 3: 创建一个触发规则的 PR

```bash
git checkout -b fix/remove-unused-field

# 故意写一个违反规则的代码
cat > UserController.java << 'EOF'
@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理")
public class UserController {

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        log.info("查询用户: {}", id);
        User user = userService.findById(id);
        log.info("用户信息: password={}, phone={}", user.getPassword(), user.getPhone());
        return userMapper.toResponse(user);
    }
}
EOF

git add -A
git commit -m "fix: remove unused field"
git push origin fix/remove-unused-field
```

## Step 4: 查看 Codex Review 结果

Codex 会基于 AGENTS.md 规则给出类似：

> **禁止在日志中打印用户密码和手机号。** AGENTS.md 安全边界规则要求：禁止在日志中打印用户密码、Token、手机号明文。当前 `log.info("用户信息: password={}, phone={}", ...)` 违反此规则。建议改为 `log.info("用户查询成功: userId={}", id)`。

## Step 5: 迭代优化规则

```bash
# 规则太宽泛会产生噪音
# 规则太具体会遗漏

# 好的规则结构:
# 1. 说清楚不变量（为什么不能这样做）
# 2. 给出安全路径（应该怎么做）
# 3. 限定范围（哪些文件/目录适用）
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| Review 产生太多噪音 | 缩小规则范围，加 glob 限定适用目录 |
| 规则没有被触发 | 检查 AGENTS.md 路径是否在仓库根目录 |
| 想跳过某次 Review | 在 PR 描述中加 `@codex skip-review` |
| 规则之间冲突 | 按优先级排列，安全规则 > 性能规则 > 风格规则 |

## 延伸

- → [T06: 定时自动化](T06-Codex定时自动化.md)
- → [08-MCP 与 Skills](../08-MCP与Skills.md)
