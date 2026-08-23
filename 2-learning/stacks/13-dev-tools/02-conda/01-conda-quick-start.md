# Conda 环境管理入门

> 面向 AI 开发者的 Conda 快速上手，解决 Python 环境管理痛点。

---

## 1. 为什么需要 Conda？

在 AI 开发中，你经常面临这样的困境：

- 项目 A 需要 Python 3.9 + TensorFlow 2.10
- 项目 B 需要 Python 3.11 + PyTorch 2.0
- 系统自带的 Python 是 3.8

如果没有环境隔离，这些依赖冲突会让你寸步难行。Conda 正是解决这个问题的工具。

---

## 2. Conda vs pip vs venv

| 特性 | Conda | pip | venv |
|------|-------|-----|------|
| 包管理 | 支持 | 支持 | 不支持 |
| 环境隔离 | 支持 | 不支持 | 支持 |
| 非 Python 依赖 | 支持 | 不支持 | 不支持 |
| 二进制包 | 预编译 | 部分源码编译 | - |
| GPU 支持 | 内置 CUDA 版本 | 需手动处理 | - |
| 适用场景 | 数据科学 / AI | 通用 Python | 轻量 Python |

**核心优势**：Conda 可以管理 Python 版本和非 Python 的 C/C++ 库（如 CUDA、OpenCV），这是 pip + venv 做不到的。

---

## 3. 安装 Conda

### Miniconda（推荐）

轻量级安装，仅包含 Conda 本身和 Python。

```bash
# Windows：下载 Miniconda 安装包
# https://docs.conda.io/en/latest/miniconda.html

# macOS
brew install --cask miniconda

# Linux
wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh
bash Miniconda3-latest-Linux-x86_64.sh
```

### Anaconda

包含 Conda + 150+ 常用数据科学包，适合新手。

---

## 4. 环境管理

### 创建环境

```bash
# 创建名为 mall-ai 的环境，指定 Python 3.11
conda create -n mall-ai python=3.11

# 创建时同时安装依赖
conda create -n mall-ai python=3.11 numpy pandas scikit-learn

# 指定 Python 版本（2.7/3.8/3.9/3.10/3.11/3.12）
conda create -n py39 python=3.9
```

### 激活与退出

```bash
# 激活环境
conda activate mall-ai

# 退出当前环境
conda deactivate

# 查看当前环境名称（在 prompt 中，或使用）
conda info --envs
```

### 查看与管理环境

```bash
# 列出所有环境
conda env list

# 删除环境
conda env remove -n mall-ai

# 复制环境
conda create -n mall-ai-dev --clone mall-ai-outdated

# 重命名（没有直接命令，通过 clone + remove 实现）
conda create -n mall-ai --clone old-name
conda env remove -n old-name
```

---

## 5. 包管理

### 安装包

```bash
# 从 Conda 默认频道安装
conda install numpy pandas

# 指定版本
conda install numpy=1.24.0

# 从 Conda-Forge 频道安装
conda install -c conda-forge jupyterlab

# 安装多个包
conda install numpy pandas matplotlib scikit-learn

# 与 pip 混用（先用 conda，再用 pip）
conda install pytorch torchvision -c pytorch
pip install transformers datasets
```

### 更新与删除

```bash
# 更新指定包
conda update numpy

# 更新所有包
conda update --all

# 删除包
conda remove numpy

# 查看已安装包
conda list
conda list | grep numpy  # 搜索特定包
```

---

## 6. 环境导出与导入

```bash
# 导出环境配置（推荐给团队分享）
conda env export > environment.yml

# 包含精确版本号（更可靠）
conda env export > environment.yml

# 仅导出显式安装的包（跨平台兼容性更好）
conda env export --from-history > environment.yml

# 从 environment.yml 创建环境
conda env create -f environment.yml

# 更新现有环境
conda env update -f environment.yml
```

---

## 7. 实战：为 AI 商城项目创建隔离环境

```bash
# 1. 创建项目环境
conda create -n mall-ai python=3.11 -y
conda activate mall-ai

# 2. 安装后端依赖
conda install -c conda-forge -y \
  fastapi uvicorn \
  sqlalchemy pymysql \
  redis-py

# 3. 安装 AI 依赖
conda install -c pytorch -y pytorch torchvision
pip install transformers sentence-transformers faiss-cpu

# 4. 安装开发工具
conda install -c conda-forge -y jupyterlab black pytest

# 5. 验证安装
python -c "import torch; print(torch.__version__)"
python -c "import fastapi; print(fastapi.__version__)"

# 6. 导出环境配置
conda env export > environment.yml

# 7. 退出环境
conda deactivate
```

---

## 8. 常见问题

### Q: 环境激活后提示符没有变化？

Windows 上可能需要在 PowerShell 中先运行 `conda init powershell`。

### Q: Conda 安装太慢怎么办？

```bash
# 配置国内镜像源（以清华源为例）
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/main/
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/free/
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/cloud/conda-forge/
conda config --set show_channel_urls yes
```

### Q: Conda 和 pip 混用有什么注意事项？

- 先 conda install，再用 pip install
- 不要在一个环境中同时用 conda 和 pip 安装同一个包
- 导出环境时，Conda 会自动记录 pip 安装的包

---

## 下一步

掌握基础操作后，进入 [Conda 进阶](02-conda-advanced.md) 学习 environment.yml 完整配置与生产环境管理。