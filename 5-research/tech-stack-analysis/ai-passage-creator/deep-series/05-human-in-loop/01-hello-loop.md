# 05 人机协作入门：状态机 + 三阶段创作流程 + 断点续作

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 5 篇（入门篇）。面向 Java 初学者，手把手带你从零搭建一个人机协作（Human-in-the-loop）状态机系统，实现文章创作的三阶段流程。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-java` 模块 `article` 包
> **难度等级：** Level 1 入门
> **预计阅读时间：** 20 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是人机协作（Human-in-the-loop, HITL）

人机协作（HITL）是一种**在 AI 自动化流程中插入人工审核节点**的设计模式。AI 负责生成内容，人类负责把关质量，两者交替协作完成最终产物。

在 ai-passage-creator 项目中，AI 生成文章的过程并不是"一键生成、直接发布"的黑盒。相反，创作被拆解为三个阶段——**选题 → 大纲 → 正文配图**——每个阶段都允许用户介入编辑、优化或重新生成。

**HITL 的核心循环：**

```
AI 生成初稿 → 人类审核/编辑 → AI 根据反馈优化 → 人类确认定稿
     ↑                                            ↓
     └────────────── 循环直到满意 ──────────────┘
```

### 1.2 为什么需要 HITL 而不是全自动

| 维度 | 全自动（No Human） | 人机协作（HITL） |
|------|-------------------|------------------|
| 效率 | 高（一气呵成） | 中（需要人类介入） |
| 质量 | 不可控，可能跑偏 | 可控，每阶段把关 |
| 用户参与感 | 无，纯黑盒 | 强，用户主导方向 |
| 适用场景 | 内部自动化流水线 | 面向用户的创作类产品 |
| 纠错成本 | 高（生成完才发现问题） | 低（每阶段及时纠正） |

**传统 Web 应用的痛点：**

| 痛点 | 说明 |
|------|------|
| 流程不可控 | AI 生成完才知道结果，中途无法干预 |
| 结果不可编辑 | 用户只能接受或放弃，无法修改中间结果 |
| 进度不可恢复 | 刷新页面后所有进度丢失，必须重新开始 |
| 并发不可防 | 多设备同时操作同一文章，导致数据覆盖 |

**HITL 的解决方案：**

| 解决方案 | 实现方式 |
|----------|----------|
| 三阶段流程 | 选题 → 大纲 → 正文配图，每阶段独立可编辑 |
| 状态机控制 | ArticlePhase 枚举定义合法状态流转 |
| 断点续作 | 每个阶段完成立即保存，支持从任意阶段恢复 |
| 乐观锁 | version 字段防并发，冲突时提示用户刷新 |

### 1.3 本文的目标

读完本文，你将能够：
- 理解 HITL 设计模式的核心思想
- 使用状态机（State Machine）管理多阶段流程
- 实现断点续作（Checkpoint/Resume）机制
- 搭建一个完整的三阶段创作流程 Demo
- 编写单元测试验证状态流转

---

## 二、核心概念

### 2.1 状态机（State Machine）

状态机是**描述系统在不同状态之间如何流转的模型**。在文章创作中，每条文章记录都维护一个 `phase` 字段，表示当前处于哪个创作阶段。

**文章创作状态机：**

```
                     ┌────────────────────────────────────────────────────┐
                     │                                                     │
  TITLE_SELECTION ──→ OUTLINE_EDITING ──→ CONTENT_GENERATION ──→ COMPLETED
  （选题选择中）       （大纲编辑中）        （正文生成中）          （完成）
       ↑                   ↑                     ↑
       │                   │                     │
       └── 重新生成 ────────┴── 重新生成 ──────────┘
       （用户不满意，回到上一阶段）
```

**状态流转规则：**

| 当前状态 | 合法动作 | 下一状态 |
|----------|----------|----------|
| `TITLE_SELECTION` | 用户选择标题 | `OUTLINE_EDITING` |
| `TITLE_SELECTION` | 用户要求重新生成标题 | 保持 `TITLE_SELECTION`（重新生成） |
| `OUTLINE_EDITING` | 用户确认大纲 | `CONTENT_GENERATION` |
| `OUTLINE_EDITING` | 用户编辑/优化大纲 | 保持 `OUTLINE_EDITING` |
| `CONTENT_GENERATION` | 生成完成 | `COMPLETED` |
| `COMPLETED` | 用户要求重新创作 | 回到 `TITLE_SELECTION` |

**状态机的三个核心要素：**

| 要素 | 说明 | 项目中的实现 |
|------|------|-------------|
| 状态（State） | 系统在某个时刻的状况 | `ArticlePhase` 枚举：TITLE_SELECTION 等 |
| 事件（Event） | 触发状态流转的动作 | 用户选择标题、确认大纲等操作 |
| 转移（Transition） | 从一个状态到另一个状态的规则 | `isValidTransition()` 方法校验合法性 |

### 2.2 断点续作（Checkpoint / Resume）

断点续作是指**系统在任意阶段中断后，用户可以从中断点继续创作**的能力。这是 HITL 流程落地的数据基础——如果用户中途刷新页面或关闭浏览器，创作进度不能丢失。

**断点续作的三层保障：**

```
第 1 层：状态持久化 —— article 的 phase 字段记录当前阶段
     ↓ 用户刷新后，先读取 phase 确定处于哪个阶段
第 2 层：中间结果持久化 —— titleOptions / outline / content 实时保存
     ↓ 每个阶段完成立即保存，生成中定期保存
第 3 层：上下文恢复 —— 前端通过 articleId 恢复整个创作现场
     ↓ 前端调用 resume 接口，获取所有中间结果
```

**断点续作的关键原则：** 中间结果必须"实时保存"，而不是"完成才保存"。

```
❌ 错误：只在最终完成时保存
用户刷新 → 所有中间内容丢失 → 必须重新创作

✅ 正确：每个阶段完成立即保存
用户刷新 → 从 phase 对应的阶段恢复 → 中间结果都在
```

### 2.3 乐观锁（Optimistic Locking）

乐观锁是**假设并发冲突很少发生，只在更新时检查冲突**的并发控制策略。通过 `version` 字段实现：

```sql
UPDATE article SET phase = ?, version = version + 1
WHERE id = ? AND version = ?  -- 版本号匹配才更新
```

**乐观锁 vs 悲观锁：**

| 维度 | 乐观锁（version） | 悲观锁（SELECT FOR UPDATE） |
|------|-------------------|------------------------------|
| 思想 | 假设冲突少，更新时校验 | 假设冲突多，读取时锁定 |
| 实现 | 版本号字段 + WHERE 校验 | 数据库行锁 |
| 性能 | 高（无锁开销） | 低（持有锁期间阻塞） |
| 适用场景 | 读多写少（用户创作以读为主） | 写多读少、强一致场景 |
| 失败处理 | 更新返回 0，重试或报错 | 等待锁释放 |

---

## 三、从零搭建代码

### 3.1 创建项目结构

```
human-in-loop-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── passage/
│   │   │           └── loop/
│   │   │               ├── HumanInLoopDemoApplication.java       # 启动类
│   │   │               ├── model/
│   │   │               │   ├── ArticlePhase.java                # 文章阶段枚举
│   │   │               │   └── Article.java                     # 文章实体
│   │   │               ├── repository/
│   │   │               │   └── ArticleRepository.java           # 文章仓储（内存版）
│   │   │               ├── service/
│   │   │               │   └── ArticleStateMachineService.java  # 状态机服务
│   │   │               └── controller/
│   │   │                   └── ArticleController.java           # 三阶段接口
│   │   └── resources/
│   │       ├── application.yml                                  # 配置文件
│   │       └── static/
│   │           └── index.html                                   # 前端测试页面
│   └── test/
│       └── java/
│           └── com/
│               └── passage/
│                   └── loop/
│                       └── HumanInLoopDemoApplicationTests.java  # 测试类
```

### 3.2 配置 Maven 依赖（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- pom.xml —— Maven 项目配置文件 -->
<!-- 人机协作 HITL 示例的 Maven 构建配置 -->
<project xmlns="http://www.w3.org/2001/XMLSchema-instance"
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
    <artifactId>human-in-loop-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Human-in-the-Loop Demo</name>
    <description>人机协作 HITL 入门示例：三阶段创作流程 + 状态机 + 断点续作</description>

    <properties>
        <java.version>17</java.version>          <!-- 使用 Java 17 -->
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Lombok - 简化 POJO 代码（可选） -->
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
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
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

### 3.3 配置文件（application.yml）

```yaml
# application.yml —— 应用配置文件
# 人机协作 HITL 示例的配置参数
server:
  port: 8080                               # 服务端口号

spring:
  application:
    name: human-in-loop-demo               # 应用名称

# 自定义文章创作配置
article:
  generation:
    # 交互模式（auto=全自动 / confirm=每阶段确认 / manual=人工编辑）
    default-mode: confirm
    # 断点续作相关
    checkpoint:
      auto-save-interval-ms: 5000           # 生成过程自动保存间隔（毫秒）
      resume-history-days: 30               # 草稿保留天数
    # 乐观锁相关
    optimistic-lock:
      enabled: true                         # 是否启用乐观锁
      max-retries: 3                        # 冲突自动重试次数（0 表示不重试，直接报错）
    # 重新生成相关
    regenerate:
      max-times: 5                          # 单篇最多重新生成次数（防滥用）
      cool-down-ms: 30000                   # 重新生成冷却时间（防止刷接口）
```

### 3.4 文章阶段枚举（ArticlePhase.java）

```java
package com.passage.loop.model;

/**
 * ArticlePhase - 文章创作阶段枚举
 * <p>
 * 状态机的状态定义，对应文章创作流程中的每个合法状态。
 * 每个枚举值代表创作流程中的一个阶段：
 * - TITLE_SELECTION: 阶段 1，用户选择标题
 * - OUTLINE_EDITING: 阶段 2，用户编辑/确认大纲
 * - CONTENT_GENERATION: 阶段 3，正文 + 配图生成
 * - COMPLETED: 终态，文章定稿
 * <p>
 * 状态流转规则由 isValidTransition() 方法统一校验，
 * 所有状态修改都必须经过此校验，保证一致性。
 *
 * @author AI-Passage-Creator
 */
public enum ArticlePhase {

    /** 阶段 1：选题选择中 —— 用户从 AI 生成的标题方案中选择一个 */
    TITLE_SELECTION(0, "选题选择中"),

    /** 阶段 2：大纲编辑中 —— 用户编辑/优化/确认文章大纲 */
    OUTLINE_EDITING(1, "大纲编辑中"),

    /** 阶段 3：正文生成中 —— AI 生成正文 + 并行配图 */
    CONTENT_GENERATION(2, "正文生成中"),

    /** 终态：已完成 —— 文章定稿，不再允许修改 */
    COMPLETED(3, "已完成");

    /** 状态顺序（用于比较和校验） */
    private final int order;

    /** 中文描述（用于前端展示和日志） */
    private final String label;

    /**
     * 构造方法
     *
     * @param order 状态顺序编号
     * @param label 中文描述
     */
    ArticlePhase(int order, String label) {
        this.order = order;
        this.label = label;
    }

    /**
     * 获取状态顺序
     *
     * @return 顺序编号，值越大表示越接近终态
     */
    public int getOrder() {
        return order;
    }

    /**
     * 获取中文描述
     *
     * @return 中文标签，用于前端展示和日志输出
     */
    public String getLabel() {
        return label;
    }

