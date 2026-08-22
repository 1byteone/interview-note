# 博客系统数据库设计 — 独立小项目实战

> 🎯 项目实战 · 掌握完整数据库设计流程

---

## 一、项目概述

从零设计一个博客系统（Blog System）的数据库，涵盖用户、文章、分类、标签、评论等核心模块。

### 功能需求

- 用户可以注册、登录，管理个人信息
- 用户发布文章，支持分类和标签
- 读者可以对文章进行评论
- 文章支持按分类、标签、关键词搜索
- 支持文章阅读量统计

---

## 二、ER 图

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│    User     │     │      Post        │     │  Category   │
│─────────────│     │──────────────────│     │─────────────│
│ id (PK)     │1──N│ id (PK)           │N──1│ id (PK)     │
│ username    │     │ title             │     │ name        │
│ email       │     │ content           │     │ description │
│ password    │     │ user_id (FK)     │     │ parent_id   │
│ avatar      │     │ category_id (FK) │     │ create_time │
│ bio         │     │ status            │     └─────────────┘
│ create_time │     │ view_count        │
└─────────────┘     │ like_count        │
     │ 1            │ create_time       │
     │              │ update_time       │
     │              └────────┬──────────┘
     │                       │
     │              ┌────────┴──────────┐
     │              │    Post_Tag       │
     │              │───────────────────│
     │              │ post_id (FK)      │
     │              │ tag_id (FK)       │
     │              │ PK(post_id,tag_id)│
     │              └────────┬──────────┘
     │                       │
     │              ┌────────┴──────────┐     ┌─────────────┐
     │              │      Comment      │     │     Tag     │
     │              │───────────────────│     │─────────────│
     │              │ id (PK)           │     │ id (PK)     │
     │        1──N  │ content           │     │ name        │
     │              │ post_id (FK)      │     │ create_time │
     │              │ user_id (FK)      │     └─────────────┘
     │              │ parent_id (FK)    │
     │              │ create_time       │
     └──────────────┴───────────────────┘
```

---

## 三、表结构设计

### 3.1 用户表

```sql
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密后）',
    `avatar` VARCHAR(500) DEFAULT '' COMMENT '头像URL',
    `bio` VARCHAR(200) DEFAULT '' COMMENT '个人简介',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1=正常 0=禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 3.2 分类表

```sql
CREATE TABLE `category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `description` VARCHAR(200) DEFAULT '' COMMENT '分类描述',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID（0=顶级分类）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';
```

### 3.3 标签表

```sql
CREATE TABLE `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';
```

### 3.4 文章表

```sql
CREATE TABLE `post` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `summary` VARCHAR(500) DEFAULT '' COMMENT '摘要',
    `content` MEDIUMTEXT NOT NULL COMMENT '文章内容（Markdown 格式）',
    `cover_image` VARCHAR(500) DEFAULT '' COMMENT '封面图',
    `user_id` BIGINT NOT NULL COMMENT '作者ID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0=草稿 1=已发布 2=私密',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '阅读量',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数',
    `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶 0=否 1=是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_category_status` (`category_id`, `status`),
    FULLTEXT INDEX `ft_title_content` (`title`, `summary`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';
```

### 3.5 文章标签关联表

```sql
CREATE TABLE `post_tag` (
    `post_id` BIGINT NOT NULL COMMENT '文章ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`post_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表';
