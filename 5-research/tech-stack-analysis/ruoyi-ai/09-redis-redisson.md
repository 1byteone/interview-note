# 09 · Redis + Redisson：缓存策略与分布式锁

> AI 应用的高并发底座：Redis 承担"热点数据缓存 + 会话/上下文存储"，Redisson 提供"可重入分布式锁"，配合 Lock4j 注解化封装，双保险解决缓存三大难题（穿透/击穿/雪崩）与并发安全（重复提交、幂等、库存扣减）。
>
> **对应项目：** `ruoyi-ai/ruoyi-common` 公共 starter（Redis/Redisson 配置）+ 各业务模块

---

## 一、你必须知道的 3 个核心概念

### 1.1 缓存策略

缓存策略是"什么时候写缓存、什么时候读缓存、缓存失效了怎么办"的一整套约定。项目中缓存常见的有三种使用姿势：

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| Cache-Aside（旁路缓存） | 先查缓存，未命中再查 DB，然后回填缓存；写操作直接更新 DB，再删除缓存 | 读多写少、对一致性要求一般的场景，**最主流** |
| Read-Through（穿透读） | 应用只和缓存交互，未命中由缓存组件回源 DB 并回填 | 追求代码整洁、缓存语义统一 |
| 双删缓存（延迟双删） | 更新 DB 后删除缓存，延迟一段时间再次删除 | 高并发读写、防止"先删缓存后写库"的旧值回填问题 |

> 项目采用 **Cache-Aside + 延迟双删** 组合：读走缓存、写更新 DB 后删缓存，并配合延迟双删规避并发窗口下的脏数据。

### 1.2 分布式锁

分布式锁用于解决**多实例（多进程）之间的互斥问题**——单体应用里 JVM 自带 `synchronized` / `ReentrantLock` 就够了，但微服务是多节点部署，锁必须落在所有实例都能访问的第三方上。分布式锁的两个核心诉求：

- **互斥性**：同一时刻只有一个节点能拿到锁
- **安全性**：锁会自动释放（防死锁）、只能释放自己持有的锁（防误删）

实现载体主要有三类：**数据库唯一索引**（简单但性能差）、**Redis SETNX / Redisson**（性能好、功能全，生产主流）、**ZooKeeper / etcd 临时节点**（强一致但运维重）。Redis 方案因为性能与功能兼备成为首选。

### 1.3 Redisson 可重入锁

Redisson 是 Redis 官方推荐的 Java 客户端与分布式数据平台。它把分布式锁封装成了**纯 Java API（`RLock`），用法和 JVM 里的 `ReentrantLock` 几乎一样**——`lock()` 加锁、`unlock()` 解锁、支持 `tryLock(timeout)` 等待超时。

可重入锁（Reentrant Lock）的核心特性：

| 特性 | 说明 |
|------|------|
| 可重入 | 同一线程可重复获取同一把锁，内部计数器 +1；释放时 -1，减到 0 才真正释放 |
| 原子性 | 加锁/解锁用 **Lua 脚本**保证，Redis 单线程执行脚本天然原子 |
| 自动续期 | **WatchDog 机制**：默认锁有效期 30s，每 10s 检查一次，业务未结束自动续期 30s |
| 防误删 | Hash 结构存线程标识（field=线程ID，value=重入次数），只能释放自己持有的锁 |

---

## 二、项目中的实战应用

### 2.1 解决了什么问题

**问题场景：** AI 应用天然高并发——同一个智能体可能被大量用户同时调用，知识库文档重复入库、对话会话上下文并发读写、支付/扣减类操作需要幂等，多实例部署后单机锁全部失效。

| 痛点 | 解决方案 |
|------|----------|
| 热点数据（系统配置、模型列表、知识库摘要）反复查库 | Redis 缓存热点数据，降低 DB 压力与响应时延 |
| 对话会话/SSE 流式上下文需要跨实例共享 | Redis 存会话历史与流式拼接缓冲，多实例消费同一 Key |
| 文档重复上传入库、任务重复执行 | Lock4j 注解 + Redisson 分布式锁，保证同一资源只处理一次 |
| 高并发下缓存失效瞬间全部打到 DB（击穿/雪崩） | 缓存空值（防穿透） + 互斥重建（防击穿） + 过期时间加随机因子（防雪崩） |
| 分布式环境下需要锁等待、可重入、自动续期 | Redisson `RLock` 替代原生 SETNX，功能完整 |

