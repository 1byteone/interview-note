# 08 Redis + Redisson 缓存与分布式锁入门：缓存策略、分布式锁与看门狗

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 8 篇（入门篇）。面向 Java 初学者，手把手带你从零搭建基于 Redis + Redisson 的缓存与分布式锁系统，理解 Cache-Aside 旁路缓存、缓存三大难题（穿透/击穿/雪崩）和 Redisson 看门狗自动续期。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-server` 缓存与分布式锁模块
> **难度等级：** Level 1 入门
> **预计阅读时间：** 30 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是 Redis 和 Redisson

**Redis**（Remote Dictionary Server）是一个开源的内存数据存储系统，常用作缓存、会话存储和消息中间件。它支持 String、Hash、List、Set、Sorted Set 等丰富的数据类型，所有数据都在内存中读写，延迟通常在毫秒级别。

**Redisson** 是 Redis 官方推荐的 Java 客户端，它在 Redis 的基础上封装了丰富的分布式数据结构和分布式服务，其中最核心的是**分布式锁（RLock）和看门狗（Watchdog）自动续期机制**。

| 核心概念 | 说明 | 项目中的用途 |
|---------|------|-------------|
| **Redis** | 内存数据存储，支持多种数据类型 | 热点数据缓存、会话存储、配额计数 |
| **Redisson** | Redis 的 Java 客户端，封装分布式锁等功能 | 分布式锁、看门狗自动续期 |
| **Spring Cache** | Spring 的缓存注解框架 | 注解化缓存操作（@Cacheable、@CacheEvict） |
| **RedisTemplate** | Spring Data Redis 提供的操作模板 | 手动读写缓存（复杂场景） |

**Redis 在项目中的整体定位：**

```
┌─────────────────────────────────────────────────────┐
│                  业务层（Service）                      │
│  UserService  ArticleService  AgentService  QuotaService │
└──────────────────────┬──────────────────────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
┌──────────────┐ ┌──────────┐ ┌──────────────┐
│ Spring Cache │ │ RedisTemplate │ │  Redisson    │
│ @Cacheable   │ │ 手动读写缓存  │ │  RLock 分布式锁│
│ @CacheEvict  │ │             │ │  Watchdog    │
└──────┬───────┘ └─────┬─────┘ └──────┬───────┘
       │               │              │
       └───────────────┼──────────────┘
                       ▼
               ┌──────────────┐
               │    Redis     │
               │  (内存存储)   │
               └──────────────┘
```

### 1.2 为什么需要缓存和分布式锁

在微服务架构中，多个服务实例同时运行，单体应用中的 `synchronized` 和 `ReentrantLock` 无法跨实例工作。以下是项目中必须解决的几个核心问题：

| 问题 | 场景 | 后果 | 解决方案 |
|------|------|------|----------|
| **热点数据反复查库** | 用户信息、文章列表每次请求都查数据库 | 数据库压力大，响应速度慢（10-50ms） | Redis 缓存热点数据（1-5ms） |
| **会话状态跨实例共享** | AI Agent 生成文章时，请求可能落在不同实例上 | Agent 状态丢失，任务中断 | Redis 存储会话状态 |
| **并发扣减配额** | 同一用户同时发起多个生成请求 | 配额超发（如配额 50，实际用了 55） | Redisson 分布式锁保证原子性 |
| **缓存失效瞬间压力爆增** | 热点 key 过期，大量请求同时打到数据库 | 数据库连接被打满，服务不可用 | 互斥锁 + 随机 TTL |

**缓存三大难题：**

| 难题 | 现象 | 类比 | 解决方案 |
|------|------|------|----------|
| **缓存穿透** | 查询一个不存在的数据，每次请求都穿透到数据库 | 有人用假钥匙试你的门，每次都试，每次都报警 | 缓存空值（短 TTL） |
| **缓存击穿** | 热点 key 过期瞬间，大量并发请求同时打到数据库 | 一个热门景点突然关门，所有人都冲进办公室问 | 互斥锁（只允许一个请求重建缓存） |
| **缓存雪崩** | 大量 key 在同一时间过期，数据库压力暴增 | 一个城市所有红绿灯同时坏掉，交通瘫痪 | 过期时间加随机因子 |

### 1.3 本文的目标

读完本文，你将能够：
- 理解 Redis 缓存的核心概念：Cache-Aside 模式、缓存穿透/击穿/雪崩
- 理解 Redisson 分布式锁的核心概念：RLock、可重入锁、看门狗
- 搭建一个完整的 Redis + Redisson Demo
- 使用 Spring Cache 注解实现 Cache-Aside 缓存
- 使用 Redisson 分布式锁实现配额扣减
- 实现缓存穿透防护（空值缓存）
- 编写单元测试验证缓存和锁的功能
- 编写 3 道面试题的标准答案

---

## 二、核心概念

### 2.1 缓存策略：Cache-Aside（旁路缓存）

Cache-Aside 是最经典的缓存策略，也是项目中使用的核心模式。它的核心思想是：**缓存层在数据库前面，业务代码直接操作缓存和数据库，两者之间通过"删除缓存"来保持最终一致**。

**读流程：**

```
① 查缓存
    ↓
    命中 → 直接返回（1-5ms）
    未命中 → 继续
    ↓
② 查数据库（10-50ms）
    ↓
③ 将结果回填到缓存
    ↓
④ 返回结果
```

**写流程：**

```
① 更新数据库
    ↓
② 删除缓存（不是更新缓存！）
    ↓
③ 下次查询时，缓存未命中 → 重新查库并回填
```

**为什么写操作是删除缓存而不是更新缓存？**

| 操作 | 问题 | 结论 |
|------|------|------|
| **更新缓存** | 并发写场景下，两个线程同时更新数据库和缓存，可能导致缓存中是旧值 | 不推荐 |
| **删除缓存** | 删除操作天然幂等，下次读时重新回填，永远是最新值 | 推荐 |

**Cache-Aside 的优点是"简单可靠"**——写操作时只删除缓存不更新缓存，避免了复杂的并发一致性问题。虽然读操作多了一次缓存回填（代价很小），但保障了数据的最终一致性。

### 2.2 分布式锁：RLock 可重入锁

分布式锁用于解决**多实例（多进程）之间的互斥问题**。单体应用中，JVM 自带的 `synchronized` 和 `ReentrantLock` 只能保证同一 JVM 内的线程互斥，但微服务是多个节点部署的，锁必须落在所有实例都能访问的第三方组件上。

**分布式锁的三个核心诉求：**

| 诉求 | 说明 | 类比 |
|------|------|------|
| **互斥性** | 同一时刻只有一个节点能拿到锁 | 会议室只有一个钥匙，谁拿到谁进去 |
| **安全性** | 锁会自动释放（防死锁），只能释放自己持有的锁（防误删） | 会议结束自动归还钥匙，不能把别人的钥匙还了 |
| **高可用** | 锁服务本身是高可用的，Redis 宕机时锁不丢失 | 有备用钥匙保管员 |

**分布式锁的实现载体对比：**

| 载体 | 优点 | 缺点 | 生产推荐 |
|------|------|------|----------|
| **数据库唯一索引** | 简单，不依赖额外组件 | 性能差（磁盘 IO），有锁间隙 | 不推荐 |
| **Redis SETNX / Redisson** | 性能好（内存操作），功能全，支持自动续期 | 依赖 Redis 高可用 | **推荐** |
| **ZooKeeper / etcd 临时节点** | 强一致，无过期时间问题 | 运维重，性能不如 Redis | 对一致性要求极高时 |

**Redisson 可重入锁的核心特性：**

| 特性 | 说明 |
|------|------|
| **可重入** | 同一线程可重复获取同一把锁，内部计数器 +1；释放时 -1，减到 0 才真正释放 |
| **原子性** | 加锁/解锁用 Lua 脚本保证，Redis 单线程执行脚本天然原子 |
| **自动续期（看门狗）** | 默认锁过期时间 30s，每 10s 检查一次，业务未结束自动续期 30s |
| **防误删** | Hash 结构存线程标识（field=线程ID，value=重入次数），只能释放自己持有的锁 |

### 2.3 看门狗（Watchdog）自动续期

看门狗是 Redisson 最核心的机制之一，解决了分布式锁的"过期时间设置难题"。

**问题场景：**

假设业务执行需要 45 秒，锁的过期时间设置为 30 秒：
- 设 30 秒：业务还没执行完，锁就自动释放了，其他线程可能进来
- 设 60 秒：业务执行 5 秒就结束了，但锁要等 60 秒才释放，如果此时客户端宕机，锁要等 60 秒才自动释放

**看门狗的解决方案：**

```
lock.lock()  // 默认锁过期时间 30 秒
    ↓
