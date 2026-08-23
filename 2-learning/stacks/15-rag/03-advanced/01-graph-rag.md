# Graph RAG — 知识图谱与检索增强生成

> 知识图谱通过显式的实体-关系结构，为 RAG 系统提供结构化推理能力。本章讲解如何将知识图谱与向量检索融合，构建既能理解语义又能进行多跳推理的 RAG 系统。

---

## 1. 为什么需要知识图谱？

### 1.1 向量检索的局限性

向量检索擅长语义相似度匹配，但存在以下不足：

| 能力 | 向量检索 | 知识图谱 |
|------|---------|---------|
| 语义相似 | 强 | 弱 |
| 精确匹配 | 中 | 强（精确匹配实体） |
| 多跳推理 | 不支持 | 原生支持（如"谁影响了谁"） |
| 关系表达 | 隐性（向量空间） | 显性（实体-关系-实体） |
| 可解释性 | 中 | 高（路径可追溯） |
| 动态更新 | 需重新索引 | 增量更新 |

### 1.2 知识图谱 + 向量 = 最佳组合

```
用户问题："水稻得了稻瘟病怎么治？"
    ↓
向量检索：找到"稻瘟病"相关文档（语义相似）
    ↓
图检索：MATCH 水稻-[:AFFECTS]-稻瘟病-[:TREATS]-药剂（结构化推理）
    ↓
RRF 融合 → Reranker 精排 → LLM 生成
```

---

## 2. 知识图谱基础

### 2.1 核心概念

知识图谱由**实体（节点）**和**关系（边）**组成。

```
实体类型：Crop（作物）, Disease（病害）, Pest（虫害）, Chemical（农药）, ...

关系类型：AFFECTS（影响）, DAMAGES（损害）, TREATS（治疗）, REQUIRES（需要）, ...

示例三元组：
(水稻)-[:AFFECTS]->(稻瘟病)
(稻瘟病)-[:TREATS]->(三环唑)
(水稻)-[:GROWS_IN]->(南方地区)
```

### 2.2 实体与关系提取

从非结构化文本中提取实体和关系，构建知识图谱。

```python
from langchain.chat_models import ChatOpenAI
from langchain.prompts import PromptTemplate
import json

def extract_entities_and_relations(text: str, llm) -> dict:
    """从文本中提取实体和关系"""
    prompt = PromptTemplate.from_template(
        "从以下文本中提取实体和关系，以 JSON 格式输出。\n\n"
        "实体类型：Crop（作物）, Disease（病害）, Pest（虫害）, Chemical（农药/化肥）, "
        "Variety（品种）, Soil（土壤）, Irrigation（灌溉）, Weather（天气）, "
        "Policy（政策）, Machinery（机械）, Region（地区）\n\n"
        "关系类型：AFFECTS, DAMAGES, TREATS, REQUIRES, RECOMMENDS, "
        "PREVENTS, GROWS_IN, CAUSES, USES, PRODUCES\n\n"
        "文本：{text}\n\n"
        "JSON 输出格式：\n"
        '{{"entities": [{{"name": "...", "type": "..."}}], '
        '"relations": [{{"source": "...", "target": "...", "type": "..."}}]}}'
    )
    response = llm.invoke(prompt.format(text=text))
    return json.loads(response.content)

# 使用示例
text = "水稻稻瘟病是由真菌引起的病害，可使用三环唑进行防治。"
result = extract_entities_and_relations(text, llm)
print(json.dumps(result, ensure_ascii=False, indent=2))
# {
#   "entities": [
#     {"name": "水稻", "type": "Crop"},
#     {"name": "稻瘟病", "type": "Disease"},
#     {"name": "三环唑", "type": "Chemical"}
#   ],
#   "relations": [
#     {"source": "水稻", "target": "稻瘟病", "type": "AFFECTS"},
#     {"source": "稻瘟病", "target": "三环唑", "type": "TREATS"}
#   ]
# }
```

---

## 3. 实战：Neo4j + 向量检索

### 3.1 Docker 部署 Neo4j

```yaml
# docker-compose.neo4j.yml
services:
  neo4j:
    image: neo4j:5-community
    ports:
      - "7474:7474"   # HTTP 浏览器
      - "7687:7687"   # Bolt 驱动
    environment:
      NEO4J_AUTH: neo4j/ragdemo2026
      NEO4J_PLUGINS: '["apoc"]'
    volumes:
      - neo4j_data:/data
      - ./kg/init.cypher:/init.cypher

volumes:
  neo4j_data:
```

### 3.2 Python 连接 Neo4j

```python
from neo4j import GraphDatabase
from typing import List, Dict

class Neo4jConnection:
    def __init__(self, uri="bolt://localhost:7687", user="neo4j", password="ragdemo2026"):
        self.driver = GraphDatabase.driver(uri, auth=(user, password))
    
    def close(self):
        self.driver.close()
    
    def query_graph(self, entity: str) -> List[Dict]:
        """查询与指定实体相关的所有节点和关系"""
        with self.driver.session() as session:
            result = session.run(
                "MATCH (n)-[r]->(m) WHERE n.name CONTAINS $e "
                "RETURN n.name AS source, type(r) AS relation, m.name AS target",
                e=entity
            )
            return [record.data() for record in result]
    
    def query_multi_hop(self, entity: str, max_hops: int = 2) -> List[Dict]:
        """多跳查询"""
        with self.driver.session() as session:
            query = f"""
            MATCH path = (n)-[*1..{max_hops}]->(m)
            WHERE n.name CONTAINS $e
            RETURN [node IN nodes(path) | node.name] AS nodes,
                   [rel IN relationships(path) | type(rel)] AS relations
            LIMIT 20
            """
            result = session.run(query, e=entity)
            return [record.data() for record in result]

# 使用
kg = Neo4jConnection()
relations = kg.query_graph("水稻")
for r in relations:
    print(f"{r['source']} -[{r['relation']}]-> {r['target']}")
```

