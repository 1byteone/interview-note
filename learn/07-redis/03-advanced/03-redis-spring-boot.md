# Redis + Spring Boot 整合 — RedisTemplate · @Cacheable · 序列化

> 等级：🎯 面试进阶
> 目标：掌握 Spring Boot 中使用 Redis 的三种方式：RedisTemplate 原生操作、@Cacheable 声明式缓存、Redisson 客户端。

---

## 一、RedisTemplate 与 StringRedisTemplate

### 1.1 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<!-- 连接池（推荐） -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

### 1.2 配置

```yaml
# application.yml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password:
      database: 0
      timeout: 3s
      lettuce:
        pool:
          max-active: 16      # 最大连接数
          max-idle: 8         # 最大空闲连接
          min-idle: 4         # 最小空闲连接
          max-wait: -1s       # 获取连接超时（-1 不超时）
```

### 1.3 核心 API

```java
@Service
public class RedisService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;  // K+V 都是 String
    @Autowired
    private RedisTemplate<String, Object> redisTemplate; // 需要配置序列化

    // ========== String 操作 ==========
    public void setString(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS);
    }
    public String getString(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    // ========== Hash 操作 ==========
    public void setHash(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }
    public Object getHash(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    // ========== List 操作 ==========
    public void pushList(String key, String value) {
        stringRedisTemplate.opsForList().leftPush(key, value);
    }
    public String popList(String key) {
        return stringRedisTemplate.opsForList().rightPop(key);
    }

    // ========== Set 操作 ==========
    public void addSet(String key, String... values) {
        stringRedisTemplate.opsForSet().add(key, values);
    }
    public Set<String> getSet(String key) {
        return stringRedisTemplate.opsForSet().members(key);
    }

    // ========== ZSet 操作 ==========
    public void addZSet(String key, String value, double score) {
        stringRedisTemplate.opsForZSet().add(key, value, score);
    }
    public Set<String> getTopN(String key, int n) {
        return stringRedisTemplate.opsForZSet().reverseRange(key, 0, n - 1);
    }

    // ========== 通用操作 ==========
    public void setExpire(String key, long timeout, TimeUnit unit) {
        stringRedisTemplate.expire(key, timeout, unit);
    }
    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
}
```

### 1.4 StringRedisTemplate vs RedisTemplate

| 对比 | StringRedisTemplate | RedisTemplate |
|------|-------------------|---------------|
| 序列化方式 | StringRedisSerializer | 默认 JDK Serialization |
| key 类型 | String | String + 其他 |
| value 类型 | String | Object（可存任意对象） |
| 可读性 | 可读（纯文本） | 不可读（二进制乱码） |
| 推荐场景 | 90% 场景 | 需要存对象时，配合 JSON 序列化 |

**最佳实践**：大部分场景用 `StringRedisTemplate`，value 手动序列化 JSON。需要存复杂对象时，用 RedisTemplate + Jackson2JsonRedisSerializer。

---

## 二、序列化配置

### 2.1 为什么需要自定义序列化？

RedisTemplate 默认使用 JDK 序列化，key 和 value 都是二进制乱码，Redis 客户端无法直接查看。生产环境建议配置 JSON 序列化。

### 2.2 配置示例

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 序列化：String
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 序列化：JSON（推荐）
        Jackson2JsonRedisSerializer<Object> jsonSerializer = 
            new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LazyObjectMapper.defaultPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL);
        jsonSerializer.setObjectMapper(om);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

### 2.3 序列化方案对比

| 方案 | 可读性 | 性能 | 兼容性 | 推荐度 |
|------|--------|------|--------|--------|
| JDK 序列化 | 差 | 差 | 好 | 不推荐 |
| Jackson JSON | 好 | 中 | 好 | 推荐 |
| Fastjson JSON | 好 | 高 | 中 | 可选 |
| String 手动序列化 | 最好 | 最高 | 最好 | **最推荐** |

---

## 三、@Cacheable 声明式缓存

### 3.1 基础用法

```java
@Service
public class ProductService {
    @Autowired
    private ProductMapper productMapper;

    // 缓存注解：查缓存 → 未命中 → 执行方法 → 写入缓存
    @Cacheable(value = "product", key = "#id", unless = "#result == null")
    public Product getProduct(Long id) {
        return productMapper.selectById(id);
    }

    // 更新缓存（删除缓存，下次读时重建）
    @CacheEvict(value = "product", key = "#product.id")
    @Transactional
    public void updateProduct(Product product) {
        productMapper.updateById(product);
    }

    // 组合操作：先执行方法，然后将结果存入缓存
    @CachePut(value = "product", key = "#product.id")
    public Product updateAndCache(Product product) {
        productMapper.updateById(product);
        return product;
    }
}
```

