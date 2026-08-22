# Jupyter 进阶与生产环境

> 深入掌握 Jupyter 的高级特性，从实验工具走向生产级数据分析平台。

---

## 1. Magic 命令

Magic 命令是 Jupyter 最强大的功能之一，以 `%` 开头，分为行 Magic（`%`）和 Cell Magic（`%%`）。

### 1.1 性能分析

```python
# 计时：执行单条语句
%timeit sum(range(1000000))

# 计时：执行整个 cell
%%timeit
total = 0
for i in range(1000000):
    total += i

# 分析代码性能瓶颈
%prun -s cumulative my_function()

# 内存使用分析
%load_ext memory_profiler
%memit my_function()
```

### 1.2 调试

```python
# 进入交互式调试器
%debug
# 在异常发生后执行，进入 pdb 调试界面

# 在 cell 中设置断点
%%debug
x = 1
y = 0
z = x / y  # 触发异常，进入调试

# 显示异常信息
%pdb on   # 自动在异常时进入调试器
```

### 1.3 文件与系统操作

```python
# 执行 shell 命令
!ls -la
!pwd
!pip list | grep torch

# 将 shell 输出赋给变量
files = !ls *.csv
print(files)

# 运行另一个 Python 脚本
%run data_preprocessing.py

# 加载外部 Python 文件内容
%load my_module.py
```

### 1.4 其他实用 Magic

```python
# 查看所有 Magic 命令
%lsmagic

# 显示当前变量的值（自动补全）
%who
%who_ls
%whos

# 重置命名空间
%reset -f

# 在 notebook 中渲染 HTML
%%html
<h1 style="color: blue;">Hello World</h1>

# 在 notebook 中渲染 LaTeX
%%latex
$$ \sum_{i=1}^{n} i = \frac{n(n+1)}{2} $$

# 快速查看 Matplotlib 配置
%matplotlib --list
%matplotlib inline  # 默认，图形嵌入 notebook
%matplotlib widget  # 交互式图形
```

---

## 2. 扩展

### 2.1 ipywidgets（交互式控件）

```python
import ipywidgets as widgets
from IPython.display import display
import matplotlib.pyplot as plt
import numpy as np

# 创建滑块
slider = widgets.FloatSlider(
    value=0.5,
    min=0,
    max=1.0,
    step=0.01,
    description='频率:',
    continuous_update=False
)

# 创建交互式绘图
@widgets.interact
def plot_sine(amplitude=(0.1, 2.0, 0.1), frequency=(0.1, 5.0, 0.1)):
    x = np.linspace(0, 4 * np.pi, 1000)
    y = amplitude * np.sin(frequency * x)
    plt.figure(figsize=(10, 4))
    plt.plot(x, y)
    plt.ylim(-2.5, 2.5)
    plt.grid(True, alpha=0.3)
    plt.show()

# 下拉选择
dropdown = widgets.Dropdown(
    options=['线性回归', '决策树', '随机森林', 'XGBoost'],
    value='随机森林',
    description='模型选择:'
)
display(dropdown)
```

### 2.2 jupyter-dash（Dash 应用嵌入）

```python
from jupyter_dash import JupyterDash
from dash import dcc, html
import plotly.express as px

# 创建 Dash 应用
app = JupyterDash(__name__)

df = px.data.iris()

app.layout = html.Div([
    html.H1("AI 商城数据分析仪表盘"),
    dcc.Dropdown(
        id='dropdown',
        options=[{'label': c, 'value': c} for c in df.columns],
        value='sepal_length'
    ),
    dcc.Graph(id='graph')
])

@app.callback(
    dash.dependencies.Output('graph', 'figure'),
    [dash.dependencies.Input('dropdown', 'value')]
)
def update_graph(column):
    fig = px.histogram(df, x=column)
    return fig

# 在 notebook 中运行
app.run_server(mode='inline')
```

### 2.3 nbconvert（自动化导出）

```bash
# 导出为不同格式
jupyter nbconvert --to html report.ipynb
jupyter nbconvert --to pdf report.ipynb
jupyter nbconvert --to markdown report.ipynb
jupyter nbconvert --to slides report.ipynb  # 生成演示文稿
jupyter nbconvert --to script report.ipynb  # 提取 Python 代码

# 不执行代码导出
jupyter nbconvert --to html --no-input report.ipynb

# 通过模板自定义
jupyter nbconvert --to html --template classic report.ipynb
```

---

## 3. 与 Git 的集成

### 3.1 Notebook Diff 的挑战

Jupyter Notebook 存储为 JSON 格式，包含代码、输出、元数据。直接进行 Git diff 时，输出内容（尤其是 Base64 编码的图片）会导致巨大的 diff。

### 3.2 解决方案：nbdime

```bash
# 安装 nbdime
pip install nbdime

# 配置 Git 使用 nbdime 作为 diff 和 merge 工具
nbdime config-git --enable

# 手动配置（在 .gitconfig 中）
[diff "jupyter"]
    textconv = nbdime
    cachetextconv = true

# 使用 nbdime 查看 diff
nbdiff notebook_before.ipynb notebook_after.ipynb

# 使用 nbdime 解决合并冲突
nbdime notebook.ipynb
```

