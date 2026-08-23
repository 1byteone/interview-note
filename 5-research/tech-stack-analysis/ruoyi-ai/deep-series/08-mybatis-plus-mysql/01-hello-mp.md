# MyBatis-Plus入门：从JDBC到自动映射的进化

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第8篇，面向 Java 后端开发者，旨在帮助读者理解 MyBatis-Plus 如何从原始的 JDBC 操作一步步进化到今天的"自动映射 + 零 SQL CRUD"，从零搭建一个完整的 Spring Boot + MyBatis-Plus 项目，实现最简增删改查、分页查询和条件构造器的使用。

---

## 一、项目背景：为什么需要 MyBatis-Plus？

### 1.1 JDBC 时代的痛点

在 MyBatis 出现之前，Java 操作数据库的标准方式是 JDBC（Java Database Connectivity）。让我们先回顾一下 JDBC 的原始写法，这样才能理解为什么需要 MyBatis，以及为什么 MyBatis-Plus 能更进一步。

```java
/**
 * JDBC 原生查询示例 —— 演示最原始的数据库操作方式。
 *
 * 这段代码只是为了展示 JDBC 的繁琐之处，
 * 现实项目中没有人会这样写，但理解它才能理解框架的价值。
 * 代码量：约 50 行，实际作用：查询一条用户记录。
 */
public class JdbcDemo {

    /**
     * 根据 ID 查询用户 —— JDBC 原生方式。
     *
     * 需要手动处理：加载驱动、获取连接、创建语句、执行查询、
     * 解析结果集、处理异常、关闭资源 —— 每一步都不能少，
     * 但每一步都是重复的样板代码。
     */
    public User findUserById(Long id) {
        // 数据库连接对象：需要手动创建和关闭
        Connection connection = null;
        // 预编译语句对象：防止 SQL 注入
        PreparedStatement statement = null;
        // 结果集对象：存储查询结果
        ResultSet resultSet = null;
        // 返回的用户对象
        User user = null;

        try {
            // ==== 第一步：加载数据库驱动 ====
            // Class.forName 反射加载驱动类，MySQL 8 以上使用 com.mysql.cj.jdbc.Driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // ==== 第二步：获取数据库连接 ====
            // 需要提供 URL、用户名、密码 —— 硬编码在代码中，无法外部配置
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/ruoyi_ai",  // 数据库 URL
                    "root",                                   // 用户名
                    "root123"                                 // 密码
            );

            // ==== 第三步：创建预编译 SQL 语句 ====
            // SQL 语句以字符串形式写在代码中，没有编译期检查
            // 字段名写错只有运行时才能发现
            String sql = "SELECT id, username, nickname, email, status, create_time FROM sys_user WHERE id = ?";
            statement = connection.prepareStatement(sql);
            // 设置参数：索引从 1 开始，类型需要手动匹配
            statement.setLong(1, id);

            // ==== 第四步：执行查询，获取结果集 ====
            resultSet = statement.executeQuery();

            // ==== 第五步：手动解析结果集 ====
            // 每一列都需要手动 getXxx 并 set 到实体对象中
            // 如果表有 20 个字段，这里就要写 20 行 get/set 代码
            if (resultSet.next()) {
                user = new User();
                // 按列名获取值，列名必须与数据库字段名完全一致
                user.setId(resultSet.getLong("id"));
                user.setUsername(resultSet.getString("username"));
                user.setNickname(resultSet.getString("nickname"));
                user.setEmail(resultSet.getString("email"));
                user.setStatus(resultSet.getInt("status"));
                // 日期类型需要特殊处理：getTimestamp 获取时间戳
                user.setCreateTime(resultSet.getTimestamp("create_time").toLocalDateTime());
            }

        } catch (ClassNotFoundException e) {
            // 驱动类找不到异常 —— 通常是依赖没加或版本不对
            e.printStackTrace();
        } catch (SQLException e) {
            // SQL 执行异常 —— 可能是 SQL 语法错误或数据库连接失败
            e.printStackTrace();
        } finally {
            // ==== 第六步：手动关闭资源 ====
            // 关闭顺序与创建顺序相反：ResultSet -> Statement -> Connection
            // 每个 close 都要 try-catch，代码极其冗长
            try {
                if (resultSet != null) {
                    resultSet.close();  // 关闭结果集
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                if (statement != null) {
                    statement.close();  // 关闭语句
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                if (connection != null) {
                    connection.close();  // 关闭连接
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return user;
    }
}
```

**JDBC 的痛点总结：**

| 痛点 | 具体表现 | 影响 |
|------|---------|------|
| **样板代码多** | 一个简单查询需要 50+ 行代码，其中 70% 是资源获取和释放 | 开发效率低，代码可读性差 |
| **资源管理繁琐** | 手动获取/关闭 Connection、Statement、ResultSet，容易遗漏 | 连接泄漏导致数据库连接池耗尽 |
| **SQL 与代码耦合** | SQL 语句以字符串形式写在 Java 代码中，没有语法高亮和编译期检查 | 维护困难，修改 SQL 需要重新编译 |
| **结果集手动映射** | 每一条查询都需要手动将 ResultSet 的列映射到 Java 对象的属性 | 重复劳动，字段多时容易出错 |
| **异常处理冗长** | 每个数据库操作都需要 try-catch-finally，异常处理代码超过业务代码 | 代码臃肿，真正业务逻辑不易阅读 |
| **无连接池管理** | 每次请求都创建新的数据库连接，频繁创建和销毁连接 | 性能极差，高并发下直接崩溃 |

### 1.2 MyBatis 的改进

MyBatis 的出现解决了 JDBC 的大部分痛点。它是一个**半自动 ORM（Object Relational Mapping）**框架，核心思想是"SQL 与代码分离，结果自动映射"。

```java
/**
 * MyBatis Mapper 接口 —— 对比 JDBC 的改进。
 *
 * 改进点：
 * 1. 不需要写任何 JDBC 样板代码
 * 2. SQL 写在 XML 文件中，与 Java 代码分离
 * 3. 结果集自动映射到 Java 对象
 * 4. 参数自动绑定，无需手动 setXxx
 * 5. 异常统一处理，无需每个方法都 try-catch
 */
@Mapper
public interface UserMapper {

    /**
     * 根据 ID 查询用户。
     *
     * MyBatis 通过 XML 或注解中的 SQL 语句自动完成：
     * - 加载驱动、获取连接由 MyBatis 管理
     * - 参数自动绑定到 SQL 中的 ? 占位符
     * - 结果集自动映射到 User 对象
     * - 资源自动关闭，无需手动释放
     * - 异常统一由 MyBatis 处理
     *
     * 对应的 XML 文件内容：
     * <select id="findById" resultType="com.example.entity.User">
     *     SELECT id, username, nickname, email, status, create_time
     *     FROM sys_user WHERE id = #{id}
     * </select>
     */
    User findById(@Param("id") Long id);
}
```

**MyBatis 相比 JDBC 的改进：**

1. **SQL 与代码分离**：SQL 写在 XML 文件中，修改 SQL 无需重新编译 Java 代码
2. **自动结果映射**：MyBatis 自动将 ResultSet 的列映射到 Java 对象的属性，无需手动 get/set
3. **参数自动绑定**：`#{id}` 自动将参数绑定到 SQL 语句的占位符，防止 SQL 注入
4. **连接池管理**：MyBatis 集成连接池（如 HikariCP），复用数据库连接，提升性能
5. **统一异常处理**：MyBatis 将 SQLException 转换为统一的 PersistenceException
6. **动态 SQL**：通过 `<if>`、`<where>`、`<foreach>` 等标签实现动态条件拼接

**但是，MyBatis 仍然有痛点：**

| 痛点 | 具体表现 |
|------|---------|
| **简单 CRUD 仍要写 XML** | 即使只是一个简单的 `insert` 或 `selectById`，也需要写 XML 映射文件 |
| **条件查询繁琐** | 每次组合查询条件都要写动态 SQL，<if> 标签满天飞 |
| **分页需要手动处理** | 分页查询需要手动写 `LIMIT #{offset}, #{size}` 和 `COUNT` 查询 |
| **无内置代码生成** | 需要借助 MyBatis Generator 或其他第三方工具生成代码 |

### 1.3 MyBatis-Plus 的进一步简化

MyBatis-Plus（简称 MP）是 MyBatis 的增强工具，**只做增强不做改变**。它不是在 MyBatis 之外另起炉灶，而是在 MyBatis 的基础上提供了一套"懒人包"——把简单 CRUD 的体力活全部自动化，同时保留 MyBatis 的复杂查询能力。

**MyBatis-Plus 的核心价值一句话：**

> 简单 CRUD 零 SQL，复杂查询不限制——"简单不写，复杂不拦"。

具体来说，MyBatis-Plus 解决了 MyBatis 的以下痛点：

1. **BaseMapper 通用 CRUD**：继承 `BaseMapper<T>` 后自动获得 insert、deleteById、updateById、selectById 等 20+ 方法，**零 XML 零 SQL 完成增删改查**
2. **LambdaQueryWrapper 类型安全条件构造**：使用 Lambda 表达式引用实体字段，编译期即可发现字段名拼写错误，**告别手写 WHERE 条件拼接**
3. **分页插件自动拦截**：`PaginationInnerInterceptor` 自动拦截分页查询，**无需手动写 LIMIT 和 COUNT**
4. **自动代码生成器**：AutoGenerator 根据数据库表结构一键生成 Entity、Mapper、Service、Controller 全套代码
5. **乐观锁插件**：`@Version` 注解标记版本字段，更新时自动管理版本号
6. **逻辑删除**：`@TableLogic` 注解标记删除字段，deleteById 自动转为 UPDATE 语句

### 1.4 在 ruoyi-ai 项目中的角色

在 ruoyi-ai 项目中，MyBatis-Plus 是数据访问层的底座，承载着所有业务数据的持久化操作：

- **用户模块**：用户注册、登录、权限管理的数据持久化
- **AI 对话模块**：会话记录、对话消息的存储和查询
- **知识库模块**：知识库文档的增删改查和分页检索
- **模型配置模块**：多模型配置的持久化存储
- **系统管理模块**：菜单、角色、部门等基础数据的管理

ruoyi-ai 项目中的 MyBatis-Plus 配置位于 `ruoyi-common/mybatis` 模块，它是一个公共 starter，所有业务模块通过引入此 starter 获得 MyBatis-Plus 的全部能力。

---

