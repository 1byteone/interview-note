# 面试题自动生成器

## 使用方法

```bash
# 基本用法（扫描项目并生成面试题）
python question_generator.py --project-path ./my-java-project

# 指定输出目录
python question_generator.py -p ./my-project -o ./output

# 指定题型（choice,short-answer,scenario,design,deep-dive）
python question_generator.py -p ./my-project -t scenario,design

# 指定难度（junior/mid/senior/expert/all）
python question_generator.py -p ./my-project -d senior

# 限制题目数量
python question_generator.py -p ./my-project -m 30

# JSON 格式输出
python question_generator.py -p ./my-project -f json
```

## 参数说明

| 参数 | 简写 | 默认值 | 说明 |
|------|------|--------|------|
| `--project-path` | `-p` | 必填 | 项目路径 |
| `--output` | `-o` | 项目目录 | 输出目录 |
| `--type` | `-t` | 全部题型 | 题型（逗号分隔） |
| `--difficulty` | `-d` | `all` | 难度级别 |
| `--format` | `-f` | `md` | 输出格式（md/json） |
| `--max` | `-m` | `50` | 最大题目数 |

## 识别技术栈

支持从 pom.xml/build.gradle/requirements.txt 等文件自动识别以下技术栈：

### Java 后端
- Spring Boot / Spring Cloud
- Nacos / Gateway / OpenFeign / Sentinel / Seata
- RocketMQ / Kafka
- Redis / Redisson
- MySQL / MyBatis / JPA
- Elasticsearch
- Docker / Nginx

### AI 工程
- LangChain / LangGraph
- FastAPI / PyTorch
- Transformers / HuggingFace
- 向量数据库 (Chroma/Pinecone/Qdrant)
- OpenAI / Claude API

## 输出示例

扫描一个 Spring Cloud Alibaba 电商项目会生成：
- 12 种技术栈的面试题
- 从 L1 基础到 L5 架构设计
- 包含场景题和项目深挖题
- 附带追问和参考答案