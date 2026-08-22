# 场景题

## 场景 1：网关超时

**问题：** 网关转发请求到下游服务超时，如何处理？

**排查步骤：**

1. **确认超时现象**：客户端请求返回 504 Gateway Timeout，查看 Gateway 日志确认超时异常
2. **检查超时配置**：确认 Gateway 的 `connect-timeout`、`response-timeout` 配置是否合理
   ```yaml
   spring:
     cloud:
       gateway:
         httpclient:
           connect-timeout: 5000    # 连接超时 5s
           response-timeout: 10s    # 响应超时 10s
   ```
3. **排查下游服务**：直接调用下游服务接口，确认是否真的慢或不可用
4. **确认慢调用原因**：
   - 数据库慢查询：检查 SQL 执行计划，优化索引
   - 外部 API 调用慢：检查第三方接口响应时间
   - 业务逻辑复杂：考虑异步处理或缓存
5. **检查服务负载**：查看下游服务的 CPU、内存、连接池使用率，是否资源不足

**解决方案：**

- **调整超时时间**：根据业务场景合理设置超时阈值，避免一刀切
- **熔断降级**：配置 Sentinel 或 Hystrix，超时后快速失败，避免线程堆积
  ```yaml
  filters:
    - name: CircuitBreaker
      args:
        name: orderServiceBreaker
        fallbackUri: forward:/fallback/order
  ```
- **异步处理**：非核心操作改为异步消息队列（如发送短信、生成报表）
- **缓存优化**：对高频读取的数据做本地缓存或 Redis 缓存，减少下游调用
- **连接池调优**：增大 Gateway 和下游服务的连接池大小，避免连接耗尽
- **超时分类**：区分读超时和写超时，读超时可重试，写超时需幂等处理

---

## 场景 2：限流失效

**问题：** Sentinel 规则配置了但没生效，如何排查？

**排查步骤：**

1. **确认规则是否下发到客户端**
   - 访问 Sentinel 控制台，查看实时监控是否有规则推送记录
   - 查看客户端日志，确认 `SentinelRuleManager` 是否接收到规则推送
   - 通过 Sentinel 客户端 API 查看当前生效规则：
     ```bash
     curl http://localhost:8081/actuator/sentinel
     # 查看 flowRules 中是否有配置的规则
     ```

2. **检查资源名是否匹配**
   - Sentinel 控制台配置的 `resource` 名称必须与代码中 `@SentinelResource` 的 `value` 一致
   - Gateway 限流时，资源名默认格式为 `GET:/api/order/list`，注意 HTTP 方法和路径拼写
   - 检查是否有多余空格或大小写差异

3. **确认 Sentinel 依赖和配置完整**
   - 检查 `pom.xml` 是否包含 `spring-cloud-starter-alibaba-sentinel` 依赖
   - 检查 `application.yml` 中 Sentinel 配置是否正确：
     ```yaml
     spring:
       cloud:
         sentinel:
           enabled: true
           transport:
             dashboard: localhost:8858
             heartbeat-interval-ms: 5000
           datasource:
             ds1:
               nacos:
                 server-addr: localhost:8848
                 dataId: sentinel-rules
                 rule-type: flow
     ```

4. **检查规则持久化方式**
   - 控制台配置的规则默认存储在内存中，服务重启后丢失
   - 确认是否配置了 Nacos 持久化，规则是否已写入 Nacos 配置
   - 检查 Nacos 中配置的 DataID 格式是否与客户端一致

5. **确认 Sentinel 上下文正确初始化**
   - Sentinel 需要第一次请求触发后才能初始化链路
   - 发送几次测试请求后再检查规则是否生效
   - 检查 `@SentinelResource` 注解是否被 AOP 正确拦截（类是否在 Spring 容器中）

**常见原因及修复：**
- **规则未持久化**：配置 Nacos 数据源，确保规则持久化到 Nacos
- **资源名不匹配**：统一资源命名规范，Gateway 资源名会自动生成，可通过 `SphU.entry()` 自定义资源名
- **版本兼容问题**：确保 Sentinel 版本与 Spring Cloud Alibaba 版本兼容
- **控制台和客户端网络不通**：检查 `transport.dashboard` 配置是否正确，防火墙是否放行

---

## 场景 3：配置中心故障

**问题：** Nacos 挂了，服务还能正常调用吗？

**分析：**

Nacos 作为注册中心和配置中心，故障后对服务的影响视情况而定：

**注册中心故障：**
- **服务间调用不受影响**：服务启动时已将服务实例信息缓存到本地内存，已有连接继续正常通信
- **新服务无法注册**：新启动的服务无法注册到 Nacos，其他服务无法发现它
- **服务下线不感知**：已注册的服务宕机后，其他服务无法及时感知，会继续调用已宕机的实例
- **本地缓存有效期**：Spring Cloud 的 `NacosDiscoveryClient` 有本地缓存，默认 30 秒内可正常发现

**配置中心故障：**
- **已加载的配置不受影响**：服务启动时已拉取的配置在内存中正常使用
- **配置变更不生效**：Nacos 故障期间无法推送配置变更，配置停留在故障前的状态
- **新服务无法启动**：新服务启动时需要从 Nacos 拉取配置，如果配置中心不可用，启动失败
- **动态刷新失效**：`@RefreshScope` 标注的 Bean 无法刷新

