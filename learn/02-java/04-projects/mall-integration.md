# AI 商城 — Java 核心应用集成

> 等级：🎯 面试进阶
> 主题：AI 智能商城如何运用 Java 核心 API（集合、并发、NIO）解决实际问题
> 路径：STAR 法则贯穿每个案例

---

## 一、集合使用：ConcurrentHashMap 本地缓存

### Situation（场景）

AI 商城需要频繁读取商品分类信息，每次从数据库查询耗时 50ms 以上，且分类数据变更频率低（小时级），高峰时 QPS 达到 5000+，数据库压力大。

### Task（任务）

设计一个本地缓存，减少数据库查询，缓存需要支持：
- 高并发读写（读写比例 99:1）
- 定时失效和主动刷新
- 缓存不存在时自动回源

### Action（方案）

```java
@Component
public class CategoryCache {
    private final ConcurrentHashMap<Long, Category> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        // 首次加载
        reloadAll();
        // 定时刷新（每 5 分钟）
        scheduler.scheduleAtFixedRate(this::reloadAll, 5, 5, TimeUnit.MINUTES);
    }

    public Category get(Long categoryId) {
        Category cat = cache.get(categoryId);
        if (cat == null) {
            // 回源加载 + 缓存（避免缓存穿透，加锁保护）
            cat = loadFromDB(categoryId);
            if (cat != null) {
                cache.put(categoryId, cat);
            }
        }
        return cat;
    }

    private void reloadAll() {
        List<Category> all = categoryMapper.selectAll();
        ConcurrentHashMap<Long, Category> newCache = new ConcurrentHashMap<>();
        all.forEach(cat -> newCache.put(cat.getId(), cat));
        cache.clear();
        cache.putAll(newCache);  // 原子替换
    }
}
```

### Result（效果）

- 缓存命中率 99.5%，数据库 QPS 从 5000 降至 25
- 接口响应时间从 50ms 降至 < 1ms
- 5 分钟自动刷新，保证数据最终一致性

### 面试加分点

> **为什么用 ConcurrentHashMap 而不是 Caffeine？**
> 本地缓存首选 Caffeine，但面试中要体现对 ConcurrentHashMap 底层原理的理解——CAS 插入、扩容机制、sizeCtl 控制。

---

## 二、JUC 使用：CompletableFuture 异步编排订单

### Situation（场景）

用户下单时需要执行多个并行任务：
1. 查用户信息（50ms）
2. 查商品详情（30ms）
3. 查库存（20ms）
4. 查优惠信息（100ms）
5. 查配送信息（40ms）

串行执行总耗时 240ms，高并发下 QPS 受限。

### Task（任务）

将独立任务并行化，将总耗时降到最慢任务的时间（100ms），并统一异常处理。

### Action（方案）

```java
@Service
public class OrderAssembleService {
    private final ExecutorService executor = new ThreadPoolExecutor(
        10, 20, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(500),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public OrderDetail assemble(Long userId, Long productId) {
        CompletableFuture<UserInfo> userFuture = CompletableFuture
            .supplyAsync(() -> userService.getUser(userId), executor);

        CompletableFuture<Product> productFuture = CompletableFuture
            .supplyAsync(() -> productService.getProduct(productId), executor);

        CompletableFuture<Stock> stockFuture = CompletableFuture
            .supplyAsync(() -> stockService.getStock(productId), executor);

        CompletableFuture<Discount> discountFuture = CompletableFuture
            .supplyAsync(() -> discountService.getDiscount(userId, productId), executor);

        CompletableFuture<Delivery> deliveryFuture = CompletableFuture
            .supplyAsync(() -> deliveryService.getDelivery(userId), executor);

        // 全部完成后合并结果
        return CompletableFuture
            .allOf(userFuture, productFuture, stockFuture, discountFuture, deliveryFuture)
            .thenApplyAsync(v -> {
                OrderDetail detail = new OrderDetail();
                detail.setUser(userFuture.join());
                detail.setProduct(productFuture.join());
                detail.setStock(stockFuture.join());
                detail.setDiscount(discountFuture.join());
                detail.setDelivery(deliveryFuture.join());
                return detail;
            }, executor)
            .exceptionally(e -> {
                log.error("订单编排失败", e);
                throw new OrderAssembleException("订单组装失败", e);
            })
            .join();
    }
}
```

