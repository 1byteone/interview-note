# Redis + Redisson 入门：第一个缓存和分布式锁

> 本文是 ruoyi-ai 技术栈深度剖析系列的 Redis/Redisson 入门篇，Level 1（入门篇），面向已有 Spring Boot 基础、初次接触缓存的开发者。

---

## 1. 项目背景

### 1.1 为什么需要缓存？

在任何一个 Web 应用中，数据库查询是最常见的性能瓶颈。假设你的 ruoyi-ai 项目中有这样一个场景：用户每次刷新首页都要从 MySQL 中查询菜单权限、系统配置、字典数据。如果并发量只有几十，数据库还能扛住；但如果并发上升到几百、几千，每一次请求都直接穿透到数据库，MySQL 的连接池很快就会耗尽，响应时间从毫秒级飙升到秒级，甚至引发雪崩。

缓存（Cache）就是为了解决这个问题而生的。它的核心思想很简单：**将昂贵操作的结果暂存在更快的存储介质中，后续相同请求直接返回缓存结果**。在 Java 企业级应用中，最常见的缓存介质就是 Redis（Remote Dictionary Server）。

缓存的价值可以概括为三点：

- **降低延迟**：Redis 基于内存，读写速度是微秒级，而 MySQL 的磁盘 IO 是毫秒级，差距在 1000 倍以上。
- **减轻数据库压力**：将热点数据放在 Redis 中，数据库的查询量可以降低 80% 以上。
- **提升系统吞吐量**：同样的硬件资源，配合缓存后系统能支撑的并发量可以提升数倍。

### 1.2 为什么需要分布式锁？

在单体应用中，Java 的 `synchronized` 关键字或 `ReentrantLock` 就能解决线程安全问题。但在微服务架构下，ruoyi-ai 的多个实例（比如部署了 3 个商品服务节点）同时处理同一个订单的扣减库存请求时，单体锁就失效了——因为每个 JVM 的锁互不感知。

这时候就需要**分布式锁**：一个全局的、跨 JVM 的互斥机制。Redis 凭借其单线程模型和原子操作，天然适合作为分布式锁的载体。而 Redisson 则在此基础上提供了开箱即用的 `RLock` 接口，让分布式锁的使用体验和本地锁几乎一样。

### 1.3 ruoyi-ai 中的缓存场景

在 ruoyi-ai 项目中，Redis 的典型使用场景包括：

| 场景 | 说明 | 数据类型 |
|------|------|---------|
| 系统配置缓存 | 从数据库加载后缓存到 Redis | String |
| 验证码缓存 | 存储验证码，带过期时间 | String |
| 登录令牌 | JWT Token 的存储与校验 | String |
| 数据字典 | 字典数据缓存，减少数据库查询 | String/Hash |
| 接口限流 | 基于 Redis 的计数器限流 | String/ZSet |
| 分布式锁 | 定时任务、库存扣减等场景 | Redisson Lock |
| AI 对话上下文 | 临时存储用户会话上下文 | String/Hash |

### 1.4 本文要做什么

本文将从零开始，带你在 Spring Boot 项目中完成以下目标：

1. 配置 Redis 连接（Lettuce 客户端）
2. 使用 Spring Data Redis 操作五种基础数据类型
3. 使用 `@Cacheable` 等注解实现声明式缓存
4. 使用 Redisson 实现分布式锁
5. 使用 Lock4j 注解简化锁的使用

---

## 2. 核心概念

### 2.1 Redis 的五种基础数据类型

Redis 是一个键值对存储系统，但它支持丰富的数据类型，远不止 String 那么简单。

#### 2.1.1 String（字符串）

String 是 Redis 最基础的数据类型，一个 key 对应一个 value。它能存储字符串、整数、浮点数，甚至序列化的 JSON 对象（最大 512MB）。

**典型应用**：缓存单个对象（如用户信息）、计数器（如文章阅读数）、分布式 ID 生成。

**常用命令**：`SET`、`GET`、`INCR`、`DECR`、`EXPIRE`、`SETNX`。

#### 2.1.2 Hash（哈希）

Hash 是一个键值对集合，类似于 Java 中的 `Map<String, String>`。它适合存储对象类型的数据，比如一个用户的多个属性。

**典型应用**：缓存对象的部分字段（如用户昵称、头像、积分），更新时只更新某个字段而不是整个对象。

**常用命令**：`HSET`、`HGET`、`HGETALL`、`HDEL`、`HINCRBY`。

#### 2.1.3 List（列表）

List 是一个有序的字符串列表，底层是双向链表。可以从左或右插入元素。

**典型应用**：消息队列（LPUSH + BRPOP）、最新消息列表（如用户通知）、时间线数据。

**常用命令**：`LPUSH`、`RPUSH`、`LPOP`、`RPOP`、`LRANGE`、`LLEN`。

#### 2.1.4 Set（集合）

Set 是无序的字符串集合，且元素唯一（自动去重）。

**典型应用**：标签系统（用户兴趣标签）、好友关系（共同好友）、数据去重。

**常用命令**：`SADD`、`SREM`、`SMEMBERS`、`SISMEMBER`、`SINTER`（交集）、`SUNION`（并集）。

#### 2.1.5 ZSet（有序集合）

ZSet 和 Set 一样元素唯一，但每个元素关联一个 double 类型的分数（score），Redis 按分数从小到大排序。

**典型应用**：排行榜（积分排名）、延时队列（时间戳作为分数）、滑动窗口限流。

**常用命令**：`ZADD`、`ZRANGE`、`ZREVRANGE`、`ZRANK`、`ZSCORE`、`ZINCRBY`。

### 2.2 Spring Data Redis 缓存注解

Spring 从 3.1 开始提供了基于注解的声明式缓存。你不需要手动编写 `set` / `get` 代码，只需要在方法上添加注解，Spring 会自动将返回值存入缓存，或从缓存中读取。

#### 2.2.1 @Cacheable

`@Cacheable` 用于标注查询方法。执行前会先检查缓存中是否存在，如果存在则直接返回缓存值，不再执行方法体；如果不存在则执行方法，并将返回值存入缓存。

**核心属性**：

- `value` / `cacheNames`：缓存名称，相当于命名空间
- `key`：缓存的键，支持 SpEL 表达式
- `condition`：满足条件才缓存
- `unless`：满足条件则不缓存
- `sync`：是否同步模式（防止缓存击穿）

#### 2.2.2 @CacheEvict

`@CacheEvict` 用于标注更新或删除方法，执行后清除指定缓存。

**核心属性**：

- `allEntries`：是否清除该缓存名称下的所有条目
- `beforeInvocation`：是否在方法执行前清除（默认方法执行后）

#### 2.2.3 @CachePut

`@CachePut` 用于标注更新方法，无论缓存是否存在，都会执行方法并将返回值存入缓存。和 `@Cacheable` 不同，它不会检查缓存。

#### 2.2.4 @Caching

当需要同时应用多个缓存注解时，可以用 `@Caching` 组合。

### 2.3 分布式锁原理

#### 2.3.1 为什么 synchronized 不行？

`synchronized` 是 JVM 级别的锁，只对同一个进程内的线程有效。在微服务架构下，多个服务实例部署在不同的 JVM 中，每个实例都有自己的锁，无法互斥。

#### 2.3.2 Redis 分布式锁原理

Redis 实现分布式锁的核心是 `SETNX` 命令（SET if Not eXists）：

```
SETNX lock_key value  // 如果 key 不存在则设置，返回 1；存在则返回 0
```

但仅仅 `SETNX` 是不够的，还需要考虑：

- **过期时间**：防止锁持有者崩溃导致死锁
- **原子操作**：SETNX 和设置过期时间必须是原子操作
- **锁续期**：业务执行时间超过锁过期时间时需要自动续期
- **可重入**：同一个线程可以重复获取同一把锁

Redisson 完美解决了上述所有问题，它提供了 `RLock` 接口，实现了自动续期（看门狗 Watchdog）、可重入、公平锁等功能。

#### 2.3.3 Redisson 看门狗机制

Redisson 的看门狗（Watchdog）是一个后台线程，它会在锁的过期时间还剩 1/3 时自动续期。默认锁的过期时间是 30 秒，看门狗每 10 秒检查一次，如果业务还在执行，就将锁的过期时间重置为 30 秒。这样业务方法执行多久，锁就能持有多久，不会提前过期。

### 2.4 Lock4j

Lock4j 是一个基于 Redisson 的轻量级分布式锁框架，提供了 `@Lock4j` 注解，让你在方法上只需要一行注解就能获取分布式锁，而无需手动编写 `lock()` 和 `unlock()` 代码。

---

## 3. 从零搭建代码

下面我们创建一个全新的 Spring Boot 项目，演示 Redis 和 Redisson 的完整用法。

### 3.1 项目结构

