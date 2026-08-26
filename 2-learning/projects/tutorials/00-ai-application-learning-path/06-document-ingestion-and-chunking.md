# 农业文档摄入与分块

> 来源：`第 5 章 检索增强生成.docx` 和 `AI_EXAM/docs/rag_intro.txt`。

## 学习目标

建立从 PDF、DOCX、TXT 到可追溯知识片段的最小流水线。

## 摄入流水线

```text
解析 → 清洗 → 结构识别 → 分块 → 元数据 → content hash → 索引
```

每个 chunk 至少保留 `source`、`document_id`、`page`、`heading`、`version`、`content_hash` 和 `evidence_level`。页眉页脚、重复空白、断行和 OCR 噪声要在入库前处理。

## 分块参数

`512/64`、`1000/200` 都只能作为实验起点，不能当作农业文档通用最佳值。按标题、表格、段落和句子边界评估；使用验证集比较召回率、引用完整性和上下文长度。

## 去重与版本

对规范化文本计算 hash。相同内容不重复索引；内容变化生成新版本，旧版本按业务策略保留或下线。摄入任务应记录 `pending/running/succeeded/failed`，失败可重试且不得产生重复 chunk。

## 示例数据

`rag_intro.txt` 适合作为基础测试语料，包含 IPM、轮作、黄板、生物防治、合理密植和安全间隔期等主题。高风险用药建议必须绑定来源和适用条件。

## 验收

随机抽样检查页码、标题和来源；验证 hash 去重；用 10 条已知问题检查正确 chunk 是否可召回；解析失败时保留错误原因和原始文档标识。

## 来源

- `AI_EXAM/docs/第 5 章 检索增强生成.docx`
- `AI_EXAM/docs/rag_intro.txt`