### 2.2 缓存 + 锁整体结构图

```dot
digraph RedisRedisson {
    rankdir = LR;
    splines = ortho;
    node [fontname = "Microsoft YaHei", fontsize = 11, shape = box, style = rounded];
    edge [fontname = "Microsoft YaHei", fontsize = 10];

    subgraph cluster_redis {
        label = "Redis 层";
        style = dashed;
        color = "#4A90D9";
        fontcolor = "#4A90D9";
        cache [label = "缓存 Key\nsession:xxx / config:xxx\nknowledge:xxx"];
        lock [label = "锁 Key\nlock:repeat:submit\nlock:doc:upload"];
    }

    subgraph cluster_client {
        label = "客户端层";
        style = dashed;
        color = "#E67E22";
        fontcolor = "#E67E22";
        springCache [label = "Spring Cache\n@Cacheable / @CacheEvict\n（Cache-Aside 旁路缓存）"];
        lock4j [label = "Lock4j\n@Lock4j 注解 + AOP"];
        redisson [label = "Redisson\nRLock 可重入锁\nWatchDog 自动续期"];
    }

    subgraph cluster_biz {
        label = "业务层";
        style = dashed;
        color = "#27AE60";
        fontcolor = "#27AE60";
        chat [label = "ChatService\n会话上下文读写"];
        upload [label = "DocumentService\n文档导入去重"];
        pay [label = "OrderService\n扣减 / 幂等"];
    }

    chat -> springCache [label = "读缓存/回填"];
    upload -> lock4j [label = "@Lock4j 注解"];
    pay -> lock4j;
    springCache -> cache;
    lock4j -> redisson [label = "AOP 内调用"];
    redisson -> lock;
}
```

### 2.3 核心实现（关键代码片段，带逐行中文注释）

#### 2.3.1 Redisson 配置类

```java
/**
 * Redisson 配置类 —— 读取 application.yml 的 redis 参数，构建 RedissonClient
 * RedissonClient 是 Redisson 的入口：创建锁、读写分布式对象都从它发起
 */
@Configuration
@EnableConfigurationProperties(RedisProperties.class) // 复用 Spring Boot 内置 Redis 配置
public class RedissonConfig {

    /** Spring Boot 内置的 Redis 配置属性（含 host/port/password 等） */
    @Resource
    private RedisProperties properties;

    /**
     * 声明 RedissonClient Bean —— 单例，全局共享一个客户端连接池
     */
    @Bean(destroyMethod = "shutdown") // destroyMethod 指定销毁时优雅关闭连接
    public RedissonClient redissonClient() {
        // 构建 Redisson 基础配置
        Config config = new Config();
        // 单节点模式：redis:// + host:port（redis:// 内部走 SSL 握手，本地无密码更稳）
        config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setPassword(properties.getPassword()); // 无密码时传 null 即可
        // 看门狗相关：锁默认失效时间 30 秒，可通过 setLockWatchdogTimeout 调整
        config.setLockWatchdogTimeout(30_000L);
        // 创建并返回客户端
        return Redisson.create(config);
    }
}
```

对应 `application.yml`：

```yaml
spring:
  data:
    redis:
      host: localhost      # Redis 服务地址
      port: 6379           # Redis 端口
      password:            # 无密码留空
      database: 0          # 默认库
      lettuce:
        pool:
          max-active: 16   # 连接池最大活跃连接数
          max-idle: 8      # 连接池最大空闲连接数
```

#### 2.3.2 手动使用 Redisson 分布式锁（典型业务场景）