```
hello-redis/
├── pom.xml
├── src/main/java/com/example/helloredis/
│   ├── HelloRedisApplication.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   └── RedissonConfig.java
│   ├── service/
│   │   ├── RedisDataTypeService.java      // 操作五种数据类型
│   │   ├── CacheService.java              // 使用 @Cacheable 等注解
│   │   └── LockService.java               // 分布式锁演示
│   └── controller/
│       └── TestController.java
├── src/main/resources/
│   ├── application.yml
│   └── application-dev.yml
└── src/test/java/com/example/helloredis/
    └── HelloRedisTest.java
```

### 3.2 pom.xml：引入依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <!-- 父工程：使用 Spring Boot 3.2.x -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>hello-redis</artifactId>
    <version>1.0.0</version>
    <name>hello-redis</name>
    <description>Redis + Redisson 入门示例项目</description>

    <properties>
        <java.version>17</java.version>
        <!-- Redisson 版本，需要与 Spring Boot 3.x 兼容 -->
        <redisson.version>3.30.0</redisson.version>
        <!-- Lock4j 版本 -->
        <lock4j.version>2.2.7</lock4j.version>
    </properties>

    <dependencies>
        <!-- 1. Spring Boot Web Starter：提供 REST API 支持 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 2. Spring Data Redis Starter：集成 Redis 的核心依赖 -->
        <!-- 默认使用 Lettuce 作为 Redis 客户端（非 Jedis） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- 3. 缓存依赖：启用 Spring 缓存抽象层，配合 @Cacheable 等注解使用 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>

        <!-- 4. Redisson 原生依赖：提供分布式锁、RateLimiter 等高级功能 -->
        <!-- spring-boot-starter-data-redis 中不包含 Redisson，需要单独引入 -->
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
            <version>${redisson.version}</version>
        </dependency>

        <!-- 5. Lock4j 依赖：简化分布式锁注解 -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>lock4j-redisson-spring-boot-starter</artifactId>
            <version>${lock4j.version}</version>
        </dependency>

        <!-- 6. Jackson 序列化：Redis 存储对象时需要序列化 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- 7. Commons Pool2：Lettuce 连接池依赖 -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>

        <!-- 8. Lombok：简化代码 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 9. Test 依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**依赖说明**：

- `spring-boot-starter-data-redis`：Spring 官方提供的 Redis 集成，默认使用 Lettuce 客户端（非阻塞、响应式），替代了旧版的 Jedis。
- `redisson-spring-boot-starter`：Redisson 官方提供的 Spring Boot Starter，自动配置 RedissonClient Bean。
- `lock4j-redisson-spring-boot-starter`：基于 Redisson 的分布式锁注解框架，一行注解即可加锁。
- `spring-boot-starter-cache`：Spring 缓存抽象层，用于激活 `@Cacheable` 等注解。

### 3.3 application.yml：配置文件

```yaml
# 服务器配置
server:
  port: 8080

# Spring 配置
spring:
  application:
    name: hello-redis

  # Redis 数据源配置
  # 使用 kebab-case（短横线命名法）风格
  data:
    redis:
      # Redis 服务器地址，单机模式
      host: 127.0.0.1
      # Redis 端口，默认 6379
      port: 6379
      # Redis 密码，如果没有设置密码则留空
      password: ''
      # 连接超时时间，单位毫秒
      connect-timeout: 5000
      # 读取超时时间，单位毫秒
      timeout: 5000
      # 使用的数据库索引，默认 0（Redis 有 0-15 共 16 个数据库）
      database: 0
      # Lettuce 客户端连接池配置
      lettuce:
        pool:
          # 连接池最大连接数，建议根据并发量调整
          max-active: 16
          # 连接池最大空闲连接数
          max-idle: 8
          # 连接池最小空闲连接数
          min-idle: 4
          # 获取连接的最大等待时间，-1 表示无限等待
          max-wait: 3000ms

  # 缓存配置
  cache:
    # 缓存类型：使用 Redis 作为缓存实现
    type: redis
    # 缓存名称前缀，区分不同业务模块的缓存
    redis:
      # 全局缓存过期时间，单位毫秒，默认永不过期
      # 这里设置为 1 小时
      time-to-live: 3600000
      # 是否允许缓存空值，防止缓存穿透
      cache-null-values: true
      # 是否使用键前缀
      use-key-prefix: true
      # 键前缀分隔符
      key-prefix: 'hello-redis:'

# Redisson 配置
# Redisson 的配置项以 redisson 开头，支持多种配置方式
redisson:
  # 单机模式配置
  # 格式：redis://host:port
  # 如果 Redis 启用了 SSL，则使用 rediss:// 协议
  address: redis://127.0.0.1:6379
  # 密码，和 spring.data.redis.password 保持一致
  password: ''
  # 连接池大小
  connection-pool-size: 64
  # 最小空闲连接数
  connection-minimum-idle-size: 10
  # 空闲连接超时时间，单位毫秒
  idle-connection-timeout: 10000
  # 连接超时时间，单位毫秒
  connect-timeout: 5000
  # 命令等待超时时间，单位毫秒
  timeout: 5000
  # 重试次数
  retry-attempts: 3
  # 重试间隔，单位毫秒
  retry-interval: 1500

# Lock4j 配置
lock4j:
  # Lock4j 默认使用的锁执行器
  # 从 2.0 版本开始，默认使用 RedissonLockExecutor
  executor: com.baomidou.lock.executor.RedissonLockExecutor
  # 获取锁的默认超时时间，单位毫秒
  # 超过这个时间未获取到锁则抛出异常
  acquire-timeout: 3000
  # 锁的默认过期时间，单位毫秒
  # 业务执行超过这个时间锁会自动释放，防止死锁
  expire: 30000
```

**配置说明**：

- `spring.data.redis`：Spring Data Redis 的配置，用于 RedisTemplate 和缓存注解。
- `spring.cache.redis.time-to-live`：@Cacheable 注解缓存的默认过期时间，防止缓存无限膨胀。
- `redisson`：Redisson 客户端配置，用于分布式锁等高级功能。
- `lock4j`：Lock4j 注解的全局默认配置，也可以在 `@Lock4j` 注解上单独指定。

### 3.4 RedisConfig：配置 RedisTemplate 和序列化

```java
package com.example.helloredis.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 配置类
 * <p>
 * 核心职责：
 * 1. 配置 RedisTemplate，设置 key 和 value 的序列化方式
 * 2. 配置 RedisCacheManager，支持 @Cacheable 等注解
 * 3. 启用 Spring 缓存抽象（@EnableCaching）
 * <p>
 * 为什么需要自定义序列化？
 * 默认的 JdkSerializationRedisSerializer 可读性差、体积大。
 * 我们改用 JSON 序列化，便于跨语言调试和查看。
 */
@Configuration
// 启用 Spring 缓存注解功能
// 这个注解是必须的，否则 @Cacheable 等注解不会生效
@EnableCaching
public class RedisConfig {

    /**
     * 配置 RedisTemplate Bean
     * <p>
     * RedisTemplate 是 Spring Data Redis 的核心操作类，
     * 封装了对 Redis 五种数据类型的操作。
     * 默认的 RedisTemplate 使用 JdkSerializationRedisSerializer，
     * 我们替换为 StringRedisSerializer（key）和
     * GenericJackson2JsonRedisSerializer（value）。
     *
     * @param redisConnectionFactory Redis 连接工厂，由 Spring Boot 自动注入
     * @return 配置好的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory) {

        // 创建 RedisTemplate 实例，泛型指定 key 为 String，value 为 Object
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // 设置连接工厂，必须指定
        template.setConnectionFactory(redisConnectionFactory);

        // ------------------ 配置序列化方式 ------------------

        // 1. 创建字符串序列化器
        // 用于 key 的序列化，因为 Redis 的 key 通常是字符串
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 2. 创建 JSON 序列化器
        // 用于 value 的序列化，将 Java 对象序列化为 JSON 字符串
        // GenericJackson2JsonRedisSerializer 会自动在 JSON 中保存类型信息
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        // 3. 设置 key 的序列化方式为 String
        // 这样 Redis 中的 key 就是可读的字符串，如 "user:1001"
        template.setKeySerializer(stringSerializer);

        // 4. 设置 value 的序列化方式为 JSON
        // 这样 Redis 中的 value 是 JSON 格式，方便查看和调试
        template.setValueSerializer(jsonSerializer);

        // 5. 设置 hash key 的序列化方式为 String
        // Hash 结构中的字段名也是字符串
        template.setHashKeySerializer(stringSerializer);

        // 6. 设置 hash value 的序列化方式为 JSON
        template.setHashValueSerializer(jsonSerializer);

        // 调用 afterPropertiesSet 完成初始化
        // 这是一个标准流程，确保所有配置生效
        template.afterPropertiesSet();

        return template;
    }

    /**
     * 配置 RedisCacheManager Bean
     * <p>
     * RedisCacheManager 是 Spring 缓存抽象的具体实现，
     * 负责管理 @Cacheable 等注解的缓存操作。
     * 这里我们自定义了缓存配置，包括：
     * - 默认过期时间
     * - 不同缓存名称的个性化过期时间
     * - 序列化方式
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @return 配置好的 RedisCacheManager 实例
     */
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory) {

        // ------------------ 默认缓存配置 ------------------

        // 创建 RedisCacheConfiguration 的默认配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                // 设置 key 的序列化方式为 String
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                // 设置 value 的序列化方式为 JSON
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(
                                        new GenericJackson2JsonRedisSerializer()))
                // 设置全局默认过期时间：1 小时
                .entryTtl(Duration.ofHours(1))
                // 禁止缓存 null 值，可以防止缓存穿透
                // 但这里我们设置为允许，因为有 @Cacheable(unless = ...) 控制
                .disableCachingNullValues()
                // 使用缓存名称作为前缀，方便区分不同业务
                .prefixCacheNameWith("hello-redis:");

        // ------------------ 个性化缓存配置 ------------------

        // 某些缓存需要不同的过期时间，可以在这里配置
        Map<String, RedisCacheConfiguration> configMap = new HashMap<>();

        // 用户缓存：过期时间 30 分钟
        configMap.put("users",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 配置缓存：缓存时间较长，1 小时
        configMap.put("configs",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        // 验证码缓存：过期时间很短，5 分钟
        configMap.put("captcha",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 会话缓存：过期时间 2 小时
        configMap.put("sessions",
                defaultConfig.entryTtl(Duration.ofHours(2)));

        // ------------------ 构建 CacheManager ------------------

        return RedisCacheManager.builder(redisConnectionFactory)
                // 设置默认缓存配置
                .cacheDefaults(defaultConfig)
                // 设置个性化缓存配置
                .withInitialCacheConfigurations(configMap)
                .build();
    }
}
```

