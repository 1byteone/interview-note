# 安全与内容审核

## Moderation API

### 概述

Moderation API 是 OpenAI 提供的内容安全审核接口，能够检测文本和图片中的违规内容，支持多种分类。

### 支持的内容分类

| 分类 | 说明 | 示例 |
|------|------|------|
| hate | 仇恨言论 | 针对种族、民族、宗教的歧视 |
| hate/threatening | 仇恨威胁 | 针对特定群体的暴力威胁 |
| harassment | 骚扰 | 针对个人的恶意言论 |
| harassment/threatening | 骚扰威胁 | 针对个人的暴力威胁 |
| self-harm | 自残 | 自伤、自杀相关内容 |
| self-harm/intent | 自残意图 | 明确的自残计划 |
| self-harm/instructions | 自残指导 | 自残方法指导 |
| sexual | 色情内容 | 露骨的色情描述 |
| sexual/minors | 涉及未成年人的色情内容 | 严禁 |
| violence | 暴力 | 暴力行为描述 |
| violence/graphic | 暴力细节 | 血腥暴力细节描述 |

### 基本使用

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")


def check_content(text: str) -> dict:
    """检查内容是否违规"""
    response = client.moderations.create(
        model="omni-moderation-latest",  # 最新审核模型
        input=text,
    )

    result = response.results[0]
    return {
        "flagged": result.flagged,
        "categories": {
            category: getattr(result.categories, category)
            for category in [
                "harassment", "hate", "self_harm", "sexual", "violence"
            ]
        },
        "scores": {
            category: getattr(result.category_scores, category)
            for category in [
                "harassment", "hate", "self_harm", "sexual", "violence"
            ]
        },
    }


# 示例
test_texts = [
    "这个商品质量很好，推荐购买",
    "这家店太差了，大家不要买",
    "我要投诉你们，客服态度恶劣",
]

for text in test_texts:
    result = check_content(text)
    print(f"文本: {text[:20]}...")
    print(f"违规: {result['flagged']}")
    print("---")
```

## 内容安全策略

### 多层安全架构

```
用户输入
    │
    ▼
┌─────────────────────┐
│  Layer 1: 输入过滤   │  ← Moderation API
│  检查用户输入是否违规 │
└─────────┬───────────┘
          │ 通过
          ▼
┌─────────────────────┐
│  Layer 2: Prompt 安全│  ← System Prompt 约束
│  模型行为约束        │
└─────────┬───────────┘
          │ 通过
          ▼
┌─────────────────────┐
│  Layer 3: 输出过滤   │  ← Moderation API
│  检查模型输出是否违规 │
└─────────┬───────────┘
          │ 通过
          ▼
┌─────────────────────┐
│  Layer 4: 业务规则   │  ← 自定义规则
│  PII 脱敏、关键词过滤 │
└─────────┬───────────┘
          │ 通过
          ▼
       返回给用户
```

### 输入过滤

```python
class InputFilter:
    """用户输入过滤器"""

    def __init__(self, client: OpenAI):
        self.client = client

    def filter_input(self, user_input: str) -> tuple:
        """过滤用户输入，返回 (是否通过, 原因)"""
        # 1. Moderation 检查
        moderation = self.client.moderations.create(
            model="omni-moderation-latest",
            input=user_input,
        )

        if moderation.results[0].flagged:
            return False, "输入内容包含违规信息"

        # 2. 长度检查
        if len(user_input) > 10000:
            return False, "输入内容过长"

        # 3. 敏感信息检查（简化示例）
        sensitive_patterns = [
            r"\d{18}",           # 身份证号
            r"1[3-9]\d{9}",      # 手机号
            r"\d{16}",           # 银行卡号
        ]
        import re
        for pattern in sensitive_patterns:
            if re.search(pattern, user_input):
                return False, "输入包含敏感个人信息"

        return True, "通过"


# 使用
filter = InputFilter(client)
is_safe, reason = filter.filter_input("我要投诉，你们服务太差了！")
print(f"安全: {is_safe}, 原因: {reason}")
```

### 输出过滤

```python
class OutputFilter:
    """模型输出过滤器"""

    def __init__(self, client: OpenAI):
        self.client = client

    def filter_output(self, model_output: str) -> tuple:
        """过滤模型输出，返回 (是否通过, 过滤后的内容)"""
        # 1. Moderation 检查
        moderation = self.client.moderations.create(
            model="omni-moderation-latest",
            input=model_output,
        )

        if moderation.results[0].flagged:
            return False, "模型生成内容违规，已拦截"

        # 2. PII 脱敏
        import re
        cleaned = model_output
        cleaned = re.sub(r"1[3-9]\d{9}", "[手机号已隐藏]", cleaned)
        cleaned = re.sub(r"\d{18}", "[身份证已隐藏]", cleaned)

        # 3. 业务规则检查
        banned_words = ["诈骗", "赌博", "色情"]
        for word in banned_words:
            if word in cleaned:
                return False, "输出包含违禁词"

        return True, cleaned
