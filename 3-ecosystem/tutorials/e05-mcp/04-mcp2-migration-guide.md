# MCP 2.0 迁移指南：从有状态协议到无状态核心

> 本文档属于 **E05 MCP 协议生态** 系列教程的第四篇，系统解读 MCP 2.0（2026-07-28 规范公告）的核心变化，给出从 MCP 1.x 有状态协议迁移到 2.0 无状态协议的完整改造路径，覆盖握手移除、自描述请求、Header 路由、响应缓存、MRTR、授权迁移、Tasks 扩展和弃用处理。

---

## 1. 版本里程碑：MCP 2026-07-28 公告概览

2026 年 7 月 28 日，MCP 官方发布 2.0 规范公告。这是 MCP 自 2024 年 11 月开源以来最大的一次架构级变更——**协议从"双向有状态会话"彻底转向"请求/响应无状态核心"**。核心动机是解决生产环境中的三大痛点：

| 痛点 | 1.x 表现 | 2.0 解法 |
|------|---------|---------|
| 负载均衡困难 | 服务器依赖 `Mcp-Session-Id` 保存会话状态，请求必须粘滞到同一实例 | 无状态核心，任意请求可落在任意实例 |
| 网关集成成本高 | 网关需解析 JSON 请求体才能知道方法名，无法做透明路由/限流 | `Mcp-Method`、`Mcp-Name` HTTP Header |
| 协议演进僵化 | 能力协商依赖初始化握手，客户端与服务器版本强绑定 | 自描述请求（`_meta` 携带版本、身份、能力） |

### 1.1 关键变更总览

| 变更项 | 类型 | 说明 |
|--------|------|------|
| 无状态核心 | 架构变更 | 移除 initialize/initialized 握手、移除 `Mcp-Session-Id` |
| 自描述请求 | 协议变更 | 每个请求在 `_meta` 中携带协议版本、客户端身份与能力 |
| `server/discover` | 新增 RPC | 可选的按需能力发现，不调用也能处理任意请求 |
| Header 路由 | 新增 | `Mcp-Method` / `Mcp-Name` HTTP Header |
| 列表结果缓存 | 增强 | `tools/list` 等返回 `ttlMs` 与 `cacheScope` |
| 多轮往返请求（MRTR） | 替代 | 取代 elicitation/create、sampling/createMessage、roots/list |
| 授权加固 | 变更 | RFC 9207 `iss` 校验、CIMD 取代 DCR |
| Tasks 扩展 | 转正 | `io.modelcontextprotocol/tasks`，新增 `tasks/get`、`tasks/update` |
| Roots/Sampling/Logging | 弃用 | 12 个月弃用窗口，2027-07 后移除 |
| 旧 HTTP+SSE 传输 | 弃用 | 由流式 HTTP 传输取代 |

---

## 2. 无状态核心：理解架构转变

### 2.1 为什么无状态

1.x 中，服务器需要为每个客户端维护一个"会话"。会话标识（`Mcp-Session-Id`）被用于：

- 关联初始化协商出的协议版本与能力；
- 维护服务器主动推送（logging、resources updated）的通道上下文；
- 关联采样（sampling）与根目录（roots）等共享状态。

这在单体部署下尚可工作，但一旦服务器被部署到 Kubernetes 多副本、Serverless 或边缘网络后面，会话粘滞（session stickiness）就与"水平扩容"直接冲突：扩容后新实例没有旧会话，缩容会导致在线会话被截断，网关需要额外的粘滞会话支持，WAF/限流器也无法感知协议语义。

2.0 的选择是**让每个请求自包含（self-contained）**：协议版本、客户端身份、能力声明全部跟随请求行进，服务器不再需要记住"这个客户端是谁、能干什么"。

### 2.2 架构变化对比

```
MCP 1.x（有状态）                          MCP 2.0（无状态）
─────────────────────                      ─────────────────────
Client               Server                Client               Server
 │  initialize       │                     │  tools/list        │
 │──────────────────>│                     │  (_meta 自描述)     │
 │<──────────────────│  capabilities       │───────────────────>│
 │  initialized 通知  │                     │<───────────────────│ 任意实例可处理
 │──────────────────>│  （此后请求携带      │                     │
 │  tools/list       │   Mcp-Session-Id     │  tools/call        │
 │  (携带 Session)   │   并粘滞同实例)      │───────────────────>│
 │──────────────────>│                     │<───────────────────│
 │                   │                      │                    │
 │  负载均衡后请求无法                              │  轮询 LB 下请求可落任意实例
 │  路由到原实例 → 会话丢失                         │  无任何会话依赖
```

