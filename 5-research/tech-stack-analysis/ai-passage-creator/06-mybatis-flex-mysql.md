# 06 · MyBatis-Flex + MySQL：ORM 加速与 APT 编译期代码生成

> MyBatis-Flex 是项目的数据访问层底座，基于 MyBatis 提供"零 SQL CRUD"能力，通过 APT 在编译期生成静态元数据，运行时无需 SQL 解析、无需反射，性能优于传统 MyBatis 增强框架。配合 QueryWrapper 实现多表联查，满足复杂查询需求。
>
> **对应项目模块：** `ai-passage-creator-server` 数据访问层

---

## 一、你必须知道的 3 个核心概念

### 1.1 MyBatis-Flex 自动映射 + APT 编译期代码生成

MyBatis-Flex 是 MyBatis 的增强框架，**只做增强不做改变**，与 MyBatis-Plus 定位相似但架构不同。核心机制是**自动映射 + APT 编译期代码生成**：

| 机制 | 说明 |
|------|------|
| **BaseMapper** | 继承 `BaseMapper<T>` 后自动获得 `insert`、`deleteById`、`updateById`、`selectById` 等 20+ 方法，无需写 XML |
| **APT 编译期生成** | 编译期扫描 `@Table` 和 `@Column` 注解，生成静态元数据类（如 `ACCOUNT` 表字段常量），运行时直接引用，无需反射 |
| **QueryWrapper** | 链式调用条件构造器，支持多表关联、子查询、函数调用，类型安全 |
| **无 SQL 解析** | 架构上避免 MyBatis 拦截器和 SQL 解析，性能更高，无运行时开销 |
| **分页查询** | 内置 `paginate()` 方法，无需额外分页插件 |
| **逻辑删除** | `@Column(isLogicDelete = true)` 注解标记删除字段 |
| **乐观锁** | `@Column(version = true)` 注解标记版本字段 |

**核心思想：** 简单 CRUD 用 BaseMapper 内置方法零 SQL，复杂查询用 QueryWrapper 链式调用或手写 XML。APT 在编译期生成字段常量，IDE 自动补全效果好，字段引用错误在编译期即可发现。

### 1.2 APT 编译期代码生成

APT（Annotation Processing Tool）是 Java 编译期注解处理工具，在 `javac` 编译阶段扫描注解并生成源代码。MyBatis-Flex 使用 APT 生成每个表对应的字段常量类：

```
编译前（源代码）：
    @Table("tb_account")
    public class Account {
        @Column("id") private Long id;
        @Column("user_name") private String userName;
    }

编译期（APT 处理）：
    → 扫描 @Table 和 @Column 注解
    → 生成静态元数据类 `Tables.ACCOUNT`
    → 其中 ACCOUNT.ID、ACCOUNT.USER_NAME 等字段常量

编译后（运行时）：
    QueryWrapper.create()
        .select(ACCOUNT.ID, ACCOUNT.USER_NAME)  // 直接引用常量，无反射
        .from(ACCOUNT)
        .where(ACCOUNT.ID.ge(100));
```

| 对比 | APT 编译期生成 | 运行时代理（MyBatis-Plus） |
|------|-------------|------------------------|
| 生成时机 | 编译期 | 运行时 |
| 性能开销 | 零运行时开销 | 有 SQL 解析开销 |
| 错误发现 | 编译期 | 运行时 |
| IDE 支持 | 编译后自动补全 | 运行时生成 |
| 依赖 | 仅 MyBatis 本身 | 较多第三方依赖 |

### 1.3 多表关联查询

MyBatis-Flex 的 QueryWrapper 原生支持多表关联，无需手写 XML，通过链式 API 构建 JOIN 查询：