```java
/**
 * 文档导入服务 —— 演示分布式锁的标准用法
 * 场景：同一份文档被多个实例同时导入，防止重复解析与入库
 */
@Service
@RequiredArgsConstructor
public class DocumentImportService {

    private final RedissonClient redissonClient; // 注入 Redisson 客户端

    /** 导入文档：同一 docId 并发时只允许一个实例执行 */
    public void importDoc(String docId) {
        // 1. 构造锁 Key：按业务维度隔离，不同资源不同锁
        String lockKey = "lock:doc:import:" + docId;
        // 2. 获取分布式锁（可重入锁）
        RLock lock = redissonClient.getLock(lockKey);
        // 3. 尝试加锁：最多等 5 秒，锁 10 秒后自动释放（WatchDog 会续期）
        boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
        if (!locked) {
            // 拿不到锁说明别的实例正在处理，直接幂等返回，避免重复导入
            throw new BusinessException("文档正在导入中，请勿重复操作");
        }
        try {
            // 4. 真正执行业务：解析 + 向量化 + 入库（这段逻辑全局互斥）
            doImport(docId);
        } finally {
            // 5. 释放锁 —— 必须放在 finally，防止异常时锁不释放导致死锁
            //    只释放自己持有的锁：Redisson 内部校验线程 ID，误删他人锁被 Lua 脚本拦截
            if (lock.isHeldByCurrentThread()) { // 二次确认：当前线程确实持有锁
                lock.unlock();
            }
        }
    }

    /** 实际导入逻辑（略）：文档解析 -> Embedding -> 写入向量库 */
    private void doImport(String docId) { /* ... */ }
}
```

#### 2.3.3 Lock4j 注解化封装（推荐业务写法）

```java
/**
 * Lock4j —— 基于注解的分布式锁封装
 * 原理：Lock4j 的 AOP 切面拦截 @Lock4j 注解方法，内部自动调用 Redisson 加锁/解锁
 * 位于 ruoyi-common 公共 starter 中，全局可用
 */
@Service
@RequiredArgsConstructor
public class DocumentImportService2 {

    /**
     * 注解化加锁 —— 比手动 tryLock 更简洁
     * keys：锁 Key 的 SpEL 表达式，#docId 取方法参数
     * expire：锁过期时间（秒），acquireTimeout：获取锁的等待超时（秒）
     * 业务方法结束自动解锁，无需 finally 手动释放
     */
    @Lock4j(keys = "#docId", expire = 30, acquireTimeout = 5)
    public void importDocByAnnotation(String docId) {
        // 方法体被 Lock4j 切面包裹：进入前加锁，正常/异常返回后自动解锁
        doImport(docId);
    }

    private void doImport(String docId) { /* ... */ }
}
```

> Lock4j 与 Redisson 的关系一句话：**Lock4j 是"壳"，Redisson 是"核"**。Lock4j 只负责把加锁/解锁流程用注解 + AOP 包装成声明式编程，具体锁实现仍然委托给 `RedissonClient`（`ruoyi-common` 的 `Lock4jProperties` 可配置锁类型，默认走 Redisson）。业务上 `@Lock4j` 一行搞定，比手写 `tryLock/finally/unlock` 更不容易出错。

#### 2.3.4 缓存注解使用（Cache-Aside 旁路缓存）

```java
/**
 * 模型配置查询服务 —— 演示 Spring Cache 注解
 * 场景：AI 模型列表、系统配置等热点数据重复查询，用缓存扛住高并发读
 */
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigMapper modelConfigMapper; // MyBatis-Plus Mapper

    /**
     * 查询模型配置
     * @Cacheable：查缓存，命中直接返回；未命中执行方法体，把结果写入缓存
     * key：'model_config:' + #modelCode（SpEL 动态拼 Key）
     * unless：#result == null 时不缓存（避免把 null 缓存成"无数据"）
     */
    @Cacheable(cacheNames = "model_config", key = "#modelCode",
            unless = "#result == null")
    public ModelConfig getByCode(String modelCode) {
        // 缓存未命中时执行：查询数据库
        return modelConfigMapper.selectOne(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getCode, modelCode));
    }

    /**
     * 更新模型配置
     * @CacheEvict：更新 DB 后删除缓存（旁路缓存"删缓存"这一步）
     * 配合延迟双删：业务层再发一个延迟 500ms 删缓存的队列消息，兜底并发窗口
     */
    @CacheEvict(cacheNames = "model_config", key = "#config.code")
    public void updateConfig(ModelConfig config) {
        modelConfigMapper.updateById(config); // 先写数据库
        // 延迟双删兜底：提交后延迟 500ms 再次删除缓存，防止并发读回填旧值
        delayedDeleteCache(config.getCode());
    }

    /** 延迟双删（简化示意）：真实项目用 Redis 延迟队列 / ThreadPool 定时器实现 */
    private void delayedDeleteCache(String code) {
        // 延时 500ms 后再次 @CacheEvict 删除缓存（此处省略实现细节）
    }
}
```