**代码要点**：

1. **为什么自定义序列化**：Spring Boot 自动配置的 RedisTemplate 使用 JdkSerializationRedisSerializer，序列化后的数据是二进制格式，不可读且体积大。改用 JSON 序列化后，可以在 Redis 命令行中直接查看和修改数据。

2. **GenericJackson2JsonRedisSerializer 的优势**：它会在序列化时在 JSON 中存入 `@class` 字段记录类型信息，反序列化时能还原为正确的 Java 类型。

3. **RedisCacheManager 的个性化配置**：不同的业务数据有不同的缓存时效。验证码 5 分钟过期，用户信息 30 分钟，系统配置 1 小时。通过 `withInitialCacheConfigurations` 可以为每个缓存名称设置独立的过期时间。

### 3.5 RedissonConfig：配置 RedissonClient

```java
package com.example.helloredis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置类
 * <p>
 * Redisson 是 Redis 的 Java 客户端，提供了分布式锁、分布式集合、
 * 分布式原子变量等高级功能。
 * 相比 Jedis 和 Lettuce，Redisson 最大的优势是提供了丰富的分布式数据结构。
 * <p>
 * 虽然 redisson-spring-boot-starter 会自动配置 RedissonClient，
 * 但为了更灵活地控制配置，我们手动创建 RedissonClient Bean。
 */
@Configuration
public class RedissonConfig {

    // 从 application.yml 中读取 Redis 地址
    @Value("${redisson.address}")
    private String address;

    // 从 application.yml 中读取 Redis 密码
    @Value("${redisson.password}")
    private String password;

    // 从 application.yml 中读取连接池大小
    @Value("${redisson.connection-pool-size}")
    private int connectionPoolSize;

    // 从 application.yml 中读取最小空闲连接数
    @Value("${redisson.connection-minimum-idle-size}")
    private int connectionMinimumIdleSize;

    // 从 application.yml 中读取连接超时时间
    @Value("${redisson.connect-timeout}")
    private int connectTimeout;

    // 从 application.yml 中读取命令超时时间
    @Value("${redisson.timeout}")
    private int timeout;

    // 从 application.yml 中读取重试次数
    @Value("${redisson.retry-attempts}")
    private int retryAttempts;

    // 从 application.yml 中读取重试间隔
    @Value("${redisson.retry-interval}")
    private int retryInterval;

    /**
     * 创建 RedissonClient Bean
     * <p>
     * RedissonClient 是 Redisson 的核心接口，
     * 通过它可以获取 RLock、RAtomicLong、RMap 等各种分布式对象。
     *
     * @return 配置好的 RedissonClient 实例
     */
    @Bean
    public RedissonClient redissonClient() {

        // 创建 Redisson 配置对象
        Config config = new Config();

        // 使用单机模式连接 Redis
        // 如果是集群模式，可以使用 config.useClusterServers()
        config.useSingleServer()
                // 设置 Redis 地址，格式：redis://127.0.0.1:6379
                .setAddress(address)
                // 设置密码，如果没有密码则传 null
                .setPassword(password.isEmpty() ? null : password)
                // 设置连接池大小
                .setConnectionPoolSize(connectionPoolSize)
                // 设置最小空闲连接数
                .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                // 设置连接超时时间
                .setConnectTimeout(connectTimeout)
                // 设置命令等待超时时间
                .setTimeout(timeout)
                // 设置重试次数
                .setRetryAttempts(retryAttempts)
                // 设置重试间隔
                .setRetryInterval(retryInterval);

        // 创建 RedissonClient 实例
        // Redisson 实例是线程安全的，整个应用只需要一个实例
        return Redisson.create(config);
    }
}
```

**代码要点**：

- RedissonClient 是线程安全的，一个应用只需要创建一次，所以声明为 `@Bean` 单例。
- 单机模式使用 `useSingleServer()`，集群模式使用 `useClusterServers()`，哨兵模式使用 `useSentinelServers()`。
- Redisson 的配置项较多，建议从 yml 中读取，不要硬编码。

### 3.6 RedisDataTypeService：操作五种数据类型

