from pathlib import Path
import re
root = Path(r'D:\code\codeAgentDev\interview-note\.claude\worktrees\mall-ai-search-interview-deliverables')
qpath = root/'projects/ai-mall/mall-ai-search-interview-questions.md'
t = qpath.read_text(encoding='utf-8')
start=t.index('### 题目 12：'); end=t.index('### 题目 13：',start)
q12='''### 题目 12：OpenFeign 基础：声明式 HTTP 调用是什么
**难度**：Level 1
**类型**：接口契约
**考察点**：OpenFeign 基础、声明式客户端、契约边界
**项目证据**：Word 第 9.2 节提到 Java/Gateway/Feign 适配方案 `[Word描述]`；本次指定范围无 Java 源码 `[待验证]`。

**问题**：在 Java 微服务中，`@FeignClient` 主要解决什么问题？写出一个面向搜索服务的最小声明式客户端应关注哪些契约字段。

**标准答案**：OpenFeign 用接口和注解描述 HTTP 调用，由客户端代理完成请求构造、参数绑定和响应反序列化，减少手写 HTTP 模板代码。最小客户端至少要明确服务名或 URL、HTTP 方法与路径、query/body 参数、请求/响应 DTO、超时和错误映射；例如搜索适配层要明确 `/recommend`、`/extract` 的版本路径及 `threadId`/`thread_id` 转换边界。当前材料只证明 Word 描述了该整合方向，不能说项目已有可运行的 Feign 客户端。

**深入解析**：Feign 只是调用方式，不自动解决服务发现、重试、幂等、鉴权或业务正确性。Java/Gateway 是否实际完成路径重写、DTO 映射和超时策略，必须通过 Java 源码、网关日志和端到端契约测试验证。

**面试官追问**：OpenFeign 的超时、重试和错误解码为什么不能只用默认值？

**优秀回答应包含**：区分连接/读取 timeout，按幂等性和错误类型设计有限重试，统一错误码与 HTTP status，并明确当前实现待验证。

**常见误答**：说 `@FeignClient` 会自动保证服务高可用；把 Word 方案当成已有 Java 源码；忽略 DTO 和版本化路径。

'''
t=t[:start]+q12+t[end:]
# Q31 code block
start=t.index('### 题目 31：'); cs=t.index('```python',start); ce=t.index('```',cs+3)+3
code='''```python
import asyncio


async def write_batches(store, chunks, batch_size=100):
    success_batches = 0
    failed_batches = 0
    failed_ranges = []
    batch = []
    start = 0

    async def flush(current, offset):
        nonlocal success_batches, failed_batches
        try:
            # add_documents 是同步 I/O，不能直接阻塞 async 事件循环。
            await asyncio.to_thread(store.add_documents, current)
            success_batches += 1
        except Exception as exc:
            failed_batches += 1
            failed_ranges.append({"start": offset, "size": len(current),
                                  "error_type": type(exc).__name__})
            # 生产方案：记录任务 ID，按有限退避策略重试或标记待补偿。

    for chunk in chunks:
        batch.append(chunk)
        if len(batch) == batch_size:
            await flush(batch, start)
            start += len(batch)
            batch = []
    if batch:
        await flush(batch, start)

    status = ("failed" if failed_batches and not success_batches
              else "partial" if failed_batches else "succeeded")
    return {"status": status, "success_batches": success_batches,
            "failed_batches": failed_batches, "failed_ranges": failed_ranges}
```

这里将同步 `add_documents` 放入 `asyncio.to_thread`，避免在异步请求线程直接阻塞事件循环；批次状态和安全失败记录仍由调用方持久化。示例的 `chunks` 是可迭代的同步输入；若上游是异步流，应由异步生成器/队列收集到 100 条再调用 `flush`，不要在 `flush` 中一次性把无限流转成 list。'''
t=t[:cs]+code+t[ce:]
for a,b in [('Agent 调用唯一的 `vector_search_tool`','Agent 配置/注册 `vector_search_tool`，实际运行调用仍需集成验证'),('只注册一个 `vector_search_tool`','配置/注册 `vector_search_tool`，实际运行调用仍需集成验证'),('推荐 Agent 可根据 Prompt 使用唯一 `vector_search_tool`','推荐 Agent 可根据 Prompt 使用已配置/注册的 `vector_search_tool`；实际运行调用仍需集成验证'),('含唯一 vector tool 的 Agent','含已配置/注册 vector tool 的 Agent（实际运行调用仍需集成验证）')]: t=t.replace(a,b)
ls=t.index('## Level 4：'); le=t.index('## Level 5：',ls); t=t[:ls]+re.sub(r'\*\*面试官追问（第 ([1-4]) 轮）\*\*：',r'**面试官追问**：第 \1 轮：',t[ls:le])+t[le:]
t=t.replace('27, 35, 38-44, 49-50, 53, 55, 58','27, 30, 35, 38-44, 49-50, 53, 55, 58')
qpath.write_text(t,encoding='utf-8')