### 2.3 迁移决策要点

- **STDIO 本地传输**受无状态化影响最小：本地子进程天然实例唯一，但接口层行为仍需对齐 2.0（去掉握手、`_meta` 生效）。
- **HTTP 远程传输**受益最大：完会去掉"会话粘滞"约束后，可直接使用普通轮询负载均衡器，配合 Serverless 自动扩缩。
- 服务器内部若仍需要跨请求上下文（如用户身份、租户），应显式放入 `_meta` 或业务参数，**而不是依赖传输层会话**。

---

## 3. 移除握手与会话

### 3.1 迁移前（1.x）：三次握手 + 会话头

**Step 1 — initialize 请求：**

```http
POST /mcp HTTP/1.1
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 0,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {
      "roots": { "listChanged": true }
    },
    "clientInfo": { "name": "my-client", "version": "1.4.2" }
  }
}
```

**Step 2 — initialize 响应（携带会话头）：**

```http
HTTP/1.1 200 OK
Content-Type: application/json
Mcp-Session-Id: 6f8a1c2e-9b3d-4f5a-8c7e-2d4b6f8a0c1e

{
  "jsonrpc": "2.0",
  "id": 0,
  "result": {
    "protocolVersion": "2025-06-18",
    "capabilities": { "tools": {}, "logging": {} },
    "serverInfo": { "name": "my-server", "version": "2.3.0" }
  }
}
```

**Step 3 — initialized 通知：**

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized",
  "params": {}
}
```

**Step 4 — 之后每个请求都要带 `Mcp-Session-Id`：**

```http
POST /mcp HTTP/1.1
Content-Type: application/json
Mcp-Session-Id: 6f8a1c2e-9b3d-4f5a-8c7e-2d4b6f8a0c1e

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "search_users",
    "arguments": { "query": "alice" }
  }
}
```

### 3.2 迁移后（2.0）：直接请求

**首个请求即业务请求，无需握手：**

```http
POST /mcp HTTP/1.1
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "_meta": {
      "protocolVersion": "2026-07-28",
      "clientInfo": { "name": "my-client", "version": "2.0.0" },
      "capabilities": { "tools": {}, "resources": {} }
    },
    "name": "search_users",
    "arguments": { "query": "alice" }
  }
}
```

### 3.3 改造清单

| 对象 | 1.x 行为 | 2.0 行为 |
|------|---------|---------|
| 客户端启动序列 | `initialize` → `initialized` → 业务请求 | 直接发业务请求 |
| `Mcp-Session-Id` 响应头 | 必须 | 删除 |
| `Mcp-Session-Id` 请求头 | 每个请求携带 | 删除 |
| 协议版本/能力协商 | 握手时一次性完成 | 每个请求在 `_meta` 中自描述 |
| 协议版本降级 | 协商后取交集 | 服务器按 `_meta.protocolVersion` 选择响应格式 |

**FastMCP 4.0 迁移示意：**

```python
# 迁移前（FastMCP 3.x / MCP 1.x）
from fastmcp import FastMCP
mcp = FastMCP("my-server")

# 迁移后（FastMCP 4.0 / MCP 2.0）：
# FastMCP 4.0 默认运行无状态核心，无需任何握手代码
from fastmcp import FastMCP
mcp = FastMCP(
    "my-server",
    protocol_version="2026-07-28",  # 显式声明 2.0 版本
)
```

> 对存量客户端（仍在走 1.x 握手流）建议在入口做短期双协议兼容垫片：识别到 `initialize` 请求时走 1.x 逻辑，识别到 `_meta.protocolVersion` 时走 2.0 逻辑。垫片只用于过渡期，12 个月弃用窗口结束后移除。

---

## 4. 自描述请求（Self-Describing Requests）

### 4.1 `_meta` 结构

2.0 中，每个请求的 `params._meta` 是协议元数据的标准位置，典型字段：

```json
{
  "_meta": {
    "protocolVersion": "2026-07-28",
    "clientInfo": {
      "name": "my-agent",
      "version": "3.1.0"
    },
    "capabilities": {
      "tools": { "listChanged": true },
      "resources": {},
      "prompts": {}
    },
    "auth": {
      "issuer": "https://auth.example.com",
      "clientId": "mcp-client-42"
    }
  }
}
```

### 4.2 服务端如何消费

服务器应忽略 `_meta` 中未知字段（向前兼容），并基于声明内容决定行为：

```python
async def handle_tools_list(params: dict) -> dict:
    meta = params.get("_meta", {})
    version = meta.get("protocolVersion", "2025-06-18")

    tools = await load_tools()

    # 2.0 客户端支持缓存字段，1.x 客户端忽略
    if version.startswith("2026"):
        return {**tools, "ttlMs": 300_000, "cacheScope": "user"}
    return tools
