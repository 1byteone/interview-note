# MiniBlog — 基于 Redis ZSet 的排行榜系统

> 等级：🎯 独立小项目
> 目标：只用 Redis + Java 核心 API（无 Spring Boot），实现一个文章热度排行榜系统，掌握 ZSet 的核心操作。

---

## 一、项目概述

### 技术栈

- Java 17+
- Jedis / Lettuce（Redis 客户端）
- Redis ZSet 作为核心数据结构
- 纯控制台应用，无 Web 框架

### 功能

1. 文章发布与点赞
2. 热度计算（点赞 + 评论 + 时间衰减）
3. 实时排行榜（Top 10/20）
4. 定时刷新（热度衰减）
5. 按时间范围查看热度

---

## 二、核心设计

### 2.1 数据模型

```java
public record Article(String id, String title, String author, LocalDateTime createdAt) {}

public record RankItem(String articleId, String title, double score) {}
```

### 2.2 Redis 数据结构设计

| 场景 | Redis 数据结构 | Key 格式 | 说明 |
|------|---------------|----------|------|
| 文章存储 | Hash | `article:{id}` | 文章标题、作者、创建时间 |
| 点赞数 | String | `article:like:{id}` | 原子自增 |
| 评论数 | String | `article:comment:{id}` | 原子自增 |
| 热度排行 | ZSet | `rank:hot` | score = 热度分数 |
| 周榜 | ZSet | `rank:weekly` | 每周独立的排行榜 |

### 2.3 热度计算公式

```
热度 = 点赞数 x 3 + 评论数 x 5 + 时间衰减因子

时间衰减因子 = - (当前时间 - 发布时间) / (24 x 3600) x 10
```

**说明**：每过一天，热度衰减 10 分。新文章相对老文章有天然优势。

### 2.4 完整实现