```java
// 多表联查：Account LEFT JOIN Article
QueryWrapper query = QueryWrapper.create()
    .select()
    .from(ACCOUNT)
    .leftJoin(ARTICLE).on(ACCOUNT.ID.eq(ARTICLE.ACCOUNT_ID))
    .where(ACCOUNT.AGE.ge(10))
    .orderBy(ACCOUNT.ID.desc())
    .limit(10)
    .offset(0);

// 执行查询返回关联结果
List<Account> accounts = accountMapper.selectListByQuery(query);
```

---

## 二、项目中的实战应用

### 2.1 解决了什么问题

| 痛点 | 解决方案 |
|------|----------|
| 简单 CRUD 也要写大量重复的 XML 和 SQL | BaseMapper 提供通用方法，零 XML 完成增删改查 |
| 条件查询拼接 SQL 容易出错 | QueryWrapper 链式调用，编译期类型安全 |
| 多表关联查询需要维护复杂 XML | QueryWrapper 原生支持 LEFT JOIN、子查询等 |
| 运行时 SQL 解析影响性能 | APT 编译期生成元数据，零运行时开销 |
| 字段名变更后运行时才报错 | APT 生成常量，编译期发现字段引用错误 |

### 2.2 核心代码实现（带逐行中文注释）

#### 2.2.1 数据源与 MyBatis-Flex 配置

```yaml
# application.yml —— MyBatis-Flex 数据源配置
# 使用 kebab-case 风格（连字符命名）
spring:
  datasource:
    # MySQL 8.0 数据源配置
    url: jdbc:mysql://localhost:3306/ai_passage_creator?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: root123

# MyBatis-Flex 配置
mybatis-flex:
  # 实体类扫描路径
  type-aliases-package: com.example.passage.entity
  # Mapper XML 文件路径（手写复杂 SQL 时使用）
  mapper-locations: classpath*:mapper/**/*.xml
  # 全局配置
  global-config:
    # 逻辑删除属性名
    logic-delete-field: deleted
    # 逻辑删除值（默认 1 表示已删除）
    logic-delete-value: 1
    # 逻辑未删除值（默认 0 表示未删除）
    logic-not-delete-value: 0
```

#### 2.2.2 APT 生成器配置（pom.xml）

```xml
<!-- pom.xml —— MyBatis-Flex APT 代码生成器配置 -->
<!-- APT 在编译期自动生成 Table 字段常量类 -->
<build>
    <plugins>
        <!-- APT 处理器：编译期生成静态元数据 -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <!-- MyBatis-Flex APT 处理器 -->
                    <path>
                        <groupId>com.mybatis-flex</groupId>
                        <artifactId>mybatis-flex-annotation-processor</artifactId>
                        <version>1.11.1</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
        <!-- 代码生成器插件（可选）：根据数据库表生成 Entity 代码 -->
        <plugin>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-codegen</artifactId>
            <version>1.11.1</version>
        </plugin>
    </plugins>
</build>
```

#### 2.2.3 实体类与 APT 注解

```java
/**
 * 文章实体类 —— 演示 MyBatis-Flex 的 @Table 和 @Column 注解
 * 
 * @Table：指定数据库表名（默认驼峰转下划线）
 * @Column：标注字段映射，APT 编译期据此生成静态常量
 * 编译后，APT 自动生成 Tables.ARTICLE 类，包含所有字段常量
 */
@Table(value = "article")                       // 指定数据库表名
public class Article {

    @Column(value = "id", isPrimaryKey = true)  // 主键字段
    private Long id;

    @Column(value = "title")                     // 文章标题
    private String title;

    @Column(value = "content")                   // 文章正文（Markdown 格式）
    private String content;

    @Column(value = "user_id")                   // 用户 ID（外键关联 user 表）
    private Long userId;

    @Column(value = "status")                    // 文章状态：0-草稿 1-已发布 2-已删除
    private Integer status;

    @Column(value = "phase")                     // 创作阶段：TITLE_SELECTION / OUTLINE_EDITING / CONTENT_GENERATION / COMPLETED
    private String phase;

    @Column(value = "create_time")               // 创建时间
    private LocalDateTime createTime;

    @Column(value = "update_time")               // 更新时间
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)                // 逻辑删除字段：deleteById 自动转为 UPDATE SET deleted = 1
    private Boolean deleted;

    @Column(version = true)                      // 乐观锁版本字段：更新时自动 SET version = version + 1
    private Integer version;
}
```

