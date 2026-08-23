# 后端开发速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| RESTful | 面向资源的架构风格，用 HTTP 方法表达对资源的操作 | REST 是风格规范不是协议，无认证状态 ≠ 无状态 |
| HTTP 方法 | GET 查 / POST 建（非幂等）/ PUT 整体改（幂等）/ PATCH 局部改 / DELETE 删 | PUT 和 PATCH 别混用；GET 严禁有副作用 |
| 状态码 | 2xx 成功、4xx 客户端错、5xx 服务端错 | 业务失败别一律 200 + code 字段；429/503 要会用 |
| 状态码细节 | 200 OK、201 Created、204 No Content、301 永久、302 临时、304 缓存、400 参数错、401 未认证、403 无权限、404 不存在、409 冲突、500 内部错、502 Bad Gateway、503 服务不可用 | 401 强调"你是谁"，403 强调"你能干嘛"，别搞反 |
| 分层架构 | Controller(入参校验)→Service(业务/事务)→Mapper/Repository(数据) | Controller 里写大量业务逻辑是大忌 |
| 贫血模型 | 数据和行为分离的领域对象，配合 Service 层做事 | 一上来就 DDD 很危险，CRUD 系统用贫血模型更务实 |
| DDD | 领域驱动设计：限界上下文、聚合根、领域事件；战术模式(Entity/VO/Aggregate) + 战略模式(BC) | DDD ≠ 一堆贫血 Service；不要为了 DDD 而 DDD |
| 充血模型 | 领域对象自带行为，业务内聚在实体中 | 复杂交互落地难，要配合事件/防腐层 |
| DTO | Data Transfer Object，API 边界传输对象，只传需要字段 | 不要直接序列化 Entity（会泄漏巨型字段、耦合持久层） |
| VO | Value Object：① 视图对象(view) ② 值对象(领域内不可变) | 同一个词两种含义，面试时先说清上下文 |
| PO/Entity | 与数据库表一一对应的持久化对象 | @JsonIgnore 敏感字段；避免循环引用 |
| 参数校验 | Bean Validation：@NotNull/@NotBlank/@Size/@Pattern + @Valid/@Validated | @Valid 分组校验、嵌套校验、自定义注解常考 |
| 全局异常 | @RestControllerAdvice + @ExceptionHandler 统一兜底 | 漏配导致 500 白屏；日志要带 traceId |
| 幂等 | 同一请求执行多次结果一致：唯一键、token、分布式锁、状态机 | 扣库存/转账/下单必须幂等；MQ 消费天然要幂等 |
| 版本控制 | URL /v1、Header Accept、Query param 三种 | 破坏性变更必须升版本，兼容旧客户端 |

## 🔧 常用命令/API

```java
// 分页 + 校验的典型 Controller 写法（Spring Boot 3）
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserVO create(@Valid @RequestBody UserCreateReq req) {
        Long id = userService.create(req);
        return userService.get(id);
    }
}
```

```java
// 全局异常处理模板
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResp> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResp.fail(400, msg));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResp> handleBiz(BizException e) {
        return ResponseEntity.status(e.getCode()).body(ApiResp.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResp> handleOther(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(500).body(ApiResp.fail(500, "Internal Server Error"));
    }
}
```

```java
// 幂等方案：唯一键去重（数据库唯一索引兜底）
record IdempotentRecord(String key, String result) {}

@Service
public class OrderService {
    public String create(String idempotentKey, OrderReq req) {
        // 1. 查唯一表，存在则直接返回上次结果
        // 2. 不存在 → 插入唯一键记录（利用唯一索引，冲突即重复请求）
        // 3. 成功后才执行业务；失败回滚并删除占位记录
        return orderNo;
    }
}
```

```bash
# 接口排查常用命令
curl -i -X POST http://localhost:8080/api/v1/users -H 'Content-Type: application/json' -d '{"name":"tom"}'
curl -X GET "http://localhost:8080/api/v1/users?page=1&size=20"
```

## 🎯 面试高频 TOP10