#### 2.3.5 防缓存穿透（空值缓存）

```java
/**
 * 知识库摘要查询 —— 演示"缓存空值"防止缓存穿透
 * 穿透：恶意请求查不存在的 Key，缓存一直 miss，全部打到 DB
 */
@Service
@RequiredArgsConstructor
public class KnowledgeSummaryService {

    private final StringRedisTemplate stringRedisTemplate; // Redis 模板

    /** 查询摘要：先查缓存，未命中查库，查不到则缓存空值 */
    public String getSummary(String kbId) {
        String cacheKey = "knowledge:summary:" + kbId;
        // 1. 查缓存
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached; // 命中直接返回（包括缓存中的空占位符）
        }
        // 2. 未命中查数据库
        String summary = queryDb(kbId);
        if (summary == null) {
            // 3. 数据库中不存在：缓存一个短 TTL 的占位符，防止下次穿透
            //    注意：空值 TTL 要短（如 60s），避免"真正写入后还要等占位符过期"
            stringRedisTemplate.opsForValue()
                    .set(cacheKey, "", Duration.ofSeconds(60));
            return null;
        }
        // 4. 查到了回填缓存（TTL 加随机因子，防止热点 Key 同时过期造成雪崩）
        long ttl = 300 + ThreadLocalRandom.current().nextInt(60);
        stringRedisTemplate.opsForValue()
                .set(cacheKey, summary, Duration.ofSeconds(ttl));
        return summary;
    }

    private String queryDb(String kbId) { /* ... */ }
}
```

#### 2.3.6 防缓存击穿（互斥重建）

```java
/**
 * 热点配置查询 —— 演示"分布式锁互斥重建"防止缓存击穿
 * 击穿：某个热点 Key 过期瞬间，大量请求同时打到 DB（缓存没崩，单个 Key 崩了）
 */
@Service
@RequiredArgsConstructor
public class HotConfigService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    /** 查询热点配置：缓存过期瞬间只让一个线程查库，其余线程等待后读缓存 */
    public String getHotConfig(String key) {
        String cacheKey = "config:hot:" + key;
        String value = stringRedisTemplate.opsForValue().get(cacheKey);
        if (value != null) {
            return value; // 缓存命中，直接返回
        }
        // 缓存未命中：加锁，让多个请求只有一个真正查库（互斥重建）
        RLock lock = redissonClient.getLock("lock:config:rebuild:" + key);
        try {
            // 等待 5 秒获取锁（其他线程在这里阻塞等待，而不是打 DB）
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                // 拿到锁后二次查缓存（double check）：可能上一个线程已回填
                value = stringRedisTemplate.opsForValue().get(cacheKey);
                if (value != null) {
                    return value; // 已被重建，直接返回
                }
                // 真正只有这一个线程查库 + 回填
                value = queryDb(key);
                long ttl = 300 + ThreadLocalRandom.current().nextInt(60); // 随机 TTL 防雪崩
                stringRedisTemplate.opsForValue()
                        .set(cacheKey, value, Duration.ofSeconds(ttl));
                return value;
            }
            // 拿不到锁：主动重查缓存（大概率已重建），避免直接 fail
            return stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态，规范做法
            return null;
        } finally {
            // 释放锁（仅释放自己持有的）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String queryDb(String key) { /* ... */ }
}
```

### 2.4 设计亮点

**亮点一：注解化 + AOP，业务零侵入**