```java
package com.example.helloredis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 五种数据类型操作演示服务
 * <p>
 * 本 Service 演示了如何使用 RedisTemplate 操作 Redis 的
 * String、Hash、List、Set、ZSet 五种基础数据类型。
 * 每个方法都包含完整的业务场景说明。
 */
@Slf4j
@Service
// 使用构造器注入，替代 @Autowired
@RequiredArgsConstructor
public class RedisDataTypeService {

    // 注入 RedisTemplate，泛型为 <String, Object>
    // key 统一为 String 类型，value 为 Object 类型
    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== 1. String 类型操作 ====================

    /**
     * 缓存字符串数据
     * <p>
     * 业务场景：缓存用户登录令牌，设置过期时间 30 分钟。
     * String 类型是最常用的缓存类型，适合存储单个值。
     *
     * @param key   缓存键，如 "token:user:1001"
     * @param value 缓存值，如 "eyJhbGciOiJIUzI1NiJ9..."
     * @param ttl   过期时间，单位秒
     */
    public void setString(String key, Object value, long ttl) {
        // opsForValue() 获取 String 类型操作对象
        // set() 方法带过期时间参数，使用 TimeUnit.SECONDS 指定单位为秒
        redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
        log.info("String 缓存已设置：key={}, ttl={}秒", key, ttl);
    }

    /**
     * 获取字符串缓存
     *
     * @param key 缓存键
     * @return 缓存值，如果不存在则返回 null
     */
    public Object getString(String key) {
        // opsForValue().get() 获取缓存值
        // 如果 key 不存在，返回 null
        Object value = redisTemplate.opsForValue().get(key);
        log.info("String 缓存已获取：key={}, value={}", key, value);
        return value;
    }

    /**
     * 递增计数器
     * <p>
     * 业务场景：文章阅读数、点赞数、限流计数器。
     * increment() 是原子操作，线程安全。
     *
     * @param key 计数器键，如 "article:view:1001"
     * @return 递增后的值
     */
    public Long incrementCounter(String key) {
        // opsForValue().increment() 对 key 的值加 1
        // 如果 key 不存在，则先将 key 的值设为 0，再执行递增
        Long count = redisTemplate.opsForValue().increment(key);
        log.info("计数器递增：key={}, 当前值={}", key, count);
        return count;
    }

    // ==================== 2. Hash 类型操作 ====================

    /**
     * 缓存用户信息到 Hash
     * <p>
     * 业务场景：存储用户的基本信息（昵称、头像、积分等）。
     * Hash 类型适合存储对象类型的结构化数据。
     * 相比 String 序列化整个对象，Hash 可以单独获取或修改某个字段。
     *
     * @param key    缓存键，如 "user:1001"
     * @param field  字段名，如 "nickname"
     * @param value  字段值，如 "张三"
     */
    public void setHashField(String key, String field, Object value) {
        // opsForHash() 获取 Hash 类型操作对象
        // put() 设置单个字段的值
        redisTemplate.opsForHash().put(key, field, value);
        log.info("Hash 字段已设置：key={}, field={}, value={}", key, field, value);
    }

    /**
     * 批量设置 Hash 字段
     * <p>
     * 业务场景：注册用户时，一次性将用户多个属性写入缓存。
     *
     * @param key    缓存键
     * @param map    字段名和字段值的映射
     */
    public void setHashFields(String key, Map<String, Object> map) {
        // putAll() 批量设置多个字段，比逐个 put() 性能更好
        redisTemplate.opsForHash().putAll(key, map);
        log.info("Hash 批量设置完成：key={}, 字段数={}", key, map.size());
    }

    /**
     * 获取 Hash 的单个字段
     *
     * @param key   缓存键
     * @param field 字段名
     * @return 字段值
     */
    public Object getHashField(String key, String field) {
        // get() 获取单个字段的值
        Object value = redisTemplate.opsForHash().get(key, field);
        log.info("Hash 字段已获取：key={}, field={}, value={}", key, field, value);
        return value;
    }

    /**
     * 获取 Hash 的所有字段
     *
     * @param key 缓存键
     * @return 所有字段的键值对
     */
    public Map<Object, Object> getAllHashFields(String key) {
        // entries() 获取所有字段和值，返回 Map
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        log.info("Hash 所有字段：key={}, 数据={}", key, entries);
        return entries;
    }

    // ==================== 3. List 类型操作 ====================

    /**
     * 向列表左侧推送消息
     * <p>
     * 业务场景：消息队列，LPUSH 从左侧写入，BRPOP 从右侧阻塞读取。
     * List 类型可以作为轻量级消息队列使用。
     *
     * @param key   列表键，如 "queue:notifications"
     * @param value 消息内容
     */
    public void pushToListLeft(String key, Object value) {
        // opsForList() 获取 List 类型操作对象
        // leftPush() 从列表左侧插入元素
        Long size = redisTemplate.opsForList().leftPush(key, value);
        log.info("List 左推成功：key={}, value={}, 当前长度={}", key, value, size);
    }

    /**
     * 从列表右侧弹出消息
     *
     * @param key 列表键
     * @return 弹出的元素
     */
    public Object popFromListRight(String key) {
        // rightPop() 从列表右侧弹出元素
        // 如果列表为空，返回 null
        Object value = redisTemplate.opsForList().rightPop(key);
        log.info("List 右弹成功：key={}, value={}", key, value);
        return value;
    }

    /**
     * 获取列表指定范围的元素
     * <p>
     * 业务场景：分页查询最新消息列表。
     *
     * @param key   列表键
     * @param start 起始索引（从 0 开始）
     * @param end   结束索引（-1 表示最后一个元素）
     * @return 元素列表
     */
    public List<Object> getListRange(String key, long start, long end) {
        // range() 获取指定范围的元素
        List<Object> range = redisTemplate.opsForList().range(key, start, end);
        log.info("List 范围查询：key={}, start={}, end={}, 结果数={}",
                key, start, end, range != null ? range.size() : 0);
        return range;
    }

    // ==================== 4. Set 类型操作 ====================

    /**
     * 向集合添加元素
     * <p>
     * 业务场景：用户标签系统，每个用户可以有多个标签，自动去重。
     * Set 类型会自动去除重复元素，适合做集合运算。
     *
     * @param key    集合键，如 "user:tags:1001"
     * @param values 要添加的元素，可变参数
     */
    public void addToSet(String key, Object... values) {
        // opsForSet() 获取 Set 类型操作对象
        // add() 添加一个或多个元素，返回成功添加的数量
        Long count = redisTemplate.opsForSet().add(key, values);
        log.info("Set 添加成功：key={}, 添加数量={}", key, count);
    }

    /**
     * 判断元素是否在集合中
     *
     * @param key   集合键
     * @param value 要判断的元素
     * @return true 表示存在，false 表示不存在
     */
    public boolean isMemberOfSet(String key, Object value) {
        // isMember() 判断元素是否在集合中
        boolean isMember = Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(key, value));
        log.info("Set 成员判断：key={}, value={}, 结果={}", key, value, isMember);
        return isMember;
    }

    /**
     * 获取集合的所有元素
     *
     * @param key 集合键
     * @return 集合中的所有元素
     */
    public Set<Object> getSetMembers(String key) {
        // members() 返回集合中的所有元素
        Set<Object> members = redisTemplate.opsForSet().members(key);
        log.info("Set 所有成员：key={}, 数量={}", key, members != null ? members.size() : 0);
        return members;
    }

    /**
     * 计算两个集合的交集
     * <p>
     * 业务场景：查找共同好友、共同标签。
     *
     * @param key1 集合键 1
     * @param key2 集合键 2
     * @return 交集结果
     */
    public Set<Object> intersectSets(String key1, String key2) {
        // intersect() 计算两个集合的交集
        Set<Object> intersect = redisTemplate.opsForSet().intersect(key1, key2);
        log.info("Set 交集计算：key1={}, key2={}, 结果={}", key1, key2, intersect);
        return intersect;
    }

    // ==================== 5. ZSet 类型操作 ====================

    /**
     * 向有序集合添加元素
     * <p>
     * 业务场景：游戏排行榜，score 为玩家积分，Redis 自动按分数排序。
     * ZSet 的每个元素都有一个分数，按分数从小到大排序。
     *
     * @param key   有序集合键，如 "leaderboard:game1"
     * @param value 元素，如 "player:1001"
     * @param score 分数，如 9999
     */
    public void addToZSet(String key, Object value, double score) {
        // opsForZSet() 获取 ZSet 类型操作对象
        // add() 添加元素并指定分数
        // 如果元素已存在，则更新其分数
        Boolean added = redisTemplate.opsForZSet().add(key, value, score);
        log.info("ZSet 添加成功：key={}, value={}, score={}, 新增={}",
                key, value, score, added);
    }

    /**
     * 获取排行榜（从高到低）
     * <p>
     * 业务场景：显示积分排行榜，从高到低排列。
     *
     * @param key  有序集合键
     * @param topN 取前 N 名
     * @return 排行榜列表，按分数从高到低
     */
    public Set<Object> getTopNFromZSet(String key, long topN) {
        // reverseRange() 按分数从高到低获取元素
        // 注意：ZSet 默认按分数升序排列，reverseRange 是降序
        Set<Object> top = redisTemplate.opsForZSet()
                .reverseRange(key, 0, topN - 1);
        log.info("ZSet 排行榜：key={}, topN={}, 结果={}", key, topN, top);
        return top;
    }

    /**
     * 获取元素的分数
     *
     * @param key   有序集合键
     * @param value 元素
     * @return 该元素的分数
     */
    public Double getScoreFromZSet(String key, Object value) {
        // score() 获取指定元素的分数
        Double score = redisTemplate.opsForZSet().score(key, value);
        log.info("ZSet 获取分数：key={}, value={}, score={}", key, value, score);
        return score;
    }

    /**
     * 增加元素的分数
     * <p>
     * 业务场景：玩家完成一局游戏后增加积分。
     *
     * @param key    有序集合键
     * @param value  元素
     * @param delta  增加的分数
     * @return 增加后的分数
     */
    public Double incrementZSetScore(String key, Object value, double delta) {
        // incrementScore() 原子性地增加分数
        // 如果元素不存在，则创建并设置分数为 delta
        Double score = redisTemplate.opsForZSet()
                .incrementScore(key, value, delta);
        log.info("ZSet 分数增加：key={}, value={}, 增量={}, 新分数={}",
                key, value, delta, score);
        return score;
    }
}
```

### 3.7 CacheService：使用缓存注解

