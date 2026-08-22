# 🔧 yu-rpc 自研 RPC 框架 — 完整源码级剖析

> 从零到一理解 RPC 原理的最佳教学项目

---

## 一、项目定位

| 维度 | 信息 |
|------|------|
| **仓库** | [github.com/liyupi/yu-rpc](https://github.com/liyupi/yu-rpc) |
| **Stars** | 603 ⭐ / 153 Fork |
| **Java 版本** | Java 8 |
| **Spring Boot** | 2.6.13 |
| **核心依赖** | Vert.x 4.5.1 / jetcd 0.7.7 / Curator 5.6.0 / Kryo 5.6.0 |
| **教学价值** | ⭐⭐⭐⭐⭐ 面试"手写 RPC"类问题的完美参考 |

---

## 二、模块结构

```
yu-rpc/
├── yu-rpc-core/                    ← 框架核心（全功能版）
├── yu-rpc-easy/                    ← 简化版（HTTP + JDK 序列化 + 本地注册）
├── yu-rpc-spring-boot-starter/     ← Spring Boot 注解驱动启动器
├── example-common/                 ← 共享 API：User + UserService
├── example-provider/               ← 纯 Java Provider 示例
├── example-consumer/               ← 纯 Java Consumer 示例
├── example-springboot-provider/    ← Spring Boot Provider 示例
└── example-springboot-consumer/    ← Spring Boot Consumer 示例
```

---

## 三、自定义 TCP 协议设计（Dubbo 风格）

### 3.1 协议头：17 字节定长

```
┌──────┬──────┬──────────┬──────────┬────────┬────────────┬────────────┐
│ magic│ ver  │serializer│   type   │ status │  requestId │ bodyLength │
│ 1B   │ 1B   │   1B     │   1B     │  1B    │    8B      │    4B      │
│ 0x1  │ 0x1  │ 0-3      │ 0-3      │20/40/50│  Snowflake │  body len  │
└──────┴──────┴──────────┴──────────┴────────┴────────────┴────────────┘
                           17 bytes total
```

### 3.2 字段说明

| 字段 | 偏移 | 大小 | 说明 |
|------|------|------|------|
| **magic** | 0 | 1B | 魔数 `0x1`，校验协议合法性 |
| **version** | 1 | 1B | 协议版本号 `0x1` |
| **serializer** | 2 | 1B | 序列化器类型：0=JDK, 1=JSON, 2=Kryo, 3=Hessian |
| **type** | 3 | 1B | 消息类型：0=REQUEST, 1=RESPONSE, 2=HEART_BEAT, 3=OTHERS |
| **status** | 4 | 1B | 状态码：20=OK, 40=BAD_REQUEST, 50=BAD_RESPONSE |
| **requestId** | 5 | 8B | 雪花算法生成的请求 ID |
| **bodyLength** | 13 | 4B | 消息体长度（用于解决粘包/半包） |
| **body** | 17 | N | 序列化后的 RpcRequest/RpcResponse |

---

## 四、序列化器 — SPI 可插拔

### 4.1 四种实现

| 序列化器 | SPI Key | 原理 | 性能 |
|---------|---------|------|------|
| **JdkSerializer** | `jdk` | ObjectOutputStream/ObjectInputStream | ⭐⭐ 最慢但兼容性最好 |
| **JsonSerializer** | `json` | Jackson ObjectMapper | ⭐⭐⭐ 调试友好 |
| **KryoSerializer** | `kryo` | Kryo 5.6.0，ThreadLocal 包装 | ⭐⭐⭐⭐ 速度快 |
| **HessianSerializer** | `hessian` | Hessian 4.0.66 | ⭐⭐⭐⭐ 跨语言 |

### 4.2 自研 SPI 加载器

```
扫描路径（后者覆盖前者）:
1. META-INF/rpc/system/   — 框架内置实现
2. META-INF/rpc/custom/   — 用户自定义扩展

文件格式:
kryo=com.yupi.yurpc.serializer.KryoSerializer
json=com.yupi.yurpc.serializer.JsonSerializer
```

**与 Java SPI 的区别**: 支持 key→class 映射，单例缓存，按需加载。

---

## 五、服务注册与发现

### 5.1 Etcd 注册中心（默认）

```
Key 路径: /rpc/{serviceName}:{serviceVersion}/{host}:{port}
Value: JSON(ServiceMetaInfo)
TTL: 30 秒租约（自动续约）
心跳: 每 10 秒 CronUtil 重新注册
```

**发现流程**:
1. Consumer 按前缀 `/rpc/{serviceKey}/` 搜索
2. 结果缓存在 `RegistryServiceMultiCache`
3. 对每个节点 `watch()`，DELETE 事件触发缓存清理

### 5.2 ZooKeeper 注册中心

```
使用 Curator ServiceDiscovery
临时节点 — 断开自动删除，无需心跳
CuratorCache 监听节点变化 → 清理缓存
```

### 5.3 对比

| 维度 | Etcd | ZooKeeper |
|------|------|-----------|
| 一致性 | 强一致(Raft) | 强一致(ZAB) |
| 心跳 | 需要（30s 租约） | 不需要（临时节点） |
| Watch | 前缀 Watch | CuratorCache |
| 依赖 | jetcd 0.7.7 | Curator 5.6.0 |

---

## 六、负载均衡策略

### 6.1 一致性哈希（重点）

```java
// TreeMap 环形空间，100 个虚拟节点/真实节点
TreeMap<Integer, ServiceMetaInfo> ring = new TreeMap<>();

for (ServiceMetaInfo node : serviceMetaInfoList) {
    for (int i = 0; i < 100; i++) {
        String hashKey = node.getServiceAddress() + "#" + i;
        ring.put(hashKey.hashCode(), node);
    }
}

// 查找：顺时针找到第一个 >= hash 的节点
Map.Entry<Integer, ServiceMetaInfo> entry = ring.ceilingEntry(hash);
if (entry == null) entry = ring.firstEntry(); // 环形回绕
return entry.getValue();
```

**面试追问点**:
- 为什么用虚拟节点？→ 保证节点均匀分布
- 为什么不用 MurmurHash？→ 教学简化，实际生产必须用
- TreeMap 每次 select 都重建？→ 教学简化，生产应用缓存

---

## 七、网络通信 — Vert.x TCP

### 7.1 粘包/半包处理

```java
// TcpBufferHandlerWrapper — 装饰器模式
// 使用 Vert.x RecordParser:
// 1. 先读固定 17 字节 Header
// 2. 从 Header 中提取 bodyLength
// 3. 切换到 fixedSizeMode(bodyLength) 读完整消息体
// 4. 组装完整帧交给 Handler 处理
// 5. 重置回 Header 模式
```

### 7.2 Consumer 调用链（完整流程）

```
ServiceProxy.invoke()
  │
  ├─ 1. 构建 RpcRequest（接口名、方法名、参数类型、参数值）
  │
  ├─ 2. RegistryFactory → registry.discover(serviceKey)
  │     └─ 缓存: RegistryServiceMultiCache
  │
  ├─ 3. LoadBalancer.select(requestParams, serviceList)
  │     └─ 三种策略: random / roundRobin / consistentHash
  │
  ├─ 4. RetryStrategy.doRetry(() → VertxTcpClient.doRequest())
  │     └─ 两种策略: no / fixedInterval(3s, 3次)
  │
  ├─ 5. 异常时 → TolerantStrategy.doTolerant()
  │     └─ failFast(默认) / failSafe / failOver(TODO) / failBack(TODO)
  │
  └─ 6. 返回 RpcResponse.getData()
```

---

## 八、Spring Boot Starter 注解驱动

### 8.1 三个核心注解

```java
@EnableRpc(needServer = true)          // 启用 RPC，是否启动 Server
@RpcService(interfaceClass = UserService.class)  // 标记 Provider 实现
@RpcReference(loadBalancer = "roundRobin")       // 注入 Consumer 代理
```

### 8.2 BeanPostProcessor 自动注册

- **RpcProviderBootstrap**: 扫描 `@RpcService` 注解 → 注册到 LocalRegistry + Remote Registry
- **RpcConsumerBootstrap**: 扫描 `@RpcReference` 字段 → 生成 JDK 动态代理 → 注入

---

## 九、面试高频问题（附答案要点）

### Q1: 为什么选 TCP 而不是 HTTP？
> HTTP 是文本协议，每次请求都要携带完整 Header，TCP 可以自定义精简协议（17字节 Header），减少网络开销。而且 TCP 支持长连接复用，HTTP/1.1 虽然也支持 keep-alive，但 TCP 更灵活。

### Q2: 如何解决粘包半包？
> 自定义协议使用固定长度 Header（17字节），Header 中包含 bodyLength 字段。接收端先读固定 17 字节 Header，再根据 bodyLength 读取完整消息体。Vert.x 的 RecordParser 天然支持这种模式。

### Q3: 为什么不直接用 Java SPI？
> Java SPI 只能按接口全量加载，不支持 key→class 映射。自研 SPI 支持按名称获取具体实现，支持单例缓存，支持 system/custom 两级覆盖。

### Q4: Etcd 和 ZooKeeper 做注册中心的区别？
> Etcd 基于 Raft 协议，强一致；需要租约+心跳续约。ZooKeeper 基于 ZAB 协议，也是强一致；临时节点断开自动删除，无需心跳。Etcd 更轻量，K8s 生态首选；ZK 在 Java 生态（Dubbo/Hadoop）更成熟。

### Q5: 一致性哈希的虚拟节点作用？
> 如果节点数少，直接 hash 到环上会导致分布不均。每个真实节点映射 100 个虚拟节点，使得环上分布更均匀，负载更均衡。

---

*此文档可作为面试中"手写 RPC 框架"类问题的完整知识储备*