```java
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Tuple;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RedisRankingService {
    private final JedisPool jedisPool;
    private static final String RANK_KEY = "rank:hot";
    private static final String WEEKLY_RANK_KEY = "rank:weekly";
    private static final String ARTICLE_PREFIX = "article:";
    private static final String LIKE_PREFIX = "article:like:";
    private static final String COMMENT_PREFIX = "article:comment:";

    public RedisRankingService() {
        this.jedisPool = new JedisPool("localhost", 6379);
    }

    // ========== 文章管理 ==========

    /**
     * 发布文章
     */
    public void publishArticle(String id, String title, String author) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 存储文章基本信息
            String articleKey = ARTICLE_PREFIX + id;
            jedis.hset(articleKey, "title", title);
            jedis.hset(articleKey, "author", author);
            jedis.hset(articleKey, "createdAt", LocalDateTime.now().toString());

            // 初始化点赞和评论数量
            jedis.set(LIKE_PREFIX + id, "0");
            jedis.set(COMMENT_PREFIX + id, "0");

            // 计算初始热度（新文章热度 = 0，但时间衰减为 0）
            updateScore(jedis, id, title);
            System.out.println("文章发布成功: " + title);
        }
    }

    /**
     * 点赞文章
     */
    public void likeArticle(String articleId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Long count = jedis.incr(LIKE_PREFIX + articleId);
            System.out.println("点赞成功! 当前点赞数: " + count);
            // 更新热度
            String title = jedis.hget(ARTICLE_PREFIX + articleId, "title");
            if (title != null) {
                updateScore(jedis, articleId, title);
            }
        }
    }

    /**
     * 评论文章
     */
    public void commentArticle(String articleId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Long count = jedis.incr(COMMENT_PREFIX + articleId);
            System.out.println("评论成功! 当前评论数: " + count);
            // 更新热度
            String title = jedis.hget(ARTICLE_PREFIX + articleId, "title");
            if (title != null) {
                updateScore(jedis, articleId, title);
            }
        }
    }

    // ========== 排行榜 ==========

    /**
     * 计算并更新热度分
     */
    private void updateScore(Jedis jedis, String articleId, String title) {
        // 获取点赞和评论数
        String likeStr = jedis.get(LIKE_PREFIX + articleId);
        String commentStr = jedis.get(COMMENT_PREFIX + articleId);
        String createdAtStr = jedis.hget(ARTICLE_PREFIX + articleId, "createdAt");

        int likes = likeStr != null ? Integer.parseInt(likeStr) : 0;
        int comments = commentStr != null ? Integer.parseInt(commentStr) : 0;
        LocalDateTime createdAt = createdAtStr != null
            ? LocalDateTime.parse(createdAtStr)
            : LocalDateTime.now();

        // 热度计算：点赞 x3 + 评论 x5 - 时间衰减
        long hoursSinceCreation = java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
        double timeDecay = hoursSinceCreation * 0.5;  // 每小时衰减 0.5 分

        double score = likes * 3.0 + comments * 5.0 - timeDecay;
        score = Math.max(0, score);  // 热度不低于 0

        // 更新 ZSet
        jedis.zadd(RANK_KEY, score, articleId + ":" + title);
        jedis.zadd(WEEKLY_RANK_KEY, score, articleId + ":" + title);
    }

    /**
     * 获取全局排行榜 Top N
     */
    public List<RankItem> getTopN(int n) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 按分数降序获取
            Set<Tuple> tuples = jedis.zrevrangeWithScores(RANK_KEY, 0, n - 1);
            return parseRankItems(tuples);
        }
    }

    /**
     * 获取周榜 Top N
     */
    public List<RankItem> getWeeklyTopN(int n) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<Tuple> tuples = jedis.zrevrangeWithScores(WEEKLY_RANK_KEY, 0, n - 1);
            return parseRankItems(tuples);
        }
    }

    /**
     * 获取某篇文章的排名
     */
    public Long getRank(String articleId) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 获取所有 entry（因为 ZSet 存的是 articleId:title，需要模糊匹配）
            Set<String> entries = jedis.zrevrange(RANK_KEY, 0, -1);
            long rank = 0;
            for (String entry : entries) {
                rank++;
                if (entry.startsWith(articleId + ":")) {
                    return rank;
                }
            }
            return null;  // 未上榜
        }
    }

    // ========== 工具方法 ==========

    private List<RankItem> parseRankItems(Set<Tuple> tuples) {
        List<RankItem> items = new ArrayList<>();
        int rank = 1;
        for (Tuple tuple : tuples) {
            String element = tuple.getElement();
            double score = tuple.getScore();
            String[] parts = element.split(":", 2);
            String articleId = parts[0];
            String title = parts.length > 1 ? parts[1] : "未知";
            items.add(new RankItem(articleId, title, score));
            rank++;
        }
        return items;
    }

    // ========== 定时任务：每周清理周榜 ==========

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void startWeeklyReset() {
        scheduler.scheduleAtFixedRate(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(WEEKLY_RANK_KEY);
                System.out.println("周榜已重置");
            }
        }, 7, 7, TimeUnit.DAYS);
    }

    // ========== 主程序 ==========

    public static void main(String[] args) throws Exception {
        RedisRankingService service = new RedisRankingService();

        // 1. 发布文章
        service.publishArticle("1", "Redis 从入门到精通", "张三");
        service.publishArticle("2", "Spring Boot 实战指南", "李四");
        service.publishArticle("3", "微服务架构设计", "王五");
        service.publishArticle("4", "Java 并发编程", "赵六");
        service.publishArticle("5", "Docker 容器化部署", "钱七");

        // 2. 模拟点赞和评论
        service.likeArticle("1");
        service.likeArticle("1");
        service.likeArticle("1");  // 文章 1 获得 3 赞
        service.commentArticle("1");  // 文章 1 获得 1 评论

        service.likeArticle("2");
        service.likeArticle("2");  // 文章 2 获得 2 赞
        service.commentArticle("2");
        service.commentArticle("2");  // 文章 2 获得 2 评论

        service.likeArticle("3");  // 文章 3 获得 1 赞

        // 3. 显示排行榜
        System.out.println("\n===== 实时排行榜 =====");
        List<RankItem> topN = service.getTopN(5);
        int rank = 1;
        for (RankItem item : topN) {
            System.out.printf("第 %d 名: %s (文章 %s), 热度: %.1f\n",
                rank++, item.title(), item.articleId(), item.score());
        }

        // 4. 查询某篇文章排名
        Long rank1 = service.getRank("1");
        System.out.println("\n文章 1 排名: " + (rank1 != null ? "第 " + rank1 + " 名" : "未上榜"));

        // 5. 验证数据
        System.out.println("\n===== 验证 =====");
        try (Jedis jedis = service.jedisPool.getResource()) {
            System.out.println("总排行榜文章数: " + jedis.zcard("rank:hot"));
        }

        // 关闭连接池
        service.jedisPool.close();
        service.scheduler.shutdown();
    }
}
```

---

## 三、涉及的核心知识点

| 知识点 | 应用位置 | 面试要点 |
|--------|----------|---------|
| ZSet 增删改查 | `zadd`、`zrevrangeWithScores`、`zcard`、`zrem` | 排行榜的核心数据结构 |
| 分数计算 | 热度公式 | 含时间衰减，新文章优势 |
| Hash | 文章信息存储 | `hset`、`hget` 存储结构化数据 |
| String 原子操作 | 点赞/评论计数 | `incr` 原子自增 |
| 定时任务 | 周榜重置 | ScheduledExecutorService |
| 连接池 | JedisPool | 资源管理，避免频繁创建连接 |

---

## 四、扩展方向

1. **分页排行榜**：用 `zrevrangeWithScores` 的 start/end 参数实现分页
2. **多维度排行**：同时维护日榜、周榜、月榜（多个 ZSet Key）
3. **个性化推荐**：结合用户行为，生成个性化热度排行
4. **Web API**：用 Java 内置 `HttpServer` 或 Spring Boot 暴露 REST 接口
5. **可视化**：前端用 Chart.js 或 ECharts 展示排行榜趋势

---

## 五、项目结构

```
mini-blog/
├── README.md                  ← 本文档
├── src/
│   └── RedisRankingService.java  ← 完整实现
└── pom.xml (可选)
```

> 进入面试冲刺篇：速记版、深挖题、场景题、代码题。