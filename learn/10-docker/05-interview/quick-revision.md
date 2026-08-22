# 速记版 — 25 个高频考点一句话版

> 等级：🎯 面试冲刺
> 目标：考前 30 分钟快速回顾，镜像/容器/网络/编排/安全 各 5 个考点。

---

## 一、镜像与容器（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 镜像 vs 容器 | 镜像是只读模板，容器是镜像的运行实例，多了一层可写层 |
| 2 | 镜像分层原理 | 多层只读叠加，容器启动时加可写层，写时复制（Copy-on-Write） |
| 3 | 多阶段构建 | 第一阶段构建（大镜像），第二阶段运行（小镜像），缩小体积 60%+ |
| 4 | 容器生命周期 | create → start → running → stop → rm，核心是进程管理 |
| 5 | 容器退出码 | 0 正常退出，137 SIGKILL(OOM)，143 SIGTERM(优雅关闭) |

---

## 二、Dockerfile 指令（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | FROM | 指定基础镜像，尽量用 alpine / -slim 变体 |
| 2 | COPY vs ADD | COPY 复制文件，ADD 支持自动解压，建议用 COPY |
| 3 | CMD vs ENTRYPOINT | CMD 可被覆盖，ENTRYPOINT 固定主程序，可组合使用 |
| 4 | RUN 合并 | 多行 RUN 用 && 合并为一行，减少镜像层数 |
| 5 | .dockerignore | 排除无关文件，减小构建上下文，加速构建 |

---

## 三、网络与数据卷（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 四种网络模式 | bridge（默认）、host（共享宿主机）、none（无网络）、container（共享其他容器） |
| 2 | 容器间通信 | 同一自定义网络内，通过容器名 + DNS 解析访问 |
| 3 | Volume vs Bind Mount | Volume 由 Docker 管理，Bind Mount 挂宿主机目录 |
| 4 | 数据持久化关键 | 数据库容器必须挂载 Volume，否则删除容器数据全丢 |
| 5 | 写时复制原理 | 修改只读层文件时，先复制到可写层再修改，原文件不变 |

---

## 四、Docker Compose（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 三要素 | services / networks / volumes |
| 2 | depends_on | 保证启动顺序，但不等同于就绪，需配合 healthcheck |
| 3 | healthcheck | 定义健康检查命令，condition: service_healthy 等待依赖就绪 |
| 4 | 环境变量注入 | environment 直接写 / env_file 加载文件 / 引用 .env 变量 |
| 5 | down -v 风险 | 连带删除命名数据卷，数据库数据会丢失，生产慎用 |

---

## 五、K8s 与安全（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | Pod | 最小调度单元，一个或多个共享网络和存储的容器 |
| 2 | Deployment vs StatefulSet | Deployment 无状态应用，StatefulSet 有状态应用（稳定身份） |
| 3 | Service vs Ingress | Service 集群内访问，Ingress 外部域名路由 |
| 4 | 容器安全三原则 | 非 root 运行、最小权限（cap_drop）、镜像扫描 |
| 5 | 资源限制 | memory 不可压缩（超限 OOM Kill），CPU 可压缩（超限节流） |

---

## 速记口诀

```
镜像容器分两层：只读模板 vs 可写实例
Dockerfile 五指令：FROM / COPY / RUN / CMD / ENTRYPOINT
网络模式四选一：bridge 默认 / host 共享 / none 隔离 / container 共用
持久化三件套：Volume 推荐 / Bind 开发 / tmpfs 内存
Compose 三要素：services 定义 / networks 连接 / volumes 存储
K8s 四核心：Pod 最小 / Deployment 编排 / Service 发现 / Ingress 入口
安全三底线：非 root / 最小权限 / 漏洞扫描
```

> 进入深挖题篇：容器运行时原理、Namespace、Cgroup 深度分析。