手动 `tryLock/finally/unlock` 写多了容易漏 `finally` 导致死锁。Lock4j 通过 AOP 切面把加锁/解锁收敛进公共 starter，业务方法只需要 `@Lock4j(keys = "#docId")` 一行注解——**把最容易出错的"释放锁"交给了框架**。

**亮点二：Redisson WatchDog 彻底解决"锁超时"**

原生 `SETNX + EXPIRE` 最大的坑是"业务执行时间 > 锁过期时间"——业务还没跑完锁先被释放，其他实例趁虚而入。WatchDog 默认每 10 秒检查一次，业务未结束自动续期 30 秒；若线程崩溃，WatchDog 随 JVM 守护线程销毁，锁正常超时释放，不会永久占用。

**亮点三：缓存三层防护体系**

| 问题 | 现象 | 项目对策 |
|------|------|----------|
| 穿透 | 查不存在的 Key，永远 miss 打 DB | 缓存空值（短 TTL） + 布隆过滤器可选 |
| 击穿 | 热点 Key 过期瞬间并发打 DB | Redisson 互斥重建 + 二次 check |
| 雪崩 | 大量 Key 同时过期 | TTL 加随机因子错峰过期 + 多级缓存 |

**亮点四：Redis 承担"AI 特有"的共享状态**

除了常规缓存，Redis 还承担 AI 场景特有职责：对话会话上下文（多实例共享记忆）、SSE 流式拼接缓冲、RAG 文档入库去重锁、模型调用限流计数——这些都是单机内存无法满足的跨实例状态。

---

## 三、面试高频题

### Q1: Redis 分布式锁的几种实现方式？Redisson 为什么比 SETNX 好？

**参考答案：**

分布式锁实现按演进顺序分三层：

**方案一：原生 SETNX + EXPIRE（最朴素）**

```java
// 1. 加锁：SETNX 成功返回 1 代表拿到锁，同时用 EXPIRE 设过期时间防死锁
// 注意：SETNX 与 EXPIRE 必须合并成一条 SET EX NX PX 命令，否则可能"加锁成功但没设过期"——死锁
Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
        lockKey, token, Duration.ofSeconds(30));
if (Boolean.TRUE.equals(ok)) {
    // 执行业务
    // 2. 释放锁：必须用 Lua 脚本"先比对 value（持有者标识）再 DEL"，防止误删他人锁
    //    if redis.call('get', KEYS[1]) == ARGV[1] then
    //        return redis.call('del', KEYS[1])
    //    else
    //        return 0
    //    end
}
```

**方案二：Redisson RLock（生产主流）**

在方案一基础上补齐五大能力：

| 能力 | SETNX 原生化 | Redisson |
|------|-------------|----------|
| 可重入 | 不支持（需自己用 Hash 存计数） | 内置，Hash 结构 + 线程 ID + 计数 |
| 自动续期 | 不支持（定死 30s，业务超时即失效） | WatchDog 自动续期 |
| 等待锁 | 拿不到立刻失败或自旋 | `tryLock(timeout)` 阻塞等待 |
| 原子释放 | 需手写 Lua 脚本 | 内置 Lua 脚本 |
| 锁类型 | 只有一种 | 重入/公平/读写/联锁/红锁/信号量 |

**方案三：RedLock（多节点强一致，争议大）**

同时在 N 个独立 Redis 节点上获取锁，超过半数（N/2+1）成功才算获取。Martin Kleppmann 在《How to do distributed locking》中质疑其在 GC pause、时钟跳变下仍不安全；实践中**"单主从 + Redisson WatchDog"已满足绝大多数生产需求**，RedLock 更多用于对一致性极度敏感的场景。

**选型结论：** 简单定时任务互斥可用 SETNX 就够；业务执行时间长、需要重入/等待/续期的场景必须 Redisson——我们项目统一用 Lock4j + Redisson，正是因为这个原因。

**追问应对：** "SETNX 释放锁为什么要 Lua 脚本？" 答：`GET 比对` 和 `DEL` 是两个命令，中间可能被其他线程插队误删；Lua 脚本在 Redis 单线程模型下整体原子执行，杜绝了"判断与删除"之间的竞态窗口。

