# Docker Swarm 与 Kubernetes — 集群编排 · 核心概念 · 选型

> 等级：🎯 面试进阶
> 目标：理解容器集群编排，掌握 Kubernetes 核心概念，学会 Docker 与 K8s 的选型。

---

## 一、为什么需要集群编排

单机 Docker Compose 有明确天花板：

| 问题 | 说明 |
|------|------|
| 单点故障 | 宿主机宕机，全部服务不可用 |
| 无法扩缩容 | 只能手动调整，无自动伸缩 |
| 无滚动更新 | 更新需停机，无法灰度 |
| 无服务发现 | 跨主机容器间通信困难 |
| 资源不隔离 | 无法按服务分配资源配额 |

**编排器**（Orchestrator）解决这些问题：调度、扩缩容、自愈、滚动更新、服务发现、负载均衡。

---

## 二、Docker Swarm 简介

### 2.1 核心概念

| 概念 | 说明 |
|------|------|
| **Manager 节点** | 集群管理，调度任务，维护状态 |
| **Worker 节点** | 运行任务，执行调度指令 |
| **Service** | 一个服务的期望状态定义 |
| **Task** | Service 的最小调度单元（一个容器） |
| **Stack** | 由 Compose 文件定义的完整应用集 |

### 2.2 基本操作

```bash
# 初始化 Swarm 集群（Manager）
docker swarm init --advertise-addr 192.168.1.10

# 添加 Worker 节点（输出会给出完整命令）
docker swarm join --token SWMTKN-1-xxx 192.168.1.10:2377

# 部署服务
docker service create --name web --replicas 3 -p 8080:80 nginx:alpine

# 查看服务
docker service ls

# 扩缩容
docker service scale web=5

# 滚动更新
docker service update --image nginx:1.25 web

# 查看节点
docker node ls
```

### 2.3 Swarm 的定位

- **优点**：内置 Docker、命令简单、学习成本低
- **缺点**：功能远弱于 K8s，无自动伸缩、无命名空间隔离、生态差
- **现状**：适合中小规模场景，但业界已基本转向 K8s

---

## 三、Kubernetes 核心概念

### 3.1 三种视角

```
应用视角（抽象）              运行视角（物理）
┌──────────────┐             ┌──────────────┐
│  Deployment  │             │  Node 1      │
│  (无状态应用)  │             │  ┌────────┐  │
│  StatefulSet │  调度到      │  │ Pod    │  │
│  (有状态应用)  │ ──────────► │  │ 容器   │  │
│  Service     │             │  │ 容器   │  │
│  Ingress     │             │  └────────┘  │
│  ConfigMap   │             │  Node 2      │
│  Secret      │             │  ┌────────┐  │
└──────────────┘             │  │ Pod    │  │
                             │  └────────┘  │
                             └──────────────┘
```

### 3.2 核心对象详解

| 对象 | 作用 | 类比 |
|------|------|------|
| **Pod** | 最小的调度单元，一个或多个容器共享网络和存储 | 一个"豆荚"，里面几个容器 |
| **Deployment** | 管理无状态应用副本、滚动更新、回滚 | 副本调度器 |
| **StatefulSet** | 管理有状态应用（数据库等） | 有身份的应用 |
| **Service** | 稳定的访问入口，负载均衡 + 服务发现 | 内部 DNS + VIP |
| **Ingress** | 外部访问入口，域名 + 路径路由 | 网关 |
| **ConfigMap** | 非敏感配置 | 配置文件 |
| **Secret** | 敏感配置（密码、证书） | 加密配置 |
| **Namespace** | 资源隔离分组 | 虚拟集群 |
| **PV/PVC** | 持久化存储 | 存储抽象 |

### 3.3 Deployment 示例

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mall-order-service
  namespace: mall