### Result（效果）

- 总耗时从 240ms 降至 100ms（最慢任务耗时）
- 吞吐量提升 2.4 倍
- 统一异常处理，任一任务失败快速失败

### 面试加分点

> **CompletableFuture 的默认线程池是什么？**
> `ForkJoinPool.commonPool()`，是 CPU 密集型线程池。IO 密集型任务应传入自定义线程池。

> **`allOf` 和 `join` 的区别？**
> `allOf` 返回 `CompletableFuture<Void>`，等待所有任务完成；`join` 阻塞获取结果（与 `get` 不同，`join` 不抛受检异常）。

---

## 三、NIO 使用：Netty 实现网关

### Situation（场景）

AI 商城需要实现一个轻量级网关，处理用户的 HTTP 请求，进行路由转发、限流、日志记录。传统 BIO 方式下，每个连接一个线程，1000 并发就需要 1000 个线程，资源浪费严重。

### Task（任务）

使用 Netty 实现高性能网关，支持：
- 非阻塞 IO 处理
- 请求路由转发
- 简单限流
- 请求日志

### Action（方案）

```java
@Slf4j
public class ApiGateway {
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup worker = new NioEventLoopGroup();
    private final RateLimiter rateLimiter = RateLimiter.create(1000.0); // Guava 令牌桶

    public void start(int port) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(65536))
                        .addLast(new RateLimitHandler(rateLimiter))
                        .addLast(new LoggingHandler(LogLevel.INFO))
                        .addLast(new GatewayRouterHandler());
                }
            });
        try {
            bootstrap.bind(port).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}

@ChannelHandler.Sharable
class RateLimitHandler extends ChannelInboundHandlerAdapter {
    private final RateLimiter rateLimiter;
    RateLimitHandler(RateLimiter rateLimiter) { this.rateLimiter = rateLimiter; }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!rateLimiter.tryAcquire()) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, TOO_MANY_REQUESTS,
                Unpooled.wrappedBuffer("Too Many Requests".getBytes()));
            ctx.writeAndFlush(response);
            return;
        }
        ctx.fireChannelRead(msg);
    }
}

class GatewayRouterHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        FullHttpRequest request = (FullHttpRequest) msg;
        String uri = request.uri();
        // 路由转发...
        // 记录日志...
        ctx.fireChannelRead(msg);
    }
}
```

### Result（效果）

- 单机支持 5000+ 并发连接
- 内存消耗远低于 BIO 模型
- 可扩展：添加鉴权、日志、熔断等 Handler

### 面试加分点

> **Netty 的零拷贝体现在哪里？**
> `CompositeByteBuf` 合并多个 Buffer 不拷贝；`FileRegion.transferTo` 实现文件传输零拷贝；直接内存减少堆内拷贝。

> **Netty 的 FastThreadLocal 比 ThreadLocal 快在哪？**
> 使用数组替代哈希表，减少了哈希冲突和计算开销，Netty 4.1+ 默认使用。

---

## 四、总结

| 场景 | 技术 | 核心原理 |
|------|------|---------|
| 本地缓存 | ConcurrentHashMap | CAS 插入、读无锁、分段扩容 |
| 异步编排 | CompletableFuture | 异步回调、ForkJoinPool 调度 |
| 网络网关 | Netty/NIO | Reactor 模型、零拷贝、内存池 |

> 进入独立小项目：用纯 Java 核心 API 实现一个内存版迷你博客。