### 3.3 初始化知识图谱

```cypher
// init.cypher — 创建作物知识图谱
CREATE (c1:Crop {name: '水稻', description: '主要粮食作物'})
CREATE (c2:Crop {name: '小麦', description: '北方主要粮食作物'})
CREATE (d1:Disease {name: '稻瘟病', description: '真菌性病害，危害水稻'})
CREATE (d2:Disease {name: '小麦锈病', description: '真菌性病害，危害小麦'})
CREATE (chem1:Chemical {name: '三环唑', description: '防治稻瘟病'})
CREATE (chem2:Chemical {name: '戊唑醇', description: '防治锈病'})

CREATE (c1)-[:AFFECTS]->(d1)
CREATE (d1)-[:TREATS]->(chem1)
CREATE (c2)-[:AFFECTS]->(d2)
CREATE (d2)-[:TREATS]->(chem2)
```

### 3.4 图检索 + 向量检索 融合

```python
import numpy as np
from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import HuggingFaceEmbeddings

class HybridGraphVectorRetriever:
    """图检索 + 向量检索 融合检索器"""
    
    def __init__(self, vector_store: Chroma, kg_conn: Neo4jConnection):
        self.vector_store = vector_store
        self.kg_conn = kg_conn
    
    def retrieve(self, query: str, top_k: int = 5):
        # 1. 向量检索（语义相似）
        vector_results = self.vector_store.similarity_search(query, k=top_k)
        
        # 2. 图检索（结构化推理）
        # 从查询中提取主实体（简化版：取查询中的关键词）
        entities = ["水稻", "稻瘟病", "小麦", "锈病"]  # 实际需用 NER 提取
        graph_results = []
        for entity in entities:
            if entity in query:
                relations = self.kg_conn.query_graph(entity)
                for r in relations:
                    graph_results.append(
                        f"{r['source']} {r['relation']} {r['target']}"
                    )
        
        # 3. RRF 融合
        all_results = self._rrf_fusion(vector_results, graph_results)
        return all_results
    
    def _rrf_fusion(self, vector_results, graph_results, k=60):
        # 简化的融合逻辑
        scores = {}
        for rank, doc in enumerate(vector_results):
            doc_id = hash(doc.page_content[:50])
            scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank + 1)
        
        for rank, result in enumerate(graph_results):
            doc_id = hash(result[:50])
            scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank + 1)
        
        return sorted(scores.items(), key=lambda x: x[1], reverse=True)
```

---

## 4. GraphRAG 完整链路

```
用户问题："水稻得了稻瘟病应该用什么药？"
    ↓
1. 实体提取（NER）
   实体：水稻（Crop）, 稻瘟病（Disease）
    ↓
2. 并行检索
   ├── 向量检索：找到"稻瘟病防治"相关文档
   └── 图检索：MATCH (水稻)-[:AFFECTS]->(稻瘟病)-[:TREATS]->(药)
    ↓
3. RRF 融合
   融合向量和图检索结果
    ↓
4. Reranker 精排
   交叉编码器精排
    ↓
5. LLM 生成
   基于检索到的文档 + 图结构生成回答
    ↓
回答："三环唑可用于防治水稻稻瘟病。建议在发病初期使用..."
```

### 代码实现

```python
def graph_rag_pipeline(query: str, vector_store, kg_conn, llm):
    """GraphRAG 完整流水线"""
    # 1. 实体提取
    entities = extract_entities(query)  # 简化版
    
    # 2. 向量检索
    vector_docs = vector_store.similarity_search(query, k=5)
    
    # 3. 图检索
    graph_context = []
    for entity in entities:
        relations = kg_conn.query_graph(entity)
        for r in relations:
            graph_context.append(
                f"关系：{r['source']} - {r['relation']} → {r['target']}"
            )
    
    # 4. 构建 Prompt
    context = "\n".join([d.page_content for d in vector_docs])
    graph_info = "\n".join(graph_context)
    
    prompt = f"""请基于以下信息回答问题。

文档信息：
{context}

知识图谱信息：
{graph_info}

问题：{query}

回答："""
    
    return llm.invoke(prompt)
```

---

## 5. 知识图谱 vs 向量检索 选型建议

| 场景 | 推荐方案 | 原因 |
|------|---------|------|
| 语义搜索、文档检索 | 纯向量检索 | 简单、高效 |
| 精确匹配（型号、编号） | BM25 或 Elasticsearch | 精确命中 |
| 多跳推理（A→B→C） | 知识图谱 | 原生支持 |
| 综合问答 | 向量 + 图混合检索 | 取长补短 |
| 结构化知识查询 | 知识图谱 + Cypher | 关系查询更准确 |

---

## 总结

本章你学会了：

- 知识图谱与向量检索的优劣势对比
- 实体与关系提取方法
- Neo4j 的 Docker 部署与 Python 集成
- 图检索 + 向量检索的融合方案
- GraphRAG 完整的处理流水线
- 不同场景的选型建议

下一步：学习 [幻觉控制](../03-advanced/02-hallucination-control.md)，探讨 RAG 系统中幻觉的来源与治理方案。