```

### 4.3 可选能力发现：`server/discover`

无状态模型下，普通请求无需发现即可独立处理。但对于希望在调用前一次性拿到能力清单的客户端（如移动端 UI、受限网络环境），可调用新增的可选 RPC：

```json
{
  "jsonrpc": "2.0",
  "id": 10,
  "method": "server/discover",
  "params": {
    "_meta": {
      "protocolVersion": "2026-07-28",
      "clientInfo": { "name": "my-client", "version": "2.0.0" }
    }
  }
}
```

响应一次性返回协议版本、工具/资源/提示的元数据以及认证要求。**注意：`server/discover` 是纯优化，永远不要依赖它——任意请求都可以独立处理。**

---

## 5. Header 路由：让网关读懂 MCP

### 5.1 新增 HTTP Header

2.0 为 HTTP 传输新增两个请求头，网关、限流器、WAF 无需解析 JSON 请求体即可做路由决策：

| Header | 含义 | 示例值 |
|--------|------|--------|
| `Mcp-Method` | JSON-RPC 方法名 | `tools/list`、`tools/call`、`resources/read` |
| `Mcp-Name` | 服务器/客户端声明的服务名（路由目标） | `search-gateway` |

### 5.2 网关配置示例（Nginx）

```nginx
# 读操作直接路由到只读副本池，写操作路由到主池
map $http_mcp_method $mcp_pool {
    "~^resources/"            readonly_upstream;
    "~^tools/call"            writer_upstream;
    default                   readonly_upstream;
}

server {
    listen 443 ssl;
    server_name mcp.example.com;

    location /mcp {
        proxy_pass http://$mcp_pool;
        proxy_set_header Host $host;
        # 转发 MCP 路由头，供后端与可观测性使用
        proxy_set_header Mcp-Method $http_mcp_method;
        proxy_set_header Mcp-Name $http_mcp_name;
    }
}
```

### 5.3 限流与可观测性

```bash
# 基于 Mcp-Method 做差异化限流（示例：tools/call 限 100 rps，只读方法不限）
if ($http_mcp_method = "tools/call") {
    set $rate_limit_key "mcp:call:${remote_addr}";
}

# 访问日志直接记录方法名，无需解析 body
log_format mcp '$remote_addr $http_mcp_method $http_mcp_name '
               '$status $request_time';
access_log /var/log/nginx/mcp-access.log mcp;
```

网关层从此可以做到：**按方法路由到不同后端池、按方法差异化限流、按方法生成审计日志**，全部不触碰 JSON 请求体。

---

## 6. 响应缓存：List 结果可缓存

### 6.1 新增缓存字段

2.0 中以下方法的结果现在携带缓存元数据，允许网关或客户端缓存而不破坏协议语义：

| 方法 | 新增字段 |
|------|---------|
| `tools/list` | `ttlMs`、`cacheScope` |
| `prompts/list` | `ttlMs`、`cacheScope` |
| `resources/list` | `ttlMs`、`cacheScope` |
| `resources/read` | `ttlMs`、`cacheScope` |

字段语义：

- `ttlMs`：结果有效时长（毫秒），过期后缓存失效；
- `cacheScope`：缓存作用域，常见取值如 `user`（按用户隔离）、`global`（全局共享）、`tenant`（按租户隔离）。

### 6.2 服务端声明示例

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "search_users",
        "description": "在数据库中搜索用户",
        "inputSchema": { "type": "object", "properties": {} }
      }
    ],
    "ttlMs": 300000,
    "cacheScope": "user"
  }
}
```