# Synchronize map matrix headings and numbers directly from final question bank.
mpath=root/'projects/ai-mall/mall-ai-search-knowledge-map.md'; m=mpath.read_text(encoding='utf-8')
levels=[]
for lm in re.finditer(r'^## Level ([1-5])：.*$',t,re.M):
    lvl=int(lm.group(1)); nm=re.search(r'^## Level [1-5]：',t[lm.end():],re.M); sec=t[lm.end():lm.end()+nm.start() if nm else len(t)]
    levels.append((lvl,[(int(x.group(1)),x.group(2).strip()) for x in re.finditer(r'^### 题目 (\d+)：(.+)$',sec,re.M)]))
labels={1:'基础项目事实与主链路',2:'组件原理、契约与测试边界',3:'代码题、场景排障与局部设计',4:'项目深挖与生产化方案',5:'综合架构设计'}
rows=[]
for lvl,qs in levels:
    rows.append(f'### Level {lvl}：{labels[lvl]}（{len(qs)} 题）\n\n| 题号 | 最终题目主题 | 覆盖知识点 | 题目事实边界 |\n|---:|---|---|---|')
    for n,title in qs:
        rows.append(f'| {n} | {title} | 以题目正文为准 | 与题目正文的 `[源码已确认]`、`[Word描述]`、`[架构规划]`、`[待验证]` 标签一致 |')
    rows.append('')
new='## 4. Level 1—5 题目覆盖矩阵（与最终题库同步）\n\n> 本矩阵以最终题库的 `### 题目 N` 标题为事实来源；Task 3 在 Level 3/5 采用了代码题、测试题和综合架构题的新主题，因此这里同步更新主题与编号，避免“预留题目”与题库正文漂移。\n\n'+'\n'.join(rows)+'\n'
ss=m.index('## 4. Level 1—5'); ee=m.index('### 4.1 Level 数量核对',ss); m=m[:ss]+new+m[ee:]
m=m.replace('## 4.1 Level 数量核对','### 4.1 Level 数量核对').replace('27, 35, 38-44, 49-50, 53, 55, 58','27, 30, 35, 38-44, 49-50, 53, 55, 58')
# Make coverage granular and explicit; no aggregate full-chain claim.
cs=m.index('### 6.2 核心知识点三层覆盖'); ce=m.index('### 6.3 事实边界检查',cs)
coverage='''### 6.2 核心知识点三层覆盖

> 按可独立回答的核心点检查三层覆盖，不用“全链路”粗粒度聚合项冒充。基础/事实层优先为 Level 1；原理/场景层为 Level 2 或 3；深挖/架构层为 Level 4 或 5。缺少基础层时明确标注缺口。

| 核心知识点 | 基础/事实层 | 原理/场景层 | 项目深挖/架构层 | 检查结论 |
|---|---|---|---|---|
| SKU 加载与正文构造 | 2 | 13-16 | 45,57 | 通过 |
| Chunk 与 Embedding | 4-5 | 16-18,35,44 | 48-50,53,57-58 | 通过 |
| Redis Vector 与 Top-K | 6,9 | 18,21,35,40 | 48-50,58-59 | 通过 |
| 批次同步与文档 ID | 7 | 13,19,31,38,40,42 | 45,51,57 | 通过 |
| Agent 与 Tool | 8 | 20,34,41-43 | 54,58-60 | 通过 |
| Schema 与条件提取 | 10-11 | 22-24,32-37,42 | 46,54,58,60 | 通过 |
| OpenFeign/Java/Gateway 契约 | 12 | 24,29,39 | 47,56,60 | 通过（Java实现仍待验证） |
| 会话与 Checkpoint | 1 | 25,39 | 52,59-60 | 通过（持久化方案为规划） |
| 异常、超时与 Provider | 5 | 17,26-28,33,38,44 | 47,49,53,55,59 | 通过 |
| 测试与可观测性 | 30（事实基线） | 42-44 | 47,50,53,57,59-60 | 基础题不在 Level 1，已明确而非冒充 |
| 前端协作与版本契约 | 1 | 24,28-29,36-39 | 47,56,60 | 通过 |

'''
m=m[:cs]+coverage+m[ce:]
mpath.write_text(m,encoding='utf-8')
print('updated')