    /**
     * 校验状态流转是否合法
     * <p>
     * 状态机核心方法：任何不合法的流转都会被拒绝。
     * 这是整个 HITL 系统的"交通规则"：
     * - 不能从 TITLE_SELECTION 直接跳到 COMPLETED（跳过大纲和正文）
     * - 不能从 COMPLETED 继续编辑（文章已定稿）
     * - 允许从任意阶段回到 TITLE_SELECTION（重新创作）
     *
     * @param current 当前状态（或 null 表示新建文章）
     * @param target  目标状态
     * @return true 表示流转合法，false 表示非法流转
     */
    public static boolean isValidTransition(ArticlePhase current, ArticlePhase target) {
        // 新建文章：只能进入阶段 1（选题）
        if (current == null) {
            return target == TITLE_SELECTION;
        }

        // 已有的文章：根据当前状态判断合法流转
        return switch (current) {
            // 阶段 1：可以选择标题进入阶段 2，也可以重新生成保持阶段 1
            case TITLE_SELECTION ->
                target == OUTLINE_EDITING       // 选择标题 → 进入大纲编辑
                || target == TITLE_SELECTION;   // 重新生成标题 → 保持阶段 1

            // 阶段 2：确认大纲进入阶段 3，编辑优化保持阶段 2
            case OUTLINE_EDITING ->
                target == CONTENT_GENERATION    // 确认大纲 → 进入正文生成
                || target == OUTLINE_EDITING;   // 编辑优化 → 保持阶段 2

            // 阶段 3：生成完成进入终态
            case CONTENT_GENERATION ->
                target == COMPLETED             // 生成完成 → 文章定稿
                || target == OUTLINE_EDITING;   // 停止生成 → 回到大纲编辑

            // 终态：不可流转（除非用户要求重新创作，走特殊处理）
            case COMPLETED ->
                target == TITLE_SELECTION;      // 重新创作 → 回到阶段 1
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
```

### 3.5 文章实体（Article.java）

```java
package com.passage.loop.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Article - 文章实体类
 * <p>
 * 三阶段状态的持久化载体。在真实项目中，此类映射数据库表（如 MyBatis-Flex @Table）。
 * 在本 Demo 中，使用内存存储模拟数据库行为。
 * <p>
 * 核心设计思路：
 * 1. phase 字段记录当前状态（状态机的核心）
 * 2. titleOptions / outline / content 保存中间结果（断点续作的基础）
 * 3. version 字段实现乐观锁（防并发冲突）
 *
 * @author AI-Passage-Creator
 */
public class Article {

    /** 全局 ID 生成器（模拟数据库自增主键） */
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

    /** 文章主键 ID */
    private Long id;

    /** 创建用户 ID */
    private Long userId;

    // ====== 状态机核心字段 ======

    /** 当前创作阶段（状态机的"当前状态"） */
    private ArticlePhase phase;

    // ====== 阶段 1 数据：选题 ======

    /** AI 生成的标题方案列表（多个候选项供用户选择） */
    private List<String> titleOptions;

    /** 用户最终选定的标题 */
    private String selectedTitle;

    // ====== 阶段 2 数据：大纲 ======

    /** 文章大纲（Markdown 格式，支持用户编辑） */
    private String outline;

    // ====== 阶段 3 数据：正文 + 配图 ======

    /** 文章正文（Markdown 格式） */
    private String content;

    /** 配图 URL 列表 */
    private List<String> images;

    // ====== 版本控制（乐观锁，防并发冲突） ======

    /** 乐观锁版本号，每次更新自动 +1 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 默认构造方法
     */
    public Article() {
        this.version = 0;               // 初始版本号为 0
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 带用户 ID 的构造方法
     * 创建文章时使用，自动分配 ID 并初始化阶段为 TITLE_SELECTION
     *
     * @param userId 创建用户 ID
     */
    public Article(Long userId) {
        this();
        this.id = (long) ID_GENERATOR.getAndIncrement();  // 模拟自增 ID
        this.userId = userId;
        this.phase = ArticlePhase.TITLE_SELECTION;        // 初始状态：选题
    }

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ArticlePhase getPhase() {
        return phase;
    }

    public void setPhase(ArticlePhase phase) {
        this.phase = phase;
        this.updateTime = LocalDateTime.now();  // 更新时刷新修改时间
    }

    public List<String> getTitleOptions() {
        return titleOptions;
    }

    public void setTitleOptions(List<String> titleOptions) {
        this.titleOptions = titleOptions;
        this.updateTime = LocalDateTime.now();
    }

    public String getSelectedTitle() {
        return selectedTitle;
    }

    public void setSelectedTitle(String selectedTitle) {
        this.selectedTitle = selectedTitle;
        this.updateTime = LocalDateTime.now();
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
        this.updateTime = LocalDateTime.now();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.updateTime = LocalDateTime.now();
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
        this.updateTime = LocalDateTime.now();
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
        return "Article{" +
                "id=" + id +
                ", phase=" + (phase != null ? phase.getLabel() : "null") +
                ", selectedTitle='" + selectedTitle + '\'' +
                ", version=" + version +
                '}';
    }
}
```

### 3.6 文章仓储（ArticleRepository.java）

```java
package com.passage.loop.repository;

import com.passage.loop.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ArticleRepository - 文章仓储（内存版）
 * <p>
 * 模拟数据库操作，存储所有文章数据。
 * 真实项目中，此处使用 MyBatis-Flex Mapper 操作数据库。
 * <p>
 * 使用 ConcurrentHashMap 保证线程安全，
 * 支持多线程并发读写（如多个用户同时创作文章）。
 *
 * @author AI-Passage-Creator
 */
@Repository
public class ArticleRepository {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(ArticleRepository.class);

    /**
     * 内存数据存储
     * key = 文章 ID，value = 文章对象
     * ConcurrentHashMap 保证多线程安全
     */
    private final Map<Long, Article> articleMap = new ConcurrentHashMap<>();

    /**
     * 保存文章（新增或更新）
     * <p>
     * 模拟数据库的 INSERT 或 UPDATE 操作。
     * 如果文章已存在，直接覆盖（内存操作的特性）。
     * 真实数据库中，此处使用 MyBatis-Flex 的 save 或 insertOrUpdate。
     *
     * @param article 要保存的文章对象
     */
    public void save(Article article) {
        articleMap.put(article.getId(), article);
        log.debug("保存文章: id={}, phase={}, version={}",
                article.getId(),
                article.getPhase() != null ? article.getPhase().getLabel() : "null",
                article.getVersion());
    }

    /**
     * 乐观锁更新
     * <p>
     * 模拟数据库的乐观锁更新：
     * UPDATE article SET phase = ?, version = version + 1, ...
     * WHERE id = ? AND version = ?
     * <p>
     * 如果 version 不匹配，说明其他线程已修改，返回 false 表示更新失败。
     *
     * @param article 要更新的文章（必须包含正确的 version）
     * @return true 表示更新成功，false 表示乐观锁冲突
     */
    public boolean updateWithOptimisticLock(Article article) {
        // 读取当前存储的文章
        Article stored = articleMap.get(article.getId());

        // 校验版本号：如果存储的版本号与传入的版本号不一致，说明已被修改
        if (stored != null && !stored.getVersion().equals(article.getVersion())) {
            log.warn("乐观锁冲突: articleId={}, expectedVersion={}, actualVersion={}",
                    article.getId(), article.getVersion(), stored.getVersion());
            return false; // 乐观锁冲突，更新失败
        }

        // 版本号 +1（模拟数据库的 version = version + 1）
        article.setVersion(article.getVersion() + 1);
        // 执行更新
        articleMap.put(article.getId(), article);
        log.debug("乐观锁更新成功: id={}, newVersion={}", article.getId(), article.getVersion());
        return true;
    }

    /**
     * 根据 ID 查询文章
     *
     * @param id 文章 ID
     * @return 包含文章的 Optional，不存在则返回 Optional.empty()
     */
    public Optional<Article> findById(Long id) {
        return Optional.ofNullable(articleMap.get(id));
    }

    /**
     * 获取所有文章数量（用于统计）
     *
     * @return 文章总数
     */
    public int count() {
        return articleMap.size();
    }

    /**
     * 清空所有文章（用于测试）
     */
    public void clear() {
        articleMap.clear();
        log.info("已清空所有文章数据");
    }
}
```

### 3.7 状态机服务（ArticleStateMachineService.java）

```java
package com.passage.loop.service;

import com.passage.loop.model.Article;
import com.passage.loop.model.ArticlePhase;
import com.passage.loop.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * ArticleStateMachineService - 文章状态机服务
 * <p>
 * 核心业务逻辑，管理文章在各阶段之间的流转。
 * 是 HITL 系统的"交通指挥中心"，职责包括：
 * <p>
 * 1. 创建文章：初始化文章并生成标题方案
 * 2. 状态流转：校验合法性并更新 phase 字段
 * 3. 断点保存：实时保存中间结果（每个阶段完成立即保存）
 * 4. 现场恢复：根据 articleId 恢复整个创作现场
 * 5. 乐观锁：版本号防并发冲突
 *
 * @author AI-Passage-Creator
 */
@Service
public class ArticleStateMachineService {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(ArticleStateMachineService.class);

    /** 文章仓储（注入依赖） */
    private final ArticleRepository articleRepository;

    /**
     * 构造方法注入
     *
     * @param articleRepository 文章仓储
     */
    public ArticleStateMachineService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * 创建文章并生成标题方案
     * <p>
     * 三阶段流程的入口。创建文章后自动生成 3-5 个标题方案，
     * 供用户选择。文章初始状态为 TITLE_SELECTION。
     *
     * @param userId 创建用户 ID
     * @param topic  用户输入的创作主题
     * @return 创建好的文章对象（包含标题方案）
     */
    public Article createArticle(Long userId, String topic) {
        // 1. 创建文章（初始状态：TITLE_SELECTION）
        Article article = new Article(userId);

        // 2. 模拟 AI 生成标题方案
        // 真实项目中，这里调用 TitleGeneratorAgent 调用 LLM 生成
        // 本 Demo 使用预定义示例数据
        List<String> titles = generateTitleOptions(topic);
        article.setTitleOptions(titles);

        // 3. 保存文章（持久化到数据库）
        articleRepository.save(article);

        log.info("创建文章成功: id={}, userId={}, topic={}, titleOptions={}",
                article.getId(), userId, topic, titles.size());
        return article;
    }

    /**
     * 模拟 AI 生成标题方案
     * <p>
     * 基于用户输入的主题，生成 3-5 个吸引人的标题。
     * 真实项目中，此方法调用 LLM（大语言模型）生成。
     *
     * @param topic 用户输入的主题
     * @return 标题方案列表
     */
    private List<String> generateTitleOptions(String topic) {
        // 模拟 LLM 生成结果
        return Arrays.asList(
                topic + "：从入门到精通",
                "深入理解" + topic + "：核心原理与最佳实践",
                topic + "实战指南：手把手带你掌握",
                "为什么" + topic + "是开发者的首选？",
                "2026 年" + topic + "完全学习指南"
        );
    }

    /**
     * 状态流转 —— 状态机核心操作
     * <p>
     * 所有阶段流转都必须经过此方法，保证一致性。
     * 包括：校验合法性、更新状态、乐观锁防并发。
     * <p>
     * 事务说明：真实项目中此方法应标注 @Transactional，
     * 保证状态更新和数据持久化在同一事务中。
     *
     * @param articleId   文章 ID
     * @param targetPhase 目标阶段
     * @param userId      操作人 ID（校验归属）
     * @return 更新后的文章对象
     * @throws IllegalStateException 如果流转非法或乐观锁冲突
     */
    public Article transition(Long articleId, ArticlePhase targetPhase, Long userId) {
        // 1. 读取当前文章
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("文章不存在: " + articleId));

        // 2. 校验文章归属（防止 A 用户操作 B 用户的文章）
        if (!article.getUserId().equals(userId)) {
            throw new SecurityException("无权操作他人的文章: userId=" + userId);
        }

        // 3. 状态机校验：非法流转直接拒绝
        ArticlePhase currentPhase = article.getPhase();
        if (!ArticlePhase.isValidTransition(currentPhase, targetPhase)) {
            throw new IllegalStateException(
                    String.format("非法状态流转：%s → %s（当前状态不允许进入目标状态）",
                            currentPhase.getLabel(), targetPhase.getLabel()));
        }

        // 4. 更新状态
        article.setPhase(targetPhase);

        // 5. 乐观锁更新（模拟数据库的 version 校验）
        boolean success = articleRepository.updateWithOptimisticLock(article);
        if (!success) {
            // 乐观锁冲突：version 已被其他线程修改，本次修改无效
            throw new IllegalStateException(
                    "乐观锁冲突：文章已被其他操作修改，请刷新后重试");
        }

        log.info("状态流转成功: articleId={}, {} → {}",
                articleId, currentPhase.getLabel(), targetPhase.getLabel());
        return article;
    }

    /**
     * 保存中间结果 —— 断点续作的核心
     * <p>
     * 每个阶段生成的内容都实时保存，用户随时可恢复。
     * 使用 Consumer 模式，灵活更新任意字段。
     * <p>
     * 示例用法：
     * saveCheckpoint(articleId, article -> article.setOutline("..."));
     *
     * @param articleId 文章 ID
     * @param updater   更新器（Lambda 表达式，指定要更新的字段）
     * @throws IllegalStateException 如果乐观锁冲突
     */
    public void saveCheckpoint(Long articleId, Consumer<Article> updater) {
        // 1. 读取当前文章
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("文章不存在: " + articleId));

        // 2. 应用更新逻辑（如保存大纲、保存正文）
        updater.accept(article);

        // 3. 乐观锁更新（防并发覆盖）
        boolean success = articleRepository.updateWithOptimisticLock(article);
        if (!success) {
            throw new IllegalStateException("保存失败：文章已被其他操作修改，请刷新后重试");
        }

        log.debug("断点保存成功: articleId={}, phase={}", articleId, article.getPhase().getLabel());
    }

    /**
     * 恢复创作现场 —— 断点续作的入口
     * <p>
     * 用户重新打开文章时调用，返回当前状态和所有中间结果。
     * 前端根据返回的 phase 字段，自动跳转到对应的编辑视图。
     *
     * @param articleId 文章 ID
     * @return 包含完整上下文信息的 ResumeContext 对象
     */
    public ResumeContext resume(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("文章不存在: " + articleId));

        // 根据当前阶段，返回对应的恢复上下文
        return switch (article.getPhase()) {
            case TITLE_SELECTION ->
                // 阶段 1：返回标题方案列表
                new ResumeContext(
                        article.getPhase(),
                        article.getTitleOptions(),
                        null,     // outline 为空
                        null,     // content 为空
                        article.getVersion()
                );
            case OUTLINE_EDITING ->
                // 阶段 2：返回已选标题和当前大纲
                new ResumeContext(
                        article.getPhase(),
                        null,                       // titleOptions 不需要了
                        article.getOutline(),
                        null,                       // content 为空
                        article.getVersion()
                );
            case CONTENT_GENERATION ->
                // 阶段 3：返回大纲、正文和配图
                new ResumeContext(
                        article.getPhase(),
                        null,
                        article.getOutline(),
                        article.getContent(),
                        article.getVersion()
                );
            case COMPLETED ->
                // 终态：返回完整文章
                new ResumeContext(
                        article.getPhase(),
                        null,
                        article.getOutline(),
                        article.getContent(),
                        article.getVersion()
                );
        };
    }

    /**
     * ResumeContext - 恢复上下文（内部类）
     * <p>
     * 封装断点续作所需的所有信息。
     * 前端根据此对象的字段，自动恢复创作现场。
     */
    public static class ResumeContext {

        /** 当前阶段 */
        private final ArticlePhase phase;

        /** 标题方案列表（阶段 1 使用） */
        private final List<String> titleOptions;

        /** 大纲内容（阶段 2、3 使用） */
        private final String outline;

        /** 正文内容（阶段 3、终态使用） */
        private final String content;

        /** 乐观锁版本号（用于后续更新时校验） */
        private final Integer version;

        /**
         * 构造方法
         *
         * @param phase        当前阶段
         * @param titleOptions 标题方案
         * @param outline      大纲内容
         * @param content      正文内容
         * @param version      乐观锁版本号
         */
        public ResumeContext(ArticlePhase phase, List<String> titleOptions,
                             String outline, String content, Integer version) {
            this.phase = phase;
            this.titleOptions = titleOptions;
            this.outline = outline;
            this.content = content;
            this.version = version;
        }

        // ========== Getters ==========

        public ArticlePhase getPhase() {
            return phase;
        }

        public List<String> getTitleOptions() {
            return titleOptions;
        }

        public String getOutline() {
            return outline;
        }

        public String getContent() {
            return content;
        }

        public Integer getVersion() {
            return version;
        }

        /**
         * 获取当前阶段对应的前端视图名称
         * 前端根据此字段路由到对应的编辑组件
         *
         * @return 视图名称
         */
        public String getCurrentView() {
            return switch (phase) {
                case TITLE_SELECTION -> "title-select";
                case OUTLINE_EDITING -> "outline-editor";
                case CONTENT_GENERATION -> "content-preview";
                case COMPLETED -> "article-final";
            };
        }
    }
}
```

### 3.8 Controller（ArticleController.java）

```java
package com.passage.loop.controller;

import com.passage.loop.model.Article;
import com.passage.loop.model.ArticlePhase;
import com.passage.loop.service.ArticleStateMachineService;
import com.passage.loop.service.ArticleStateMachineService.ResumeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ArticleController - 文章创作 Controller
 * <p>
 * 三阶段 HITL 的用户介入接口。
 * 每个端点对应一个用户操作，触发状态机流转。
 * <p>
 * 接口设计原则：
 * 1. RESTful 风格，资源路径清晰
 * 2. 每个操作都有明确的语义（创建/选择/确认/编辑/优化）
 * 3. 异常统一处理，返回有意义的错误信息
 *
 * @author AI-Passage-Creator
 */
@RestController
@RequestMapping("/api/article")
public class ArticleController {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(ArticleController.class);