#### 2.2.4 Mapper 接口 —— BaseMapper 通用 CRUD

```java
/**
 * 文章 Mapper 接口 —— 继承 BaseMapper 获得通用 CRUD 能力
 * 
 * 继承 BaseMapper<Article> 后自动获得以下方法：
 * - insert()：插入记录
 * - deleteById()：根据主键删除
 * - updateById()：根据主键更新
 * - selectById()：根据主键查询
 * - selectList()：条件查询列表
 * - selectOne()：查询单条记录
 * - paginate()：分页查询
 * 
 * 编译期 APT 自动生成 Tables.ARTICLE 字段常量
 * 运行时直接引用，无需 SQL 解析
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 自定义查询：根据用户 ID 查询文章列表
     * 复杂查询仍需手写 SQL，在 XML 中定义
     * 
     * @param userId 用户 ID
     * @return 文章列表
     */
    List<Article> selectByUserId(@Param("userId") Long userId);
}
```

#### 2.2.5 Service 层 —— QueryWrapper 条件查询

```java
/**
 * 文章服务 —— 演示 QueryWrapper 的典型用法
 * 
 * QueryWrapper 是 MyBatis-Flex 的类型安全条件构造器
 * 字段引用使用静态常量（Tables.ARTICLE.TITLE），编译期可校验
 */
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;  // MyBatis-Flex Mapper

    /**
     * 根据 ID 查询文章 —— BaseMapper 内置方法
     * 零 SQL，一行调用
     */
    public Article getById(Long id) {
        // selectById 是 BaseMapper 内置方法，无需写 SQL
        return articleMapper.selectById(id);
    }

    /**
     * 多条件组合查询 —— QueryWrapper 链式调用
     * 
     * 业务场景：根据用户 ID、状态、阶段过滤文章列表
     * 字段常量 Tables.ARTICLE.* 由 APT 编译期生成
     */
    public List<Article> listArticles(Long userId, Integer status, String phase) {
        // 构建查询条件：QueryWrapper 链式调用，类型安全
        QueryWrapper query = QueryWrapper.create()
                // 等值匹配：按用户 ID 过滤
                .where(ARTICLE.USER_ID.eq(userId))
                // 等值匹配：按状态过滤（如果 status 不为 null）
                .and(ARTICLE.STATUS.eq(status))
                // 等值匹配：按创作阶段过滤（如果 phase 不为 null）
                .and(ARTICLE.PHASE.eq(phase))
                // 排序：按创建时间倒序
                .orderBy(ARTICLE.CREATE_TIME.desc());

        // 执行查询：selectListByQuery 返回多条记录
        return articleMapper.selectListByQuery(query);
    }

    /**
     * 分页查询 —— 内置 paginate() 方法
     * 
     * 无需额外分页插件，paginate 自动生成 COUNT + LIMIT 语句
     * 
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param userId   用户 ID（过滤条件）
     * @return 分页结果
     */
    public Page<Article> pageArticles(int pageNum, int pageSize, Long userId) {
        // 构建查询条件
        QueryWrapper query = QueryWrapper.create()
                .where(ARTICLE.USER_ID.eq(userId))
                .orderBy(ARTICLE.CREATE_TIME.desc());

        // 分页查询：paginate 自动处理 COUNT 和 LIMIT
        // 参数：页码、每页条数、查询条件
        return articleMapper.paginate(pageNum, pageSize, query);
    }

    /**
     * 创建文章 —— BaseMapper 内置 insert 方法
     * 
     * 业务场景：用户完成选题阶段后，创建文章记录
     * 初始 phase = TITLE_SELECTION（选题阶段）
     */
    public void createArticle(Article article) {
        // insert 是 BaseMapper 内置方法，自动填充主键
        articleMapper.insert(article);
    }

    /**
     * 更新文章阶段 —— 条件更新，只更新指定字段
     * 
     * 业务场景：当 Agent 流程推进到下一阶段时，更新 phase 字段
     * 乐观锁 version 字段自动 +1，防止并发覆盖
     */
    public boolean updatePhase(Long id, String newPhase) {
        // 构建更新条件：只更新 phase 字段
        Article update = new Article();
        update.setPhase(newPhase);              // 设置新的阶段值

        // 创建更新 QueryWrapper：WHERE id = ?
        QueryWrapper query = QueryWrapper.create()
                .where(ARTICLE.ID.eq(id));

        // 执行条件更新：只更新匹配条件的记录
        // 内部自动处理 version 乐观锁
        return articleMapper.updateByQuery(update, query) > 0;
    }
}
```

