# DevOps 部署面试题

## 📚 知识点概览

DevOps 是现代软件开发的重要实践，包括容器化、反向代理、内网穿透、CI/CD 等。

## 🎯 面试题分类

### Level 1: 基础题

#### Docker 基础
1. **Docker 核心概念**
   - 问题：什么是 Docker？它的核心概念有哪些？
   - 答案：Docker 是一个开源的容器化平台，用于自动化应用的构建、发布和运行。核心概念包括镜像（Image，只读模板）、容器（Container，镜像的运行实例）、仓库（Repository，存储分发镜像）、Dockerfile（构建镜像的指令文件）以及 Docker Daemon（管理容器生命周期的守护进程）。
   - 解析：Docker 基于 Linux 内核的 Namespace（进程/网络/文件系统隔离）与 Cgroups（CPU/内存限制）实现轻量级虚拟化，无需完整 Guest OS。镜像采用分层 UnionFS 存储，各层只读可复用，容器启动时仅在顶层追加可写层，因此构建快、传输小。相比虚拟机，容器共享宿主机内核，启动可达毫秒级，资源开销极低，从根本上解决了开发与运维环境不一致的问题，成为微服务与 CI/CD 的基石。

2. **镜像与容器**
   - 问题：Docker 镜像和容器的区别是什么？
   - 答案：镜像是只读的静态文件，包含应用及其运行环境的全部层，是构建与分发单元；容器是镜像的运行实例，在镜像只读层上增加可写层，拥有独立的文件系统、进程与网络命名空间。同一镜像可启动多个容器，容器可被启动、暂停、停止和删除。
   - 解析：二者关系类似“类与对象”或“安装包与运行中的程序”。镜像不可变，构建后内容固定，保证交付一致性；容器是可变的运行态，其写入数据默认随容器销毁而丢失，持久化需依赖数据卷。排障中常见误区是把修改写在容器可写层而没有重建镜像，导致“改了不生效”；理解镜像层缓存机制也能解释为什么修改 Dockerfile 靠前的指令会触发后续全部重建。

3. **Dockerfile 编写**
   - 问题：如何编写 Dockerfile？有哪些最佳实践？
   - 答案：Dockerfile 通过指令描述镜像构建过程，常用指令：FROM 指定基础镜像、COPY/ADD 拷贝文件、RUN 执行构建命令、ENV 设置环境变量、EXPOSE 声明端口、CMD/ENTRYPOINT 定义启动命令、WORKDIR 设置工作目录。最佳实践包括使用官方基础镜像、合并 RUN 减少层数、利用 .dockerignore 排除无关文件、使用多阶段构建减小体积、固定依赖版本。
   - 解析：每条指令对应镜像的一个只读层，指令越多体积与构建时间越大，因此应将易变内容（源码、依赖安装）放在 Dockerfile 靠后位置以命中构建缓存。多阶段构建（先编译后仅拷贝运行产物）可将最终镜像从 GB 级降到几十 MB。安全方面应避免以 root 运行、避免把密钥写入镜像层；启动命令建议使用 exec 形式（JSON 数组），保证信号能正确传递以实现优雅停机。

#### Nginx 基础
4. **Nginx 作用**
   - 问题：Nginx 的主要作用有哪些？
   - 答案：Nginx 是高性能的 HTTP 服务器和反向代理服务器，主要作用包括静态资源服务、反向代理、负载均衡、HTTP/HTTPS 协议转发、内容缓存、限流与访问控制，并可作为邮件代理。其事件驱动异步架构使其在高并发场景下具备低内存、高吞吐的显著优势。
   - 解析：Nginx 采用 master-worker 进程模型与事件驱动（epoll）机制，单 worker 可承载数万并发连接，因此常部署为网关层统一入口。作为反向代理它能隐藏后端拓扑，实现请求分发、健康检查与故障转移；与 LVS（四层）组合可搭建多层流量入口。相比 Apache 的进程/线程模型，Nginx 的静态资源处理能力与并发支撑更好，是当前 Web 架构的事实标准，常与 Spring Boot、Node.js 等后端配合部署。