```

## 实战：AI 商城内容审核

### 用户评论审核

```python
class ReviewModerator:
    """AI 商城评论审核系统"""

    def __init__(self, client: OpenAI):
        self.client = client
        self.thresholds = {
            "harassment": 0.5,
            "hate": 0.3,
            "sexual": 0.2,
            "violence": 0.4,
            "self_harm": 0.3,
        }

    def moderate_review(self, review: dict) -> dict:
        """审核一条用户评论"""
        content = review.get("content", "")
        moderation = self.client.moderations.create(
            model="omni-moderation-latest",
            input=content,
        )

        result = moderation.results[0]
        decision = "approved"
        reasons = []

        # 检查是否违规
        if result.flagged:
            for category, threshold in self.thresholds.items():
                score = getattr(result.category_scores, category, 0)
                if score > threshold:
                    reasons.append(f"{category}: {score:.2f}")

            if reasons:
                decision = "rejected"

        # 使用模型判断评论质量
        if decision == "approved":
            quality = self._check_review_quality(content)
            if quality["spam_probability"] > 0.7:
                decision = "flagged_for_review"
                reasons.append("疑似垃圾评论")

        return {
            "review_id": review.get("id"),
            "decision": decision,
            "reasons": reasons,
            "scores": {
                k: getattr(result.category_scores, k, 0)
                for k in self.thresholds
            },
        }

    def _check_review_quality(self, content: str) -> dict:
        """检查评论质量"""
        response = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": (
                        "分析以下评论的质量，返回 JSON：\n"
                        "- spam_probability: 0-1 的垃圾评论概率\n"
                        "- relevance: 是否与商品相关\n"
                        "- helpfulness: 是否有帮助"
                    ),
                },
                {"role": "user", "content": content},
            ],
            response_format={"type": "json_object"},
        )
        return json.loads(response.choices[0].message.content)

    def batch_moderate(self, reviews: list) -> list:
        """批量审核评论"""
        results = []
        for review in reviews:
            result = self.moderate_review(review)
            results.append(result)
        return results


# 使用
moderator = ReviewModerator(client)

reviews = [
    {"id": "R001", "content": "手机很好用，拍照清晰，推荐购买"},
    {"id": "R002", "content": "这家店太黑了，大家不要买，质量差服务差"},
    {"id": "R003", "content": "加微信 xxx，优惠多多"},
]

for r in moderator.batch_moderate(reviews):
    print(f"评论 {r['review_id']}: {r['decision']} - {r['reasons']}")
```

### 商品描述审核

```python
def moderate_product_description(description: dict) -> dict:
    """审核商品描述"""
    text_to_check = f"{description.get('title', '')} {description.get('description', '')}"

    # Moderation 检查
    moderation = client.moderations.create(
        model="omni-moderation-latest",
        input=text_to_check,
    )

    result = moderation.results[0]

    # 合规检查
    compliance_issues = []
    if result.flagged:
        compliance_issues.append("包含违规内容")

    # 价格格式检查
    price = description.get("price", 0)
    if price <= 0:
        compliance_issues.append("价格格式异常")

    # 标题长度检查
    if len(description.get("title", "")) < 5:
        compliance_issues.append("标题过短")

    return {
        "product_id": description.get("id"),
        "approved": len(compliance_issues) == 0,
        "issues": compliance_issues,
    }
```

## 最佳实践

### 1. 多层审核
- 不要只依赖单一的安全措施
- 结合 Moderation API 和业务规则
- 对高风险内容进行人工复审

### 2. 阈值调优
```python
# 根据不同场景调整敏感度
def get_thresholds(scenario: str) -> dict:
    thresholds = {
        "user_review": {
            "harassment": 0.5,
            "hate": 0.3,
            "sexual": 0.2,
        },
        "product_description": {
            "harassment": 0.6,
            "hate": 0.4,
            "sexual": 0.1,  # 商品描述更严格
        },
        "chat_message": {
            "harassment": 0.4,
            "hate": 0.2,
            "sexual": 0.15,
        },
    }
    return thresholds.get(scenario, thresholds["chat_message"])
```

### 3. 日志与审计
- 记录所有审核结果
- 保留违规内容的样本
- 定期审查审核准确率

### 4. 用户反馈
- 对误报提供申诉机制
- 收集用户反馈改进审核策略
- 保持透明，告知用户审核规则

### 5. 注意事项
- Moderation API 不是万能的，需要配合业务规则
- 定期更新违规词库和审核策略
- 注意不同语言文化的差异
- 平衡安全性和用户体验