Redisson 启动一个后台定时任务（看门狗线程）
    ↓
每 10 秒检查一次：锁是否还被当前线程持有？
    ↓
    是 → 续期锁的过期时间到 30 秒
    否 → 停止续期，锁到期自动释放
    ↓
客户端宕机 → 看门狗线程停止 → 锁到期自动释放（防死锁）
```

**时间线示例：**

```
t=0s:    lock.lock() 获取锁，锁过期时间 = 30s
t=10s:   看门狗检查 → 锁还在持有 → 续期到 30s（从 t=10s 算起，过期时间 = t=40s）
t=20s:   看门狗检查 → 锁还在持有 → 续期到 30s（从 t=20s 算起，过期时间 = t=50s）
t=35s:   业务执行完毕 → unlock() → 看门狗停止
```

**一句话总结：** 看门狗让分布式锁的过期时间 = "业务实际执行时间 + 30 秒"——业务执行多久，锁就活多久。

---

## 三、从零搭建代码

### 3.1 创建项目结构

```
redis-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── passage/
│   │   │           └── redis/
│   │   │               ├── RedisDemoApplication.java                  # 启动类
│   │   │               ├── config/
│   │   │               │   ├── RedissonConfig.java                    # Redisson 配置
│   │   │               │   └── CacheConfig.java                       # Spring Cache 配置
│   │   │               ├── entity/
│   │   │               │   └── User.java                             # 用户实体
│   │   │               ├── mapper/
│   │   │               │   └── UserMapper.java                       # 用户 Mapper
│   │   │               ├── service/
│   │   │               │   ├── UserService.java                      # 用户服务（Spring Cache）
│   │   │               │   ├── QuotaService.java                     # 配额服务（分布式锁）
│   │   │               │   ├── AgentSessionService.java              # Agent 会话服务（看门狗）
│   │   │               │   └── ArticleCacheService.java              # 文章缓存服务（穿透防护）
│   │   │               └── controller/
│   │   │                   └── CacheController.java                  # 缓存 API
│   │   └── resources/
│   │       └── application.yml                                       # 配置文件
│   └── test/
│       └── java/
│           └── com/
│               └── passage/
│                   └── redis/
│                       └── RedisDemoApplicationTests.java             # 测试类
```

### 3.2 配置 Maven 依赖（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- pom.xml —— Maven 项目配置文件 -->
<!-- Redis + Redisson 缓存与分布式锁示例的 Maven 构建配置 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父项目：Spring Boot 3.2.x -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标信息 -->
    <groupId>com.passage</groupId>
    <artifactId>redis-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Redis Redisson Demo</name>
    <description>Redis + Redisson 缓存与分布式锁入门：缓存策略、分布式锁与看门狗</description>

    <properties>
        <java.version>17</java.version>                      <!-- 使用 Java 17 -->
        <mybatis-flex.version>1.11.1</mybatis-flex.version>  <!-- MyBatis-Flex 版本 -->
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data Redis（RedisTemplate + Spring Cache） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Redisson 分布式锁 + 看门狗 -->
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
            <version>3.35.0</version>
        </dependency>

        <!-- MyBatis-Flex 核心依赖 -->
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-spring-boot-starter</artifactId>
            <version>${mybatis-flex.version}</version>
        </dependency>

        <!-- H2 数据库（测试用，无需安装真实数据库） -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Lombok - 简化 POJO 代码 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Test 测试框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <!-- APT 处理器配置：MyBatis-Flex 编译期代码生成 -->
                    <annotationProcessorPaths>
                        <path>
                            <groupId>com.mybatis-flex</groupId>
                            <artifactId>mybatis-flex-annotation-processor</artifactId>
                            <version>${mybatis-flex.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 配置文件（application.yml）

```yaml
# application.yml —— 应用配置文件
# Redis + Redisson 缓存与分布式锁配置

server:
  port: 8080                             # 服务端口号

spring:
  application:
    name: redis-demo                     # 应用名称

  # Spring Data Redis 配置
  data:
    redis:
      # Redis 连接地址（单机模式）
      host: localhost
      # Redis 端口（默认 6379）
      port: 6379
      # Redis 密码（没有密码则留空）
      password:
      # Redis 数据库索引（0-15，默认 0）
      database: 0
      # 连接超时时间（毫秒）
      connect-timeout: 5000
      # 读取超时时间（毫秒）
      timeout: 5000
      # Lettuce 连接池配置
      lettuce:
        pool:
          # 最大连接数
          max-active: 16
          # 最大空闲连接数
          max-idle: 8
          # 最小空闲连接数
          min-idle: 4
          # 获取连接的最大等待时间（毫秒）
          max-wait: 3000

  # 数据源配置（测试用，使用 H2 内存数据库）
  datasource:
    url: jdbc:h2:mem:redis_demo
    driver-class-name: org.h2.Driver
    username: sa
    password:

# MyBatis-Flex 配置
mybatis-flex:
  type-aliases-package: com.passage.redis.entity

# Redisson 独立配置（与 Spring Data Redis 共用同一个 Redis 实例）
redisson:
  # Redisson 连接地址（redis:// 前缀表示非加密连接）
  address: redis://localhost:6379
  # 密码（与 Spring Data Redis 一致）
  password:
  # 连接池配置
  pool:
    min-idle: 4
    max-idle: 8
    max-active: 16
```

### 3.4 配置类

#### Redisson 配置（RedissonConfig.java）

```java
package com.passage.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RedissonConfig - Redisson 配置类
 * <p>
 * 构建 RedissonClient 实例，提供分布式锁、看门狗等功能。
 * RedissonClient 是 Redisson 的入口，线程安全，全局单例。
 * <p>
 * Redisson 负责分布式锁，RedisTemplate 负责缓存读写，
 * 两者共用同一个 Redis 实例，分工明确。
 *
 * @author AI-Passage-Creator
 */
@Configuration
public class RedissonConfig {

    /**
     * 创建 RedissonClient Bean
     * <p>
     * 配置项说明：
     * - singleServerConfig：单机模式配置
     * - codec：编解码器，使用 JSON 序列化，方便跨语言调试
     * - lockWatchdogTimeout：看门狗超时时间，默认 30 秒
     *
     * @return RedissonClient 实例
     */
    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        // 构建 Redisson 配置
        Config config = new Config();

        // 单机模式配置
        // Redisson 还支持主从模式、哨兵模式、集群模式
        config.useSingleServer()
                .setAddress("redis://localhost:6379")  // Redis 连接地址
                .setPassword(null)                      // Redis 密码
                .setConnectionPoolSize(16)              // 连接池大小
                .setConnectionMinimumIdleSize(4)        // 最小空闲连接数
                .setIdleConnectionTimeout(10000)        // 空闲连接超时（毫秒）
                .setConnectTimeout(5000)                // 连接超时（毫秒）
                .setTimeout(5000);                      // 响应超时（毫秒）

        // 设置编解码器：使用 JSON 序列化
        // 方便在 Redis 中直接查看数据（String 格式，可读性强）
        config.setCodec(new JsonJacksonCodec());

        // 设置看门狗超时时间：默认 30 秒
        // 每 10 秒（lockWatchdogTimeout / 3）检查一次
        // 业务未完成自动续期 30 秒
        config.setLockWatchdogTimeout(30_000); // 30 秒

        // 创建 RedissonClient 实例
        return Redisson.create(config);
    }
}
```

#### Spring Cache 配置（CacheConfig.java）

```java
package com.passage.redis.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * CacheConfig - 缓存配置类
 * <p>
 * 配置 Spring Cache 的 Redis 实现。
 * 使用 Spring Cache 注解（@Cacheable、@CacheEvict、@CachePut）
 * 实现 Cache-Aside 旁路缓存模式。
 * <p>
 * 缓存策略：
 * - 读：先查缓存，命中直接返回，未命中查 DB 并回填缓存
 * - 写：更新 DB 后删除缓存（下次读时再回填）
 * - 过期时间：基础值 + 随机因子，防止缓存雪崩
 *
 * @author AI-Passage-Creator
 */