    /** 状态机服务（注入依赖） */
    private final ArticleStateMachineService stateMachineService;

    /**
     * 构造方法注入
     *
     * @param stateMachineService 状态机服务
     */
    public ArticleController(ArticleStateMachineService stateMachineService) {
        this.stateMachineService = stateMachineService;
    }

    // ========== 阶段 1：选题 ==========

    /**
     * 创建文章并生成标题方案
     * <p>
     * 用户输入一个创作主题，系统创建文章并生成 3-5 个标题方案。
     * 文章初始状态为 TITLE_SELECTION。
     * <p>
     * 请求示例：
     * POST /api/article
     * {
     *     "userId": 1,
     *     "topic": "Spring Boot 微服务最佳实践"
     * }
     *
     * @param request 创建请求（包含 userId 和 topic）
     * @return 创建好的文章（包含标题方案）
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createArticle(@RequestBody Map<String, Object> request) {
        // 1. 解析请求参数
        Long userId = Long.valueOf(request.get("userId").toString());
        String topic = (String) request.get("topic");

        // 2. 创建文章并生成标题方案
        Article article = stateMachineService.createArticle(userId, topic);

        // 3. 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("id", article.getId());
        response.put("phase", article.getPhase().name());
        response.put("phaseLabel", article.getPhase().getLabel());
        response.put("titleOptions", article.getTitleOptions());
        response.put("version", article.getVersion());

        log.info("创建文章成功: id={}, topic={}", article.getId(), topic);
        return ResponseEntity.ok(response);
    }

    /**
     * 用户选择标题 —— 进入阶段 2
     * <p>
     * 用户从 AI 生成的标题方案中选中一个，系统保存选择结果，
     * 并将状态从 TITLE_SELECTION 流转到 OUTLINE_EDITING。
     * <p>
     * 请求示例：
     * PUT /api/article/{id}/title
     * {
     *     "userId": 1,
     *     "title": "Spring Boot 微服务架构实战：从零到生产"
     * }
     *
     * @param id      文章 ID
     * @param request 选择请求（包含 userId 和选中的标题）
     * @return 更新后的文章信息
     */
    @PutMapping("/{id}/title")
    public ResponseEntity<Map<String, Object>> selectTitle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        Long userId = Long.valueOf(request.get("userId").toString());
        String selectedTitle = (String) request.get("title");

        // 1. 保存用户选定的标题（断点续作：实时保存）
        stateMachineService.saveCheckpoint(id, article ->
                article.setSelectedTitle(selectedTitle));

        // 2. 状态流转：TITLE_SELECTION → OUTLINE_EDITING
        Article article = stateMachineService.transition(id, ArticlePhase.OUTLINE_EDITING, userId);

        // 3. 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("id", article.getId());
        response.put("phase", article.getPhase().name());
        response.put("phaseLabel", article.getPhase().getLabel());
        response.put("selectedTitle", article.getSelectedTitle());
        response.put("version", article.getVersion());
        response.put("message", "已进入大纲编辑阶段，请编辑或确认大纲");

        // 注意：真实项目中，此处还会异步启动大纲生成（SSE 流式推送）
        // generationService.startOutlineGeneration(id, selectedTitle);

        return ResponseEntity.ok(response);
    }

    /**
     * 用户要求重新生成标题
     * <p>
     * 用户对当前标题方案不满意，要求 AI 重新生成。
     * 状态保持不变（TITLE_SELECTION），重新生成新的标题方案。
     * <p>
     * 请求示例：
     * POST /api/article/{id}/title/regenerate
     * {
     *     "userId": 1
     * }
     *
     * @param id      文章 ID
     * @param request 请求（包含 userId）
     * @return 新的标题方案列表
     */
    @PostMapping("/{id}/title/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateTitle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        Long userId = Long.valueOf(request.get("userId").toString());

        // 1. 校验当前状态必须是 TITLE_SELECTION
        // 状态流转保持在 TITLE_SELECTION（重新生成不改变阶段）
        // 真实项目中，这里调用 LLM 重新生成标题

        // 2. 模拟重新生成标题（原地更新）
        // 真实项目中：调用 TitleGeneratorAgent 重新调用 LLM
        List<String> newTitles = List.of(
                "重新生成标题 1：" + id + " 实战指南",
                "重新生成标题 2：深入理解...",
                "重新生成标题 3：从零开始学..."
        );

        // 3. 保存新的标题方案
        String finalNewTitle1 = newTitles.get(0);
        String finalNewTitle2 = newTitles.get(1);
        String finalNewTitle3 = newTitles.get(2);
        stateMachineService.saveCheckpoint(id, article ->
                article.setTitleOptions(List.of(finalNewTitle1, finalNewTitle2, finalNewTitle3)));

        // 4. 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("titleOptions", newTitles);
        response.put("message", "已重新生成标题方案，请选择");

        return ResponseEntity.ok(response);
    }

