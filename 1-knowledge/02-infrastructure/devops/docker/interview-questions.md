# Docker 面试题大全

## 📚 知识体系

```
Docker 核心概念
├── 镜像 (Image)
├── 容器 (Container)
├── Dockerfile
├── Docker Hub / Registry
├── 数据卷 (Volume)
├── 网络 (Network)
└── Docker Compose

Docker 高级特性
├── Dockerfile 多阶段构建
├── Docker 网络模式
├── Docker 存储驱动
├── Docker 安全
├── Docker 日志
├── Docker 监控
├── Docker Swarm
└── Docker BuildKit

Docker 实战
├── Spring Boot 容器化
├── 微服务容器化
├── 数据库容器化
├── Nginx 容器化
├── CI/CD 集成
├── 镜像优化
└── 容器编排
```

---

## 🎯 Level 1：基础题

### 1. Docker 的核心概念是什么？
**答案**：
- **镜像（Image）**：只读模板，包含运行环境
- **容器（Container）**：镜像的运行实例
- **仓库（Registry）**：存储和分发镜像（Docker Hub）
- **Dockerfile**：构建镜像的脚本

### 2. 镜像和容器的区别？
**答案**：

| 特性 | 镜像 | 容器 |
|------|------|------|
| 状态 | 只读静态 | 可读写运行 |
| 层级 | 多层叠加 | 镜像层 + 容器层（可写） |
| 生命周期 | 持久 | 临时 |
| 使用方式 | 构建、分发 | 运行、停止、删除 |
| 类比 | 类（Class） | 实例（Instance） |

### 3. 如何编写高效的 Dockerfile？
**答案**：
```dockerfile
# 1. 选择合适的基础镜像
FROM eclipse-temurin:17-jre-alpine

# 2. 设置工作目录
WORKDIR /app

# 3. 先复制依赖文件，利用缓存
COPY pom.xml .
RUN mvn dependency:resolve

# 4. 再复制源码
COPY src ./src
RUN mvn package -DskipTests

# 5. 多阶段构建
FROM eclipse-temurin:17-jre-alpine
COPY --from=builder /app/target/*.jar app.jar

# 6. 非 root 用户运行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 7. 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 🎯 Level 2：进阶题

### 4. Docker 网络模式有哪些？
**答案**：

| 网络模式 | 说明 | 使用场景 |
|----------|------|----------|
| bridge | 默认，NAT 方式 | 单机容器通信 |
| host | 共享宿主机网络 | 性能敏感场景 |
| none | 无网络 | 安全隔离 |
| overlay | 跨主机覆盖网络 | Swarm/K8s 集群 |
| macvlan | 分配 MAC 地址 | 直接接入物理网络 |

### 5. Docker Compose 的作用？
**答案**：
Docker Compose 用于定义和运行多容器 Docker 应用，通过 `docker-compose.yml` 文件编排服务。

**示例**：
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: myapp
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/myapp
      SPRING_REDIS_HOST: redis
    depends_on:
      - mysql
      - redis

volumes:
  mysql_data:
```

---

## 🎯 Level 3：高级题

### 6. 如何优化 Docker 镜像大小？
**答案**：

**优化策略**：
1. **选择 alpine 基础镜像**：`eclipse-temurin:17-jre-alpine` (~50MB)
2. **多阶段构建**：构建环境与运行环境分离
3. **减少层数**：合并 RUN 命令
4. **清理缓存**：`apt clean`、`rm -rf /var/cache/*`
5. **使用 .dockerignore**：排除不需要的文件
6. **分层利用缓存**：不常变动的放前面

**优化对比**：
| 方案 | 大小 | 特点 |
|------|------|------|
| ubuntu + openjdk | ~400MB | 臃肿 |
| alpine + jre | ~150MB | 推荐 |
| distroless | ~100MB | 最安全 |
| Native Image (GraalVM) | ~30MB | 启动最快 |

### 7. Spring Boot 应用如何容器化部署？
**答案**：

**Dockerfile**：
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml**：
```yaml
version: '3.8'
services:
  gateway:
    build: ./gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    depends_on:
      - nacos
      - redis

  nacos:
    image: nacos/nacos-server:v2.2.3
    environment:
      MODE: standalone
    ports:
      - "8848:8848"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"

volumes:
  mysql_data:
```

---

## 🎯 Level 4：专家题

### 8. Docker Swarm 与 Kubernetes 的区别？
**答案**：

| 特性 | Docker Swarm | Kubernetes |
|------|--------------|------------|
| 安装复杂度 | 简单 | 复杂 |
| 学习曲线 | 低 | 高 |
| 功能丰富度 | 基础 | 丰富 |
| 自动伸缩 | 支持（基础） | 强大（HPA） |
| 服务发现 | 内置 DNS | Service + DNS |
| 负载均衡 | 内置 | Ingress + Service |
| 存储编排 | 基础 | 丰富（PV/PVC） |
| 社区生态 | 小 | 大 |
| 生产推荐 | 小规模 | 大规模 |

### 9. 容器化部署的监控方案？
**答案**：

**常见监控方案**：
```
Prometheus + Grafana + cAdvisor + Node Exporter
```

**监控指标**：
- CPU 使用率
- 内存使用率
- 磁盘 IO
- 网络 IO
- 容器状态
- 应用指标（JVM、请求数）

---

## 📖 学习资源

### 推荐项目
- [Docker 官方文档](https://docs.docker.com/)
- [awesome-compose](https://github.com/docker/awesome-compose) - Docker Compose 示例
- [nginx-proxy](https://github.com/nginx-proxy/nginx-proxy) - Nginx 反向代理

### 最佳实践
1. 使用 .dockerignore 减少构建上下文
2. 多阶段构建减少镜像体积
3. 容器内以非 root 用户运行
4. 合理设置资源限制（--memory/--cpus）
5. 日志统一收集到 stdout/stderr