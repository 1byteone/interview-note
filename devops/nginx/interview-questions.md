# Nginx 面试题大全

## 📚 知识体系

```
Nginx 核心功能
├── 反向代理
├── 负载均衡
├── 静态资源服务
├── SSL/TLS 终止
├── HTTP/HTTPS
├── 缓存
├── 压缩 (Gzip)
├── 限流
├── 日志
└── 安全防护

Nginx 架构
├── Master-Worker 进程模型
├── 事件驱动 (epoll)
├── 异步非阻塞
├── 多 Worker 负载均衡
└── 热重载 (reload)

Nginx 配置
├── http 模块
├── server 模块
├── location 模块
├── upstream 模块
└── 变量与指令
```

---

## 🎯 Level 1：基础题

### 1. Nginx 是什么？为什么用 Nginx？
**答案**：
Nginx 是一个高性能的 HTTP 服务器、反向代理服务器和负载均衡器。

**核心优势**：
1. **高性能**：单台可支撑数万并发连接
2. **低资源消耗**：采用事件驱动异步非阻塞
3. **高稳定性**：Master-Worker 进程模型
4. **高扩展性**：模块化设计（第三方模块丰富）
5. **功能丰富**：反向代理、负载均衡、缓存、SSL

### 2. Nginx 的进程模型是什么？
**答案**：

```text
Master 进程（主进程）
├── 读取配置
├── 管理 Worker 进程
└── 热重载 (reload)

Worker 进程（多个）
├── 处理请求
├── 事件循环 (epoll)
└── 相互独立
```

**特点**：
- 一个 Master 管理多个 Worker
- Worker 之间通过共享内存通信
- 热重载：`nginx -s reload` 不中断服务
- 平滑升级：新 Worker 处理新请求，旧 Worker 处理旧请求

### 3. 什么是正向代理和反向代理？
**答案**：

**正向代理**：
```
客户端 → 代理服务器 → 目标服务器
```
- 代理服务器在客户端一侧
- 用途：翻墙、访问控制、缓存

**反向代理**：
```
客户端 → Nginx（反向代理） → 后端服务器集群
```
- 代理服务器在服务端一侧
- 用途：负载均衡、隐藏后端、安全防护

---

## 🎯 Level 2：进阶题

### 4. Nginx 如何配置负载均衡？
**答案**：

**upstream 配置**：
```nginx
upstream backend_servers {
    # 轮询（默认）
    server 192.168.1.10:8080;
    server 192.168.1.11:8080;
    
    # 权重（weight）
    server 192.168.1.12:8080 weight=3;
    server 192.168.1.13:8080 weight=1;
    
    # 失败处理
    server 192.168.1.14:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    
    location /api/ {
        proxy_pass http://backend_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

**负载均衡算法**：

| 算法 | 说明 | 场景 |
|------|------|------|
| 轮询（round-robin） | 默认，依次分发 | 无状态服务 |
| 权重（weight） | 按权重比例分发 | 异构服务器 |
| IP Hash | 按客户端 IP 哈希 | Session 保持 |
| Least Conn | 分发到连接数最少 | 长连接场景 |
| URL Hash | 按 URL 哈希 | 缓存命中 |

---

## 🎯 Level 3：高级题

### 5. Nginx 如何实现限流？
**答案**：

**按 IP 限流**：
```nginx
# 定义限流区域（内存共享）
limit_req_zone $binary_remote_addr zone=mylimit:10m rate=10r/s;

server {
    location /api/ {
        # 限流：burst 20 个缓冲，nodelay 立即处理
        limit_req zone=mylimit burst=20 nodelay;
        proxy_pass http://backend_servers;
    }
}
```

**连接数限流**：
```nginx
# 每 IP 最多 10 个并发连接
limit_conn_zone $binary_remote_addr zone=perip:10m;

server {
    location / {
        limit_conn perip 10;
        limit_conn_status 503;
        limit_conn_log_level error;
    }
}
```

### 6. Nginx 如何配置 HTTPS？
**答案**：

```nginx
server {
    listen 443 ssl;
    server_name example.com;
    
    # SSL 证书
    ssl_certificate /etc/nginx/ssl/example.com.crt;
    ssl_certificate_key /etc/nginx/ssl/example.com.key;
    
    # 协议和加密套件
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    
    # 安全增强
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    
    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Frame-Options SAMEORIGIN;
    add_header X-Content-Type-Options nosniff;
    
    location / {
        proxy_pass http://backend_servers;
    }
}

# HTTP 跳转 HTTPS
server {
    listen 80;
    server_name example.com;
    return 301 https://$host$request_uri;
}
```

---

## 🎯 Level 4：专家题

### 7. Nginx 性能优化有哪些方法？
**答案**：

**worker 配置**：
```nginx
worker_processes auto;          # 与 CPU 核数一致
worker_rlimit_nofile 65535;     # 文件描述符限制

events {
    worker_connections 10240;    # 每个 Worker 连接数
    use epoll;                   # 事件驱动模型
    multi_accept on;             # 一次接受多个连接
}
```

**HTTP 配置**：
```nginx
http {
    # 开启 Gzip 压缩
    gzip on;
    gzip_min_length 1k;
    gzip_types text/plain text/css application/json text/javascript
               application/xml application/javascript;
    gzip_comp_level 5;
    
    # 静态文件缓存
    location ~* \.(jpg|png|js|css)$ {
        root /var/www/static;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
    
    # 上传大小
    client_max_body_size 50m;
    
    # 开启 keepalive
    keepalive_timeout 65;
}
```

---

## 📖 学习资源

### 推荐项目
- [Nginx 官方文档](https://nginx.org/en/docs/)
- [nginx-proxy](https://github.com/nginx-proxy/nginx-proxy) - Docker 自动反向代理
- [Nginx 配置大全](https://github.com/digitalocean/nginxconfig.io)

### 最佳实践
1. Worker 数与 CPU 核数一致
2. 生产环境开启 Gzip 压缩
3. 静态资源设置长缓存
4. 配置合理的限流保护后端
5. HTTPS 必须配置（HSTS、TLS1.3）
6. 日志切割（logrotate）