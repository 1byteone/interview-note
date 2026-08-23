# 推荐资源

> 收录 Git、Conda、Jupyter 三大工具的高质量学习资源，涵盖官方文档、书籍、视频和实战项目。

---

## Git

### 官方文档

- [Git 官方文档](https://git-scm.com/doc) — 最权威的参考，包含 Pro Git 电子书
- [Git 参考手册](https://git-scm.com/docs) — 所有命令的详细说明
- [Git LFS 文档](https://git-lfs.com/) — 大文件存储官方指南

### 书籍

- **Pro Git**（免费）— Scott Chacon 著，Git 公认最佳入门书籍
  - 在线阅读：https://git-scm.com/book/zh/v2
  - 涵盖：基础操作、分支、服务器、分布式工作流、Git 内部原理

### 在线教程

- [Learn Git Branching](https://learngitbranching.js.org/) — 交互式 Git 学习，可视化分支操作
- [GitHub Skills](https://skills.github.com/) — GitHub 官方互动课程
- [Oh My Git!](https://ohmygit.org/) — 开源 Git 游戏化学习

### 可视化工具

- [Sourcetree](https://www.sourcetreeapp.com/) — 免费 Git GUI（Atlassian）
- [GitHub Desktop](https://desktop.github.com/) — GitHub 官方桌面客户端
- [GitKraken](https://www.gitkraken.com/) — 功能强大的付费 GUI

---

## Conda

### 官方文档

- [Conda 官方文档](https://docs.conda.io/) — 环境管理、包管理完整指南
- [Miniconda 下载](https://docs.conda.io/en/latest/miniconda.html) — 轻量级安装包
- [Conda-Forge 文档](https://conda-forge.org/docs/) — 社区频道使用指南

### 教程与指南

- [Conda 入门指南](https://docs.conda.io/projects/conda/en/latest/user-guide/getting-started.html) — 官方入门
- [Managing environments](https://docs.conda.io/projects/conda/en/latest/user-guide/tasks/manage-environments.html) — 环境管理官方指南
- [conda-lock 文档](https://github.com/conda/conda-lock) — 环境锁定工具

### 实用工具

- [mamba](https://mamba.readthedocs.io/) — 替代 Conda 包管理器，安装速度更快
- [boa](https://boa-build.readthedocs.io/) — 快速 Conda 包构建工具
- [conda-pack](https://conda.github.io/conda-pack/) — 打包环境用于离线部署

---

## Jupyter

### 官方文档

- [JupyterLab 文档](https://jupyterlab.readthedocs.io/) — JupyterLab 用户指南
- [Jupyter Notebook 文档](https://jupyter-notebook.readthedocs.io/) — 传统 Notebook 文档
- [nbconvert 文档](https://nbconvert.readthedocs.io/) — 格式转换工具
- [JupyterHub 文档](https://jupyterhub.readthedocs.io/) — 多用户平台部署指南

### 扩展生态

- [ipywidgets](https://ipywidgets.readthedocs.io/) — 交互式控件
- [Voilà](https://voila.readthedocs.io/) — 将 Notebook 转化为 Web 应用
- [Papermill](https://papermill.readthedocs.io/) — 参数化 Notebook 执行
- [nbdime](https://nbdime.readthedocs.io/) — Notebook diff 和 merge 工具
- [jupyter-dash](https://github.com/plotly/jupyter-dash) — Dash 应用嵌入 Notebook

### 学习资源

- [JupyterLab 官方教程](https://jupyterlab.readthedocs.io/en/stable/getting_started/overview.html)
- [Jupyter Notebook 最佳实践](https://docs.quantifiedcode.com/python-anti-patterns/jupyter_notebook/) — 代码质量指南
- [DataCamp 的 Jupyter 教程](https://www.datacamp.com/tutorial/tutorial-jupyter-notebook)

---

## 综合项目

- [GitHub 上的 Awesome Jupyter](https://github.com/markusschanta/awesome-jupyter) — Jupyter 资源大全
- [GitHub 上的 Awesome Git](https://github.com/dictcp/awesome-git) — Git 资源大全
- [Kaggle Notebooks](https://www.kaggle.com/notebooks) — 大量可参考的 Jupyter 数据分析案例

---

## 推荐学习路径

### 1-2 天快速入门

1. Git：完成 [Learn Git Branching](https://learngitbranching.js.org/) 前 50% 关卡
2. Conda：阅读 [Conda 官方入门指南](https://docs.conda.io/projects/conda/en/latest/user-guide/getting-started.html)
3. Jupyter：在本地启动 JupyterLab，跟着教程完成一个数据分析案例

### 1-2 周系统学习

1. 阅读《Pro Git》前 3 章，掌握 Git 核心概念
2. 为个人项目搭建 Conda 环境，实践 `environment.yml` 管理
3. 用 Jupyter 完成一个完整的数据分析项目，练习 Magic 命令和可视化

### 1-2 月进阶精通

1. 深入理解 Git 内部原理（Pro Git 第 10 章）
2. 搭建 JupyterHub 多用户环境
3. 将 Git Hooks 和 CI/CD 集成到项目工作流中
4. 使用 Papermill 实现 Notebook 自动化