-- ============================================
-- 示例数据
-- ============================================

USE ecommerce;

-- 用户
INSERT INTO users (username, email, phone, password) VALUES
('alice', 'alice@example.com', '13800138001', '$2a$10$e7k...'),
('bob', 'bob@example.com', '13800138002', '$2a$10$e7k...'),
('carol', 'carol@example.com', '13800138003', '$2a$10$e7k...');

-- 分类
INSERT INTO categories (id, name, parent_id, sort_order, level) VALUES
(1, '电子产品',  NULL, 1, 1),
(2, '手机',      1,    1, 2),
(3, '笔记本电脑', 1,    2, 2),
(4, '服装',      NULL, 2, 1),
(5, '男装',      4,    1, 2),
(6, '女装',      4,    2, 2);

-- 商品
INSERT INTO products (category_id, name, description, price, stock, sales) VALUES
(2, 'iPhone 15', 'Apple 旗舰手机', 6999.00, 100, 500),
(2, '华为 Mate 60', '华为旗舰手机', 5999.00, 80, 320),
(3, 'MacBook Pro 14', 'Apple M3 芯片', 14999.00, 30, 150),
(3, 'ThinkPad X1', '商务轻薄本', 9999.00, 50, 200),
(5, '纯棉T恤', '男士纯棉短袖', 99.00, 500, 1200),
(6, '连衣裙', '夏季新款碎花裙', 199.00, 300, 800);

-- 订单
INSERT INTO orders (order_no, user_id, total_amount, status, address) VALUES
('ORD202608220001', 1, 7098.00, 1, '北京市朝阳区xx路1号'),
('ORD202608220002', 1, 14999.00, 2, '北京市朝阳区xx路1号'),
('ORD202608220003', 2, 198.00, 0, '上海市浦东新区xx路2号');

-- 订单明细
INSERT INTO order_items (order_id, product_id, product_name, price, quantity, subtotal) VALUES
(1, 1, 'iPhone 15', 6999.00, 1, 6999.00),
(1, 5, '纯棉T恤', 99.00, 1, 99.00),
(2, 3, 'MacBook Pro 14', 14999.00, 1, 14999.00),
(3, 6, '连衣裙', 199.00, 1, 199.00);