5. **反向代理**
   - 问题：什么是反向代理？Nginx 如何配置反向代理？
   - 答案：反向代理位于客户端与后端服务器之间，代理后端接收请求并将其转发给内部服务器，再把响应返回给客户端，客户端感知不到后端的存在。Nginx 配置核心：在 http 块用 upstream 定义后端服务器组，在 server 的 location 中通过 proxy_pass 转发，并用 proxy_set_header 传递客户端真实 IP、Host 等头部。
   - 解析：反向代理与正向代理方向相反：正向代理代表客户端访问外部资源，反向代理代表服务器接收外部请求，其价值包括隐藏后端架构、统一入口、负载均衡、SSL 终结与安全过滤。配置时需关注 proxy_set_header X-Real-IP/X-Forwarded-For 保留客户端 IP（否则后端日志与限流拿到的是代理地址）、设置合理超时与重试、处理 WebSocket 升级头；同时注意代理层自身不要成为单点，需配合 keepalive 复用与健康检查。

#### 内网穿透基础
6. **内网穿透概念**
   - 问题：什么是内网穿透？有哪些常见的内网穿透工具？
   - 答案：内网穿透指通过公网服务器中转，使外部用户能够访问位于 NAT 或防火墙后的内网服务的方案，常用于无公网 IP 的本地开发调试、演示或临时暴露服务。常见工具包括 frp、ngrok、natapp、cpolar，以及 zerotier、tailscale 这类虚拟组网工具。
   - 解析：核心原理是内网客户端主动向外网中转服务器建立长连接（出站连接通常不受 NAT 限制），中转服务器把公网端口与该隧道绑定，实现双向数据转发。相比申请公网 IP 或搭建 VPN，内网穿透零成本、即开即用，适合开发期验证；但其安全性依赖工具实现，token、密钥必须妥善管理并限制暴露端口。生产环境通常改用云上部署或专线，避免依赖第三方中转带来的延迟与安全风险。

### Level 2: 进阶题

#### Docker 进阶
7. **Docker Compose**
   - 问题：Docker Compose 的作用是什么？如何编写 docker-compose.yml？
   - 答案：Docker Compose 用于定义和编排同一主机的多个容器，通过 docker-compose.yml 以声明式配置描述服务（services）、网络（networks）和数据卷（volumes），执行 docker compose up 即可一键创建整套环境。常用于本地开发、测试环境搭建与 CI 集成测试。
   - 解析：Compose 文件核心是 services 段，每个服务可配置 image/build、ports 端口映射、environment 环境变量、depends_on 依赖顺序、healthcheck 健康检查、volumes 挂载等；容器之间默认在同一自定义网络中，可用服务名互访。它解决了多容器手动 docker run 难以维护的问题，通过项目名（-p）隔离多套环境，可复现整套依赖。但 Compose 仅限单机编排，跨主机调度需 Swarm 或 Kubernetes，生产环境通常由 K8s 承接。

8. **Docker 网络**
   - 问题：Docker 的网络模式有哪些？各自的使用场景是什么？
   - 答案：Docker 默认提供多种网络驱动：bridge（默认，容器间隔离并通过 NAT 与外网通信）、host（容器共享宿主机网络栈，性能最好但无隔离）、none（禁用网络）、overlay（跨主机容器通信，供 Swarm 使用）、macvlan（为容器分配物理网络地址）。自定义 bridge 网络内置 DNS，容器可用服务名互相解析。
   - 解析：选型依据是隔离性与通信需求：单机多容器推荐自定义 bridge 网络，利用内置 DNS 按服务名访问，避免依赖易变的容器 IP；追求极致性能或大量端口暴露用 host 模式，但会牺牲端口隔离；跨主机场景用 overlay 联合 Swarm/Kubernetes。排障时用 docker network inspect 查看网络信息、docker network connect 动态接入容器；注意 -p 端口映射本质是 DNAT 规则，容器内端口并不直接暴露在宿主机上。