```java
package com.example.helloredis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 缓存注解演示服务
 * <p>
 * 本 Service 演示了 Spring 缓存注解的声明式使用方式。
 * 通过 @Cacheable、@CacheEvict、@CachePut 注解，
 * 你不需要编写任何 Redis 操作代码，Spring 会自动处理缓存逻辑。
 * <p>
 * 模拟一个简单的用户服务：数据存储在内存 Map 中，
 * 通过缓存注解将查询结果缓存到 Redis。
 */
@Slf4j
@Service
public class CacheService {

    // 模拟数据库，存储用户信息
    // 实际项目中，这里应该是 UserRepository 或 UserMapper
    private final Map<Long, Map<String, Object>> database = new HashMap<>();

    /**
     * 初始化模拟数据
     * <p>
     * 在真实项目中，数据存储在 MySQL 中。
     * 这里用内存 Map 模拟数据库。
     */
    {
        // 模拟用户 1001
        Map<String, Object> user1 = new HashMap<>();
        user1.put("id", 1001L);
        user1.put("username", "zhangsan");
        user1.put("nickname", "张三");
        user1.put("email", "zhangsan@example.com");
        user1.put("score", 1000);
        database.put(1001L, user1);

        // 模拟用户 1002
        Map<String, Object> user2 = new HashMap<>();
        user2.put("id", 1002L);
        user2.put("username", "lisi");
        user2.put("nickname", "李四");
        user2.put("email", "lisi@example.com");
        user2.put("score", 2000);
        database.put(1002L, user2);
    }

    /**
     * 根据用户 ID 查询用户信息
     * <p>
     * 使用 @Cacheable 注解：
     * - 第一次调用时，方法执行，结果缓存到 Redis
     * - 第二次调用时，直接从 Redis 返回，方法不执行
     * - 缓存名称：users（对应 RedisCacheManager 中的配置）
     * - 缓存键：用户 ID（SpEL 表达式 #id 表示方法参数）
     * <p>
     * 缓存键格式：hello-redis:users::1001
     * 其中 hello-redis: 是前缀，users 是缓存名称，:: 是分隔符，1001 是用户 ID
     *
     * @param id 用户 ID
     * @return 用户信息 Map
     */
    @Cacheable(value = "users", key = "#id")
    public Map<String, Object> getUserById(Long id) {
        // 模拟数据库查询耗时
        // 实际项目中，这里可能是复杂的 SQL 查询或远程调用
        log.info("模拟从数据库查询用户：id={}", id);
        try {
            // 模拟数据库查询延迟 500ms
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 从模拟数据库获取数据
        Map<String, Object> user = database.get(id);

        // 如果用户不存在，返回 null
        // 注意：由于我们配置了 disableCachingNullValues，null 不会被缓存
        // 这样做的目的是防止缓存穿透（大量请求缓存中不存在的数据）
        if (user == null) {
            log.warn("用户不存在：id={}", id);
            return null;
        }

        log.info("数据库查询成功：id={}, user={}", id, user);
        return user;
    }

    /**
     * 更新用户信息
     * <p>
     * 使用 @CachePut 注解：
     * - 每次都会执行方法体
     * - 每次执行后都会将返回值更新到缓存中
     * - 适用于更新数据的场景，保证缓存和数据库一致
     * <p>
     * 注意：@CachePut 和 @Cacheable 的区别
     * - @Cacheable：先查缓存，缓存有就返回，不执行方法
     * - @CachePut：总是执行方法，执行后更新缓存
     *
     * @param id      用户 ID
     * @param newData 新的用户数据
     * @return 更新后的用户信息
     */
    @CachePut(value = "users", key = "#id")
    public Map<String, Object> updateUser(Long id, Map<String, Object> newData) {
        // 模拟更新数据库
        log.info("模拟更新数据库用户：id={}, newData={}", id, newData);

        // 从模拟数据库中获取已有数据
        Map<String, Object> user = database.get(id);
        if (user == null) {
            throw new RuntimeException("用户不存在：" + id);
        }

        // 更新字段
        user.putAll(newData);

        // 将更新后的数据写回模拟数据库
        database.put(id, user);

        // 方法返回后，@CachePut 会将返回值写入 Redis 缓存
        // 这样下次查询时就能获取到最新的数据
        log.info("数据库更新成功：id={}, user={}", id, user);
        return user;
    }

    /**
     * 删除用户信息
     * <p>
     * 使用 @CacheEvict 注解：
     * - 方法执行后，清除指定 key 的缓存
     * - 保证下次查询时从数据库重新加载
     * <p>
     * 注意：@CacheEvict 默认在方法执行成功后清除缓存
     * 如果方法抛出异常，缓存不会被清除（防止缓存和数据库不一致）
     *
     * @param id 用户 ID
     */
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        // 模拟从数据库删除用户
        log.info("模拟从数据库删除用户：id={}", id);

        // 从模拟数据库中移除
        database.remove(id);

        // 方法执行后，Spring 会自动清除 key 为 "users::id" 的缓存
        log.info("数据库删除成功，缓存已清除：id={}", id);
    }

    /**
     * 清除所有用户缓存
     * <p>
     * 使用 @CacheEvict 注解的 allEntries 属性：
     * - allEntries = true 表示清除该缓存名称下的所有条目
     * - 适用于批量更新数据后，需要清空整个缓存区域
     * <p>
     * 注意：谨慎使用 allEntries，它会清空整个缓存区域
     * 如果缓存中有大量数据，清空操作可能导致瞬间的数据库压力
     */
    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUserCache() {
        // 这个方法不需要执行任何操作
        // 注解的 allEntries 属性会清除 "users" 缓存下的所有条目
        log.info("已清除所有用户缓存");
    }

    /**
     * 获取用户信息，带条件缓存
     * <p>
     * 使用 @Cacheable 注解的 condition 和 unless 属性：
     * - condition = "#id > 0"：只有 id 大于 0 时才缓存
     * - unless = "#result == null"：如果结果为 null 则不缓存
     * <p>
     * 这两个属性提供了更精细的缓存控制：
     * - condition：在方法执行前判断，满足条件才走缓存逻辑
     * - unless：在方法执行后判断，满足条件则不缓存结果
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    @Cacheable(value = "users", key = "#id",
            condition = "#id > 0", unless = "#result == null")
    public Map<String, Object> getUserByIdConditional(Long id) {
        log.info("模拟从数据库查询用户（带条件缓存）：id={}", id);
        return database.get(id);
    }

    /**
     * 获取用户信息，同步模式
     * <p>
     * 使用 @Cacheable 的 sync 属性：
     * - sync = true 表示开启同步模式
     * - 防止缓存击穿（高并发下同一个 key 的缓存失效时，
     *   只让一个线程去查询数据库，其他线程等待）
     * <p>
     * 缓存击穿 vs 缓存穿透 vs 缓存雪崩：
     * - 缓存击穿：热点 key 失效，大量请求打到数据库
     * - 缓存穿透：查询不存在的数据，缓存和数据库都没有
     * - 缓存雪崩：大量 key 同时过期，或 Redis 宕机
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    @Cacheable(value = "users", key = "#id", sync = true)
    public Map<String, Object> getUserByIdSync(Long id) {
        log.info("模拟从数据库查询用户（同步模式）：id={}", id);
        try {
            // 模拟数据库查询延迟
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return database.get(id);
    }
}
```

**代码要点**：

1. **@Cacheable 的缓存逻辑**：方法被调用时，Spring 通过 AOP 拦截，先检查 Redis 中是否有对应 key 的缓存。有则直接返回，没有则执行方法并缓存结果。

2. **@CachePut 的更新逻辑**：方法总是执行，返回值始终写入缓存。适用于需要保证缓存和数据一致性的更新操作。

3. **@CacheEvict 清除逻辑**：方法执行后清除指定 key 的缓存。`allEntries = true` 会清空整个缓存区域。

4. **sync 模式**：`sync = true` 时，Spring 使用 `synchronized` 或 Redis 的原子操作保证只有一个线程去加载数据，防止缓存击穿。

### 3.8 LockService：分布式锁演示

