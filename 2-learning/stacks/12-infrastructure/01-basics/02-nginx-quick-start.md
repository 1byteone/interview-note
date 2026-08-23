# Nginx 快速入门

## 安装与启动

### 方式一：Docker 运行（推荐）

```bash
# 拉取镜像
docker pull nginx:stable-alpine

# 启动容器
docker run -d --name nginx-demo -p 80:80 nginx:stable-alpine

# 挂载自定义配置
docker run -d --name nginx-demo \
  -p 80:80 \
  -v /path/to/nginx.conf:/etc/nginx/nginx.conf:ro \
  -v /path/to/html:/usr/share/nginx/html:ro \
  nginx:stable-alpine
```

### 方式二：系统安装

```bash
# Ubuntu / Debian
sudo apt update && sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx

# CentOS / RHEL
sudo yum install epel-release -y
sudo yum install nginx -y
sudo systemctl start nginx
```

安装后访问 `http://localhost` 即可看到 Nginx 欢迎页面。

## 静态资源服务

```nginx
server {
    listen 80;
    server_name static.example.com;

    root /var/www/ai-mall-static;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /images/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    location /assets/ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}
```

关键指令：
- `root` — 指定静态文件根目录
- `index` — 默认首页文件
- `try_files` — 按顺序尝试查找文件，用于 SPA 路由回退
- `expires` — 设置缓存过期时间

## 反向代理

```nginx
server {
    listen 80;
    server_name api.example.com;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ai/ {
        proxy_pass http://127.0.0.1:9090/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

`proxy_pass` 末尾的 `/` 很关键：带 `/` 会截断匹配的 location 前缀再转发，不带 `/` 则将完整路径原样转发。

## 负载均衡

```nginx
upstream ai-mall-backend {
    # 轮询（默认）
    server 192.168.1.10:8080;
    server 192.168.1.11:8080;
    server 192.168.1.12:8080;
}

upstream ai-mall-weighted {
    # 权重：性能好的机器分配更多请求
    server 192.168.1.10:8080 weight=3;
    server 192.168.1.11:8080 weight=2;
    server 192.168.1.12:8080 weight=1;
}

upstream ai-mall-ip-hash {
    # IP 哈希：同一客户端始终打到同一台服务器
    ip_hash;
    server 192.168.1.10:8080;
    server 192.168.1.11:8080;
}

upstream ai-mall-least-conn {
    # 最少连接：转发给当前活跃连接数最少的服务器
    least_conn;
    server 192.168.1.10:8080;
    server 192.168.1.11:8080;
}

server {
    listen 80;
    server_name mall.example.com;

    location / {
        proxy_pass http://ai-mall-backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 负载均衡策略对比

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| 轮询（默认） | 请求依次分发到各服务器 | 服务器配置相近 |
| 权重 | 按比例分配请求 | 服务器性能不同 |
| IP 哈希 | 同一 IP 固定到同一服务器 | 需要 session 粘性 |
| 最少连接 | 转发给活跃连接最少的服务器 | 请求处理时间差异大 |

## 最小案例：AI 商城前端 + 后端代理

```nginx
upstream backend-servers {
    server 127.0.0.1:8080 weight=2;
    server 127.0.0.1:8081 weight=1;
}

upstream ai-servers {
    server 127.0.0.1:9090;
}

server {
    listen 80;
    server_name ai-mall.local;

    # 前端静态资源
    location / {
        root /var/www/ai-mall-frontend/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://backend-servers/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # AI 推荐服务
    location /ai-api/ {
        proxy_pass http://ai-servers/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # AI 接口可能耗时较长，调大超时时间
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
    }
}
```

启动方式：

```bash
# 后端服务
java -jar ai-mall-user.jar --server.port=8080
java -jar ai-mall-order.jar --server.port=8081
java -jar ai-mall-recommend.jar --server.port=9090

# 前端构建
cd ai-mall-frontend && npm run build

# Nginx（Docker）
docker run -d --name ai-mall-nginx \
  -p 80:80 \
  -v /path/to/nginx.conf:/etc/nginx/conf.d/ai-mall.conf:ro \
  -v /path/to/ai-mall-frontend/dist:/var/www/ai-mall-frontend/dist:ro \
  nginx:stable-alpine
```

这个最小案例覆盖了 Nginx 最核心的三种用途：静态资源托管、反向代理、负载均衡。在实际生产环境中，还会在此基础上叠加 HTTPS 证书、限流规则、日志切割、健康检查等增强配置。