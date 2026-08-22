# 服务发现与配置中心 — Nacos

## 从 "手动配置 IP" 到 "自动发现"

没有服务发现时，服务 A 调用服务 B 需要在配置里写死 `http://192.168.1.100:8080`。一旦 B 扩容、缩容或迁移，A 就得改配置重启。**服务发现**让服务动态感知彼此的网络位置，彻底解决这个问题。

---

## Nacos 服务注册与发现

Nacos 的全称是 **Naming and Configuration Service**，同时承担服务发现和配置管理两个角色。

### 注册流程

```
服务提供者启动
    → 向 Nacos Server 发送注册请求（IP + Port + 服务名）
    → Nacos 写入注册表
    → 返回成功
```

示例配置（`bootstrap.yml`）：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.1.10:8848
        namespace: dev
```

### 心跳机制

服务提供者每 **5 秒** 发送一次心跳（`PUT /instance/beat`）。Nacos 在 **15 秒** 内未收到心跳则标记为不健康，**30 秒** 彻底剔除。

### 订阅/通知

服务消费者启动时订阅目标服务；Nacos 在服务列表变更时通过 **UDP push** 或 **长轮询** 推送给消费者，消费者本地缓存服务列表。

---

## Nacos 配置中心

### 热更新原理

- 客户端通过 **长轮询（Long Polling）** 监听配置变更
- 服务端配置修改后，客户端在 30 秒内收到变更通知
- 配合 `@RefreshScope` 注解，Bean 属性自动刷新

```java
@RefreshScope
@Component
public class OrderConfig {
    @Value("${order.timeout:5000}")
    private int timeout;
}
```

### 长轮询 vs 短轮询

| 机制 | 描述 | 延迟 |
|------|------|------|
| 短轮询 | 客户端每隔几秒主动拉取 | 取决于间隔 |
| 长轮询 | 客户端发起请求，服务端有变更才返回，否则 hold 30 秒 | 即时 |

### 灰度发布

Nacos 支持配置的 **Beta 发布**：指定一部分 IP 先应用新配置，验证无误后全量发布，降低配置错误风险。

---

## 命名空间环境隔离

```yaml
spring:
  cloud:
    nacos:
      config:
        namespace: prod   # dev / test / prod
      discovery:
        namespace: prod
```

不同命名空间的服务和配置完全隔离，实现 **dev / test / prod** 环境分离。同一个命名空间内的服务可以互相发现。

---

## 对比：Nacos vs Eureka vs Consul vs Zookeeper

| 特性 | Nacos | Eureka | Consul | Zookeeper |
|------|-------|--------|--------|-----------|
| **CAP 模型** | AP + CP 可切换 | AP | CP | CP |
| **健康检查** | 心跳 + 主动探测 | 心跳 | 多种协议 | 心跳 |
| **配置中心** | 内置 | 无 | 内置 | 可做（弱） |
| **控制台** | 功能丰富 | 简单 | 功能丰富 | 无 |
| **一致性协议** | Raft / Distro | 无 | Raft | Zab |
| **K8s 集成** | 支持 | 停更 | 支持 | 支持 |
| **社区活跃度** | 高 | 低（已停更） | 高 | 高 |

### CAP 解读

- **Nacos**：默认 AP（Distro 协议，保证可用性 + 最终一致性），可切换 CP（Raft 协议，保证强一致性）
- **Eureka 2.x 已停更**，不建议新项目使用
- **Consul**：强一致性（CP），适合对一致性要求高的场景
- **Zookeeper**：强一致性（CP），但服务发现场景下更适合 AP

---

## 健康检查机制

### 临时实例（默认）

- 注册后 **临时存活**，心跳断了就剔除
- 适合 **K8s Pod** 或随时扩缩容的微服务
- 实例数 = 当前活跃实例

### 持久实例

- 注册后 **永久存在**，即使心跳断了也不剔除，仅标记为不健康
- 适合 **数据库、固定物理机** 等不会随意销毁的节点
- 需要手动注销或通过 API 删除

```yaml
spring:
  cloud:
    nacos:
      discovery:
        ephemeral: false   # 持久实例
```

---

## 关键要点速记

| 问题 | 答案 |
|------|------|
| Nacos 心跳间隔 | 5 秒 |
| 不健康判定 | 15 秒无心跳 |
| 实例剔除时间 | 30 秒 |
| 配置刷新注解 | `@RefreshScope` |
| 配置推送机制 | 长轮询（最长 hold 30 秒） |
| 默认 CAP | AP |
| 命名空间作用 | 环境隔离（dev / test / prod） |