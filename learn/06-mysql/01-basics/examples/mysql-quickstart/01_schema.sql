-- ============================================
-- MySQL 电商数据库 Schema 设计
-- 演示：建表 / 索引 / 外键 / 约束
-- ============================================

-- 创建数据库（编码 utf8mb4 支持 emoji 和中文）
CREATE DATABASE IF NOT EXISTS ecommerce
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE ecommerce;

-- ---------- 1. 用户表 ----------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名',
    email       VARCHAR(100) NOT NULL COMMENT '邮箱',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    password    VARCHAR(255) NOT NULL COMMENT '密码（加密后）',
    avatar      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1=正常 0=禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_username (username),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='用户表';

-- ---------- 2. 分类表 ----------
CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    name        VARCHAR(50)  NOT NULL COMMENT '分类名称',
    parent_id   BIGINT       DEFAULT NULL COMMENT '父分类ID（自引用）',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    level       TINYINT      NOT NULL DEFAULT 1 COMMENT '层级: 1/2/3',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id),
    INDEX idx_sort (sort_order),
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='商品分类表';

-- ---------- 3. 商品表 ----------
CREATE TABLE IF NOT EXISTS products (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    category_id   BIGINT       NOT NULL COMMENT '分类ID',
    name          VARCHAR(200) NOT NULL COMMENT '商品名称',
    description   TEXT         COMMENT '商品描述',
    price         DECIMAL(10,2) NOT NULL COMMENT '价格（元）',
    stock         INT          NOT NULL DEFAULT 0 COMMENT '库存',
    sales         INT          NOT NULL DEFAULT 0 COMMENT '销量',
    image_url     VARCHAR(500) DEFAULT NULL COMMENT '主图URL',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '上架状态: 1=上架 0=下架',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_price (price),
    INDEX idx_status_stock (status, stock),
    INDEX idx_sales (sales DESC),
    FULLTEXT INDEX ft_name_desc (name, description) WITH PARSER ngram COMMENT '全文索引（支持中文搜索）',
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB COMMENT='商品表';

-- ---------- 4. 订单表 ----------
CREATE TABLE IF NOT EXISTS orders (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    order_no      VARCHAR(32)  NOT NULL COMMENT '订单号（业务唯一）',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    total_amount  DECIMAL(12,2) NOT NULL COMMENT '订单总金额',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0=待付款 1=已付款 2=已发货 3=已完成 4=已取消',
    payment_at    DATETIME     DEFAULT NULL COMMENT '付款时间',
    shipping_at   DATETIME     DEFAULT NULL COMMENT '发货时间',
    address       VARCHAR(500) NOT NULL COMMENT '收货地址',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_user_status (user_id, status),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='订单表';

-- ---------- 5. 订单详情表 ----------
CREATE TABLE IF NOT EXISTS order_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    order_id      BIGINT       NOT NULL COMMENT '订单ID',
    product_id    BIGINT       NOT NULL COMMENT '商品ID',
    product_name  VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
    price         DECIMAL(10,2) NOT NULL COMMENT '购买单价',
    quantity      INT          NOT NULL COMMENT '购买数量',
    subtotal      DECIMAL(12,2) NOT NULL COMMENT '小计金额',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB COMMENT='订单明细表';