### 6.3 客户端缓存策略

```python
import time

_cache = {}  # key -> (expires_at, payload)


async def list_tools_with_cache(client, cache_key: str = "tools") -> dict:
    now = int(time.time() * 1000)
    if cache_key in _cache and _cache[cache_key][0] > now:
        return _cache[cache_key][1]

    result = await client.request("tools/list", {})
    ttl = result.get("ttlMs", 0)
    if ttl > 0:
        _cache[cache_key] = (now + ttl, result)
    return result
```

> 变更提醒：缓存只应用于 `*_list` 与 `resources/read` 这类只读幂等请求；`tools/call` 可能有副作用，**不应下发 `ttlMs`，客户端也禁止默认缓存工具调用结果**。

---

## 7. MRTR：多轮往返请求

### 7.1 为什么需要 MRTR

1.x 中，服务器可以"主动"向客户端发起请求来获取缺失输入，主要包括三种场景：

| 1.x 服务器主动请求 | 用途 |
|---------------------|------|
| `elicitation/create` | 向用户收集缺失的输入 |
| `sampling/createMessage` | 请求客户端调用 LLM 生成内容 |
| `roots/list` | 查询客户端允许访问的文件系统根目录 |

这些"服务器 → 客户端"的反向调用在有状态会话中可行，但在无状态核心中，服务器无法反向推送请求。2.0 的解决方案是 **MRTR（Multi Round-Trip Requests）**：服务器在**响应**中声明"我还需要更多输入"，客户端**重试**时携带这些输入。

### 7.2 迁移前（1.x）：服务器主动请求

```json
// 1.x：服务器在工具执行中途反向发起 elicitation
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "elicitation/create",
  "params": {
    "request": {
      "uri": "https://api.example.com/users/123",
      "description": "需要用户确认记录 123 的归属部门"
    }
  }
}
```

### 7.3 迁移后（2.0）：`input_required` + `inputResponses`

**第一轮 — 服务器响应声明输入缺失：**

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "resultType": "input_required",
    "requests": [
      {
        "uri": "https://api.example.com/users/123",
        "description": "需要用户确认记录 123 的归属部门",
        "type": "text/plain"
      },
      {
        "uri": "mcp://analysis/summary",
        "description": "需要 LLM 生成的风险摘要",
        "type": "text/markdown"
      }
    ]
  }
}
```

**第二轮 — 客户端重试并携带响应：**

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "tools/call",
  "params": {
    "_meta": {
      "protocolVersion": "2026-07-28",
      "clientInfo": { "name": "my-client", "version": "2.0.0" }
    },
    "name": "audit_record",
    "arguments": { "recordId": "123" },
    "inputResponses": [
      {
        "uri": "https://api.example.com/users/123",
        "content": {
          "type": "text",
          "text": "该记录归属财务部，风险等级：中"
        }
      },
      {
        "uri": "mcp://analysis/summary",
        "content": {
          "type": "text",
          "text": "近期交易频次异常，建议人工复核"
        }
      }
    ]
  }
}
```

### 7.4 迁移要点

- 服务器从"主动发起请求"改为"在响应中声明 `resultType: 'input_required'` 并列出 `requests`"；
- 客户端收到 `input_required` 后，补齐输入并通过 `inputResponses` 重试**同一方法**；
- 流程上仍可多轮往返（每轮都是独立的无状态请求-响应），但没有反向推送通道；
- 原 `sampling/createMessage`、`roots/list` 的能力诉求统一走 MRTR 表达。

---

## 8. 授权迁移：DCR → CIMD

### 8.1 变更背景

1.x 时代使用 OAuth 2.0 动态客户端注册（**DCR**，Dynamic Client Registration）来让客户端动态注册到授权服务器。2.0 出于安全和运维透明性考虑，用 **CIMD（Client ID Metadata Documents，客户端 ID 元数据文档）** 取代 DCR，并引入 **RFC 9207 颁发者校验**：

| 项 | 1.x（DCR） | 2.0（CIMD + RFC 9207） |
|----|-----------|------------------------|
| 客户端注册 | 运行时向 AS 动态注册 | 部署前的静态元数据文档 |
| 客户端凭据 | 动态生成 | 与颁发者（issuer）绑定 |
| `iss` 校验 | 无强制 | 强制校验 RFC 9207 的 `iss` 参数，防授权码/令牌替换攻击 |
| 运维审计 | 注册记录分散 | 元数据文档可版本化、可审计 |