@Configuration
@EnableCaching  // 启用 Spring Cache 注解功能
public class CacheConfig {

    /**
     * 配置 RedisCacheManager
     * <p>
     * 为不同的缓存区域（cacheNames）设置不同的 TTL（过期时间）。
     * 避免所有缓存使用相同的过期时间，降低缓存雪崩风险。
     * <p>
     * 缓存区域命名规则：
     * - "users"：用户信息缓存，TTL 30 分钟
     * - "articles"：文章列表缓存，TTL 10 分钟
     * - "configs"：系统配置缓存，TTL 2 小时
     *
     * @param factory Redis 连接工厂（由 Spring Boot 自动配置）
     * @return RedisCacheManager 实例
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // 构建默认的 RedisCacheConfiguration
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 序列化 key：使用 String 序列化（Redis key 通常是字符串）
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                // 序列化 value：使用 JSON 序列化（可读性强，跨语言兼容）
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                // 缓存 null 值：防止缓存穿透
                // 当数据库查不到数据时，将 null 也缓存起来
                .allowCachingNullValues(true)
                // 默认过期时间：1 小时
                .entryTtl(Duration.ofHours(1));

        // 为不同缓存区域设置不同的 TTL
        Map<String, RedisCacheConfiguration> configMap = new HashMap<>();

        // 用户信息缓存：30 分钟
        // 用户信息变化频率中等，30 分钟过期比较合理
        configMap.put("users", config.entryTtl(Duration.ofMinutes(30)));

        // 文章列表缓存：10 分钟
        // 文章列表变化频率高，10 分钟过期可以快速更新
        configMap.put("articles", config.entryTtl(Duration.ofMinutes(10)));

        // 系统配置缓存：2 小时
        // 系统配置变化频率极低，2 小时过期减少数据库压力
        configMap.put("configs", config.entryTtl(Duration.ofHours(2)));

        // 构建并返回 RedisCacheManager
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)                    // 默认配置
                .withInitialCacheConfigurations(configMap) // 各缓存区域自定义配置
                .build();
    }
}
```

### 3.5 实体类（User.java）

```java
package com.passage.redis.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * User - 用户实体类
 * <p>
 * 实现 Serializable 接口，因为 Redis 中的对象需要序列化。
 * 使用 MyBatis-Flex 的 @Table 和 @Column 注解映射数据库表。
 * 包含每日配额相关信息，用于演示分布式锁的配额扣减场景。
 *
 * @author AI-Passage-Creator
 */
@Table(value = "user")                          // 指定数据库表名
public class User implements Serializable {     // 实现 Serializable 以便 Redis 序列化

    @Column(value = "id", isPrimaryKey = true)  // 主键 ID
    private Long id;

    @Column(value = "user_name")                 // 用户名
    private String userName;

    @Column(value = "daily_quota")               // 每日配额（生成文章次数上限）
    private Integer dailyQuota;

    @Column(value = "used_quota")                // 已用配额
    private Integer usedQuota;

    @Column(value = "create_time")               // 创建时间
    private LocalDateTime createTime;

    @Column(value = "update_time")               // 更新时间
    private LocalDateTime updateTime;

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(Integer dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public Integer getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(Integer usedQuota) {
        this.usedQuota = usedQuota;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", userName='" + userName + "'}";
    }
}
```

### 3.6 Mapper 接口（UserMapper.java）

```java
package com.passage.redis.mapper;

import com.mybatisflex.core.BaseMapper;
import com.passage.redis.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserMapper - 用户 Mapper 接口
 * <p>
 * 继承 BaseMapper<User> 后自动获得通用 CRUD 方法。
 * Spring Cache 注解配合此 Mapper 实现 Cache-Aside 缓存模式。
 *
 * @author AI-Passage-Creator
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 通用 CRUD 由 BaseMapper 提供，无需额外定义
}
```

### 3.7 服务层

#### 用户服务（UserService.java）—— Spring Cache 注解

```java
package com.passage.redis.service;

import com.passage.redis.entity.User;
import com.passage.redis.mapper.UserMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * UserService - 用户服务
 * <p>
 * 演示 Spring Cache 注解的使用，实现 Cache-Aside 旁路缓存模式。
 * <p>
 * Cache-Aside 模式：
 * - @Cacheable：先查缓存，未命中再查 DB，结果自动回填缓存
 * - @CacheEvict：更新 DB 后删除缓存，下次查询时重新回填
 * - @CachePut：更新 DB 后同时更新缓存（不常用，因为写操作频繁时反而浪费）
 *
 * @author AI-Passage-Creator
 */
@Service
public class UserService {

    /** 用户 Mapper（MyBatis-Flex BaseMapper） */
    private final UserMapper userMapper;

    /**
     * 构造方法注入
     */
    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 根据 ID 查询用户信息 —— @Cacheable 注解
     * <p>
     * 执行流程：
     * 1. 检查缓存（key = "users::" + userId）
     * 2. 命中 → 直接返回缓存数据（1-5ms，无需查数据库）
     * 3. 未命中 → 执行方法体（查数据库，10-50ms）
     * 4. 将方法返回值自动存入缓存（key = "users::" + userId）
     * 5. 返回结果
     * <p>
     * 缓存 key 生成规则：value + "::" + key
     * 例如：@Cacheable(value = "users", key = "#userId")
     * 生成的 Redis key = "users::123"
     *
     * @param userId 用户 ID
     * @return 用户信息（或 null）
     */
    @Cacheable(value = "users", key = "#userId")  // 缓存区域 users，key 为用户 ID
    public User getUserById(Long userId) {
        // 缓存未命中时执行：查询数据库
        // 结果会自动回填到 Redis 缓存中
        // 下次相同 userId 的查询直接从缓存返回
        return userMapper.selectById(userId);
    }

    /**
     * 更新用户信息 —— @CacheEvict 注解
     * <p>
     * 执行流程：
     * 1. 执行方法体（更新数据库）
     * 2. 删除缓存（key = "users::" + user.id）
     * 3. 下次查询时，@Cacheable 发现缓存未命中，重新查库并回填
     * <p>
     * 为什么是删除缓存而不是更新缓存？
     * - 删除操作天然幂等，删除多次和删除一次效果一样
     * - 更新缓存在并发写场景下容易出现脏数据（两个线程同时写，后写的把先写的覆盖了）
     * - 延迟删除：下次读时再回填，保证缓存数据与数据库一致
     *
     * @param user 更新后的用户信息
     */
    @CacheEvict(value = "users", key = "#user.id")  // 更新数据库后删除缓存
    public void updateUser(User user) {
        // 更新数据库
        userMapper.updateById(user);
        // 缓存由 @CacheEvict 自动删除
        // 不需要手动操作 Redis
    }

    /**
     * 删除用户 —— 同时删除多个缓存
     * <p>
     * allEntries = true 表示删除该缓存区域下的所有缓存。
     * 适用于"批量操作"场景，但粒度较粗。
     * 例如：用户被删除后，该用户的所有缓存都应该失效。
     *
     * @param userId 用户 ID
     */
    @CacheEvict(value = "users", allEntries = true)  // 删除 users 区域所有缓存
    public void deleteUser(Long userId) {
        // 删除数据库中的用户记录
        userMapper.deleteById(userId);
        // 缓存由 @CacheEvict 自动清空
    }
}
```

#### 配额服务（QuotaService.java）—— Redisson 分布式锁

```java
package com.passage.redis.service;

import com.passage.redis.entity.User;
import com.passage.redis.mapper.UserMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * QuotaService - 配额服务
 * <p>
 * 演示 Redisson 分布式锁的使用。
 * <p>
 * 业务场景：每天用户生成文章的配额是有限的。
 * 多个服务实例同时扣减配额时，需要保证原子性，防止超发。
 * 例如：用户配额 50，同时发来 3 个请求，没有锁的话可能 3 个都扣减成功，变成 53。
 * 使用 Redisson 分布式锁保证同一时刻只有一个请求能扣减配额。
 *
 * @author AI-Passage-Creator
 */
@Service
public class QuotaService {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    /** Redisson 客户端（提供分布式锁） */
    private final RedissonClient redissonClient;

