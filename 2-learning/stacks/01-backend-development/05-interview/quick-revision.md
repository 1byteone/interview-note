# 速记版 — 后端开发面试高频知识点

> 面试前 10 分钟快速过一遍，确保不丢基础分。

## RESTful API 规范速查

| 方法 | 操作 | 幂等 | 安全 | 状态码 |
|------|------|------|------|--------|
| GET | 查询资源 | 是 | 是 | 200 |
| POST | 创建资源 | 否 | 否 | 201 |
| PUT | 全量更新 | 是 | 否 | 200 |
| PATCH | 部分更新 | 幂等性取决于实现 | 否 | 200 |
| DELETE | 删除资源 | 是 | 否 | 204 |

**版本管理**：URL 路径版本（`/api/v1/`）最常用，破坏性变更加版本号，6-12 个月过渡期后废弃旧版本。

**统一响应格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1712345678000,
  "traceId": "a1b2c3d4e5f6"
}
```

**分页规范**：`page=1&size=20&sort=createTime,desc`，响应带 `totalElements / totalPages / first / last`。

## HTTP 状态码速查

- **2xx 成功**：200 OK / 201 Created / 204 No Content
- **4xx 客户端错误**：400 Bad Request / 401 Unauthorized / 403 Forbidden / 404 Not Found / 409 Conflict / 429 Too Many Requests
- **5xx 服务端错误**：500 Internal Server Error / 502 Bad Gateway / 503 Service Unavailable / 504 Gateway Timeout

## API 安全要点

- **认证**：JWT（无状态，适合分布式）vs Session（有状态，适合单体）
- **授权**：RBAC（用户 → 角色 → 权限）
- **限流**：令牌桶（允许突发）vs 漏桶（平滑处理）vs 滑动窗口（精确统计）
- **防攻击**：参数化查询（防 SQL 注入）、输出转义（防 XSS）、CSRF Token / SameSite Cookie、精确配置 CORS Origin

## 三层架构速记

```
Controller（接收请求，返回响应）→ Service（业务编排，事务管理）→ Repository（数据访问）
```

**DTO/VO/PO 区分**：
- PO（Entity）：与数据库表一一对应
- DTO：跨层传输，按需组装
- VO：返回给前端，可能组合多数据源

**各层禁止事项**：
- Controller 不能写业务逻辑、不能直接调 Repository
- Service 不能暴露 Entity 给 Controller
- Repository 不能包含业务逻辑

## DDD 战术设计速记

- **Entity**：有唯一标识、会变化、有业务行为
- **Value Object**：无标识、不可变、描述属性（如 Money, Address）
- **Aggregate**：聚合根+内部实体，外部只能通过聚合根访问
- **Repository**：聚合的集合式存储接口
- **Domain Service**：跨实体的领域逻辑

## 架构演进路线

```
单体 → SOA → 微服务
         ↑
       DDD 建模（决定边界）
```

**何时拆微服务**（至少满足 2-3 条）：
- 团队 20+ 人，代码冲突严重
- 不同模块扩缩容需求差异大
- 需要独立发布节奏
- 技术栈异构需求

## 常见面试题一句话回答

| 问题 | 一句话回答 |
|------|------------|
| 为什么用微服务？ | 独立部署、独立扩容、故障隔离、团队自治 |
| 什么时候不该用微服务？ | 团队小、业务未验证、不需要独立扩缩容时 |
| RESTful 是什么？ | 资源导向的 API 设计风格，URL 是名词，方法是动词，状态码表达结果 |
| JWT 和 Session 选哪个？ | 分布式系统选 JWT（无状态），传统 Web 应用选 Session（可主动失效）|
| 三层架构为什么分三层？ | 关注点分离：Controller 处理请求、Service 处理业务、Repository 处理数据 |
| DDD 和三层架构的区别？ | DDD 关注业务建模，三层架构关注技术分层；DDD 用富血模型，三层架构常用贫血模型 |