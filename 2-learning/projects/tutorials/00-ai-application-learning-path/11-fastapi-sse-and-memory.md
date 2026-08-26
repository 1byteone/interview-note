# FastAPI、SSE 与 Memory

## 事件契约

推荐事件：`status`、`delta`、`tool`、`sources`、`memory`、`done`、`error`。每条事件带 `event_id`、`thread_id` 和序号，前端按序号去重。

## SSE 注意事项

SSE 数据必须以空行结束；网络 chunk 可能截断一条事件，客户端必须缓存未完成片段。`fetch` + `ReadableStream` 不会自动获得 EventSource 的重连能力，断线、Abort、代理缓冲和重复事件都要单独处理。

## 会话状态

`thread_id` 用于隔离对话状态；服务端必须校验用户是否拥有该线程。InMemory 只适合单进程教学，SQLite 适合单机持久化，Redis/PostgreSQL 更适合多实例和可靠恢复，具体选型取决于一致性和查询需求。

## 失败与取消

客户端断开后取消模型和工具任务；发送一次 `error` 或可识别的取消终态；副作用工具必须幂等。重连时使用最后事件 ID 或服务端快照恢复，不重复执行已完成写操作。

## 来源

- `AI_EXAM/docs/第2章 构建智能体.docx`
- `AI_EXAM/docs/第3章 智能体的高级特性.docx`
- [现有 SSE 教程](../16-sse-streaming/README.md)
