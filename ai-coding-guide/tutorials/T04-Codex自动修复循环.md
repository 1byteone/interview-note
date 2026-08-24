# T04: Codex Full Auto 自动修复测试

> **[← 教程目录](README.md) | 工具: Codex CLI | 时长: ~15min**

---

## Goal

用 Codex CLI 的 Full Auto 模式**自主修复** CI 中失败的测试，直到全部通过。

## 前置条件

```bash
# 安装 Codex CLI
npm install -g @openai/codex

# 配置 API Key
export OPENAI_API_KEY="sk-..."

# 项目中有失败的测试
cd your-spring-boot-project
mvn test  # 确认有测试失败
```

## Step 1: 先看清楚失败了什么

```bash
# 运行测试，记录失败信息
mvn test 2>&1 | tail -50

# 或者只运行失败的测试类
mvn test -Dtest=OrderServiceTest 2>&1
```

## Step 2: 用 Codex Full Auto 修复

```bash
codex --full-auto "
当前项目有测试失败。

你的任务：
1. 运行 mvn test 找到所有失败的测试
2. 分析每个失败的根因
3. 修复实现代码（不是修改测试的预期）
4. 重新运行 mvn test 验证
5. 如果还有失败，继续修复循环
6. 直到所有测试通过

最后输出修复报告：
- 每个失败测试的根因
- 修改了哪些文件
- 为什么这样修复
"
```

## Step 3: 观察 Codex 自主工作

Codex 会自动执行：

```
[Agent] 运行 mvn test
[Agent] 发现 3 个测试失败
[Agent] 分析 OrderServiceTest.testCreateOrder - 失败原因: NPE
[Agent] 读取 OrderService.java
[Agent] 修复: 添加库存检查空值处理
[Agent] 运行 mvn test -Dtest=OrderServiceTest
[Agent] 1 个通过，2 个仍失败
[Agent] 分析 OrderServiceTest.testCancelOrder - 失败原因: 状态未更新
[Agent] 修复: cancelOrder 方法中补充状态变更逻辑
[Agent] 运行 mvn test
[Agent] 全部测试通过 ✓
```

## Step 4: 审查修复结果

```bash
# 查看 Codex 修改了什么
git diff

# 确认修改合理
git log --oneline -5

# 再次运行全量测试确认
mvn verify
```

## Step 5: 配置权限（可选）

为了更安全地使用 Full Auto：

```toml
# ~/.codex/config.toml
preferred_auth_method = "api_key"

# 限制可以执行的命令
[permissions]
allow = ["bash(mvn *)", "bash(git diff*)", "bash(git status*)"]
deny = ["bash(git push*)", "bash(rm -rf*)"]
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| Codex 修改了不相关的文件 | 在 Prompt 中明确: `只修改与失败测试直接相关的代码` |
| 修复引入了新问题 | 先 `git stash` 回退，缩小修复范围重新来 |
| Full Auto 模式不生效 | 检查 `codex --version`，确保 ≥ 1.0 |
| Codex 陷入无限循环 | 设置最大轮次: `codex --full-auto --max-iterations 10` |

## 延伸

- → [T05: 自定义 Review 规则](T05-Codex自定义Review规则.md)
- → [T06: 定时自动化](T06-Codex定时自动化.md)
