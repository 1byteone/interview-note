# 幻觉控制

> 幻觉（Hallucination）是 RAG 系统面临的最大挑战之一。即使检索到了正确的文档，LLM 仍可能生成与事实不符的内容。本章系统讲解幻觉的来源、检测方法和控制策略。

---

## 1. 幻觉来源分析

### 1.1 RAG 中的三种幻觉类型

| 类型 | 表现 | 原因 |
|------|------|------|
| **输入冲突** | LLM 忽略检索到的文档，自己编造 | Prompt 指令弱、模型过度自信 |
| **上下文冲突** | 检索到的文档本身包含矛盾信息 | 知识库质量差、版本冲突 |
| **事实捏造** | LLM 生成看似合理但实际错误的内容 | 模型参数化知识干扰、外推 |

### 1.2 幻觉的根因

```
检索层问题：
  - 检索到的文档不相关（recall 低）
  - 文档被截断，关键信息丢失
  - 检索结果中包含过时或错误的信息

增强层问题：
  - 上下文过长，LLM 找不到关键信息
  - 多文档矛盾，LLM 无所适从
  - Prompt 未明确要求"仅基于文档回答"

生成层问题：
  - 模型参数化知识太强，覆盖了检索结果
  - 模型过度自信，不承认不知道
  - 温度太高导致随机生成
```

---

## 2. 证据门控（Evidence Gate）

证据门控是幻觉控制的第一道防线，在检索结果输入 LLM 之前进行质量过滤。

### 2.1 证据分级系统

```python
class EvidenceGater:
    """证据门控：对检索结果进行质量分级和过滤"""
    
    def __init__(self):
        self.evidence_levels = {
            "A": {"label": "官方来源", "threshold": 0.8, "description": "可支撑结论"},
            "B": {"label": "权威文档", "threshold": 0.6, "description": "背景解释"},
            "C": {"label": "普通知识", "threshold": 0.4, "description": "仅参考"},
            "D": {"label": "未核验", "threshold": 0.0, "description": "不可用"},
        }
    
    def classify_evidence(self, doc, score, source_type):
        """对证据进行分级"""
        if score >= 0.8 and source_type == "official":
            return "A"
        elif score >= 0.6:
            return "B"
        elif score >= 0.4:
            return "C"
        else:
            return "D"
    
    def gate(self, results, min_level="C", high_risk_topics=None):
        """
        证据门控过滤
        min_level: 最低证据级别
        high_risk_topics: 高风险主题（如涉及安全/政策时需更高等级）
        """
        high_risk_topics = high_risk_topics or ["农药", "剂量", "政策", "法律"]
        
        passed = []
        for doc, score in results:
            level = self.classify_evidence(doc, score, doc.metadata.get("source_type", ""))
            
            # 高风险主题要求更高证据等级
            required_level = "A" if any(
                topic in doc.page_content for topic in high_risk_topics
            ) else min_level
            
            if self._level_rank(level) >= self._level_rank(required_level):
                passed.append((doc, level))
            else:
                print(f"证据门控过滤：{level} 级证据，需要 {required_level} 级")
        
        return passed
    
    def _level_rank(self, level):
        return {"A": 4, "B": 3, "C": 2, "D": 1}.get(level, 0)

# 使用
gater = EvidenceGater()
filtered = gater.gate(
    results,
    min_level="C",
    high_risk_topics=["农药", "剂量"],
)
```

### 2.2 高风险闸门机制

当涉及高风险话题时，使用更严格的证据要求：

```python
def high_risk_gate(query: str, results, llm):
    """高风险闸门：涉及政策/安全时，只使用 A 级证据"""
    
    # 检测高风险话题
    risk_prompt = f"以下问题是否涉及安全、政策、法律、医疗等高风险领域？仅回答 'yes' 或 'no'。\n问题：{query}"
    is_high_risk = llm.invoke(risk_prompt).content.strip().lower() == "yes"
    
    if is_high_risk:
        # 只保留 A 级证据
        results = [r for r in results if r[1] == "A"]
        if not results:
            return None  # 无可用证据，拒绝回答
    
    return results
```

---

## 3. 引用溯源

在生成回答时附上引用来源，让用户和系统都能追溯答案的依据。

### 3.1 带引用的 Prompt

```python
def generate_with_citations(query: str, docs: list, llm) -> str:
    """生成带引用的回答"""
    # 为每个文档分配编号
    numbered_docs = []
    for i, doc in enumerate(docs, 1):
        numbered_docs.append(f"[{i}] {doc.page_content}")
    
    context = "\n\n".join(numbered_docs)
    
    prompt = f"""请基于以下文档回答问题。回答中需在引用内容后标注来源编号。

文档：
{context}

要求：
1. 如果文档中有相关信息，请使用该信息，并标注来源编号，如 [1][2]
2. 如果文档中没有相关信息，请明确回答"文档中没有相关信息"
3. 不要编造信息
4. 如果多个文档信息矛盾，请指出矛盾之处

问题：{query}

回答："""
    
    return llm.invoke(prompt).content
```

### 3.2 引用格式示例