```java
package com.example.helloredis.service;

import com.baomidou.lock.annotation.Lock4j;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁演示服务
 * <p>
 * 本 Service 演示了两种使用分布式锁的方式：
 * 1. 使用 Redisson 原生 API（手动加锁、解锁）
 * 2. 使用 Lock4j 注解（声明式加锁，更简洁）
 * <p>
 * 业务场景：模拟库存扣减操作，演示分布式锁在并发场景下的作用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LockService {

    // 注入 RedissonClient，用于创建分布式锁
    private final RedissonClient redissonClient;

    // 模拟商品库存，初始为 100 件
    // 在分布式环境下，多个服务实例共享这个库存值
    private int stock = 100;

    // ==================== 方式一：Redisson 原生 API ====================

    /**
     * 扣减库存（使用 Redisson 原生 API）
     * <p>
     * 手动使用 Redisson 的 RLock 进行加锁和解锁。
     * 这种方式更加灵活，可以控制锁的粒度、过期时间、等待时间等。
     * <p>
     * 注意：锁的 key 是 "lock:stock"，所有需要扣减库存的实例
     * 竞争同一把锁，保证同一时间只有一个实例能操作库存。
     *
     * @param quantity 扣减数量
     * @return 扣减后的库存
     */
    public int deductStockWithRedisson(int quantity) {

        // 定义锁的 key
        // 命名规范：lock:业务名，方便区分不同业务的锁
        String lockKey = "lock:stock";

        // 获取 RLock 实例
        // RLock 实现了 java.util.concurrent.locks.Lock 接口
        // 使用起来和本地锁非常相似
        RLock lock = redissonClient.getLock(lockKey);

        // 尝试获取锁
        // tryLock 方法：
        //   第一个参数：等待获取锁的超时时间（10 秒）
        //   第二个参数：锁的过期时间（30 秒），超过这个时间自动释放
        //   第三个参数：时间单位
        // 在等待时间内获取不到锁，返回 false，不会一直阻塞
        boolean locked = false;
        try {
            locked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            // 判断是否成功获取到锁
            if (!locked) {
                // 获取锁失败，说明有其他实例正在操作库存
                log.warn("获取锁失败，请稍后重试");
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            // 成功获取到锁，执行临界区代码
            log.info("获取锁成功，开始扣减库存：quantity={}", quantity);

            // 检查库存是否充足
            if (stock < quantity) {
                log.warn("库存不足：当前库存={}, 需要={}", stock, quantity);
                throw new RuntimeException("库存不足");
            }

            // 模拟业务处理耗时
            // 实际项目中，这里可能是数据库操作
            TimeUnit.MILLISECONDS.sleep(100);

            // 扣减库存
            stock = stock - quantity;
            log.info("库存扣减成功：扣减={}, 剩余={}", quantity, stock);

            return stock;

        } catch (InterruptedException e) {
            // 线程被中断，恢复中断状态
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断", e);
        } finally {
            // 在 finally 块中释放锁
            // 确保无论是否发生异常，锁都会被释放
            // 注意：只有当前线程持有锁时才释放
            if (locked) {
                lock.unlock();
                log.info("锁已释放：lockKey={}", lockKey);
            }
        }
    }

    /**
     * 获取当前库存
     *
     * @return 当前库存数量
     */
    public int getCurrentStock() {
        return stock;
    }

    // ==================== 方式二：Lock4j 注解 ====================

    /**
     * 扣减库存（使用 Lock4j 注解）
     * <p>
     * Lock4j 的 @Lock4j 注解提供了声明式加锁。
     * 你只需要在方法上添加注解，框架会自动处理加锁和解锁。
     * 这种方式比手动 API 更加简洁，代码侵入性更低。
     * <p>
     * @Lock4j 注解参数说明：
     * - name：锁的 key，支持 SpEL 表达式
     * - keys：锁的附加 key，支持多个
     * - acquireTimeout：获取锁的超时时间，默认 3000ms
     * - expire：锁的过期时间，默认 30000ms
     * <p>
     * 注意：@Lock4j 注解的方法必须声明为 public
     * 因为 Lock4j 基于 Spring AOP，AOP 只能拦截 public 方法
     *
     * @param quantity 扣减数量
     * @return 扣减后的库存
     */
    @Lock4j(name = "lock:stock", acquireTimeout = 3000, expire = 30000)
    public int deductStockWithLock4j(int quantity) {

        // 方法执行时，Lock4j 会自动获取锁
        // 方法执行后，Lock4j 会自动释放锁
        // 不需要手动编写 lock() 和 unlock() 代码

        log.info("Lock4j 锁已获取，开始扣减库存：quantity={}", quantity);

        // 检查库存是否充足
        if (stock < quantity) {
            log.warn("库存不足：当前库存={}, 需要={}", stock, quantity);
            throw new RuntimeException("库存不足");
        }

        // 模拟业务处理耗时
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 扣减库存
        stock = stock - quantity;
        log.info("库存扣减成功：扣减={}, 剩余={}", quantity, stock);

        // 方法返回后，Lock4j 自动释放锁
        return stock;
    }

    /**
     * 带参数的 Lock4j 锁
     * <p>
     * 支持 SpEL 表达式，可以根据方法参数动态生成锁的 key。
     * 例如：@Lock4j(name = "lock:order:pay", keys = {"#orderId"})
     * 会生成锁 key 为 "lock:order:pay:1001"（如果 orderId = 1001）
     * <p>
     * 业务场景：每个订单的支付操作互不影响，但同一个订单的支付
     * 只能有一个线程处理。
     *
     * @param orderId 订单 ID
     * @param userId  用户 ID
     */
    @Lock4j(name = "lock:order:pay", keys = {"#orderId", "#userId"})
    public void payOrder(Long orderId, Long userId) {
        log.info("处理支付订单：orderId={}, userId={}", orderId, userId);

        // 模拟支付处理
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("支付完成：orderId={}", orderId);
    }

    /**
     * 重置库存（测试用）
     */
    public void resetStock() {
        this.stock = 100;
        log.info("库存已重置为 100");
    }
}
```

**代码要点**：

1. **Redisson 原生 API**：使用 `RLock.tryLock()` 手动加锁，在 `finally` 块中释放锁。这是最灵活的方式，适合复杂的锁逻辑。

2. **Lock4j 注解**：使用 `@Lock4j` 声明式加锁，框架自动处理加锁和解锁。这是最简洁的方式，适合简单的锁场景。

3. **锁的 key 设计**：锁的 key 应该具有业务含义，如 `lock:stock`、`lock:order:pay`。支持 SpEL 表达式动态生成 key，如 `lock:order:pay:#orderId`。

4. **看门狗机制**：Redisson 的锁默认启用看门狗，每 10 秒续期一次，保证业务执行期间锁不会过期。Lock4j 基于 Redisson，也继承了看门狗机制。

### 3.9 TestController：提供 REST API 测试

```java
package com.example.helloredis.controller;

import com.example.helloredis.service.CacheService;
import com.example.helloredis.service.LockService;
import com.example.helloredis.service.RedisDataTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 测试控制器
 * <p>
 * 提供 REST API 接口，方便快速验证 Redis 功能。
 * 通过 HTTP 请求调用各种 Service 方法。
 */
@RestController
@RequestMapping("/api/redis")
@RequiredArgsConstructor
public class TestController {

    private final RedisDataTypeService dataTypeService;
    private final CacheService cacheService;
    private final LockService lockService;

    // ==================== 数据类型测试 ====================

    /**
     * 测试 String 类型的缓存和读取
     */
    @GetMapping("/string")
    public String testString() {
        // 设置缓存，过期时间 60 秒
        dataTypeService.setString("hello", "世界, Redis!", 60);
        // 获取缓存
        Object value = dataTypeService.getString("hello");
        return "String 测试结果: " + value;
    }

    /**
     * 测试 Hash 类型
     */
    @GetMapping("/hash")
    public String testHash() {
        // 设置用户信息
        dataTypeService.setHashField("user:1001", "name", "张三");
        dataTypeService.setHashField("user:1001", "age", "25");
        // 获取所有字段
        Map<Object, Object> all = (Map<Object, Object>)
                dataTypeService.getAllHashFields("user:1001");
        return "Hash 测试结果: " + all;
    }

    /**
     * 测试 List 类型
     */
    @GetMapping("/list")
    public String testList() {
        // 推送三条消息
        dataTypeService.pushToListLeft("queue:msg", "消息1");
        dataTypeService.pushToListLeft("queue:msg", "消息2");
        dataTypeService.pushToListLeft("queue:msg", "消息3");
        // 弹出一条
        Object msg = dataTypeService.popFromListRight("queue:msg");
        return "List 测试结果 - 弹出: " + msg;
    }

    // ==================== 缓存注解测试 ====================

    /**
     * 测试 @Cacheable 注解
     * 第一次调用会执行方法，第二次直接从缓存读取
     */
    @GetMapping("/cache/user/{id}")
    public Map<String, Object> getUser(@PathVariable Long id) {
        // 第一次调用时，控制台会打印"模拟从数据库查询用户"
        // 第二次调用时，控制台不会打印，直接从缓存返回
        return cacheService.getUserById(id);
    }

    /**
     * 测试 @CachePut 注解（更新缓存）
     */
    @PutMapping("/cache/user/{id}")
    public Map<String, Object> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> newData) {
        // 更新用户信息，并同步更新缓存
        return cacheService.updateUser(id, newData);
    }

    /**
     * 测试 @CacheEvict 注解（清除缓存）
     */
    @DeleteMapping("/cache/user/{id}")
    public String deleteUser(@PathVariable Long id) {
        // 删除用户，并清除缓存
        cacheService.deleteUser(id);
        return "用户已删除，缓存已清除";
    }

    // ==================== 分布式锁测试 ====================

    /**
     * 测试 Redis 分布式锁（Redisson 原生 API）
     * 扣减库存
     */
    @PostMapping("/lock/deduct")
    public String deductStock(@RequestParam(defaultValue = "1") int quantity) {
        // 使用 Redisson 原生 API 扣减库存
        int remaining = lockService.deductStockWithRedisson(quantity);
        return "扣减成功，剩余库存: " + remaining;
    }

    /**
     * 测试 Lock4j 注解
     * 扣减库存
     */
    @PostMapping("/lock4j/deduct")
    public String deductStockWithLock4j(@RequestParam(defaultValue = "1") int quantity) {
        // 使用 Lock4j 注解扣减库存
        int remaining = lockService.deductStockWithLock4j(quantity);
        return "Lock4j 扣减成功，剩余库存: " + remaining;
    }

    /**
     * 获取当前库存
     */
    @GetMapping("/stock")
    public String getStock() {
        return "当前库存: " + lockService.getCurrentStock();
    }
}
```

### 3.10 主启动类

```java
package com.example.helloredis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类
 * <p>
 * Spring Boot 3.x 启动类，集成 Redis、Redisson、Lock4j。
 */
@SpringBootApplication
public class HelloRedisApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(HelloRedisApplication.class, args);
        System.out.println("===========================================");
        System.out.println("Hello Redis 应用已启动！");
        System.out.println("访问地址: http://localhost:8080/api/redis/string");
        System.out.println("===========================================");
    }
}
```

### 3.11 单元测试

