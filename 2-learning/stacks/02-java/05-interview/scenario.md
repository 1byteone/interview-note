# 场景题 — 缓存设计 · 异步编排 · 大文件处理

> 等级：🎯 面试进阶
> 目标：在真实业务场景中灵活运用 Java 核心 API，解决实际问题。

---

## 场景一：缓存设计（本地缓存 + Caffeine）

### 需求

电商系统需要缓存商品详情，特点：
- 读多写少（读写比 99:1）
- 数据量 10 万级别
- 需要过期淘汰
- 高并发下防止缓存雪崩、穿透、击穿

### 方案

```java
@Component
public class ProductCache {
    // 使用 Caffeine + ConcurrentHashMap 两层缓存
    private final Cache<Long, Product> caffeineCache = Caffeine.newBuilder()
        .maximumSize(10_000)            // 最多缓存 1 万
        .expireAfterWrite(5, TimeUnit.MINUTES)  // 写入后 5 分钟过期
        .recordStats()                  // 记录命中率
        .build();

    private final ConcurrentHashMap<Long, Product> localCache = new ConcurrentHashMap<>();

    public Product get(Long productId) {
        // 1. 查 Caffeine（一级缓存）
        Product product = caffeineCache.getIfPresent(productId);
        if (product != null) return product;

        // 2. 查本地 Map（二级缓存，防止 Caffeine 淘汰后突增 DB 压力）
        product = localCache.get(productId);
        if (product != null) {
            caffeineCache.put(productId, product);  // 回填一级缓存
            return product;
        }

        // 3. 查数据库（加锁，防止缓存击穿）
        synchronized (this) {
            product = localCache.get(productId);  // 双重检查
            if (product != null) return product;
            product = productMapper.selectById(productId);
            if (product != null) {
                localCache.put(productId, product);
                caffeineCache.put(productId, product);
            }
        }
        return product;
    }

    // 数据更新时主动失效
    public void evict(Long productId) {
        caffeineCache.invalidate(productId);
        localCache.remove(productId);
    }
}
```

### 缓存问题应对策略

| 问题 | 描述 | 应对 |
|------|------|------|
| 缓存穿透 | 查询不存在的数据 | 缓存空对象（设置短 TTL）或布隆过滤器 |
| 缓存击穿 | 热点 key 失效，高并发回源 | 互斥锁回源（如上） |
| 缓存雪崩 | 大量 key 同时失效 | 过期时间加随机值、多级缓存 |

### 面试加分点

> **Caffeine 的淘汰策略是什么？**
> W-TinyLFU（Window TinyLFU），结合频率和近因性，比 LRU 更精准，比 LFU 更省内存。

---

## 场景二：异步任务编排

### 需求

双十一大促，用户下单后需要：
1. 发送订单确认短信（100ms）
2. 发送邮件通知（200ms）
3. 更新积分（50ms）
4. 推送消息到物流系统（50ms）
5. 写入 ES 用于搜索（30ms）
6. 记录订单日志（10ms）

要求：不阻塞主流程，任务间有依赖，且需要统一超时控制。

### 方案

```java
@Service
public class OrderAsyncProcessor {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void processOrderAsync(Order order) {
        var start = System.currentTimeMillis();

        // 并行执行独立的后续任务
        CompletableFuture<Void> smsFuture = CompletableFuture.runAsync(
            () -> sendSms(order), executor);
        CompletableFuture<Void> emailFuture = CompletableFuture.runAsync(
            () -> sendEmail(order), executor);
        CompletableFuture<Void> logisticsFuture = CompletableFuture.runAsync(
            () -> pushLogistics(order), executor);
        CompletableFuture<Void> esFuture = CompletableFuture.runAsync(
            () -> indexEs(order), executor);

        // 积分更新必须在短信发送之后（依赖）
        CompletableFuture<Void> pointsFuture = smsFuture.thenRunAsync(
            () -> updatePoints(order), executor);

        // 日志记录必须在所有任务之后
        CompletableFuture.allOf(smsFuture, emailFuture, logisticsFuture,
                                esFuture, pointsFuture)
            .orTimeout(5, TimeUnit.SECONDS)  // 整体超时 5 秒
            .thenRunAsync(() -> saveOrderLog(order), executor)
            .exceptionally(e -> {
                log.error("订单异步处理失败: orderId={}", order.getId(), e);
                // 发送告警，但不影响主流程
                alertService.sendAlert("订单处理异常", order.getId());
                return null;
            });
        // 主流程不等待，直接返回
    }
}
```