```

### 3.6 评论表

```sql
CREATE TABLE `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `post_id` BIGINT NOT NULL COMMENT '文章ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（回复评论时使用）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1=正常 0=隐藏',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_post_id` (`post_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_post_status` (`post_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';
```

---

## 四、索引设计说明

| 表名 | 索引 | 说明 |
|------|------|------|
| user | uk_username, uk_email | 用户登录查询 |
| category | idx_parent_id | 树形结构查询子分类 |
| post | idx_user_status | 用户查询自己的文章列表 |
| post | idx_category_status | 按分类查询已发布文章 |
| post | ft_title_content | 全文搜索标题和摘要 |
| post_tag | 联合主键 | 保证文章和标签不重复关联 |
| comment | idx_post_status | 查询文章下的可见评论 |

---

## 五、SQL 查询优化

### 5.1 文章列表查询（分页 + 关联）

```sql
-- 查询已发布文章列表（含作者和分类信息）
SELECT
    p.id, p.title, p.summary, p.cover_image,
    p.view_count, p.like_count, p.comment_count,
    p.create_time,
    u.username AS author,
    c.name AS category_name
FROM post p
INNER JOIN user u ON p.user_id = u.id
INNER JOIN category c ON p.category_id = c.id
WHERE p.status = 1
ORDER BY p.is_top DESC, p.create_time DESC
LIMIT 10 OFFSET 0;
```

### 5.2 文章详情查询

```sql
-- 查询文章详情及标签
SELECT p.*, u.username, u.avatar
FROM post p
INNER JOIN user u ON p.user_id = u.id
WHERE p.id = 100 AND p.status = 1;

-- 查询文章标签
SELECT t.name
FROM tag t
INNER JOIN post_tag pt ON t.id = pt.tag_id
WHERE pt.post_id = 100;
```

### 5.3 评论列表（树形结构）

```sql
-- 查询文章评论（一级评论 + 子评论）
WITH RECURSIVE comment_tree AS (
    -- 一级评论
    SELECT id, content, user_id, parent_id, create_time, 1 AS level
    FROM comment
    WHERE post_id = 100 AND parent_id IS NULL AND status = 1
    UNION ALL
    -- 子评论
    SELECT c.id, c.content, c.user_id, c.parent_id, c.create_time, ct.level + 1
    FROM comment c
    INNER JOIN comment_tree ct ON c.parent_id = ct.id
    WHERE c.status = 1
)
SELECT * FROM comment_tree ORDER BY level, create_time;
```

### 5.4 标签云查询

```sql
-- 统计每个标签的文章数量
SELECT t.id, t.name, COUNT(pt.post_id) AS post_count
FROM tag t
LEFT JOIN post_tag pt ON t.id = pt.tag_id
LEFT JOIN post p ON pt.post_id = p.id AND p.status = 1
GROUP BY t.id, t.name
ORDER BY post_count DESC;
```

---

## 六、写入性能优化

### 6.1 阅读量更新（防并发）

```sql
-- 使用乐观锁更新阅读量
UPDATE post
SET view_count = view_count + 1
WHERE id = 100;

-- 或者使用 Redis 缓存阅读量，定时批量写入
-- Redis: INCR post:view_count:100
-- 定时任务：每 5 分钟将缓存数据批量写入 MySQL
```

### 6.2 评论数冗余

```sql
-- 文章表冗余 comment_count 字段
-- 新增评论时同步更新
UPDATE post SET comment_count = comment_count + 1 WHERE id = 100;
```

---

## 七、扩展思考

| 场景 | 优化方案 |
|------|----------|
| 文章内容很大（> 10MB） | 内容存 OSS，数据库只存 URL |
| 评论量极大（> 100 万） | 按文章 ID 分表，或使用 MongoDB |
| 全文搜索性能不足 | 引入 Elasticsearch |
| 热点文章缓存 | Redis 缓存文章详情 |
| 用户关注关系 | 建 follower 关联表，或用 Redis Set |

---

## 总结

通过博客系统数据库设计实战，掌握了：
- 完整的数据建模流程（需求分析 -> ER 图 -> 表结构）
- 多表关联查询的 SQL 写法
- 索引设计策略（联合索引、全文索引、唯一索引）
- 递归 CTE 查询树形结构数据
- 读写性能优化手段

> 下一步：[05-interview/quick-revision.md](../../05-interview/quick-revision.md) — 面试速记版