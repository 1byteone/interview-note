# MiniBlog — 内存版迷你博客

> 等级：🎯 独立小项目
> 目标：仅使用 Java 核心 API（无 Spring、无数据库），实现一个内存版博客系统，理解集合 + 并发 + Stream API 的综合运用。

---

## 一、项目概述

### 技术栈

- Java 17+（Record、Stream API）
- 集合框架（ConcurrentHashMap、ArrayList、TreeSet）
- 并发工具（ThreadPoolExecutor、ReentrantLock、CompletableFuture）
- 纯内存存储，无外部依赖

### 功能

1. 用户注册、登录
2. 发布文章、查看文章列表
3. 按标签搜索文章
4. 文章点赞、评论
5. 并发请求处理

---

## 二、核心设计

### 2.1 数据模型

```java
public record User(Long id, String username, String password, String email, LocalDateTime createdAt) {}

public record Article(Long id, Long userId, String title, String content, Set<String> tags,
                      int likeCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}

public record Comment(Long id, Long articleId, Long userId, String content, LocalDateTime createdAt) {}
```

### 2.2 存储层

```java
public class InMemoryStore {
    // 使用 ConcurrentHashMap 保证线程安全
    private final ConcurrentHashMap<Long, User> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Article> articles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Comment> comments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> tagIndex = new ConcurrentHashMap<>();

    // 按标签查询文章
    public List<Article> findByTag(String tag) {
        Set<Long> ids = tagIndex.getOrDefault(tag, Collections.emptySet());
        return ids.stream()
            .map(articles::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(Article::createdAt).reversed())
            .collect(Collectors.toList());
    }

    // 添加文章时建立标签索引
    public void saveArticle(Article article) {
        articles.put(article.id(), article);
        for (String tag : article.tags()) {
            tagIndex.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet()).add(article.id());
        }
    }
}
```

### 2.3 服务层

```java
public class BlogService {
    private final InMemoryStore store = new InMemoryStore();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ReentrantLock lock = new ReentrantLock();

    // 注册用户
    public User register(String username, String password, String email) {
        // 简单校验
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        User user = new User(idGenerator.getAndIncrement(), username, password, email, LocalDateTime.now());
        store.saveUser(user);
        return user;
    }

    // 发布文章
    public Article publishArticle(Long userId, String title, String content, Set<String> tags) {
        lock.lock();
        try {
            Article article = new Article(
                idGenerator.getAndIncrement(), userId, title, content,
                tags != null ? tags : Set.of(), 0, LocalDateTime.now(), LocalDateTime.now()
            );
            store.saveArticle(article);
            return article;
        } finally {
            lock.unlock();
        }
    }

    // 点赞文章（CAS 方式，无锁）
    public void likeArticle(Long articleId) {
        store.likeArticle(articleId);  // 内部使用 AtomicInteger 或 CAS
    }

    // 搜索文章（Stream API）
    public List<Article> search(String keyword) {
        return store.getAllArticles().stream()
            .filter(a -> a.title().contains(keyword) || a.content().contains(keyword))
            .sorted(Comparator.comparing(Article::createdAt).reversed())
            .limit(20)
            .collect(Collectors.toList());
    }
}
```

### 2.4 并发请求处理

```java
public class BlogServer {
    private final BlogService blogService = new BlogService();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4, 8, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public void handleRequest(String action, Map<String, Object> params) {
        executor.submit(() -> {
            try {
                switch (action) {
                    case "register" -> {
                        User user = blogService.register(
                            (String) params.get("username"),
                            (String) params.get("password"),
                            (String) params.get("email")
                        );
                        System.out.println("注册成功: " + user);
                    }
                    case "publish" -> {
                        Article article = blogService.publishArticle(
                            (Long) params.get("userId"),
                            (String) params.get("title"),
                            (String) params.get("content"),
                            (Set<String>) params.get("tags")
                        );
                        System.out.println("发布成功: " + article.title());
                    }
                    case "search" -> {
                        List<Article> results = blogService.search((String) params.get("keyword"));
                        results.forEach(a -> System.out.println(a.title()));
                    }
                    default -> throw new IllegalArgumentException("未知操作: " + action);
                }
            } catch (Exception e) {
                System.err.println("请求处理失败: " + e.getMessage());
            }
        });
    }
}
```

---

## 三、涉及的核心知识点

| 知识点 | 应用位置 | 面试要点 |
|--------|----------|---------|
| Record | 数据模型 | 不可变、自动 equals/hashCode |
| ConcurrentHashMap | 内存存储 + 标签索引 | CAS 插入、读无锁、扩容 |
| Stream API | 搜索、排序、过滤 | 惰性求值、中间/终端操作 |
| ThreadPoolExecutor | 请求处理 | 核心参数、拒绝策略 |
| ReentrantLock | 发布文章（写保护） | 公平/非公平、可重入 |
| AtomicLong | ID 生成器 | CAS 乐观锁 |
| CompletableFuture | 异步编排（扩展） | 并行查询、异常处理 |
| Lambda + 方法引用 | 排序、比较器 | 函数式接口、简洁性 |

---

## 四、扩展方向

1. **持久化**：添加 `ObjectOutputStream` 序列化到文件
2. **REST API**：用 `com.sun.net.httpserver.HttpServer` 内置 HTTP 服务器
3. **全文搜索**：用 `HashMap<String, Set<Long>>` 构建倒排索引
4. **缓存**：用 `LinkedHashMap` 实现 LRU 缓存
5. **排行榜**：用 `TreeSet` 或 `PriorityQueue` 实现文章热度排序

---

## 五、项目结构

```
mini-blog/
├── README.md                    ← 本文档
├── src/
│   ├── model/
│   │   ├── User.java
│   │   ├── Article.java
│   │   └── Comment.java
│   ├── store/
│   │   └── InMemoryStore.java
│   ├── service/
│   │   └── BlogService.java
│   └── server/
│       └── BlogServer.java
└── pom.xml  (可选，如果想用 Maven 管理)
```

> 进入面试冲刺篇：速记版、深挖题、场景题、代码题。