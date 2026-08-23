# 推荐资源

> 精选 RocketMQ 学习资源，从官方文档到实战书籍。

---

## 官方资源

| 资源 | 链接 | 说明 |
|------|------|------|
| 官方文档 | https://rocketmq.apache.org/docs/ | 最新版本官方文档 |
| GitHub 仓库 | https://github.com/apache/rocketmq | 源码与 Issue |
| RocketMQ Dashboard | 直连控制台 | 可视化查看 Topic、Consumer 进度 |
| 官方示例 | https://github.com/apache/rocketmq/tree/develop/example | Java 示例代码 |

---

## 书籍推荐

| 书名 | 作者 | 推荐理由 |
|------|------|----------|
| **《RocketMQ 实战与原理解析》** | 杨开元 | 国人写的 MQ 实战书，理论与实践结合 |
| **《RocketMQ 技术内幕》** | 丁威 | 源码级讲解，适合深挖的同学 |
| **《深入理解 Apache RocketMQ》** | 林清山 | 架构设计 + 源码分析，进阶必读 |

---

## 视频教程

| 课程 | 平台 | 说明 |
|------|------|------|
| RocketMQ 官方入门教程 | B站 | 官方出品，适合入门 |
| Spring Boot + RocketMQ 实战 | 慕课网 | 结合 Spring Boot 的实战教程 |
| RocketMQ 源码解析 | 极客时间 | 付费课程，深度解读源码 |

---

## 常用工具

| 工具 | 说明 |
|------|------|
| **RocketMQ Dashboard** | 官方控制台，查看消息、消费进度、Topic 管理 |
| **mqadmin** | 命令行管理工具，查看集群、Broker、Topic 信息 |
| **rocketmq-exporter** | Prometheus 监控指标导出器 |
| **rocketmq-spring-boot-starter** | Spring Boot 集成 Starter |

---

## 社区与博客

| 名称 | 链接 | 说明 |
|------|------|------|
| Apache RocketMQ 社区 | dev@rocketmq.apache.org | 邮件列表交流 |
| RocketMQ 中文社区 | https://rocketmq.cloud/ | 中文技术文章 |
| 阿里云 RocketMQ 文档 | https://help.aliyun.com/product/29530.html | 企业版文档 |

---

## 快速参考

```bash
# mqadmin 常用命令
mqadmin clusterInfo -n localhost:9876              # 查看集群信息
mqadmin topicList -n localhost:9876                # 查看 Topic 列表
mqadmin topicRoute -n localhost:9876 -t topicName  # 查看 Topic 路由
mqadmin consumerProgress -n localhost:9876 -g groupName  # 查看消费进度
mqadmin updateTopic -n localhost:9876 -t topicName -r 8 -w 8  # 更新 Queue 数量
mqadmin resetOffsetByTime -n localhost:9876 -g groupName -t topicName -s now-1h  # 重置消费偏移
```