# Spring Data JPA 面试题大全

## 📚 知识体系

```
Spring Data JPA 核心
├── Repository 体系
│   ├── CrudRepository
│   ├── JpaRepository
│   ├── PagingAndSortingRepository
│   └── 自定义 Repository
├── 查询方式
│   ├── 方法名推导（findByName）
│   ├── @Query（JPQL/SQL）
│   ├── 查询方法（findBy/readBy/getBy）
│   └── 分页 Pageable
├── 实体关系
│   ├── @OneToOne / @OneToMany / @ManyToOne / @ManyToMany
│   ├── 懒加载/急加载（FetchType）
│   ├── 级联操作（CascadeType）
│   └── 索引
├── 事务管理
│   ├── @Transactional
│   ├── 传播行为
│   └── 隔离级别
└── 审计功能
    ├── @CreatedDate
    ├── @LastModifiedDate
    └── @CreatedBy / @LastModifiedBy
```

---

## 🎯 Level 1：基础题

### 1. JPA 和 MyBatis 的区别？如何选择？
**答案**：

| 特性 | JPA / Hibernate | MyBatis |
|------|-----------------|---------|
| 定位 | ORM 框架 | SQL Mapper |
| 灵活度 | 低（自动生成） | 高（手写 SQL） |
| 开发效率 | 高（CRUD 零代码） | 中（需写 SQL） |
| 学习成本 | 高（关系映射复杂） | 低（直接写 SQL） |
| 复杂查询 | 难（JPQL/Criteria） | 方便（SQL 直接） |
| 优化控制 | 弱（自动生成） | 强（完全控制） |
| 性能 | 中（N+1 问题） | 高（自定义 SQL） |

**选择建议**：
- 简单 CRUD + 标准查询 → **JPA**
- 复杂 SQL + 性能敏感 → **MyBatis**
- 真正生产：**JPA + MyBatis 并存**（JPA 做常规 CRUD，MyBatis 做复杂统计）

### 2. JPA 实体关系映射的注解？
**答案**：

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)  // 多对一（默认立即加载，改为懒加载）
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;
}
```

---

## 🎯 Level 2：进阶题

### 3. 什么是 N+1 问题？如何解决？
**答案**：
**N+1 问题**：查询 N 条主记录时，额外发出 N 条查询关联记录。

**场景**：
```java
// 1 条查询 Order + N 条查询 User（每个 Order 查一次 User）
List<Order> orders = orderRepository.findAll();
for (Order order : orders) {
    System.out.println(order.getUser().getName());  // 触发 N 条查询
}
```

**解决方案**：

| 方案 | 方式 | 效果 |
|------|------|------|
| **JOIN FETCH** | JPQL | 一条 SQL 全部查出 |
| **@EntityGraph** | 注解 | 类似 JOIN FETCH |
| **@BatchSize** | 注解 | 批量加载（N 个一批） |
| **懒加载** | 按需加载 | 减少查询次数 |

```java
// 方案一：JOIN FETCH
@Query("SELECT o FROM Order o JOIN FETCH o.user")
List<Order> findAllWithUser();

// 方案二：@EntityGraph
@EntityGraph(attributePaths = {"user"})
@Query("SELECT o FROM Order o")
List<Order> findAllWithUser();
```

### 4. @Transactional 的传播行为？
**答案**：

| 传播行为 | 说明 |
|----------|------|
| REQUIRED（默认） | 有则用，无则新建 |
| REQUIRES_NEW | 挂起当前，新建事务 |
| NESTED | 嵌套事务（Savepoint） |
| SUPPORTS | 有则用，无则非事务 |
| NOT_SUPPORTED | 非事务运行 |
| MANDATORY | 必须存在事务 |
| NEVER | 必须没有事务 |

---

## 🎯 Level 3：高级题

### 5. 乐观锁和悲观锁在 JPA 中如何实现？
**答案**：

**乐观锁（@Version）**：
```java
@Entity
public class Product {
    @Id
    private Long id;

    @Version
    private Integer version;  // 版本号，更新时自动 +1

    private Integer stock;
}
```
- 更新时：`WHERE id = ? AND version = ?`
- 版本不匹配抛 `OptimisticLockException`

**悲观锁（@Lock）**：
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)  // FOR UPDATE
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

---

## 📖 学习资源

### 推荐项目
- [Spring Data JPA 官方文档](https://spring.io/projects/spring-data-jpa)
- [JPA + MyBatis 共存示例](https://github.com/spring-projects/spring-data-examples)

### 最佳实践
1. 关联关系默认 LAZY（懒加载）
2. 复杂查询用 @Query 写 JPQL/SQL
3. 避免 N+1：JOIN FETCH / @EntityGraph
4. 分页查询用 Pageable
5. 批量操作用 saveAll 而非逐条 save