9. **Docker 数据卷**
   - 问题：Docker 数据卷的作用是什么？如何持久化数据？
   - 答案：Docker 数据持久化主要有三种方式：volume（卷，由 Docker 管理，存储在数据卷目录，推荐用于数据库等关键数据）、bind mount（绑定挂载宿主机目录，便于开发调试与配置注入）、tmpfs（仅存内存，容器停止即消失）。数据卷支持跨容器共享，且容器删除后数据保留。
   - 解析：容器可写层与容器生命周期绑定，删除即数据丢失，且写入存在 I/O 性能损耗，因此持久化数据必须用卷或挂载。volume 由 Docker 统一管理，备份迁移简单，生产环境首选；bind mount 依赖宿主机目录结构，适合日志收集与热更新配置；tmpfs 适合临时敏感数据且不落盘。Kubernetes 中对应 PersistentVolume/PVC 概念。数据库容器务必单独挂卷并制定备份策略。

#### Nginx 进阶
10. **负载均衡**
    - 问题：Nginx 如何实现负载均衡？有哪些负载均衡算法？
    - 答案：Nginx 通过 upstream 定义服务器组，在 location 中用 proxy_pass 指向该组即实现反向代理负载均衡。支持的算法：round-robin 轮询（默认）、weight 权重、ip_hash（按客户端 IP 哈希保持会话粘滞）、least_conn（最少连接），以及第三方模块的 url_hash、fair 等。
    - 解析：负载均衡的价值是横向扩容与高可用：配合 fail_timeout 与 max_fails 可在节点故障时自动摘除；ip_hash 解决需要会话保持的业务，但可能引入热点，可用与 weight 组合缓解；一致性哈希（url_hash）在缓存类场景命中率更高。生产上建议开启 upstream keepalive 连接池，减少与后端重复建连的开销，并配合健康检查（主动/被动）。需区分四层（stream 模块）与七层（http 模块）负载均衡的适用场景。

11. **SSL/TLS 配置**
    - 问题：如何为 Nginx 配置 SSL/TLS？
    - 答案：获取证书后（自签或 Let's Encrypt），在 server 块配置 listen 443 ssl、ssl_certificate 指向证书链、ssl_certificate_key 指向私钥，并设置 ssl_protocols 仅启用 TLSv1.2/1.3、ssl_ciphers 指定安全套件；再在 80 端口 server 中 return 301 将 HTTP 跳转 HTTPS。
    - 解析：配置要点：证书链应包含中间证书，否则部分客户端校验失败；私钥须收紧权限（如 chmod 600）且禁止入库；务必禁用 TLS 1.0/1.1 与 RC4 等弱套件；开启 ssl_session_cache 复用会话可显著降低握手开销，OCSP stapling 提升证书校验速度。实践上常配合 HTTP/2（listen 443 ssl http2）与 HSTS 头（Strict-Transport-Security）。证书到期未续期是线上高频故障，应配置自动续期（certbot renew）与到期监控告警。

12. **性能优化**
    - 问题：如何优化 Nginx 的性能？
    - 答案：性能优化涵盖系统层与应用层：worker_processes 与 CPU 核数对齐、worker_connections 调大、事件模型 use epoll、开启 keepalive 与 gzip、静态资源配置 expires/Cache-Control、启用 sendfile 与 tcp_nopush 提升文件发送效率、open_file_cache 缓存文件句柄，并同步放宽系统层 ulimit 与 sysctl 限制。
    - 解析：调优本质是消除瓶颈而非堆参数。先用压测（wrk/ab/locust）定位瓶颈在 CPU、内存、磁盘还是网络：文件描述符不足报 too many open files，需同时调 worker_rlimit_nofile 与系统 ulimit；连接数不足调 worker_connections；延迟敏感关注 keepalive_timeout 与请求排队。合理顺序是先系统层、再事件层、后业务层（压缩、缓存、限流），每步用压测对比验证，避免盲目调参造成内存膨胀或参数冲突。