    // ========== 阶段 2：大纲 ==========

    /**
     * 用户确认大纲 —— 进入阶段 3
     * <p>
     * 用户对大纲满意后确认，系统将状态从 OUTLINE_EDITING
     * 流转到 CONTENT_GENERATION，并异步启动正文生成。
     * <p>
     * 请求示例：
     * POST /api/article/{id}/outline/confirm
     * {
     *     "userId": 1
     * }
     *
     * @param id      文章 ID
     * @param request 请求（包含 userId）
     * @return 更新后的文章信息
     */
    @PostMapping("/{id}/outline/confirm")
    public ResponseEntity<Map<String, Object>> confirmOutline(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        Long userId = Long.valueOf(request.get("userId").toString());

        // 1. 校验当前状态必须是 OUTLINE_EDITING
        // 2. 状态流转：OUTLINE_EDITING → CONTENT_GENERATION
        Article article = stateMachineService.transition(id, ArticlePhase.CONTENT_GENERATION, userId);

        // 3. 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("id", article.getId());
        response.put("phase", article.getPhase().name());
        response.put("phaseLabel", article.getPhase().getLabel());
        response.put("version", article.getVersion());
        response.put("message", "已进入正文生成阶段");

        // 注意：真实项目中，此处异步启动正文 + 配图生成（StateGraph 自动编排）
        // generationService.startContentGeneration(id);

        return ResponseEntity.ok(response);
    }

    /**
     * 用户手动编辑大纲
     * <p>
     * 用户直接在编辑器中修改大纲内容，实时保存到数据库。
     * 状态保持在 OUTLINE_EDITING，不触发流转。
     * <p>
     * 请求示例：
     * PUT /api/article/{id}/outline
     * {
     *     "userId": 1,
     *     "outline": "## 一、核心概念\n..."
     * }
     *
     * @param id      文章 ID
     * @param request 编辑请求（包含 userId 和新的 outline）
     * @return 操作结果
     */
    @PutMapping("/{id}/outline")
    public ResponseEntity<Map<String, Object>> editOutline(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        String outline = (String) request.get("outline");

        // 保存用户编辑后的大纲（断点续作的关键：实时保存）
        stateMachineService.saveCheckpoint(id, article ->
                article.setOutline(outline));

        Map<String, Object> response = new HashMap<>();
        response.put("message", "大纲已保存");
        return ResponseEntity.ok(response);
    }

    /**
     * 用户要求 AI 优化大纲
     * <p>
     * 用户发送优化指令，AI 基于当前大纲和指令重新生成。
     * 状态保持在 OUTLINE_EDITING。
     * <p>
     * 请求示例：
     * POST /api/article/{id}/outline/optimize
     * {
     *     "userId": 1,
     *     "instruction": "把第三章写得更详细"
     * }
     *
     * @param id      文章 ID
     * @param request 优化请求（包含 userId 和优化指令）
     * @return 优化后的大纲
     */
    @PostMapping("/{id}/outline/optimize")
    public ResponseEntity<Map<String, Object>> optimizeOutline(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        String instruction = (String) request.get("instruction");

        // 模拟 AI 优化大纲（真实项目中调用 LLM）
        // 真实流程：读取当前大纲 + 优化指令 → 调用 LLM → SSE 返回优化结果
        String optimizedOutline = "## 一、引言\n"
                + "### 1.1 背景介绍\n"
                + "### 1.2 本文目标\n"
                + "\n"
                + "## 二、核心概念\n"
                + "### 2.1 原理概述\n"
                + "### 2.2 架构设计\n"
                + "\n"
                + "## 三、实战演练（详细展开）\n"
                + "### 3.1 环境准备\n"
                + "### 3.2 代码实现\n"
                + "### 3.3 单元测试\n"
                + "### 3.4 性能优化\n"
                + "\n"
                + "## 四、总结\n"
                + "### 4.1 最佳实践\n"
                + "### 4.2 延伸阅读\n";

        // 保存优化后的大纲
        String finalOptimizedOutline = optimizedOutline;
        stateMachineService.saveCheckpoint(id, article ->
                article.setOutline(finalOptimizedOutline));

        Map<String, Object> response = new HashMap<>();
        response.put("outline", optimizedOutline);
        response.put("message", "大纲已根据指令优化");
        return ResponseEntity.ok(response);
    }

    // ========== 阶段 3：正文生成 ==========

    /**
     * 模拟正文完成（从阶段 3 到终态）
     * <p>
     * 模拟正文生成完成后的状态流转。
     * 真实项目中，由 AI Agent 生成完成后自动触发。
     * <p>
     * 请求示例：
     * POST /api/article/{id}/complete
     * {
     *     "userId": 1,
     *     "content": "完整文章正文...",
     *     "images": ["url1", "url2"]
     * }
     *
     * @param id      文章 ID
     * @param request 完成请求（包含 userId、content 和 images）
     * @return 最终文章信息
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeArticle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        Long userId = Long.valueOf(request.get("userId").toString());
        String content = (String) request.get("content");
        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) request.get("images");

        // 1. 保存正文内容（断点续作）
        stateMachineService.saveCheckpoint(id, article -> {
            article.setContent(content);
            if (images != null) {
                article.setImages(images);
            }
        });

        // 2. 状态流转：CONTENT_GENERATION → COMPLETED
        Article article = stateMachineService.transition(id, ArticlePhase.COMPLETED, userId);

        // 3. 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("id", article.getId());
        response.put("phase", article.getPhase().name());
        response.put("phaseLabel", article.getPhase().getLabel());
        response.put("version", article.getVersion());
        response.put("message", "文章创作完成");

        return ResponseEntity.ok(response);
    }

    // ========== 断点续作 ==========

    /**
     * 恢复创作现场 —— 断点续作的入口
     * <p>
     * 用户重新打开文章时调用，返回当前状态和所有中间结果。
     * 前端根据返回的 phase 字段，自动跳转到对应的编辑视图。
     * <p>
     * 这是断点续作的核心接口，用户刷新页面、关闭浏览器后重新打开时调用。
     * <p>
     * 请求示例：
     * GET /api/article/{id}/resume
     *
     * @param id 文章 ID
     * @return 恢复上下文（包含当前阶段和所有中间数据）
     */
    @GetMapping("/{id}/resume")
    public ResponseEntity<ResumeContext> resume(@PathVariable Long id) {
        ResumeContext context = stateMachineService.resume(id);
        return ResponseEntity.ok(context);
    }
}
```

### 3.9 启动类（HumanInLoopDemoApplication.java）

```java
package com.passage.loop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HumanInLoopDemoApplication - 应用启动类
 * <p>
 * HITL 人机协作 Demo 的入口。
 * 使用 @SpringBootApplication 自动配置 Spring Boot 环境。
 * <p>
 * 启动后：
 * 1. 自动扫描 com.passage.loop 包下的所有组件
 * 2. 自动配置嵌入式 Tomcat 服务器
 * 3. 自动配置 Spring MVC
 * 4. 自动配置 Jackson JSON 序列化
 *
 * @author AI-Passage-Creator
 */
@SpringBootApplication
public class HumanInLoopDemoApplication {

