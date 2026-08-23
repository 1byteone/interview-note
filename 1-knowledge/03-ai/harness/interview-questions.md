# Harness Engineering 面试题大全

## 📚 知识体系

```
Harness Engineering 核心概念
├── Agent Harness (Agent 约束框架)
├── Golden Principles (黄金原则)
├── CLAUDE.md / AGENTS.md (上下文文档)
├── Agent Review Loop (审查循环)
├── Mechanical Enforcement (机器强制执行)
├── Execution Plan (执行计划)
├── Garbage Collection (垃圾回收)
└── Agent 稳定性

Agent 工程化实践
├── 上下文管理
├── 记忆持久化
├── 工具约束
├── 权限控制
├── 错误处理
├── 重试机制
├── 日志追踪
└── 质量门禁

Agent 生产化
├── 长任务稳定性
├── 状态持久化与恢复
├── 跨模型审查
├── 隔离 Session
├── 自动循环 (Loop)
├── Agent 评估 (Eval)
└── 持续优化
```

---

## 🎯 Level 1：基础题

### 1. 什么是 Harness Engineering？
**答案**：
Harness Engineering 是一种让 AI Agent 高质量完成长期、复杂任务的工程方法论。核心思想是**用文档、规范、循环和工具约束 Agent 的行为**，而不是期望 Agent 每次都能"自己表现良好"。

**核心三要素**：
1. **文档（Documentation）**：CLAUDE.md、规范文件
2. **规范（Golden Principles）**：明确的行为准则
3. **强制执行（Mechanical Enforcement）**：用 Hook、工具强制规范生效

### 2. 为什么要用 Harness？直接让 Agent 干活不行吗？
**答案**：
直接让 Agent 干活的问题：
- **任务遗忘**：长任务中 Agent 偏离原目标
- **上下文漂移**：对话越长越容易跑偏
- **质量不稳定**：每次执行结果不一致
- **不可复现**：无法保证相同的任务有相同的质量

**Harness 的价值**：
1. 目标锁定：通过执行计划确保 Agent 不跑偏
2. 质量保证：通过 review loop 持续校验
3. 可复现性：同样的输入有稳定的输出
4. 可持续性：长任务可以持续运行而不崩溃

### 3. 什么执行计划（Execution Plan）？
**答案**：
Execution Plan 是让 Agent 在开始时制定详细的执行步骤和完成标准，然后逐步执行并自我校验的机制。

```markdown
## 任务：重构用户服务模块

### 完成标准（Definition of Done）
- [ ] 所有用户相关接口迁移到新模块
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 全量测试通过
- [ ] 无遗留 TODO

### 执行步骤
1. **分析阶段**
   - 梳理现有用户模块代码
   - 识别耦合点
   - 输出重构方案
   - 校验点：方案获得确认

2. **重构阶段**
   - 按方案拆分模块
   - 保持接口兼容
   - 校验点：编译通过 + 测试通过

3. **验证阶段**
   - 运行全量测试
   - 检查代码质量
   - 校验点：全绿 + 无告警

### 风险与回退
- 若重构失败，回退到原方案
- 保留中间提交点
```

---

## 🎯 Level 2：进阶题

### 4. What are 黄金原则（Golden Principles）？
**答案**：
黄金原则是指导 Agent 的顶层行为准则，通常写在 CLAUDE.md 中。

**典型黄金原则示例**：
```
## 黄金原则
1. 先探索再修改：修改代码前先理解代码
2. 小步提交：每次修改保持最小化，可回滚
3. 测试先行：先写测试再写实现
4. 不破坏现有功能：任何修改不以破坏现有功能为代价
5. 忠实报告：实验失败要如实报告，不掩盖
6. 验证后再声明完成：不声称"完成了"除非已验证
7. 遇到不确定时询问：不要猜测需求
```

### 5. 什么是 Agent Review Loop？
**答案**：
Agent Review Loop 是 Agent 在完成任务过程中，对其工作结果进行反复检查、校验、修正的循环机制。

**Review Loop 流程**：
```text
执行任务步骤
    ↓
输出中间结果
    ↓
对照完成标准检查
    ├── 满足 → 进入下一步
    ├── 发现问题 → 修正 → 重新检查
    └── 大偏差 → 复盘 + 调整方案
    ↓
全部完成
```

**实现方式**：
1. **内置反思**：Agent 自己检查自己的工作
2. **代码评审 Agent**：独立 Agent 审查输出
3. **跨模型审查**：用不同模型交叉验证
4. **自动测试**：跑测试验证正确性

---

## 🎯 Level 3：高级题

### 6. 什么是 Mechanical Enforcement？如何实现？
**答案**：
Mechanical Enforcement 是用机器/代码强制规范生效，而不是依赖 Agent"自觉"。

**实现方式**：

