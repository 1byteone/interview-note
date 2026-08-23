# 🎯 yu-rpc 面试逐题精讲 — 从浅入深 15 题

> 每题按「面试官意图 → 标准答案 → 追问准备 → 代码定位」四层展开

---

## 题目 1/15：先聊基础 — 什么是 RPC？

**面试官意图**: 看你能不能用一句话讲清楚，再展开讲细节

**标准答案**:
> RPC (Remote Procedure Call) 是远程过程调用，让调用远程服务像调用本地方法一样。核心要解决三个问题：
> 1. **通信** — 网络传输（TCP/HTTP）
> 2. **序列化** — 对象↔字节流转换
> 3. **寻址** — 怎么找到目标服务（注册中心）

**追问准备**:
- RPC vs HTTP API? → RPC 是协议级抽象，HTTP API 是 RESTful 风格，RPC 更强调类型安全和调用透明
- Dubbo/gRPC 区别? → Dubbo 基于 TCP 自定义协议，gRPC 基于 HTTP/2 + Protobuf

---

## 题目 2/15：你的 RPC 整体架构是什么？

**面试官意图**: 考察全局视野，能否画出架构图

**标准答案**:

```
Consumer ──→ [动态代理] ──→ [路由(注册中心发现+负载均衡)]
                                    │
                                    ▼
                              [序列化+编码] ──→ TCP ──→ [解码+反序列化]
                                                            │
                                                            ▼
                                                    [反射调用] ──→ Provider
```

**关键模块**:
| 模块 | 职责 | yu-rpc 实现 |
|------|------|------------|
| 动态代理 | 将方法调用转为网络请求 | JDK Proxy + ServiceProxy |
| 序列化 | Java 对象 ↔ 字节流 | 4种: JDK/JSON/Kryo/Hessian |
| 网络通信 | 可靠传输 | Vert.x TCP + 自定义协议 |
| 注册中心 | 服务寻址 | Etcd / ZooKeeper |
| 负载均衡 | 选择目标节点 | Random / RoundRobin / ConsistentHash |
| 容错 | 故障处理 | Retry(重试) + Tolerant(熔断) |

---

## 题目 3/15：为什么自定义 TCP 协议而不直接用 HTTP？

**面试官意图**: 考察协议设计能力和性能意识

**标准答案**:
> HTTP/1.1 是文本协议，每次请求都要携带完整 Header（几百字节），对于 RPC 这种高频内部调用开销大。自定义 TCP 协议只用 **17 字节 Header**，包含魔数、版本、序列化类型、消息类型、请求ID、消息体长度，极致精简。
>
> 而且 TCP 天然支持长连接复用，不需要每次请求重新建立连接。

**追问 — 17 字节怎么设计的**:
```
magic(1) + version(1) + serializer(1) + type(1) + status(1) + requestId(8) + bodyLength(4) = 17
```

**追问 — 每个字段为什么存在**:
- **magic**: 校验是否是合法的 RPC 消息（防止误解析其他 TCP 流）
- **version**: 后续协议升级兼容
- **serializer**: 告诉接收端用哪个反序列化器
- **type**: 区分请求/响应/心跳
- **requestId**: 匹配请求和响应（异步场景必须）
- **bodyLength**: 解决 TCP 粘包半包

---

## 题目 4/15：TCP 粘包半包怎么解决？

**面试官意图**: 经典网络编程问题，必问

**标准答案**:
> 粘包半包是 TCP 的本质特性 — TCP 是字节流协议，没有消息边界。解决方案有三种：
> 1. **固定长度** — 简单但浪费
> 2. **分隔符** — 适合文本协议
> 3. **长度字段** — 最常用，本框架采用这种
>
> yu-rpc 在 Header 的第 13-16 字节存放 bodyLength。接收端用 Vert.x 的 RecordParser：
> ```
> 1. 先固定读 17 字节 Header
> 2. 从 Header 解析 bodyLength
> 3. 切换 fixedSizeMode(bodyLength) 读完整消息体
> 4. 组装完整帧 → 交给 Handler
> 5. 重置回 17 字节 Header 模式
> ```

**追问 — 为什么用 RecordParser**:
> RecordParser 是 Vert.x 提供的半包处理器，内部维护了一个状态机，自动处理缓冲区的拼接和切割，比手写 Buffer 处理更可靠。

---

## 题目 5/15：4 种序列化器怎么选？各有什么优缺点？

**面试官意图**: 考察对序列化原理的理解

**标准答案**:

| 序列化器 | 原理 | 优点 | 缺点 | 适用场景 |
|---------|------|------|------|---------|
| **JDK** | ObjectOutputStream | Java 原生，零依赖 | 慢、体积大、不跨语言 | 测试验证 |
| **JSON** | Jackson | 可读性好、跨语言 | 体积较大、性能一般 | 调试、对外 API |
| **Kryo** | 自定义二进制 | 体积小、速度快 | 不跨语言、非线程安全 | Java 内部高性能场景 |
| **Hessian** | 二进制协议 | 跨语言、性能好 | 实现复杂 | 跨语言 RPC |