### Q2: 项目中 Redis 有哪些缓存场景？缓存穿透/击穿/雪崩怎么防？

**参考答案：**

**项目缓存场景分类：**

1. **热点配置类**：模型列表、系统参数、大模型接入配置——`@Cacheable` 旁路缓存
2. **AI 会话状态类**：对话上下文、SSE 流式缓冲——跨实例共享，用 Redis 而非本地内存
3. **聚合结果类**：知识库摘要、统计计数——避免高频聚合查询打 DB
4. **去重/幂等类**：文档导入锁、验证码、请求限流计数——Redis 天然带 TTL 与原子自增

**三大难题与对策（逐条讲，面试官最爱追问）：**

| 难题 | 本质 | 对策 | 注意点 |
|------|------|------|--------|
| 穿透 | 查**不存在**的数据，缓存永远 miss | ① 缓存空值（短 TTL） ② 布隆过滤器先拦截 | 空值 TTL 不能太长，否则真实数据写入后仍读不到 |
| 击穿 | 某个**热点 Key 过期**瞬间并发打 DB | ① 互斥重建（分布式锁，见 2.3.6） ② 逻辑过期（值里存过期时间，后台异步重建） | double-check：拿到锁后要再查一次缓存 |
| 雪崩 | **大量 Key 同时过期** | ① TTL 加随机因子错峰 ② 缓存集群高可用（哨兵/Cluster） ③ 多级缓存（本地 Caffeine + Redis）兜底 | 别忽略"缓存服务本身宕机"也是一种雪崩（熔断/降级） |

**追问应对：** "互斥重建和逻辑过期有什么区别？" 答：互斥重建是"牺牲写的一致性换读的可用性"——过期时一锁挡住所有请求；逻辑过期是"读不等待"——直接返回可能过期的旧值，后台线程异步刷新，适合容忍短暂旧数据的高并发场景。

### Q3: Lock4j 和 Redisson 的关系？项目中怎么结合使用的？

**参考答案：**

**关系一句话：Lock4j 是注解壳，Redisson 是核心锁实现。** 两者是"封装层与实现层"的关系，不是并列的两个方案：

1. **Lock4j**（Dromara 社区）只负责"声明式编程"：提供 `@Lock4j` 注解 + Spring AOP 切面，拦截被注解的方法，在方法进入前加锁、退出后解锁。它**不做锁的具体实现**。
2. **Redisson** 提供真正的分布式锁能力：`RLock` 可重入、Lua 原子脚本、WatchDog 续期、公平锁/读写锁等。
3. Lock4j 内部通过 `LockTemplate` 抽象接口对接具体实现（Redisson 是其中一个实现），`ruoyi-common` 的公共 starter 默认装配 Redisson 实现。

**项目中的结合方式分三层：**

| 层次 | 做什么 | 代码形态 |
|------|--------|----------|
| 配置层 | `RedissonConfig` 构建 `RedissonClient`，注册 Lock4j 的锁模板 | Bean 配置，全局一次 |
| 中间层 | Lock4j starter 的 AOP 切面，绑定 Redisson 锁模板 | 公共依赖引入 |
| 业务层 | 直接 `@Lock4j(keys = "#docId", expire = 30, acquireTimeout = 5)` | 一行注解 |

```java
// 典型结合：知识库文档入库去重 —— 注解声明锁，切面 + Redisson 执行加解锁
@Lock4j(keys = "#knowledgeBaseId + ':' + #fileName", expire = 60, acquireTimeout = 5)
public void importKnowledge(String knowledgeBaseId, String fileName) {
    // 同一 KB 同一文件名并发导入时全局互斥
}
```

**追问应对：** "为什么不用 Spring 自带的 `@Cacheable` 做锁？" 答：`@Cacheable` 是缓存注解不是锁——它解决"读的缓存复用"，不保证"写的互斥"。锁与缓存的职责要分开：**缓存管读，锁管写**。防击穿场景两者才配合使用（互斥重建就是"锁 + 缓存"的合体）。

---

## 四、面试避坑指南

### 坑 1：忘记解锁 / 解锁不放在 finally