**1. Hooks（钩子）**：
```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "bash enforcement.sh $CLAUDE_PROJECT_DIR"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash check_style.sh"
          }
        ]
      }
    ]
  }
}
```

**2. 工具约束**：
- 权限白名单：只允许 Agent 使用特定工具
- 目录限制：限制 Agent 的读写范围
- 命令校验：危险命令自动拦截

**3. 强制检查脚本**：
```bash
# 提交前强制检查
#!/bin/bash
# 1. 检查是否有未处理的 TODO
if grep -r "TODO" src/; then
  echo "有未处理 TODO，禁止提交"
  exit 1
fi

# 2. 检查代码格式
if ! npm run lint; then
  echo "代码格式不规范"
  exit 1
fi

# 3. 检查测试
if ! npm test; then
  echo "测试失败"
  exit 1
fi
```

---

## 🎯 Level 4：专家题

### 7. 如何让 Agent 长时间稳定自主完成任务？
**答案**：

**核心思想**：不让 Agent 一次性"扛"完全部任务，而是设计可持续的循环机制。

**方案一：自动循环 + 任务队列**
```text
任务队列 (Queue)
    ↓
取出任务 → 新 Agent 执行
    ↓
执行结果写入任务系统
    ↓
验证 → 通过 → 下一个任务
        → 失败 → 记录 + 重试
```

**方案二：隔离 Session**
- 每步任务使用全新 Agent Session
- 避免上下文污染
- 通过文件系统共享状态

**方案三：检查点存档**
- 定期保存 Agent 状态
- 支持断点恢复
- 崩溃后从检查点继续

**方案四：Cross-Vendor Review**
- 用不同模型（Claude / GPT / Gemini）审查
- 避免单个模型的盲区

---

### 8. 如何评估 Agent 的开发质量？
**答案**：

**评估框架**：

| 维度 | 指标 | 说明 |
|------|------|------|
| 正确性 | 测试通过率 | 单元/集成测试 |
| 完整性 | DoD 达成率 | 完成标准是否全满足 |
| 稳定性 | 失败率/重试率 | 长任务运行稳定性 |
| 效率 | 工具调用数 | 是否低效重复 |
| 偏航率 | 任务偏离率 | 是否偏离原目标 |
| 代码质量 | 审查通过率 | 代码评审结果 |

**评估工具**：
- 人工评审 + 自动化测试
- Agent 日志分析
- 结果对比（黄金答案）

---

### 9. 如何设计一个生产级的 Agent Harness 系统？
**答案**：

```text
┌────────────────────────────────────────────┐
│            任务输入层                       │
│  ├── 任务定义 (Goal)                       │
│  ├── 输入数据                               │
│  └── 约束条件 (Constraints)                │
└────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────┐
│            规划层                           │
│  ├── 任务分解 (Task Decomposition)         │
│  ├── 执行计划 (Execution Plan)             │
│  └── 风险评估 (Risk Assessment)            │
└────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────┐
│            执行层                           │
│  ├── Agent Session (隔离)                  │
│  ├── 工具调用 (受控)                        │
│  ├── 检查点保存 (Checkpoint)               │
│  └── 超时控制                               │
└────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────┐
│            审查层                           │
│  ├── 自动测试                              │
│  ├── 代码评审 Agent                        │
│  ├── Cross-Vendor Review                   │
│  └── 门禁控制 (Gate)                       │
└────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────┐
│            反馈层                           │
│  ├── 结果验证                              │
│  ├── 错误分析                              │
│  ├── 知识沉淀                              │
│  └── 参数调优                              │
└────────────────────────────────────────────┘
```

**关键设计原则**：
1. **Fail Fast**：快失败快恢复
2. **一切可追踪**：日志、检查点、评审记录
3. **一切可回滚**：每个步骤可撤销
4. **状态外置**：上下文尽量存放在文件/数据库，不依赖对话
5. **门禁控制**：质量不达标不放行

---

## 📖 学习资源

### 推荐项目
- [harness-skills](https://github.com/lispking/harness-skills) - Claude Code Harness 规范
- [harness-engineering-skills](https://github.com/stone16/harness-engineering-skills) - 多 Agent 编排
- [Agent Skills](https://github.com/anthropics/skills) - Anthropic Agent Skills 规范

### 关键概念对照
| 概念 | 简释 |
|------|------|
| Harness | Agent 的"约束框架" |
| Golden Principles | 顶层行为准则 |
| Execution Plan | 执行前先定计划 |
| Review Loop | 反复检查修正 |
| Mechanical Enforce | 用代码强制规范 |
| Isolation | 执行隔离 |
| Gate | 质量门禁 |

### 最佳实践
1. 文档先行，CLAUDE.md 写好再干活
2. 小步快跑，频繁校验
3. 用 Review Loop 而不是信任一次输出
4. 关键操作设置权限门禁
5. 状态外置，不依赖上下文长度