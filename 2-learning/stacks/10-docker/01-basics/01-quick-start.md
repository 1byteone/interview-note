# 快速入门 — 安装 · 核心概念 · 常用命令 · 最小案例

> 等级：👶 新手通道
> 目标：安装 Docker，理解核心概念，掌握常用命令，运行第一个容器。

---

## 一、安装 Docker

### 1.1 Docker Desktop（Windows / macOS 推荐）

访问 https://www.docker.com/products/docker-desktop/ 下载安装包。

安装完成后，打开终端验证：

```bash
docker --version
# Docker version 26.1.4, build 5650f9b

docker info
# 查看 Docker 引擎状态
```

### 1.2 Linux 安装

```bash
# Ubuntu / Debian
curl -fsSL https://get.docker.com | bash

# 启动 Docker 并设置开机自启
sudo systemctl enable docker
sudo systemctl start docker

# 将当前用户加入 docker 组（避免每次 sudo）
sudo usermod -aG docker $USER
# 退出重新登录生效
```

### 1.3 验证安装

```bash
docker run hello-world
# 输出 Hello from Docker! 表示安装成功
```

---

## 二、核心概念

| 概念 | 说明 | 类比 |
|------|------|------|
| **镜像（Image）** | 只读模板，包含运行环境、代码、依赖 | 类的定义 |
| **容器（Container）** | 镜像的运行实例，可读可写 | 类的实例 |
| **仓库（Registry）** | 存储和分发镜像的服务 | 代码仓库 |
| **数据卷（Volume）** | 持久化存储容器数据 | U 盘 |
| **网络（Network）** | 容器间通信的通道 | 网线 |

### 镜像 vs 容器

```
镜像（Image）                   容器（Container）
┌──────────────┐               ┌──────────────┐
│ 只读层 3     │               │ 可写层       │ ← 容器运行时修改
├──────────────┤               ├──────────────┤
│ 只读层 2     │               │ 只读层 3     │
├──────────────┤               ├──────────────┤
│ 只读层 1     │               │ 只读层 2     │
├──────────────┤               ├──────────────┤
│ 基础镜像     │               │ 只读层 1     │
└──────────────┘               ├──────────────┤
                               │ 基础镜像     │
                               └──────────────┘
```

**关键区别**：镜像是静态的，容器是动态的。一个镜像可以启动多个容器。

---

## 三、常用命令

### 3.1 镜像管理

```bash
# 拉取镜像
docker pull nginx:alpine
docker pull mysql:8.0

# 列出本地镜像
docker images

# 删除镜像
docker rmi nginx:alpine

# 构建镜像
docker build -t my-app:1.0 .
```

### 3.2 容器管理

```bash
# 运行容器（前台）
docker run nginx:alpine

# 运行容器（后台）
docker run -d --name my-nginx -p 8080:80 nginx:alpine

# 列出运行中的容器
docker ps

# 列出所有容器（包括已停止的）
docker ps -a

# 停止容器
docker stop my-nginx

# 启动已停止的容器
docker start my-nginx

# 重启容器
docker restart my-nginx

# 删除容器
docker rm my-nginx

# 强制删除运行中的容器
docker rm -f my-nginx
```

### 3.3 进入容器

```bash
# 进入正在运行的容器
docker exec -it my-nginx /bin/sh

# 查看容器日志
docker logs my-nginx
docker logs -f my-nginx    # 实时跟踪日志

# 查看容器详细信息
docker inspect my-nginx

# 查看容器资源占用
docker stats
```

### 3.4 清理命令

```bash
# 清理所有未使用的容器、网络、镜像
docker system prune

# 清理所有未使用的镜像
docker image prune

# 清理所有已停止的容器
docker container prune
```

---

## 四、最小案例：运行 Nginx + MySQL

### 案例 1：Nginx 静态页面

```bash
# 拉取并运行 Nginx
docker run -d \
  --name web-server \
  -p 8080:80 \
  nginx:alpine

# 访问 http://localhost:8080，看到 Nginx 欢迎页

# 创建自定义页面
mkdir -p ~/docker-html
echo "<h1>Hello Docker!</h1>" > ~/docker-html/index.html

# 挂载本地目录到容器
docker run -d \
  --name web-server-custom \
  -p 8081:80 \
  -v ~/docker-html:/usr/share/nginx/html:ro \
  nginx:alpine

# 访问 http://localhost:8081，看到自定义页面
```

### 案例 2：MySQL 数据库

```bash
# 运行 MySQL 容器
docker run -d \
  --name mysql-db \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=testdb \
  -v mysql_data:/var/lib/mysql \
  mysql:8.0

# 连接 MySQL
docker exec -it mysql-db mysql -uroot -proot123

# 创建表并插入数据
mysql> USE testdb;
mysql> CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50));
mysql> INSERT INTO users (name) VALUES ('Alice'), ('Bob');
mysql> SELECT * FROM users;
```

### 案例 3：容器间通信

```bash
# 创建自定义网络
docker network create my-network

# 在同一个网络启动容器
docker run -d --name app1 --network my-network alpine sleep 3600
docker run -d --name app2 --network my-network alpine sleep 3600

# 容器间通过容器名通信
docker exec app1 ping app2
```

---

## 五、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Docker 镜像和容器的区别？ | 镜像是只读模板，容器是镜像的运行实例，多了可写层 |
| docker run -d 和 -it 的区别？ | -d 后台运行，-it 交互式前台运行 |
| docker exec 和 docker attach 的区别？ | exec 创建新进程，attach 连接到主进程 |
| 容器删除后数据会丢失吗？ | 容器内数据会丢失，但 Volume 挂载的数据会保留 |
| -p 8080:80 是什么意思？ | 宿主机 8080 端口映射到容器 80 端口 |

> 掌握了基础命令，下一节学习 Dockerfile：如何构建自己的镜像。