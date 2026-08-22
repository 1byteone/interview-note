# 推荐资源

> Java 核心知识的学习资源，包括书籍、网站、视频、开源项目。

---

## 一、书籍推荐

### 必读经典

| 书名 | 难度 | 推荐理由 | 读完能解决什么问题 |
|------|------|---------|-------------------|
| 《Java 核心技术 卷 I》 | 入门 | 最全面的 Java 基础教程，覆盖集合、并发、IO | 系统梳理 Java 基础，面试基础题全覆盖 |
| 《深入理解 Java 虚拟机》（周志明） | 进阶 | **JVM 圣经**，第 3 版涵盖 G1/ZGC | JVM 面试题几乎全部出自此书 |
| 《Java 并发编程的艺术》 | 进阶 | 并发编程深度解析，AQS/synchronized/线程池 | 并发面试从入门到精通 |
| 《Java 8 实战》 | 入门 | Lambda + Stream + Optional 实战 | 函数式编程思路，日常编码效率翻倍 |
| 《Effective Java》（第 3 版） | 进阶 | 90 条 Java 最佳实践 | 写出高质量的 Java 代码 |

### 补充阅读

| 书名 | 适合人群 |
|------|---------|
| 《On Java 8》（Bruce Eckel） | 想从 C++ 转 Java 的开发者 |
| 《Java 性能权威指南》 | 需要做 JVM 调优的资深工程师 |
| 《Netty 权威指南》 | 需要做网络编程、网关开发的工程师 |
| 《Java 编程思想》（第 4 版） | 时间充裕时阅读，部分内容偏旧（Java 5） |

---

## 二、在线资源

### 文档与教程

| 资源 | 链接 | 说明 |
|------|------|------|
| JavaGuide | https://javaguide.cn | 面试宝典，Java 核心全覆盖，持续更新 |
| 并发编程网 | http://ifeve.com | 并发编程中文资料最全的网站 |
| Java 官方文档 | https://docs.oracle.com/en/java/ | 最权威的 API 文档 |
| Baeldung | https://www.baeldung.com | 英文教程，Spring + Java 核心全覆盖 |
| Program Creek | https://www.programcreek.com | 大量源码分析文章 |

### 源码分析

| 资源 | 内容 |
|------|------|
| HashMap 源码分析（美团技术博客） | https://tech.meituan.com/2016/06/24/java-hashmap.html |
| ConcurrentHashMap 源码分析（美团技术博客） | https://tech.meituan.com/2016/06/24/java-concurrenthashmap.html |
| Java 线程池实现原理 | 阿里 Java 手册泰山版解读 |

---

## 三、视频推荐

| 名称 | 平台 | 说明 |
|------|------|------|
| 尚硅谷 JVM 全套教程（宋红康） | B站 | 最好的 JVM 入门视频，配合《深入理解 JVM》使用 |
| 黑马程序员 Java 并发编程 | B站 | 并发编程入门到实战，适合面试系统复习 |
| JUC 并发编程（狂神说） | B站 | 高并发快速入门，适合考前突击 |
| Java 集合框架源码分析 | B站 | 逐行讲解 HashMap/ConcurrentHashMap 源码 |

---

## 四、开源项目

| 项目 | 说明 | 推荐理由 |
|------|------|---------|
| JDK 源码 | 你的 JDK 安装目录下的 `src.zip` | 最权威的源码，直接看 ArrayList、HashMap、ThreadPoolExecutor 实现 |
| Caffeine | 高性能本地缓存 | 看 W-TinyLFU 淘汰策略的实现 |
| Netty | 异步事件驱动网络框架 | 看 NIO 的最佳实践，ByteBuf 零拷贝设计 |
| disruptor | 无锁并发框架 | 学习 CAS + 环形缓冲区 + 伪共享（false sharing）处理 |

---

## 五、面试刷题

| 平台 | 说明 | 推荐题目 |
|------|------|---------|
| LeetCode | 算法题 | 146. LRU Cache、设计类题目 |
| 牛客网 | 面试题 | Java 专项练习、大厂面试真题 |
| 万题面试 | 模拟面试 | 按知识点分类的 Java 面试题 |

---

## 六、学习路线建议

```
第一阶段（1-2 周）：基础巩固
  ├── 《Java 核心技术 卷 I》通读
  └── JavaGuide 集合篇 + JVM 篇

第二阶段（2-3 周）：深度分析
  ├── 《深入理解 Java 虚拟机》（重点：GC + 内存模型）
  ├── 《Java 并发编程的艺术》（重点：AQS + 线程池）
  └── 阅读 JDK 源码（HashMap、ConcurrentHashMap、ThreadPoolExecutor）

第三阶段（1 周）：项目实战
  ├── MiniBlog 动手实现
  └── AI 商城集成点理解

第四阶段（考前 1 天）：面试冲刺
  ├── quick-revision.md 速记版
  ├── deep-dive.md 深挖题
  ├── scenario.md 场景题
  └── coding.md 代码题
```

> 祝你面试顺利，Java 核心是后端基本功，值得反复锤炼。