```java
package com.example.helloredis;

import com.example.helloredis.service.CacheService;
import com.example.helloredis.service.LockService;
import com.example.helloredis.service.RedisDataTypeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis 功能集成测试类
 * <p>
 * 测试前请确保：
 * 1. Redis 服务已启动（默认 127.0.0.1:6379）
 * 2. 不需要外部依赖
 */
@SpringBootTest
// 按方法顺序执行，方便观察输出
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelloRedisTest {

    @Autowired
    private RedisDataTypeService dataTypeService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private LockService lockService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 测试准备 ====================

    /**
     * 每个测试方法执行前，重置库存
     */
    @BeforeEach
    void setUp() {
        lockService.resetStock();
    }

    // ==================== 数据类型测试 ====================

    @Test
    @Order(1)
    @DisplayName("测试 String 类型：缓存和读取")
    void testStringDataType() {
        // 准备测试数据
        String key = "test:string:hello";
        String value = "Hello Redis!";

        // 执行缓存操作，设置过期时间 10 秒
        dataTypeService.setString(key, value, 10);

        // 验证缓存读取
        Object cachedValue = dataTypeService.getString(key);
        // 断言读取的值和设置的值一致
        Assertions.assertEquals(value, cachedValue);

        // 测试计数器递增
        String counterKey = "test:counter:views";
        // 递增三次
        dataTypeService.incrementCounter(counterKey);
        dataTypeService.incrementCounter(counterKey);
        Long count = dataTypeService.incrementCounter(counterKey);

        // 验证递增后的值
        Assertions.assertEquals(3L, count);

        // 清理测试数据
        redisTemplate.delete(key);
        redisTemplate.delete(counterKey);
    }

    @Test
    @Order(2)
    @DisplayName("测试 Hash 类型：缓存对象字段")
    void testHashDataType() {
        // 准备测试数据
        String key = "test:hash:user:2001";

        // 设置单个字段
        dataTypeService.setHashField(key, "name", "测试用户");
        dataTypeService.setHashField(key, "age", "28");

        // 验证单个字段
        Object name = dataTypeService.getHashField(key, "name");
        Assertions.assertEquals("测试用户", name);

        // 验证所有字段
        Map<Object, Object> allFields = (Map<Object, Object>)
                dataTypeService.getAllHashFields(key);
        Assertions.assertEquals(2, allFields.size());

        // 清理测试数据
        redisTemplate.delete(key);
    }

    @Test
    @Order(3)
    @DisplayName("测试 ZSet 类型：排行榜")
    void testZSetDataType() {
        // 准备测试数据
        String key = "test:zset:leaderboard";

        // 添加排行榜数据（玩家分数）
        dataTypeService.addToZSet(key, "player1", 1000);
        dataTypeService.addToZSet(key, "player2", 2000);
        dataTypeService.addToZSet(key, "player3", 1500);

        // 获取排行榜前三名
        Set<Object> top3 = dataTypeService.getTopNFromZSet(key, 3);
        Assertions.assertEquals(3, top3.size());

        // 增加玩家1的分数
        Double newScore = dataTypeService.incrementZSetScore(key, "player1", 500);
        Assertions.assertEquals(1500, newScore);

        // 清理测试数据
        redisTemplate.delete(key);
    }

    // ==================== 缓存注解测试 ====================

    @Test
    @Order(4)
    @DisplayName("测试 @Cacheable 注解：缓存查询结果")
    void testCacheable() {
        Long userId = 1001L;

        // 第一次查询：会执行方法，从模拟数据库获取数据
        System.out.println("===== 第一次查询（应该执行方法）=====");
        Map<String, Object> user1 = cacheService.getUserById(userId);
        Assertions.assertNotNull(user1);
        Assertions.assertEquals("zhangsan", user1.get("username"));

        // 第二次查询：不会执行方法，直接从缓存返回
        System.out.println("===== 第二次查询（应该从缓存返回）=====");
        Map<String, Object> user2 = cacheService.getUserById(userId);
        Assertions.assertNotNull(user2);
        Assertions.assertEquals("zhangsan", user2.get("username"));

        // 验证两次返回的是同一个对象（缓存命中）
        // 注意：如果序列化方式不同，可能是不同的对象
        System.out.println("===== 缓存测试通过 =====");
    }

    @Test
    @Order(5)
    @DisplayName("测试 @CacheEvict 注解：清除缓存")
    void testCacheEvict() {
        Long userId = 1001L;

        // 先查询一次，让缓存中有数据
        cacheService.getUserById(userId);

        // 清除缓存
        cacheService.deleteUser(userId);

        // 再次查询，应该重新执行方法
        System.out.println("===== 缓存清除后查询（应该重新执行方法）=====");
        Map<String, Object> user = cacheService.getUserById(userId);
        // 用户已被删除，应该返回 null
        Assertions.assertNull(user);
    }

    // ==================== 分布式锁测试 ====================

    @Test
    @Order(6)
    @DisplayName("测试 Redisson 分布式锁：并发扣减库存")
    void testRedissonLock() throws InterruptedException {
        // 并发线程数：20 个线程同时扣减库存
        int threadCount = 20;
        // 每个线程扣减数量
        int quantity = 1;

        // 计数器：记录成功扣减的次数
        AtomicInteger successCount = new AtomicInteger(0);
        // 计数器：记录失败次数
        AtomicInteger failCount = new AtomicInteger(0);

        // 使用 CountDownLatch 等待所有线程执行完成
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // 启动 20 个线程，模拟并发扣减库存
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 调用带分布式锁的扣减库存方法
                    lockService.deductStockWithRedisson(quantity);
                    // 扣减成功，计数器加 1
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 扣减失败（如库存不足），计数器加 1
                    failCount.incrementAndGet();
                    System.out.println("扣减失败: " + e.getMessage());
                } finally {
                    // 计数器减 1，表示一个线程完成
                    latch.countDown();
                }
            });
        }

        // 等待所有线程执行完成
        latch.await();
        // 关闭线程池
        executor.shutdown();

        // 输出结果
        System.out.println("===== 并发测试结果 =====");
        System.out.println("成功扣减: " + successCount.get() + " 次");
        System.out.println("失败扣减: " + failCount.get() + " 次");
        System.out.println("最终库存: " + lockService.getCurrentStock());

        // 验证：初始库存 100，扣减了 successCount 次，每次 1 件
        // 最终库存 = 100 - successCount
        Assertions.assertEquals(100 - successCount.get(),
                lockService.getCurrentStock());
    }

    @Test
    @Order(7)
    @DisplayName("测试 Lock4j 注解：并发扣减库存")
    void testLock4j() throws InterruptedException {
        // 重置库存
        lockService.resetStock();

        // 并发线程数
        int threadCount = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 使用 Lock4j 注解的扣减库存方法
                    lockService.deductStockWithLock4j(1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("===== Lock4j 并发测试结果 =====");
        System.out.println("成功扣减: " + successCount.get() + " 次");
        System.out.println("失败扣减: " + failCount.get() + " 次");
        System.out.println("最终库存: " + lockService.getCurrentStock());

        Assertions.assertEquals(100 - successCount.get(),
                lockService.getCurrentStock());
    }
}
```

---

## 4. 运行验证

### 4.1 启动 Redis

在运行项目之前，确保本地已经安装了 Redis 并启动。

**Windows 环境**（使用 WSL 或 Memurai）：

```bash
# 方式一：使用 WSL（推荐）
wsl
sudo service redis-server start

# 方式二：使用 Memurai（Windows 原生 Redis 替代品）
# 安装后会自动作为 Windows 服务运行

# 验证 Redis 是否启动
redis-cli ping
# 返回 PONG 表示启动成功
```

**Docker 环境**（推荐，无需安装）：

```bash
# 使用 Docker 启动 Redis 6.2
docker run -d --name redis \
  -p 6379:6379 \
  redis:6.2

# 验证是否启动
docker logs redis
# 或
docker exec -it redis redis-cli ping
```

### 4.2 启动项目

```bash
# 编译并启动
mvn spring-boot:run

# 或者先打包成 jar 再启动
mvn clean package -DskipTests
java -jar target/hello-redis-1.0.0.jar
```

### 4.3 执行测试

**方式一：运行单元测试**

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=HelloRedisTest
```

**方式二：通过 curl 或浏览器测试 REST API**

```bash
# 1. 测试 String 类型
curl http://localhost:8080/api/redis/string
# 预期输出: String 测试结果: 世界, Redis!

# 2. 测试 @Cacheable 注解
# 第一次访问（会执行方法，控制台打印日志）
curl http://localhost:8080/api/redis/cache/user/1001
# 预期输出: {"id":1001,"username":"zhangsan","nickname":"张三",...}

# 第二次访问（直接从缓存返回，控制台不打印日志）
curl http://localhost:8080/api/redis/cache/user/1001

# 3. 测试分布式锁（扣减库存）
curl -X POST "http://localhost:8080/api/redis/lock/deduct?quantity=1"
# 预期输出: 扣减成功，剩余库存: 99

# 4. 查看当前库存
curl http://localhost:8080/api/redis/stock
# 预期输出: 当前库存: 99
```

### 4.4 验证 Redis 中的数据

```bash
# 进入 Redis 命令行
redis-cli

# 查看所有 key（注意：生产环境慎用）
keys *

# 查看 String 类型缓存
get "hello-redis:users::1001"
# 预期输出: JSON 格式的用户数据

# 查看锁的状态
exists "lock:stock"
# 返回 0 表示锁已释放，返回 1 表示锁被持有

