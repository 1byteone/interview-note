# T02: CLAUDE.md 企业级配置实战

> **[← 教程目录](README.md) | 工具: Claude Code | 时长: ~20min**

---

## Goal

为一个**多模块 Spring Cloud 微服务项目**编写分层 CLAUDE.md，让 Claude Code 理解项目架构并按规范工作。

## 前置条件

- 已有 Spring Cloud 微服务项目（或用 T01 生成的）
- 已安装 Claude Code

## Step 1: 根目录 CLAUDE.md（全局视角）

```bash
cat > CLAUDE.md << 'ENDOFFILE'
# 电商平台后端

## 架构概览
Spring Cloud Alibaba 微服务，Maven 多模块。

## 服务清单
- gateway/        → API 网关（Spring Cloud Gateway）
- user-service/   → 用户认证、注册、信息管理
- order-service/  → 订单创建、状态流转、超时取消
- product-service/→ 商品管理、库存、搜索
- payment-service/→ 支付、退款、回调
- common/         → 公共工具（Redis/MQ/异常/DTO）

## 服务间通信
- 同步: OpenFeign + Sentinel 熔断
- 异步: RocketMQ（订单创建→支付→库存扣减）

## 数据库
- 每个服务独立 MySQL schema
- 共享 common 库只放数据字典

## 编码铁律
1. Controller 禁止写业务逻辑
2. Service 方法 ≤30 行
3. DTO ≠ Entity，禁止直接暴露
4. 所有写操作必须幂等
5. 分布式锁用 Redisson，不用原生 SETNX
6. 异常统一 @RestControllerAdvice，禁止 catch 后不处理

## 构建命令
```bash
mvn clean compile                    # 全量编译
mvn test -pl order-service           # 单服务测试
mvn verify -pl common/common-core    # 公共模块验证
docker-compose up -d                 # 启动所有依赖
```

## Git
- 分支: feature/xxx, fix/xxx, refactor/xxx
- 提交: type(scope): description
- 禁止直接推 main
ENDOFFILE
```

## Step 2: 子目录 CLAUDE.md（本地约定）

```bash
# order-service/CLAUDE.md
cat > order-service/CLAUDE.md << 'ENDOFFILE'
# 订单服务

## 核心领域
- 订单创建（含库存预扣）
- 订单状态机: CREATED → PAID → SHIPPED → COMPLETED / CANCELLED
- 超时取消（RocketMQ 延迟消息 30min）

## 关键约束
- 库存扣减: Redis Lua 原子操作 + DB 兜底
- 幂等: orderId 作为全局唯一键
- 分布式锁: Redisson，key = lock:order:{userId}:{productId}

## 测试命令
```bash
mvn test -pl order-service                    # 单元测试
mvn verify -pl order-service                  # 含集成测试
mvn test -pl order-service -Dtest=OrderServiceTest  # 单个测试类
```

## 相关服务
- 调用 product-service: FeignClient ProductApi
- 调用 payment-service: FeignClient PaymentApi
- 发送 MQ: topic=ORDER_EVENT, tag=CREATED|PAID|CANCELLED
ENDOFFILE
```

## Step 3: 验证效果

```bash
claude
# 在 order-service 目录下启动
cd order-service && claude
```

输入：
```
分析当前服务的订单状态流转逻辑，
找出潜在的并发安全问题。
只读分析，不修改代码。
```

Claude Code 会：
1. 自动加载根 CLAUDE.md（全局架构）
2. 自动加载 order-service/CLAUDE.md（本地约定）
3. 精准定位到状态机相关代码
4. 按照你的约束（幂等、分布式锁）给出分析

## Step 4: 进阶——用 Hooks 自动更新

```json
// .claude/settings.json
{
  "hooks": {
    "Stop": [{
      "hooks": [{
        "type": "command",
        "command": "echo '[Auto] 会话结束，请检查 CLAUDE.md 是否需要更新'"
      }]
    }]
  }
}
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| CLAUDE.md 太长导致性能下降 | 根文件只放指针，细节放子目录 |
| 不同服务规范不一致 | 在根 CLAUDE.md 统一规范，子目录只放差异 |
| Claude 忽略了某个规则 | 在规则前加 `CRITICAL:` 或 `必须` 强调 |

## 延伸

- → [T03: 遗留代码重构](T03-Claude-Code遗留代码重构.md)
- → [07-Context Engineering](../07-Context-Engineering.md)