#### 内网穿透进阶
13. **FRP 原理**
    - 问题：FRP 的工作原理是什么？
    - 答案：FRP（Fast Reverse Proxy）是 Go 语言实现的开源内网穿透工具，采用“内网客户端主动连接公网服务器”的模型：frps 运行在有公网 IP 的服务器上，frpc 运行在内网，frpc 启动后主动向 frps 建立长连接并注册端口映射，公网请求到达 frps 指定端口后，经该隧道转发至内网对应服务。
    - 解析：关键在于“出站连接不受 NAT 限制”，内网客户端可主动建连，穿透成功后双向数据复用同一隧道，因此 frp 也被称为反向代理型工具。FRP 支持 TCP、UDP、HTTP/HTTPS、STCP 等代理类型，具备 token 认证、TLS 加密、限速、端口复用与 dashboard 监控。典型场景是远程调试内网 Web/SSH 服务。相比 ngrok 依赖第三方中转，FRP 完全自建可控。部署上 frpc/frps 常以 systemd 托管，修改配置后需重启或 reload 生效。

14. **安全配置**
    - 问题：如何配置内网穿透的安全性？
    - 答案：内网穿透安全配置包括：使用强 token 认证（FRP auth token）、开启传输加密（TLS）、遵循最小暴露原则只穿透必要端口、配置访问来源 IP 白名单、服务侧加强用户认证（如 SSH 密钥登录）、启用访问限流，以及及时升级工具版本修复已知漏洞。
    - 解析：内网穿透把原本受 NAT 保护的资源暴露到公网，实质是扩大攻击面，因此最小暴露是核心：只暴露需要的服务与端口，调试完成立即关闭隧道。FRP 的 dashboard 应设置强密码且仅绑定内网；服务侧建议叠一层反向代理做认证与限流，云安全组只放行代理服务器 IP。日志审计与异常流量告警（如暴力破解）不可少，发现异常立即处置。生产环境应评估是否有必要使用，优先考虑 VPN 或云上直连等更可控的方案。

### Level 3: 高级题

#### Docker 高级
15. **Docker Swarm**
    - 问题：Docker Swarm 是什么？如何实现容器编排？
    - 答案：Docker Swarm 是 Docker 原生的容器编排工具，将多台 Docker 主机组成集群（manager 节点基于 Raft 共识负责调度，worker 节点运行任务）。通过 docker service create 创建服务，支持副本伸缩、滚动更新、服务发现、内置负载均衡、健康检查与故障自愈，配置可声明在 stack 文件中。
    - 解析：Swarm 的核心是 Manager 节点通过 Raft 算法保持集群状态一致，节点间以 overlay 网络（ingress）实现跨主机服务访问与 VIP 负载均衡。相比 Kubernetes，Swarm 简单易上手、与 Docker API 原生集成，适合中小规模场景；但扩展性、生态与可移植性不如 K8s，生产主流已转向 Kubernetes。选型对比：简单单机用 Compose，中小集群用 Swarm，大规模云原生用 K8s，回答时讲清适用边界即可。

16. **Kubernetes 集成**
    - 问题：Docker 与 Kubernetes 的关系是什么？
    - 答案：Docker 提供容器运行时与镜像打包能力，Kubernetes 是容器编排平台，负责容器的调度、伸缩、自愈、服务发现与配置管理。两者是“运行时与编排”的分工关系：Kubernetes 通过 CRI 接口调用符合 OCI 标准的容器运行时（containerd、CRI-O）来创建和管理 Pod 中的容器。
    - 解析：Kubernetes 1.24 移除了 Dockershim，K8s 不再直接依赖 Docker 守护进程，但仍兼容 Docker 构建的 OCI 镜像——即“开发用 Docker 构建镜像、生产由 K8s 调度运行”的协作依然成立，K8s 节点无需安装 docker daemon。理解这一层可解释“项目已有 Kubernetes 为何还用 Docker”这类问题：Docker 负责镜像化封装，K8s 负责大规模调度与治理，二者是生态上的互补而非替代关系。

#### Nginx 高级
17. **性能调优**
    - 问题：如何进行 Nginx 的性能调优？
    - 答案：性能调优涵盖系统层与应用层：worker_processes 与 CPU 核数对齐、worker_rlimit_nofile 提高文件描述符上限、use epoll 事件模型、开启 keepalive 与 gzip、静态资源配置 expires 缓存、proxy 场景调整 buffer 与超时参数、启用 HTTP/2 与 SSL 会话复用，同时用 ulimit 与 sysctl 放宽系统限制。
    - 解析：调优的前提是先压测定位瓶颈再动手：文件描述符不足表现为 too many open files，需同步调整 worker_rlimit_nofile 与系统 ulimit；连接数不足调大 worker_connections；延迟敏感场景关注 keepalive_timeout、请求排队与后端连接复用；磁盘 I/O 密集场景用 sendfile、open_file_cache 减少用户态拷贝。推荐顺序：系统层 → 事件层 → 业务层（缓存、压缩、限流），每步用 wrk/ab 对比验证，避免未经测量盲目调参导致资源浪费。

