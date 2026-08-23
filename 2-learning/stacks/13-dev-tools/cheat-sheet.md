# 开发工具速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| Git 分支 | 轻量指针指向提交，创建/切换开销极小 | 分支太多没清理，长期不合并产生大量冲突 |
| 工作区/暂存区/版本库 | 修改文件 → git add 到暂存区 → git commit 到版本库 | 忘记 add 就 commit 会漏掉改动；--cached 与 -- 含义易混 |
| rebase vs merge | merge 保留历史分叉+合并提交；rebase 线性化历史(重放) | 已 push 的分支别 rebase(会遗留垃圾提交/影响协作者) |
| Git Flow | main/master + develop + feature/bugfix/hotfix/release 分支模型 | 团队小/迭代快时 Git Flow 流程重，用 GitHub Flow(PR 为主) 更合适 |
| 子模块 (submodule) | 嵌套 git 仓库，引用特定提交 | 子模块更新后主仓库失同步，勿忘 git submodule update |
| 暂存 (stash) | 暂存未提交的改动，切换分支后恢复 | stash pop 冲突解决后要尽快清理，别堆一堆 stash |
| Conda | Python 环境管理 + 包管理，可切换 Python 版本 | 混用 pip 和 conda 装包可能产生依赖冲突 |
| Jupyter | 交互式笔记本，逐格执行，可视化输出 | 单元格有状态依赖，顺序执行错乱结果不可复现 |
| 魔法命令 (Magic) | % 行魔法 / %% 单元格魔法，增强笔记本功能 | 魔法命令只在 Jupyter 内可用，脚本中写 % 会报错 |
| 代码格式化 | Black(默认) / Ruff(快) / Prettier，统一代码风格 | 格式化工具配置不统一，团队风格混乱 |

## 🔧 常用命令/API

```bash
# Git 常用命令速查
git init                                        # 初始化仓库
git clone <url>                                 # 克隆远程仓库
git add -A                                      # 暂存所有改动
git commit -m "feat: add login api"             # 提交
git push origin main                            # 推送
git pull --rebase                               # 拉取并变基(推荐，保持线性历史)
git status -sb                                  # 查看状态(简洁)
git diff                                        # 未暂存改动
git diff --cached                               # 已暂存改动

# 分支操作
git checkout -b feature/xxx                     # 新建并切换分支
git switch -c feature/xxx                       # (新命令同样效果)
git merge feature/xxx                           # 合并分支
git rebase main                                 # 变基重放
git branch -a                                   # 查看所有分支

# 回滚/撤销
git reset --hard HEAD~1                         # 丢弃最近提交(本地，别对已提交远程的用)
git revert <commit-hash>                        # 反做某提交(安全的远程回滚)
git checkout -- <file>                          # 丢弃工作区改动
git restore <file>                              # 新语法恢复文件
git rm --cached <file>                          # 停止跟踪但保留文件

# 查看/搜索
git log --oneline --graph --decorate            # 图形化提交历史
git log --author="name" --oneline               # 按作者查看
git blame <file>                                # 逐行追溯修改人
git show <commit-hash>                          # 查看某次提交内容
git reflog                                      # 所有 HEAD 移动记录(救急神器)
```

```bash
# 冲突解决模板
git merge feature/xxx     # 冲突出现
git status                # 查看冲突文件(Unmerged paths)
# 编辑冲突文件: <<<<<<< HEAD / ======= / >>>>>>> 保留所需部分
git add <resolved-file>   # 标记已解决
git commit                # 完成合并
```

```bash
# Conda 常用命令速查
conda create -n py310 python=3.10 -y            # 创建环境(指定Python版本)
conda activate py310                            # 激活环境
conda deactivate                                # 退出环境
conda env list                                  # 列出环境
conda list                                      # 查看当前环境包
conda install numpy pandas -c conda-forge       # 安装包
conda env export > environment.yml              # 导出环境配置
conda env create -f environment.yml             # 从配置重建环境
conda remove -n py310 --all                     # 删除环境
```

```python
# Jupyter 魔法命令速查
# %%timeit -n 1000 -r 5        计时重复执行
# %timeit 单行计时
# %matplotlib inline           图表内嵌显示
# %%bash 单元格作为 bash 执行
# %%python3 单元格用其他解释器
# %load file.py                加载文件到单元格
# %run script.py               执行脚本
# %who / %whos                 查看变量
# !pip install xxx             执行 shell 命令
# %debug                       进入调试器

import pandas as pd
# 单元格内 %timeit 示例:
# %timeit [x**2 for x in range(1000)]
```

