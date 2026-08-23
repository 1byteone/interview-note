# Conda 进阶与生产实践

> 深入掌握 Conda 的高级特性，确保环境可复现、可迁移、可部署。

---

## 1. environment.yml 完整配置

### 基础结构

```yaml
name: mall-ai
channels:
  - pytorch
  - conda-forge
  - defaults
dependencies:
  - python=3.11
  - numpy=1.26.0
  - pandas=2.1.0
  - pip
  - pip:
    - transformers>=4.35.0
    - sentence-transformers>=2.2.0
    - faiss-cpu==1.7.4
```

### 字段说明

| 字段 | 说明 | 示例 |
|------|------|------|
| `name` | 环境名称 | `mall-ai` |
| `channels` | 包来源优先级 | `- pytorch` 优先于 `- conda-forge` |
| `dependencies` | 依赖列表，含 pip 包 | `- pip: - transformers` |
| `prefix` | 指定安装路径 | `/opt/conda/envs/mall-ai` |

### 版本约束语法

```yaml
dependencies:
  - python=3.11          # 精确版本
  - numpy>=1.24,<2.0     # 版本范围
  - pandas>=2.1          # 最低版本
  - scikit-learn         # 任意版本
```

### 跨平台兼容的 environment.yml

```yaml
name: mall-ai
channels:
  - conda-forge
  - defaults
dependencies:
  - python=3.11
  - numpy
  - pandas
  - scikit-learn
  - pip
  - pip:
    - transformers
    - sentence-transformers
variables:
  PYTHONPATH: ${PYTHONPATH:-}:/app/src
  LOG_LEVEL: INFO
```

`--from-history` 导出方式不会包含依赖的依赖，跨平台兼容性更好：

```bash
conda env export --from-history > environment.yml
```

---

## 2. Conda 频道管理

### 频道优先级

```bash
# 查看当前频道配置
conda config --show channels

# 添加频道（优先级从高到低）
conda config --add channels conda-forge
conda config --add channels pytorch

# 设置频道优先级严格模式
conda config --set channel_priority strict

# 删除频道
conda config --remove channels pytorch

# 通过环境变量设置
export CONDA_CHANNELS="conda-forge,pytorch,defaults"
```

### 主要频道一览

| 频道 | 维护方 | 特点 | 适用场景 |
|------|--------|------|----------|
| `defaults` | Anaconda | 官方稳定，更新慢 | 基础包 |
| `conda-forge` | 社区 | 包最全，更新快 | 大多数场景 |
| `pytorch` | PyTorch 团队 | 含 GPU 版本 | 深度学习 |
| `nvidia` | NVIDIA | CUDA 相关包 | GPU 加速 |
| `bioconda` | 生物信息社区 | 生物信息学工具 | 生信分析 |

### 配置国内镜像加速

```bash
# 清华源
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/main/
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/free/
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/cloud/conda-forge/

# 中科大源
conda config --add channels https://mirrors.ustc.edu.cn/anaconda/pkgs/main/
conda config --add channels https://mirrors.ustc.edu.cn/anaconda/pkgs/free/
```

---

## 3. 环境复用与迁移

### 场景一：团队标准化

团队统一使用同一个 environment.yml，确保所有成员开发环境一致。

```bash
# 项目根目录放置 environment.yml
# 新成员克隆项目后
git clone https://github.com/xxx/mall-ai.git
cd mall-ai
conda env create -f environment.yml
conda activate mall-ai

# 项目依赖更新后
conda env update -f environment.yml
```

### 场景二：跨机器迁移

```bash
# 源机器导出精确环境
conda env export > exact-environment.yml

# 目标机器上重建
conda env create -f exact-environment.yml

# 如果目标机器架构不同（如 x86 -> ARM），使用 --from-history
conda env export --from-history > portable-environment.yml
```

### 场景三：不同环境对比

```bash
# 比较两个环境的包差异
conda list -n mall-ai > env1.txt
conda list -n mall-ai-dev > env2.txt
diff env1.txt env2.txt
```

---

## 4. 与 Docker 结合使用

### 方式一：基于 Conda 环境的 Dockerfile

```dockerfile
FROM continuumio/miniconda3:23.10.0

# 复制环境配置
COPY environment.yml /app/environment.yml

# 创建 Conda 环境
RUN conda env create -f /app/environment.yml

# 激活环境的快捷方式
ENV PATH /opt/conda/envs/mall-ai/bin:$PATH

# 复制项目代码
COPY src/ /app/src/
WORKDIR /app

CMD ["python", "src/main.py"]
```

构建并运行：

```bash
docker build -t mall-ai:latest .
docker run -it mall-ai:latest
```

### 方式二：多阶段构建（减小镜像体积）

```dockerfile
# 第一阶段：构建环境
FROM continuumio/miniconda3:23.10.0 AS builder
COPY environment.yml /tmp/environment.yml
RUN conda env create -f /tmp/environment.yml && \
    conda clean -afy

# 第二阶段：仅复制必要文件
FROM debian:bullseye-slim
COPY --from=builder /opt/conda/envs/mall-ai /opt/conda/envs/mall-ai
ENV PATH /opt/conda/envs/mall-ai/bin:$PATH
COPY src/ /app/
WORKDIR /app
CMD ["python", "main.py"]
```

### 方式三：Conda + Poetry 混合模式

```yaml
# environment.yml - 仅管理 Python 版本和系统级依赖
name: mall-ai
channels:
  - conda-forge
  - defaults
dependencies:
  - python=3.11
  - pip
  - pip:
    - poetry
```

```bash
conda env create -f environment.yml
conda activate mall-ai
poetry install
```

---

## 5. 生产环境最佳实践

### 5.1 锁定版本

```bash
# 使用 conda-lock 生成精确锁定文件
conda install -c conda-forge conda-lock
conda lock -f environment.yml -p linux-64
conda lock -f environment.yml -p win-64
```

### 5.2 定期清理

```bash
# 清理未使用的包和缓存
conda clean --all

# 查看缓存大小
du -sh ~/.conda/pkgs/

# 删除不用的环境
conda env remove -n old-project
```

### 5.3 CI/CD 集成

```yaml
# GitHub Actions 示例
- name: Setup Conda
  uses: conda-incubator/setup-miniconda@v2
  with:
    environment-file: environment.yml
    auto-activate-base: false
    activate-environment: mall-ai

- name: Run tests
  shell: bash -l {0}
  run: |
    conda activate mall-ai
    pytest tests/
```

---

## 6. 常见问题

### Q: Conda 环境目录占用空间太大？

```bash
# 查看环境大小
du -sh ~/miniconda3/envs/mall-ai/

# 清理缓存
conda clean --all -y

# 使用 --no-default-packages 创建精简环境
conda create -n minimal python=3.11 --no-default-packages
```

### Q: 如何指定 Conda 环境中 Python 的版本？

创建时指定：`conda create -n env-name python=3.10`。已有环境可通过重建来升级。

### Q: Conda 和 venv 可以共存吗？

可以，但不推荐。Conda 已经包含了 venv 的所有功能，且更强大。保持一致使用 Conda 即可。