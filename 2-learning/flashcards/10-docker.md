# Docker — 面试抽认卡

> 来源：`learn/10-docker/05-interview/`

---

### Card 1: 镜像分层原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Docker 镜像的分层结构是如何工作的？写时复制（Copy-on-Write）如何实现？**

**A:** Docker 镜像由多层只读层叠加组成，每一层对应 Dockerfile 的一条指令，多个镜像共享相同的基础层。容器启动时在镜像层上加一个可写层（容器层）。写时复制：修改只读层文件时，先将文件复制到可写层再修改，原只读层文件不变，减少空间占用。`docker commit` 将容器层保存为新镜像层。`docker history` 查看镜像分层历史。

---

### Card 2: 多阶段构建
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: 多阶段构建解决了什么问题？如何实现？**

**A:** 多阶段构建允许在一个 Dockerfile 中使用多个 `FROM` 指令，每个 FROM 是新阶段，后阶段可以从前阶段复制产物。解决：第一阶段用完整构建环境（如 JDK 编译 JAR，`maven:3.8-eclipse-temurin-17`），第二阶段用最小运行时（如 `eclipse-temurin:17-jre-alpine`），最终镜像只包含运行时和产物，不包含构建工具。示例：`FROM maven:3.8 AS build; COPY . .; RUN mvn package; FROM eclipse-temurin:17-jre-alpine; COPY --from=build target/app.jar .`，镜像体积从 300MB 降到 80MB。

---

### Card 3: Namespace 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Linux Namespace 如何实现容器隔离？有哪些类型的 Namespace？**

**A:** Namespace 是 Linux 内核提供的资源隔离机制，将全局资源封装到不同 Namespace 中，进程只能看到自己 Namespace 内的资源。主要类型：PID（进程隔离，容器内看不到宿主机进程）、Network（网络栈隔离，独立 IP/端口）、Mount（文件系统隔离，独立挂载点）、UTS（主机名隔离）、IPC（进程间通信隔离，如信号量）、User（用户 ID 隔离，容器内 root ≠ 宿主机 root）。Cgroup 负责资源限制（CPU/内存），Namespace 负责隔离。

---

### Card 4: Cgroup 资源限制
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Cgroup 如何限制容器的 CPU 和内存使用？**

**A:** Cgroup（Control Groups）控制系统资源分配。CPU 限制：`--cpus=1.5` 限制使用 1.5 个核心，底层写 `cpu.cfs_quota_us` 和 `cpu.cfs_period_us`。内存限制：`--memory=512m` 限制最大 512MB，超限触发 OOM Kill。`--memory-reservation=256m` 设置软限制（内存足够时可超过，不足时压缩到该值）。内存是不可压缩资源（超限即 OOM），CPU 是可压缩资源（超限则节流，不影响进程运行）。Cgroup v2（Linux 4.5+）统一管理所有资源。

---

### Card 5: OverlayFS 存储驱动
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Docker 默认的存储驱动 OverlayFS 是如何工作的？**

**A:** OverlayFS 将多个目录叠加挂载为一个目录。Docker 使用 Overlay2（推荐），由 lowerdir（只读镜像层）+ upperdir（可写容器层）+ merged（统一视图）组成。读取文件：先看 upperdir，有则返回，无则查 lowerdir。修改文件：将文件从 lowerdir 复制到 upperdir（Copy-on-Write），然后修改。删除文件：在 upperdir 创建 whiteout 文件（标记删除）。Overlay2 比 AUFS 性能更好（更少的文件描述符，更快的 page cache 共享）。

---

### Card 6: Compose 编排
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Docker Compose 的核心三要素是什么？如何保证服务启动顺序？**

**A:** services（服务定义）、networks（网络定义）、volumes（数据卷定义）。`depends_on` 只保证启动顺序，不等同于服务就绪（如 MySQL 端口已开但初始化未完成）。配合 `healthcheck`：`healthcheck: test: ["CMD", "mysqladmin", "ping"]; interval: 10s; retries: 5`，使用 `condition: service_healthy` 确保依赖服务就绪后再启动。`restart: unless-stopped` 保证崩溃后自动重启。生产环境建议用 K8s 替代 Compose。

---

### Card 7: K8s 核心概念
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Kubernetes 的核心组件有哪些？它们之间的关系是什么？**

**A:** Master 节点：API Server（所有操作的入口）、Scheduler（调度 Pod 到 Node）、Controller Manager（控制器，如 DeploymentController、ReplicaSetController）、etcd（分布式存储，集群状态）。Worker 节点：Kubelet（Pod 生命周期管理，和 API Server 通信）、Kube-proxy（网络代理，Service 规则）、Container Runtime（Docker/containerd）。Pod 是调度最小单元，Deployment 管理 Pod 副本，Service 暴露服务，ConfigMap 管理配置，PV/PVC 管理存储。

---

### Card 8: Pod 生命周期
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Pod 的生命周期有哪些阶段？Liveness 和 Readiness 探针的区别？**