### 8.2 CIMD 元数据文档示例

```json
{
  "client_id": "mcp-client-42",
  "client_name": "内部审计 Agent",
  "redirect_uris": ["https://my-client.example.com/callback"],
  "grant_types": ["authorization_code", "client_credentials"],
  "token_endpoint_auth_method": "private_key_jwt",
  "issuer": "https://auth.example.com",
  "allowed_issuers": ["https://auth.example.com"],
  "scopes": ["mcp:tools:read", "mcp:tools:call"],
  "jwks_uri": "https://my-client.example.com/jwks"
}
```

### 8.3 服务端校验逻辑

```python
from urllib.parse import urlparse


def validate_issuer(token_claims: dict, expected_issuer: str) -> bool:
    """RFC 9207：校验令牌 iss 参数与预期颁发者完全一致，防替换攻击"""
    iss = token_claims.get("iss")
    if not iss:
        return False
    return urlparse(iss).netloc == urlparse(expected_issuer).netloc
```

### 8.4 迁移清单

1. 在授权服务器上发布 CIMD 文档，替换运行时 DCR 注册流程；
2. 所有验证码/令牌交换流程强制校验 `iss`（RFC 9207）；
3. 客户端凭据（如私钥）与颁发者强绑定，禁止跨颁发者复用；
4. 审计日志记录客户端 ID + 颁发者 + 作用域三元组，保证可追溯。

---

## 9. Tasks 扩展：从实验到标准

### 9.1 概述

MCP 2.0 将 Tasks 从实验特性转正为官方扩展 **`io.modelcontextprotocol/tasks`**，用于表达长时间运行、可分阶段提交的异步任务。新增标准方法：

| 方法 | 用途 |
|------|------|
| `tasks/get` | 查询任务状态与结果 |
| `tasks/update` | 更新任务元数据/取消任务 |

```json
// 查询任务状态
{
  "jsonrpc": "2.0",
  "id": 20,
  "method": "tasks/get",
  "params": {
    "_meta": { "protocolVersion": "2026-07-28" },
    "taskId": "task-batch-2026-08-001"
  }
}
```

```json
// 取消任务
{
  "jsonrpc": "2.0",
  "id": 21,
  "method": "tasks/update",
  "params": {
    "_meta": { "protocolVersion": "2026-07-28" },
    "taskId": "task-batch-2026-08-001",
    "status": "cancelled"
  }
}
```

### 9.2 迁移要点

- 若你的服务器自行实现了非标准的长任务协议，统一迁移到 `io.modelcontextprotocol/tasks` 扩展；
- 客户端通过扩展发现机制确认服务器是否支持 Tasks，再使用对应方法；
- 任务状态变化配合资源/通知机制（如果仍使用通知）或轮询 `tasks/get` 消费。

---

## 10. 弃用处理：Roots / Sampling / Logging

### 10.1 弃用时间表

MCP 2.0 宣布弃用以下能力，并给出 **12 个月弃用窗口**（约至 2027-07）：

| 能力 | 状态 | 替代方案 |
|------|------|---------|
| Roots（根目录） | 弃用 | MRTR（`input_required` 表达根目录输入需求） |
| Sampling（采样） | 弃用 | MRTR（`inputResponses` 携带 LLM 生成内容） |
| Logging（日志） | 弃用 | 使用标准可观测性（OTel 等）在传输层外收集 |
| HTTP+SSE 传输 | 弃用 | 流式 HTTP 传输（Text/Event-Stream 能力并入 HTTP） |

### 10.2 弃用窗口内策略

```
2026-07-28  发布弃用公告，1.x 能力仍可用（可发送 deprecation 警告）
    │
    ├─ 0-6 个月  双栈兼容：服务器同时支持 1.x 能力与新替代方案
    ├─ 6-12 个月 默认关闭 1.x 能力，仅对显式声明旧版本的客户端启用
    │
2027-07-28   弃用窗口结束，1.x 能力移除
```

### 10.3 服务器侧迁移示例

