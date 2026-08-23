-- =============================================================================
-- AI Mall (AI 智能商城) - 数据库初始化脚本
-- 挂载至 MySQL 容器的 /docker-entrypoint-initdb.d/，首次启动自动执行
-- 执行顺序：创建数据库 → 创建表 → 插入示例数据
-- =============================================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `ai_mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ai_mall`;

-- =============================================================================
-- 1. 用户表 (users)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    `username`      VARCHAR(50)     NOT NULL                 COMMENT '用户名',
    `password`      VARCHAR(255)    NOT NULL                 COMMENT '密码（BCrypt 加密）',
    `nickname`      VARCHAR(50)     DEFAULT NULL             COMMENT '昵称',
    `email`         VARCHAR(100)    DEFAULT NULL             COMMENT '邮箱',
    `phone`         VARCHAR(20)     DEFAULT NULL             COMMENT '手机号',
    `avatar`        VARCHAR(500)    DEFAULT NULL             COMMENT '头像URL',
    `gender`        TINYINT         DEFAULT 0                COMMENT '性别：0-未知 1-男 2-女',
    `status`        TINYINT         DEFAULT 1                COMMENT '状态：0-禁用 1-启用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =============================================================================
-- 2. 商品分类表 (categories)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `categories` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    `name`          VARCHAR(100)    NOT NULL                 COMMENT '分类名称',
    `parent_id`     BIGINT          DEFAULT 0                COMMENT '父分类ID（0 表示顶级分类）',
    `level`         TINYINT         DEFAULT 1                COMMENT '层级：1-一级 2-二级 3-三级',
    `icon`          VARCHAR(500)    DEFAULT NULL             COMMENT '图标URL',
    `sort_order`    INT             DEFAULT 0                COMMENT '排序权重（越大越靠前）',
    `status`        TINYINT         DEFAULT 1                COMMENT '状态：0-隐藏 1-显示',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_level` (`level`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- =============================================================================
-- 3. 商品表 (products)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `products` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '商品ID',
    `category_id`       BIGINT          NOT NULL                 COMMENT '分类ID',
    `name`              VARCHAR(200)    NOT NULL                 COMMENT '商品名称',
    `subtitle`          VARCHAR(500)    DEFAULT NULL             COMMENT '副标题/卖点',
    `description`       TEXT            DEFAULT NULL             COMMENT '商品描述（富文本）',
    `brand`             VARCHAR(100)    DEFAULT NULL             COMMENT '品牌',
    `thumbnail`         VARCHAR(500)    DEFAULT NULL             COMMENT '缩略图URL',
    `images`            JSON            DEFAULT NULL             COMMENT '商品图片列表（JSON数组）',
    `price`             DECIMAL(10, 2)  NOT NULL DEFAULT 0.00    COMMENT '原价',
    `discount_price`    DECIMAL(10, 2)  DEFAULT NULL             COMMENT '折扣价',
    `stock`             INT             NOT NULL DEFAULT 0       COMMENT '库存数量',
    `sales`             INT             NOT NULL DEFAULT 0       COMMENT '销量',
    `rating`            DECIMAL(2, 1)   DEFAULT 5.0              COMMENT '评分（1.0-5.0）',
    `status`            TINYINT         DEFAULT 1                COMMENT '状态：0-下架 1-上架',
    `is_new`            TINYINT         DEFAULT 0                COMMENT '是否新品：0-否 1-是',
    `is_hot`            TINYINT         DEFAULT 0                COMMENT '是否热销：0-否 1-是',
    `is_recommended`    TINYINT         DEFAULT 0                COMMENT '是否推荐：0-否 1-是',
    `tags`              JSON            DEFAULT NULL             COMMENT '标签（JSON数组，如["夏季","新品"]）',
    `attributes`        JSON            DEFAULT NULL             COMMENT '商品属性（JSON，如{"颜色":"红色","尺寸":"M"}）',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_is_new` (`is_new`),
    KEY `idx_is_hot` (`is_hot`),
    KEY `idx_is_recommended` (`is_recommended`),
    KEY `idx_price` (`price`),
    KEY `idx_sales` (`sales`),
    KEY `idx_rating` (`rating`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_name` (`name`(100)),
    CONSTRAINT `fk_products_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- =============================================================================
-- 4. 购物车表 (cart)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `cart` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '购物车项ID',
    `user_id`       BIGINT          NOT NULL                 COMMENT '用户ID',
    `product_id`    BIGINT          NOT NULL                 COMMENT '商品ID',
    `quantity`      INT             NOT NULL DEFAULT 1       COMMENT '数量',
    `selected`      TINYINT         DEFAULT 1                COMMENT '是否选中：0-未选中 1-已选中',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_product_id` (`product_id`),
    CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- =============================================================================
-- 5. 订单表 (orders)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `orders` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单ID',
    `order_no`          VARCHAR(50)     NOT NULL                 COMMENT '订单编号',
    `user_id`           BIGINT          NOT NULL                 COMMENT '用户ID',
    `total_amount`      DECIMAL(10, 2)  NOT NULL DEFAULT 0.00    COMMENT '订单总金额',
    `discount_amount`   DECIMAL(10, 2)  DEFAULT 0.00             COMMENT '折扣金额',
    `pay_amount`        DECIMAL(10, 2)  NOT NULL DEFAULT 0.00    COMMENT '实付金额',
    `status`            TINYINT         NOT NULL DEFAULT 0       COMMENT '订单状态：0-待付款 1-待发货 2-已发货 3-已签收 4-已完成 5-已取消 6-售后中',
    `payment_method`    TINYINT         DEFAULT NULL             COMMENT '支付方式：1-微信 2-支付宝 3-银联',
    `payment_time`      DATETIME        DEFAULT NULL             COMMENT '支付时间',
    `delivery_time`     DATETIME        DEFAULT NULL             COMMENT '发货时间',
    `receive_time`      DATETIME        DEFAULT NULL             COMMENT '签收时间',
    `consignee`         VARCHAR(50)     NOT NULL                 COMMENT '收货人姓名',
    `phone`             VARCHAR(20)     NOT NULL                 COMMENT '收货人电话',
    `address`           VARCHAR(500)    NOT NULL                 COMMENT '收货地址',
    `remark`            VARCHAR(500)    DEFAULT NULL             COMMENT '订单备注',
    `cancel_reason`     VARCHAR(500)    DEFAULT NULL             COMMENT '取消原因',
    `order_source`      TINYINT         DEFAULT 0                COMMENT '订单来源：0-普通下单 1-秒杀 2-拼团',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_payment_time` (`payment_time`),
    CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- =============================================================================
-- 6. 订单项表 (order_items)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `order_items` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单项ID',
    `order_id`      BIGINT          NOT NULL                 COMMENT '订单ID',
    `product_id`    BIGINT          NOT NULL                 COMMENT '商品ID',
    `product_name`  VARCHAR(200)    NOT NULL                 COMMENT '商品名称（快照）',
    `product_image` VARCHAR(500)    DEFAULT NULL             COMMENT '商品图片（快照）',
    `price`         DECIMAL(10, 2)  NOT NULL DEFAULT 0.00    COMMENT '购买单价',
    `quantity`      INT             NOT NULL DEFAULT 1       COMMENT '购买数量',
    `subtotal`      DECIMAL(10, 2)  NOT NULL DEFAULT 0.00    COMMENT '小计金额',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`),
    CONSTRAINT `fk_order_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_order_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- =============================================================================
-- 7. 支付记录表 (payments)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `payments` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '支付记录ID',
    `payment_no`        VARCHAR(50)     NOT NULL                 COMMENT '支付流水号',
    `order_id`          BIGINT          NOT NULL                 COMMENT '订单ID',
    `order_no`          VARCHAR(50)     NOT NULL                 COMMENT '订单编号',
    `user_id`           BIGINT          NOT NULL                 COMMENT '用户ID',
    `amount`            DECIMAL(10, 2)  NOT NULL DEFAULT 0.00    COMMENT '支付金额',
    `payment_method`    TINYINT         NOT NULL                 COMMENT '支付方式：1-微信 2-支付宝 3-银联',
    `status`            TINYINT         NOT NULL DEFAULT 0       COMMENT '支付状态：0-待支付 1-支付成功 2-支付失败 3-已退款',
    `trade_no`          VARCHAR(100)    DEFAULT NULL             COMMENT '第三方支付交易号',
    `callback_time`     DATETIME        DEFAULT NULL             COMMENT '回调通知时间',
    `callback_content`  TEXT            DEFAULT NULL             COMMENT '回调原始数据（JSON）',
    `refund_amount`     DECIMAL(10, 2)  DEFAULT 0.00             COMMENT '退款金额',
    `refund_time`       DATETIME        DEFAULT NULL             COMMENT '退款时间',
    `refund_reason`     VARCHAR(500)    DEFAULT NULL             COMMENT '退款原因',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_trade_no` (`trade_no`),
    CONSTRAINT `fk_payments_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_payments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';

-- =============================================================================
-- 8. 地址表 (addresses)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `addresses` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '地址ID',
    `user_id`       BIGINT          NOT NULL                 COMMENT '用户ID',
    `consignee`     VARCHAR(50)     NOT NULL                 COMMENT '收货人姓名',
    `phone`         VARCHAR(20)     NOT NULL                 COMMENT '收货人电话',
    `province`      VARCHAR(50)     NOT NULL                 COMMENT '省',
    `city`          VARCHAR(50)     NOT NULL                 COMMENT '市',
    `district`      VARCHAR(50)     NOT NULL                 COMMENT '区/县',
    `detail`        VARCHAR(500)    NOT NULL                 COMMENT '详细地址',
    `zip_code`      VARCHAR(10)     DEFAULT NULL             COMMENT '邮编',
    `is_default`    TINYINT         DEFAULT 0                COMMENT '是否默认地址：0-否 1-是',
    `label`         VARCHAR(20)     DEFAULT NULL             COMMENT '地址标签：家/公司/学校',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_addresses_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- =============================================================================
-- 9. 商品评价表 (reviews)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `reviews` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '评价ID',
    `product_id`    BIGINT          NOT NULL                 COMMENT '商品ID',
    `user_id`       BIGINT          NOT NULL                 COMMENT '用户ID',
    `order_id`      BIGINT          NOT NULL                 COMMENT '订单ID',
    `rating`        TINYINT         NOT NULL DEFAULT 5       COMMENT '评分（1-5星）',
    `content`       TEXT            DEFAULT NULL             COMMENT '评价内容',
    `images`        JSON            DEFAULT NULL             COMMENT '评价图片列表',
    `is_anonymous`  TINYINT         DEFAULT 0                COMMENT '是否匿名：0-否 1-是',
    `is_show`       TINYINT         DEFAULT 1                COMMENT '是否显示：0-不显示 1-显示',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_rating` (`rating`),
    CONSTRAINT `fk_reviews_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_reviews_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_reviews_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

-- =============================================================================
-- 10. 操作日志表 (operation_logs)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `operation_logs` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '日志ID',
    `user_id`       BIGINT          DEFAULT NULL             COMMENT '操作用户ID',
    `module`        VARCHAR(50)     NOT NULL                 COMMENT '操作模块（如：order, product）',
    `operation`     VARCHAR(100)    NOT NULL                 COMMENT '操作类型（如：CREATE, UPDATE, DELETE）',
    `target_id`     BIGINT          DEFAULT NULL             COMMENT '操作对象ID',
    `detail`        JSON            DEFAULT NULL             COMMENT '操作详情（JSON）',
    `ip_address`    VARCHAR(50)     DEFAULT NULL             COMMENT '客户端IP',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_module` (`module`),
    KEY `idx_operation` (`operation`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- =============================================================================
-- 11. Nacos 配置表（Nacos 使用 MySQL 作为后端存储时需要）
-- 注意：Nacos 官方有独立的建表脚本，此处仅创建业务表
-- 如果 Nacos 配置了使用 MySQL 存储，请执行 Nacos 官方 conf/nacos-mysql.sql
-- =============================================================================

-- =============================================================================
-- 示例数据插入
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 10 个示例用户（密码为 BCrypt 加密后的 "123456"）
-- ---------------------------------------------------------------------------
INSERT INTO `users` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `avatar`, `gender`, `status`) VALUES
(1,  'admin',      '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '管理员',   'admin@ai-mall.com',   '13800000001', 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin', 1, 1),
(2,  'zhangsan',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '张三',     'zhangsan@email.com',  '13800000002', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan', 1, 1),
(3,  'lisi',       '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '李四',     'lisi@email.com',      '13800000003', 'https://api.dicebear.com/7.x/avataaars/svg?seed=lisi', 1, 1),
(4,  'wangwu',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '王五',     'wangwu@email.com',    '13800000004', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu', 2, 1),
(5,  'zhaoliu',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '赵六',     'zhaoliu@email.com',   '13800000005', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhaoliu', 2, 1),
(6,  'sunqi',      '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '孙七',     'sunqi@email.com',     '13800000006', 'https://api.dicebear.com/7.x/avataaars/svg?seed=sunqi', 0, 1),
(7,  'zhouba',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '周八',     'zhouba@email.com',    '13800000007', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhouba', 1, 1),
(8,  'wujiu',      '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '吴九',     'wujiu@email.com',     '13800000008', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wujiu', 2, 1),
(9,  'zhengshi',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '郑十',     'zhengshi@email.com',  '13800000009', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhengshi', 0, 1),
(10, 'demo_user',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '演示用户', 'demo@ai-mall.com',    '13800000010', 'https://api.dicebear.com/7.x/avataaars/svg?seed=demo', 1, 1);

-- ---------------------------------------------------------------------------
-- 5 个商品分类（含二级分类，共 11 条）
-- ---------------------------------------------------------------------------
INSERT INTO `categories` (`id`, `name`, `parent_id`, `level`, `icon`, `sort_order`, `status`) VALUES
-- 一级分类
(1,  '电子产品',  0, 1, '📱', 100, 1),
(2,  '服装鞋帽',  0, 1, '👕', 90,  1),
(3,  '食品饮料',  0, 1, '🍜', 80,  1),
(4,  '家居生活',  0, 1, '🏠', 70,  1),
(5,  '图书音像',  0, 1, '📚', 60,  1),
-- 二级分类：电子产品
(6,  '手机通讯',  1, 2, '📱', 100, 1),
(7,  '电脑办公',  1, 2, '💻', 90,  1),
(8,  '智能穿戴',  1, 2, '⌚', 80,  1),
-- 二级分类：服装鞋帽
(9,  '男装',      2, 2, '👔', 90,  1),
(10, '女装',      2, 2, '👗', 80,  1),
(11, '运动鞋',    2, 2, '👟', 70,  1);

-- ---------------------------------------------------------------------------
-- 50 个示例商品
-- ---------------------------------------------------------------------------
INSERT INTO `products` (`id`, `category_id`, `name`, `subtitle`, `brand`, `price`, `discount_price`, `stock`, `sales`, `rating`, `status`, `is_new`, `is_hot`, `is_recommended`, `tags`, `attributes`) VALUES
-- 手机通讯 (category_id=6)
(1,  6, 'iPhone 15 Pro Max 256GB', 'A17 Pro 芯片 · 钛金属设计', 'Apple', 9999.00, 8999.00, 500, 3200, 4.8, 1, 1, 1, 1, '["5G","旗舰","新品"]', '{"颜色":"原色钛金属","存储":"256GB","屏幕":"6.7英寸"}'),
(2,  6, '华为 Mate 60 Pro 512GB', '卫星通话 · 昆仑玻璃', '华为', 7999.00, 7499.00, 300, 2800, 4.9, 1, 1, 1, 1, '["5G","旗舰","卫星通信"]', '{"颜色":"雅丹黑","存储":"512GB","屏幕":"6.82英寸"}'),
(3,  6, '小米 14 Ultra 512GB', '骁龙8 Gen3 · 徕卡光学', '小米', 5999.00, 5499.00, 800, 4500, 4.7, 1, 1, 1, 1, '["5G","旗舰","徕卡"]', '{"颜色":"黑色","存储":"512GB","屏幕":"6.73英寸"}'),
(4,  6, 'OPPO Find X7 Ultra', '双潜望 · 哈苏影像', 'OPPO', 5999.00, 5499.00, 400, 2100, 4.6, 1, 1, 0, 1, '["5G","旗舰","拍照"]', '{"颜色":"海阔天空","存储":"256GB","屏幕":"6.82英寸"}'),
(5,  6, 'vivo X100 Pro', '蔡司APO超级长焦', 'vivo', 4999.00, 4599.00, 600, 1800, 4.7, 1, 0, 1, 1, '["5G","旗舰","蔡司"]', '{"颜色":"星迹蓝","存储":"256GB","屏幕":"6.78英寸"}'),
(6,  6, '三星 Galaxy S24 Ultra', 'Galaxy AI · S Pen', '三星', 9699.00, 8499.00, 350, 1500, 4.5, 1, 0, 1, 0, '["5G","旗舰","AI"]', '{"颜色":"钛灰","存储":"256GB","屏幕":"6.8英寸"}'),
(7,  6, '荣耀 Magic6 Pro', '鹰眼相机 · 荣耀绿洲护眼', '荣耀', 5699.00, 4999.00, 450, 1200, 4.6, 1, 0, 0, 1, '["5G","旗舰","护眼"]', '{"颜色":"海湖青","存储":"256GB","屏幕":"6.8英寸"}'),
(8,  6, '一加 12', '2K东方屏 · 哈苏全焦段', '一加', 4299.00, 3999.00, 700, 2200, 4.7, 1, 0, 1, 1, '["5G","旗舰","哈苏"]', '{"颜色":"留白","存储":"256GB","屏幕":"6.82英寸"}'),

-- 电脑办公 (category_id=7)
(9,  7, 'MacBook Pro 14英寸 M3 Pro', '18GB内存 · 512GB存储', 'Apple', 16999.00, 15499.00, 200, 980, 4.9, 1, 1, 1, 1, '["笔记本","轻薄","高性能"]', '{"颜色":"深空黑","内存":"18GB","存储":"512GB"}'),
(10, 7, '联想 ThinkPad X1 Carbon Gen 11', 'i7-1360P · 16GB · 512GB', '联想', 12999.00, 11499.00, 150, 760, 4.8, 1, 0, 0, 1, '["笔记本","商务","轻薄"]', '{"颜色":"黑色","内存":"16GB","存储":"512GB"}'),
(11, 7, '华为 MateBook X Pro 2024', '酷睿Ultra 9 · OLED屏', '华为', 13999.00, 12999.00, 180, 620, 4.7, 1, 1, 1, 1, '["笔记本","轻薄","触屏"]', '{"颜色":"砚黑","内存":"32GB","存储":"1TB"}'),
(12, 7, '戴尔 XPS 15 9530', 'i7-13700H · RTX4070', '戴尔', 15999.00, 14499.00, 100, 430, 4.5, 1, 0, 0, 0, '["笔记本","高性能","创作"]', '{"颜色":"铂金银","内存":"32GB","存储":"1TB"}'),
(13, 7, 'iPad Pro 12.9英寸 M2', 'M2芯片 · Liquid Retina XDR', 'Apple', 9299.00, 8499.00, 300, 1500, 4.8, 1, 0, 1, 1, '["平板","创作","便携"]', '{"颜色":"深空灰","存储":"256GB","屏幕":"12.9英寸"}'),

-- 智能穿戴 (category_id=8)
(14, 8, 'Apple Watch Ultra 2', '49mm钛金属 · 精度双频GPS', 'Apple', 5999.00, 5499.00, 250, 1200, 4.9, 1, 1, 1, 1, '["手表","运动","户外"]', '{"颜色":"钛金属","尺寸":"49mm","网络":"GPS+蜂窝"}'),
(15, 8, '华为 WATCH GT 4', '46mm · 八边棱角设计', '华为', 1588.00, 1388.00, 500, 2800, 4.7, 1, 1, 1, 1, '["手表","健康","运动"]', '{"颜色":"黑色","尺寸":"46mm","续航":"14天"}'),
(16, 8, '小米手环 8 Pro', '1.74英寸AMOLED屏', '小米', 399.00, 349.00, 2000, 8000, 4.5, 1, 0, 1, 1, '["手环","健康","运动"]', '{"颜色":"黑色","屏幕":"1.74英寸","续航":"14天"}'),

-- 男装 (category_id=9)
(17, 9, '商务修身西装套装', '羊毛混纺 · 免烫抗皱', '雅戈尔', 2999.00, 1999.00, 300, 560, 4.6, 1, 1, 0, 1, '["西装","商务","正装"]', '{"颜色":"深蓝色","材质":"羊毛混纺","尺码":"M-XXL"}'),
(18, 9, '纯棉休闲衬衫', '新疆长绒棉 · 亲肤透气', '海澜之家', 299.00, 199.00, 800, 3200, 4.5, 1, 1, 1, 1, '["衬衫","休闲","纯棉"]', '{"颜色":"白色","材质":"100%棉","尺码":"M-XXL"}'),
(19, 9, '轻薄羽绒服', '90%白鹅绒 · 可收纳', '优衣库', 799.00, 599.00, 600, 4500, 4.7, 1, 0, 1, 1, '["羽绒服","保暖","轻薄"]', '{"颜色":"黑色","材质":"90%白鹅绒","尺码":"M-XXL"}'),
(20, 9, '牛仔裤', '弹力棉 · 经典直筒', 'Levi''s', 699.00, 499.00, 500, 2800, 4.4, 1, 0, 0, 0, '["牛仔裤","休闲","百搭"]', '{"颜色":"深蓝","材质":"棉+弹力纤维","尺码":"28-36"}'),

-- 女装 (category_id=10)
(21, 10, '法式碎花连衣裙', '雪纺面料 · 收腰设计', '韩都衣舍', 399.00, 299.00, 400, 2200, 4.5, 1, 1, 1, 1, '["连衣裙","夏季","碎花"]', '{"颜色":"蓝色碎花","材质":"雪纺","尺码":"S-XL"}'),
(22, 10, '羊毛大衣', '双面羊绒 · 宽松版型', '伊芙丽', 2599.00, 1999.00, 200, 890, 4.7, 1, 0, 1, 1, '["大衣","冬季","羊毛"]', '{"颜色":"驼色","材质":"90%羊毛","尺码":"S-XL"}'),
(23, 10, '真丝衬衫', '100%桑蚕丝 · 优雅通勤', '之禾', 1299.00, 999.00, 150, 650, 4.6, 1, 0, 0, 1, '["衬衫","通勤","真丝"]', '{"颜色":"米白色","材质":"100%桑蚕丝","尺码":"S-XL"}'),
(24, 10, '运动休闲卫衣', '加绒 · 宽松落肩', '耐克', 499.00, 399.00, 700, 3800, 4.5, 1, 1, 1, 1, '["卫衣","运动","休闲"]', '{"颜色":"灰色","材质":"棉+聚酯纤维","尺码":"S-XL"}'),

-- 运动鞋 (category_id=11)
(25, 11, 'Nike Air Jordan 1 High OG', '经典复刻 · 芝加哥配色', '耐克', 1599.00, 1299.00, 300, 5000, 4.8, 1, 1, 1, 1, '["球鞋","潮流","经典"]', '{"颜色":"黑红","尺码":"39-45","材质":"皮革"}'),
(26, 11, 'Adidas Ultraboost Light', 'BOOST中底 · 轻弹脚感', '阿迪达斯', 1299.00, 899.00, 500, 3200, 4.7, 1, 1, 1, 1, '["跑鞋","运动","舒适"]', '{"颜色":"白色","尺码":"38-45","材质":"针织"}'),
(27, 11, 'Vans Old Skool 经典款', '滑板鞋 · 棋盘格元素', 'Vans', 569.00, 469.00, 600, 4500, 4.5, 1, 0, 1, 1, '["板鞋","潮流","经典"]', '{"颜色":"黑白","尺码":"36-44","材质":"帆布+皮革"}'),
(28, 11, 'New Balance 990v6', '美产 · 总统慢跑鞋', 'New Balance', 1899.00, 1599.00, 200, 1200, 4.6, 1, 0, 0, 1, '["跑鞋","复古","舒适"]', '{"颜色":"灰色","尺码":"38-45","材质":"网面+皮革"}'),

-- 食品饮料 (category_id=3)
(29, 3, '云南普洱茶 357g', '古树熟茶 · 陈香醇厚', '大益', 399.00, 299.00, 1000, 5600, 4.8, 1, 1, 1, 1, '["茶叶","普洱","礼品"]', '{"规格":"357g/饼","年份":"2023","工艺":"熟茶"}'),
(30, 3, '特级龙井茶 250g', '明前采摘 · 西湖产区', '西湖牌', 599.00, 499.00, 500, 3200, 4.7, 1, 1, 1, 1, '["茶叶","绿茶","龙井"]', '{"规格":"250g","产地":"西湖","采摘":"明前"}'),
(31, 3, '进口坚果礼盒 1kg', '8种坚果混合 · 每日坚果', '三只松鼠', 199.00, 149.00, 2000, 12000, 4.5, 1, 0, 1, 1, '["零食","坚果","礼盒"]', '{"规格":"1kg","种类":"8种混合","保质期":"12个月"}'),
(32, 3, '有机黑巧克力礼盒', '72%可可含量 · 低糖', '德芙', 129.00, 99.00, 1500, 8000, 4.6, 1, 1, 1, 1, '["巧克力","礼品","低糖"]', '{"规格":"200g","可可含量":"72%","口味":"苦甜"}'),

-- 家居生活 (category_id=4)
(33, 4, '智能扫地机器人', 'LDS激光导航 · 5000Pa吸力', '石头科技', 3999.00, 3299.00, 400, 2800, 4.8, 1, 1, 1, 1, '["家电","清洁","智能"]', '{"颜色":"白色","吸力":"5000Pa","续航":"180分钟"}'),
(34, 4, '记忆棉护颈枕', '慢回弹 · 人体工学设计', 'MLILY', 299.00, 199.00, 800, 5200, 4.5, 1, 0, 1, 1, '["家居","枕头","护颈"]', '{"尺寸":"60x40cm","材质":"记忆棉","高度":"10/12cm"}'),
(35, 4, '电动升降桌', '双电机 · 1.6m 实木桌面', '乐歌', 2999.00, 2499.00, 200, 1200, 4.7, 1, 1, 0, 1, '["家具","办公","升降桌"]', '{"颜色":"胡桃色","尺寸":"160x75cm","升降范围":"72-120cm"}'),
(36, 4, '空气炸锅 5.5L', '可视窗口 · 不粘锅涂层', '飞利浦', 599.00, 449.00, 600, 6800, 4.4, 1, 0, 1, 1, '["家电","厨房","空气炸锅"]', '{"容量":"5.5L","功率":"1500W","颜色":"黑色"}'),

-- 图书音像 (category_id=5)
(37, 5, '深入理解Java虚拟机（第3版）', '周志明 著 · 程序员必读', '机械工业出版社', 129.00, 99.00, 3000, 25000, 4.9, 1, 1, 1, 1, '["书籍","编程","Java"]', '{"作者":"周志明","出版社":"机械工业出版社","ISBN":"9787111641247"}'),
(38, 5, 'Spring实战（第6版）', 'Craig Walls 著 · 全面升级', '人民邮电出版社', 119.00, 89.00, 2500, 18000, 4.8, 1, 1, 1, 1, '["书籍","编程","Spring"]', '{"作者":"Craig Walls","出版社":"人民邮电出版社","ISBN":"9787115612345"}'),
(39, 5, '算法导论（第4版）', 'CLRS 经典巨著', '机械工业出版社', 139.00, 109.00, 2000, 12000, 4.8, 1, 1, 1, 1, '["书籍","编程","算法"]', '{"作者":"Thomas H. Cormen","出版社":"机械工业出版社","ISBN":"9787115678901"}'),
(40, 5, '设计模式：可复用面向对象软件的基础', 'GoF 四人帮经典', '机械工业出版社', 79.00, 59.00, 3500, 20000, 4.7, 1, 0, 1, 1, '["书籍","编程","设计模式"]', '{"作者":"Erich Gamma","出版社":"机械工业出版社","ISBN":"9787111612345"}'),

-- 更多商品补充到 50 个
(41, 6, '魅族 21 Pro', '骁龙8 Gen3 · 无界设计', '魅族', 4999.00, 4499.00, 250, 800, 4.5, 1, 0, 0, 0, '["5G","旗舰","直屏"]', '{"颜色":"魅族白","存储":"256GB","屏幕":"6.79英寸"}'),
(42, 7, '机械键盘 87键', '热插拔轴体 · RGB背光', 'Keychron', 599.00, 499.00, 800, 5600, 4.6, 1, 1, 1, 1, '["键盘","外设","机械"]', '{"轴体":"茶轴","布局":"87键","连接":"三模"}'),
(43, 7, '4K显示器 27英寸', 'IPS面板 · Type-C 65W', '戴尔', 3999.00, 3499.00, 300, 1800, 4.7, 1, 1, 1, 1, '["显示器","4K","办公"]', '{"尺寸":"27英寸","分辨率":"3840x2160","面板":"IPS"}'),
(44, 8, '索尼 WH-1000XM5', '旗舰降噪 · 30小时续航', '索尼', 2999.00, 2499.00, 400, 3500, 4.8, 1, 1, 1, 1, '["耳机","降噪","蓝牙"]', '{"颜色":"黑色","续航":"30小时","降噪":"自适应"}'),
(45, 9, 'Polo衫 纯棉珠地', '经典翻领 · 商务休闲', '七匹狼', 299.00, 199.00, 900, 4500, 4.4, 1, 0, 0, 0, '["Polo","休闲","商务"]', '{"颜色":"藏青色","材质":"100%棉","尺码":"M-XXL"}'),
(46, 10, '百褶裙 A字半身裙', '高腰显瘦 · 通勤百搭', '优衣库', 299.00, 249.00, 500, 3200, 4.5, 1, 1, 1, 1, '["裙子","半身裙","百褶"]', '{"颜色":"黑色","材质":"聚酯纤维","尺码":"S-XL"}'),
(47, 11, 'Asics Kayano 30', '稳定支撑 · 顶级缓震', '亚瑟士', 1399.00, 1099.00, 350, 2100, 4.7, 1, 0, 1, 1, '["跑鞋","稳定","缓震"]', '{"颜色":"蓝色","尺码":"38-45","科技":"FF BLAST+ECO"}'),
(48, 3, '精品咖啡豆 500g', '阿拉比卡 · 中度烘焙', '星巴克', 199.00, 159.00, 1000, 7200, 4.5, 1, 0, 1, 1, '["咖啡","豆子","烘焙"]', '{"规格":"500g","产地":"哥伦比亚","烘焙度":"中度"}'),
(49, 4, '智能灯泡 彩色版', 'WiFi直连 · 语控调光', 'Yeelight', 79.00, 59.00, 3000, 15000, 4.3, 1, 0, 1, 1, '["灯具","智能","家居"]', '{"颜色":"彩色","接口":"E27","功率":"9W"}'),
(50, 5, 'Python编程：从入门到实践（第3版）', 'Eric Matthes 著 · 零基础', '人民邮电出版社', 89.00, 69.00, 4000, 28000, 4.8, 1, 1, 1, 1, '["书籍","编程","Python"]', '{"作者":"Eric Matthes","出版社":"人民邮电出版社","ISBN":"9787115612346"}');

-- ---------------------------------------------------------------------------
-- 示例地址
-- ---------------------------------------------------------------------------
INSERT INTO `addresses` (`user_id`, `consignee`, `phone`, `province`, `city`, `district`, `detail`, `zip_code`, `is_default`, `label`) VALUES
(1, '管理员', '13800000001', '北京市', '北京市', '朝阳区', '建国路88号SOHO现代城A座1208', '100022', 1, '公司'),
(2, '张三', '13800000002', '上海市', '上海市', '浦东新区', '张江高科技园区博云路2号', '201203', 1, '公司'),
(2, '张三', '13800000002', '上海市', '上海市', '闵行区', '古美路1000弄12号302室', '201102', 0, '家'),
(3, '李四', '13800000003', '广东省', '深圳市', '南山区', '科技园南区粤海街道R2-B栋', '518057', 1, '公司'),
(4, '王五', '13800000004', '浙江省', '杭州市', '西湖区', '文三路478号华星科技大厦', '310012', 1, '公司'),
(5, '赵六', '13800000005', '四川省', '成都市', '高新区', '天府大道北段1700号环球中心', '610041', 1, '公司');

-- ---------------------------------------------------------------------------
-- 示例订单（每个用户 1-2 个订单）
-- ---------------------------------------------------------------------------
INSERT INTO `orders` (`id`, `order_no`, `user_id`, `total_amount`, `discount_amount`, `pay_amount`, `status`, `payment_method`, `payment_time`, `consignee`, `phone`, `address`, `remark`, `order_source`, `created_at`) VALUES
(1, 'ORD202608010001', 1, 9999.00, 1000.00, 8999.00, 4, 2, '2026-08-01 10:30:00', '管理员', '13800000001', '北京市朝阳区建国路88号SOHO现代城A座1208', '请尽快发货', 0, '2026-08-01 10:00:00'),
(2, 'ORD202608010002', 2, 1599.00, 300.00, 1299.00, 5, 1, '2026-08-01 14:00:00', '张三', '13800000002', '上海市浦东新区张江高科技园区博云路2号', NULL, 0, '2026-08-01 13:30:00'),
(3, 'ORD202608020001', 2, 299.00, 0.00, 299.00, 1, 2, '2026-08-02 09:15:00', '张三', '13800000002', '上海市闵行区古美路1000弄12号302室', NULL, 0, '2026-08-02 09:00:00'),
(4, 'ORD202608030001', 3, 3999.00, 0.00, 3999.00, 2, 1, '2026-08-03 11:00:00', '李四', '13800000003', '广东省深圳市南山区科技园南区粤海街道R2-B栋', NULL, 0, '2026-08-03 10:30:00'),
(5, 'ORD202608050001', 4, 599.00, 150.00, 449.00, 3, 2, '2026-08-05 16:45:00', '王五', '13800000004', '浙江省杭州市西湖区文三路478号华星科技大厦', '放快递柜', 0, '2026-08-05 16:00:00'),
(6, 'ORD202608100001', 5, 1299.00, 0.00, 1299.00, 0, NULL, NULL, '赵六', '13800000005', '四川省成都市高新区天府大道北段1700号环球中心', NULL, 0, '2026-08-10 20:00:00'),
(7, 'ORD202608150001', 2, 298.00, 0.00, 298.00, 1, 2, '2026-08-15 08:30:00', '张三', '13800000002', '上海市浦东新区张江高科技园区博云路2号', '周末派送', 0, '2026-08-15 08:00:00');

-- ---------------------------------------------------------------------------
-- 示例订单项
-- ---------------------------------------------------------------------------
INSERT INTO `order_items` (`order_id`, `product_id`, `product_name`, `product_image`, `price`, `quantity`, `subtotal`) VALUES
(1, 1, 'iPhone 15 Pro Max 256GB', 'https://example.com/images/iphone15pm.jpg', 8999.00, 1, 8999.00),
(2, 25, 'Nike Air Jordan 1 High OG', 'https://example.com/images/aj1.jpg', 1299.00, 1, 1299.00),
(3, 18, '纯棉休闲衬衫', 'https://example.com/images/shirt.jpg', 199.00, 1, 199.00),
(3, 20, '牛仔裤', 'https://example.com/images/jeans.jpg', 100.00, 1, 100.00),
(4, 33, '智能扫地机器人', 'https://example.com/images/robot.jpg', 3299.00, 1, 3299.00),
(4, 49, '智能灯泡 彩色版', 'https://example.com/images/bulb.jpg', 59.00, 2, 118.00),
(4, 36, '空气炸锅 5.5L', 'https://example.com/images/airfryer.jpg', 449.00, 1, 449.00),
(5, 36, '空气炸锅 5.5L', 'https://example.com/images/airfryer.jpg', 449.00, 1, 449.00),
(6, 26, 'Adidas Ultraboost Light', 'https://example.com/images/ultraboost.jpg', 899.00, 1, 899.00),
(6, 27, 'Vans Old Skool 经典款', 'https://example.com/images/vans.jpg', 469.00, 1, 469.00),
(7, 31, '进口坚果礼盒 1kg', 'https://example.com/images/nuts.jpg', 149.00, 2, 298.00);

-- ---------------------------------------------------------------------------
-- 示例支付记录
-- ---------------------------------------------------------------------------
INSERT INTO `payments` (`payment_no`, `order_id`, `order_no`, `user_id`, `amount`, `payment_method`, `status`, `trade_no`, `callback_time`, `created_at`) VALUES
('PAY2026080110001', 1, 'ORD202608010001', 1, 8999.00, 2, 1, '2026000000000001', '2026-08-01 10:30:05', '2026-08-01 10:30:00'),
('PAY2026080114002', 2, 'ORD202608010002', 2, 1299.00, 1, 1, '2026000000000002', '2026-08-01 14:00:05', '2026-08-01 14:00:00'),
('PAY2026080209003', 3, 'ORD202608020001', 2, 299.00, 2, 1, '2026000000000003', '2026-08-02 09:15:05', '2026-08-02 09:15:00'),
('PAY2026080311004', 4, 'ORD202608030001', 3, 3999.00, 1, 1, '2026000000000004', '2026-08-03 11:00:05', '2026-08-03 11:00:00'),
('PAY2026080516005', 5, 'ORD202608050001', 4, 449.00, 2, 1, '2026000000000005', '2026-08-05 16:45:05', '2026-08-05 16:45:00'),
('PAY2026081508006', 7, 'ORD202608150001', 2, 298.00, 2, 1, '2026000000000006', '2026-08-15 08:30:05', '2026-08-15 08:30:00');

-- ---------------------------------------------------------------------------
-- 示例商品评价
-- ---------------------------------------------------------------------------
INSERT INTO `reviews` (`product_id`, `user_id`, `order_id`, `rating`, `content`, `is_anonymous`, `is_show`) VALUES
(1, 1, 1, 5, '钛金属质感很好，A17 Pro 芯片性能强劲，续航明显提升！', 0, 1),
(25, 2, 2, 4, '经典配色，尺码标准，就是价格有点贵。', 0, 1),
(36, 4, 5, 5, '非常好用，炸薯条和鸡翅都很酥脆，清洗也很方便。', 0, 1),
(33, 3, 4, 4, '路径规划很智能，避障能力不错，就是噪音有点大。', 0, 1);

-- =============================================================================
-- 结束
-- =============================================================================