```
回答：根据文档，7 天无理由退货的适用条件是"商品签收后 7 天内，在不影响二次销售的前提下" [1]。 
退货流程为：登录商城 APP → 我的订单 → 申请售后 → 选择退货退款 → 提交 [2]。
退款时效为审核通过后 1-3 个工作日 [3]。

来源：
[1] 《退换货政策》第 2 条
[2] 《退货流程指南》第 1 节
[3] 《退款说明》第 3 条
```

---

## 4. 领域守卫（Domain Guard）

防止 RAG 系统回答非领域问题，避免生成不可靠内容。

```python
class DomainGuard:
    """领域守卫：控制 RAG 系统的回答范围"""
    
    def __init__(self, domain_terms: list, forbidden_terms: list):
        self.domain_terms = domain_terms
        self.forbidden_terms = forbidden_terms
    
    def check_query(self, query: str) -> dict:
        """检查查询是否在领域范围内"""
        domain_hits = [t for t in self.domain_terms if t in query]
        forbidden_hits = [t for t in self.forbidden_terms if t in query]
        
        if forbidden_hits and not domain_hits:
            return {
                "allowed": False,
                "reason": "非领域问题",
                "detail": f"检测到非领域关键词：{forbidden_hits}"
            }
        
        if domain_hits:
            return {
                "allowed": True,
                "reason": "领域内问题",
                "detail": f"匹配到领域关键词：{domain_hits}"
            }
        
        return {
            "allowed": False,
            "reason": "无法确定领域",
            "detail": "未检测到明确的领域关键词，建议明确问题范围"
        }

# 农业领域示例
guard = DomainGuard(
    domain_terms=["水稻", "小麦", "病虫害", "施肥", "农药", "产量", "土壤", "灌溉"],
    forbidden_terms=["java", "python", "编程", "代码", "spring", "微服务"],
)

result = guard.check_query("水稻稻瘟病防治方法")
# {"allowed": True, "reason": "领域内问题", ...}

result = guard.check_query("Java 微服务架构设计")
# {"allowed": False, "reason": "非领域问题", ...}
```

---

## 5. 幻觉检测与评估

### 5.1 自动检测方法

```python
def detect_hallucination(query: str, answer: str, docs: list, llm) -> dict:
    """检测回答中是否存在幻觉"""
    
    # 构建检测 Prompt
    doc_text = "\n".join([d.page_content for d in docs])
    
    prompt = f"""请判断以下回答是否基于提供的文档，是否存在幻觉。

文档：
{doc_text}

问题：{query}

回答：{answer}

请逐项检查：
1. 回答中的每个事实是否都在文档中有依据？
2. 是否有文档中没有的信息？
3. 是否有与文档矛盾的信息？

输出 JSON 格式：
{{
    "has_hallucination": true/false,
    "hallucination_details": ["具体幻觉内容1", "具体幻觉内容2"],
    "confidence": 0.95,
    "suggestion": "建议修改的内容"
}}"""
    
    import json
    response = llm.invoke(prompt)
    return json.loads(response.content)
```

### 5.2 统计指标

```python
def evaluate_hallucination(answers_with_docs: list, llm) -> dict:
    """批量评估幻觉率"""
    total = len(answers_with_docs)
    hallucinated = 0
    details = []
    
    for item in answers_with_docs:
        result = detect_hallucination(
            item["query"],
            item["answer"],
            item["docs"],
            llm,
        )
        if result["has_hallucination"]:
            hallucinated += 1
            details.append(result)
    
    return {
        "total": total,
        "hallucinated": hallucinated,
        "hallucination_rate": hallucinated / total if total > 0 else 0,
        "details": details,
    }
```

---

## 6. 幻觉控制最佳实践

### 6.1 多层级防御

```
第一层：检索质量
  - 混合检索（向量 + BM25）
  - Reranker 精排
  - 证据门控过滤

第二层：Prompt 约束
  - 明确要求"仅基于文档回答"
  - 要求标注引用来源
  - 允许回答"不知道"

第三层：生成后验证
  - 事实一致性检查
  - 引用溯源验证
  - 领域守卫拦截

第四层：监控与反馈
  - 用户反馈收集
  - 自动评估流水线
  - 持续改进
```

### 6.2 关键配置

```python
# RAG 幻觉控制配置
HALLUCINATION_CONFIG = {
    # 证据门控
    "evidence_min_level": "C",
    "high_risk_topics": ["农药", "剂量", "政策", "法律"],
    "high_risk_min_level": "A",
    
    # 生成参数
    "temperature": 0.0,        # 降低随机性
    "top_p": 0.9,
    "max_tokens": 1024,
    
    # 引用
    "require_citations": True,
    
    # 领域守卫
    "domain_guard_enabled": True,
    
    # 检测
    "hallucination_detection": True,
    "auto_reject_threshold": 0.7,  # 幻觉概率超过此值则拒绝回答
}
```

---

## 总结

本章你学会了：

- 幻觉的三种类型及其根因分析
- 证据门控机制：证据分级、高风险闸门
- 引用溯源方法：带来源编号的生成
- 领域守卫：防止跨领域回答
- 幻觉自动检测与评估方法
- 多层级幻觉控制防御体系

下一步：学习 [高级 RAG 模式](../03-advanced/03-advanced-rag-patterns.md)，了解 Self-RAG、Corrective RAG 等前沿方案。