**代码定位 — Kryo 的线程安全问题**:
```java
// Kryo 不是线程安全的，用 ThreadLocal 包装
private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = 
    ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 不需要预注册类
        return kryo;
    });
```

---

## 题目 6/15：你这个自研 SPI 和 Java SPI 有什么区别？

**面试官意图**: 考察扩展机制设计能力

**标准答案**:

| 特性 | Java SPI | 自研 SPI |
|------|---------|---------|
| 加载方式 | `ServiceLoader.load()` 全量加载 | `SpiLoader.load(key)` 按 key 加载 |
| 查找方式 | 遍历所有实现 | key→class 映射，O(1) 查找 |
| 缓存 | 无 | 单例缓存 (`instanceCache`) |
| 扩展性 | 一个目录 | 两级目录（system 覆盖 custom） |

**核心代码**:
```java
// 两级扫描，后者覆盖前者
1. META-INF/rpc/system/   → 框架内置 (jdk, json, kryo, hessian)
2. META-INF/rpc/custom/   → 用户自定义

// 文件格式：key=全限定类名
kryo=com.yupi.yurpc.serializer.KryoSerializer
```

**追问 — 为什么需要两级目录**:
> 用户想自定义序列化器，不想改框架源码。放在 custom 目录，框架内置的 system 不会被覆盖，但用户可以添加新的 key 或覆盖 system 的实现。

---

## 题目 7/15：Etcd 注册中心的心跳机制怎么设计的？

**面试官意图**: 考察对服务治理的理解

**标准答案**:
> yu-rpc 的 Etcd 注册用了 **租约(Lease) + 心跳续约** 双重保障：
>
> 1. **注册时**: 创建 30 秒租约 (`leaseClient.grant(30)`)，Put 时绑定 LeaseId
> 2. **心跳**: 每 10 秒 CronUtil 触发，重新 Register（本质上是续约）
> 3. **销毁时**: 主动删除所有注册的 Key + 关闭客户端
>
> 如果 Provider 崩溃，10 秒内没续约 → Etcd 租约过期 → Key 自动删除 → Consumer Watch 收到 DELETE 事件 → 清除缓存 → 不再路由到故障节点。

**对比 ZooKeeper**:
> ZK 用临时节点(Ephemeral Node)，断开连接自动删除，不需要心跳。更简洁但依赖会话机制。

---

## 题目 8/15：一致性哈希原理？为什么用虚拟节点？

**面试官意图**: 负载均衡的经典题