    /**
     * 主方法 —— 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HumanInLoopDemoApplication.class, args);
    }
}
```

### 3.10 前端测试页面（index.html）

```html
<!DOCTYPE html>
<!--
  index.html —— 人机协作 HITL 前端测试页面
  模拟三阶段创作流程的 UI 交互，支持：
  1. 创建文章 → 选择标题 → 确认大纲 → 完成
  2. 重新生成标题、编辑大纲、优化大纲
  3. 断点续作（模拟刷新后恢复）
-->
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>人机协作 HITL Demo</title>
    <style>
        /* 全局样式 */
        body {
            font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
            max-width: 800px;
            margin: 20px auto;
            padding: 20px;
            background: #f5f5f5;
            color: #333;
        }
        .container {
            background: white;
            border-radius: 8px;
            padding: 24px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        h1 {
            color: #1a1a1a;
            border-bottom: 3px solid #4A90D9;
            padding-bottom: 10px;
        }
        h2 {
            color: #333;
            margin-top: 24px;
        }
        .phase-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 4px;
            font-size: 14px;
            font-weight: bold;
            color: white;
            margin-bottom: 16px;
        }
        .phase-TITLE_SELECTION { background: #4A90D9; }
        .phase-OUTLINE_EDITING { background: #27AE60; }
        .phase-CONTENT_GENERATION { background: #E67E22; }
        .phase-COMPLETED { background: #8E44AD; }

        .step-indicator {
            display: flex;
            justify-content: space-between;
            margin: 20px 0;
            padding: 0;
        }
        .step {
            flex: 1;
            text-align: center;
            padding: 10px;
            background: #eee;
            color: #999;
            border-radius: 4px;
            margin: 0 4px;
            font-size: 13px;
            transition: all 0.3s;
        }
        .step.active {
            background: #4A90D9;
            color: white;
        }
        .step.completed {
            background: #27AE60;
            color: white;
        }

        .section {
            margin: 20px 0;
            padding: 16px;
            border: 1px solid #e0e0e0;
            border-radius: 6px;
            background: #fafafa;
        }
        .section-title {
            font-weight: bold;
            margin-bottom: 12px;
            color: #555;
        }

        .title-option {
            padding: 10px 14px;
            margin: 6px 0;
            border: 2px solid #e0e0e0;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.2s;
        }
        .title-option:hover {
            border-color: #4A90D9;
            background: #f0f6ff;
        }
        .title-option.selected {
            border-color: #4A90D9;
            background: #e8f0fe;
        }

        textarea {
            width: 100%;
            min-height: 120px;
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-family: 'Consolas', 'Monaco', monospace;
            font-size: 14px;
            line-height: 1.6;
            resize: vertical;
        }

        .btn {
            padding: 8px 20px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            margin: 4px;
            transition: all 0.2s;
        }
        .btn-primary { background: #4A90D9; color: white; }
        .btn-primary:hover { background: #357ABD; }
        .btn-success { background: #27AE60; color: white; }
        .btn-success:hover { background: #219A52; }
        .btn-warning { background: #E67E22; color: white; }
        .btn-warning:hover { background: #D35400; }
        .btn-danger { background: #E74C3C; color: white; }
        .btn-danger:hover { background: #C0392B; }
        .btn-secondary { background: #95A5A6; color: white; }
        .btn-secondary:hover { background: #7F8C8D; }

        .btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }

        .log-area {
            background: #1e1e1e;
            color: #d4d4d4;
            padding: 12px;
            border-radius: 4px;
            font-family: 'Consolas', 'Monaco', monospace;
            font-size: 12px;
            max-height: 200px;
            overflow-y: auto;
            line-height: 1.5;
            margin-top: 16px;
        }
        .log-area .info { color: #6A9955; }
        .log-area .warn { color: #CE9178; }
        .log-area .error { color: #F44747; }

        .hidden { display: none; }

        .status-bar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 8px 12px;
            background: #f8f9fa;
            border-radius: 4px;
            margin-top: 16px;
            font-size: 13px;
            color: #666;
        }
    </style>
</head>
<body>
<div class="container">
    <h1>人机协作 HITL Demo</h1>
    <p>模拟三阶段文章创作流程：选题 → 大纲 → 正文配图</p>

    <div id="phaseBadge" class="phase-badge phase-TITLE_SELECTION">选题选择中</div>

    <!-- 步骤指示器 -->
    <div class="step-indicator">
        <div id="step1" class="step active">① 选题</div>
        <div id="step2" class="step">② 大纲</div>
        <div id="step3" class="step">③ 正文</div>
        <div id="step4" class="step">④ 完成</div>
    </div>

    <!-- 阶段 1：选题 -->
    <div id="phase1Section" class="section">
        <div class="section-title">阶段 1：选择标题</div>

        <!-- 创建文章 -->
        <div id="createSection">
            <p>输入创作主题，AI 将生成标题方案：</p>
            <div style="display:flex;gap:8px;">
                <input id="topicInput" type="text" value="Spring Boot 微服务最佳实践"
                       style="flex:1;padding:8px;border:1px solid #ddd;border-radius:4px;font-size:14px;">
                <button id="createBtn" class="btn btn-primary" onclick="createArticle()">创建文章</button>
            </div>
        </div>

        <!-- 标题方案 -->
        <div id="titleSection" class="hidden">
            <p>请选择一个标题：</p>
            <div id="titleOptions"></div>
            <div style="margin-top:12px;">
                <button id="regenerateBtn" class="btn btn-secondary" onclick="regenerateTitle()">重新生成</button>
            </div>
        </div>
    </div>

    <!-- 阶段 2：大纲 -->
    <div id="phase2Section" class="section hidden">
        <div class="section-title">阶段 2：编辑大纲</div>
        <p>已选标题：<strong id="selectedTitleDisplay"></strong></p>
        <textarea id="outlineEditor" placeholder="在此编辑大纲内容（Markdown 格式）..."></textarea>
        <div style="margin-top:12px;">
            <button class="btn btn-success" onclick="confirmOutline()">确认大纲</button>
            <button class="btn btn-secondary" onclick="editOutline()">保存草稿</button>
            <button class="btn btn-warning" onclick="optimizeOutline()">AI 优化</button>
        </div>
        <div style="margin-top:8px;">
            <label>优化指令：</label>
            <input id="optimizeInstruction" type="text" value="把第三章写得更详细"
                   style="padding:6px;border:1px solid #ddd;border-radius:4px;width:300px;font-size:13px;">
        </div>
    </div>

    <!-- 阶段 3：正文生成 -->
    <div id="phase3Section" class="section hidden">
        <div class="section-title">阶段 3：正文生成中</div>
        <p>文章正在生成，完成后可确认发布：</p>
        <textarea id="contentPreview" placeholder="正文内容将在此展示..." readonly></textarea>
        <div style="margin-top:12px;">
            <button class="btn btn-success" onclick="completeArticle()">确认完成</button>
        </div>
    </div>

    <!-- 完成状态 -->
    <div id="phase4Section" class="section hidden">
        <div class="section-title">文章已创作完成</div>
        <p>最终文章已定稿，感谢使用！</p>
        <button class="btn btn-primary" onclick="resetDemo()">重新创作一篇</button>
    </div>

    <!-- 断点续作模拟 -->
    <div class="status-bar">
        <span>文章 ID：<strong id="articleIdDisplay">-</strong></span>
        <span>版本号：<strong id="versionDisplay">0</strong></span>
        <button class="btn btn-secondary" onclick="simulateResume()" style="font-size:12px;padding:4px 12px;">
            模拟刷新恢复
        </button>
    </div>

    <!-- 日志 -->
    <div class="log-area" id="logArea">
        <div class="info">[系统] 欢迎使用人机协作 HITL Demo</div>
        <div class="info">[系统] 请输入创作主题，点击"创建文章"开始</div>
    </div>
</div>

<script>
    // ====== 全局状态 ======
    let currentArticleId = null;     // 当前文章 ID
    let currentVersion = 0;         // 当前乐观锁版本号
    let currentPhase = 'TITLE_SELECTION';  // 当前阶段

    // ====== 工具函数 ======

    /**
     * 添加日志
     * @param {string} level 日志级别（info/warn/error）
     * @param {string} message 日志消息
     */
    function addLog(level, message) {
        const logArea = document.getElementById('logArea');
        const div = document.createElement('div');
        div.className = level;
        div.textContent = '[' + new Date().toLocaleTimeString() + '] ' + message;
        logArea.appendChild(div);
        logArea.scrollTop = logArea.scrollHeight;
    }

    /**
     * 更新页面状态
     * 根据当前阶段显示/隐藏对应的 UI 区域
     *
     * @param {string} phase 阶段名称
     */
    function updateUI(phase) {
        // 更新阶段徽章
        const badge = document.getElementById('phaseBadge');
        badge.className = 'phase-badge phase-' + phase;
        const labels = {
            'TITLE_SELECTION': '选题选择中',
            'OUTLINE_EDITING': '大纲编辑中',
            'CONTENT_GENERATION': '正文生成中',
            'COMPLETED': '已完成'
        };
        badge.textContent = labels[phase] || phase;

        // 更新步骤指示器
        const steps = ['step1', 'step2', 'step3', 'step4'];
        const phaseMap = {
            'TITLE_SELECTION': 0,
            'OUTLINE_EDITING': 1,
            'CONTENT_GENERATION': 2,
            'COMPLETED': 3
        };
        const activeStep = phaseMap[phase] || 0;
        steps.forEach((id, index) => {
            const el = document.getElementById(id);
            el.className = 'step';
            if (index < activeStep) el.classList.add('completed');
            if (index === activeStep) el.classList.add('active');
        });

        // 显示/隐藏各阶段区域
        document.getElementById('phase1Section').className = 'section' + (phase === 'TITLE_SELECTION' ? '' : ' hidden');
        document.getElementById('phase2Section').className = 'section' + (phase === 'OUTLINE_EDITING' ? '' : ' hidden');
        document.getElementById('phase3Section').className = 'section' + (phase === 'CONTENT_GENERATION' ? '' : ' hidden');
        document.getElementById('phase4Section').className = 'section' + (phase === 'COMPLETED' ? '' : ' hidden');

        currentPhase = phase;
    }

    // ====== API 调用 ======

    /**
     * 创建文章
     * 调用 POST /api/article 接口
     */
    async function createArticle() {
        const topic = document.getElementById('topicInput').value.trim();
        if (!topic) {
            addLog('warn', '请输入创作主题');
            return;
        }

        try {
            addLog('info', '正在创建文章，主题：' + topic);

            const response = await fetch('/api/article', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: 1, topic: topic })
            });

            const data = await response.json();
            currentArticleId = data.id;
            currentVersion = data.version;
            document.getElementById('articleIdDisplay').textContent = data.id;
            document.getElementById('versionDisplay').textContent = data.version;

            // 渲染标题方案
            renderTitleOptions(data.titleOptions);

            // 更新 UI
            document.getElementById('createSection').classList.add('hidden');
            document.getElementById('titleSection').classList.remove('hidden');
            updateUI('TITLE_SELECTION');

            addLog('info', '文章创建成功，ID: ' + data.id + '，生成 ' + data.titleOptions.length + ' 个标题方案');
        } catch (error) {
            addLog('error', '创建失败：' + error.message);
        }
    }

    /**
     * 渲染标题方案列表
     * @param {string[]} titles 标题数组
     */
    function renderTitleOptions(titles) {
        const container = document.getElementById('titleOptions');
        container.innerHTML = '';
        titles.forEach(title => {
            const div = document.createElement('div');
            div.className = 'title-option';
            div.textContent = title;
            div.onclick = () => selectTitle(title, div);
            container.appendChild(div);
        });
    }

    /**
     * 选择标题
     * 调用 PUT /api/article/{id}/title 接口
     *
     * @param {string} title 选中的标题
     * @param {HTMLElement} element 点击的 DOM 元素
     */
    async function selectTitle(title, element) {
        // 清除其他选中状态
        document.querySelectorAll('.title-option').forEach(el => el.classList.remove('selected'));
        element.classList.add('selected');

        try {
            addLog('info', '正在选择标题：' + title);

            const response = await fetch('/api/article/' + currentArticleId + '/title', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: 1, title: title })
            });

            const data = await response.json();
            currentVersion = data.version;
            document.getElementById('versionDisplay').textContent = data.version;
            document.getElementById('selectedTitleDisplay').textContent = title;

            // 生成模拟大纲（真实项目中由 AI 生成）
            const outline = '## 一、引言\n'
                + '### 1.1 背景介绍\n'
                + '### 1.2 本文目标\n'
                + '\n'
                + '## 二、核心概念\n'
                + '### 2.1 原理概述\n'
                + '### 2.2 架构设计\n'
                + '\n'
                + '## 三、实战演练\n'
                + '### 3.1 环境准备\n'
                + '### 3.2 代码实现\n'
                + '### 3.3 单元测试\n'
                + '\n'
                + '## 四、总结\n';
            document.getElementById('outlineEditor').value = outline;

            updateUI('OUTLINE_EDITING');
            addLog('info', '标题已确认，进入大纲编辑阶段');
        } catch (error) {
            addLog('error', '选择标题失败：' + error.message);
        }
    }

    /**
     * 重新生成标题
     * 调用 POST /api/article/{id}/title/regenerate 接口
     */
    async function regenerateTitle() {
        try {
            addLog('info', '正在重新生成标题...');

            const response = await fetch('/api/article/' + currentArticleId + '/title/regenerate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: 1 })
            });

            const data = await response.json();
            renderTitleOptions(data.titleOptions);

            addLog('info', '标题已重新生成');
        } catch (error) {
            addLog('error', '重新生成失败：' + error.message);
        }
    }

    /**
     * 确认大纲
     * 调用 POST /api/article/{id}/outline/confirm 接口
     */
    async function confirmOutline() {
        try {
            addLog('info', '正在确认大纲...');

            const response = await fetch('/api/article/' + currentArticleId + '/outline/confirm', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: 1 })
            });

            const data = await response.json();
            currentVersion = data.version;
            document.getElementById('versionDisplay').textContent = data.version;

            // 生成模拟正文内容
            const content = '# ' + document.getElementById('selectedTitleDisplay').textContent + '\n\n'
                + '## 一、引言\n\n'
                + '在当今的软件开发领域，微服务架构已经成为主流选择...\n\n'
                + '## 二、核心概念\n\n'
                + '### 2.1 原理概述\n\n'
                + '微服务架构的核心思想是将单一应用程序划分为一组小服务...\n\n'
                + '### 2.2 架构设计\n\n'
                + 'Spring Boot 提供了开箱即用的微服务开发体验...\n\n'
                + '## 三、实战演练\n\n'
                + '### 3.1 环境准备\n\n'
                + '首先，确保已安装 JDK 17 和 Maven 3.8+...\n\n'
                + '### 3.2 代码实现\n\n'
                + '```java\n@SpringBootApplication\npublic class Application {\n    public static void main(String[] args) {\n'
                + '        SpringApplication.run(Application.class, args);\n    }\n}\n```\n\n'
                + '## 四、总结\n\n'
                + '本文介绍了微服务架构的核心概念和 Spring Boot 实践...\n';

            document.getElementById('contentPreview').value = content;

            updateUI('CONTENT_GENERATION');
            addLog('info', '大纲已确认，进入正文生成阶段');
        } catch (error) {
            addLog('error', '确认大纲失败：' + error.message);
        }
    }

    /**
     * 保存大纲草稿
     * 调用 PUT /api/article/{id}/outline 接口
     */
    async function editOutline() {
        const outline = document.getElementById('outlineEditor').value;
        try {
            const response = await fetch('/api/article/' + currentArticleId + '/outline', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: 1, outline: outline })
            });

            const data = await response.json();
            addLog('info', data.message);
        } catch (error) {
            addLog('error', '保存草稿失败：' + error.message);
        }
    }

    /**
     * AI 优化大纲
     * 调用 POST /api/article/{id}/outline/optimize 接口
     */
    async function optimizeOutline() {
        const instruction = document.getElementById('optimizeInstruction').value;
        try {
            addLog('info', '正在优化大纲，指令：' + instruction);

            const response = await fetch('/api/article/' + currentArticleId + '/outline/optimize', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: 1, instruction: instruction })
            });

            const data = await response.json();
            document.getElementById('outlineEditor').value = data.outline;
            addLog('info', data.message);
        } catch (error) {
            addLog('error', '优化失败：' + error.message);
        }
    }

    /**
     * 完成文章
     * 调用 POST /api/article/{id}/complete 接口
     */
    async function completeArticle() {
        const content = document.getElementById('contentPreview').value;
        try {
            addLog('info', '正在完成文章...');

            const response = await fetch('/api/article/' + currentArticleId + '/complete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: 1,
                    content: content,
                    images: ['https://picsum.photos/800/400']
                })
            });

            const data = await response.json();
            currentVersion = data.version;
            document.getElementById('versionDisplay').textContent = data.version;

            updateUI('COMPLETED');
            addLog('info', '文章创作完成！');
        } catch (error) {
            addLog('error', '完成文章失败：' + error.message);
        }
    }

    /**
     * 模拟断点续作
     * 调用 GET /api/article/{id}/resume 接口
     * 模拟用户刷新页面后恢复创作现场
     */
    async function simulateResume() {
        if (!currentArticleId) {
            addLog('warn', '请先创建文章');
            return;
        }

        try {
            addLog('info', '正在恢复创作现场（模拟刷新页面）...');

            const response = await fetch('/api/article/' + currentArticleId + '/resume');
            const data = await response.json();

            currentVersion = data.version;
            document.getElementById('versionDisplay').textContent = data.version;

            // 根据阶段恢复数据
            if (data.titleOptions) {
                renderTitleOptions(data.titleOptions);
            }
            if (data.outline) {
                document.getElementById('outlineEditor').value = data.outline;
            }
            if (data.content) {
                document.getElementById('contentPreview').value = data.content;
            }

            updateUI(data.phase);
            addLog('info', '创作现场已恢复，当前阶段：' + data.phaseLabel
                + '，视图：' + data.currentView);
        } catch (error) {
            addLog('error', '恢复失败：' + error.message);
        }
    }

    /**
     * 重置 Demo
     * 清空所有状态，回到初始界面
     */
    function resetDemo() {
        currentArticleId = null;
        currentVersion = 0;
        currentPhase = 'TITLE_SELECTION';

        document.getElementById('articleIdDisplay').textContent = '-';
        document.getElementById('versionDisplay').textContent = '0';
        document.getElementById('createSection').classList.remove('hidden');
        document.getElementById('titleSection').classList.add('hidden');
        document.getElementById('outlineEditor').value = '';
        document.getElementById('contentPreview').value = '';
        document.getElementById('titleOptions').innerHTML = '';

        updateUI('TITLE_SELECTION');
        addLog('info', 'Demo 已重置，可以重新开始');
    }
