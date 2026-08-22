# Sentinel 限流/熔断/降级 — 流量治理三件套

> 🎯 进阶路线 · 预计阅读时间：40 分钟
> 目标：掌握 Sentinel 的核心能力（流量控制、熔断降级、热点限流、系统保护），理解限流算法原理，并能在 AI 商城秒杀场景中落地。

---

## 一、Sentinel 核心功能

Sentinel 是阿里开源的**面向分布式服务架构的流量治理组件**，被誉为"流量防卫兵"。相比 Hystrix，它强调**以流量为切入点**，提供四大核心能力：

| 能力 | 说明 | 典型场景 |
|------|------|----------|
| 流量控制 | 控制接口 QPS、线程数，防止服务被突发流量打垮 | 秒杀、大促、爬虫防护 |
| 熔断降级 | 下游依赖异常时快速失败，保护调用方 | 缓存/DB/第三方 API 故障 |
| 热点参数限流 | 针对某个热点参数（如商品 ID、用户 ID）单独限流 | 爆款商品被刷、恶意下单 |
| 系统保护 | 基于系统负载（Load、CPU、RT）自适应兜底 | 机器资源接近饱和时兜底 |

**设计理念**：先做流控，再做熔断，最后靠系统保护兜底。三层防线层层递进。

---

## 二、限流算法对比

Sentinel 默认使用**滑动窗口**，同时可切换为**令牌桶或漏桶**模式（LeapArray 实现）。先理解四种主流算法：

### 2.1 固定窗口（Fixed Window）

把时间切成固定大小的桶（如 1 秒），每个桶内计数器累加，超过阈值即拒绝，桶结束清零。

```
|-------|-------|-------|-------|
  0~1s    1~2s    2~3s    3~4s   （QPS 上限 10）
```

**缺陷：临界问题**。第 0.9s 请求 10 次 + 第 1.1s 请求 10 次 = 0.2 秒内 20 次，远超阈值，但两个桶各自都没超限。

### 2.2 滑动窗口（Sliding Window）

把固定窗口再细分为多个小格子（如 1s = 5 格，每格 200ms），每次统计**最近一个完整窗口期**的请求量。请求进入时，滑动窗口随当前时间推进，规避了临界突发问题。Sentinel 默认采用此算法。

### 2.3 令牌桶（Token Bucket）

系统以恒定速率（如 10 个/秒）往桶里放令牌，桶容量有上限；请求必须先拿到令牌才能放行。**允许一定程度的突发**（桶内积攒的令牌可瞬间消耗），适合允许突发的场景。

### 2.4 漏桶（Leakly Bucket）

请求以任意速率进入桶中，桶底部以**恒定速率**漏出（处理）。桶满则丢弃请求。**输出绝对平滑**，但无法应对突发流量，适合对实时性要求高的场景（如消息削峰）。

### 2.5 对比小结

| 算法 | 平滑性 | 允许突发 | 实现复杂度 | 适用场景 |
|------|--------|----------|------------|----------|
| 固定窗口 | 差（临界问题） | 是 | 低 | 简单限流 |
| 滑动窗口 | 较好 | 有限 | 中 | Sentinel 默认 |
| 令牌桶 | 较好 | 是 | 中 | 允许突发（如网关） |
| 漏桶 | 最好 | 否 | 中 | 削峰填谷（如 MQ） |

---

## 三、熔断降级

### 3.1 熔断状态机

```
       失败率达到阈值
  ┌───────────────┐
  │   CLOSED（关闭）│──────┐
  └───────────────┘      ▼
        ▲        ┌───────────────┐
        │        │    OPEN（打开） │
        │        └───────────────┘
        │           超时窗口后     │
        │              ┌─────────▼────────┐
        └──────────────┤  HALF_OPEN（半开） │
       探测成功则关回      └──────────────────┘
```

- **CLOSED（关闭）**：正常状态，放行所有请求；统计熔断指标。
- **OPEN（打开）**：达到阈值后熔断，直接拒绝请求（快速失败）；持续一个熔断时长。
- **HALF_OPEN（半开）**：熔断时长结束后进入，允许**少量探测请求**通过；若成功则恢复 CLOSED，失败则回到 OPEN。

### 3.2 三种熔断策略

| 策略 | 触发条件 | 场景 |
|------|----------|------|
| 慢调用比例 | 响应时间 > 阈值的请求比例 ≥ 比例阈值（如 50%），且请求数 ≥ 最小请求数 | 慢 SQL、下游服务变慢 |
| 异常比例 | 异常请求比例 ≥ 比例阈值 | 下游故障率升高 |
| 异常数 | 异常请求数 ≥ 数量阈值（分钟级） | 下游彻底不可用等明确故障 |