**A:** 阶段：Pending（调度中）、Running（容器运行）、Succeeded（任务完成）、Failed（任务失败）、Unknown（状态未知）。Pod 内容器状态：Waiting（等待）、Running（运行）、Terminated（终止）。Liveness 探针：检查容器是否存活，失败则重启容器（`kubectl get pods` 的 RESTARTS 列增加）。Readiness 探针：检查容器是否就绪，失败则从 Service 的 Endpoints 中移除，不接收流量。Startup 探针（K8s 1.18+）：慢启动容器保护，成功前 Liveness/Readiness 不生效。

---

### Card 9: Service 类型
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: K8s Service 的四种类型分别适用于什么场景？**

**A:** ClusterIP（默认）：集群内虚拟 IP，仅集群内部可访问，适合内部服务通信。NodePort：在每个 Node 上开放固定端口（30000-32767），外部可通过 `NodeIP:NodePort` 访问，适合开发测试。LoadBalancer：云提供商（AWS/Azure/GCP）自动创建负载均衡器，分配公网 IP，适合生产环境外部访问。ExternalName：将 Service 映射到外部 DNS（如 `CNAME` 到 `db.example.com`），适合调用外部服务。Ingress：七层路由，基于域名/路径转发到不同 Service，比 LoadBalancer 更灵活。

---

### Card 10: Ingress 配置
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Ingress 和 Service 的关系是什么？如何配置基于域名的路由？**

**A:** Ingress 是七层（HTTP/HTTPS）负载均衡器，将外部请求路由到集群内 Service。配置：`spec.rules[].host` 匹配域名，`http.paths[].path` 匹配路径，`backend.service.name` 转发到目标 Service。示例：`nginx.ingress.kubernetes.io/canary: "true"` 实现灰度发布。Ingress Controller（如 Nginx Ingress、Traefik）是实际的实现组件，Ingress 只是配置规则。TLS 配置：`spec.tls[].hosts` 和 `secretName` 管理 HTTPS 证书。

---

### Card 11: 资源限制与 QoS
**维度**: 📝速记 | **难度**: ⭐⭐⭐

**Q: K8s 中 Pod 的 QoS 等级有哪些？如何根据资源限制确定 QoS？**

**A:** 三个等级：Guaranteed（最高优先级，requests == limits）、Burstable（中等优先级，requests < limits）、BestEffort（最低优先级，不设置 requests/limits）。OOM 时优先 Kill BestEffort → Burstable → Guaranteed。示例：`resources: { requests: { memory: "256Mi", cpu: "500m" }, limits: { memory: "512Mi", cpu: "1" } }` 为 Burstable。`limits == requests` 为 Guaranteed。建议生产环境为所有 Pod 设置资源限制，避免资源争抢。

---

### Card 12: 镜像安全
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: Docker 镜像安全有哪些最佳实践？**

**A:** ① 使用最小基础镜像（`alpine`、`distroless`，减少攻击面）；② 非 root 运行（`USER 1000`，避免容器内 root 提权）；③ 镜像签名验证（Docker Content Trust / Notary）；④ 定期镜像扫描（Trivy / Clair 扫描 CVE 漏洞）；⑤ `.dockerignore` 排除敏感文件（如 `.env`、`credentials.json`）；⑥ 多阶段构建（构建工具和运行环境分离，构建工具可能包含漏洞）；⑦ 限制容器能力（`--cap-drop=ALL --cap-add=NET_BIND_SERVICE`）。

---

### Card 13: 日志收集
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: Docker 容器的日志如何收集和管理？**

**A:** Docker 默认日志驱动是 `json-file`（文件存储，`docker logs` 查看）。生产环境推荐：`journald`（systemd 日志，集中管理）或 `fluentd`（转发到 ELK）、`gelf`（Graylog 扩展日志格式）、`awslogs`（CloudWatch）。K8s 日志：`kubectl logs` 查看 Pod 日志，DaemonSet 部署 Fluentd/Filebeat 采集，输出到 ES + Kibana 或 Loki + Grafana。容器日志最佳实践：日志输出到 stdout/stderr（不要写文件），由日志收集器统一采集。

---

### Card 14: 网络模式
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Docker 的四种网络模式分别是什么？**

**A:** bridge（默认，NAT 模式，容器通过虚拟网桥连接，通过 `-p` 端口映射访问外部）；host（共享宿主机网络栈，容器直接使用宿主机 IP/端口，性能好但无隔离）；none（无网络，适合不需要网络的容器，如离线计算）；container（共享其他容器的网络栈，适合 sidecar 模式，如日志收集器共享主应用的网络）。自定义网络的优势：容器名 DNS 解析（`bridge` 模式需要 `--link`，已废弃）、网络隔离（不同自定义网络不能通信）。

---

### Card 15: 数据卷管理
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Volume 和 Bind Mount 的区别是什么？如何选择？**

**A:** Volume：由 Docker 管理，存储在 `/var/lib/docker/volumes/`，可通过 `docker volume ls` 查看，支持 Volume Driver（如 NFS、云存储），适合生产环境持久化数据。Bind Mount：挂载宿主机任意目录，依赖宿主机目录结构，不可移植，适合开发环境（热更新代码）。tmpfs：存储在内存中，不持久化，适合临时数据（如缓存、Session）。数据库容器必须挂载 Volume，否则删除容器数据全丢。`docker run -v myvolume:/data` 创建 Volume，`--mount type=bind,source=/host,target=/container` 使用 Bind Mount。