18. **安全加固**
    - 问题：如何进行 Nginx 的安全加固？
    - 答案：安全加固要点：及时升级版本修补漏洞、以非 root 用户运行 worker、关闭 server_tokens 隐藏版本信息、清理默认页与无用 location、限制请求体大小（client_max_body_size）、配置访问控制与 IP 白名单、启用限流（limit_req/limit_conn）、合理设置超时防慢速攻击，并为管理接口加认证、开启访问日志审计。
    - 解析：安全是纵深防御而非单点措施：请求层面过滤异常方法、UA、超长 URL 与注入特征，避免 alias 拼接导致的目录穿越；协议层面强制 TLS、禁用不安全协议与版本；资源层面限制并发与速率防止 CC 攻击；同时关注第三方模块的供应链风险，定期扫描镜像依赖。配套日志分析、WAF（如 OpenResty/ModSecurity）与异常告警，形成“发现—阻断—溯源”闭环，攻击面收敛比堆砌规则更重要。

#### CI/CD
19. **CI/CD 流水线**
    - 问题：什么是 CI/CD？如何设计 CI/CD 流水线？
    - 答案：CI（持续集成）把代码频繁合并到主干并自动构建测试，尽早发现集成问题；CD（持续交付/部署）把通过验证的产物自动部署到测试、预发乃至生产环境。典型流水线：代码提交触发 → 静态检查 → 单元测试 → 构建镜像 → 集成测试 → 部署预发 → 冒烟测试 → 灰度发布至生产。
    - 解析：流水线设计要点：一是快速反馈，控制在分钟级并区分快慢阶段，慢测试并行或后置；二是可重复可回滚，构建产物唯一（镜像 tag 映射 commit），部署脚本幂等，失败一键回滚；三是质量门禁，把测试覆盖率、漏洞扫描、人工审批作为关卡；四是环境一致，同一镜像贯穿所有环境避免“测试通过生产挂”。工具选型上 Jenkins 灵活但需自维护，GitLab CI 与仓库集成好，云原生多选 GitHub Actions、Tekton。

20. **Jenkins/GitLab CI**
    - 问题：Jenkins 和 GitLab CI 的区别是什么？如何选择？
    - 答案：Jenkins 是独立的 CI/CD 服务器，插件生态极其丰富，可编排任意复杂流水线，但需自行维护主机、插件版本与安全；GitLab CI 与 GitLab 代码托管深度集成，通过 .gitlab-ci.yml 声明式定义流水线，天然支持 Merge Request 触发与 Runner 弹性伸缩，运维成本低。选择依据是代码仓库所在、团队规模、定制复杂度与运维能力。
    - 解析：GitLab CI 的 Runner 架构（共享/专用 Runner，Docker/Kubernetes executor）开箱即用，适合中小团队与“配置即仓库”的实践；Jenkins 的多分支流水线（Jenkinsfile）与海量插件适合复杂集成和已有基础设施，但 master 节点是单点脆弱点，需高可用改造。行业趋势是声明式、容器化、云上托管，许多团队直接选用 GitHub Actions。回答时不必罗列功能，重点讲清两套方案的适用边界与取舍。

### Level 4: 专家题

#### 架构设计
21. **微服务部署架构**
    - 问题：如何设计微服务的部署架构？
    - 答案：微服务部署通常采用“容器 + 编排平台”模式：每个服务独立构建为镜像（多阶段构建控制体积），由 Kubernetes 统一编排，配套服务发现与配置中心（Nacos/Consul）、API 网关（统一入口、鉴权、限流）、可观测性三件套（日志、指标、链路追踪）以及 CI/CD 流水线，实现按服务独立发布、独立伸缩与故障隔离。
    - 解析：核心目标是独立部署、独立伸缩、隔离故障。规模增长后按团队划分命名空间与集群并做资源配额、限流；流量治理可下沉到 Service Mesh（Istio），把重试、熔断逻辑从业务代码剥离；数据层面需规划分库分表与分布式事务（Seata）。发布策略常用滚动更新、蓝绿发布、金丝雀发布。架构评审关注：服务拆分粒度是否合理（避免过度拆分导致分布式复杂度爆表）、依赖是否成环、配置与密钥管理（K8s ConfigMap/Secret）是否安全。

