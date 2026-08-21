# Task 3 工作报告：生成项目专项面试题

## 改动文件

- `D:\code\codeAgentDev\interview-note\.claude\worktrees\agent-a12a6ecf7dcd429d0\projects\ai-mall\mall-ai-search-interview-questions.md`
  - 新增 60 道云商城智能搜索专项面试题。
  - 严格消费 `mall-ai-search-project-analysis.md` 与 `mall-ai-search-knowledge-map.md`，沿用 S1-S13 证据边界。
  - 补充项目 Goal/阅读方式/证据等级/实现边界/答题策略、30 秒/3 分钟/10 分钟项目表达和 100 分评分标准。
- `D:\code\codeAgentDev\interview-note\.claude\worktrees\mall-ai-search-interview-deliverables\.superpowers\sdd\2026-08-21-mall-ai-search-interview-deliverables\task-3-report.md`
  - 本报告。

## 题目统计

- 总题数：60；题号 1-60 连续且唯一。
- Level 1：12 题；Level 2：18 题；Level 3：14 题；Level 4：12 题；Level 5：4 题。
- 十类题型各有主归类：事实复述 7、原理解释 9、代码走读 9、接口契约 7、故障排查 5、性能成本 6、测试验证 4、一致性幂等 3、安全可靠性 7、架构设计 3。
- Level 3：代码题 3、Bug 题 3、场景题 5、测试/排障题 3。
- Level 4：12 题均包含 4 轮编号追问。
- Level 5：4 题分别覆盖亿级增量向量同步、关键词+向量+Rerank 混合搜索、生产高可用与降级、多轮会话/SSE/成本/安全；每题覆盖目标、约束、架构、数据流、接口、存储、故障、指标、容量估算和取舍。
- 每题均有编号、难度、类型、考察点、项目证据、问题、标准答案、深入解析、追问、优秀回答要点和常见误答。

## 检查命令与输出

### 1. 题号、等级、统一字段和题型

命令：

```bash
python - <<'PY'
from pathlib import Path
import re, collections
p=Path('projects/ai-mall/mall-ai-search-interview-questions.md')
s=p.read_text(encoding='utf-8')
nums=[int(x) for x in re.findall(r'^### 题目 (\\d+)：',s,re.M)]
levels=[sum(a<=n<=b for n in nums) for a,b in [(1,12),(13,30),(31,44),(45,56),(57,60)]]
types=collections.Counter(re.findall(r'^\\*\\*类型\\*\\*：(.*?)(?:\\s+)?$',s,re.M))
print(len(nums), len(set(nums)), nums == list(range(1,61)), levels)
print(len(types), dict(types))
PY
```

输出摘要：

```text
题号/等级: 60 60 True [12, 18, 14, 12, 4]
十类题型: 10
字段: True
```

### 2. Level 3 与 Level 4 结构

命令同上脚本的专项检查，输出摘要：

```text
Level3专项: 代码题 3, Bug题 3, 场景题 5, 测试验证 3
Level4四轮: True
```

### 3. Markdown 空白检查

命令：

```bash
git diff --check
```

输出：无输出，检查通过。

## 敏感能力文本自检

已搜索并人工抽查 Elasticsearch/ES、Rerank、消息队列/MQ、业务缓存、Redis checkpoint、生产高可用等表述。文档始终将未证实组件标为当前缺口、`[待验证]`、`[架构规划]` 或设计约束；明确写出不能将其说成当前已有能力。当前实现只按事实文件保留 RedisVectorStore、InMemorySaver、同步 `/sync`、`k=10`、有限 Provider 工厂等结论。未复制 `.env.example` 中的密钥、口令、认证 URL 或 API key。

## 未验证项与疑虑

- 未连接真实 MySQL、Redis、Embedding API 或 Chat API；Redis index schema、向量维度、旧向量删除行为仍需受控环境验证。
- 未执行 FastAPI TestClient/真实 HTTP 联调，因此全局异常响应的实际 HTTP status 仍需验证。
- Agent tool call、结构化输出失败、跨轮记忆、超时和 Provider 连通性未运行验证。
- 前端 `/v1/search/*` 与 Python `/api/v1/*` 的 Gateway/Java/Feign 映射，以及 `threadId`/`thread_id` 转换仍需整合工程或浏览器 Network 验证。
- 现有搜索测试是手工 async 示例而非完整 pytest 断言套件；未做压力测试、离线召回评估、成本评估或多实例验证。
- 题库中的代码片段和 Level 4/5 方案明确是最小伪实现或架构规划，不代表已经修改目标项目源码或已经上线。