```python
# 迁移前（1.x）：依赖 roots 让服务器发现可访问目录
#   客户端在 initialize 中声明 roots 能力

# 迁移后（2.0）：用 MRTR 表达式——服务器在需要时声明输入需求
async def call_with_context(client, method: str, params: dict):
    result = await client.request(method, params)

    if result.get("resultType") == "input_required":
        inputs = await resolve_inputs(result["requests"])  # 从用户/LLM/本地解析
        params["inputResponses"] = inputs
        return await client.request(method, params)

    return result
```

---

## 11. SDK 迁移路径

### 11.1 SDK 支持状态

| SDK | 状态 | 版本动作 |
|-----|------|---------|
| TypeScript SDK | 已更新 | 2.0 原生支持，无状态核心 |
| Python SDK（含 FastMCP） | 已更新 | FastMCP 4.0 默认运行 2.0 无状态核心 |
| Go SDK | 已更新 | 2.0 原生支持 |
| C# SDK | 已更新 | 2.0 原生支持 |
| Rust SDK | Beta | 支持 2.0 核心与 MRTR |

### 11.2 升级步骤（以 TypeScript 为例）

```bash
# 升级 SDK 到 2.0
npm install @modelcontextprotocol/sdk@latest

# 服务器：移除手工 initialize 处理，改为声明版本（若使用高层 API，无需改动）
```

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

// MCP 2.0：无需握手代码，SDK 自动按无状态核心处理请求
const server = new McpServer({
  name: "my-server",
  version: "2.0.0",
  protocolVersion: "2026-07-28",
});

server.tool(
  "search_users",
  { query: z.string(), limit: z.number().optional() },
  async ({ query, limit }) => {
    return { content: [{ type: "text", text: `结果: ${query}` }] };
  }
);
```

### 11.3 升级注意事项

1. 先升级 **SDK 与依赖框架**（如 FastMCP 4.0），再做协议层面的行为改造；
2. 升级 SDK 后确认：握手代码是否被 SDK 自动移除、`_meta` 是否自动填充；
3. 对照本章节第 3 节改造清单逐项检查客户端与服务端两侧；
4. 优先在测试环境用双协议垫片跑通 4 类流量：1.x 客户端调用 2.0 服务器、2.0 客户端调用 1.x 服务器、双向 2.0、双向 1.x。

---

## 12. 测试策略

### 12.1 无状态核心回归测试

```python
import pytest
from mcp.client import Client  # 2.0 SDK


@pytest.mark.asyncio
async def test_no_handshake_required():
    """2.0：首个请求直接是业务请求，无需 initialize"""
    async with Client("http://mcp.example.com/mcp") as client:
        result = await client.request("tools/list", {})
        assert "tools" in result  # 成功即证明跳过握手可行


@pytest.mark.asyncio
async def test_request_is_self_describing():
    """2.0：请求必须携带 _meta"""
    async with Client("http://mcp.example.com/mcp") as client:
        result = await client.request("tools/list", {"_meta": {
            "protocolVersion": "2026-07-28",
            "clientInfo": {"name": "test", "version": "0.0.1"},
        }})
        assert result is not None


@pytest.mark.asyncio
async def test_no_session_header():
    """2.0：响应不再返回 Mcp-Session-Id"""
    async with Client("http://mcp.example.com/mcp") as client:
        headers = client.last_response.headers
        assert "mcp-session-id" not in headers
```

### 12.2 MRTR 流程测试

```python
@pytest.mark.asyncio
async def test_mrtr_input_required_roundtrip():
    """MRTR：input_required → 补齐输入 → 重试成功"""
    async with Client("http://mcp.example.com/mcp") as client:
        first = await client.request("tools/call", {
            "name": "audit_record", "arguments": {"recordId": "123"},
        })
        assert first.get("resultType") == "input_required"

        second = await client.request("tools/call", {
            "name": "audit_record", "arguments": {"recordId": "123"},
            "inputResponses": [
                {"uri": first["requests"][0]["uri"],
                 "content": {"type": "text", "text": "财务部"}}
            ],
        })
        assert second.get("resultType") != "input_required"