22. **高可用设计**
    - 问题：如何实现部署架构的高可用？
    - 答案：高可用部署的核心是多副本与冗余消除单点：应用容器多副本并配置反亲和性分布到多节点、多可用区；负载均衡器、注册中心（Nacos/Eureka 集群）、数据库（主从/集群）、缓存（Redis 哨兵/集群）均做高可用；配合健康检查、熔断限流与故障自动转移，目标可用性通常为 99.9% 以上。
    - 解析：高可用公式为 可用性 = MTBF/(MTBF+MTTR)，既要降低故障发生（冗余、优雅降级、容量规划），也要缩短恢复时间（自动发现、自动恢复、快速回滚）。无状态应用加副本最简单；有状态组件（DB、MQ、Redis）依赖集群方案并做好备份与恢复演练；网关与 DNS 层做多活容灾。还要防范“磁盘满、证书过期、OOM”等慢性故障，通过监控告警与故障演练（混沌工程）提前暴露单点，最终演进到同城双活或异地多活。

#### 性能优化
23. **性能监控**
    - 问题：如何监控部署架构的性能？
    - 答案：监控体系分层建设：基础设施层（CPU、内存、磁盘、网络，Prometheus + node-exporter + Grafana）、应用层（QPS、延迟、错误率，Micrometer/SkyWalking/Spring Boot Actuator）、业务层（订单量、转化率等业务指标）；配合日志采集（ELK/Loki）与链路追踪（Jaeger/Zipkin）形成完整可观测性，设置分级阈值告警与 SLO。
    - 解析：“可观测性”三支柱：Metrics 回答“是否异常”，Logs 回答“发生了什么”，Traces 回答“哪一步变慢”，三者缺一不可、互为印证。设计要点：控制指标基数避免高基数 label 拖垮存储、告警分级与抑制规则防止告警风暴、遵循黄金指标（RED：Rate/Errors/Duration；USE：Utilization/Saturation/Errors）、为关键链路设置 SLO 并据此告警。除实时监控外还应做容量预测与压测基线，把性能退化消灭在发版之前，而非事后救火。

24. **故障排查**
    - 问题：如何进行部署架构的故障排查？
    - 答案：故障排查遵循“由外到内、层层定位”的思路：先确认影响范围与用户面，从入口（DNS、网关、负载均衡）逐层检查连接与转发，再到应用日志与指标定位异常（错误率、延迟、OOM、连接池耗尽），最后落到基础设施与依赖（数据库、缓存、MQ）。全程以日志、指标、链路追踪三数据源交叉佐证。
    - 解析：典型排障路径：第一步快速止血（切流、回滚、扩容恢复服务），第二步根因分析（把故障时间线与变更对齐——发版、配置、流量突增，对比监控曲线定位），第三步沉淀复盘（补告警、限流、灰度与演练）。常见误区：不先看日志凭经验猜、数据不完整全靠推测、无回滚方案导致故障扩大。成熟团队应维护“变更日历 + 基线指标 + 应急演练脚本”，使排障从随机救火变成按流程执行。

## 📖 学习资源

### 书籍推荐
- 《Docker 技术入门与实战》 - 杨保华
- 《Nginx 高性能 Web 服务器详解》 - 苗泽
- 《Kubernetes in Action》 - Marko Lukša

### 在线资源
- [Docker 官方文档](https://docs.docker.com/)
- [Nginx 官方文档](https://nginx.org/en/docs/)
- [FRP 官方文档](https://github.com/fatedier/frp)

## 🔗 相关链接

- [Docker 专题](docker/)
- [Nginx 专题](nginx/)
- [内网穿透专题](nat-traversal/)
- [CI/CD 专题](ci-cd/)