## 二、核心概念：用生活类比理解 MyBatis-Plus

### 概念 1：BaseMapper —— 就像"通用遥控器"

**生活类比**：你买了一台新电视，遥控器上有"音量+、音量-、频道+、频道-、开关机"等通用按钮。不管是什么品牌的电视，这些通用按钮都能用。你不需要每次调音量都去翻说明书，也不需要知道遥控器内部是怎么发射红外信号的。

**技术映射**：`BaseMapper<T>` 就是 MyBatis-Plus 提供的"通用遥控器"——它内置了 20+ 个通用方法，覆盖了大部分 CRUD 操作：

```java
/**
 * BaseMapper 继承结构 —— 通用遥控器的类比。
 *
 * 就像遥控器上的通用按钮一样，BaseMapper 提供了：
 * - 插入：insert（音量+）
 * - 删除：deleteById（关机）
 * - 修改：updateById（频道+）
 * - 查询：selectById（频道-）
 * - 分页：selectPage（菜单键）
 *
 * 你的 Mapper 接口只需要继承 BaseMapper，就自动获得了这些能力。
 * 不需要写任何 XML 或 SQL 语句。
 */
// 你的 Mapper —— 继承 BaseMapper 后，自动获得 20+ 个通用方法
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 这个接口是空的！但已经拥有了 insert、updateById、selectById 等方法
    // 你只需要在这里添加自定义查询方法
}
```

**BaseMapper 提供的核心方法分类：**

| 方法类型 | 方法名 | 作用 |
|---------|--------|------|
| 插入 | `insert(T entity)` | 插入一条记录 |
| 删除 | `deleteById(Serializable id)` | 根据主键删除 |
| 删除 | `deleteByMap(Map<String, Object>)` | 根据列名 Map 条件删除 |
| 删除 | `delete(Wrapper<T> wrapper)` | 根据条件构造器删除 |
| 删除 | `deleteBatchIds(Collection<?>)` | 批量删除（根据 ID 集合） |
| 修改 | `updateById(T entity)` | 根据主键更新 |
| 修改 | `update(T entity, Wrapper<T> wrapper)` | 根据条件构造器更新 |
| 查询 | `selectById(Serializable id)` | 根据主键查询 |
| 查询 | `selectBatchIds(Collection<?>)` | 批量查询（根据 ID 集合） |
| 查询 | `selectByMap(Map<String, Object>)` | 根据列名 Map 条件查询 |
| 查询 | `selectOne(Wrapper<T> wrapper)` | 查询一条记录 |
| 查询 | `selectCount(Wrapper<T> wrapper)` | 查询总记录数 |
| 查询 | `selectList(Wrapper<T> wrapper)` | 查询列表 |
| 查询 | `selectMaps(Wrapper<T> wrapper)` | 查询列表，返回 Map 列表 |
| 分页 | `selectPage(Page<T> page, Wrapper<T> wrapper)` | 分页查询 |

**关键点**：BaseMapper 的泛型参数 `T` 就是你的实体类，MyBatis-Plus 通过反射获取实体类的表名、字段名、主键等信息，自动生成对应的 SQL 语句。这就是"自动映射"的核心机制。

### 概念 2：@TableName 和 @TableId —— 就像"姓名标签"

**生活类比**：你参加一个大型会议，胸前贴了一张姓名标签，上面写着"张三，销售部"。主办方看到标签就能知道你是谁、属于哪个部门。如果没有标签，主办方需要问"你叫什么名字？你在哪个部门？"——每次都要解释一遍。

**技术映射**：`@TableName` 和 `@TableId` 就是实体类的"姓名标签"，告诉 MyBatis-Plus 这个 Java 类对应哪个数据库表、哪个字段是主键：

```java
/**
 * @TableName 注解 —— 指定实体类对应的数据库表名。
 *
 * 默认规则：驼峰转下划线，如 UserInfo 类默认对应 user_info 表。
 * 如果表名不符合默认规则，使用 @TableName 显式指定。
 */
@TableName("sys_user")  // 指定此实体类对应 sys_user 表
public class User {

    /**
     * @TableId 注解 —— 指定主键字段和主键生成策略。
     *
     * type 参数指定主键生成策略，常用的有：
     * - IdType.AUTO：数据库自增（依赖数据库的自增主键）
     * - IdType.ASSIGN_ID：雪花算法生成唯一 ID（默认，适合分布式场景）
     * - IdType.ASSIGN_UUID：UUID 字符串
     * - IdType.INPUT：手动输入
     * - IdType.NONE：不设置，跟随全局配置
     */
    @TableId(type = IdType.ASSIGN_ID)  // 使用雪花算法生成分布式唯一 ID
    private Long id;

    private String username;  // 字段名默认映射到 username 列
    private String nickname;  // 字段名默认映射到 nickname 列
    private String email;     // 字段名默认映射到 email 列
}
```

**字段映射规则**：MyBatis-Plus 默认将 Java 类的驼峰命名自动转换为数据库的下划线命名：

| Java 属性名 | 数据库列名 | 映射规则 |
|-------------|-----------|---------|
| `username` | `username` | 单单词，不转换 |
| `nickname` | `nickname` | 单单词，不转换 |
| `createTime` | `create_time` | 驼峰转下划线 |
| `updateTime` | `update_time` | 驼峰转下划线 |
| `delFlag` | `del_flag` | 驼峰转下划线 |

**关键点**：如果数据库列名与 Java 属性名无法通过驼峰-下划线规则映射，可以使用 `@TableField(value = "数据库列名")` 显式指定列名。

### 概念 3：条件构造器（LambdaQueryWrapper）—— 就像"智能过滤器"

**生活类比**：你在电商网站买书，需要筛选条件：价格在 50-100 元之间、评分 4 星以上、有货、按销量排序。你不需要写 SQL 告诉网站怎么查数据库，只需要在筛选面板上勾选条件，网站就会自动生成查询语句。

**技术映射**：`LambdaQueryWrapper<T>` 就是 MyBatis-Plus 的"智能筛选面板"，通过链式调用方法构建查询条件，底层自动生成 WHERE 子句：

```java
/**
 * LambdaQueryWrapper 使用示例 —— 智能过滤器的类比。
 *
 * 就像在电商网站勾选筛选条件一样：
 * - eq：等于（价格 = 50）
 * - ge：大于等于（价格 >= 50）
 * - like：模糊匹配（标题包含 "Java"）
 * - orderByDesc：排序（按销量倒序）
 * - between：范围（价格在 50 到 100 之间）
 */
// 构建查询条件 —— 链式调用，像搭积木一样组合条件
LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
        .eq(Product::getStatus, 1)              // 状态 = 1（上架）
        .ge(Product::getPrice, 50)               // 价格 >= 50
        .le(Product::getPrice, 100)              // 价格 <= 100
        .like(Product::getTitle, "Java")         // 标题包含 "Java"
        .orderByDesc(Product::getSalesCount);    // 按销量倒序排序

// 执行查询 —— 自动生成 SQL 并执行
List<Product> products = productMapper.selectList(wrapper);
// 生成的 SQL 大致如下：
// SELECT * FROM product WHERE status = 1 AND price >= 50
//   AND price <= 100 AND title LIKE '%Java%' ORDER BY sales_count DESC
```

**LambdaQueryWrapper 的三大优势：**

1. **类型安全**：使用 `Product::getPrice`（方法引用）而不是字符串 `"price"`，编译期就能检查字段是否存在
2. **链式调用**：方法调用链像搭积木一样组合条件，代码可读性极强
3. **条件控制**：每个方法都有一个 `condition` 参数重载，可以控制是否应用该条件：

```java
/**
 * condition 参数用法 —— 根据条件决定是否应用查询条件。
 *
 * 第一个参数是 boolean 值，只有为 true 时才会应用该条件。
 * 这在条件查询中非常有用：用户没有输入某个条件时，不应用该条件。
 */
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

// 如果 keyword 不为空，才应用 like 条件
// 如果 keyword 为空（用户没输入搜索关键词），这条条件不生效
wrapper.like(StringUtils.isNotBlank(keyword), User::getUsername, keyword);

// 如果 status 不为 null，才应用 eq 条件
wrapper.eq(status != null, User::getStatus, status);

// 如果 startDate 不为 null，才应用 ge 条件
wrapper.ge(startDate != null, User::getCreateTime, startDate);
```

### 概念 4：分页插件 —— 就像"自动翻页器"

**生活类比**：你看一本电子书，只需要告诉阅读器"第 10 页，每页 30 行"，阅读器自动帮你翻到那一页，并告诉你"全书共 300 页"。你不需要手动数行数、不需要计算偏移量，阅读器全都帮你处理好了。

**技术映射**：`PaginationInnerInterceptor` 就是 MyBatis-Plus 的"自动翻页器"，你只需要告诉它"第几页、每页几条"，它就自动帮你完成 COUNT 查询和 LIMIT 拼接：

```java
/**
 * 分页查询示例 —— 自动翻页器的类比。
 *
 * 只需要创建 Page 对象，传入"第几页"和"每页几条"，
 * 分页插件自动完成：
 * 1. 执行 COUNT 查询，计算总条数
 * 2. 追加 LIMIT 子句，查询当前页数据
 * 3. 计算总页数
 * 4. 封装到 Page 对象中返回
 */
// 第 1 页，每页 10 条
Page<User> page = new Page<>(1, 10);

// 执行分页查询 —— 分页插件自动拦截，生成 COUNT + LIMIT 语句
Page<User> result = userMapper.selectPage(page, null);

// 从结果中获取分页信息
List<User> records = result.getRecords();  // 当前页的数据列表
long total = result.getTotal();            // 总记录数
long pages = result.getPages();            // 总页数
long current = result.getCurrent();        // 当前页码
long size = result.getSize();              // 每页大小
boolean hasNext = result.hasNext();        // 是否有下一页
```

---

## 三、从零搭建：完整可运行的 MyBatis-Plus 项目

### 3.1 项目结构