#### 2.2.6 多表联查 —— QueryWrapper JOIN 查询

```java
/**
 * 文章统计服务 —— 演示 QueryWrapper 多表联查
 * 
 * 业务场景：统计每个用户的文章数量，需要关联 article 和 user 表
 * QueryWrapper 原生支持 LEFT JOIN、RIGHT JOIN、INNER JOIN
 */
@Service
@RequiredArgsConstructor
public class ArticleStatsService {

    private final ArticleMapper articleMapper;  // 文章 Mapper

    /**
     * 多表联查：文章 LEFT JOIN 用户
     * 
     * 查询每个用户的最新文章列表，包含用户昵称
     * 无需手写 XML，QueryWrapper 链式调用即可
     */
    public List<ArticleWithUser> listArticlesWithUser() {
        // 构建多表联查 QueryWrapper
        QueryWrapper query = QueryWrapper.create()
                // SELECT 指定要查询的字段
                .select(ARTICLE.ID, ARTICLE.TITLE, ARTICLE.STATUS,
                        ARTICLE.CREATE_TIME, USER.NICKNAME, USER.AVATAR)
                // FROM article（主表）
                .from(ARTICLE)
                // LEFT JOIN user ON article.user_id = user.id
                .leftJoin(USER).on(ARTICLE.USER_ID.eq(USER.ID))
                // WHERE 过滤条件：只查询已发布的文章
                .where(ARTICLE.STATUS.eq(1))
                // ORDER BY 排序
                .orderBy(ARTICLE.CREATE_TIME.desc())
                // LIMIT 分页
                .limit(20);

        // 执行查询并映射到 VO 对象
        return articleMapper.selectListByQueryAs(query, ArticleWithUser.class);
    }

    /**
     * 子查询：查询有文章的用户列表
     * 
     * QueryWrapper 支持嵌套子查询，构建复杂查询条件
     */
    public List<User> listUsersWithArticles() {
        QueryWrapper query = QueryWrapper.create()
                .select()
                .from(USER)
                .where(USER.ID.in(
                        // 子查询：查询所有发布过文章的用户 ID
                        QueryWrapper.create()
                                .select(ARTICLE.USER_ID)
                                .from(ARTICLE)
                                .where(ARTICLE.STATUS.eq(1))
                ));

        // 注入 UserMapper 执行查询
        return null; // 省略具体执行
    }
}
```

#### 2.2.7 自定义 XML 复杂查询

```xml
<!-- ArticleMapper.xml —— 手写复杂 SQL（MyBatis 原生能力） -->
<!-- 当 QueryWrapper 无法满足的复杂查询时，仍然使用 XML 手写 SQL -->
<mapper namespace="com.example.passage.mapper.ArticleMapper">

    <!-- 自定义查询：根据用户 ID 查询文章列表（多表关联统计） -->
    <select id="selectByUserId" resultType="com.example.passage.entity.Article">
        SELECT
            a.*,
            u.nickname AS authorName
        FROM article a
        LEFT JOIN user u ON a.user_id = u.id
        WHERE a.user_id = #{userId}
        ORDER BY a.create_time DESC
    </select>
</mapper>
```