    /** 用户 Mapper */
    private final UserMapper userMapper;

    /**
     * 构造方法注入
     */
    public QuotaService(RedissonClient redissonClient, UserMapper userMapper) {
        this.redissonClient = redissonClient;
        this.userMapper = userMapper;
    }

    /**
     * 扣减用户每日配额 —— 使用 tryLock 非阻塞获取锁
     * <p>
     * tryLock 与 lock 的区别：
     * - tryLock：尝试获取锁，等待指定时间后返回 boolean（非阻塞）
     * - lock：阻塞等待，直到获取锁（阻塞）
     * <p>
     * 锁 key 设计：lock:quota:{userId}
     * - 粒度：每个用户一把锁，不同用户互不影响
     * - 业务标识：quota 表示配额扣减业务
     * - 资源 ID：userId 标识具体用户
     *
     * @param userId 用户 ID
     * @return true 扣减成功，false 扣减失败（配额不足或获取锁超时）
     */
    public boolean deductQuota(Long userId) {
        // 构建锁 key：粒度精确到每个用户
        // 格式：lock:业务名:资源ID
        // 不同用户的锁不同，互不影响
        String lockKey = "lock:quota:" + userId;

        // 获取分布式锁（RLock = Redis Lock）
        // RLock 是 Redisson 提供的分布式锁接口
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // ========== 尝试获取锁 ==========
            // tryLock(等待时间, 租约时间, 时间单位)
            // 等待时间 waitTime：最多等待 3 秒获取锁，超时返回 false
            // 租约时间 leaseTime：获取锁后自动释放时间（30 秒）
            // 如果 waitTime = 0，则立即返回，不等待
            boolean isLocked = lock.tryLock(3, 30, TimeUnit.SECONDS);

            if (!isLocked) {
                // 获取锁超时：其他线程正在处理该用户的配额
                // 可能是另一个请求正在扣减该用户的配额
                log.warn("获取配额锁超时: userId={}", userId);
                return false;
            }

            // ========== 获取锁成功，执行配额扣减逻辑 ==========
            // 在锁的保护下，不会出现并发覆盖问题
            // 同一时刻只有一个请求能执行到这里

            // 第一步：查询用户当前配额
            User user = userMapper.selectById(userId);
            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return false;
            }

            // 第二步：检查配额是否充足
            if (user.getUsedQuota() >= user.getDailyQuota()) {
                log.warn("用户配额已用完: userId={}, used={}, daily={}",
                        userId, user.getUsedQuota(), user.getDailyQuota());
                return false;
            }

            // 第三步：原子扣减配额：usedQuota + 1
            // 在锁的保护下，不存在并发覆盖问题
            // 即使有多个请求同时到达，也只有一个能进入此代码块
            user.setUsedQuota(user.getUsedQuota() + 1);
            userMapper.updateById(user);

            log.info("配额扣减成功: userId={}, 当前已用={}, 每日上限={}",
                    userId, user.getUsedQuota(), user.getDailyQuota());

            return true;

        } catch (InterruptedException e) {
            // 线程被中断：恢复中断状态
            // 中断异常通常来自 tryLock 等待过程中线程被中断
            Thread.currentThread().interrupt();
            log.error("配额扣减被中断: userId={}", userId, e);
            return false;
        } finally {
            // ========== 释放锁 ==========
            // 重要：确保在 finally 中释放锁
            // 使用 isHeldByCurrentThread 判断，防止释放他人的锁
            // 如果不加判断，当前线程可能释放了另一个线程持有的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取用户当前配额信息
     *
     * @param userId 用户 ID
     * @return 配额信息字符串
     */
    public String getQuotaInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return "用户不存在";
        }
        return String.format("已用: %d / 每日上限: %d",
                user.getUsedQuota(), user.getDailyQuota());
    }
}
```

#### Agent 会话服务（AgentSessionService.java）—— 看门狗

```java
package com.passage.redis.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * AgentSessionService - AI Agent 会话服务
 * <p>
 * 演示 Redisson 看门狗（Watchdog）自动续期机制。
 * <p>
 * 业务场景：AI Agent 生成文章是一个耗时操作（可能 30 秒到几分钟）。
 * 在生成过程中，需要保证同一用户的 Agent 任务不被重复创建。
 * 使用分布式锁控制并发，看门狗保证锁不会在任务执行期间过期。
 * <p>
 * 如果不使用看门狗，锁过期时间很难设置：
 * - 设 30 秒：业务执行 45 秒，锁在 30 秒时释放 → 其他线程进来了
 * - 设 60 秒：业务执行 5 秒，锁要等 60 秒才释放 → 客户端宕机则锁持有 60 秒
 * 看门狗自动续期，完美解决这个问题。
 *
 * @author AI-Passage-Creator
 */