```
hello-mp/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── hellomp/
│   │   │           ├── HelloMpApplication.java          # 启动类
│   │   │           ├── config/
│   │   │           │   └── MybatisPlusConfig.java        # MyBatis-Plus 配置（分页插件）
│   │   │           ├── entity/
│   │   │           │   └── User.java                     # 用户实体类
│   │   │           ├── mapper/
│   │   │           │   └── UserMapper.java               # Mapper 接口（继承 BaseMapper）
│   │   │           ├── service/
│   │   │           │   ├── UserService.java              # 用户服务接口
│   │   │           │   └── UserServiceImpl.java          # 用户服务实现
│   │   │           └── controller/
│   │   │               └── UserController.java           # REST 控制器
│   │   └── resources/
│   │       ├── application.yml                           # 应用配置
│   │       ├── mapper/
│   │       │   └── UserMapper.xml                        # 自定义 SQL 映射（可选）
│   │       └── db/
│   │           └── schema.sql                            # 数据库建表 SQL
│   └── test/
│       └── java/
│           └── com/
│               └── hellomp/
│                   ├── mapper/
│                   │   └── UserMapperTest.java           # Mapper 层测试
│                   └── service/
│                       └── UserServiceTest.java          # Service 层测试
```

### 3.2 数据库建表 SQL

首先，我们需要创建数据库和表。本示例使用 MySQL 数据库，创建一个简单的用户表用于演示。

```sql
-- =============================================
-- 数据库建表 SQL —— 用于 MyBatis-Plus 入门示例
-- 运行此 SQL 前，请先创建数据库：
-- CREATE DATABASE hello_mp DEFAULT CHARACTER SET utf8mb4;
-- =============================================

-- 用户表：存储用户基本信息
-- 使用 utf8mb4 字符集，支持存储 emoji 表情
CREATE TABLE IF NOT EXISTS sys_user (
    -- 主键 ID：BIGINT 类型，支持雪花算法生成的 19 位数字 ID
    id          BIGINT       NOT NULL COMMENT '主键 ID',
    -- 用户名：唯一，用于登录
    username    VARCHAR(50)  NOT NULL COMMENT '用户名',
    -- 昵称：显示名称，可为空
    nickname    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    -- 邮箱：唯一，用于找回密码等
    email       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    -- 手机号：唯一，用于登录验证
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    -- 性别：0-未知 1-男 2-女
    gender      TINYINT      DEFAULT 0 COMMENT '性别（0-未知 1-男 2-女）',
    -- 状态：0-禁用 1-启用
    status      TINYINT      DEFAULT 1 COMMENT '状态（0-禁用 1-启用）',
    -- 创建时间：自动填充
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 更新时间：自动填充
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 逻辑删除标识：0-未删除 1-已删除（MyBatis-Plus 自动处理）
    del_flag    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除（0-未删除 1-已删除）',
    -- 乐观锁版本号：每次更新自动 +1
    version     INT          DEFAULT 0 COMMENT '乐观锁版本号',
    -- 主键约束
    PRIMARY KEY (id),
    -- 唯一索引：用户名唯一
    UNIQUE INDEX idx_username (username),
    -- 普通索引：按创建时间排序
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户表';
```

### 3.3 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 项目基本信息 -->
    <groupId>com.hellomp</groupId>
    <artifactId>hello-mp</artifactId>
    <version>1.0.0</version>
    <name>hello-mp</name>
    <description>MyBatis-Plus 入门示例项目</description>

    <!--
    Spring Boot 父项目：管理所有依赖版本。
    使用 spring-boot-starter-parent 可以省去手动管理版本号的工作。
    -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <properties>
        <!-- Java 版本：使用 Java 17，支持 record、密封类等新特性 -->
        <java.version>17</java.version>
        <!-- MyBatis-Plus 版本：3.5.7 是较新的稳定版本 -->
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
    </properties>

    <dependencies>
        <!-- ========== Spring Boot Web Starter ========== -->
        <!-- 提供 Spring MVC、内嵌 Tomcat、REST 支持 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- ========== MyBatis-Plus Spring Boot Starter ========== -->
        <!--
        核心依赖：提供 BaseMapper、LambdaQueryWrapper、分页插件等。
        此 starter 会自动配置 SqlSessionFactory、SqlSessionTemplate，
        无需手动配置 MyBatis 的 XML 文件位置等。
        -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- ========== MySQL JDBC 驱动 ========== -->
        <!-- MySQL 8.0+ 使用 com.mysql.cj.jdbc.Driver 驱动 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>  <!-- 运行时需要，编译时不需要 -->
        </dependency>

        <!-- ========== HikariCP 连接池 ========== -->
        <!--
        Spring Boot 2.x/3.x 默认使用 HikariCP 连接池。
        它的特点是：性能极高、轻量级、可靠稳定。
        无需额外引入，spring-boot-starter-jdbc 已包含。
        -->

        <!-- ========== Lombok ========== -->
        <!--
        简化代码：自动生成 getter、setter、toString、equals、hashCode 等。
        @Data 注解 = @Getter + @Setter + @ToString + @EqualsAndHashCode
        -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>  <!-- 编译时需要，打包时不需要 -->
        </dependency>

        <!-- ========== Spring Boot Starter Test ========== -->
        <!-- 提供 JUnit 5、Mockito、AssertJ 等测试框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- ========== H2 内存数据库（仅测试用） ========== -->
        <!--
        测试时使用 H2 内存数据库，无需安装 MySQL。
        这样可以快速运行测试，不需要依赖外部数据库。
        -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 打包插件 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <!-- 排除 Lombok，不需要打包到最终 JAR 中 -->
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.4 application.yml

```yaml
# =============================================
# MyBatis-Plus 入门示例项目配置
# 注意：所有 yml 配置项使用 kebab-case（短横线命名）
# =============================================

# 服务器配置
server:
  port: 8080

# Spring 配置
spring:
  application:
    name: hello-mp

  # ========== 数据源配置 ==========
  datasource:
    # JDBC 驱动类：MySQL 8.0+ 使用 cj 驱动
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 数据库连接 URL：useUnicode 启用 Unicode，characterEncoding 指定编码
    url: jdbc:mysql://localhost:3306/hello_mp?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    # 数据库用户名
    username: root
    # 数据库密码：生产环境应通过环境变量注入，不要硬编码
    password: root123

    # ========== HikariCP 连接池配置 ==========
    hikari:
      # 连接池名称：方便监控和日志排查
      pool-name: HelloMpPool
      # 最大连接数：根据业务并发量调整，一般 10-20 足够
      maximum-pool-size: 20
      # 最小空闲连接数：保持一定数量的空闲连接，减少创建开销
      minimum-idle: 5
      # 获取连接的超时时间（毫秒）：超过此时间抛异常
      connection-timeout: 30000
      # 连接最大存活时间（毫秒）：超过此时间的连接会被回收
      max-lifetime: 600000
      # 空闲连接检测间隔（毫秒）：定期检测空闲连接是否有效
      idle-timeout: 600000

# ========== MyBatis-Plus 配置 ==========
mybatis-plus:
  # Mapper XML 文件位置：classpath* 表示扫描所有依赖包中的 mapper 目录
  # 注意：使用 kebab-case，不是 mapperLocations
  mapper-locations: classpath*:mapper/**/*.xml

  # 实体类包路径：MyBatis-Plus 自动扫描此包下的实体类，注册别名
  # 设置了此路径后，在 XML 中可以直接使用类名（不区分大小写），无需写全限定名
  type-aliases-package: com.hellomp.entity

  # ========== 全局配置 ==========
  global-config:
    # 数据库相关配置
    db-config:
      # 主键生成策略：ASSIGN_ID = 雪花算法（分布式场景推荐）
      id-type: assign_id
      # 表名前缀：所有表统一加前缀，如 sys_ 开头
      # 设置了此配置后，实体类 User 对应 sys_user 表
      table-prefix: sys_
      # 逻辑删除字段名：实体类中 delFlag 字段对应的数据库列名
      logic-delete-field: del_flag
      # 逻辑删除的值：1 表示已删除
      logic-delete-value: 1
      # 逻辑未删除的值：0 表示未删除
      logic-not-delete-value: 0

  # ========== 配置增强 ==========
  configuration:
    # 驼峰转下划线映射：userName -> user_name
    # 默认开启，保持开启即可
    map-underscore-to-camel-case: true
    # 开启 SQL 日志打印：开发环境开启，方便调试
    # 生产环境建议关闭，避免日志过多影响性能
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

### 3.5 启动类

```java
package com.hellomp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动类 —— 项目的入口。
 *
 * @SpringBootApplication 是一个组合注解，包含：
 * - @Configuration：标记为配置类
 * - @EnableAutoConfiguration：启用 Spring Boot 自动配置
 * - @ComponentScan：自动扫描当前包及其子包下的组件
 *
 * MyBatis-Plus 的 @MapperScan 可以放在这里，也可以放在配置类上。
 * 为了职责清晰，我们将 @MapperScan 放在 MybatisPlusConfig 配置类中。
 */
@SpringBootApplication
public class HelloMpApplication {

    /**
     * 主方法 —— 启动 Spring Boot 应用。
     * SpringApplication.run() 会启动内嵌 Tomcat 并自动装配所有 Bean。
     */
    public static void main(String[] args) {
        SpringApplication.run(HelloMpApplication.class, args);
    }
}
```

### 3.6 实体类

```java
package com.hellomp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类 —— 对应 sys_user 表。
 *
 * @Data 是 Lombok 注解，自动生成：
 * - getter/setter 方法
 * - toString() 方法
 * - equals() 和 hashCode() 方法
 * - 带参/无参构造方法（需要额外 @AllArgsConstructor 和 @NoArgsConstructor）
 *
 * MyBatis-Plus 实体类注解说明：
 * - @TableName：指定表名（默认驼峰转下划线）
 * - @TableId：指定主键字段和生成策略
 * - @TableField：指定非主键字段的映射规则
 * - @TableLogic：逻辑删除字段标记
 * - @Version：乐观锁版本号字段标记
 */
@Data  // Lombok 注解：自动生成 getter/setter/toString/equals/hashCode
@TableName("sys_user")  // 指定表名：对应数据库中的 sys_user 表
public class User {

    /**
     * 主键 ID —— 使用雪花算法生成。
     *
     * IdType.ASSIGN_ID：MyBatis-Plus 使用雪花算法生成唯一 ID。
     * 雪花算法生成的 ID 是 64 位 long 型整数，全局唯一、趋势递增。
     * 适合分布式场景，不需要依赖数据库自增。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户名 —— 用于登录。
     * 数据库字段名就是 username，与属性名一致，无需额外映射。
     */
    private String username;

    /**
     * 昵称 —— 显示名称。
     * 可以为空，数据库字段允许 NULL。
     */
    private String nickname;