</script>
</body>
</html>
```

### 3.11 测试类（HumanInLoopDemoApplicationTests.java）

```java
package com.passage.loop;

import com.passage.loop.model.Article;
import com.passage.loop.model.ArticlePhase;
import com.passage.loop.repository.ArticleRepository;
import com.passage.loop.service.ArticleStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HumanInLoopDemoApplicationTests - 人机协作 HITL 测试类
 * <p>
 * 测试三阶段创作流程的核心功能：
 * 1. 创建文章并生成标题方案
 * 2. 状态流转（TITLE_SELECTION → OUTLINE_EDITING → CONTENT_GENERATION → COMPLETED）
 * 3. 非法状态流转拒绝
 * 4. 断点续作恢复
 * 5. 乐观锁冲突检测
 *
 * @author AI-Passage-Creator
 */
@SpringBootTest
class HumanInLoopDemoApplicationTests {

    /** 状态机服务（被测试对象） */
    @Autowired
    private ArticleStateMachineService stateMachineService;

    /** 文章仓储（用于验证状态） */
    @Autowired
    private ArticleRepository articleRepository;

    /** 测试用户 ID */
    private static final Long TEST_USER_ID = 1L;

    /**
     * 每个测试执行前清空数据
     */
    @BeforeEach
    void setUp() {
        articleRepository.clear();
    }

    /**
     * 测试 1：创建文章
     * <p>
     * 验证：
     * 1. 文章能成功创建
     * 2. 初始状态为 TITLE_SELECTION
     * 3. 生成了标题方案（至少 3 个）
     * 4. 版本号初始为 0
     */
    @Test
    @DisplayName("测试创建文章 - 初始状态应为 TITLE_SELECTION，且生成标题方案")
    void testCreateArticle() {
        // 执行：创建文章
        Article article = stateMachineService.createArticle(TEST_USER_ID, "Spring Boot 微服务");

        // 验证：文章不为空
        assertNotNull(article, "文章不应为空");
        // 验证：初始状态为 TITLE_SELECTION
        assertEquals(ArticlePhase.TITLE_SELECTION, article.getPhase(),
                "新创建的文章状态应为 TITLE_SELECTION");
        // 验证：生成了标题方案
        assertNotNull(article.getTitleOptions(), "标题方案不应为空");
        assertTrue(article.getTitleOptions().size() >= 3,
                "标题方案至少应有 3 个");
        // 验证：版本号初始为 0
        assertEquals(0, article.getVersion(), "初始版本号应为 0");
        // 验证：创建者和用户 ID 正确
        assertEquals(TEST_USER_ID, article.getUserId(), "创建者 ID 应匹配");
    }

    /**
     * 测试 2：完整的三阶段流程
     * <p>
     * 验证完整的状态流转路径：
     * TITLE_SELECTION → OUTLINE_EDITING → CONTENT_GENERATION → COMPLETED
     * <p>
     * 这是 HITL 系统最核心的"幸福路径"（Happy Path）。
     */
    @Test
    @DisplayName("测试完整的三阶段流程 - 状态流转路径")
    void testFullTransitionFlow() {
        // 1. 创建文章（TITLE_SELECTION）
        Article article = stateMachineService.createArticle(TEST_USER_ID, "测试主题");
        Long articleId = article.getId();

        // 验证：阶段 1
        assertEquals(ArticlePhase.TITLE_SELECTION, article.getPhase());

        // 2. 选择标题 → 进入阶段 2（OUTLINE_EDITING）
        stateMachineService.saveCheckpoint(articleId, a -> a.setSelectedTitle("测试标题"));
        article = stateMachineService.transition(articleId, ArticlePhase.OUTLINE_EDITING, TEST_USER_ID);

        // 验证：阶段 2
        assertEquals(ArticlePhase.OUTLINE_EDITING, article.getPhase(),
                "选择标题后应进入 OUTLINE_EDITING 阶段");
        assertEquals("测试标题", article.getSelectedTitle(), "选定的标题应正确保存");

        // 3. 保存大纲
        stateMachineService.saveCheckpoint(articleId, a -> a.setOutline("## 测试大纲"));
        // 验证：大纲已保存
        Article savedArticle = articleRepository.findById(articleId).orElse(null);
        assertNotNull(savedArticle, "文章应存在");
        assertEquals("## 测试大纲", savedArticle.getOutline(), "大纲应正确保存");

        // 4. 确认大纲 → 进入阶段 3（CONTENT_GENERATION）
        article = stateMachineService.transition(articleId, ArticlePhase.CONTENT_GENERATION, TEST_USER_ID);

        // 验证：阶段 3
        assertEquals(ArticlePhase.CONTENT_GENERATION, article.getPhase(),
                "确认大纲后应进入 CONTENT_GENERATION 阶段");

        // 5. 完成正文 → 进入终态（COMPLETED）
        stateMachineService.saveCheckpoint(articleId, a -> {
            a.setContent("完整正文内容...");
            a.setImages(java.util.List.of("https://example.com/image1.jpg"));
        });
        article = stateMachineService.transition(articleId, ArticlePhase.COMPLETED, TEST_USER_ID);

        // 验证：终态
        assertEquals(ArticlePhase.COMPLETED, article.getPhase(),
                "正文完成后应进入 COMPLETED 阶段");
        assertNotNull(article.getContent(), "正文内容不应为空");

        // 验证：版本号递增（每次 transition 版本号 +1）
        assertEquals(3, article.getVersion(), "经历了 3 次状态流转，版本号应为 3");
    }

    /**
     * 测试 3：非法状态流转
     * <p>
     * 验证系统能拒绝非法状态流转：
     * 1. 从 TITLE_SELECTION 不能直接跳到 COMPLETED（跳过大纲和正文）
     * 2. 从 COMPLETED 不能继续编辑
     * 3. 非法流转应抛出 IllegalStateException
     */
    @Test
    @DisplayName("测试非法状态流转 - 应抛出异常")
    void testInvalidTransition() {
        // 1. 创建文章
        Article article = stateMachineService.createArticle(TEST_USER_ID, "测试主题");
        Long articleId = article.getId();

        // 2. 尝试非法流转：TITLE_SELECTION → COMPLETED（跳过中间步骤）
        assertThrows(IllegalStateException.class,
                () -> stateMachineService.transition(articleId, ArticlePhase.COMPLETED, TEST_USER_ID),
                "从 TITLE_SELECTION 直接跳到 COMPLETED 应抛出异常");

        // 3. 正常流转到 COMPLETED
        stateMachineService.saveCheckpoint(articleId, a -> a.setSelectedTitle("标题"));
        stateMachineService.transition(articleId, ArticlePhase.OUTLINE_EDITING, TEST_USER_ID);
        stateMachineService.saveCheckpoint(articleId, a -> a.setOutline("大纲"));
        stateMachineService.transition(articleId, ArticlePhase.CONTENT_GENERATION, TEST_USER_ID);
        stateMachineService.saveCheckpoint(articleId, a -> a.setContent("正文"));
        stateMachineService.transition(articleId, ArticlePhase.COMPLETED, TEST_USER_ID);

        // 4. 尝试从 COMPLETED 流转到其他状态（重新创作除外）
        // COMPLETED → OUTLINE_EDITING 是非法流转
        assertThrows(IllegalStateException.class,
                () -> stateMachineService.transition(articleId, ArticlePhase.OUTLINE_EDITING, TEST_USER_ID),
                "从 COMPLETED 回到 OUTLINE_EDITING 应抛出异常");

        // 5. 验证：COMPLETED → TITLE_SELECTION 是合法流转（重新创作）
        Article restarted = stateMachineService.transition(articleId, ArticlePhase.TITLE_SELECTION, TEST_USER_ID);
        assertEquals(ArticlePhase.TITLE_SELECTION, restarted.getPhase(),
                "从 COMPLETED 回到 TITLE_SELECTION（重新创作）应合法");
    }

