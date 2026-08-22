# 数据同步：MySQL 与 Elasticsearch 的数据一致性方案

## 1. 概述

在 AI 商城等业务场景中，MySQL 作为关系型数据库承载事务性数据，Elasticsearch 提供全文搜索与聚合分析能力。两者之间的数据同步是保证搜索结果准确性的核心环节。本文将介绍三种主流的同步方案：Logstash 定时同步、Canal 实时 binlog 同步、以及业务双写策略。

---

## 2. Logstash 同步 MySQL → ES

### 2.1 基本原理

Logstash 通过 JDBC 插件定期拉取 MySQL 数据，写入 Elasticsearch。适用于对实时性要求不高的场景（如商品数据 T+1 同步）。

### 2.2 配置示例

```ruby
input {
  jdbc {
    jdbc_driver_library => "/path/mysql-connector-java.jar"
    jdbc_driver_class => "com.mysql.cj.jdbc.Driver"
    jdbc_connection_string => "jdbc:mysql://localhost:3306/ai_mall"
    jdbc_user => "root"
    jdbc_password => "password"
    statement => "SELECT id, name, title, price, category_id, update_time
                  FROM product WHERE update_time > :sql_last_value
                  ORDER BY update_time ASC"
    use_column_value => true
    tracking_column => "update_time"
    tracking_column_type => "timestamp"
    schedule => "*/5 * * * *"
    last_run_metadata_path => "/data/logstash/last_run.txt"
  }
}

output {
  elasticsearch {
    hosts => ["http://localhost:9200"]
    index => "products"
    document_id => "%{id}"
    action => "index"
  }
}
```

### 2.3 增量同步要点

- `sql_last_value`：Logstash 自动维护的上次同步时间戳，实现增量拉取
- `schedule`：cron 表达式控制同步频率，推荐 1-5 分钟
- `tracking_column`：选择 `update_time` 作为增量字段，每次同步记录最大时间戳

### 2.4 全量+增量策略

首次部署时先执行全量同步，后续通过 `update_time` 增量同步。全量同步可临时调大 Logstash 的 `jdbc_fetch_size` 和 ES 的批量参数，加速数据导入。

---

## 3. Canal 实时 binlog 同步

### 3.1 架构图

```
┌──────────┐     Binlog      ┌──────────┐     MQ/Adapter     ┌──────────┐
│  MySQL   │ ──────────────> │  Canal   │ ──────────────────> │    ES    │
│ Master   │                 │ Server   │                     │ Cluster  │
└──────────┘                 └──────────┘                     └──────────┘
                                  │
                                  │ 解析 Binlog
                                  │ INSERT / UPDATE / DELETE
                                  ▼
                          ┌──────────────────┐
                          │   Canal Adapter  │
                          │  (es 适配器)     │
                          └──────────────────┘
```

### 3.2 工作原理

Canal 模拟 MySQL Slave 节点，订阅 Master 的 binlog 事件。当 MySQL 发生数据变更时，Canal 实时解析 binlog 中的 INSERT、UPDATE、DELETE 操作，通过 Canal Adapter 的 ES 适配器同步到 Elasticsearch。

### 3.3 配置要点

```yaml
# canal-adapter/application.yml
canal.adapter:
  instances:
    - instance: example
      groups:
        - groupId: g1
          outerAdapters:
            - name: es
              hosts: http://localhost:9200
              properties:
                cluster.name: elasticsearch
                commit_batch: 3000
```

Canal 适配器支持批量提交（`commit_batch`），可显著提升同步吞吐量，适合高并发写入场景。

### 3.4 优缺点

| 维度 | 说明 |
|------|------|
| 实时性 | 秒级延迟，几乎与 MySQL 事务同步 |
| 侵入性 | 无需修改业务代码，对应用透明 |
| 复杂度 | 需独立部署 Canal 集群，运维成本较高 |

---

## 4. 业务双写策略

### 4.1 基本流程

在业务代码中，写 MySQL 后同步写入 ES，适用于对一致性要求高的场景。

```java
@Transactional
public void updateProduct(ProductDTO dto) {
    // 1. 更新 MySQL
    productMapper.updateById(dto);

    // 2. 同步写入 ES（同步方式）
    esProductRepository.save(convertToEsProduct(dto));
}
```

### 4.2 事务补偿

同步双写存在 MySQL 写入成功但 ES 写入失败的风险，需要补偿机制：

```java
@Service
public class ProductSyncService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RocketMQTemplate mqTemplate;

    @Transactional
    public void updateProductWithMQ(ProductDTO dto) {
        // 1. 更新 MySQL
        productMapper.updateById(dto);

        // 2. 发送 MQ 消息，异步同步 ES
        ProductSyncMessage msg = new ProductSyncMessage();
        msg.setProductId(dto.getId());
        msg.setAction(ProductAction.UPDATE);
        mqTemplate.send("sync-product-topic", msg);
    }
}
```

### 4.3 MQ 异步方案

引入 RocketMQ / Kafka 解耦双写逻辑：

- 写 MySQL 成功后发送 MQ 消息
- 消费者监听消息，写入 ES
- 失败重试机制：消费失败后进入重试队列，最多重试 3 次
- 最终一致性：结合定时任务扫描未同步的数据，兜底补偿

```java
@Component
@RocketMQMessageListener(consumerGroup = "sync-product-group", topic = "sync-product-topic")
public class ProductSyncConsumer implements RocketMQListener<ProductSyncMessage> {

    @Override
    public void onMessage(ProductSyncMessage message) {
        Product product = productMapper.selectById(message.getProductId());
        EsProduct esProduct = convertToEsProduct(product);
        esProductRepository.save(esProduct);
    }
}
```

---

## 5. 实战：AI 商城商品数据同步

### 5.1 增量字段设计

MySQL `product` 表必须包含 `update_time` 字段，Logstash 和 Canal 均依赖该字段识别变更：

```sql
ALTER TABLE product ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
CREATE INDEX idx_update_time ON product(update_time);
```

### 5.2 推荐策略

| 场景 | 方案 | 说明 |
|------|------|------|
| 商品搜索 | Canal + ES | 实时性要求高，商品上架后需立即被搜到 |
| 后台报表 | Logstash | 报表 T+1 即可，降低系统复杂度 |
| 库存变更 | 双写 + MQ | 库存数据一致性要求极高，需事务补偿 |

### 5.3 监控与告警

- 监控 Logstash 同步延迟：检查 `last_run_metadata_path` 中的时间戳
- 监控 Canal 延迟：Canal 提供 `delayTime` 指标
- 对比 MySQL 与 ES 的记录数，定期校验数据一致性

---

## 6. 总结

三种同步方案各有适用场景：Logstash 简单可靠适合低频同步；Canal 实时无侵入适合高实时性搜索；双写策略灵活可控适合核心业务链路。实际生产环境中常组合使用，以 Canal 为主链路，Logstash 定时兜底，确保数据最终一致。