### 2.3 设计亮点

**亮点一：APT 编译期生成，零运行时开销**

与 MyBatis-Plus 在运行时通过拦截器 + SQL 解析器动态生成 SQL 不同，MyBatis-Flex 通过 APT 在编译期生成静态元数据（如 `ARTICLE.ID`、`ARTICLE.TITLE` 字段常量）。运行时直接引用这些常量构建查询，无需任何 SQL 解析，**性能更高、启动更快、编译期就能发现字段引用错误**。

**亮点二：QueryWrapper 原生多表关联**

MyBatis-Flex 的 QueryWrapper 原生支持 `LEFT JOIN`、`RIGHT JOIN`、`INNER JOIN`、子查询、函数调用等复杂查询能力，无需像 MyBatis-Plus 那样依赖 `@TableField(exist = false)` 或额外 VO 类。**多表联查的代码可读性和维护性更好。**

**亮点三：零依赖，框架轻量**

MyBatis-Flex 除 MyBatis 本身外无任何第三方依赖，不像 MyBatis-Plus 依赖较多第三方库。**依赖少意味着冲突少、包体积小、更容易排查问题。**

**亮点四：BaseMapper + QueryWrapper + XML 三层渐进**

| 查询复杂度 | 使用方式 | 示例 |
|-----------|---------|------|
| 简单单表 CRUD | BaseMapper 内置方法 | `selectById()`、`insert()` |
| 条件查询 | QueryWrapper 链式调用 | `where().eq().orderBy()` |
| 多表关联 | QueryWrapper JOIN | `leftJoin().on()` |
| 复杂聚合/统计 | 手写 XML | 自定义 `<select>` 标签 |

---

## 三、面试高频题

### Q1: MyBatis-Flex 和 MyBatis-Plus 的区别？项目为什么选 Flex 而不是 Plus？

**参考答案：**

**核心区别一句话：MyBatis-Flex 通过 APT 编译期代码生成实现零运行时 SQL 解析，MyBatis-Plus 通过运行时的拦截器 + SQL 解析器实现动态 SQL。**

| 维度 | MyBatis-Flex | MyBatis-Plus |
|------|-------------|-------------|
| **依赖** | 仅 MyBatis，零第三方依赖 | 较多第三方依赖 |
| **SQL 解析** | 无（编译期 APT 生成字段常量） | 运行时拦截器 + SQL 解析 |
| **性能** | 更高（无解析开销） | 有解析开销 |
| **代码生成** | APT 编译期生成静态元数据 | 运行时代理 |
| **多表查询** | QueryWrapper 原生支持 JOIN | LambdaQueryWrapper 需额外配置 |
| **多数据源** | 内置支持 + 读写分离 | 需引入 dynamic-datasource |
| **逻辑删除** | `@Column(isLogicDelete = true)` | `@TableLogic` |
| **乐观锁** | `@Column(version = true)` | `@Version` |
| **分页** | `paginate()` 内置方法 | `Page` 对象 + 分页插件 |
| **社区生态** | 较新，文档相对较少 | 成熟，文档丰富 |

**项目为什么选 Flex：**

1. **性能优先**：GPT 生成文章的场景中，Agent 需要频繁读写数据库保存中间状态（每生成一段就要保存一次），高频率的数据库操作对 ORM 性能有要求。Flex 零运行时反射和 SQL 解析的开销，在批量操作场景下性能优势明显。
2. **编译期安全**：项目频繁迭代，实体类字段经常变动。Flex 的 APT 编译期校验能在编译阶段就发现字段引用错误，避免"改字段名后运行时才报错"的尴尬。
3. **轻量简洁**：项目不需要 MyBatis-Plus 的复杂功能（如多数据源、乐观锁插件等），Flex 的轻量设计更符合项目需求，依赖少、容易排查问题。
4. **QueryWrapper 多表关联**：项目需要关联查询（文章 + 用户 + 配图记录），Flex 的 QueryWrapper 原生支持 JOIN，代码更简洁。

