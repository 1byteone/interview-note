# 开发工具在 AI 商城项目中的集成

> 将 Git、Conda、Jupyter 三大工具融入 AI 智能商城开发工作流，实现从实验到上线的全链路工具链覆盖。

---

## 1. 项目背景

AI 智能商城是一个基于 Spring Boot + FastAPI 的混合架构电商平台，核心模块包括：

- **商品管理**：商品 CRUD、库存管理、类目体系
- **用户系统**：注册登录、权限管理、用户画像
- **AI 推荐**：基于协同过滤和 Embedding 的商品推荐
- **AI 搜索**：基于语义理解的智能商品搜索
- **数据分析**：用户行为分析、销售趋势预测

---

## 2. Git 分支策略

### 2.1 采用 GitHub Flow

选择 GitHub Flow 的原因：
- 项目采用持续部署模式
- 团队规模较小（5-8 人）
- 需要快速迭代 AI 模型

```bash
# 分支命名规范
main                        # 生产分支，始终可部署
feat/ai-search              # 功能开发
feat/recommend-v2           # 功能开发
fix/price-calc-bug          # 缺陷修复
chore/update-deps           # 杂项维护
docs/api-doc-update         # 文档更新
```

### 2.2 工作流示例

```bash
# 1. 从 main 拉取最新代码
git checkout main && git pull

# 2. 创建功能分支
git checkout -b feat/embedding-upgrade

# 3. 分步提交
git add src/ai/embedding.py
git commit -m "feat: upgrade embedding model to text2vec-large-chinese"

git add tests/test_embedding.py
git commit -m "test: add embedding similarity tests"

# 4. 推送并创建 PR
git push origin feat/embedding-upgrade

# 5. PR 审查通过后 Squash Merge
# 远程仓库操作：GitHub 上的 Squash and merge
```

### 2.3 AI 模型文件管理

```yaml
# .gitattributes
*.pkl filter=lfs diff=lfs merge=lfs -text
*.onnx filter=lfs diff=lfs merge=lfs -text
*.bin filter=lfs diff=lfs merge=lfs -text
*.pt filter=lfs diff=lfs merge=lfs -text
```

```bash
# 初始化 Git LFS
git lfs install
git lfs track "*.pkl"
git lfs track "*.onnx"
git add .gitattributes
git commit -m "chore: configure Git LFS for model files"
```

---

## 3. Conda 环境管理

### 3.1 项目环境配置

```yaml
# environment.yml
name: mall-ai
channels:
  - pytorch
  - conda-forge
  - defaults
dependencies:
  - python=3.11
  - fastapi=0.104.0
  - uvicorn=0.24.0
  - sqlalchemy=2.0.23
  - pymysql=1.1.0
  - redis-py=5.0.0
  - pandas=2.1.3
  - numpy=1.26.2
  - scikit-learn=1.3.2
  - matplotlib=3.8.2
  - jupyterlab=4.0.8
  - pytest=7.4.3
  - black=23.11.0
  - pip
  - pip:
    - transformers>=4.35.0
    - sentence-transformers>=2.2.2
    - faiss-cpu==1.7.4
    - pydantic-settings>=2.1.0
```

### 3.2 多环境管理

```bash
# 开发环境
conda create -n mall-ai-dev python=3.11
conda env update -f environment.yml

# 测试环境（精简版，不含 AI 依赖）
conda create -n mall-ai-test python=3.11
conda install -c conda-forge -y fastapi uvicorn pytest

# 生产环境（仅运行时依赖）
conda create -n mall-ai-prod python=3.11
conda install -c conda-forge -y fastapi uvicorn gunicorn
```

### 3.3 Conda 与 Docker 结合

```dockerfile
# Dockerfile — AI 搜索服务
FROM continuumio/miniconda3:23.10.0

WORKDIR /app

# 复制环境配置
COPY environment.yml /app/
RUN conda env create -f /app/environment.yml && \
    conda clean -afy

# 设置环境变量激活环境
ENV PATH /opt/conda/envs/mall-ai/bin:$PATH

# 复制模型文件（通过 Git LFS 下载）
COPY models/ /app/models/

# 复制应用代码
COPY src/ai_search/ /app/src/

# 启动服务
CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

---

## 4. Jupyter 数据分析和实验

### 4.1 数据分析工作流

```python
# notebooks/analytics/sales_analysis.ipynb

# 1. 连接数据库
import pandas as pd
from sqlalchemy import create_engine
import matplotlib.pyplot as plt

