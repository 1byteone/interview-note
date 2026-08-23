# 推荐资源

> Redis 学习资源，包括书籍、网站、视频、开源项目。

---

## 一、书籍推荐

### 必读经典

| 书名 | 难度 | 推荐理由 | 读完能解决什么问题 |
|------|------|---------|-------------------|
| 《Redis 设计与实现》（黄健宏） | 入门→进阶 | **Redis 中文圣经**，源码级解析数据结构、持久化、复制、哨兵 | 面试深挖题全覆盖，跳表、RDB/AOF 原理都在这本书里 |
| 《Redis 开发与运维》（付磊/张益军） | 入门→进阶 | 实战经验丰富，大 Key 排查、慢查询、集群运维 | 生产环境遇到的问题基本都能找到答案 |
| 《Redis 核心原理与实战》（钱文品） | 入门 | 适合初学者，图文并茂，手把手搭建 | 零基础入门，快速上手 Redis |
| 《Redis 实战》（Josiah L. Carlson） | 入门→进阶 | 英文经典，涵盖 Redis 在游戏、社交、广告等场景的实践 | 拓宽 Redis 应用视野 |

### 补充阅读

| 书名 | 适合人群 |
|------|---------|
| 《Redis 深度历险：核心原理与应用实践》（钱文品） | 面试冲刺，精炼 |

---

## 二、在线资源

### 文档与教程

| 资源 | 链接 | 说明 |
|------|------|------|
| Redis 官方文档 | https://redis.io/docs | 最权威的文档，命令参考、配置说明 |
| Redis 命令参考（中文） | https://redis.com.cn/commands.html | 中文版命令速查 |
| Redisson 官方文档 | https://redisson.org/docs | 分布式锁、高级数据结构 API |
| Spring Data Redis | https://spring.io/projects/spring-data-redis | Spring Boot 整合官方文档 |
| 阿里云 Redis 最佳实践 | https://help.aliyun.com/product/26340.html | 生产环境配置、大 Key 处理 |

### 博客与文章

| 资源 | 内容 |
|------|------|
| 美团技术博客：Redis 缓存三大问题 | https://tech.meituan.com/2017/03/17/cache-about.html |
| 美团技术博客：Redis 分布式锁 | https://tech.meituan.com/2018/03/01/redis-distributed-lock.html |
| 腾讯云：Redis 大 Key 排查与处理 | 腾讯云官方文档 |
| 阿里云 Jedis 常见问题 | 阿里云官方文档 |

---

## 三、视频推荐

| 名称 | 平台 | 说明 |
|------|------|------|
| 尚硅谷 Redis 全套教程（周阳） | B站 | 最好的 Redis 入门视频，从安装到集群 |
| 黑马程序员 Redis 入门到精通 | B站 | 实战项目多，包含秒杀系统 |
| Redis 核心技术与实战（蒋德钧） | 极客时间 | 专栏形式，深入浅出，适合面试准备 |
| 跟高手学 Redis 源码 | B站 | 看源码分析，适合进阶 |

---

## 四、开源项目

| 项目 | 说明 | 推荐理由 |
|------|------|---------|
| Redisson | Java Redis 客户端，功能最全 | 分布式锁、信号量、限流器、集合，面试必用 |
| Jedis | 轻量级 Redis 客户端 | 源码简单，适合学习 Redis 协议 |
| Lettuce | 异步非阻塞 Redis 客户端 | Spring Data Redis 默认客户端，Netty 实现 |
| Redis 官方源码 | C 语言实现 | 看单线程模型、事件循环、跳表实现 |
| Caffeine | 高性能本地缓存 | 多级缓存中的 L1 缓存，W-TinyLFU 淘汰策略 |

---

## 五、面试刷题

| 平台 | 说明 | 推荐题目 |
|------|------|---------|
| LeetCode | 算法题 | 146. LRU Cache、设计类题目 |
| 牛客网 | 面试题 | Redis 专项练习、大厂面试真题 |
| JavaGuide | 面试宝典 | https://javaguide.cn — Redis 篇全覆盖 |
| 小林 Coding | 图解面试 | https://xiaolincoding.com — Redis 图解 |

---

## 六、学习路线建议

```
第一阶段（1 天）：基础入门
  ├── Docker 启动 Redis，敲 5 大结构命令
  ├── 理解过期策略、内存淘汰
  └── 完成商品缓存最小案例

第二阶段（2 天）：核心原理
  ├── 持久化 RDB + AOF + 混合持久化
  ├── 缓存穿透/击穿/雪崩 + 缓存一致性
  ├── 分布式锁演进 + Redisson 实战
  └── 主从/哨兵/Cluster 架构

第三阶段（1 天）：项目实战
  ├── AI 商城集成（多级缓存/秒杀/幂等）
  └── 排行榜小项目（ZSet 操作）

第四阶段（考前 1 小时）：面试冲刺
  ├── quick-revision.md 速记版
  ├── deep-dive.md 深挖题
  ├── scenario.md 场景题
  └── coding.md 代码题
```

> 祝面试顺利！Redis 是后端面试中性价比最高的中间件——掌握好它，面试官会觉得你"懂缓存、懂并发、懂分布式"。