# MiniBlog — Spring Boot 博客 API 项目

> 等级：🎯 独立小项目
> 目标：使用 Spring Boot 3 + JPA + 分页 + 异常处理 + 单元测试，实现一个完整的博客 API。
> 这是一个微型但完整的 RESTful 服务，覆盖 Spring Boot 核心知识点。

---

## 一、项目概述

### 功能需求

1. 用户注册、登录（Token 认证）
2. 发布文章、查看文章列表（分页）
3. 按标签搜索文章
4. 文章评论
5. 全局异常处理
6. 单元测试 + 集成测试

### 技术栈

| 组件 | 用途 |
|------|------|
| Spring Boot 3.3 | 基础框架 |
| Spring Data JPA | 数据持久化 |
| H2 Database | 内存数据库（开发/测试） |
| Spring Security + JWT | 认证授权 |
| Spring Boot Test + MockMvc | 测试 |
| Lombok | 代码简化 |

---

## 二、项目结构

```
mini-blog/
├── pom.xml
├── src/main/java/com/miniblog/
│   ├── MiniBlogApplication.java              ← 启动类
│   ├── config/
│   │   ├── SecurityConfig.java               ← 安全配置
│   │   └── JwtTokenProvider.java             ← JWT 工具
│   ├── controller/
│   │   ├── AuthController.java               ← 登录注册
│   │   ├── ArticleController.java            ← 文章 CRUD
│   │   └── CommentController.java            ← 评论 CRUD
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── CreateArticleRequest.java
│   │   │   └── CreateCommentRequest.java
│   │   └── response/
│   │       ├── AuthResponse.java
│   │       ├── ArticleResponse.java
│   │       └── PagedResponse.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Article.java
│   │   └── Comment.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── BusinessException.java
│   │   └── ErrorCode.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── ArticleRepository.java
│   │   └── CommentRepository.java
│   └── service/
│       ├── AuthService.java
│       ├── ArticleService.java
│       └── CommentService.java
├── src/main/resources/
│   └── application.yml
└── src/test/java/com/miniblog/
    ├── controller/
    │   └── ArticleControllerTest.java
    └── service/
        └── ArticleServiceTest.java
```

---

## 三、核心代码

### 3.1 数据实体

```java
// User.java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;  // BCrypt 加密存储

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

// Article.java
@Entity
@Table(name = "articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ElementCollection
    @CollectionTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();
}

// Comment.java
@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### 3.2 Repository 层

```java
// ArticleRepository.java
public interface ArticleRepository extends JpaRepository<Article, Long> {

    // 分页查询所有文章（按时间倒序）
    Page<Article> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 按标签查询文章
    @Query("SELECT a FROM Article a JOIN a.tags t WHERE t = :tag ORDER BY a.createdAt DESC")
    Page<Article> findByTag(@Param("tag") String tag, Pageable pageable);

    // 搜索标题或内容
    @Query("SELECT a FROM Article a WHERE a.title LIKE %:keyword% OR a.content LIKE %:keyword%")
    Page<Article> search(@Param("keyword") String keyword, Pageable pageable);
}

