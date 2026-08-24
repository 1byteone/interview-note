# T03: 遗留代码 50K 行渐进式重构

> **[← 教程目录](README.md) | 工具: Claude Code | 时长: ~60min**

---

## Goal

用 Claude Code 将一个 50,000 行的 Spring Boot 2.x 遗留项目**渐进式迁移**到 Spring Boot 3.x，不破坏生产。

## 前置条件

- 已有 Spring Boot 2.x 项目（javax.* 包名）
- 项目有基本测试覆盖
- Git 工作区干净（所有改动已提交）

## Step 1: 特征测试——先记录当前行为

**这是最关键的一步。** 在重构之前，先让 Claude Code 为每个核心 API 写"特征测试"：

```
为当前项目编写特征测试（Characterization Tests）。

要求：
1. 找到所有 @RestController
2. 为每个 API 端点编写集成测试
3. 测试覆盖：正常流程 + 异常流程 + 边界条件
4. 记录当前的请求/响应格式作为"契约"
5. 不修改任何生产代码
6. 测试通过后，这些测试就是重构的安全网

先列出所有 API 端点清单，确认后再逐步编写测试。
```

## Step 2: 创建 CLAUDE.md 标记遗留约束

```bash
cat > CLAUDE.md << 'EOF'
# 遗留项目重构规则

## 当前状态
- Spring Boot 2.7 + Java 11
- javax.* 包名
- 部分代码没有测试
- 使用 Spring Data JPA

## 重构目标
- Spring Boot 3.3 + Java 21
- jakarta.* 包名
- 所有核心 API 有集成测试
- 逐步替换过时 API

## 重构原则
- CRITICAL: 每次只改一个模块，不跨模块重构
- CRITICAL: 每次修改后必须通过所有已有测试
- CRITICAL: 不修改测试的预期行为，只修改实现
- 使用渐进式策略：先升级依赖 → 再改包名 → 再优化代码
- 每次提交都是可工作的状态

## 禁止
- 不允许一次性重构整个项目
- 不允许删除任何测试
- 不允许修改 API 的请求/响应格式（除非明确要求）
EOF
```

## Step 3: 分阶段重构

### Phase 1: 升级依赖（不改代码）

```
Phase 1: 升级 pom.xml 中的依赖版本。

目标：
- Spring Boot 2.7 → 3.3
- Java 11 → 21
- 更新所有 Spring 相关依赖

约束：
- 只改 pom.xml，不改 Java 代码
- 改完后执行 mvn compile 检查编译
- 列出所有编译错误，但不要自动修复
```

### Phase 2: javax → jakarta 迁移

```
Phase 2: 将所有 javax.* 包名替换为 jakarta.*。

目标：
- javax.persistence → jakarta.persistence
- javax.validation → jakarta.validation
- javax.servlet → jakarta.servlet
- 其他 javax → jakarta

约束：
- 全局替换后执行 mvn compile
- 逐个修复编译错误
- 每修复一批就运行 mvn test
- 保持测试全部通过
```

### Phase 3: 修复编译错误

```
Phase 3: 修复升级后的编译错误。

常见的包括：
- Spring Security 配置方式变更
- WebMvcConfigurer 方法签名变更
- 部分废弃 API 的替代方案

对每个错误：
1. 分析根因
2. 给出修复方案
3. 应用修复
4. 运行相关测试验证
```

### Phase 4: 优化新特性

```
Phase 4: 利用新版本特性优化代码。

可选优化：
- 使用 Java 21 Record 替代简单 DTO
- 使用 Virtual Threads 处理 IO 密集型任务
- 使用 Pattern Matching 替代 instanceof 链
- 使用 Sealed Classes 限制继承

约束：
- 每个优化单独提交
- 优化后必须通过所有测试
- 不改变外部行为
```

## Step 4: 验证重构结果

```bash
# 全量测试必须通过
mvn verify

# 对比重构前后的 API 行为
# 特征测试应该全部通过（行为未变）

# 检查代码质量
find . -name "*.java" | xargs grep "javax\." | head -20
# 应该返回空（没有遗留的 javax 引用）
```

## Step 5: 渐进式提交策略

```bash
git add -A
git commit -m "refactor(deps): upgrade Spring Boot 2.7 to 3.3"

git add -A
git commit -m "refactor(javax): migrate javax.* to jakarta.*"

git add -A
git commit -m "refactor(security): update SecurityConfig for Spring Security 6"

git add -A
git commit -m "refactor(java21): adopt Record and Pattern Matching"
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 测试大量失败 | 回退到上一个可工作的状态，缩小重构范围 |
| 某个库不兼容 Spring Boot 3 | 查找替代库，或暂时保留旧版本 |
| 重构范围太大无法控制 | 用 Subagent 分模块并行重构 |
| 不确定改动是否安全 | 让 Claude Code 先分析影响范围再动手 |

## 延伸

- → [T01: 从零搭建](T01-Claude-Code搭建Spring-Boot项目.md)（新项目用 T01）
- → [T14: 五工具协作](T14-五工具协作全流程.md)