```

### 12.3 测试关注点清单

- [ ] 无会话头、无握手也能完成全部能力调用
- [ ] 同一请求可被负载均衡器路由到**不同实例**且结果一致
- [ ] `_meta` 中未知字段被服务器忽略（向前兼容）
- [ ] `tools/list` 返回 `ttlMs`/`cacheScope`，网关缓存后旧版本客户端仍兼容
- [ ] `Mcp-Method`/`Mcp-Name` 头正确透传，网关按方法路由生效
- [ ] MRTR 多轮往返中 `inputResponses` 与 `requests` 的 URI 一一对应
- [ ] `iss` 校验拦截伪造颁发者令牌
- [ ] 弃用能力在窗口内正确返回 deprecation 警告

---

## 13. 迁移时间线与风险

### 13.1 建议迁移节奏

| 阶段 | 时间 | 动作 |
|------|------|------|
| 评估 | 2026-08 ～ 2026-09 | 盘点依赖 1.x 特性的清单（握手、会话、sampling、roots、logging、DCR） |
| 双栈 | 2026-09 ～ 2026-12 | SDK 升级到 2.0，入口加双协议垫片，网关接入 Header 路由 |
| 主迁 | 2026-12 ～ 2027-03 | 全面切换到无状态核心，移除会话依赖，MRTR 落地 |
| 清理 | 2027-04 ～ 2027-07 | 移除 1.x 垫片与已弃用能力，关闭 DCR 端点 |
| 完成 | 2027-07-28 | 弃用窗口关闭，锁定 2.0-only |

### 13.2 主要风险与对策

| 风险 | 对策 |
|------|------|
| 会话粘滞逻辑散落在业务代码中 | 全局排查 `Mcp-Session-Id`、连接生命周期包裹类，改为显式上下文参数 |
| 依赖 sampling/roots 的旧服务器 | 用 MRTR 重写交互路径，在高频场景先行验证 |
| 网关/WAF 需升级支持 Header 路由 | 先透传头做观测，再逐步启用按方法路由/限流 |
| 存量 1.x 客户端无法立即升级 | 双协议垫片 + deprecation 警告，按计划推进对端升级 |

---

## 14. 生态支持状态

截至 2026-08，MCP 2.0 已获得主要生态伙伴支持：

| 生态方 | 支持情况 |
|--------|---------|
| AWS Bedrock | 提供 MCP 2.0 无状态服务器托管与代理支持 |
| Cloudflare Agents SDK | 原生支持 2.0 无状态核心与 Header 路由 |
| Microsoft Foundry | 集成 MCP 2.0 客户端与服务端 |
| FastMCP（Python） | 4.0 版本默认启用 2.0 无状态核心 |
| Anthropic / 官方 SDK | TypeScript、Python、Go、C# 已更新，Rust 处于 Beta |

**行动建议**：即使当前未规划立即升级，也建议在 2026 年内完成以下事项——升级 SDK 到 2.x 兼容线、在网关层透传 `Mcp-Method`/`Mcp-Name` 头采集观测数据、将新服务器按 2.0 无状态模型编写，为 12 个月弃用窗口收口做好准备。

---

## 15. 总结

MCP 2.0 迁移的本质是一次**架构思维转变**：

1. **从"连接"到"请求"**：握手与会话取消后，协议不再管理连接生命周期，只处理请求；
2. **从"协调"到"自描述"**：`_meta` 让每个请求独立成文，协议版本与能力随请求而行；
3. **从"反向通道"到"多轮重试"**：MRTR 用标准请求-响应循环替代服务器主动推送；
4. **从"动态注册"到"静态文档"**：CIMD + RFC 9207 让授权可审计、可防替换攻击；
5. **从"粘滞"到"可水平扩展"**：任何请求可落在任何实例之上，负载均衡回归朴素实现。

对开发者而言，最直接的收益是：**服务器终于可以像普通无状态 HTTP 服务一样，被 Nginx 轮询、被 K8s 水平扩缩、被 Serverless 无感调度**——这对于 MCP 真正成为"AI 时代的 HTTP"至关重要。

---

## 参考链接

- [MCP 协议原理与核心概念](./01-protocol-concepts.md)
- [MCP Server 开发实战](./02-server-development.md)
- [MCP Client 集成与生产部署](./03-client-integration.md)
- [MCP 官方文档](https://modelcontextprotocol.io)
- [MCP GitHub](https://github.com/modelcontextprotocol)
- [RFC 9207: OAuth 2.0 Authorization Server Issuer Identification](https://www.rfc-editor.org/rfc/rfc9207)
- [FastMCP 文档](https://gofastmcp.com)