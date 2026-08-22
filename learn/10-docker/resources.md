# 推荐资源

> Docker 相关的官方文档、书籍、视频、开源项目、实用工具。

---

## 一、书籍推荐

| 书名 | 难度 | 推荐理由 | 读完能解决什么问题 |
|------|------|---------|-------------------|
| 《Docker 实战》（Jeff Nickoloff） | 入门 | 从零开始，覆盖 Docker 基础命令和概念 | 快速上手 Docker |
| 《Docker 容器与容器云》（张磊） | 进阶 | 深入分析容器运行时、K8s 核心原理 | 理解容器和编排底层 |
| 《Kubernetes 权威指南》 | 进阶 | K8s 完整教材，从入门到生产实践 | 系统掌握 K8s 部署和运维 |
| 《深入剖析 Kubernetes》（张磊） | 进阶 | K8s 核心原理深度剖析，社区大佬之作 | 面试深挖题必备 |
| 《Cloud Native Patterns》 | 进阶 | 云原生设计模式，涵盖容器化、服务网格 | 架构师视角看容器化 |
| 《Docker 源码分析》 | 高级 | 深入 Docker 引擎源码 | 理解 Namespace/Cgroup 底层 |

---

## 二、官方文档

| 资源 | 链接 | 说明 |
|------|------|------|
| Docker 官方文档 | https://docs.docker.com | 最权威参考，覆盖所有功能 |
| Dockerfile 参考 | https://docs.docker.com/reference/dockerfile/ | 所有指令详解 |
| Docker Compose 参考 | https://docs.docker.com/compose/compose-file/ | Compose 文件规范 |
| Docker Hub | https://hub.docker.com | 镜像仓库，搜索千万级镜像 |
| Kubernetes 官方文档 | https://kubernetes.io/docs | K8s 完整文档 |
| Play with Docker | https://labs.play-with-docker.com | 在线 Docker 实验环境，无需安装 |

---

## 三、视频推荐

| 名称 | 平台 | 说明 |
|------|------|------|
| Docker 基础教程（尚硅谷） | B站 | 从零开始，适合入门 |
| Docker 容器化实战（黑马） | B站 | 项目实战，微服务容器化 |
| Kubernetes 入门到实战 | B站 | K8s 核心概念 + 集群部署 |
| 大厂 Docker 面试题解析 | B站 | 面试高频考点 + 源码分析 |
| Docker 官方入门教程 | YouTube | 官方出品，质量高 |

---

## 四、开源项目

| 项目 | 说明 | 推荐理由 |
|------|------|---------|
| awesome-docker | https://github.com/veggiemonk/awesome-docker | Docker 资源大全，覆盖工具、框架、教程 |
| docker-practice | https://github.com/yeasy/docker_practice | Docker 从入门到实践，中文教程 |
| faas | https://github.com/openfaas/faas | 容器化 Serverless 平台 |
| portainer | https://github.com/portainer/portainer | Docker 可视化 Web 管理界面 |
| lazydocker | https://github.com/jesseduffield/lazydocker | 终端 Docker 管理工具，类 GUI 体验 |
| watchtower | https://github.com/containrrr/watchtower | 自动更新运行中的容器镜像 |
| dozzle | https://github.com/amir20/dozzle | 实时 Docker 日志查看器 |
| dockge | https://github.com/louislam/dockge | 可视化的 Docker Compose 管理工具 |

---

## 五、实用工具

| 工具 | 用途 | 安装方式 |
|------|------|---------|
| **lazydocker** | 终端 UI 管理容器、镜像、网络 | `brew install lazydocker` |
| **Portainer** | 浏览器管理 Docker 集群 | `docker run -d -p 9000:9000 portainer/portainer` |
| **Dive** | 分析镜像分层大小 | `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock wagoodman/dive` |
| **Trivy** | 镜像漏洞扫描 | `docker run aquasec/trivy image nginx:alpine` |
| **Hadolint** | Dockerfile 语法检查 | `docker run --rm -v $PWD/Dockerfile:/Dockerfile hadolint/hadolint` |
| **cTop** | 容器实时指标监控 | `brew install ctop` |
| **DockerSlim** | 自动镜像瘦身 | `docker-slim build --http-probe my-app` |

### Dive 使用示例

```bash
# 分析镜像各层大小
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock wagoodman/dive mall-order:1.0
# 输出每一层新增的文件和大小，帮助定位臃肿层
```

### Hadolint 使用示例

```bash
# 检查 Dockerfile 规范
docker run --rm -v $PWD/Dockerfile:/Dockerfile hadolint/hadolint hadolint /Dockerfile
# 输出：DL3008 建议固定 apt 包版本
#      DL4006 建议使用管道错误处理
```

---

## 六、面试刷题

| 平台 | 说明 | 推荐题目 |
|------|------|---------|
| Docker 官方文档 | https://docs.docker.com | 官方教程 + 最佳实践 |
| 牛客网 | https://www.nowcoder.com | Docker 专项练习 |
| LeetCode 系统设计 | https://leetcode.com | 容器化相关系统设计题 |
| Baeldung Docker | https://www.baeldung.com/ops/docker | 英文 Docker 教程 |
| 掘金 | https://juejin.cn | 大量 Docker 实战文章 |
| 面试鸭 | https://www.mianshiya.com | Docker 面试题合集 |

---

## 七、学习路线建议

```
第一阶段（1 周）：基础入门
  ├── 安装 Docker Desktop，运行 hello-world
  ├── 掌握核心命令：run/exec/logs/ps/rm
  ├── 运行 Nginx + MySQL 容器
  └── Dockerfile 编写基础

第二阶段（1 周）：深入原理
  ├── 镜像分层原理（OverlayFS）
  ├── 数据卷管理（Volume / Bind Mount）
  ├── 网络模式（bridge / host / none）
  ├── 多阶段构建 + 镜像优化
  └── Docker Compose 编排

第三阶段（1 周）：生产实践
  ├── Docker Compose 部署微服务
  ├── CI/CD 流水线集成
  ├── 私有镜像仓库（Harbor）
  └── 容器安全 + 资源限制

第四阶段（1 周）：K8s 入门
  ├── 核心概念：Pod / Deployment / Service / Ingress
  ├── minikube 本地集群
  ├── 部署一个 3 层应用到 K8s
  └── 探针 + 滚动更新

第五阶段（考前 1 天）：面试冲刺
  ├── quick-revision 速记 25 个考点
  ├── deep-dive 容器运行时原理
  ├── scenario 场景题（启动失败、网络不通）
  └── coding 手写 Dockerfile / Compose
```

---

## 八、官方推荐工具

| 工具 | 用途 |
|------|------|
| Docker Desktop | 本地开发环境，内置 Compose 和 GUI |
| Docker Buildx | 多架构镜像构建（ARM + x86） |
| Docker Scout | 镜像安全扫描和漏洞分析 |
| Docker Init | 自动生成 Dockerfile 和 Compose 配置 |
| Compose Watch | 开发时自动热重载（类似 DevTools） |

> 祝你面试顺利，Docker 是现代软件的交付基础设施，值得深入掌握。