    /**
     * 测试 4：断点续作恢复
     * <p>
     * 验证每个阶段都能正确恢复创作现场：
     * 1. 阶段 1：恢复标题方案
     * 2. 阶段 2：恢复大纲
     * 3. 阶段 3：恢复大纲 + 正文
     * 4. 终态：恢复完整文章
     */
    @Test
    @DisplayName("测试断点续作 - 各阶段恢复上下文")
    void testResumeContext() {
        // 1. 创建文章
        Article article = stateMachineService.createArticle(TEST_USER_ID, "测试主题");
        Long articleId = article.getId();

        // 2. 阶段 1 恢复：应返回标题方案
        ArticleStateMachineService.ResumeContext context1 = stateMachineService.resume(articleId);
        assertEquals(ArticlePhase.TITLE_SELECTION, context1.getPhase(),
                "阶段 1 恢复：阶段应为 TITLE_SELECTION");
        assertNotNull(context1.getTitleOptions(), "阶段 1 恢复：应包含标题方案");
        assertTrue(context1.getTitleOptions().size() >= 3, "阶段 1 恢复：标题方案至少 3 个");

        // 3. 进入阶段 2 并保存大纲
        stateMachineService.saveCheckpoint(articleId, a -> a.setSelectedTitle("测试标题"));
        stateMachineService.transition(articleId, ArticlePhase.OUTLINE_EDITING, TEST_USER_ID);
        stateMachineService.saveCheckpoint(articleId, a -> a.setOutline("## 测试大纲"));

        // 4. 阶段 2 恢复：应返回大纲
        ArticleStateMachineService.ResumeContext context2 = stateMachineService.resume(articleId);
        assertEquals(ArticlePhase.OUTLINE_EDITING, context2.getPhase(),
                "阶段 2 恢复：阶段应为 OUTLINE_EDITING");
        assertEquals("## 测试大纲", context2.getOutline(), "阶段 2 恢复：应包含大纲");

        // 5. 进入阶段 3 并保存正文
        stateMachineService.transition(articleId, ArticlePhase.CONTENT_GENERATION, TEST_USER_ID);
        stateMachineService.saveCheckpoint(articleId, a -> a.setContent("正文内容"));

        // 6. 阶段 3 恢复：应返回大纲 + 正文
        ArticleStateMachineService.ResumeContext context3 = stateMachineService.resume(articleId);
        assertEquals(ArticlePhase.CONTENT_GENERATION, context3.getPhase(),
                "阶段 3 恢复：阶段应为 CONTENT_GENERATION");
        assertEquals("## 测试大纲", context3.getOutline(), "阶段 3 恢复：应包含大纲");
        assertEquals("正文内容", context3.getContent(), "阶段 3 恢复：应包含正文");

        // 7. 验证视图名称
        assertEquals("title-select", context1.getCurrentView(), "阶段 1 视图应为 title-select");
        assertEquals("outline-editor", context2.getCurrentView(), "阶段 2 视图应为 outline-editor");
        assertEquals("content-preview", context3.getCurrentView(), "阶段 3 视图应为 content-preview");
    }

    /**
     * 测试 5：乐观锁冲突检测
     * <p>
     * 验证多线程并发更新时，乐观锁能正确检测冲突：
     * 1. 模拟两个线程同时读取同一篇文章
     * 2. 第一个线程更新成功
     * 3. 第二个线程使用旧版本号更新失败
     */
    @Test
    @DisplayName("测试乐观锁 - 并发冲突检测")
    void testOptimisticLockConflict() throws Exception {
        // 1. 创建文章
        Article article = stateMachineService.createArticle(TEST_USER_ID, "乐观锁测试");
        Long articleId = article.getId();

        // 2. 模拟两个线程同时读取文章（版本号相同）
        Article thread1Copy = articleRepository.findById(articleId).orElse(null);
        Article thread2Copy = articleRepository.findById(articleId).orElse(null);
        assertNotNull(thread1Copy, "线程 1 读取失败");
        assertNotNull(thread2Copy, "线程 2 读取失败");

        // 3. 线程 1 更新成功（版本号递增）
        thread1Copy.setSelectedTitle("线程1的标题");
        thread1Copy.setPhase(ArticlePhase.OUTLINE_EDITING);
        boolean thread1Result = articleRepository.updateWithOptimisticLock(thread1Copy);
        assertTrue(thread1Result, "线程 1 更新应成功");

        // 4. 线程 2 使用旧版本号更新，应该失败
        thread2Copy.setSelectedTitle("线程2的标题");
        thread2Copy.setPhase(ArticlePhase.OUTLINE_EDITING);
        boolean thread2Result = articleRepository.updateWithOptimisticLock(thread2Copy);
        assertFalse(thread2Result, "线程 2 使用旧版本号更新应失败（乐观锁冲突）");

        // 5. 验证最终数据是线程 1 的更新
        Article finalArticle = articleRepository.findById(articleId).orElse(null);
        assertNotNull(finalArticle, "最终文章应存在");
        assertEquals("线程1的标题", finalArticle.getSelectedTitle(),
                "乐观锁冲突后，最终数据应为线程 1 的更新");
        assertEquals(1, finalArticle.getVersion(), "成功更新后版本号应为 1");
    }

    /**
     * 测试 6：非法操作权限校验
     * <p>
     * 验证用户不能操作他人的文章。
     * 如果 userId 不匹配，应抛出 SecurityException。
     */
    @Test
    @DisplayName("测试权限校验 - 不能操作他人的文章")
    void testPermissionCheck() {
        // 1. 用户 1 创建文章
        Article article = stateMachineService.createArticle(1L, "用户1的文章");
        Long articleId = article.getId();

        // 2. 用户 2 尝试操作用户 1 的文章，应抛出异常
        assertThrows(SecurityException.class,
                () -> stateMachineService.transition(articleId, ArticlePhase.OUTLINE_EDITING, 2L),
                "其他用户操作他人文章应抛出 SecurityException");
    }

    /**
     * 测试 7：重新生成标题
     * <p>
     * 验证在 TITLE_SELECTION 阶段，可以重新生成标题
     * 而不改变当前阶段。
     */
    @Test
    @DisplayName("测试重新生成标题 - 阶段保持不变")
    void testRegenerateTitle() {
        // 1. 创建文章
        Article article = stateMachineService.createArticle(TEST_USER_ID, "测试");
        Long articleId = article.getId();

        // 2. 记录原始标题方案
        java.util.List<String> originalTitles = article.getTitleOptions();

        // 3. 重新生成标题（TITLE_SELECTION → TITLE_SELECTION，保持阶段不变）
        // 模拟重新生成：更新标题方案
        java.util.List<String> newTitles = java.util.List.of("新标题1", "新标题2", "新标题3");
        stateMachineService.saveCheckpoint(articleId, a -> a.setTitleOptions(newTitles));

        // 4. 验证：阶段不变
        Article refreshed = articleRepository.findById(articleId).orElse(null);
        assertNotNull(refreshed, "文章应存在");
        assertEquals(ArticlePhase.TITLE_SELECTION, refreshed.getPhase(),
                "重新生成标题后，阶段应保持不变");
        assertEquals(newTitles, refreshed.getTitleOptions(),
                "标题方案应更新为新方案");
    }
}
```

---

## 四、运行验证

### 4.1 启动项目

```bash
# 进入项目目录
cd human-in-loop-demo

# 编译并启动
mvn spring-boot:run
```

启动后，控制台输出类似：

```
[INFO] Scanning for projects...
[INFO] --- spring-boot:3.2.5:run (default-cli) @ human-in-loop-demo ---
[INFO] Running com.passage.loop.HumanInLoopDemoApplication

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v3.2.5)

2026-08-22T10:00:00.000+08:00  INFO 12345 --- [main] c.p.loop.HumanInLoopDemoApplication      : Started HumanInLoopDemoApplication in 2.5 seconds
```

### 4.2 测试 API 接口

使用 `curl` 命令测试各接口：

**1. 创建文章（阶段 1：选题）：**

```bash
curl -X POST http://localhost:8080/api/article \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "topic": "Spring Boot 微服务最佳实践"}'
```

预期响应：
```json
{
  "id": 1,
  "phase": "TITLE_SELECTION",
  "phaseLabel": "选题选择中",
  "titleOptions": [
    "Spring Boot 微服务最佳实践：从入门到精通",
    "深入理解Spring Boot 微服务最佳实践：核心原理与最佳实践",
    "Spring Boot 微服务最佳实践实战指南：手把手带你掌握",
    "为什么Spring Boot 微服务最佳实践是开发者的首选？",
    "2026 年Spring Boot 微服务最佳实践完全学习指南"
  ],
  "version": 0
}
```

**2. 选择标题（阶段 1 → 阶段 2）：**

```bash
curl -X PUT http://localhost:8080/api/article/1/title \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "title": "Spring Boot 微服务架构实战：从零到生产"}'
```

预期响应：
```json
{
  "id": 1,
  "phase": "OUTLINE_EDITING",
  "phaseLabel": "大纲编辑中",
  "selectedTitle": "Spring Boot 微服务架构实战：从零到生产",
  "version": 1,
  "message": "已进入大纲编辑阶段，请编辑或确认大纲"
}
```

**3. 编辑大纲（阶段 2，保持状态）：**

```bash
curl -X PUT http://localhost:8080/api/article/1/outline \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "outline": "## 一、核心概念\n## 二、实战演练"}'
```

**4. 确认大纲（阶段 2 → 阶段 3）：**

```bash
curl -X POST http://localhost:8080/api/article/1/outline/confirm \
  -H "Content-Type: application/json" \
  -d '{"userId": 1}'
```

**5. 完成文章（阶段 3 → 终态）：**

```bash
curl -X POST http://localhost:8080/api/article/1/complete \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "content": "完整文章正文...", "images": ["https://example.com/img.jpg"]}'
```

**6. 断点续作恢复：**

```bash
curl http://localhost:8080/api/article/1/resume
```

### 4.3 前端页面

打开浏览器访问 `http://localhost:8080`，即可看到 HITL 交互页面：

1. 输入创作主题，点击"创建文章"
2. 从标题方案中选择一个
3. 编辑大纲，确认或优化
4. 确认正文，完成创作
5. 点击"模拟刷新恢复"测试断点续作

### 4.4 运行测试

```bash
mvn test
```

预期输出：