@Service
public class AgentSessionService {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);

    /** Redisson 客户端 */
    private final RedissonClient redissonClient;

    /**
     * 构造方法注入
     */
    public AgentSessionService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 执行 AI Agent 任务 —— 使用 lock() 阻塞等待锁，看门狗自动续期
     * <p>
     * lock() 与 tryLock() 的区别：
     * - lock()：阻塞等待，直到获取锁；看门狗自动续期
     * - tryLock()：尝试获取锁，等待指定时间后返回 boolean
     * <p>
     * 看门狗机制：
     * - 调用 lock() 时，默认锁过期时间 30 秒
     * - 看门狗线程每 10 秒检查一次锁是否还在持有
     * - 如果还在持有，将锁的过期时间续期到 30 秒
     * - 业务执行完毕，显式调用 unlock() 释放锁
     * - 客户端宕机，看门狗线程停止，锁到期自动释放（防死锁）
     *
     * @param userId 用户 ID
     * @param taskId 任务 ID
     */
    public void executeAgentTask(Long userId, String taskId) {
        // 锁 key：lock:agent:{userId}
        // 一个用户同时只能有一个 Agent 任务在执行
        // 粒度精确到用户，不同用户互不影响
        String lockKey = "lock:agent:" + userId;

        // 获取分布式锁
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // ========== 获取锁（阻塞等待，看门狗自动续期） ==========
            // lock() 会阻塞直到获取锁
            // 默认锁过期时间 30 秒，看门狗每 10 秒续期一次
            // 如果业务执行超过 30 秒，看门狗会自动续期，不会自动释放
            // 如果其他线程已持有锁，当前线程会阻塞等待
            log.info("正在获取 Agent 锁: userId={}, taskId={}", userId, taskId);
            lock.lock();
            log.info("获取 Agent 锁成功: userId={}, taskId={}", userId, taskId);

            // ========== 执行业务逻辑（可能耗时较长） ==========
            // 模拟 AI Agent 执行的三个阶段
            // 看门狗会在整个执行过程中自动续期

            // 阶段 1：选题生成（模拟耗时 5 秒）
            log.info("阶段1: 选题生成中... userId={}", userId);
            Thread.sleep(5000); // 模拟耗时操作

            // 阶段 2：大纲生成（模拟耗时 10 秒）
            log.info("阶段2: 大纲生成中... userId={}", userId);
            Thread.sleep(10000); // 模拟耗时操作

            // 阶段 3：正文+配图生成（模拟耗时 15 秒）
            log.info("阶段3: 正文+配图生成中... userId={}", userId);
            Thread.sleep(15000); // 模拟耗时操作

            // 在整个执行过程中，看门狗每 10 秒续期一次
            // 锁不会过期，即使业务执行超过 30 秒
            // 总耗时约 30 秒，看门狗会在 t=10s 和 t=20s 时续期
            log.info("Agent 任务执行完成: userId={}, taskId={}", userId, taskId);

        } catch (InterruptedException e) {
            // 线程被中断：恢复中断状态
            Thread.currentThread().interrupt();
            log.error("Agent 任务被中断: userId={}", userId, e);
        } finally {
            // ========== 释放锁 ==========
            // 业务执行完毕，手动释放锁
            // 看门狗收到锁释放后，停止续期
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Agent 锁已释放: userId={}", userId);
            }
        }
    }

    /**
     * 使用 lock(leaseTime) 指定租约时间（不使用看门狗）
     * <p>
     * 当明确知道业务执行时间时，可以指定租约时间。
     * 此时看门狗不会启动，锁到期自动释放。
     * <p>
     * 适用场景：业务执行时间稳定且可预测。
     * <p>
     * 注意：如果业务执行时间超过了租约时间，锁会自动释放，
     * 但业务代码会继续执行（不会抛异常），
     * 所以租约时间一定要设置得比业务最大执行时间长。
     *
     * @param userId 用户 ID
     */
    public void executeWithLeaseTime(Long userId) {
        // 锁 key
        RLock lock = redissonClient.getLock("lock:fixed:" + userId);

        try {
            // 获取锁，指定租约时间 10 秒
            // 10 秒后锁自动释放，看门狗不启动
            // 适合"明确知道 10 秒内一定能完成"的业务
            // 例如：批量处理 100 条数据，每条处理 50ms，总时间可预测
            lock.lock(10, TimeUnit.SECONDS);

            // 执行业务逻辑（必须在 10 秒内完成）
            log.info("执行短时业务: userId={}", userId);

        } finally {
            // 即使锁已自动释放，调用 unlock() 也是安全的
            // Redisson 会检查锁是否还被当前线程持有
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

#### 文章缓存服务（ArticleCacheService.java）—— 缓存穿透防护

```java
package com.passage.redis.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * ArticleCacheService - 文章缓存服务
 * <p>
 * 演示缓存穿透防护：缓存空值。
 * <p>
 * 缓存穿透：查询一个不存在的数据（如不存在的文章 ID），
 * 导致每次请求都穿透到数据库。如果攻击者批量请求不存在的 ID，
 * 大量请求可能打垮数据库。
 * <p>
 * 解决方案：缓存空值（null 也缓存，TTL 较短）。
 * 第一次查询不存在的数据时，将 null 写入缓存。
 * 后续请求直接返回 null，不再穿透到数据库。
 * <p>
 * 注意：这里使用 RedisTemplate 手动操作缓存，
 * 因为没有使用 Spring Cache 注解（@Cacheable 不支持空值缓存 + 短 TTL 组合）。
 *
 * @author AI-Passage-Creator
 */
@Service
public class ArticleCacheService {

    /** Redis 操作模板（手动读写缓存） */
    private final RedisTemplate<String, Object> redisTemplate;

    // 文章缓存前缀，用于区分不同业务类型的缓存
    private static final String CACHE_KEY_PREFIX = "cache:article:";

    // 空值缓存过期时间（5 分钟）
    // 空值缓存不需要保留太久，因为数据不存在的事实不会变化
    private static final long NULL_VALUE_TTL = 5; // 分钟

    // 正常数据缓存过期时间（30 分钟 + 随机因子）
    // 30 分钟是基础值，随机因子用于防止缓存雪崩
    private static final long NORMAL_TTL_BASE = 30; // 分钟

    /**
     * 构造方法注入
     */
    public ArticleCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 模拟查询文章（带缓存穿透防护）
     * <p>
     * 执行流程：
     * 1. 查缓存
     * 2. 命中 → 判断是否是空值标记 → 返回结果
     * 3. 未命中 → 查数据库
     * 4. 数据库有结果 → 写入缓存（正常 TTL）
     * 5. 数据库无结果 → 写入空值缓存（短 TTL）
     * 6. 返回结果
     * <p>
     * 这里用 String 模拟文章数据，实际情况是 Article 对象。
     *
     * @param articleId 文章 ID
     * @return 文章内容（或 null）
     */
    public String getArticleWithProtection(Long articleId) {
        // 构建缓存 key
        String cacheKey = CACHE_KEY_PREFIX + articleId;

        // ========== 第一步：查缓存 ==========
        // 使用 RedisTemplate 的 opsForValue() 操作 String 类型
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            // 缓存命中
            if (cached instanceof String && "NULL_VALUE".equals(cached)) {
                // 缓存的是空值标记：数据不存在
                // 直接返回 null，避免穿透到数据库
                // 攻击者用大量不存在的 ID 请求时，只查一次数据库
                return null;
            }
            // 正常数据：直接返回缓存数据
            return (String) cached;
        }

        // ========== 第二步：缓存未命中，查数据库 ==========
        // 模拟数据库查询：根据 articleId 模拟不同的结果
        // 实际项目中这里调用 ArticleMapper.selectById()
        String article = queryFromDatabase(articleId);

        // ========== 第三步：结果写入缓存 ==========
        if (article != null) {
            // 数据库有数据：写入缓存，正常 TTL + 随机因子
            // 随机因子防止缓存雪崩：同一批 key 的过期时间分散
            long ttl = NORMAL_TTL_BASE + randomOffset(); // 30 + 随机 0-5 分钟
            redisTemplate.opsForValue().set(cacheKey, article, ttl, TimeUnit.MINUTES);
        } else {
            // 数据库无数据：写入空值缓存，短 TTL
            // 防止缓存穿透：后续相同请求直接返回 null
            // 空值 TTL 短，因为数据可能被创建（但概率很小）
            redisTemplate.opsForValue().set(
                    cacheKey,
                    "NULL_VALUE",           // 空值标记字符串
                    NULL_VALUE_TTL,          // 5 分钟过期
                    TimeUnit.MINUTES);
        }

        return article;
    }

    /**
     * 模拟数据库查询
     * <p>
     * 实际项目中调用 Mapper 查询数据库。
     * 这里模拟：ID 为 1 的文章存在，其他 ID 不存在。
     *
     * @param articleId 文章 ID
     * @return 文章内容（或 null）
     */
    private String queryFromDatabase(Long articleId) {
        // 模拟数据库查询
        // 实际代码：return articleMapper.selectById(articleId);
        if (articleId != null && articleId == 1L) {
            return "这是 ID 为 1 的文章内容";
        }
        return null; // 其他 ID 不存在，模拟缓存穿透
    }

    /**
     * 生成随机偏移量，防止缓存雪崩
     * <p>
     * 如果所有缓存的过期时间都是 30 分钟，
     * 那么它们会在同一时刻过期，导致数据库压力暴增。
     * 加上随机偏移量后，过期时间分散在 30-35 分钟之间。
     *
     * @return 0-5 分钟的随机偏移量
     */
    private long randomOffset() {
        // 返回 0-5 分钟的随机偏移量
        // ThreadLocalRandom 是线程安全的随机数生成器
        return ThreadLocalRandom.current().nextLong(0, 5);
    }
}
```

### 3.8 控制器（CacheController.java）

```java
package com.passage.redis.controller;

import com.passage.redis.entity.User;
import com.passage.redis.service.AgentSessionService;
import com.passage.redis.service.ArticleCacheService;
import com.passage.redis.service.QuotaService;
import com.passage.redis.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CacheController - 缓存 API 控制器
 * <p>
 * 提供缓存操作和分布式锁的 REST API。
 * 用于演示 Spring Cache 注解、Redisson 分布式锁和缓存穿透防护。
 *
 * @author AI-Passage-Creator
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final UserService userService;
    private final QuotaService quotaService;
    private final AgentSessionService agentSessionService;
    private final ArticleCacheService articleCacheService;

    public CacheController(UserService userService, QuotaService quotaService,
                           AgentSessionService agentSessionService,
                           ArticleCacheService articleCacheService) {
        this.userService = userService;
        this.quotaService = quotaService;
        this.agentSessionService = agentSessionService;
        this.articleCacheService = articleCacheService;
    }

    /**
     * 查询用户信息（测试 Spring Cache）
     * GET /api/cache/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<User> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    /**
     * 扣减配额（测试分布式锁）
     * POST /api/cache/quota/deduct/{userId}
     */
    @PostMapping("/quota/deduct/{userId}")
    public ResponseEntity<Map<String, Object>> deductQuota(@PathVariable Long userId) {
        boolean success = quotaService.deductQuota(userId);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "quotaInfo", quotaService.getQuotaInfo(userId)
        ));
    }

    /**
     * 执行 Agent 任务（测试看门狗）
     * POST /api/cache/agent/execute?userId=1&taskId=xxx
     */
    @PostMapping("/agent/execute")
    public ResponseEntity<String> executeAgent(@RequestParam Long userId,
                                               @RequestParam String taskId) {
        // 看门狗自动续期，即使任务执行时间较长
        agentSessionService.executeAgentTask(userId, taskId);
        return ResponseEntity.ok("Agent 任务执行完成");
    }

    /**
     * 查询文章（测试缓存穿透防护）
     * GET /api/cache/article/{articleId}
     */
    @GetMapping("/article/{articleId}")
    public ResponseEntity<Map<String, Object>> getArticle(
            @PathVariable Long articleId) {
        String article = articleCacheService.getArticleWithProtection(articleId);
        return ResponseEntity.ok(Map.of(
                "articleId", articleId,
                "content", article != null ? article : "null（空值缓存）"
        ));
    }
}
```

### 3.9 启动类（RedisDemoApplication.java）

```java
package com.passage.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RedisDemoApplication - 应用启动类
 * <p>
 * Redis + Redisson 缓存与分布式锁 Demo 的入口。
 * 使用 @SpringBootApplication 自动配置 Spring Boot 环境。
 * <p>
 * 启动后：
 * 1. 自动扫描 com.passage.redis 包下的所有组件
 * 2. 自动配置 Spring Data Redis（RedisTemplate）
 * 3. 自动注册 RedissonClient（分布式锁）
 * 4. 自动配置 Spring Cache（@Cacheable 等注解）
 * 5. 自动配置 MyBatis-Flex Mapper
 *
 * @author AI-Passage-Creator
 */
@SpringBootApplication
public class RedisDemoApplication {

    /**
     * 主方法 —— 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RedisDemoApplication.class, args);
    }
}
```

### 3.10 测试类（RedisDemoApplicationTests.java）

```java
package com.passage.redis;

import com.passage.redis.entity.User;
import com.passage.redis.mapper.UserMapper;
import com.passage.redis.service.AgentSessionService;
import com.passage.redis.service.ArticleCacheService;
import com.passage.redis.service.QuotaService;
import com.passage.redis.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisDemoApplicationTests - Redis + Redisson 测试类
 * <p>
 * 测试缓存和分布式锁的核心功能：
 * 1. Spring Cache 注解（@Cacheable / @CacheEvict）
 * 2. Redisson 分布式锁（配额扣减）
 * 3. 缓存穿透防护（空值缓存）
 * 4. 并发场景下的锁互斥
 *
 * @author AI-Passage-Creator
 */
@SpringBootTest
class RedisDemoApplicationTests {

    /** 用户服务（Spring Cache） */
    @Autowired
    private UserService userService;

    /** 配额服务（分布式锁） */
    @Autowired
    private QuotaService quotaService;

    /** 文章缓存服务（穿透防护） */
    @Autowired
    private ArticleCacheService articleCacheService;

    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;

    /** Redis 模板 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 测试用户 ID */
    private Long testUserId;

    /**
     * 每个测试执行前初始化测试数据
     */
    @BeforeEach
    void setUp() {
        // 清空所有数据
        userMapper.deleteByQuery(new com.mybatisflex.core.query.QueryWrapper().where("1=1"));

        // 创建一个测试用户（配额：50，已用：0）
        User user = new User();
        user.setUserName("test_user");
        user.setDailyQuota(50);     // 每日配额：50 篇
        user.setUsedQuota(0);       // 已用配额：0
        userMapper.insert(user);
        testUserId = user.getId();
    }

    /**
     * 测试 1：Spring Cache @Cacheable 注解
     * <p>
     * 验证 @Cacheable 注解的缓存功能：
     * 1. 第一次查询：缓存未命中，查询数据库
     * 2. 第二次查询：缓存命中，直接从缓存返回
     */
    @Test
    @DisplayName("测试 @Cacheable 缓存注解 - 第二次查询不走数据库")
    void testCacheable() {
        // 第一次查询：缓存未命中，应查询数据库
        User user1 = userService.getUserById(testUserId);
        assertNotNull(user1, "第一次查询应返回用户信息");

        // 第二次查询：缓存命中，直接从缓存返回
        User user2 = userService.getUserById(testUserId);
        assertNotNull(user2, "第二次查询应返回用户信息");

        // 验证两次查询结果一致
        assertEquals(user1.getUserName(), user2.getUserName(), "缓存数据应与数据库一致");
    }

    /**
     * 测试 2：Spring Cache @CacheEvict 注解
     * <p>
     * 验证 @CacheEvict 注解的缓存删除功能：
     * 1. 先查询，缓存被填充
     * 2. 更新用户信息，缓存被删除
     * 3. 再次查询，缓存未命中，重新查询数据库
     */
    @Test
    @DisplayName("测试 @CacheEvict 缓存删除 - 更新后缓存失效，重新查询数据库")
    void testCacheEvict() {
        // 第一次查询：填充缓存
        userService.getUserById(testUserId);

        // 更新用户：缓存被删除
        User updatedUser = new User();
        updatedUser.setId(testUserId);
        updatedUser.setUserName("updated_user");
        userService.updateUser(updatedUser);

        // 第二次查询：缓存未命中（因为被删除了），重新查询数据库
        User cachedUser = userService.getUserById(testUserId);
        assertEquals("updated_user", cachedUser.getUserName(), "缓存应返回更新后的数据");
    }

    /**
     * 测试 3：Redisson 分布式锁 —— 配额扣减
     * <p>
     * 验证分布式锁保证配额扣减的原子性：
     * 1. 初始配额：50/50
     * 2. 扣减一次：49/50
     * 3. 扣减到 0：配额用完，返回 false
     */
    @Test
    @DisplayName("测试分布式锁配额扣减 - 正常扣减和配额用完")
    void testQuotaDeduct() {
        // 初始配额：50/50
        String info = quotaService.getQuotaInfo(testUserId);
        assertTrue(info.contains("0 / 50"), "初始配额应为 0/50");

        // 扣减一次
        boolean result = quotaService.deductQuota(testUserId);
        assertTrue(result, "第一次扣减应成功");

        // 验证配额已扣减
        info = quotaService.getQuotaInfo(testUserId);
        assertTrue(info.contains("1 / 50"), "扣减后配额应为 1/50");

        // 扣减 50 次，直到配额用完
        for (int i = 0; i < 49; i++) {
            quotaService.deductQuota(testUserId);
        }

        // 配额已用完，再次扣减应失败
        result = quotaService.deductQuota(testUserId);
        assertFalse(result, "配额用完后续扣减应失败");
    }

    /**
     * 测试 4：并发场景下的锁互斥
     * <p>
     * 验证分布式锁在并发场景下的互斥性：
     * 1. 50 个线程同时扣减配额
     * 2. 在锁的保护下，只有 50 次扣减成功
     * 3. 最终配额 = 50/50（全部用完，没有超发）
     */
    @Test
    @DisplayName("测试并发配额扣减 - 50个线程同时扣减，最终配额正好用完")
    void testConcurrentQuotaDeduct() throws InterruptedException {
        // 并发线程数
        int threadCount = 50;
        // 计数器：记录成功扣减的次数
        AtomicInteger successCount = new AtomicInteger(0);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        // 倒计时锁存器：等待所有线程完成
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 50 个线程同时扣减配额
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 扣减配额
                    if (quotaService.deductQuota(testUserId)) {
                        // 扣减成功，计数器 +1
                        successCount.incrementAndGet();
                    }
                } finally {
                    // 线程完成，计数器 -1
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成（最多等待 30 秒）
        latch.await();

        // 关闭线程池
        executor.shutdown();

        // 验证：50 次扣减全部成功，没有超发
        // 因为配额是 50，所以最多只能成功 50 次
        String info = quotaService.getQuotaInfo(testUserId);
        assertTrue(info.contains("50 / 50"), "并发扣减后配额应为 50/50，实际: " + info);
    }

    /**
     * 测试 5：缓存穿透防护
     * <p>
     * 验证缓存空值防止缓存穿透的功能：
     * 1. 查询不存在的文章 ID（如 ID=999）
     * 2. 第一次查询：查数据库，未找到，缓存空值
     * 3. 第二次查询：缓存命中空值，直接返回 null，不查数据库
     */
    @Test
    @DisplayName("测试缓存穿透防护 - 不存在的 ID 第二次查询不查数据库")
    void testCachePenetrationProtection() {
        // 查询不存在的文章
        String result1 = articleCacheService.getArticleWithProtection(999L);
        assertNull(result1, "不存在的文章应返回 null");

        // 第二次查询：应直接从缓存返回，不查数据库
        String result2 = articleCacheService.getArticleWithProtection(999L);
        assertNull(result2, "第二次查询应返回 null（空值缓存）");
    }

    /**
     * 测试 6：缓存命中 — 存在的文章
     * <p>
     * 验证正常数据的缓存功能：
     * 1. 查询存在的文章 ID（如 ID=1）
     * 2. 第一次查询：查数据库，回填缓存
     * 3. 第二次查询：缓存命中，直接返回
     */
    @Test
    @DisplayName("测试缓存命中 - 存在的文章第二次查询从缓存返回")
    void testCacheHit() {
        // 查询存在的文章
        String result1 = articleCacheService.getArticleWithProtection(1L);
        assertNotNull(result1, "存在的文章应返回内容");

        // 第二次查询：应直接从缓存返回
        String result2 = articleCacheService.getArticleWithProtection(1L);
        assertNotNull(result2, "第二次查询应返回内容");
        assertEquals(result1, result2, "两次查询结果应一致");
    }
}
```

---

## 四、运行验证

### 4.1 启动项目

```bash
# 进入项目目录
cd redis-demo

# 编译并启动
mvn spring-boot:run
```

启动前，请确保本地已安装并启动 Redis：

```bash
# 启动 Redis（Windows 使用 WSL 或下载 Windows 版本）
redis-server

# 确认 Redis 已启动
redis-cli ping
# 预期返回：PONG
```

启动后，控制台输出类似：

```
[INFO] Scanning for projects...
[INFO] --- spring-boot:3.2.5:run (default-cli) @ redis-demo ---
[INFO] Running com.passage.redis.RedisDemoApplication

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v3.2.5)

2026-08-22T10:00:00.000+08:00  INFO 12345 --- [main] c.p.redis.RedisDemoApplication           : Started RedisDemoApplication in 3.5 seconds
```

### 4.2 测试 API 接口

**1. 查询用户信息（验证 Spring Cache）：**

```bash
# 第一次查询：缓存未命中，查数据库
curl http://localhost:8080/api/cache/user/1

# 第二次查询：缓存命中，直接从缓存返回（响应速度更快）
curl http://localhost:8080/api/cache/user/1
```

**2. 扣减配额（验证分布式锁）：**

```bash
# 扣减一次
curl -X POST http://localhost:8080/api/cache/quota/deduct/1

# 预期返回：{"success":true,"quotaInfo":"已用: 1 / 每日上限: 50"}
```

**3. 查询文章（验证缓存穿透防护）：**

```bash
# 查询不存在的文章（ID=999）
curl http://localhost:8080/api/cache/article/999
# 预期返回：{"articleId":999,"content":"null（空值缓存）"}

# 查询存在的文章（ID=1）
curl http://localhost:8080/api/cache/article/1
# 预期返回：{"articleId":1,"content":"这是 ID 为 1 的文章内容"}
```

### 4.3 验证 Redis 中的缓存数据

```bash
# 进入 Redis 命令行
redis-cli

# 查看所有缓存 key
KEYS *
# 预期输出：users::1  cache:article:1  cache:article:999  ...

# 查看用户缓存
GET "users::1"
# 预期输出：{"id":1,"userName":"test_user",...}

# 查看空值缓存
GET "cache:article:999"
# 预期输出："NULL_VALUE"
```

### 4.4 运行测试

```bash
mvn test
```

预期输出：

```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 五、项目对照

### 5.1 Demo 与真实项目的对比

| 对比维度 | 本 Demo（redis-demo） | 真实项目（ai-passage-creator） |
|---------|----------------------|-------------------------------|
| 数据库 | H2 内存数据库 | MySQL 生产数据库 |
| Redis 模式 | 单机模式 | 主从 + 哨兵模式（高可用） |
| 缓存策略 | Cache-Aside 基础 | Cache-Aside + 延迟双删 |
| 空值缓存 | String 标记 | NullValue 专用对象 |
| 分布式锁 | 基础 tryLock/lock | 锁 + 幂等 Key + 业务校验 |
| 缓存区域 | 3 个区域（users/articles/configs） | 10+ 个缓存区域 |
| 并发测试 | 50 线程 | 1000+ 线程 |
| 看门狗 | 默认 30 秒 | 根据业务配置（30s-120s） |
| 监控 | 无 | Redis 监控 + 缓存命中率统计 |
| 限流 | 无 | Redisson RateLimiter |

### 5.2 Demo 的局限性

1. **无 Redis 高可用**：Demo 使用单机 Redis，生产环境需要主从 + 哨兵
2. **无缓存监控**：Demo 未实现缓存命中率统计和 Redis 性能监控
3. **无延迟双删**：Demo 仅实现基础 Cache-Aside，未实现延迟双删策略
4. **无分布式限流**：Demo 未使用 Redisson 的 RateLimiter 限流器
5. **无锁续期监控**：Demo 未监控看门狗的续期情况

### 5.3 进阶路径

从本 Demo 到真实项目，需要掌握以下知识：

| 步骤 | 知识点 | 参考文章 |
|------|--------|----------|
| 1 | Redis 基础：数据类型、缓存策略 | 08 Redis + Redisson（本文） |
| 2 | 分布式锁：RLock、tryLock、看门狗 | 08 Redis + Redisson（本文） |
| 3 | 缓存穿透/击穿/雪崩防护 | 08 Redis + Redisson（本文进阶） |
| 4 | Redis 高可用：主从 + 哨兵 | Redis 官方文档 |
| 5 | 延迟双删 + 缓存一致性 | 后续系列 |
| 6 | Redisson RateLimiter 限流器 | Redisson 官方文档 |

---

## 六、面试题

### Q1: 项目中 Redis 缓存有哪些使用场景？为什么用 Redis 而不是本地缓存？

**参考答案：**

**项目中的 Redis 缓存使用场景：**

| 场景 | 缓存内容 | 用途 | 缓存策略 |
|------|----------|------|----------|
| 用户信息 | 用户基本信息、VIP 状态 | 每次请求都要校验用户权限，避免反复查库 | Cache-Aside，30 分钟 TTL |
| 文章列表 | 用户文章列表、文章详情 | 列表页和详情页高频读取 | Cache-Aside，10 分钟 TTL |
| 系统配置 | 配图策略配置、定价配置 | 变化频率低，但读取频率高 | Cache-Aside，2 小时 TTL |
| Agent 会话 | 当前 Agent 执行状态、中间结果 | 多实例共享 Agent 状态，支持断点续作 | 手动读写，随任务生命周期 |
| 配额计数 | 用户每日已用配额 | 实时扣减，需要原子操作 | Redis 自增 + 分布式锁保护 |

**为什么用 Redis 而不是本地缓存（Caffeine / Guava Cache）：**

| 维度 | Redis（分布式缓存） | Caffeine（本地缓存） |
|------|-------------------|---------------------|
| **数据共享** | 所有实例共享同一份缓存 | 每个实例各自缓存，数据不一致 |
| **一致性** | 删除缓存后所有实例都感知 | 需要广播通知其他实例删除 |
| **容量** | 独立服务器，容量大（GB 级） | 受限于 JVM 堆内存（MB 级） |
| **持久化** | 支持 RDB/AOF 持久化，重启不丢 | 重启后缓存全部丢失 |
| **分布式锁** | 原生支持（Redisson RLock） | 不支持（只能单机锁） |
| **延迟** | 1-5ms（网络 IO） | 纳秒级（内存访问） |
| **适用场景** | 多实例共享数据、分布式锁、会话存储 | 单实例、不共享、对延迟极高要求 |

**项目选型结论：** AI 项目是多实例部署的，用户会话、Agent 状态、配额计数都需要跨实例共享，本地缓存无法满足需求。**Redis 虽然比本地缓存多一次网络 IO（1ms vs 0.001ms），但换来了数据共享和一致性，在 AI 应用场景下是值得的。**

**追问应对：** "如果 Redis 宕机了怎么办？" 答：缓存层面降级为"跳过缓存，直接查数据库"，业务不受影响（只是响应变慢）。分布式锁降级为"不锁定"，在配额扣减等高并发场景可能出现超发，但概率极低。生产环境应部署 Redis 主从 + 哨兵集群，保证 Redis 高可用。

### Q2: 分布式锁的选型依据是什么？Redis 分布式锁和 ZooKeeper 分布式锁如何选择？

**参考答案：**

**分布式锁选型需要评估三个维度：**

| 维度 | 说明 | 重要性 |
|------|------|--------|
| **性能** | 获取锁的延迟和吞吐量 | 高（核心业务） |
| **一致性** | 锁的互斥性保证强度 | 高（不容有失） |
| **运维成本** | 额外组件的部署和维护 | 中（决定落地难度） |

**Redis 分布式锁 vs ZooKeeper 分布式锁：**

| 对比维度 | Redis（Redisson） | ZooKeeper / etcd |
|----------|------------------|-----------------|
| **一致性模型** | AP（最终一致） | CP（强一致） |
| **性能** | 高（1-5ms 延迟） | 中（10-50ms 延迟） |
| **吞吐量** | 10 万+ QPS | 1 万+ QPS |
| **自动释放** | 过期时间 + 看门狗续期 | 会话断开自动删除临时节点 |
| **可重入** | 原生支持（Hash 结构计数） | 需自行实现 |
| **续期机制** | 看门狗自动续期 | 心跳检测 |
| **脑裂问题** | 可能同时持有锁（AP 模型） | 无脑裂（CP 模型） |
| **运维复杂度** | 低（已有 Redis 则零成本） | 高（需额外部署 ZooKeeper 集群） |

**选型决策树：**

```
是否需要分布式锁？
    ├── 不需要 → 单机锁（synchronized / ReentrantLock）
    └── 需要 →
        ├── 已有 Redis？ → 用 Redisson（零成本，高性能）
        ├── 对一致性要求极高（金融级）？ → ZooKeeper（强一致）
        └── 一般业务场景 → Redisson（性能好，功能全）
```

**项目选型依据：** 项目已经使用了 Redis（缓存 + 会话存储），零额外成本引入 Redisson 分布式锁。配额扣减虽然需要互斥，但偶尔的"超发"（配额从 50 变成 51）在业务上是可以接受的——不是金融交易，不需要强一致性保证。**"用 Redis 的 AP 模型，换 10 倍性能，代价是千分之一的概率出现问题，业务上可以接受。"**

**追问应对：** "RedLock 红锁是什么？" 答：RedLock 是 Redis 官方提出的多节点分布式锁算法，要求锁在大多数 Redis 节点（N/2+1）上同时成功才算获取锁，解决了单点 Redis 的脑裂问题。但 RedLock 也存在争议——Martin Kleppmann 曾发文批评其不是真正的强一致。且需要 5 个 Redis 节点，运维成本高。**生产实践中，大多数场景使用单机 Redisson + 主从哨兵已经足够，不需要 RedLock。**

### Q3: Redisson 看门狗（Watchdog）的原理是什么？如果业务执行时间超过锁过期时间会怎样？

**参考答案：**

**看门狗原理：**

```
lock.lock()  // 默认可过期时间 30 秒
    ↓
Redisson 内部启动一个 Netty 定时任务（TimeoutTask）
    ↓
定时任务每 10 秒执行一次：
    1. 检查锁是否还被当前线程持有
       （通过 Redis Hash 结构中的 field 判断）
    2. 如果持有，执行 Lua 脚本续期：
       "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
            redis.call('pexpire', KEYS[1], ARGV[2])
            return 1
        end
        return 0"
    3. 续期成功：锁的过期时间重置为 30 秒
    4. 续期失败（锁已被释放）：停止定时任务
    ↓
业务执行完毕 → lock.unlock() → 删除锁 → 看门狗停止
客户端宕机 → 看门狗线程停止 → 锁到期自动释放（防死锁）
```

**看门狗超时时间配置：**

```java
// 默认看门狗超时时间：30 秒
config.setLockWatchdogTimeout(30_000); // 30 秒

// 如果业务执行时间普遍较长，可以调大
config.setLockWatchdogTimeout(60_000); // 60 秒
```

**看门狗续期时间间隔：** 续期间隔 = `lockWatchdogTimeout / 3`，默认 30 秒 / 3 = 10 秒。

**业务执行时间超过锁过期时间：**

不会出现业务没执行完锁就过期的情况，因为**看门狗会在锁过期前自动续期**。具体来说：

```
时间线：
t=0s:    lock.lock() 获取锁，锁过期时间 = 30s
t=10s:   看门狗检查 → 锁还在持有 → 续期到 30s（从 t=10s 算起，过期时间 = t=40s）
t=20s:   看门狗检查 → 锁还在持有 → 续期到 30s（从 t=20s 算起，过期时间 = t=50s）
t=35s:   业务执行完毕 → unlock() → 看门狗停止
```

**看门狗不启动的场景：**

```java
// 场景一：指定了租约时间（leaseTime），看门狗不启动
lock.lock(10, TimeUnit.SECONDS); // 10 秒后自动释放，看门狗不工作

// 场景二：tryLock 指定了租约时间
lock.tryLock(3, 10, TimeUnit.SECONDS); // 等待 3 秒，租约 10 秒，看门狗不工作
```

**追问应对：** "看门狗续期失败（Redis 宕机或网络超时）会怎样？" 答：续期失败时，锁的过期时间不会延长，锁会在剩余的过期时间后自动释放。此时如果有其他线程/实例在等待锁，会正常获取到锁。但原来的业务线程并不知道锁已经释放，会继续执行——这就出现了"两个线程同时持有锁"的短暂不一致。**解决方案：** 业务代码中定期检查锁的状态（如每 5 秒检查一次 `lock.isHeldByCurrentThread()`），发现锁丢失后主动停止业务执行。

---

## 七、避坑指南

### 7.1 不要在 finally 块中不加判断直接释放锁

```java
// ❌ 错误：不加判断直接释放锁
// 如果当前线程已经不持有锁（比如锁已过期），调用 unlock() 会抛异常
// 更严重的是：可能释放了其他线程持有的锁
finally {
    lock.unlock();  // 错误！
}

// ✅ 正确：先判断是否持有锁，再释放
// isHeldByCurrentThread() 检查当前线程是否还持有锁
finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();  // 安全释放
    }
}
```

### 7.2 锁 key 要精确到资源，避免使用大锁

```java
// ❌ 错误：使用大锁，所有用户共用一把锁
// 用户 A 扣减配额时，用户 B 也在等待，互不相关地阻塞
String lockKey = "lock:quota:all";  // 一把锁锁住所有用户！