### 关键点

- **虚拟线程**：每个任务创建虚拟线程，无池化开销
- **超时控制**：`orTimeout(5, TimeUnit.SECONDS)` 防止任务卡死
- **异常隔离**：`exceptionally` 处理异常，不影响主流程
- **依赖编排**：`thenRunAsync` 建立任务依赖

---

## 场景三：大文件处理

### 需求

日志文件 10GB，需要按行读取、解析、统计，输出汇总报告。要求：
- 内存占用不超过 512MB
- 支持断点续传
- 处理过程中可监听进度

### 方案

```java
public class LargeFileProcessor {
    private volatile long processedLines = 0;
    private volatile long totalLines = 0;

    public void process(Path filePath) throws IOException {
        // 1. 先统计总行数（用于进度）
        try (Stream<String> lines = Files.lines(filePath)) {
            totalLines = lines.count();
        }

        // 2. 按行读取 + 并行处理
        try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
            Map<String, Long> result = lines
                .skip(processedLines)  // 断点续传：跳过已处理行
                .parallel()            // 并行处理
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingByConcurrent(
                    LogEntry::getLevel,  // 按日志级别分组
                    Collectors.counting()
                ));
            // 输出结果
            result.forEach((level, count) ->
                System.out.println(level + ": " + count));
        }
    }

    private LogEntry parseLine(String line) {
        try {
            processedLines++;
            // 简单解析：假设格式 [INFO] 2026-08-22 message
            if (line.startsWith("[")) {
                String level = line.substring(1, line.indexOf(']'));
                return new LogEntry(level, line);
            }
            return null;
        } catch (Exception e) {
            return null;  // 跳过异常行
        }
    }

    // 记录断点
    public void saveCheckpoint() {
        // 将 processedLines 写入 checkpoint 文件
        try (var writer = Files.newBufferedWriter(Paths.get("checkpoint.txt"))) {
            writer.write(String.valueOf(processedLines));
        } catch (IOException e) {
            log.error("保存断点失败", e);
        }
    }

    // 获取进度
    public double getProgress() {
        if (totalLines == 0) return 0;
        return (double) processedLines / totalLines;
    }
}
```

### 关键点

- **Files.lines()**：惰性读取，不会一次性加载整个文件到内存
- **parallel()**：并行流处理大数据行，利用多核 CPU
- **skip()**：断点续传，跳过已处理行
- **AtomicLong** 替代 volatile：`processedLines` 在多线程写时可能丢失计数，更严谨用 `AtomicLong`（此处用 volatile 简化，实际需评估精度）

### 面试加分点

> **为什么不用 BufferedInputStream 逐行读？**
> `Files.lines()` 内部使用 `BufferedReader.lines()`，惰性逐行读取，内存占用 O(1)。推荐使用。

> **10GB 文件用 parallelStream 有问题吗？**
> 有。`parallelStream` 使用 `ForkJoinPool.commonPool()`，如果 IO 成为瓶颈，并行反而增加竞争。更好的做法是：手动分片（文件分块），每个线程处理一个块。

---

## 总结

| 场景 | 核心技术 | 核心问题 |
|------|---------|---------|
| 缓存设计 | Caffeine + ConcurrentHashMap + 互斥锁 | 缓存穿透/击穿/雪崩 |
| 异步编排 | CompletableFuture + 虚拟线程 | 超时控制、异常隔离、依赖编排 |
| 大文件处理 | Files.lines() + parallelStream + 断点 | 内存控制、进度监控、并行瓶颈 |

> 进入代码题篇：手写 LRU Cache、生产者消费者、单例模式。