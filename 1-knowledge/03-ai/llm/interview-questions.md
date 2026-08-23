# LLM (大语言模型) 面试题大全

## 📚 知识体系

```
LLM 基础
├── Transformer 架构
│   ├── Self-Attention
│   ├── Multi-Head Attention
│   ├── Position Encoding
│   └── Feed-Forward Network
├── 预训练
│   ├── MLM（Masked Language Model）
│   ├── CLM（Causal Language Model）
│   └── 训练数据 / Tokenizer
├── 微调
│   ├── Fine-tuning（全量微调）
│   ├── LoRA（低秩适配）
│   ├── QLoRA（量化 LoRA）
│   └── P-Tuning / Prefix Tuning
├── 推理
│   ├── Temperature / Top-K / Top-P
│   ├── KV Cache
│   ├── Speculative Decoding
│   └── 量化（INT4/INT8/FP16）
└── 评估
    ├── Perplexity
    ├── BLEU / ROUGE
    ├── HumanEval
    └── MMLU / C-Eval
```

---

## 🎯 Level 1：基础题

### 1. Transformer 的 Self-Attention 是什么？
**答案**：
Self-Attention 让模型在处理每个位置时，**关注输入序列中所有位置**、计算相关性权重。

**公式**：`Attention(Q,K,V) = softmax(QK^T / √d_k) * V`

**Q/K/V 的含义**：
- **Q（Query）**：当前查询
- **K（Key）**：被匹配的键
- **V（Value）**：提取的值

**核心优势**：相比 RNN，Self-Attention 可以并行计算，捕捉长距离依赖。

### 2. 什么是 Prompt Engineering？
**答案**：
Prompt Engineering 是设计输入提示词，引导 LLM 输出期望结果的技术。

**常用技巧**：
| 技巧 | 说明 | 示例 |
|------|------|------|
| 角色设定 | 给模型一个角色 | "你是一个 Java 专家" |
| 思维链 (CoT) | 引导分步推理 | "Let's think step by step" |
| Few-shot | 给几个示例 | 给出问答示例 |
| 格式化输出 | 指定输出格式 | "返回 JSON 格式" |
| 系统提示 | 系统层约束 | 设定行为准则 |

---

## 🎯 Level 2：进阶题

### 3. LoRA 微调的原理？
**答案**：
LoRA（Low-Rank Adaptation）通过**低秩矩阵**近似权重更新，冻结原始权重，只训练少量参数。

**原理**：`W_new = W_original + AB`（A 和 B 是低秩矩阵，r << d）

**优势**：
- 显存占用大幅降低（可消费级 GPU 微调）
- 训练参数量只需 0.1%-1%
- 切换任务只需切换 LoRA 权重

### 4. KV Cache 是什么？
**答案**：
KV Cache 是 LLM 推理时缓存**历史 token 的 Key 和 Value**，避免每次生成重新计算。

**原理**：
```text
生成第 1 个 token：计算所有 token 的 K、V（缓存）
生成第 2 个 token：只计算新 token 的 K、V + 复用缓存
生成第 3 个 token：只计算新 token 的 K、V + 复用缓存
...
```

**优化效果**：推理速度提升 10-100 倍（长序列场景）

---

## 🎯 Level 3：高级题

### 5. 大模型推理优化有哪些手段？
**答案**：

| 技术 | 原理 | 效果 |
|------|------|------|
| **量化** | INT4/INT8 降低精度 | 显存减半，速度提升 |
| **KV Cache** | 缓存历史状态 | 避免重复计算 |
| **Speculative Decoding** | 小模型先猜，大模型验证 | 提速 2-3x |
| **Flash Attention** | 分块计算，减少显存读写 | 显存降低，速度提升 |
| **Paged Attention** | 分页管理 KV Cache | 减少碎片 |
| **连续批处理** | 等待时插入新请求 | 吞吐提升 |

---

## 📖 学习资源

### 推荐项目
- [Hugging Face Transformers](https://github.com/huggingface/transformers)
- [vLLM](https://github.com/vllm-project/vllm) - 推理加速
- [LLaMA-Factory](https://github.com/hiyouga/LLaMA-Factory) - 微调工具

### 最佳实践
1. 推理时合理设置 Temperature（0.1 精确，0.7 创造）
2. 复杂任务用 CoT + Few-shot
3. 微调优先 LoRA（低成本、易切换）