spec:
  replicas: 3                # 副本数
  selector:
    matchLabels:
      app: order
  template:
    metadata:
      labels:
        app: order
    spec:
      containers:
        - name: order
          image: registry.example.com/mall/order:1.0.0
          ports:
            - containerPort: 8082
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
          readinessProbe:              # 就绪探针
            httpGet:
              path: /actuator/health/readiness
              port: 8082
            initialDelaySeconds: 20
          livenessProbe:               # 存活探针
            httpGet:
              path: /actuator/health/liveness
              port: 8082
```

### 3.4 Service 与 Ingress 示例

```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: mall
spec:
  selector:
    app: order                    # 通过标签选择 Pod
  ports:
    - port: 80                    # Service 端口
      targetPort: 8082            # Pod 容器端口
  type: ClusterIP                 # 集群内访问
```

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mall-ingress
  namespace: mall
spec:
  rules:
    - host: mall.example.com
      http:
        paths:
          - path: /api/order
            pathType: Prefix
            backend:
              service:
                name: order-service
                port:
                  number: 80
```

### 3.5 常用命令

```bash
kubectl get pods -n mall                        # 查看 Pod
kubectl get deployments -n mall                 # 查看 Deployment
kubectl get svc -n mall                         # 查看 Service
kubectl logs -f pod/order-xxxxx -n mall         # 查看日志
kubectl scale deployment order --replicas=5     # 扩缩容
kubectl rollout status deployment/order         # 滚动更新状态
kubectl rollout undo deployment/order           # 回滚
kubectl apply -f deployment.yaml                # 应用配置
kubectl port-forward svc/order-service 8082:80  # 端口转发调试
```

---

## 四、minikube 快速入门

```bash
# 1. 安装 minikube（Windows 需配合 WSL2 或 Hyper-V）
minikube start --driver=docker --cpus=4 --memory=8g

# 2. 简单部署验证
kubectl create deployment nginx-demo --image=nginx:alpine
kubectl expose deployment nginx-demo --port=80 --type=NodePort
minikube service nginx-demo      # 打开浏览器访问

# 3. 部署自建镜像
docker build -t mall-order:1.0 .
# 本地镜像需先导入
minikube image load mall-order:1.0
kubectl create deployment order-demo --image=mall-order:1.0
```

---

## 五、Docker vs K8s 选型

| 维度 | Docker Compose | Docker Swarm | Kubernetes |
|------|---------------|--------------|------------|
| 定位 | 单机多容器 | 集群编排 | 企业级容器平台 |
| 学习成本 | ★☆☆☆☆ | ★★☆☆☆ | ★★★★★ |
| 自动扩缩容 | ❌ | 手动 scale | ✅ HPA 自动伸缩 |
| 服务发现 | 容器名 DNS | ✅ | ✅ Service |
| 滚动更新 | 手动 | ✅ | ✅ + 灰度策略 |
| 自愈 | ❌ | 重启丢失的容器 | ✅ 重建、重新调度 |
| 存储 | Volume | Volume | PV/PVC 持久化 |
| 配置管理 | env | env | ConfigMap/Secret |
| 适用场景 | 本地开发、小项目 | 中小规模集群 | 生产大规模集群 |

### 选型建议

```
20 个容器以内，单机 → Docker Compose
20-100 容器，多机，追求简单 → Docker Swarm（少用）
生产环境、超过百容器、需要 DevOps → Kubernetes
```

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Pod 和容器的关系？ | Pod 是最小调度单元，可包含多个紧密协作的容器 |
| Deployment 和 StatefulSet 区别？ | Deployment 无状态适合应用，StatefulSet 有稳定身份适合数据库 |
| Service 解决了什么问题？ | Pod IP 随时变化，Service 提供稳定访问入口 + 负载均衡 |
| Ingress 和 Service 区别？ | Service 集群内访问，Ingress 是外部入口做域名路径路由 |
| 探针有几种？ | readinessProbe 就绪、livenessProbe 存活、startupProbe 启动 |

> 掌握 K8s 概念后，下一节学习 CI/CD 流水线与私有镜像仓库。