    /**
     * 邮箱 —— 用于找回密码等操作。
     * 数据库字段名 email，与属性名一致。
     */
    private String email;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 性别：0-未知 1-男 2-女。
     * 数据库字段类型为 TINYINT，映射到 Java 的 Integer。
     */
    private Integer gender;

    /**
     * 状态：0-禁用 1-启用。
     */
    private Integer status;

    /**
     * 创建时间 —— 插入时自动填充。
     *
     * @TableField(fill = FieldFill.INSERT) 表示：
     * 执行 insert 操作时，自动设置此字段的值。
     * 配合 MetaObjectHandler 使用，自动填充当前时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间 —— 插入和更新时自动填充。
     *
     * @TableField(fill = FieldFill.INSERT_UPDATE) 表示：
     * 执行 insert 和 update 操作时，自动设置此字段的值。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识：0-未删除 1-已删除。
     *
     * @TableLogic 注解标记此字段为逻辑删除字段。
     * 标记后：
     * - deleteById() 自动转为 UPDATE SET del_flag = 1
     * - selectList() 自动追加 WHERE del_flag = 0
     * - 查询时自动过滤已删除的记录
     *
     * 注意：逻辑删除和唯一索引一起使用时要注意冲突问题。
     */
    @TableLogic
    private String delFlag;

    /**
     * 乐观锁版本号 —— 每次更新自动 +1。
     *
     * @Version 注解标记此字段为乐观锁版本号。
     * 更新时自动执行：SET version = version + 1 WHERE version = oldVersion
     * 如果版本号不匹配（说明数据被其他线程修改过），更新失败。
     * 防止并发修改导致的数据覆盖问题。
     */
    @Version
    private Integer version;
}
```

### 3.7 Mapper 接口

```java
package com.hellomp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hellomp.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper 接口 —— 数据访问层。
 *
 * 继承 BaseMapper<User> 后，自动获得 20+ 个通用 CRUD 方法：
 * - insert、deleteById、updateById、selectById
 * - selectList、selectPage、selectCount、selectOne
 * - 等等，无需写任何 SQL 或 XML
 *
 * 对于自定义查询（如多表 JOIN、复杂统计），
 * 在 XML 文件中编写对应的 SQL 语句，方法定义在此接口中。
 * MyBatis-Plus 不限制 MyBatis 的原生能力。
 */
@Mapper  // 标记为 MyBatis Mapper，Spring 会自动扫描并创建代理对象
public interface UserMapper extends BaseMapper<User> {

    /**
     * 自定义查询：根据用户名模糊搜索用户列表。
     *
     * 这是一个自定义方法，需要在 XML 中编写对应的 SQL。
     * 如果只是简单的单表查询，使用 BaseMapper 的 selectList 方法即可，
     * 不需要写 XML。这里演示自定义 XML 的用法。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表
     */
    List<User> searchByUsername(@Param("keyword") String keyword);

    /**
     * 自定义查询：统计指定状态的用户数量。
     * 演示聚合查询的用法。
     *
     * @param status 用户状态
     * @return 用户数量
     */
    Long countByStatus(@Param("status") Integer status);
}
```

### 3.8 Mapper XML 映射文件

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<!--
UserMapper.xml —— MyBatis 映射文件。

namespace 必须与 Mapper 接口的全限定名一致。
MyBatis 通过 namespace + id 定位到对应的方法。
-->
<mapper namespace="com.hellomp.mapper.UserMapper">

    <!--
    自定义查询：根据用户名模糊搜索用户列表。

    这里演示了 MyBatis 的原生能力：
    - 手写 SQL 语句，完全控制查询逻辑
    - 使用 LIKE 模糊匹配
    - 使用 ORDER BY 排序
    - MyBatis-Plus 不限制这些能力

    resultType 指定返回类型，MyBatis 自动将结果集映射到 User 对象。
    由于在 application.yml 中配置了 type-aliases-package，
    这里可以直接使用 User，不需要写全限定名。
    -->
    <select id="searchByUsername" resultType="com.hellomp.entity.User">
        -- 查询用户名包含关键词的用户，按创建时间倒序排列
        SELECT id, username, nickname, email, phone, gender, status,
               create_time, update_time, del_flag, version
        FROM sys_user
        WHERE username LIKE CONCAT('%', #{keyword}, '%')
          AND del_flag = '0'
        ORDER BY create_time DESC
    </select>

    <!--
    自定义查询：统计指定状态的用户数量。

    使用 COUNT 聚合函数，返回 Long 类型的结果。
    parameterType 指定参数类型，可以省略（MyBatis 自动推断）。
    -->
    <select id="countByStatus" resultType="java.lang.Long">
        -- 统计指定状态的用户数量
        SELECT COUNT(*)
        FROM sys_user
        WHERE status = #{status}
          AND del_flag = '0'
    </select>

</mapper>
```

### 3.9 MyBatis-Plus 配置类

```java
package com.hellomp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置类 —— 注册各种插件和处理器。
 *
 * 职责：
 * 1. 配置分页插件：自动拦截 Page 类型参数，生成 COUNT + LIMIT
 * 2. 配置乐观锁插件：自动管理 @Version 注解的版本号
 * 3. 配置自动填充处理器：自动填充 createTime、updateTime 等公共字段
 * 4. 配置 Mapper 扫描路径
 */
@Configuration  // 标记为 Spring 配置类
@MapperScan("com.hellomp.mapper")  // 扫描 Mapper 接口包，自动创建代理对象
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 插件集合 —— 注册所有拦截器。
     *
     * MybatisPlusInterceptor 是 MyBatis-Plus 的插件容器，
     * 所有拦截器添加到此容器中，按添加顺序执行。
     * 注意：分页插件必须放在首位，否则可能影响其他插件的执行。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建插件容器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // ========== 1. 分页插件 ==========
        // 作用：拦截分页查询，自动生成 COUNT 和 LIMIT 语句
        // 参数：DbType.MYSQL 指定数据库类型，插件会根据数据库类型生成对应的分页语法
        // MySQL 生成 LIMIT，PostgreSQL 生成 LIMIT...OFFSET，Oracle 生成 ROWNUM
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // ========== 2. 乐观锁插件 ==========
        // 作用：拦截 UPDATE 语句，自动处理 @Version 注解的版本号字段
        // 更新时自动执行：SET version = version + 1 WHERE version = oldVersion
        // 如果 version 不匹配（数据被其他线程修改过），更新失败，返回 0 条记录
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 自动填充处理器 —— 自动设置 createTime 和 updateTime。
     *
     * 配合实体类上的 @TableField(fill = FieldFill.INSERT) 使用。
     * 当执行 insert 或 update 操作时，自动填充对应字段的值。
     *
     * 作用：
     * - 插入时：自动填充 createTime 和 updateTime
     * - 更新时：自动填充 updateTime
     * - 业务代码不需要手动设置这些公共字段
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        // 返回 MetaObjectHandler 的匿名内部类实现
        return new MetaObjectHandler() {

            /**
             * 插入时自动填充 —— 设置 createTime 和 updateTime。
             * 在调用 BaseMapper.insert() 时自动触发。
             *
             * @param metaObject MyBatis 的元对象，可以获取和设置实体类的属性值
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                // strictInsertFill：严格模式插入填充
                // 参数1：metaObject 元对象
                // 参数2：字段名（实体类中的属性名，不是数据库列名）
                // 参数3：填充的值（Lambda 表达式，延迟执行）
                // 参数4：字段类型
                this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
            }

            /**
             * 更新时自动填充 —— 设置 updateTime。
             * 在调用 BaseMapper.updateById() 或 update() 时自动触发。
             *
             * @param metaObject MyBatis 的元对象
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                // strictUpdateFill：严格模式更新填充
                // 只填充实体类中值为 null 的字段，避免覆盖已有值
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
            }
        };
    }
}
```

### 3.10 Service 接口和实现

```java
package com.hellomp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hellomp.entity.User;

import java.util.List;

/**
 * 用户服务接口 —— 业务逻辑层的抽象。
 *
 * 定义用户相关的业务操作。
 * 接口与实现分离，便于单元测试和面向接口编程。
 */
public interface UserService {

    /**
     * 创建用户。
     *
     * @param user 用户实体（不含 ID，ID 由雪花算法自动生成）
     * @return 是否成功
     */
    boolean createUser(User user);

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户实体，不存在返回 null
     */
    User getUserById(Long id);

    /**
     * 更新用户信息。
     *
     * @param user 需要更新的用户信息（根据 ID 更新）
     * @return 是否成功
     */
    boolean updateUser(User user);

    /**
     * 根据 ID 删除用户（逻辑删除）。
     *
     * @param id 用户 ID
     * @return 是否成功
     */
    boolean deleteUser(Long id);

    /**
     * 查询所有用户列表。
     *
     * @return 用户列表
     */
    List<User> listAllUsers();

    /**
     * 分页查询用户。
     *
     * @param current 当前页码（从 1 开始）
     * @param size    每页大小
     * @return 分页对象，包含数据和分页信息
     */
    IPage<User> pageUsers(long current, long size);

    /**
     * 根据条件查询用户列表。
     *
     * @param keyword 关键词（按用户名模糊匹配）
     * @param status  用户状态（为 null 时忽略此条件）
     * @return 符合条件的用户列表
     */
    List<User> searchUsers(String keyword, Integer status);
}
```

