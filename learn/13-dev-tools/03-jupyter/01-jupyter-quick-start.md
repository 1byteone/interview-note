# Jupyter 基础与数据分析

> 面向 AI 开发者的 Jupyter 快速上手，用交互式编程探索数据。

---

## 1. Jupyter Notebook 与 JupyterLab

### 是什么？

Jupyter 是一个交互式计算环境，让你在浏览器中编写代码、运行、查看结果，并穿插 Markdown 说明文字。它特别适合：

- **数据探索与可视化**：快速分析数据，即时查看图表
- **模型原型开发**：逐单元调试，可视化中间结果
- **实验记录**：代码、图表、说明文档一体化
- **教学演示**：交互式编程教学

### Notebook vs Lab

| 特性 | Jupyter Notebook | JupyterLab |
|------|-----------------|------------|
| 界面 | 单文档界面 | 多标签、多面板 |
| 文件管理 | 基本 | 内置文件浏览器 |
| 多 Notebook | 需要多个标签页 | 同一窗口内并排 |
| 终端 | 不支持 | 内置终端 |
| 扩展性 | 有限 | 强，支持插件 |
| 推荐度 | 老项目使用 | **新项目首选** |

**结论**：新项目直接用 JupyterLab，功能更强大。

---

## 2. 安装与启动

```bash
# 通过 Conda 安装（推荐）
conda create -n jupyter python=3.11
conda activate jupyter
conda install -c conda-forge jupyterlab

# 通过 pip 安装
pip install jupyterlab

# 启动 JupyterLab
jupyter lab

# 启动传统 Notebook
jupyter notebook

# 指定端口和 IP
jupyter lab --port=8888 --ip=0.0.0.0
```

启动后，浏览器会自动打开 `http://localhost:8888`。

---

## 3. 基本操作

### 3.1 界面组成

JupyterLab 界面主要分为：

- **左侧面板**：文件浏览器、运行内核列表、Git 等
- **主工作区**：Notebook 编辑器、终端、文本编辑器
- **菜单栏**：文件、编辑、视图、运行、内核、设置等

### 3.2 Cell 类型

| 类型 | 快捷键 | 用途 |
|------|--------|------|
| Code | `Y` | 编写并执行代码 |
| Markdown | `M` | 编写文档、公式、标题 |

### 3.3 核心快捷键

| 快捷键 | 功能 |
|--------|------|
| `Shift + Enter` | 运行当前 cell 并选择下一个 |
| `Ctrl + Enter` | 运行当前 cell |
| `Alt + Enter` | 运行当前 cell 并在下方插入新 cell |
| `A` | 在上方插入 cell |
| `B` | 在下方插入 cell |
| `DD` | 删除当前 cell |
| `X` | 剪切 cell |
| `C` | 复制 cell |
| `V` | 粘贴 cell |
| `Z` | 撤销 |
| `Shift + L` | 显示行号 |
| `Tab` | 代码补全 |
| `Shift + Tab` | 显示函数签名 |

### 3.4 内核管理

```python
# 查看当前内核
import sys
print(sys.executable)

# 重启内核（菜单：Kernel -> Restart Kernel）
# 清空所有输出（菜单：Edit -> Clear All Outputs）
```

### 3.5 Markdown 基础

```markdown
# 一级标题
## 二级标题

**加粗** *斜体* ~~删除线~~

- 无序列表
1. 有序列表

`行内代码` 或 ```代码块```

$$ E = mc^2 $$  （LaTeX 公式）

[链接文字](https://example.com)
![图片描述](image.png)
```

---

## 4. 数据可视化演练

### 4.1 安装依赖

```python
# 在 notebook cell 中安装
!pip install matplotlib seaborn pandas numpy
```

### 4.2 基本图表