// CommentRepository.java
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByArticleIdOrderByCreatedAtDesc(Long articleId, Pageable pageable);
}
```

### 3.3 Service 层

```java
// ArticleService.java
@Service
@Transactional
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    public ArticleResponse createArticle(CreateArticleRequest request, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Article article = new Article();
        article.setTitle(request.getTitle());
        article.setContent(request.getContent());
        article.setAuthor(user);
        article.setTags(request.getTags() != null ? request.getTags() : Set.of());

        Article saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }

    public PagedResponse<ArticleResponse> getArticles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Article> articlePage = articleRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<ArticleResponse> articles = articlePage.getContent().stream()
            .map(ArticleResponse::from)
            .toList();

        return new PagedResponse<>(
            articles,
            articlePage.getNumber(),
            articlePage.getTotalPages(),
            articlePage.getTotalElements()
        );
    }

    public ArticleResponse getArticle(Long id) {
        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        return ArticleResponse.from(article);
    }

    public PagedResponse<ArticleResponse> searchArticles(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Article> articlePage = articleRepository.search(keyword, pageable);
        // ... 响应用与上面类似
    }
}
```

### 3.4 Controller 层

```java
// ArticleController.java
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping
    public ResponseEntity<ArticleResponse> createArticle(
            @Valid @RequestBody CreateArticleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ArticleResponse article = articleService.createArticle(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(article);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ArticleResponse>> getArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(articleService.getArticles(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleResponse> getArticle(@PathVariable Long id) {
        return ResponseEntity.ok(articleService.getArticle(id));
    }

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<ArticleResponse>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(articleService.searchArticles(keyword, page, size));
    }
}
```

### 3.5 全局异常处理

```java
// GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity
            .status(e.getErrorCode().getHttpStatus())
            .body(new ErrorResponse(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("SYS_ERROR", "服务器内部错误"));
    }
}
```

### 3.6 分页响应 DTO

```java
// PagedResponse.java
@Data
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int totalPages;
    private long totalElements;
}
```

---

## 四、测试

### 4.1 Service 层单元测试

```java
// ArticleServiceTest.java
@SpringBootTest
@ActiveProfiles("test")
class ArticleServiceTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
        userRepository.deleteAll();
        testUser = userRepository.save(new User(null, "testuser", "pass", "test@test.com", LocalDateTime.now()));
    }

    @Test
    void shouldCreateArticle() {
        // 给定
        CreateArticleRequest request = new CreateArticleRequest();
        request.setTitle("Spring Boot 测试文章");
        request.setContent("这是测试内容");
        request.setTags(Set.of("Spring", "Java"));

        // 当
        ArticleResponse response = articleService.createArticle(request, testUser.getId());

        // 则
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Spring Boot 测试文章");
        assertThat(response.getTags()).contains("Spring", "Java");
    }

    @Test
    void shouldGetPagedArticles() {
        // 插入 5 篇文章
        for (int i = 0; i < 5; i++) {
            Article article = new Article();
            article.setTitle("文章 " + i);
            article.setContent("内容 " + i);
            article.setAuthor(testUser);
            articleRepository.save(article);
        }

        // 分页查询
        PagedResponse<ArticleResponse> result = articleService.getArticles(0, 3);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
    }
}
```

### 4.2 Controller 层测试

```java
// ArticleControllerTest.java
@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    @Test
    void shouldReturnArticles() throws Exception {
        // Mock 数据
        PagedResponse<ArticleResponse> pagedResponse = new PagedResponse<>(
            List.of(new ArticleResponse(1L, "测试文章", "内容", Set.of("Java"), "testuser", LocalDateTime.now(), null)),
            0, 1, 1
        );
        given(articleService.getArticles(0, 10)).willReturn(pagedResponse);

        // 执行请求
        mockMvc.perform(get("/api/articles")
                .param("page", "0")
                .param("size", "10")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturn404WhenArticleNotFound() throws Exception {
        given(articleService.getArticle(999L))
            .willThrow(new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        mockMvc.perform(get("/api/articles/999"))
            .andExpect(status().isNotFound());
    }
}
```

---

## 五、扩展方向

| 扩展 | 技术方案 | 涉及知识点 |
|------|---------|-----------|
| 持久化 | MySQL + Flyway 迁移 | 生产数据库、版本化迁移 |
| 缓存 | Redis 缓存热门文章 | Spring Cache + @Cacheable |
| 全文搜索 | Elasticsearch 集成 | Spring Data Elasticsearch |
| 消息通知 | RocketMQ 异步通知 | 事件驱动 + 消息队列 |
| 容器化 | Docker Compose 部署 | 多服务编排 |
| API 文档 | SpringDoc OpenAPI | Swagger UI 自动生成文档 |

---

## 六、项目涉及的知识点总结

| 知识点 | 应用位置 | 面试要点 |
|--------|----------|---------|
| @SpringBootApplication | 启动类 | 组合注解、自动配置入口 |
| JPA Entity 映射 | entity 包 | @Entity, @ManyToOne, @ElementCollection |
| JPA Repository | repository 包 | 方法命名查询、@Query 自定义查询 |
| 分页 | ArticleService | Pageable / Page / PagedResponse |
| 全局异常处理 | GlobalExceptionHandler | @RestControllerAdvice, 统一响应格式 |
| Bean Validation | Controller 入参 | @Valid, @NotBlank, @Size |
| @WebMvcTest | ControllerTest | 切片测试、MockMvc |
| @SpringBootTest | ServiceTest | 集成测试、事务回滚 |

> 进入面试冲刺篇：速记版、深挖题、场景题、代码题。