```java
package com.hellomp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hellomp.entity.User;
import com.hellomp.mapper.UserMapper;
import com.hellomp.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户服务实现类 —— 业务逻辑的具体实现。
 *
 * 继承 ServiceImpl<UserMapper, User> 后，自动获得：
 * - save、saveBatch、updateById、removeById、getById、list、page 等方法
 * - ServiceImpl 是对 BaseMapper 的进一步封装，提供了更丰富的业务方法
 *
 * ServiceImpl 的继承结构：
 * ServiceImpl<M extends BaseMapper<T>, T>
 *   implements IService<T>
 *     - save(T entity)：插入数据
 *     - saveBatch(Collection<T>)：批量插入
 *     - getById(Serializable)：根据 ID 查询
 *     - list()：查询所有
 *     - page(Page<T>)：分页查询
 *     - lambdaQuery()：获取 LambdaQueryChainWrapper 链式查询
 */
@Service  // 标记为 Spring Service Bean
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 创建用户 —— 使用 IService 提供的 save 方法。
     *
     * save 方法内部调用 BaseMapper.insert()，
     * 如果主键为 null，自动生成雪花算法 ID。
     * createTime 和 updateTime 由 MetaObjectHandler 自动填充。
     */
    @Override
    public boolean createUser(User user) {
        // save 方法：插入数据，成功返回 true
        // 自动处理：主键生成、createTime 填充、updateTime 填充
        return save(user);
    }

    /**
     * 根据 ID 查询用户 —— 使用 IService 提供的 getById 方法。
     *
     * getById 方法内部调用 BaseMapper.selectById()。
     * 如果用户不存在，返回 null。
     * 逻辑删除自动过滤：del_flag = 0 的记录才会被查询到。
     */
    @Override
    public User getUserById(Long id) {
        // getById 方法：根据主键查询，返回实体或 null
        return getById(id);
    }

    /**
     * 更新用户信息 —— 使用 IService 提供的 updateById 方法。
     *
     * updateById 方法内部调用 BaseMapper.updateById()。
     * 只更新实体中非 null 的字段，null 字段不更新。
     * updateTime 由 MetaObjectHandler 自动更新。
     * 如果 @Version 字段的值与数据库不一致，更新失败返回 false。
     */
    @Override
    public boolean updateUser(User user) {
        // updateById 方法：根据主键更新非 null 字段
        // 返回 boolean：true 表示更新成功，false 表示更新失败
        // 更新失败可能的原因：乐观锁版本号不一致、记录不存在
        return updateById(user);
    }

    /**
     * 根据 ID 删除用户（逻辑删除）—— 使用 IService 提供的 removeById 方法。
     *
     * 由于实体类中 delFlag 字段标注了 @TableLogic，
     * removeById 自动转为 UPDATE sys_user SET del_flag = 1 WHERE id = ?
     * 不会真正删除数据，只是标记为已删除。
     */
    @Override
    public boolean deleteUser(Long id) {
        // removeById 方法：逻辑删除（标记 del_flag = 1）
        // 如果实体类没有 @TableLogic 注解，才是物理删除
        return removeById(id);
    }

    /**
     * 查询所有用户列表 —— 使用 IService 提供的 list 方法。
     *
     * list 方法内部调用 BaseMapper.selectList()。
     * 由于逻辑删除自动过滤，只返回 del_flag = 0 的记录。
     * 相当于：SELECT * FROM sys_user WHERE del_flag = '0'
     */
    @Override
    public List<User> listAllUsers() {
        // list 方法：查询所有记录
        return list();
    }

    /**
     * 分页查询用户 —— 使用 IService 提供的 page 方法。
     *
     * 分页插件自动拦截 Page 对象，执行以下操作：
     * 1. 先执行 COUNT 查询，计算总记录数
     * 2. 再执行 SELECT 查询，追加 LIMIT 子句
     * 3. 将结果封装到 Page 对象中返回
     *
     * @param current 当前页码（从 1 开始）
     * @param size    每页大小
     * @return 分页对象，包含 records、total、pages 等信息
     */
    @Override
    public IPage<User> pageUsers(long current, long size) {
        // 创建 Page 对象：传入当前页码和每页大小
        Page<User> page = new Page<>(current, size);

        // page 方法：分页查询，自动拦截处理
        // 第二个参数是查询条件 Wrapper，传入 null 表示无条件查询
        return page(page, null);
    }

    /**
     * 根据条件查询用户列表 —— 演示 LambdaQueryWrapper 的用法。
     *
     * 这是 MyBatis-Plus 最强大的功能之一：
     * 通过 LambdaQueryWrapper 链式构建查询条件，
     * 无需写 SQL，无需拼字符串，类型安全。
     *
     * @param keyword 关键词（按用户名模糊匹配，为空时忽略）
     * @param status  用户状态（为 null 时忽略此条件）
     * @return 符合条件的用户列表
     */
    @Override
    public List<User> searchUsers(String keyword, Integer status) {
        // 创建 LambdaQueryWrapper：泛型参数指定实体类型
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                // like 方法：模糊匹配，第一个参数是条件开关
                // StringUtils.hasText(keyword) 为 true 时，才应用 like 条件
                .like(StringUtils.hasText(keyword), User::getUsername, keyword)
                // eq 方法：等值匹配，status 不为 null 时才应用
                .eq(status != null, User::getStatus, status)
                // orderByDesc 方法：按创建时间倒序
                .orderByDesc(User::getCreateTime);

        // 执行查询：list 方法接受 Wrapper 参数，返回符合条件的记录列表
        // 如果 Wrapper 为 null，查询所有记录
        return list(wrapper);
    }
}
```

### 3.11 REST 控制器

```java
package com.hellomp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hellomp.entity.User;
import com.hellomp.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户 REST 控制器 —— 提供用户管理的 HTTP API 接口。
 *
 * @RestController = @Controller + @ResponseBody
 * 所有方法的返回值自动序列化为 JSON。
 */
@RestController  // 标记为 REST 控制器
@RequestMapping("/api/users")  // 请求路径前缀：所有接口以 /api/users 开头
public class UserController {

    /**
     * 注入 UserService —— 构造器注入方式（推荐）。
     *
     * 构造器注入的优势：
     * 1. 不可变性：final 字段一旦初始化就不能修改
     * 2. 可测试性：单元测试时可以直接传入 Mock 对象
     * 3. 依赖明确：所有依赖都在构造器中显式声明
     */
    private final UserService userService;

    /**
     * 构造器注入 —— Spring 自动注入 UserService 的 Bean。
     * 在 Spring 4.3+ 中，如果类只有一个构造器，可以省略 @Autowired。
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 创建用户 —— POST 请求。
     *
     * @RequestBody：将请求体中的 JSON 自动反序列化为 User 对象
     * ResponseEntity：Spring 的 HTTP 响应封装，可以设置状态码和响应体
     *
     * 请求示例：
     * POST /api/users
     * {
     *     "username": "zhangsan",
     *     "nickname": "张三",
     *     "email": "zhangsan@example.com",
     *     "phone": "13800138000",
     *     "gender": 1,
     *     "status": 1
     * }
     */
    @PostMapping  // 处理 POST 请求
    public ResponseEntity<User> createUser(@RequestBody User user) {
        // 调用 Service 层创建用户
        boolean success = userService.createUser(user);
        if (success) {
            // 创建成功：返回 200 OK，并返回创建后的用户信息（包含自动生成的 ID）
            return ResponseEntity.ok(user);
        } else {
            // 创建失败：返回 500 Internal Server Error
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据 ID 查询用户 —— GET 请求。
     *
     * @PathVariable：从 URL 路径中获取参数
     *
     * 请求示例：GET /api/users/123456
     */
    @GetMapping("/{id}")  // 路径变量：{id} 对应方法参数
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        // 调用 Service 层查询用户
        User user = userService.getUserById(id);
        if (user != null) {
            // 找到用户：返回 200 OK
            return ResponseEntity.ok(user);
        } else {
            // 未找到用户：返回 404 Not Found
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 更新用户信息 —— PUT 请求。
     *
     * 请求示例：
     * PUT /api/users/123456
     * {
     *     "id": 123456,
     *     "nickname": "张三（已更新）",
     *     "email": "newemail@example.com"
     * }
     */
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        // 设置要更新的用户 ID（确保路径中的 ID 和请求体中的 ID 一致）
        user.setId(id);
        // 调用 Service 层更新用户
        boolean success = userService.updateUser(user);
        if (success) {
            // 更新成功：返回更新后的用户信息
            return ResponseEntity.ok(userService.getUserById(id));
        } else {
            // 更新失败：可能原因：乐观锁冲突、记录不存在
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除用户（逻辑删除）—— DELETE 请求。
     *
     * 请求示例：DELETE /api/users/123456
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        // 调用 Service 层删除用户（逻辑删除）
        boolean success = userService.deleteUser(id);
        if (success) {
            // 删除成功：返回 204 No Content（没有响应体）
            return ResponseEntity.noContent().build();
        } else {
            // 删除失败：返回 404 Not Found
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 查询所有用户 —— GET 请求。
     *
     * 请求示例：GET /api/users
     */
    @GetMapping
    public ResponseEntity<List<User>> listAllUsers() {
        // 调用 Service 层查询所有用户
        List<User> users = userService.listAllUsers();
        // 返回用户列表
        return ResponseEntity.ok(users);
    }

    /**
     * 分页查询用户 —— GET 请求，带分页参数。
     *
     * @RequestParam：从查询字符串中获取参数，并设置默认值
     *
     * 请求示例：GET /api/users/page?current=1&size=10
     */
    @GetMapping("/page")
    public ResponseEntity<IPage<User>> pageUsers(
            // 当前页码：默认第 1 页
            @RequestParam(defaultValue = "1") long current,
            // 每页大小：默认每页 10 条
            @RequestParam(defaultValue = "10") long size) {
        // 调用 Service 层分页查询
        IPage<User> page = userService.pageUsers(current, size);
        // 返回分页对象，包含：records（数据）、total（总条数）、pages（总页数）
        return ResponseEntity.ok(page);
    }

    /**
     * 条件搜索用户 —— GET 请求，带搜索参数。
     *
     * 请求示例：GET /api/users/search?keyword=zhang&status=1
     */
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(
            // 搜索关键词：可选参数
            @RequestParam(required = false) String keyword,
            // 用户状态：可选参数
            @RequestParam(required = false) Integer status) {
        // 调用 Service 层按条件查询
        List<User> users = userService.searchUsers(keyword, status);
        // 返回符合条件的用户列表
        return ResponseEntity.ok(users);
    }
}
```

### 3.12 单元测试