**错误做法：** `lock.lock()` 之后业务抛异常，没有 `finally { lock.unlock() }`——锁永远不释放，其他实例全部阻塞，随请求数增长雪崩。

**正确做法：** 手动加锁必须 **try-finally 包裹**；更推荐用 `@Lock4j` 注解，让 AOP 切面统一保证"异常也解锁"。

### 坑 2：SETNX 加锁没设置过期时间 / 设置过期时间太短

**错误做法：** 用两条命令 `SETNX` + `EXPIRE` 分开执行，中间进程崩溃导致只有锁没有过期时间——死锁；或者 `expire=5s` 但业务要跑 30s——业务没完锁先没了。

**正确做法：** 用 `SET key value EX seconds NX` 一条命令原子完成；拿不准业务时长就用 Redisson WatchDog 自动续期，而不是把过期时间拍脑袋定得很长（越长，持有者崩溃后锁占用的时间越久）。

### 坑 3：释放锁时删掉了别人的锁

**错误做法：** 释放锁直接 `DEL key`，不管锁 value 是不是自己的——线程 A 的锁超时后线程 B 拿到锁，A 的 finally 执行 `DEL`，把 B 的锁误删，并发保护失效。

**正确做法：** value 存唯一标识（UUID 或线程 ID），释放时用 **Lua 脚本"先比对再删除"**；Redisson 已内置此逻辑（`isHeldByCurrentThread()` 二次确认），不要在手动 SETNX 里省略这步。

### 坑 4：把缓存坏死问题（穿透/击穿/雪崩）混为一谈

**错误做法：** 面试或方案里把三个概念混着说——"我们加了缓存所以不怕穿透"，实际只做了 Cache-Aside，热点 Key 过期照样击穿。

**正确做法：** 先说区分：**穿透 = 数据不存在**（空值/布隆过滤器）、**击穿 = 单个热点 Key 过期**（互斥重建）、**雪崩 = 大量 Key 或缓存服务整体失效**（随机 TTL/多级缓存/熔断降级）。再对号入座报方案，体现"真做过线上优化"。

### 坑 5：锁的粒度太粗或太细

**错误做法：** ① 锁粒度太粗：给"整个知识库导入"加一把锁，不同用户导入互相阻塞，吞吐量崩；② 锁粒度太细：Key 只拼了文件名没拼 KB ID，不同知识库里同名的文件互相误锁。

**正确做法：** 锁 Key 遵循"**最小互斥范围**"原则：`lock:doc:import:{kbId}:{fileName}`——不同资源不互相影响，同一资源全局唯一。面试主动提 Key 设计，是区分"背概念"和"有实战"的关键细节。

### 坑 6：缓存序列化问题（跨实例读脏数据）

**错误做法：** 项目里 `@Cacheable` 缓存了 Java 对象，但序列化器配置不一致——一个实例存的是 Jackson JSON，另一个实例配了 JDK 序列化，读缓存直接反序列化异常；或缓存了巨对象，Key 膨胀（Redis 内存告警）。

**正确做法：** 统一序列化器（如 `GenericJackson2JsonRedisSerializer`）、开启 Value 压缩、控制缓存对象大小与 TTL，并给缓存设置 Redis `maxmemory` 淘汰策略兜底（如 `allkeys-lru`）。面试可以补一句"我们监控过 Redis 内存增长并给 key 加了健康巡检"。

---

## 五、参考资料与扩展阅读

- [Redisson 官方文档](https://github.com/redisson/redisson) — RLock / WatchDog / RedLock 完整用法
- [Lock4j 官方文档](https://github.com/dromara/lock4j) — 注解化分布式锁，支持 Redisson / ZooKeeper 等实现
- [Redisson 分布式锁原理](https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers) — Hash + Lua 脚本的可重入锁实现细节
- [Martin Kleppmann: How to do distributed locking](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html) — RedLock 算法争议的权威分析
- [Spring 官方 Cache Abstraction](https://spring.io/guides/gs/caching) — `@Cacheable` / `@CacheEvict` 注解式缓存
- [Redis 官方 SET 命令](https://redis.io/commands/set/) — SETNX 与 EXPIRE 原子合并的语法说明