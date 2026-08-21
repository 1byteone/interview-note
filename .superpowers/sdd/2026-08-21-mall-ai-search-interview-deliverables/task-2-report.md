# Task 2 报告：知识图谱与题目覆盖矩阵

## 改动文件

- `D:/code/codeAgentDev/interview-note/.claude/worktrees/agent-a8590dbef26defc93/projects/ai-mall/mall-ai-search-knowledge-map.md`
  - 新增项目知识树与 Mermaid 依赖图。
  - 新增固定字段的事实证据矩阵，覆盖商品加载、切分、Embedding、Redis Vector、Agent、结构化输出、Checkpoint、Provider、异常、前端协作、测试和生产化缺口。
  - 新增 Level 1—5 的 1-60 题号映射，数量为 12/18/14/12/4，共 60 题；未生成题库全文。
  - 新增十类题型统计、复习路线、自测标准和完整性检查。
  - 明确区分 `[源码已确认]`、`[Word描述]`、`[架构规划]`、`[待验证]`；未把 ES、Rerank、MQ、业务缓存、Redis 持久 checkpoint 或生产高可用写成现有实现。

## 检查命令与输出

```text
python textual self-check:
level rows=60 unique=60 continuous=True
type counts=[7, 9, 9, 7, 5, 6, 4, 3, 7, 3] sum=60
required sections=True
forbidden-current claims checked: ES/Rerank/MQ/cache/HA marked planning or pending in explicit boundary text

git diff --check: passed

git status --short: clean after commit
```

## Commit

`d6ffbfe74f88f1f4fe388099180e1f330c8b08fd`

## 疑虑

- 题型是每题唯一的主归类，题型统计已覆盖 1-60；同一知识点在 Level 矩阵中允许跨题重复覆盖，这是有意设计。
- Java/Gateway/Feign、Redis checkpoint、ES/Rerank/MQ、生产一致性与高可用均没有本次指定源码证据，交付物已作为 Word 描述、架构规划或待验证项处理。
- 未执行真实 MySQL、Redis、模型 Provider、FastAPI 或前端联调；这符合 Task 2 只生成规划矩阵且不接触目标源码的要求。