```java
package com.hellomp.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hellomp.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserMapper 测试类 —— 测试 MyBatis-Plus 的核心功能。
 *
 * @SpringBootTest：启动 Spring Boot 应用上下文，自动装配 Bean。
 * 测试时使用 H2 内存数据库（在 test/resources 中配置），
 * 不需要依赖外部 MySQL 数据库。
 *
 * 测试内容：
 * 1. BaseMapper 通用 CRUD 方法
 * 2. 分页查询
 * 3. LambdaQueryWrapper 条件构造器
 * 4. 自定义 XML 查询
 */
@SpringBootTest  // 启动完整的 Spring Boot 应用上下文
class UserMapperTest {

    /**
     * 自动注入 UserMapper。
     * UserMapper 继承了 BaseMapper，自动获得 20+ 个通用方法。
     */
    @Autowired
    private UserMapper userMapper;

    /**
     * 每个测试方法执行前，先插入测试数据。
     * 确保测试环境一致，避免测试之间的相互影响。
     */
    @BeforeEach
    void setUp() {
        // 清空测试数据（物理删除，H2 内存数据库不需要逻辑删除）
        userMapper.delete(null);

        // 插入 3 条测试用户数据
        for (int i = 1; i <= 3; i++) {
            User user = new User();
            user.setUsername("testuser" + i);  // testuser1, testuser2, testuser3
            user.setNickname("测试用户" + i);  // 测试用户1, 测试用户2, 测试用户3
            user.setEmail("test" + i + "@example.com");
            user.setPhone("1380000000" + i);
            user.setGender(i % 2 == 0 ? 1 : 2);  // 交替设置性别
            user.setStatus(1);  // 全部启用
            // 插入数据：ID 由雪花算法自动生成，createTime 由自动填充处理
            userMapper.insert(user);
        }
    }

    /**
     * 测试 insert —— 插入数据。
     *
     * 验证：
     * 1. insert 返回受影响的行数（应为 1）
     * 2. 插入后 ID 自动生成（不为 null）
     * 3. 自动填充生效（createTime 不为 null）
     */
    @Test
    void testInsert() {
        // 创建新用户
        User user = new User();
        user.setUsername("newuser");
        user.setNickname("新用户");
        user.setEmail("new@example.com");
        user.setPhone("13900000000");
        user.setGender(1);
        user.setStatus(1);

        // 执行插入：BaseMapper 的 insert 方法
        int rows = userMapper.insert(user);

        // 验证：插入成功，影响 1 行
        assertEquals(1, rows, "插入操作应影响 1 行记录");
        // 验证：ID 自动生成（雪花算法）
        assertNotNull(user.getId(), "ID 应由雪花算法自动生成");
        // 验证：自动填充生效
        assertNotNull(user.getCreateTime(), "createTime 应由自动填充处理");
        assertNotNull(user.getUpdateTime(), "updateTime 应由自动填充处理");
    }

    /**
     * 测试 selectById —— 根据主键查询。
     *
     * 验证：
     * 1. 能查询到已插入的数据
     * 2. 查询结果与插入数据一致
     * 3. 查询不存在的 ID 返回 null
     */
    @Test
    void testSelectById() {
        // 先查询所有用户，获取第一个用户的 ID
        List<User> users = userMapper.selectList(null);
        Long firstId = users.get(0).getId();

        // 根据 ID 查询用户
        User user = userMapper.selectById(firstId);

        // 验证：查询结果不为 null
        assertNotNull(user, "查询已存在的用户应返回非 null 结果");
        // 验证：用户名正确
        assertEquals("testuser1", user.getUsername(), "用户名应匹配");
    }

    /**
     * 测试 updateById —— 根据 ID 更新。
     *
     * 验证：
     * 1. 更新成功，返回受影响的行数
     * 2. 更新后的数据与预期一致
     * 3. updateTime 自动更新
     */
    @Test
    void testUpdateById() {
        // 获取第一个用户
        User user = userMapper.selectList(null).get(0);

        // 修改用户信息
        user.setNickname("已更新的昵称");
        user.setEmail("updated@example.com");

        // 执行更新：updateById 只更新非 null 字段
        int rows = userMapper.updateById(user);

        // 验证：更新成功，影响 1 行
        assertEquals(1, rows, "更新操作应影响 1 行记录");

        // 重新查询，验证更新生效
        User updated = userMapper.selectById(user.getId());
        assertEquals("已更新的昵称", updated.getNickname(), "昵称应已更新");
        assertEquals("updated@example.com", updated.getEmail(), "邮箱应已更新");
    }

    /**
     * 测试 deleteById —— 根据 ID 删除（逻辑删除）。
     *
     * 验证：
     * 1. 删除成功，返回受影响的行数
     * 2. 删除后查询该记录返回 null（逻辑删除自动过滤）
     * 3. 总记录数减少
     */
    @Test
    void testDeleteById() {
        // 获取第一个用户
        User user = userMapper.selectList(null).get(0);

        // 执行删除：逻辑删除（del_flag 置为 1）
        int rows = userMapper.deleteById(user.getId());

        // 验证：删除成功，影响 1 行
        assertEquals(1, rows, "删除操作应影响 1 行记录");

        // 再次查询：逻辑删除自动过滤，应返回 null
        User deleted = userMapper.selectById(user.getId());
        assertNull(deleted, "逻辑删除后，查询应返回 null（自动过滤）");
    }

    /**
     * 测试 selectPage —— 分页查询。
     *
     * 验证：
     * 1. 分页查询返回正确的总记录数
     * 2. 当前页数据与预期一致
     * 3. 分页信息正确（总页数、是否有下一页等）
     */
    @Test
    void testSelectPage() {
        // 创建分页对象：第 1 页，每页 2 条
        Page<User> page = new Page<>(1, 2);

        // 执行分页查询：分页插件自动拦截，生成 COUNT + LIMIT
        IPage<User> result = userMapper.selectPage(page, null);

        // 验证：总记录数（我们在 setUp 中插入了 3 条）
        assertEquals(3, result.getTotal(), "总记录数应为 3");
        // 验证：当前页数据条数（每页 2 条，第 1 页应有 2 条）
        assertEquals(2, result.getRecords().size(), "第 1 页应有 2 条记录");
        // 验证：总页数（3 条记录，每页 2 条，共 2 页）
        assertEquals(2, result.getPages(), "总页数应为 2");
        // 验证：有下一页
        assertTrue(result.hasNext(), "第 1 页应有下一页");
    }

    /**
     * 测试 LambdaQueryWrapper —— 条件查询。
     *
     * 验证：
     * 1. 条件构造器正确生成查询条件
     * 2. 链式调用组合多个条件
     * 3. 排序功能正常
     */
    @Test
    void testLambdaQueryWrapper() {
        // 构建查询条件：用户名包含 "testuser" 且状态为 1，按创建时间倒序
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(User::getUsername, "testuser")  // 模糊匹配：username LIKE '%testuser%'
                .eq(User::getStatus, 1)                // 等值匹配：status = 1
                .orderByDesc(User::getCreateTime);     // 排序：按创建时间倒序

        // 执行查询
        List<User> users = userMapper.selectList(wrapper);

        // 验证：查询结果不为空
        assertFalse(users.isEmpty(), "查询结果不应为空");
        // 验证：所有记录都符合条件
        for (User user : users) {
            assertTrue(user.getUsername().contains("testuser"),
                    "用户名应包含 'testuser'");
            assertEquals(1, user.getStatus(), "状态应为 1");
        }
    }

    /**
     * 测试自定义 XML 查询 —— searchByUsername。
     *
     * 验证 MyBatis 原生 XML 查询在 MyBatis-Plus 中正常使用。
     */
    @Test
    void testSearchByUsername() {
        // 调用自定义 XML 查询：按用户名模糊搜索
        List<User> users = userMapper.searchByUsername("testuser");

        // 验证：查询结果不为空
        assertFalse(users.isEmpty(), "自定义查询结果不应为空");
        // 验证：所有记录匹配关键词
        for (User user : users) {
            assertTrue(user.getUsername().contains("testuser"),
                    "用户名应包含搜索关键词");
        }
    }
}
```

### 3.13 测试配置文件

```yaml
# =============================================
# 测试环境配置 —— 使用 H2 内存数据库
# 测试时不需要启动 MySQL，快速运行测试
# =============================================

spring:
  # ========== 数据源配置（H2 内存数据库） ==========
  datasource:
    # H2 内存数据库驱动
    driver-class-name: org.h2.Driver
    # H2 内存数据库连接：MODE=MySQL 表示兼容 MySQL 语法
    # DB_CLOSE_DELAY=-1 表示 JVM 退出前不关闭数据库
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    # H2 默认用户名
    username: sa
    # H2 默认密码为空
    password:

  # ========== H2 控制台配置 ==========
  h2:
    console:
      # 启用 H2 Web 控制台：浏览器访问 /h2-console 查看数据
      enabled: true
      # H2 控制台的访问路径
      path: /h2-console

  # ========== SQL 初始化配置 ==========
  sql:
    init:
      # 测试时自动执行 schema.sql 建表
      mode: always
      # schema.sql 文件位置
      schema-locations: classpath:db/schema.sql

# ========== MyBatis-Plus 配置 ==========
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.hellomp.entity
  configuration:
    # 测试时开启 SQL 日志，方便调试
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

---

## 四、运行验证

### 4.1 环境准备

在运行项目之前，确保以下环境已就绪：

1. **JDK 17+**：项目使用 Java 17 编译
2. **Maven 3.8+**：项目构建工具
3. **MySQL 8.0+**：数据库（或者使用 H2 内存数据库运行测试）
4. **IDE（推荐 IntelliJ IDEA）**：开发工具

### 4.2 创建数据库

```sql
-- 创建数据库（如果尚未创建）
CREATE DATABASE IF NOT EXISTS hello_mp
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

然后执行前面提供的 `schema.sql` 建表语句。

### 4.3 启动应用

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功
# 2026-08-22 10:00:00 [main] INFO  c.h.HelloMpApplication - Started HelloMpApplication in 2.345 seconds
```

### 4.4 测试 API 接口

启动应用后，使用 curl 或 Postman 测试各个接口。

**1. 创建用户：**

```bash
# POST 请求：创建新用户
curl -X POST "http://localhost:8080/api/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "nickname": "张三",
    "email": "zhangsan@example.com",
    "phone": "13800138000",
    "gender": 1,
    "status": 1
  }'

# 期望输出：创建成功的用户 JSON（包含自动生成的 ID 和时间）
# {
#   "id": 1823456789012345678,
#   "username": "zhangsan",
#   "nickname": "张三",
#   "email": "zhangsan@example.com",
#   "phone": "13800138000",
#   "gender": 1,
#   "status": 1,
#   "createTime": "2026-08-22T10:00:00",
#   "updateTime": "2026-08-22T10:00:00",
#   "delFlag": "0",
#   "version": 0
# }
```

**2. 查询所有用户：**

```bash
# GET 请求：查询所有用户
curl "http://localhost:8080/api/users"

# 期望输出：用户列表 JSON 数组
```

**3. 分页查询用户：**

```bash
# GET 请求：分页查询，第 1 页，每页 5 条
curl "http://localhost:8080/api/users/page?current=1&size=5"