1. **Q: RESTful 设计规范有哪些？** **A:** 资源用名词复数、HTTP 方法语义化、状态码语义化、无状态 + 可缓存、版本控制、HATEOAS 可选。
2. **Q: CAP 是什么？BASE 呢？** **A:** CAP=一致性/可用性/分区容错三选二，分布式必须选 P，所以是 CP 或 AP；BASE=最终一致(基本可用+软状态+最终一致)，是对 AP 的工程落地。
3. **Q: 分布式 ID 方案有哪些？** **A:** UUID(无序、长)、雪花算法(趋势递增、依赖时钟)、号段模式(数据库批量取号)、Redis INCR(依赖 Redis)、美团 Leaf 组合方案。
4. **Q: 雪花算法为什么会有时钟回拨问题？** **A:** 时钟回拨会导致 ID 重复，方案：等待回拨追平、拒绝分配、备用时钟、号段兜底。
5. **Q: 秒杀系统怎么设计？** **A:** 前端限流+静态化 → 网关限流 → Redis 预扣减库存(原子) → MQ 异步削峰下单 → 数据库最终扣减，全链路监控兜底。
6. **Q: 怎么防止超卖？** **A:** 数据库乐观锁(SQL 带 stock>0 条件)、Redis Lua 原子扣减、分布式锁串行化；禁止单纯先查后改。
7. **Q: 短链系统怎么设计？** **A:** 发号器生成短码(进制/哈希+冲突处理)存映射，302 重定向跳转；读写用缓存+DB 双写；统计点击量。
8. **Q: 如何实现接口幂等？** **A:** 唯一业务键+唯一索引兜底、移动端 token 预生成、Redis SETNX、数据库乐观锁版本号、状态机。
9. **Q: 统一异常处理怎么做？** **A:** 业务异常(BizException)+系统异常分开，@RestControllerAdvice 统一捕获，返回统一响应结构，日志带 traceId 便于全链路排查。
10. **Q: 一个高并发订单系统如何落地？** **A:** 水平分库分表 → 本地+远程缓存分层 → MQ 解耦削峰 → 兜底任务补偿对账 → 监控告警，逐层各司其职。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| Controller 直接返回 Entity 对象 | 返回 DTO/VO，隐藏数据库字段和敏感信息 |
| 所有错误都返回 200 + 错误码 | 使用正确的 HTTP 状态码 + 统一错误体 |
| 事务里做远程调用/IO 操作 | 事务只包数据库操作；远程调用放事务外(本地消息表/最终一致) |
| 把密码明文存数据库 | BCrypt 加盐哈希存储，永不返回给前端 |
| GET 请求携带大量过滤条件且不分页 | 参数化 + 分页(必带 limit)，防全表扫描和接口滥用 |
| 参数校验散落在 Service 里手写 if | 统一 Bean Validation 注解 + @Valid 声明式校验 |
| 无日志或日志裸打敏感字段 | 结构化日志 + traceId 贯穿 + 脱敏工具 |
| 大文件/大对象直接进 DB | 存 OSS/对象存储，DB 只存 URL 和元信息 |
| 未考虑限流和降级 | 网关/中间件流控 + 熔断降级，保护下游 |
| 写多读少也全部走缓存 | 分层：静态数据走 CDN、热点走缓存、冷数据走 DB，缓存加过期+击穿保护 |

## 📐 架构设计要点

- **分层铁律**：Controller 薄、Service 厚(业务)、Mapper 纯(数据)；依赖单向向下，禁止反向依赖。
- **API 设计**：RESTful 资源语义 + 版本管理 + 幂等键 + 统一响应骨架 `{code,msg,data}`。
- **一致性策略**：强一致(事务/锁) 、最终一致(MQ/补偿/对账)；优先降低锁范围，能用乐观锁不用悲观锁。
- **高可用三板斧**：负载均衡 + 健康检查探活、降级熔断(Sentinel/Resilience4j)、故障转移与备份。
- **可观测**：日志(traceId) + 指标(延迟/错误率/QPS) + 链路追踪(Micrometer/OpenTelemetry) 三件套。
- **容量设计**：先估算 QPS → 压测定基线 → 缓存扛热点 → MQ 削峰 → 分库分表留余量。
- **防腐层(DDD)**：外部系统/旧系统变化不污染核心域，通过接口或事件适配。

## 🔗 关联技术

- **HTTP/网络**：状态码、幂等、Keep-Alive、HTTP/2 多路复用是后端基本功。
- **MySQL**：事务、索引设计支撑数据层正确性和性能。
- **Redis**：缓存、分布式锁、计数器承接高并发场景。
- **RocketMQ/Kafka**：解耦、削峰、最终一致的落地载体。
- **Docker/K8s**：部署形态决定了架构的可伸缩性。
- **网关(Nginx/Gateway)**：统一入口、限流、鉴权的前置设施。