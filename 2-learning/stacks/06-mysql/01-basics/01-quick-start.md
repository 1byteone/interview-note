# 快速入门 — MySQL 安装配置与基本 SQL

> 👶 初学者路线 · 预计阅读时间：30 分钟

---

## 一、Docker 本地开发环境搭建

使用 Docker 快速启动 MySQL 8.0 开发环境，避免本地安装的复杂性。

### 1.1 启动 MySQL 容器

```bash
# 拉取镜像并启动容器
docker run -d \
  --name mysql-dev \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=mall \
  -e MYSQL_CHARACTER_SET_SERVER=utf8mb4 \
  -e MYSQL_COLLATION_SERVER=utf8mb4_unicode_ci \
  -p 3306:3306 \
  mysql:8.0

# 验证启动
docker ps | grep mysql-dev
```

### 1.2 连接数据库

```bash
# 进入容器
docker exec -it mysql-dev mysql -uroot -proot123

# 或使用客户端工具连接
# Host: 127.0.0.1, Port: 3306, User: root, Password: root123
```

### 1.3 常用配置参数

```ini
# my.cnf 核心配置（开发环境）
[mysqld]
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
max_connections = 200
innodb_buffer_pool_size = 256M
# 生产环境建议 buffer_pool_size = 物理内存的 70%
```

---

## 二、基本 SQL 操作

### 2.1 DDL — 数据定义语言

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS mall
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 创建表
CREATE TABLE `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1=上架 0=下架',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 修改表
ALTER TABLE product ADD COLUMN `category_id` BIGINT AFTER `price`;
ALTER TABLE product MODIFY COLUMN `name` VARCHAR(300) NOT NULL;
ALTER TABLE product DROP COLUMN `stock`;
```

### 2.2 DML — 数据操作语言

```sql
-- 插入
INSERT INTO product (name, price, category_id, status) VALUES
('iPhone 15', 6999.00, 1, 1),
('MacBook Pro', 14999.00, 2, 1),
('AirPods Pro', 1999.00, 3, 1);

-- 批量插入（推荐，减少连接开销）
INSERT INTO product (name, price, category_id, status) VALUES
  ('iPad', 3499.00, 2, 1),
  ('Apple Watch', 3199.00, 3, 1);

-- 更新
UPDATE product SET price = 6799.00 WHERE name = 'iPhone 15';

-- 删除（逻辑删除更安全，不建议物理删除）
DELETE FROM product WHERE id = 5;
```

### 2.3 DQL — 数据查询语言

```sql
-- 基本查询
SELECT id, name, price FROM product WHERE status = 1;

-- 分页查询
SELECT * FROM product
ORDER BY id DESC
LIMIT 10 OFFSET 0;  -- 第一页

-- 聚合查询
SELECT
    COUNT(*) AS total,
    MAX(price) AS max_price,
    MIN(price) AS min_price,
    AVG(price) AS avg_price
FROM product;

-- 分组统计
SELECT category_id, COUNT(*) AS cnt, AVG(price) AS avg_price
FROM product
GROUP BY category_id
HAVING cnt > 1;
```

### 2.4 DCL — 数据控制语言

```sql
-- 创建用户并授权
CREATE USER 'mall_app'@'%' IDENTIFIED BY 'password';
GRANT SELECT, INSERT, UPDATE, DELETE ON mall.* TO 'mall_app'@'%';
FLUSH PRIVILEGES;

-- 查看权限
SHOW GRANTS FOR 'mall_app'@'%';
```

---

## 三、表设计原则

### 3.1 数据库三大范式

| 范式 | 要求 | 违反示例 |
|------|------|----------|
| 1NF | 列不可再分 | `address` 字段存 "北京市海淀区" 但业务需要分别查询省/市/区 |
| 2NF | 非主键完全依赖主键 | `order_id` 和 `product_name` 一起做主键，但 `product_price` 只依赖 `product_name` |
| 3NF | 非主键不传递依赖 | 订单表中有 `user_name`，但 `user_name` 可以通过 `user_id` 查到 |

实际开发中，**适当冗余**（违反 3NF）是常见的性能优化手段。

### 3.2 数据类型选择原则

| 数据类型 | 适用场景 | 注意事项 |
|----------|----------|----------|
| TINYINT | 状态码、枚举值（0-255） | 1 字节，比 INT 节省 75% 空间 |
| INT | 常规 ID、数量 | 4 字节，最大 21 亿 |
| BIGINT | 分布式 ID、订单号 | 8 字节，建议主键用 BIGINT |
| VARCHAR(32) | 订单号、手机号 | 按实际长度选，不要一律 255 |
| DECIMAL(10,2) | 金额 | 精确小数，**不用 FLOAT/DOUBLE** |
| DATETIME | 时间戳 | 8 字节，时区无关用 DATETIME |
| JSON | 扩展属性 | MySQL 8.0 支持 JSON 索引 |

### 3.3 表设计核心原则

1. **必须字段**：`id`, `create_time`, `update_time`（每条记录必须）
2. **主键选择**：自增 BIGINT 或雪花算法 ID（不建议 UUID）
3. **字符集**：统一 `utf8mb4`，支持表情符号
4. **字段注释**：每个字段必须有 COMMENT
5. **逻辑删除**：使用 `is_deleted` 字段代替物理删除
6. **索引策略**：查询频繁的字段加索引，索引不是越多越好

---

## 四、最小案例：商品表设计

### 4.1 需求分析

为 mall-micro-cloud 设计商品模块：
- 商品有名称、价格、描述、分类
- 商品可以有多张图片
- 商品有上下架状态
- 商品有库存数量

### 4.2 表结构设计

```sql
-- 商品表
CREATE TABLE `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `subtitle` VARCHAR(500) DEFAULT '' COMMENT '副标题',
    `description` TEXT COMMENT '商品描述',
    `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    `sales` INT NOT NULL DEFAULT 0 COMMENT '销量',
    `main_image` VARCHAR(500) DEFAULT '' COMMENT '主图URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1=上架 0=下架 -1=删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_category_status` (`category_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 商品图片表
CREATE TABLE `product_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `url` VARCHAR(500) NOT NULL COMMENT '图片URL',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';
```

### 4.3 设计要点说明

- `category_id` 和 `status` 构建联合索引，覆盖 "某分类下的上架商品" 查询
- 主图单独字段避免每次查询图片表
- `sales` 字段冗余在商品表，避免每次 COUNT 订单表
- 使用 `TINYINT` 存状态，范围 -128~127，足够使用

---

## 总结

- Docker 搭建 MySQL 开发环境只需一行命令
- 掌握 DDL/DML/DQL/DCL 四大类 SQL 语句
- 表设计遵循范式但不迷信范式，适当冗余提升性能
- 数据类型选择影响存储和查询性能，需要谨慎选择

> 下一步：[02-advanced-sql.md](./02-advanced-sql.md) — JOIN 详解与窗口函数