# 期望输出：
# {
#   "records": [ ... ],  # 当前页的用户列表
#   "total": 3,          # 总记录数
#   "size": 5,           # 每页大小
#   "current": 1,        # 当前页码
#   "pages": 1,          # 总页数
#   "hasNext": false     # 是否有下一页
# }
```

**4. 条件搜索用户：**

```bash
# GET 请求：搜索用户名包含 "zhang" 且状态为 1 的用户
curl "http://localhost:8080/api/users/search?keyword=zhang&status=1"

# 期望输出：符合条件的用户列表
```

**5. 更新用户：**

```bash
# PUT 请求：更新用户（将 ID 为 123456 的用户昵称改为 "张三（已更新）"）
curl -X PUT "http://localhost:8080/api/users/123456" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "张三（已更新）",
    "email": "newemail@example.com"
  }'

# 期望输出：更新后的用户信息
```

**6. 删除用户：**

```bash
# DELETE 请求：逻辑删除用户
curl -X DELETE "http://localhost:8080/api/users/123456"

# 期望输出：204 No Content（删除成功）
# 再次查询该用户时，返回 404 Not Found
```

### 4.5 运行单元测试

```bash
# 运行所有测试（使用 H2 内存数据库，无需 MySQL）
mvn test

# 期望输出：
# [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实使用

### 5.1 核心文件对照表

| 本文示例 | ruoyi-ai 项目位置 | 说明 |
|---------|-------------------|------|
| `UserMapper.java` | `ruoyi-common/mybatis` 模块 | 公共 Mapper 基础配置 |
| `User.java` | `ruoyi-system/src/main/java/com/ruoyi/system/domain/SysUser.java` | 用户实体类 |
| `MybatisPlusConfig.java` | `ruoyi-common/mybatis/src/main/java/com/ruoyi/common/mybatis/config/MybatisPlusConfig.java` | MyBatis-Plus 配置类 |
| `UserService.java` | `ruoyi-system/src/main/java/com/ruoyi/system/service/ISysUserService.java` | 用户服务接口 |
| `application.yml` | `ruoyi-admin/src/main/resources/application.yml` | 应用配置 |

### 5.2 ruoyi-ai 中的实际增强

ruoyi-ai 项目中的 MyBatis-Plus 使用在生产环境中做了以下增强：

1. **多数据源支持**：通过 baomidou 的 Dynamic-Datasource 组件，支持主库、从库、业务库的读写分离和数据源切换
2. **自动填充公共字段**：所有实体类统一使用 `MetaObjectHandler` 自动填充 createTime、updateTime、createBy、updateBy 等公共字段
3. **代码生成器**：使用 MyBatis-Plus 的 AutoGenerator 根据数据库表结构自动生成 Entity、Mapper、Service、Controller 全套代码
4. **分页封装**：对 `Page` 对象进行封装，统一分页请求和响应格式，方便前端使用
5. **数据权限过滤**：结合 `@TableField` 注解和自定义拦截器，实现数据级别的权限控制
6. **乐观锁防止并发冲突**：关键业务表（如订单、库存）使用 `@Version` 乐观锁，防止并发写操作导致的数据不一致

### 5.3 ruoyi-ai 中的实际代码示例

下面是 ruoyi-ai 项目中实际使用的 MyBatis-Plus 相关代码：

```java
/**
 * ruoyi-ai 项目中 SysUser 实体类的实际定义。
 *
 * 对比本文示例，实际项目中的实体类更完整：
 * - 包含更多公共字段（createBy、updateBy、remark 等）
 * - 使用 @Schema 注解生成 API 文档
 * - 使用 @NotNull 等校验注解
 */
@Data
@TableName("sys_user")
public class SysUser {

    /**
     * 用户 ID：雪花算法生成。
     * ruoyi-ai 中所有实体类统一使用 ASSIGN_ID 策略，
     * 保证分布式环境下的 ID 全局唯一且趋势递增。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;  // 注意：ruoyi-ai 中主键名为 userId 而非 id

    /**
     * 部门 ID：关联 sys_dept 表。
     * ruoyi-ai 使用多表关联时，通过手动编写 XML 实现 JOIN 查询。
     */
    private Long deptId;

    /**
     * 用户名：登录账号。
     */
    private String userName;

    /**
     * 昵称：显示名称。
     */
    private String nickName;

    /**
     * 用户类型：00-系统用户 01-注册用户。
     */
    private String userType;

    /**
     * 邮箱地址。
     */
    private String email;

    /**
     * 手机号码。
     */
    private String phonenumber;

    /**
     * 用户性别：0-男 1-女 2-未知。
     */
    private String sex;

    /**
     * 头像地址。
     */
    private String avatar;

    /**
     * 密码：BCrypt 加密存储。
     */
    private String password;

    /**
     * 帐号状态：0-正常 1-停用。
     */
    private String status;

    /**
     * 删除标志：0-正常 2-删除。
     * 注意：ruoyi-ai 中逻辑删除使用 0/2 而非 0/1。
     */
    @TableLogic
    private String delFlag;

    /**
     * 最后登录 IP。
     */
    private String loginIp;

    /**
     * 最后登录时间。
     */
    private LocalDateTime loginDate;

    /**
     * 创建者：自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间：自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新者：自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间：自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 备注。
     */
    private String remark;
}
```

### 5.4 ruoyi-ai 中的分页查询实践

```java
/**
 * ruoyi-ai 项目中的分页查询实践。
 *
 * ruoyi-ai 对 MyBatis-Plus 的分页进行了统一封装，
 * 提供了 PageUtils 工具类，统一分页请求和响应格式。
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    /**
     * 分页查询用户列表 —— ruoyi-ai 实际使用的分页方式。
     *
     * 流程：
     * 1. 前端传入 pageNum、pageSize 等分页参数
     * 2. 使用 PageUtils 的 startPage() 方法自动创建 Page 对象
     * 3. 后续的第一次查询自动被分页插件拦截
     * 4. 使用 PageUtils 的 getDataTable() 方法封装响应
     */
    @Override
    public PageResult selectUserPage(SysUser user) {
        // 分页查询：调用 ServiceImpl 的 page 方法
        // LambdaQueryWrapper 构建查询条件
        Page<SysUser> page = page(
                new Page<>(PageUtils.getPageNum(), PageUtils.getPageSize()),
                new LambdaQueryWrapper<SysUser>()
                        // 按用户名模糊查询
                        .like(StringUtils.isNotBlank(user.getUserName()),
                                SysUser::getUserName, user.getUserName())
                        // 按状态查询
                        .eq(StringUtils.isNotBlank(user.getStatus()),
                                SysUser::getStatus, user.getStatus())
                        // 按创建时间排序
                        .orderByDesc(SysUser::getCreateTime)
        );

        // 封装分页结果
        return PageUtils.getDataTable(page);
    }
}
```

### 5.5 从示例到项目的进阶之路

1. **代码生成器集成**：学习 MyBatis-Plus 的 AutoGenerator，根据数据库表一键生成 Entity、Mapper、Service、Controller 全套代码
2. **多数据源动态切换**：结合 Dynamic-Datasource 组件，实现读写分离和业务库隔离
3. **数据权限过滤**：通过自定义拦截器，实现数据级别的权限控制（如：部门经理只能查看本部门的数据）
4. **性能优化**：学习 MyBatis-Plus 的 SQL 日志分析、慢 SQL 监控、批量操作优化等性能调优手段

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：MyBatis-Plus 和 MyBatis 有什么区别？为什么选择 MyBatis-Plus 而不是 JPA？

**考察点：** 面试官想考察候选人对 ORM 框架的选型能力和对比分析能力，是否理解不同框架的适用场景。

**回答框架：**

- **背景**：MyBatis-Plus 是 MyBatis 的增强工具，**只做增强不做改变**，不会取代 MyBatis，而是在其基础上提供通用 CRUD 能力。JPA（Java Persistence API）是 Java 官方标准的 ORM 规范，Hibernate 是其最流行的实现。

- **MyBatis vs MyBatis-Plus 的区别：**

  | 维度 | MyBatis | MyBatis-Plus |
  |------|---------|-------------|
  | **CRUD** | 每个方法都要写 XML 或注解 | BaseMapper 内置 20+ 通用方法，零 SQL |
  | **条件查询** | 手写动态 SQL（<if>、<where> 标签） | LambdaQueryWrapper 链式调用，类型安全 |
  | **分页** | 手写 LIMIT + COUNT 语句 | 分页插件自动拦截，自动生成 COUNT 和 LIMIT |
  | **逻辑删除** | 手写 UPDATE SET del_flag = 1 | @TableLogic 一行注解搞定 |
  | **乐观锁** | 手写 SET version = version + 1 | @Version 自动版本管理 |
  | **代码生成** | 需 MyBatis Generator 或第三方工具 | AutoGenerator 一键生成全套代码 |
  | **复杂查询** | 原生支持（手写 XML） | 同样支持，不限制 MyBatis 能力 |

- **为什么选 MyBatis-Plus 而不是 JPA：**

  - **SQL 可控性**：MyBatis-Plus 本质上还是 MyBatis，SQL 在手、心里有底。JPA 的自动映射更"黑盒"，复杂查询生成的 SQL 可能不符合预期，且难以调优
  - **学习曲线**：MyBatis-Plus 的 API 设计直观，Java 开发者上手快。JPA 的 HQL、JPQL、实体关系映射（@OneToMany、@ManyToMany）有较高的学习成本
  - **复杂查询**：MyBatis-Plus 支持手写 XML 应对复杂 JOIN 和子查询，与简单 CRUD 的零 SQL 配合使用。JPA 的 Criteria API 写复杂查询非常繁琐，Native SQL 又失去了 ORM 的优势
  - **性能调优**：MyBatis-Plus 的 SQL 是开发者手写的，性能瓶颈一目了然。JPA 自动生成的 SQL 可能需要通过 show-sql 分析后才能优化

- **什么场景选 JPA：** 如果项目以简单的 CRUD 为主，几乎没有复杂查询，团队对 Hibernate 熟悉，且需要多数据库兼容（如同时支持 MySQL 和 PostgreSQL），JPA 是更好的选择。

- **深度**：选型不是非此即彼。Spring Data JPA + MyBatis-Plus 可以共存——JPA 管理实体关系和简单 CRUD，MyBatis-Plus 处理复杂查询，两者通过同一个数据源协同工作。但这样会增加维护成本，通常建议项目选其一为主。

### Q2：LambdaQueryWrapper 的实现原理是什么？为什么能保证类型安全？

**考察点：** 面试官想考察候选人对 MyBatis-Plus 底层实现机制的理解，以及对 Java 反射、Lambda 表达式的掌握程度。