# 从环境变量读取数据库配置
import os
DB_URL = os.getenv("DB_URL", "mysql+pymysql://user:pass@localhost/mall")

engine = create_engine(DB_URL)

# 2. 加载数据
query = """
SELECT 
    o.order_id, o.user_id, o.amount, o.created_at,
    oi.product_id, oi.quantity, p.category, p.price
FROM orders o
JOIN order_items oi ON o.order_id = oi.order_id
JOIN products p ON oi.product_id = p.product_id
WHERE o.created_at >= '2024-01-01'
"""
df = pd.read_sql(query, engine)
print(f"加载 {len(df)} 条记录")
```

### 4.2 模型实验

```python
# notebooks/experiments/recommendation_experiment.ipynb

# 1. 加载预处理数据
import joblib
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.metrics import precision_score, recall_score

# 2. 加载用户-商品交互矩阵
interaction_matrix = joblib.load('data/interaction_matrix.pkl')
user_ids = joblib.load('data/user_ids.pkl')
product_ids = joblib.load('data/product_ids.pkl')

# 3. 实验不同的推荐算法
from sklearn.neighbors import NearestNeighbors

# 协同过滤
knn = NearestNeighbors(n_neighbors=20, metric='cosine')
knn.fit(interaction_matrix)

# 评估
def evaluate_recommendation(model, test_data, k=10):
    """评估推荐模型在测试集上的表现。"""
    # ... 评估逻辑
    pass

# 4. 记录实验结果
results = {
    'model': 'KNN-Cosine',
    'precision@10': 0.35,
    'recall@10': 0.28,
    'coverage': 0.72
}
print(f"实验结果: {results}")
```

### 4.3 可视化报告

```python
# notebooks/reports/dashboard.ipynb

# 生成运营日报
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime, timedelta

# 日活用户趋势
plt.figure(figsize=(14, 6))
daily_active = df.groupby(df['date'].dt.date)['user_id'].nunique()
daily_active.plot(kind='line', marker='o', linewidth=2)
plt.title('日活用户趋势 (DAU)')
plt.ylabel('用户数')
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('reports/dau_trend.png', dpi=150)
plt.show()

# 商品类目分布
plt.figure(figsize=(10, 6))
category_revenue = df.groupby('category')['amount'].sum().sort_values(ascending=False)
category_revenue.plot(kind='bar', color='steelblue')
plt.title('各品类销售额')
plt.ylabel('销售额 (元)')
plt.xticks(rotation=45)
plt.tight_layout()
plt.savefig('reports/category_revenue.png', dpi=150)
plt.show()
```

---

## 5. 工具链集成全景图

```
┌─────────────────────────────────────────────────────────────────────┐
│                       开发工具链集成全景图                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Conda] 环境隔离                                                    │
│    └─ mall-ai-dev (开发环境)                                         │
│    └─ mall-ai-test (测试环境)                                         │
│    └─ mall-ai-prod (生产环境)                                         │
│         │                                                            │
│         ▼                                                            │
│  [Git] 版本管理                                                       │
│    └─ main (生产分支) ── 自动部署到生产                                │
│    └─ feat/* (功能分支) ── PR 审查后合并                               │
│    └─ fix/* (修复分支) ── 紧急修复                                    │
│         │                                                            │
│         ▼                                                            │
│  [Jupyter] 数据探索与实验                                             │
│    └─ notebooks/analytics/    数据分析报告                             │
│    └─ notebooks/experiments/  算法实验记录                             │
│    └─ notebooks/reports/      可视化报告（Voilà 部署）                 │
│         │                                                            │
│         ▼                                                            │
│  [CI/CD] GitHub Actions                                               │
│    └─ lint / test / build / deploy                                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. 最佳实践总结

| 工具 | 在 AI 商城中的角色 | 关键操作 |
|------|-------------------|----------|
| Git | 代码版本管理、团队协作 | 功能分支 + PR 审查 + Squash Merge |
| Git LFS | 管理 AI 模型大文件 | 跟踪 `*.pkl`、`*.onnx`、`*.pt` |
| Conda | Python 环境隔离、依赖管理 | `environment.yml` + 不同环境配置 |
| Jupyter | 数据分析、模型实验、报告生成 | 与数据库连接 + 参数化实验 + nbconvert |
| 集成 | 工具链无缝衔接 | Conda 环境 + Jupyter 实验 → Git 提交 → CI/CD 部署 |