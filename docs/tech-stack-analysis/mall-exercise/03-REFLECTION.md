# 03 · 反射实战：动态 SQL 生成、验证框架、数据权限、Bean 转换

> 反射是框架的基石。看 mall-exercise 如何用反射实现 MyBatis-Plus 风格的 SQL 构建、通用验证框架、数据权限代理和 Bean 转换器。
>
> **对应项目：** `mall-exercise/src/main/java/itcast/cloud/mall/exercise/reflection/`

---

## 一、基础概念

### 1.1 四个反射练习

| 练习类 | 解决的问题 | 核心 API | 面试价值 |
|--------|-----------|---------|---------|
| **DynamicSqlBuilder** | 从实体类注解动态生成 SQL | `@TableName`, `@TableField`, `Field` | ★★★★★ |
| **ValidationFramework** | 自定义注解实现通用验证 | `@NotNull`, `@Length`, `@Range`, `@Pattern` | ★★★★★ |
| **DataPermissionProxy** | 动态代理实现数据权限 | `InvocationHandler`, `Proxy` | ★★★★ |
| **BeanConverter** | 反射实现对象属性拷贝 | `Field.set/get`, 类型转换 | ★★★★ |

---

## 二、进阶机制

### 2.1 DynamicSqlBuilder —— 反射读取注解动态生成 SQL

```java
public class DynamicSqlBuilder {
    // 读取 @TableName 注解获取表名
    public static String getTableName(Class<?> entityClass) {
        TableName tableName = entityClass.getAnnotation(TableName.class);
        if (tableName != null && !tableName.value().isEmpty()) {
            return tableName.value();
        }
        return camelToUnderscore(entityClass.getSimpleName()); // 默认驼峰转下划线
    }

    // 反射读取所有字段，生成 SELECT 列名
    public static List<String> getColumnNames(Class<?> entityClass) {
        return Arrays.stream(entityClass.getDeclaredFields())
            .filter(f -> !"serialVersionUID".equals(f.getName()))
            .map(DynamicSqlBuilder::getColumnName)
            .collect(Collectors.toList());
    }

    // 生成 INSERT SQL
    public static String buildInsertSql(Object entity) {
        // 表名 + 字段名 + 字段值 → INSERT INTO table (col1, col2) VALUES (?, ?)
    }

    // 生成 UPDATE SQL
    public static String buildUpdateSql(Object entity) {
        // 表名 + 字段名=值 → UPDATE table SET col1=?, col2=? WHERE id=?
    }

    // 生成 SELECT SQL
    public static String buildSelectSql(Class<?> entityClass, Object id) {
        // SELECT * FROM table WHERE id = ?
    }
}
```

**这就是 MyBatis-Plus 框架的底层原理：** 通过反射读取实体类注解，动态生成 SQL 语句。

### 2.2 ValidationFramework —— 自定义注解 + 反射实现通用验证

```java
public class ValidationFramework {
    // 定义验证注解
    @Retention(RUNTIME) @Target(FIELD) public @interface NotNull {
        String message() default "字段不能为空";
    }
    @Retention(RUNTIME) @Target(FIELD) public @interface Length {
        int min() default 0; int max() default Integer.MAX_VALUE;
        String message() default "字段长度不符合要求";
    }
    @Retention(RUNTIME) @Target(FIELD) public @interface Range {
        double min() default Double.MIN_VALUE; double max() default Double.MAX_VALUE;
        String message() default "数值不在有效范围内";
    }
    @Retention(RUNTIME) @Target(FIELD) public @interface Pattern {
        String regex(); String message() default "格式不正确";
    }

    // 核心验证方法
    public ValidationResult validate(Object obj) {
        ValidationResult result = new ValidationResult();
        Field[] fields = obj.getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(obj);

            // 检查 @NotNull
            NotNull notNull = field.getAnnotation(NotNull.class);
            if (notNull != null && value == null) {
                result.addError(notNull.message());
            }

            // 检查 @Length
            Length length = field.getAnnotation(Length.class);
            if (length != null && value instanceof String) {
                int len = ((String) value).length();
                if (len < length.min() || len > length.max()) {
                    result.addError(length.message());
                }
            }

            // 检查 @Range、@Pattern...
        }
        return result;
    }
}
```

**这就是 Spring Validation 框架的底层原理。**

### 2.3 DataPermissionProxy —— 动态代理实现数据权限

```java
public class DataPermissionProxy implements InvocationHandler {
    private final Object target;
    private final String userId;

    public static <T> T createProxy(T target, String userId) {
        return (T) Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            target.getClass().getInterfaces(),
            new DataPermissionProxy(target, userId)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 在方法执行前注入数据权限
        if (method.getName().startsWith("query") || method.getName().startsWith("select")) {
            // 注入 user_id = currentUserId 过滤条件
            args = injectPermissionFilter(args, userId);
        }
        return method.invoke(target, args);
    }
}
```

**这就是 Spring Data JPA / MyBatis 数据权限拦截器的底层原理。**

---

## 三、面试要点

### Q1: DynamicSqlBuilder 和 MyBatis-Plus 的关系是什么？

**回答思路：** DynamicSqlBuilder 是 MyBatis-Plus 底层原理的简化实现。MP 通过反射读取 `@TableName`、`@TableField` 等注解，动态生成 SQL。理解了这个，就能理解 MP 为什么不需要写 XML 映射文件——它通过反射自动推断表名、字段名、主键。

### Q2: 反射有哪些性能问题？怎么优化？

**回答思路：** 反射的主要性能开销在：1) `setAccessible(true)` 安全检查；2) 方法调用从 `invoke` 分发。优化：1) 缓存 `Field` 和 `Method` 对象避免重复反射；2) 使用 `setAccessible(true)` 跳过安全检查；3) 使用 `MethodHandles`（JDK 7+）提升性能；4) 关键路径用字节码增强（CGLIB/ASM）替代反射。

### Q3: 动态代理的两种实现方式是什么？

**回答思路：** JDK Proxy 要求目标类实现接口，通过 `InvocationHandler` 拦截方法调用。CGLIB 通过字节码生成子类，不需要接口。Spring 默认策略：有接口用 JDK Proxy，没有接口用 CGLIB。项目中 `DataPermissionProxy` 使用了 JDK Proxy。

---

> **下一篇：** [04-REDIS-CACHE.md —— Redis 缓存策略实战：Cache-Aside、分布式锁、缓存穿透](./04-REDIS-CACHE.md)
>
> 从理论到实践，看项目如何用 Redis 实现 Cache-Aside 模式、分布式锁防缓存击穿、以及双写一致性。