**回答框架：**

- **背景**：LambdaQueryWrapper 是 MyBatis-Plus 的类型安全条件构造器，通过 `User::getUsername` 这样的方法引用而不是字符串 `"username"` 来引用字段。它解决了传统 MyBatis 条件查询中字段名拼写错误只能在运行时发现的问题。

- **原理（分步解释）：**

  1. **Lambda 表达式序列化**：`User::getUsername` 是一个方法引用，在 Java 中，如果方法引用指向的接口是 `Serializable` 的，JVM 会生成一个 `SerializedLambda` 对象。MyBatis-Plus 的 `SFunction` 接口继承了 `Serializable` 和 `Function<T, R>`。

  2. **解析 Lambda 元信息**：MyBatis-Plus 通过反射获取 `SerializedLambda` 对象，从中提取：
     - `implMethodName`：方法名，如 `getUsername`
     - `implClass`：所属类，如 `User`

  3. **方法名转字段名**：将 `getUsername` 这样的 getter 方法名转换为字段名：
     - 去掉 `get` 前缀：`Username`
     - 首字母小写：`username`
     - 驼峰转下划线（可选）：`username`

- **为什么能保证类型安全：**

  - **编译期检查**：`User::getUsername` 在编译时就会检查 `User` 类是否存在 `getUsername` 方法，如果字段被删除或改名，编译直接报错
  - **IDE 自动补全**：IDE 可以为方法引用提供代码补全，不需要记住字段名的字符串
  - **重构友好**：使用 IntelliJ IDEA 的重构功能重命名字段时，`User::getUsername` 会自动更新，而字符串 `"username"` 不会

- **关键源码（简化版）：**

  ```java
  /**
   * LambdaQueryWrapper 的核心解析逻辑 —— 简化版。
   *
   * 实际源码在 com.baomidou.mybatisplus.core.toolkit.LambdaUtils 中。
   * 这里展示最核心的 Lambda 解析逻辑。
   */
  public class LambdaQueryWrapper<T> {
  
      /**
       * 添加等于条件。
       *
       * @param column 方法引用，如 User::getUsername
       * @param value  条件值
       * @return 当前 Wrapper 对象（链式调用）
       */
      public LambdaQueryWrapper<T> eq(SFunction<T, ?> column, Object value) {
          // 1. 使用方法引用对象，而不是字符串
          // 2. 在方法内部解析 Lambda，获取字段名
          String fieldName = LambdaUtils.resolveFieldName(column);
          // 3. 将条件添加到 SQL 中
          // 实际存储为：column = value
          return this;
      }
  }
  
  /**
   * Lambda 解析工具类 —— 核心解析逻辑。
   * 将 User::getUsername 这样的方法引用解析为字段名。
   */
  class LambdaUtils {
  
      /**
       * 解析 Lambda 表达式，获取字段名。
       *
       * @param func 方法引用对象
       * @return 字段名，如 "username"
       */
      static String resolveFieldName(SFunction<?, ?> func) {
          // 1. 获取 SerializedLambda（需要写操作）
          SerializedLambda lambda = getSerializedLambda(func);
          
          // 2. 从 SerializedLambda 中获取方法名
          // 如 User::getUsername 的方法名是 "getUsername"
          String methodName = lambda.getImplMethodName();
          
          // 3. 方法名转字段名
          // 去掉 "get" 前缀，首字母小写
          // "getUsername" -> "username"
          if (methodName.startsWith("get")) {
              // 去掉 get，取剩余部分，将首字母转为小写
              return Introspector.decapitalize(methodName.substring(3));
          } else if (methodName.startsWith("is")) {
              // boolean 类型的 getter 以 is 开头
              return Introspector.decapitalize(methodName.substring(2));
          }
          
          return methodName;
      }
  }
  ```

- **扩展**：同样的原理也用于 MyBatis-Plus 的 `LambdaUpdateWrapper`（更新条件构造器）和 `LambdaQueryChainWrapper`（链式查询）。这种设计模式被称为"类型安全的 DSL（Domain Specific Language）"。

### Q3：MyBatis-Plus 分页插件的实现原理是什么？如何自定义分页？

**考察点：** 面试官想考察候选人对 MyBatis 插件机制和分页实现原理的理解。

**回答框架：**

- **背景**：MyBatis-Plus 的分页插件 `PaginationInnerInterceptor` 是 MyBatis 的拦截器，通过 MyBatis 的插件机制拦截 SQL 执行，自动添加 COUNT 查询和 LIMIT 子句。

- **原理（四步流程）：**

  1. **拦截 Executor.query() 方法**：MyBatis 的插件机制允许拦截 `Executor` 接口的 `query` 方法。分页插件检测参数中是否有 `Page` 对象，如果有，则进入分页流程。

  2. **执行 COUNT 查询**：插件将原始 SQL 包装为 COUNT 查询，执行 `SELECT COUNT(*) FROM (原始 SQL) AS total`。这一步是为了获取总记录数，用于计算总页数。

  3. **追加 LIMIT 子句**：根据 `Page` 对象中的 `current` 和 `size` 参数，计算偏移量 `offset = (current - 1) * size`，在原始 SQL 后追加 `LIMIT offset, size`。

  4. **封装结果**：将 COUNT 查询结果和分页数据封装到 `Page` 对象中，设置 `total`、`pages`、`hasNext` 等属性。

- **如何自定义分页：**

  **方式一：自定义分页参数**
  通过继承 `Page` 类或实现 `IPage` 接口，可以添加自定义的分页参数：

  ```java
  /**
   * 自定义分页对象 —— 扩展 MyBatis-Plus 的 Page 类。
   *
   * 可以添加自定义参数，如排序字段、统计字段等。
   */
  public class MyPage<T> extends Page<T> {
  
      /**
       * 排序字段：前端传入的排序参数。
       * 如 "createTime" 表示按创建时间排序。
       */
      private String orderByColumn;
  
      /**
       * 排序方向："asc" 或 "desc"。
       */
      private String orderDirection;
  
      public MyPage(long current, long size) {
          super(current, size);
      }
  
      public String getOrderByColumn() {
          return orderByColumn;
      }
  
      public void setOrderByColumn(String orderByColumn) {
          this.orderByColumn = orderByColumn;
      }
  
      public String getOrderDirection() {
          return orderDirection;
      }
  
      public void setOrderDirection(String orderByColumn) {
          this.orderDirection = orderDirection;
      }
  }
  ```

  **方式二：自定义分页方言**
  分页插件支持多种数据库方言，通过 `DbType` 参数指定：

  ```java
  // 支持多种数据库的分页语法
  interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));     // MySQL: LIMIT offset, size
  interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL)); // PostgreSQL: LIMIT size OFFSET offset
  interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.ORACLE));     // Oracle: ROWNUM
  interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQL_SERVER));  // SQL Server: OFFSET...FETCH NEXT
  ```

  如果需要支持自定义数据库方言，可以实现 `IDialect` 接口：

  ```java
  /**
   * 自定义分页方言 —— 实现 IDialect 接口。
   *
   * 适用于 MyBatis-Plus 不支持的数据库类型。
   * 需要实现两个方法：
   * - buildPaginationSql：生成分页 SQL
   * - getDbType：返回数据库类型
   */
  public class CustomDialect implements IDialect {
  
      @Override
      public DialectModel buildPaginationSql(String originalSql, long offset, long limit) {
          // 自定义分页 SQL 生成逻辑
          // 例如：SELECT * FROM (原始 SQL) WHERE ROWNUM BETWEEN offset AND offset + limit
          String paginationSql = originalSql + " LIMIT " + offset + ", " + limit;
          return new DialectModel(paginationSql, offset, limit);
      }
  }
  ```

- **分页插件的注意事项：**

  1. **COUNT 查询性能**：对于大表，COUNT 查询可能很慢。分页插件默认会优化 COUNT 查询（去掉 ORDER BY 子句），但如果表数据量极大（千万级），建议使用"假分页"或"游标分页"替代传统分页
  2. **Page 对象必须是第一个参数**：分页插件通过检测参数类型来识别分页请求，`Page` 对象必须作为 Mapper 方法的第一个参数
  3. **不支持 UNION 查询的分页**：UNION 查询的 COUNT 统计可能不准确，建议手动处理 UNION 查询的分页
  4. **多表 JOIN 的 COUNT 优化**：多表 JOIN 时，COUNT 查询可能比主查询还慢，可以通过 `optimizeCountSql` 参数控制是否优化 COUNT 查询

---

## 七、总结

本文从 JDBC 的痛点出发，到 MyBatis 的改进，再到 MyBatis-Plus 的进一步简化，完整展示了 Java 数据库操作技术的演进历程。通过一个完整的可运行示例项目，从零搭建了 Spring Boot + MyBatis-Plus 的 CRUD 应用，涵盖以下核心知识点：

1. **JDBC 的痛点**：样板代码多、资源管理繁琐、SQL 与代码耦合、结果集手动映射——理解了这些痛点，才能深刻理解 ORM 框架的价值
2. **MyBatis-Plus 的四大核心概念**：
   - **BaseMapper**：通用 CRUD 接口，零 SQL 完成增删改查
   - **@TableName/@TableId**：实体类与数据库表的映射注解
   - **LambdaQueryWrapper**：类型安全的条件构造器
   - **分页插件**：自动拦截分页查询，生成 COUNT + LIMIT
3. **完整项目搭建**：从 pom.xml、application.yml、实体类、Mapper、Service、Controller 到测试类的完整代码
4. **ruoyi-ai 项目对照**：了解 MyBatis-Plus 在真实项目中的使用方式和增强点
5. **面试题**：MyBatis-Plus vs MyBatis vs JPA、LambdaQueryWrapper 原理、分页插件原理三道高频面试题

在下一篇文章中，我们将深入分析 Redis 和 Redisson 在 ruoyi-ai 项目中的应用，学习如何通过 Redis 实现缓存、分布式锁和限流等功能。

---

## 参考资料

- [MyBatis-Plus 官方文档](https://baomidou.com/) — 自动映射、分页插件、代码生成器完整使用指南
- [MyBatis-Plus GitHub 仓库](https://github.com/baomidou/mybatis-plus) — 源码与 Issue
- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/) — 数据源配置、事务管理
- [MySQL 8.0 官方文档](https://dev.mysql.com/doc/refman/8.0/en/) — 索引优化、SQL 性能调优
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — `ruoyi-common/mybatis` 模块源码