### 3.2 常用注解

| 注解 | 作用 | 说明 |
|------|------|------|
| `@Cacheable` | 缓存读 | 先查缓存，命中直接返回，未命中执行方法 |
| `@CachePut` | 缓存写 | 先执行方法，再将结果写入缓存 |
| `@CacheEvict` | 缓存删除 | 在执行方法前/后删除缓存 |
| `@Caching` | 组合操作 | 同时使用多个缓存注解 |
| `@CacheConfig` | 类级别配置 | 统一配置 `cacheNames`、`cacheManager` |

### 3.3 配置

```yaml
spring:
  cache:
    type: redis          # 使用 Redis 缓存
    redis:
      time-to-live: 1h  # 全局缓存过期时间
      use-key-prefix: true        # 使用 key 前缀
      key-prefix: "mall:"         # key 前缀
      cache-null-values: false    # 不缓存空值
```

### 3.4 自定义 CacheManager（过期时间按业务配置）

```java
@Configuration
public class CacheConfig {
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer() {
        return builder -> {
            // 不同业务设置不同的过期时间
            Map<String, RedisCacheConfiguration> configMap = new HashMap<>();
            
            configMap.put("product", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)));      // 商品缓存 1 小时
            configMap.put("category", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)));   // 分类缓存 30 分钟
            configMap.put("hot_search", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)));    // 热搜缓存 5 分钟

            builder.withInitialCacheConfigurations(configMap);
        };
    }
}
```

### 3.5 注意事项

- `@Cacheable` 适用**读多写少**的场景，不适合高频写入
- 缓存方法内部调用不生效（AOP 代理限制），需在外部类调用
- `unless` 条件控制何时不缓存（如 null 值不缓存）
- 分布式环境下，`@CacheEvict` 删除缓存后，其他节点需要等下次读时重建

---

## 四、Redisson 客户端

### 4.1 依赖

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.27.0</version>
</dependency>
```

### 4.2 配置

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password:

# Redisson 独立配置（或使用默认的 Spring Data Redis 配置）
```

```java
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://127.0.0.1:6379")
            .setConnectionPoolSize(16)
            .setConnectionMinimumIdleSize(4);

        // 哨兵模式
        // config.useSentinelServers()
        //     .setMasterName("mymaster")
        //     .addSentinelAddress("redis://127.0.0.1:26379");

        // 集群模式
        // config.useClusterServers()
        //     .addNodeAddress("redis://127.0.0.1:7001", "redis://127.0.0.1:7002");

        return Redisson.create(config);
    }
}
```

### 4.3 常用 API

```java
@Service
public class RedissonService {
    @Autowired
    private RedissonClient redissonClient;

    // 分布式锁
    public void doWithLock(String key, Runnable task) {
        RLock lock = redissonClient.getLock(key);
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    // 读写锁
    public void doWithReadLock(String key, Runnable task) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(key);
        rwLock.readLock().lock();
        try { task.run(); } finally { rwLock.readLock().unlock(); }
    }

    // 限流器
    public boolean tryAcquire(String key, int permits) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, permits, 1, RateIntervalUnit.SECONDS);
        return rateLimiter.tryAcquire();
    }

    // 信号量
    public void acquireSemaphore(String key) {
        RSemaphore semaphore = redissonClient.getSemaphore(key);
        semaphore.acquire();
    }
}
```

---

## 五、三种方式选型

| 方式 | 复杂度 | 灵活性 | 推荐场景 |
|------|--------|--------|---------|
| StringRedisTemplate | 低 | 高 | 日常 CRUD、计数器、缓存操作 |
| @Cacheable | 最低 | 低 | 简单读多写少缓存场景 |
| Redisson | 中 | 最高 | 分布式锁、信号量、限流器、高级数据结构 |

**典型组合**：一般业务用 `StringRedisTemplate` + 手动 JSON 序列化；复杂缓存用 `@Cacheable`；分布式锁用 `Redisson`。

---

## 六、面试速记

| 知识点 | 要点 |
|--------|------|
| StringRedisTemplate vs RedisTemplate | 前者 String 序列化，后者需自定义序列化 |
| 序列化选型 | 手动 JSON 序列化最推荐，可读性好 |
| @Cacheable 原理 | AOP 拦截，通过 CacheManager 操作缓存 |
| Redisson 价值 | 分布式锁、Watch Dog、可重入、Lua 原子性 |
| 连接池大小 | 一般 8-16 个连接，根据 QPS 和响应时间微调 |

> 进入项目实战篇：AI 商城 Redis 集成——多级缓存、秒杀、分布式 Session、幂等性 Token。