**追问应对：** "如果项目一开始就用 MyBatis-Plus 呢？" 答：技术上两者都能实现需求，区别在于：Flex 架构更轻、性能更好，但社区文档不如 Plus 丰富；Plus 功能更全、文档更完善，但依赖更多、启动更慢。项目选型可以理解为"用性能换简洁"——对于 AI Agent 这种高频读写场景，Flex 更合适。

### Q2: MyBatis-Flex 的 APT 编译期代码生成原理是什么？有什么优缺点？

**参考答案：**

**APT 原理：**

APT（Annotation Processing Tool）是 Java 编译期注解处理工具，基于 JSR 269 规范定义。MyBatis-Flex 的 APT 处理器在 `javac` 编译阶段工作：

```
1. javac 编译源代码
    ↓
2. APT 处理器扫描 @Table 和 @Column 注解
    ↓
3. 为每个标注了 @Table 的实体类生成对应的 Tables 静态常量类
    ↓
4. 编译生成的源代码（与业务代码一起编译）
    ↓
5. 运行时直接引用生成的常量，无需反射
```

**生成的静态元数据类示例（由 APT 自动生成，无需手写）：**

```java
/**
 * 由 MyBatis-Flex APT 编译期自动生成的静态元数据类
 * 开发人员无需手写，编译后自动存在于 target/generated-sources/ 目录下
 * 
 * 每个字段对应一个 QueryColumn 对象：
 * - QueryColumn 包含字段名、表名、字段类型等信息
 * - 运行时构建 QueryWrapper 时直接引用，无需反射
 */
public class Tables {
    // ARTICLE 表的所有字段常量
    public static final QueryColumn ID = QueryColumn.of("id", "article");
    public static final QueryColumn TITLE = QueryColumn.of("title", "article");
    public static final QueryColumn CONTENT = QueryColumn.of("content", "article");
    public static final QueryColumn USER_ID = QueryColumn.of("user_id", "article");
    public static final QueryColumn STATUS = QueryColumn.of("status", "article");
    public static final QueryColumn PHASE = QueryColumn.of("phase", "article");
    public static final QueryColumn CREATE_TIME = QueryColumn.of("create_time", "article");
    public static final QueryColumn UPDATE_TIME = QueryColumn.of("update_time", "article");
}
```

**优点：**

| 优点 | 说明 |
|------|------|
| **零运行时反射** | 所有字段引用在编译期就确定了，运行时直接使用常量，无反射性能开销 |
| **编译期类型安全** | 字段名拼写错误在编译期就能发现，IDE 自动补全效果好 |
| **启动快** | 无需在启动时扫描和解析实体类，应用启动时间更短 |
| **无 SQL 解析** | 查询条件直接操作 QueryColumn 对象，无需解析字符串表达式 |

**缺点：**

| 缺点 | 说明 |
|------|------|
| **编译步骤增加** | 每次修改实体类后需要重新编译才能更新字段常量，增量编译时可能漏生成 |
| **生成的代码不可见（IDE 中默认隐藏）** | 新手可能不理解字段常量从哪来的，看到 `ARTICLE.ID` 会困惑 |
| **APT 处理器兼容性** | 某些定制化编译环境可能不支持 APT 处理器 |
| **调试困难** | 生成的代码在 target 目录，调试时不容易跟踪 |

**追问应对：** "APT 处理器的编译流程是怎样的？" 答：`javac` 编译分为三步：解析与填充符号表 → 注解处理（可多次轮次） → 分析与生成字节码。APT 处理器在第二步执行，可以读取、创建、修改源代码。MyBatis-Flex 的 APT 处理器在第一个处理轮次扫描 `@Table` 注解，生成 Tables 静态常量类，然后在后续轮次中编译生成的代码。