```python
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np

# 设置中文显示
plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False

# 生成示例数据
np.random.seed(42)
dates = pd.date_range('2024-01-01', periods=100)
values = np.cumsum(np.random.randn(100)) + 100

# 折线图
plt.figure(figsize=(12, 5))
plt.plot(dates, values, 'b-', linewidth=2)
plt.title('AI 商城商品销量趋势')
plt.xlabel('日期')
plt.ylabel('销量')
plt.grid(True, alpha=0.3)
plt.tight_layout()
plt.show()
```

### 4.3 高级可视化

```python
# 多个子图
fig, axes = plt.subplots(2, 2, figsize=(14, 10))

# 直方图
axes[0, 0].hist(values, bins=20, color='skyblue', edgecolor='black')
axes[0, 0].set_title('销量分布')

# 散点图
x = np.random.randn(100)
y = x * 0.7 + np.random.randn(100) * 0.3
axes[0, 1].scatter(x, y, alpha=0.6, c='coral')
axes[0, 1].set_title('相关性散点图')

# 箱线图
data = [np.random.randn(50) for _ in range(3)]
axes[1, 0].boxplot(data, labels=['A类', 'B类', 'C类'])
axes[1, 0].set_title('各类目销量分布')

# 热力图
corr_matrix = pd.DataFrame(np.random.randn(100, 5),
                           columns=['价格', '销量', '评分', '库存', '点击']).corr()
sns.heatmap(corr_matrix, annot=True, cmap='coolwarm', ax=axes[1, 1])
axes[1, 1].set_title('特征相关性热力图')

plt.tight_layout()
plt.show()
```

---

## 5. 实战：用 Jupyter 分析 AI 商城数据

### 场景

假设我们有一个 CSV 文件 `mall_sales.csv`，包含商城的销售数据。

```python
# 1. 加载数据
import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('mall_sales.csv')
df.head()
```

```python
# 2. 数据概览
print(f"数据维度: {df.shape}")
print(f"列名: {df.columns.tolist()}")
print(f"数据类型:\n{df.dtypes}")
print(f"缺失值:\n{df.isnull().sum()}")
print(f"基本统计:\n{df.describe()}")
```

```python
# 3. 按类目分析销售额
category_sales = df.groupby('category')['sales_amount'].sum().sort_values(ascending=False)

plt.figure(figsize=(10, 6))
category_sales.plot(kind='bar', color='steelblue')
plt.title('各品类销售额排行')
plt.xlabel('品类')
plt.ylabel('销售额 (元)')
plt.xticks(rotation=45)
plt.tight_layout()
plt.show()
```

```python
# 4. 时间序列分析
df['date'] = pd.to_datetime(df['date'])
daily_sales = df.groupby('date')['sales_amount'].sum()

plt.figure(figsize=(14, 5))
daily_sales.plot()
plt.title('日均销售额趋势')
plt.xlabel('日期')
plt.ylabel('销售额')
plt.grid(True, alpha=0.3)
plt.show()
```

```python
# 5. 用户行为分析
# 复购率
user_order_counts = df['user_id'].value_counts()
repurchase_rate = (user_order_counts > 1).mean()
print(f"用户复购率: {repurchase_rate:.1%}")

# 客单价分布
plt.figure(figsize=(10, 5))
df['order_amount'].hist(bins=50, color='lightgreen', edgecolor='black')
plt.title('客单价分布')
plt.xlabel('订单金额')
plt.ylabel('订单数')
plt.show()
```

---

## 6. 保存与导出

```python
# 在 UI 中：File -> Save Notebook (Ctrl+S)

# 命令行导出
jupyter nbconvert --to html notebook.ipynb
jupyter nbconvert --to markdown notebook.ipynb
jupyter nbconvert --to pdf notebook.ipynb  # 需要 LaTeX
jupyter nbconvert --to script notebook.ipynb  # 导出纯 Python 脚本
```

---

## 下一步

掌握基础操作后，进入 [Jupyter 进阶](02-jupyter-advanced.md) 学习 Magic 命令、扩展与生产环境部署。