配置示例（控制台）：

```json
{
  "resource": "order:create",
  "grade": 0,
  "count": 500,
  "timeWindow": 10,
  "statIntervalMs": 1000,
  "slowRatioThreshold": 0.5,
  "minRequestAmount": 100
}
```

---

## 四、@SentinelResource 注解

通过注解实现"代码零侵入"的埋点：

```java
@Service
public class OrderService {

    /**
     * 秒杀下单接口
     * blockHandler：触发限流/降级时调用
     * fallback：业务异常时调用
     */
    @SentinelResource(
        value = "seckill:createOrder",
        blockHandler = "createOrderBlock",
        fallback = "createOrderFallback",
        exceptionsToIgnore = {IllegalArgumentException.class}
    )
    public Order createOrder(Long userId, Long skuId, int quantity) {
        // 核心秒杀逻辑：扣库存 → 生成订单 → 发消息
        return doCreateOrder(userId, skuId, quantity);
    }

    // block 方法：参数与原方法一致 + BlockException
    public Order createOrderBlock(Long userId, Long skuId, int quantity,
                                  BlockException ex) {
        // 限流兜底：提示用户稍后再试 or 走排队
        throw new BizException(ErrorCode.TRAFFIC_CONTROL, "秒杀火爆，请稍后再试");
    }

    // fallback 方法：参数与原方法一致 + Throwable，用于处理业务异常
    public Order createOrderFallback(Long userId, Long skuId, int quantity,
                                     Throwable t) {
        throw new BizException(ErrorCode.SECKILL_FAILED, "秒杀失败：" + t.getMessage());
    }
}
```

> 注意：`blockHandler` 处理的是 Sentinel 抛出的 `BlockException`（限流/熔断），`fallback` 处理的是**业务异常**，两者互相补充、互不冲突。

---

## 五、规则持久化到 Nacos

默认规则保存在内存中，重启即丢失；生产环境需持久化到 Nacos，并接入**动态数据源**实现热更新。

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

```yaml
spring:
  cloud:
    sentinel:
      datasource:
        flow:                        # 流控规则数据源
          nacos:
            server-addr: ${NACOS_ADDR:127.0.0.1:8848}
            dataId: mall-order-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: flow           # flow / degrade / param-flow / system
        degrade:
          nacos:
            server-addr: ${NACOS_ADDR:127.0.0.1:8848}
            dataId: mall-order-degrade-rules
            groupId: SENTINEL_GROUP
            rule-type: degrade
```

启动时 Sentinel 自动从 Nacos 拉取规则，控制台或 Nacos 修改规则后**实时推送**到所有实例，无需重启、无需改代码。

---

## 六、实战：AI 商城秒杀限流

**场景**：AI 商城商品「AI 绘画年卡」秒杀，预估同时在线 10 万人，接口峰值需求 5000 QPS，数据库只能抗 500 QPS。

**方案**：

1. **入口 QoS 限流**：网关层按 `秒杀商品 ID` 做热点参数限流，设置单商品 2000 QPS；
2. **应用层限流**：`seckill:createOrder` 资源限流 1000 QPS（滑动窗口），超限直接返回"排队中"；
3. **熔断保护**：库存服务慢调用比例 > 50% 时熔断 10s，防止雪崩；
4. **系统保护**：服务 CPU 使用率 > 80% 或 Load > 核心数 * 1.5 时触发自适应兜底，拒绝全部非核心请求；
5. **兜底链路**：MQ 削峰 + Redis 预扣库存，秒杀请求入队异步处理，前端轮询结果。

**效果**：秒杀期间下单成功率 100%（排队即成功），订单服务 QPS 峰值控制在 1000 以内，数据库 0 打满，全程无雪崩。

---

## 七、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Sentinel 和 Hystrix 区别？ | Sentinel 以流量为切入点，支持流控+熔断+热参+系统保护，与 Spring Cloud Alibaba 生态集成 |
| 固定窗口临界问题？ | 相邻两个窗口各不超限，但边界处短时间内请求可能远超阈值 |
| Sentinel 默认限流算法？ | 滑动窗口（LeapArray 实现） |
| 熔断状态机有哪些状态？ | CLOSED → OPEN → HALF_OPEN，探测成功回 CLOSED |
| 三种熔断策略？ | 慢调用比例、异常比例、异常数 |
| blockHandler 和 fallback 区别？ | block 处理 BlockException，fallback 处理业务异常 |
| 规则怎么持久化？ | 接入 Nacos/Apollo 动态数据源，修改实时推送 |