### Q3: MyBatis-Flex 的 QueryWrapper 如何实现多表关联查询？与 MyBatis-Plus 的 LambdaQueryWrapper 有何不同？

**参考答案：**

**QueryWrapper 多表关联实现：**

MyBatis-Flex 的 QueryWrapper 原生支持多表关联，通过链式 API 构建 JOIN 查询：

```java
// MyBatis-Flex QueryWrapper 多表联查
QueryWrapper query = QueryWrapper.create()
    .select(ARTICLE.ID, ARTICLE.TITLE, USER.NICKNAME)  // 指定查询字段
    .from(ARTICLE)                                       // 主表
    .leftJoin(USER).on(ARTICLE.USER_ID.eq(USER.ID))      // LEFT JOIN
    .where(ARTICLE.STATUS.eq(1))                          // WHERE 条件
    .orderBy(ARTICLE.CREATE_TIME.desc())                  // 排序
    .limit(10);                                           // 限制条数
```

**与 MyBatis-Plus LambdaQueryWrapper 的核心区别：**

| 维度 | MyBatis-Flex QueryWrapper | MyBatis-Plus LambdaQueryWrapper |
|------|--------------------------|-------------------------------|
| **JOIN 支持** | 原生支持（`leftJoin().on()`） | 不支持，需手写 XML 或 `@Select` 注解 |
| **字段引用** | 静态常量（`ARTICLE.ID`） | Lambda 表达式（`Article::getId`） |
| **生成方式** | APT 编译期生成 | 运行时字节码代理 |
| **多表字段** | 不同表用不同前缀（`ARTICLE.TITLE`、`USER.NICKNAME`） | 单表时类型安全，多表时需额外处理 |
| **子查询** | 原生支持（`in(QueryWrapper.create()...)`） | 需手写 SQL 字符串 |
| **函数调用** | 原生支持（`max()`、`count()`、`dateFormat()`） | 需手写 SQL 片段 |

**MyBatis-Plus 多表查询的替代方案：**

```java
// MyBatis-Plus 多表查询需要手写 XML 或使用 @Select 注解
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    // 方案一：手写 XML
    List<ArticleWithUser> selectArticleWithUser(@Param("status") Integer status);

    // 方案二：@Select 注解
    @Select("SELECT a.*, u.nickname FROM article a LEFT JOIN user u ON a.user_id = u.id WHERE a.status = #{status}")
    List<ArticleWithUser> selectArticleWithUser2(@Param("status") Integer status);
}
```

**总结：** MyBatis-Flex 的 QueryWrapper 多表查询能力更强，代码更简洁，适合需要频繁多表关联的项目。MyBatis-Plus 的 LambdaQueryWrapper 在单表查询时体验更好（Lambda 表达式更直观），但多表查询需要回退到 XML。

---

## 四、参考资料与扩展阅读

### 项目源码
- [ai-passage-creator-demo GitHub 仓库](https://github.com/1byteone/ai-passage-creator-demo) — 数据访问层模块

### MyBatis-Flex 官方
- [MyBatis-Flex 官方文档](https://mybatis-flex.com/) — APT 代码生成、QueryWrapper、BaseMapper 完整使用指南
- [MyBatis-Flex GitHub 仓库](https://github.com/mybatis-flex/mybatis-flex) — 源码与 Issue

### APT 相关
- [JSR 269: Pluggable Annotation Processing API](https://jcp.org/en/jsr/detail?id=269) — Java 编译期注解处理规范
- [Java Annotation Processing 入门](https://www.baeldung.com/java-annotation-processing-builder) — APT 处理器开发教程

### MySQL 相关
- [MySQL 8.0 官方文档](https://dev.mysql.com/doc/refman/8.0/en/) — 索引优化、SQL 调优
- [MySQL JOIN 优化](https://dev.mysql.com/doc/refman/8.0/en/join.html) — 多表关联查询性能优化