### 3.3 清理 Notebook 输出

```bash
# 提交前清理输出，减小文件大小
jupyter nbconvert --ClearOutputPreprocessor.enabled=True --inplace notebook.ipynb

# 使用 pre-commit hook 自动清理
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/kynan/nbstripout
    rev: 0.6.1
    hooks:
      - id: nbstripout
```

### 3.4 使用 pre-commit 自动清理

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/kynan/nbstripout
    rev: 0.7.0
    hooks:
      - id: nbstripout
        args:
          - --extra-keys
          - "metadata.kernelspec metadata.language_info"
```

---

## 4. 生产环境

### 4.1 JupyterHub（多用户平台）

JupyterHub 为团队提供共享的 Jupyter 环境，每个用户有独立的工作区和内核。

```bash
# 安装
pip install jupyterhub

# 使用 Docker 部署（推荐）
docker run -d \
  --name jupyterhub \
  -p 8000:8000 \
  -v /data/jupyterhub:/srv/jupyterhub \
  jupyterhub/jupyterhub

# 配置 jupyterhub_config.py
c.JupyterHub.spawner_class = 'dockerspawner.DockerSpawner'
c.JupyterHub.authenticator_class = 'jupyterhub.auth.DummyAuthenticator'
```

### 4.2 Voilà（仪表盘部署）

Voilà 将 Jupyter Notebook 转换为独立的 Web 应用，隐藏代码，仅显示交互式输出。

```bash
# 安装
pip install voila

# 启动 Voilà 服务
voila notebook.ipynb --port=8866

# 指定模板
voila notebook.ipynb --template=gridstack

# 生产部署
voila --port=8866 --no-browser \
  --enable_nbextensions=True \
  --VoilaConfiguration.file_whitelist=".*\.ipynb$" \
  notebooks/
```

### 4.3 Papermill（参数化执行）

Papermill 支持参数化执行 Notebook，适合批量调度和自动化。

```python
# 在 notebook 中标记参数 cell
# parameters cell（第一个 cell 加上 tags）
params = {
    "data_path": "data/sales.csv",
    "model_type": "xgboost",
    "output_dir": "results/"
}
```

```bash
# 命令行执行（传入不同参数）
papermill report.ipynb output_01.ipynb \
  -p data_path data/sales_jan.csv \
  -p model_type xgboost

papermill report.ipynb output_02.ipynb \
  -p data_path data/sales_feb.csv \
  -p model_type lightgbm

# 批量执行
for month in jan feb mar; do
  papermill report.ipynb "output_${month}.ipynb" \
    -p data_path "data/sales_${month}.csv"
done
```

---

## 5. Notebook 代码质量最佳实践

### 5.1 结构规范

```python
# 1. 导入：所有 import 放在第一个 cell
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# 2. 配置：全局参数集中设置
pd.set_option('display.max_columns', 50)
plt.rcParams['figure.figsize'] = (12, 5)

# 3. 函数定义：封装可重用逻辑
def load_and_clean_data(path):
    """加载并清洗数据，返回 DataFrame。"""
    df = pd.read_csv(path)
    df.dropna(subset=['price'], inplace=True)
    return df

# 4. 按逻辑顺序执行（不要乱序执行 cell）
```

### 5.2 避免常见陷阱

| 陷阱 | 说明 | 建议 |
|------|------|------|
| 乱序执行 | Cell 执行顺序 != 显示顺序 | 重启内核后从上到下执行 |
| 全局状态污染 | 多个 cell 依赖隐藏变量 | 用函数封装，显式传递参数 |
| 输出过大 | 打印大量数据导致文件臃肿 | 清理输出后再提交 |
| 硬编码路径 | 路径写死，难以复用 | 使用参数化或配置文件 |
| 缺少说明 | 只有代码没有文档 | 每个重要步骤写 Markdown |

### 5.3 模板示例

```markdown
# 项目名称
## 目的
本次分析的目标是 ...

## 数据来源
- 数据路径：data/sales.csv
- 时间范围：2024-01 至 2024-06

## 依赖
需要 scikit-learn >= 1.3.0
```

```python
# 1. 导入
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split

# 2. 加载数据
df = pd.read_csv('data/sales.csv')
print(f"数据维度: {df.shape}")

# 3. 数据清洗
def clean_data(df):
    df = df.drop_duplicates()
    df = df.fillna({'price': df['price'].median()})
    return df

df = clean_data(df)

# 4. 分析
# ...
```

---

## 6. 进阶资源

- [JupyterLab 官方文档](https://jupyterlab.readthedocs.io/)
- [nbconvert 文档](https://nbconvert.readthedocs.io/)
- [JupyterHub 部署指南](https://jupyterhub.readthedocs.io/)
- [Voilà 文档](https://voila.readthedocs.io/)
- [Papermill 文档](https://papermill.readthedocs.io/)