// ✅ 正确：锁 key 精确到每个用户
// 不同用户互不影响，只有同一用户的并发请求才需要等待
String lockKey = "lock:quota:" + userId;  // 每个用户一把锁
```

### 7.3 缓存过期时间要加随机因子，防止缓存雪崩

```java
// ❌ 错误：所有缓存使用相同的过期时间
// 所有缓存同时过期，同时请求数据库，数据库压力暴增
redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES);

// ✅ 正确：过期时间 = 基础值 + 随机偏移量
// 缓存过期时间分散在 30-35 分钟之间，避免集中过期
long ttl = 30 + ThreadLocalRandom.current().nextLong(0, 5); // 30 + 随机 0-5 分钟
redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.MINUTES);
```

### 7.4 配置参考

```yaml
# application.yml —— Redis + Redisson 完整配置参考
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your-password-here
      database: 0
      connect-timeout: 5000
      timeout: 5000
      lettuce:
        pool:
          max-active: 16          # 最大连接数（根据并发量调整）
          max-idle: 8             # 最大空闲连接数
          min-idle: 4             # 最小空闲连接数（核心连接数）
          max-wait: 3000          # 获取连接最大等待时间（毫秒）
        # 关闭超时：优雅关闭时等待操作完成
        shutdown-timeout: 200ms

# Redisson 配置
redisson:
  address: redis://localhost:6379
  password: your-password-here
  # 看门狗超时时间（毫秒）
  # 默认 30000（30 秒），根据业务最慢执行时间调整
  watchdog-timeout: 30000
  pool:
    min-idle: 4
    max-idle: 8
    max-active: 16
  # 编解码器
  codec: json
```

### 7.5 测试环境注意事项

```yaml
# 测试环境配置建议
# 1. 使用本地 Redis，不要连接生产环境
# 2. 测试前执行 FLUSHALL 清空 Redis 数据
# 3. 测试环境中 Redis 可以没有密码（本地环境）
# 4. 使用 @BeforeEach 清空测试数据，避免测试间相互影响
# 5. 并发测试时注意线程数不要超过 Redis 连接池大小

# 清空 Redis 的命令
# redis-cli FLUSHALL
```