# 查看缓存过期时间
ttl "hello-redis:users::1001"
# 返回剩余秒数，-2 表示 key 不存在
```

### 4.5 预期输出

运行测试类 `HelloRedisTest` 后，控制台输出如下：

```
===== 第一次查询（应该执行方法）=====
模拟从数据库查询用户：id=1001
数据库查询成功：id=1001, user={id=1001, username=zhangsan, ...}
===== 第二次查询（应该从缓存返回）=====
===== 缓存测试通过 =====
===== 并发测试结果 =====
成功扣减: 20 次
失败扣减: 0 次
最终库存: 80
```

---

## 5. 项目对照：ruoyi-ai 中的 Redis 使用

### 5.1 ruoyi-ai 的 Redis 配置

在 ruoyi-ai 项目中，Redis 的配置和使用与我们上面搭建的示例基本一致，但更加完善和工程化。

**核心差异对比**：

| 配置项 | 示例项目 | ruoyi-ai 项目 |
|--------|---------|---------------|
| Redis 客户端 | Lettuce | Lettuce |
| 序列化方式 | GenericJackson2JsonRedisSerializer | 自定义 FastJson2JsonRedisSerializer |
| 缓存管理器 | RedisCacheManager | RedisCacheManager |
| 分布式锁 | Redisson + Lock4j | Redisson + Lock4j |
| 缓存注解 | @Cacheable / @CachePut / @CacheEvict | 同样使用，但缓存名称更规范 |

### 5.2 ruoyi-ai 中的 Redis 使用场景

**场景一：系统配置缓存**

ruoyi-ai 的 `SysConfigServiceImpl` 中使用 `@Cacheable` 缓存系统配置，避免每次请求都查询数据库。

```java
// ruoyi-ai 中的实际代码模式
@Service
public class SysConfigServiceImpl implements ISysConfigService {

    @Override
    @Cacheable(cacheNames = "sys:config", key = "#configKey")
    public String selectConfigByKey(String configKey) {
        // 查询数据库
        SysConfig config = this.baseMapper.selectOne(
                Wrappers.lambdaQuery(SysConfig.class)
                        .eq(SysConfig::getConfigKey, configKey));
        return config != null ? config.getConfigValue() : "";
    }
}
```

**场景二：验证码缓存**

ruoyi-ai 使用 Redis 存储验证码，设置短过期时间，实现验证码的时效性。

```java
// ruoyi-ai 中的验证码缓存模式
// 使用 RedisTemplate 手动缓存验证码
redisTemplate.opsForValue().set(
    CacheConstants.CAPTCHA_CODE_KEY + uuid,
    code,
    Constants.CAPTCHA_EXPIRATION,
    TimeUnit.MINUTES
);
```

**场景三：分布式锁使用**

ruoyi-ai 在定时任务、库存扣减等场景使用 Redisson 分布式锁。

```java
// ruoyi-ai 中的分布式锁模式
@Lock4j(name = "lock:task:sync", expire = 60000)
public void syncData() {
    // 同步数据的业务逻辑
    // 多个服务实例中，只有一个能执行
}
```

### 5.3 ruoyi-ai 的缓存命名规范

ruoyi-ai 项目中，缓存 key 的命名遵循以下规范：

```
模块名:业务名:具体标识
例如：
sys:config:sys.account.registerUser  // 系统配置
captcha_codes:uuid                   // 验证码
login_token:userId                   // 登录令牌
```

这种命名方式的好处是：
1. **模块隔离**：不同模块的缓存不会冲突
2. **易于管理**：可以通过前缀批量清除某个模块的缓存
3. **可读性强**：一眼就能看出缓存属于哪个业务

### 5.4 从示例到项目的迁移路径

如果你要在 ruoyi-ai 中应用本文的知识，建议按以下步骤：

1. **理解现有配置**：阅读 `ruoyi-common-redis` 模块中的 RedisConfig 和 RedissonConfig
2. **使用缓存注解**：在查询方法上添加 `@Cacheable`，在更新方法上添加 `@CacheEvict`
3. **使用分布式锁**：在需要互斥的方法上添加 `@Lock4j` 注解
4. **注意缓存一致性**：更新数据时，同步清除相关缓存

---

## 6. 面试题 3 道

### 面试题 1：Redis 的缓存穿透、缓存击穿、缓存雪崩分别是什么？如何解决？

**期望回答**：

**缓存穿透**：查询一个不存在的数据。由于缓存和数据库中都没有该数据，请求每次都穿透到数据库，导致数据库压力过大。

解决方案：
- 缓存空值：即使查询结果为 null，也缓存起来，设置较短的过期时间（如 60 秒）
- 布隆过滤器：在缓存前加一层布隆过滤器，判断数据是否存在

**缓存击穿**：一个热点 key 在缓存过期的瞬间，大量请求同时访问该 key，直接打到数据库。

解决方案：
- 互斥锁：缓存失效时，只让一个线程去查询数据库，其他线程等待（`@Cacheable(sync = true)`）
- 逻辑过期：热点数据不设置物理过期时间，而是存储一个逻辑过期时间，发现过期时异步更新

**缓存雪崩**：大量 key 同时过期，或 Redis 宕机，导致大量请求直接打到数据库。

解决方案：
- 过期时间随机化：在基础过期时间上增加随机值，避免大量 key 同时过期
- 多级缓存：本地缓存（Caffeine）+ 分布式缓存（Redis）
- Redis 集群：主从复制 + 哨兵模式，保证高可用
- 限流降级：数据库层面做好限流保护

### 面试题 2：Redisson 分布式锁的看门狗（Watchdog）机制是如何实现的？

**期望回答**：

看门狗是 Redisson 分布式锁的核心机制，用于解决锁的过期时间问题。

**工作原理**：

1. 当客户端获取锁时，默认锁的过期时间为 30 秒
2. 如果业务执行时间超过 30 秒，锁会自动释放，导致其他线程可以获取锁
3. Redisson 的看门狗机制会启动一个后台定时任务，每 10 秒检查一次
4. 如果锁还被当前线程持有，就将锁的过期时间重置为 30 秒
5. 当业务执行完毕，调用 `unlock()` 释放锁，看门狗任务也会停止

**实现细节**：

- 看门狗使用 `Timeout` 定时任务，通过 `Netty` 的 `EventLoop` 调度
- 续期操作使用 Lua 脚本，保证原子性
- 如果客户端宕机，看门狗任务也会停止，锁会在 30 秒后自动释放

**注意事项**：

- 看门狗只在 `lock()` 和 `tryLock()` 无参方法中生效
- 如果调用 `tryLock(10, 30, TimeUnit.SECONDS)` 手动指定了过期时间，看门狗不会启动
- 看门狗会带来额外的网络开销，但对于长时间业务非常必要

### 面试题 3：Spring @Cacheable 注解的 sync 属性和 Redisson 分布式锁有什么区别？分别在什么场景下使用？

**期望回答**：

**@Cacheable(sync = true)**：

- 作用：防止缓存击穿，保证同一个 key 的缓存失效时，只有一个线程去加载数据
- 实现原理：Spring 使用 `synchronized` 或 `ConcurrentHashMap` 的 `computeIfAbsent` 进行本地锁
- 适用范围：**单机应用**，或缓存加载的场景
- 粒度：针对单个缓存 key
- 过期：不涉及锁的过期时间

**Redisson 分布式锁**：

- 作用：保证跨 JVM 的互斥访问，适用于分布式环境下的临界区保护
- 实现原理：基于 Redis 的 SETNX 命令 + Lua 脚本 + 看门狗机制
- 适用范围：**分布式应用**，需要跨进程互斥的场景
- 粒度：可以是全局锁，也可以是业务级别的锁
- 过期：支持自动续期，防止死锁

**选择建议**：

| 场景 | 推荐方案 |
|------|---------|
| 缓存失效时防止多个线程同时查数据库 | @Cacheable(sync = true) |
| 跨 JVM 的库存扣减 | Redisson 分布式锁 / @Lock4j |
| 定时任务多实例互斥 | Redisson 分布式锁 / @Lock4j |
| 同一实例内防止重复请求 | @Cacheable(sync = true) |
| 分布式事务中的资源锁定 | Redisson 分布式锁 |

**总结**：两者不是替代关系，而是互补关系。`@Cacheable(sync = true)` 解决缓存层面的问题，Redisson 分布式锁解决跨进程互斥的问题。在 ruoyi-ai 这样的微服务项目中，两者都会用到。

---

## 总结

本文从零开始，完整演示了在 Spring Boot 3.x 项目中集成 Redis 和 Redisson 的全部流程。我们学习了：

1. **Redis 五种数据类型**：String、Hash、List、Set、ZSet 的核心概念和操作
2. **Spring Data Redis 配置**：Lettuce 客户端、连接池、序列化配置
3. **缓存注解**：@Cacheable、@CacheEvict、@CachePut 的声明式使用
4. **Redisson 分布式锁**：手动 API 和 Lock4j 注解两种方式
5. **并发测试**：验证分布式锁在高并发下的正确性

这些知识是 ruoyi-ai 项目中使用 Redis 的基础。在后续的文章中，我们将深入探讨 Redisson 的高级特性、Redis 集群部署、缓存一致性等进阶话题。