## 🎯 面试高频 TOP10

1. **Q: rebase 和 merge 区别？何时用哪个？** **A:** merge 保留分叉历史+合并提交；rebase 线性化(重放)；feature 分支与 main 对齐用 rebase(干净)，发布/多人协作用 merge(可追溯)。
2. **Q: 合并冲突怎么解决？** **A:** git status 找冲突文件 → 编辑绕过 <<<<<<< ======= >>>>>>> → git add → commit；冲突不可避免时用 git mergetool 辅助。
3. **Q: 误删了文件/丢失了提交怎么恢复？** **A:** 未提交用 git checkout -- file / git restore；已提交用 git reflog 找 commit hash 再 cherry-pick/reset 恢复。
4. **Q: 分支策略怎么选？** **A:** 团队小用 GitHub Flow(feature→PR→main)；团队代码质量要求高用 Git Flow(main/develop/feature/release)；K8s 型工具链 fit 主干开发。
5. **Q: Git 和 SVN 区别？** **A:** Git 分布式(本地完整历史/离线提交)，SVN 集中式(依赖服务器)；Git 分支廉价、操作本地快、支持并行开发。
6. **Q: 如何撤销一个已推送的提交？** **A:** 用 git revert(生成反向提交，安全的公开操作)，不要 reset 后 force push(除非是私有分支且由你单独使用)。
7. **Q: Conda 和 pip 区别？** **A:** Conda 是环境+包管理器(跨语言、可解依赖二进制，不依赖 Python)；pip 是 Python 专属包管理器；Conda 创建隔离环境是优势。
8. **Q: Jupyter 如何保证可复现性？** **A:** 从上到下顺序执行每个单元格、导出为 .py 脚本、记录环境(requirements.txt/environment.yml)、用 nbconvert 自动化测试。
9. **Q: Git stash 的坑？** **A:** 未 add 的文件 stash 默认不包含(--include-untracked)；pop 冲突要手动解决；stash list 要定期清(用私有限定名称)。
10. **Q: .gitignore 失效了？** **A:** 已被跟踪的文件 .gitignore 不生效，需 git rm --cached file 解除跟踪；添加后新文件才生效。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 把 node_modules/.env/密钥 push 上去 | .gitignore 排除 + git rm --cached 解除已有跟踪；密钥用 Git 管理或用 Vault/CI secret |
| 每次提交巨大文件/大量无关改动 | 原子提交：一次提交一件事，提交信息规范(Conventional Commits) |
| 直接在 main 上开发 | 永远在 feature 分支上开发，PR 合入 |
| Rebase 已推送共享分支 | 只对未推送的分支 rebase，否则破坏协作历史 |
| 混用 pip 和 conda | 环境内统一用 conda 或 pip；需求文件锁定版本 |
| 笔记本执行顺序乱 | 全部重启 kernel 并按序执行，提交前清理输出 |
| 忘记 git pull 就 push | push 前先 pull --rebase，解决远端领先问题 |
| 用 root 装 Conda | 用户级安装(Miniconda) 到用户目录 ~/miniconda3 |

## 📐 架构设计要点

- **分支流程**：feature → (CI 构建+测试) → PR 评审 → 合入 main → 自动发布，数据可观测。
- **提交规范**：`<type>(<scope>): <subject>` — feat/fix/docs/style/refactor/test/chore。
- **环境隔离**：不同项目用独立 Conda/venv 环境，版本锁定(requirements.txt/pyproject.toml)。
- **工具链整合**：Git + CI(GitHub Actions/GitLab CI) + 代码质量(SonarQube) + 文档(Jupyter nbconvert)。
- **可复现性**：环境文件版本化 + 种子固定 + 自动化测试在 CI 中运行。

## 🔗 关联技术

- **Conda/Mamba**：Python 版本管理，配合 Jupyter kernel 切换环境。
- **Jupyter**：数据分析/AI 探索首选，配合 nbformat 版本控制。
- **CI/CD**：Git 触发 CI 流水线，测试通过才合入 main。
- **Docker**：开发环境容器化，Git 仓库 + Dockerfile 构建可复现环境。
- **IDE**：Git 集成(编辑器内 diff/conflict 解决)，Jupyter 的 IDE 加持(VS Code/PyCharm)。