**最佳实践：**

1. **本地缓存兜底**：配置 `spring.cloud.nacos.config.file-extension` 和本地 `bootstrap.yaml` 作为兜底配置
2. **多集群部署**：Nacos 至少部署 3 节点集群，避免单点故障
3. **服务优雅降级**：Nacos 不可用时，服务使用本地缓存的注册表继续提供服务
   ```yaml
   spring:
     cloud:
       nacos:
         discovery:
           enabled: true
           # 开启本地缓存，Nacos 不可用时使用缓存
           cache:
             enabled: true
   ```
4. **健康检查**：定期检查 Nacos 集群健康状态，配置告警
5. **配置中心降级**：配置 `spring.cloud.nacos.config.retry.enabled=true` 和重试策略，自动重试连接

---

## 场景 4：CI/CD 失败回滚

**问题：** 部署后发现 Bug，如何快速回滚？

**回滚策略：**

**方案一：Kubernetes 镜像回滚（最快，30 秒内）**
```bash
# 回滚到上一个版本
kubectl rollout undo deployment/order-service -n production

# 回滚到指定版本
kubectl rollout undo deployment/order-service --to-revision=3 -n production

# 查看回滚状态
kubectl rollout status deployment/order-service -n production
```

**方案二：Docker Compose 回滚**
```bash
# 使用上一个版本的镜像重新启动
docker-compose -f docker-compose.prod.yml down
# 修改 .env 中 IMAGE_TAG 为上一个版本号
docker-compose -f docker-compose.prod.yml up -d
```

**方案三：GitOps 回滚**
```bash
# 1. 回滚 Git 仓库配置
git revert HEAD --no-edit
git push origin main

# 2. ArgoCD 自动同步到集群（或手动同步）
argocd app sync order-service
```

**方案四：灰度发布中的快速回滚**
```bash
# 如果使用金丝雀发布，将流量比例切回 0
kubectl set service traffic order-service-canary --to-weight=0
```

**回滚前的关键检查：**
1. **数据库兼容性**：确认回滚后的代码与新版本的数据库 Schema 兼容（表结构、索引变更应向前兼容）
2. **数据一致性**：回滚期间写入的数据不能丢失，如 Schema 变更不可逆，需额外处理
3. **缓存清空**：回滚后需清空 Redis 缓存，避免旧代码读取新格式的数据
4. **版本标签**：确保镜像 Tag 有明确版本号，避免使用 `latest` 标签

**回滚后处理：**
1. 确认回滚成功后，标记相关 Issue 为"已回滚"，记录原因
2. 修复 Bug 后重新走 CI/CD 流水线部署
3. 分析根因，防止同类问题再次发生
4. 如果回滚后错误依然存在，说明问题可能由数据或配置变更引起，需进一步排查

---

## 场景 5：链路追踪查不到

**问题：** SkyWalking 没有数据，排查步骤

**排查步骤：**

1. **检查 Agent 是否正确挂载**
   ```bash
   # 确认 JVM 参数中是否包含 -javaagent 配置
   ps aux | grep java
   # 查看启动日志中是否有 SkyWalking Agent 的初始化日志
   tail -f /var/log/skywalking-agent.log
   ```
   常见问题：Agent 路径错误、版本不兼容（Agent 版本与后端版本不匹配）

2. **检查 Agent 配置**
   ```bash
   # 查看 agent.config 中的配置
   cat /path/to/agent/config/agent.config
   ```
   关键配置项：
   - `agent.service_name`：服务名是否正确
   - `collector.backend_service`：OAP Server 地址是否正确
   - `agent.ignore_suffix`：是否误过滤了某些请求

3. **检查网络连通性**
   ```bash
   # 从服务所在机器 telnet OAP Server 端口
   telnet oap-server 11800  # gRPC 端口
   telnet oap-server 12800  # HTTP 端口
   ```
   常见问题：防火墙拦截、Kubernetes 网络策略阻止、OAP 服务未启动

4. **检查 OAP Server 状态**
   - 查看 OAP Server 日志，确认是否有接收数据的日志
   - 检查 OAP Server 存储（Elasticsearch）的磁盘空间和连接状态
   - 确认 OAP Server 的健康检查接口是否正常

5. **检查采样率配置**
   ```yaml
   # agent.config 中采样率配置
   agent.sample_n_per_3_secs: -1  # -1 表示全部采样，0 表示不采样
   ```
   生产环境可能配置了采样率（如 10%），导致部分请求未被追踪

6. **检查是否被过滤**
   - SkyWalking 默认忽略某些路径（如 `/health`、`/actuator/*`）
   - 检查 `agent.ignore_suffix` 和 `agent.trace.ignore_path` 配置

7. **检查 UI 查询条件**
   - 确认查询的时间范围是否包含请求时间
   - 确认查询的服务名是否正确
   - 确认是否选择了正确的 Endpoint

**常见问题总结：**
- Agent 未挂载：检查 JVM 启动参数
- 网络不通：Agent 无法上报数据到 OAP
- 版本不兼容：Agent 版本与 OAP 版本需一致
- 采样率低：生产环境默认采样率低，可临时调高测试
- 存储满了：Elasticsearch 磁盘空间不足，OAP 无法写入数据