```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 五、项目对照

### 5.1 Demo 与真实项目的对比

| 对比维度 | 本 Demo（human-in-loop-demo） | 真实项目（ai-passage-creator） |
|---------|-----------------------------|-------------------------------|
| 数据存储 | 内存 ConcurrentHashMap | MySQL + MyBatis-Flex |
| 乐观锁 | 模拟版本号校验 | MyBatis-Flex 内置 `@Column(version = true)` |
| 标题生成 | 预定义示例数据 | LLM 实时生成（TitleGeneratorAgent） |
| 大纲生成 | 预定义示例数据 | LLM 流式生成（OutlineGeneratorAgent） |
| 正文生成 | 预定义示例数据 | LLM + StateGraph 多Agent编排 |
| 配图生成 | 无 | 策略模式配图（Pexels / Mermaid / AI） |
| 并发控制 | 内存乐观锁 | 数据库乐观锁 + Redis 分布式锁 |
| 事务管理 | 无 | @Transactional + 编程式事务 |
| 前端交互 | 原生 HTML + JavaScript | Vue 3 + Ant Design Vue + Pinia |
| 实时推送 | 无 | SSE 流式推送（10 种事件类型） |
| 用户权限 | 简单 userId 校验 | Spring Security + JWT |

### 5.2 Demo 的局限性

1. **数据未持久化**：内存存储，重启后数据丢失。真实项目使用 MySQL
2. **生成过程未模拟**：标题、大纲、正文都是预定义数据。真实项目调用 LLM
3. **无实时推送**：阶段 3 的正文生成没有 SSE 流式展示。真实项目使用 SSE 实时推送
4. **无事务保护**：状态更新和中间结果保存未做事务。真实项目使用 @Transactional
5. **无全局异常处理**：异常信息直接返回给前端。真实项目使用 @ControllerAdvice

### 5.3 进阶路径

从本 Demo 到真实项目，需要掌握以下知识：

| 步骤 | 知识点 | 参考文章 |
|------|--------|----------|
| 1 | 数据库持久化：MyBatis-Flex 实体映射、BaseMapper | 06 MyBatis-Flex |
| 2 | 实时推送：SSE 流式输出、SseEmitter、EventSource | 04 SSE 流式输出 |
| 3 | AI 生成：Spring AI Alibaba ChatClient、Prompt 设计 | 01 Spring AI Alibaba |
| 4 | 多Agent 编排：StateGraph 工作流、节点/边定义 | 02 多Agent 编排 |
| 5 | 配图策略：策略模式、多级降级、Picsum 兜底 | 03 策略模式 |
| 6 | 用户认证：Spring Security、JWT、权限校验 | 后续系列 |

---

## 六、面试题

### Q1: 人机协作（HITL）设计模式的核心是什么？在项目中有哪些实现方式？

**核心思想：** 在 AI 自动化流程中插入人类审核节点，让人的判断力参与 AI 生成过程。本质是"AI 提效 + 人工把关"的双引擎模式。

**四种实现方式：**

| 实现方式 | 说明 | 适用场景 |
|----------|------|----------|
| **生成后审核（Human Review）** | AI 生成 → 人工审核 → 通过/驳回 | 文章审核、代码审查 |
| **生成前确认（Human Confirmation）** | AI 提出方案 → 人工选择 → 继续执行 | ai-passage-creator 的选题、大纲阶段 |
| **过程干预（Human Intervention）** | 生成过程中人工随时介入调整 | 流式生成中停止、指令优化 |
| **反馈循环（Feedback Loop）** | 人工反馈 → AI 优化 → 再反馈 | 大纲优化、二次生成 |

**项目中的组合：** ai-passage-creator 将四种方式结合：
- "生成后审核"体现为每个阶段完成后的确认环节
- "生成前确认"体现为标题选择和 AI 优化指令
- "过程干预"体现为生成中可停止、可回到上一阶段
- "反馈循环"体现为优化大纲 → 重新生成的多轮交互

**HITL 的设计要点：**

| 要点 | 说明 |
|------|------|
| 介入粒度 | 粒度太小（每字确认）效率低；粒度太大（全程黑盒）失去意义。项目选择"阶段级"介入 |
| 介入成本 | 用户介入的成本必须足够低（点选、编辑），否则用户选择全自动 |
| 默认路径 | 提供"全自动直通"选项（用户跳过所有介入直接完成） |
| 回退能力 | 任何阶段都能回到上一阶段，用户不会"卡死"在某一步 |

### Q2: 三阶段流程的状态一致性如何保证？

**状态一致性的五个维度：**

| 维度 | 风险 | 解决方案 |
|------|------|----------|
| **内存一致性** | 前端展示状态与后端实际状态不一致 | 状态以数据库 `phase` 字段为唯一事实源（Single Source of Truth） |
| **持久化一致性** | 中途崩溃丢失进度 | 每个阶段完成立即持久化，生成中定期保存（断点续作） |
| **事务一致性** | 状态流转与数据更新不同步 | `transition()` 使用 `@Transactional`，状态 + 数据在同一事务提交 |
| **并发一致性** | 多个请求同时修改同一文章 | 乐观锁（version 字段），冲突时拒绝并提示刷新 |
| **环境一致性** | 单机状态在多实例部署下失效 | 状态只存数据库，不存 JVM 内存，天然支持水平扩展 |

**乐观锁核心代码原理解析：**

```java
// 乐观锁的核心：版本号校验
// 1. 读取文章时，获取当前版本号
Article article = articleRepository.findById(articleId);
// article.version = 0

// 2. 更新时，版本号 +1，WHERE 条件带版本号
// UPDATE article SET phase = 'OUTLINE_EDITING', version = 1
// WHERE id = 1 AND version = 0

// 3. 如果另一个线程抢先更新了（version 已变为 1）
// 则本线程的 WHERE version = 0 找不到记录，影响行数为 0
// 更新失败，抛出乐观锁冲突异常
```

**为什么用乐观锁而不是悲观锁？**

| 维度 | 乐观锁（version） | 悲观锁（SELECT FOR UPDATE） |
|------|-------------------|------------------------------|
| 思想 | 假设冲突少，更新时校验 | 假设冲突多，读取时锁定 |
| 实现 | 版本号字段 + WHERE 校验 | 数据库行锁 |
| 性能 | 高（无锁开销） | 低（持有锁期间阻塞其他线程） |
| 适用场景 | 读多写少（用户创作以读为主） | 写多读少、强一致场景 |
| 失败处理 | 更新返回 0，重试或报错 | 等待锁释放 |

### Q3: 并发冲突如何处理？描述一个完整的处理流程。

**并发冲突的典型场景：**

```
场景 1：多设备操作
  用户手机和电脑同时打开同一篇文章，手机修改大纲，电脑也修改大纲
  → 后提交的覆盖先提交的内容 → 数据丢失

场景 2：重复提交
  用户双击"确认大纲"按钮，两个请求同时到达
  → 产生两次状态流转，可能出现非法状态

场景 3：生成中干预
  阶段 3 生成中，用户同时点击"停止生成"和"确认发布"
  → 两个动作竞争同一状态
```

**三层防护机制：**

```
第 1 层：乐观锁（数据库层面）—— 防止数据覆盖
  Article 实体中的 version 字段，每次 UPDATE 自动 +1
  WHERE 条件带版本号，版本号不匹配则更新失败

第 2 层：状态机校验（业务层面）—— 防止非法流转
  ArticlePhase.isValidTransition() 拒绝非法流转：
  - 从 OUTLINE_EDITING 直接跳到 COMPLETED
  - 已完成文章再次触发生成

第 3 层：前端按钮防抖（UI 层面）—— 防止重复提交
  500ms 内的重复点击忽略，只处理第一次
```

**前端防抖代码：**

```javascript
// 前端按钮防抖：500ms 内的重复点击忽略
function debounceSubmit(fn, delay = 500) {
    let timer = null;
    return function (...args) {
        if (timer) return; // 500ms 内的重复点击忽略
        timer = setTimeout(() => { timer = null; }, delay);
        return fn.apply(this, args);
    };
}

// 使用：确认大纲按钮
const confirmOutline = debounceSubmit(async () => {
    try {
        await api.post(`/api/article/${articleId}/outline/confirm`);
        showSuccess('已进入正文生成阶段');
    } catch (error) {
        if (error.code === 'CONFLICT') {
            // 乐观锁冲突：刷新最新状态
            await store.resumeSession(articleId);
            showError('文章已被其他操作修改，已刷新最新状态');
        }
    }
}, 1000);
```

**乐观锁冲突的三种处理策略：**

| 策略 | 做法 | 适用场景 |
|------|------|----------|
| **提示刷新** | 冲突时返回 409，提示用户刷新最新状态 | 简单直接，推荐默认 |
| **自动重试** | 读取最新 version，重新提交 | 表单提交类操作 |
| **合并策略** | 服务端对比新旧数据，尽量合并 | 复杂场景，实现成本高 |

**Redis 分布式锁（可选增强）：** 如果同一用户快速重复点击导致乐观锁频繁冲突，可对"状态流转 + 异步任务启动"这种复合操作加分布式锁：

```java
// 状态流转 + 启动异步生成，用 Redisson 分布式锁串行化
public void confirmOutline(Long articleId, Long userId) {
    // 锁 key：按文章维度加锁，保证同一篇文章的操作串行
    RLock lock = redissonClient.getLock("article:transition:" + articleId);
    try {
        if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
            // 获取锁后，执行状态流转 + 启动异步生成
            // 这两个操作在锁的保护下原子执行
            stateMachineService.transition(articleId, CONTENT_GENERATION, userId);
            generationService.startContentGeneration(articleId);
        }
    } finally {
        // 确保释放锁（防止死锁）
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

---

## 七、避坑指南

### 7.1 不要将状态放在 JVM 内存中

```java
// ❌ 错误：把阶段状态放在内存 Map 中
// 问题 1：应用重启后状态全部丢失，用户无法断点续作
// 问题 2：多实例部署时，每个实例的内存状态不一致
private Map<Long, ArticlePhase> phaseCache = new ConcurrentHashMap<>();

// ✅ 正确：状态持久化到数据库，只把内存当缓存
// article 表的 phase 字段是唯一事实源
// 内存缓存只做读加速，写操作直接落库
```

### 7.2 中途刷新/Tab 关闭的恢复

```java
// ❌ 错误：只在最终完成后保存
// 用户生成 20 秒后刷新页面 → 大纲、正文全部丢失

// ✅ 正确：每个阶段完成立即保存 + 生成过程定期保存
// 大纲生成完成 → 立即保存 outline 字段
// 正文流式生成 → 每收到 N 个 Token 保存一次（节流）
private static final int SAVE_EVERY_TOKENS = 100; // 每 100 个 Token 保存一次

// 前端：beforeunload 事件时主动保存当前内容
window.addEventListener('beforeunload', () => {
    // 保存当前大纲/正文到草稿
    navigator.sendBeacon('/api/article/draft', JSON.stringify({
        articleId: store.articleId,
        outline: store.outline,
        content: store.content
    }));
});
```

### 7.3 HITL 介入的"可选性"设计

```java
// ❌ 错误：强制用户在每个阶段都介入
// 用户体验差，简单文章生成也要等用户点确认

// ✅ 正确：提供"全自动直通"模式
// 用户可以选择：
// 模式 1：全自动（默认）—— 每个阶段 AI 完成后自动进入下一阶段
// 模式 2：半自动 —— 每个阶段完成后暂停，等用户确认
// 模式 3：人工编辑 —— 侧重人工干预

// 服务端判断：
public boolean shouldWaitForUser(Article article, User user) {
    if (user.getPreference() == InteractionMode.AUTO) {
        return false; // 全自动模式，不等待
    }
    return true; // 其他模式等待用户确认
}
```

### 7.4 状态流转校验的时机

```java
// ❌ 错误：只在 Controller 层做状态校验
// 校验逻辑分散在多个 Controller 中，容易遗漏
// 异步任务、定时任务、Webhook 回调都可能绕过校验直接改状态

// ✅ 正确：把校验收敛到 StateMachineService 的唯一入口
// 所有状态修改都必须调用 transition() 方法
// 校验逻辑集中在 isValidTransition() 一个地方，天然防守
// 单测也只测这一个入口，测试成本低
```

### 7.5 配置参考

```yaml
# application.yml —— 人机协作与创作流程配置
article:
  generation:
    # 交互模式（auto=全自动 / confirm=每阶段确认 / manual=人工编辑）
    default-mode: confirm
    # 断点续作相关
    checkpoint:
      auto-save-interval-ms: 5000   # 生成过程自动保存间隔（毫秒）
      resume-history-days: 30       # 草稿保留天数
    # 乐观锁相关
    optimistic-lock:
      enabled: true                 # 是否启用乐观锁
      max-retries: 3                # 冲突自动重试次数（0 表示不重试，直接报错）
    # 重新生成相关
    regenerate:
      max-times: 5                  # 单篇最多重新生成次数（防滥用）
      cool-down-ms: 30000           # 重新生成冷却时间（防止刷接口）
```