**标准答案**:
> 一致性哈希把节点映射到一个 0~2^32 的环形空间。请求也 hash 到环上，顺时针找到第一个节点。
>
> 问题是：如果只有 3 个真实节点，hash 后在环上分布很不均匀，某些节点承担大部分流量。
>
> **虚拟节点**：每个真实节点创建 100 个虚拟节点（hash(地址#序号)），均匀散布在环上，保证负载均衡。

**追问 — 缺点**:
> yu-rpc 每次 select 都重建 TreeMap 环，实际生产应该缓存。另外用了 `String.hashCode()` 而不是 MurmurHash3，hash 分布不够均匀。

---

## 题目 9/15：动态代理怎么实现的？Consumer 怎么调用远端方法？

**面试官意图**: 考察 RPC 的"透明调用"核心

**标准答案**:
> 使用 JDK 动态代理。Consumer 拿到的 `UserService` 实际上是一个 Proxy：
> ```java
> Proxy.newProxyInstance(classLoader, new Class[]{serviceClass}, new ServiceProxy());
> ```
>
> `ServiceProxy.invoke()` 做了这些事：
> 1. 构建 `RpcRequest`（接口名、方法名、参数类型、参数值）
> 2. 从注册中心发现服务地址
> 3. 负载均衡选一个节点
> 4. 序列化 + TCP 发送
> 5. 等待响应 + 反序列化
> 6. 返回结果

**追问 — 如果返回异常呢**:
> RpcResponse 里有个 `exception` 字段，Provider 端 catch 后序列化回去，Consumer 端反序列化后重新抛出。

---

## 题目 10/15：Provider 端怎么接收请求并反射调用？

**面试官意图**: 考察服务端处理流程

**标准答案**:
> 1. `VertxTcpServer` 监听端口，收到连接
> 2. `TcpServerHandler` 解码 ProtocolMessage → 得到 RpcRequest
> 3. 拼接 `serviceName:version` 作为 key，从 `LocalRegistry` 查找实现类
> 4. 反射调用: `method.invoke(implClass.newInstance(), args)`
> 5. 封装 `RpcResponse` → 编码 → TCP 返回

**追问 — 为什么用 `newInstance()` 而不是 Spring Bean**:
> 这是教学简化。实际生产应该注入 Spring 容器中的 Bean，支持 AOP、依赖注入等。

---

## 题目 11/15：重试和容错策略怎么实现的？

**面试官意图**: 考察高可用设计

**标准答案**:
> **重试 (RetryStrategy)**: 基于 guava-retrying 库
> - `NoRetry`: 直接调用
> - `FixedInterval`: 固定间隔 3 秒，最多 3 次
>
> **容错 (TolerantStrategy)**: 重试失败后
> - `FailFast` (默认): 直接抛异常，快速失败
> - `FailSafe`: 吞掉异常，返回空 RpcResponse
> - `FailOver`: 转发到其他节点（TODO）
> - `FailBack`: 返回默认值（TODO）

**追问 — Dubbo 的容错策略**:
> Dubbo 有更多策略：Failover（自动切换节点重试）、Forking（并行调多个节点取最快）、Broadcast（广播所有节点）。yu-rpc 的 FailOver/FailBack 还是 TODO。

---

## 题目 12/15：Spring Boot Starter 怎么设计的？

**面试官意图**: 考察框架集成能力

**标准答案**:
> 三个核心注解：
> ```java
> @EnableRpc(needServer = true)           // 主开关
> @RpcService(interfaceClass = X.class)   // Provider 标记
> @RpcReference(loadBalancer = "random")  // Consumer 注入
> ```
>
> 底层通过 `BeanPostProcessor` 自动处理：
> - `RpcProviderBootstrap`: 扫描所有 `@RpcService` Bean → 注册到注册中心
> - `RpcConsumerBootstrap`: 扫描所有 `@RpcReference` 字段 → 生成代理对象 → 注入

**追问 — @EnableRpc 怎么生效的**:
> `@EnableRpc` 内部 `@Import({RpcInitBootstrap, ...})`，`RpcInitBootstrap` 是 `ImportBeanDefinitionRegistrar`，在 Spring 启动时读取 `needServer` 属性，决定是否启动 TCP Server。

---

## 题目 13/15：和 Dubbo 对比，你这个框架差在哪？

**面试官意图**: 考察自我认知和深度

**标准答案**:

| 维度 | yu-rpc | Dubbo |
|------|--------|-------|
| 协议 | 自定义 TCP | Dubbo 协议 ( TCP ) / Triple (HTTP/2) |
| 序列化 | 4种 | 5种 (Hessian2默认 + JSON + Protobuf等) |
| 注册中心 | Etcd/ZK | ZK/Nacos/Consul/Redis 等 |
| 负载均衡 | 3种 | 5种 (加 LeastActive, ConsistentHash) |
| 连接池 | 每次创建新连接 (TODO) | Netty 连接池复用 |
| 异步 | CompletableFuture 阻塞等待 | 原生 Future + Callback |
| Filter 链 | 无 | 完整的 Filter/SPI 扩展体系 |
| 泛化调用 | 无 | 支持 |
| 热部署 | 无 | 支持 |

**关键差距**: 连接池、Filter 链、异步非阻塞、泛化调用、线程模型。

---

## 题目 14/15：如果让你继续完善这个框架，你会做什么？

**面试官意图**: 考察架构演进思维

**标准答案** (按优先级):
1. **连接池** — 每次请求创建新 TCP 连接太浪费，用 Netty ChannelPool 复用
2. **Filter 链** — 参考 Dubbo 的 SPI + Filter 机制，支持日志、鉴权、限流等扩展
3. **异步非阻塞** — 当前 VertxTcpClient 用 `CompletableFuture.get()` 阻塞等待，应该改为回调
4. **FailOver 容错** — 重试失败后自动切换到其他节点
5. **注册中心推拉结合** — 当前只用 Watch (推送)，应该加定时拉取兜底
6. **管理后台** — 可视化查看服务列表、调用统计、健康状态

---

## 题目 15/15（压轴）：从这个项目你学到了什么？

**面试官意图**: 考察总结能力和技术深度

**标准答案**:
> 三个层面的收获：
>
> **技术层面**: 理解了 RPC 的完整生命周期 — 从 Consumer 发起调用，到代理、路由、序列化、网络传输、解码、反射调用、响应返回，每个环节都有设计取舍。
>
> **设计层面**: SPI 扩展机制让我理解了"开闭原则"的实际应用 — 框架不变，用户通过配置文件扩展。Factory + SPI 的组合是框架设计的通用模式。
>
> **工程层面**: 教学项目和生产项目的差距 — 连接池、线程模型、异常处理、监控埋点，每个环节都需要深入优化。这让我明白了"能跑"和"能用"之间的距离。

---

## 📋 面试前 Checklist

- [ ] 能画出完整架构图（Consumer → Proxy → Serialize → TCP → Deserialize → Invoke → Provider）
- [ ] 能手写 17 字节协议头的每个字段
- [ ] 能说清 4 种序列化器的区别和适用场景
- [ ] 能解释粘包半包的解决原理（RecordParser）
- [ ] 能说清 Etcd vs ZK 的区别
- [ ] 能解释一致性哈希 + 虚拟节点
- [ ] 能说清 SPI 和 Java SPI 的区别
- [ ] 能画出 Consumer 完整调用链
- [ ] 能说清和 Dubbo 的差距以及改进方向

---

*逐题攻克，面试时 RPC 模块可以稳拿 10 分钟以上的深度对话时间*
