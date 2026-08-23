# Git 基础入门

> 面向 AI/Java 开发者的 Git 快速上手，从零开始管理你的代码版本。

---

## 1. Git 是什么？

Git 是目前最流行的分布式版本控制系统。简单说，它帮你记录代码的每一次变更，让你可以：

- **回溯历史**：回到任意一个历史版本
- **并行开发**：创建分支互不干扰，完成后合并
- **团队协作**：多人同时编辑同一项目
- **安全备份**：代码在本地和远程都有完整副本

---

## 2. 安装与配置

### 安装 Git

- **Windows**：从 [git-scm.com](https://git-scm.com) 下载安装包，一路默认即可
- **macOS**：`brew install git`
- **Linux**：`sudo apt install git` 或 `sudo yum install git`

### 首次配置（全局身份）

```bash
git config --global user.name "Your Name"
git config --global user.email "your@email.com"
git config --global core.editor "vim"      # 设置默认编辑器
git config --global init.defaultBranch main  # 默认分支名
```

查看配置结果：

```bash
git config --list
```

---

## 3. 基本操作

### 3.1 初始化仓库

```bash
# 方式一：从零开始
mkdir my-project && cd my-project
git init

# 方式二：克隆远程仓库
git clone https://github.com/user/repo.git
git clone git@github.com:user/repo.git  # SSH 方式
```

### 3.2 日常开发循环

```bash
# 1. 查看当前状态
git status

# 2. 将文件加入暂存区
git add README.md          # 添加单个文件
git add .                  # 添加所有变更
git add src/               # 添加某个目录

# 3. 提交到本地仓库
git commit -m "feat: add user login module"

# 4. 推送到远程仓库
git push origin main
```

### 3.3 查看历史

```bash
git log                     # 完整提交历史
git log --oneline           # 一行显示
git log --graph             # 图形化展示分支
git diff                    # 查看工作区与暂存区的差异
git diff --staged           # 查看暂存区与上次提交的差异
```

### 3.4 拉取远程更新

```bash
git pull                    # 拉取远程最新代码并合并
git pull --rebase           # 拉取并用 rebase 方式合并（推荐）
git fetch                   # 仅拉取，不合并
```

---

## 4. 分支管理

### 4.1 分支基本操作

```bash
# 创建分支
git branch feature/login

# 切换分支
git checkout feature/login
git switch feature/login    # Git 2.23+ 推荐

# 创建并切换（一步到位）
git checkout -b feature/login
git switch -c feature/login

# 查看分支
git branch                  # 本地分支
git branch -r               # 远程分支
git branch -a               # 所有分支

# 删除分支
git branch -d feature/login          # 已合并的分支
git branch -D feature/login          # 强制删除（未合并）
git push origin --delete feature/login  # 删除远程分支
```

### 4.2 合并分支

```bash
# 切换到目标分支（如 main）
git checkout main

# 将 feature/login 合并到 main
git merge feature/login

# 如果出现冲突，手动解决后
git add .
git commit -m "merge: resolve conflicts"
```

---

## 5. 团队协作流程

### 典型协作流程（GitHub Flow）

```
1. 从 main 拉取最新代码
   git checkout main && git pull

2. 创建功能分支
   git checkout -b feat/ai-search

3. 开发并提交
   git add . && git commit -m "feat: add embedding search"

4. 推送分支到远程
   git push origin feat/ai-search

5. 在 GitHub/GitLab 上创建 PR（Pull Request）

6. 代码审查通过后合并到 main

7. 删除功能分支
   git branch -d feat/ai-search
```

---

## 6. 最小案例：用 Git 管理 Python 项目

```bash
# 1. 初始化
mkdir mall-ai-recsys && cd mall-ai-recsys
git init
echo "# AI Recommendation System" > README.md
git add README.md && git commit -m "init: project scaffold"

# 2. 添加 Python 项目文件
cat > requirements.txt << 'EOF'
numpy==1.26.0
pandas==2.1.0
scikit-learn==1.3.0
EOF
git add requirements.txt && git commit -m "feat: add dependencies"

# 3. 创建核心模块
mkdir src
cat > src/recommend.py << 'EOF'
"""Recommendation engine placeholder."""
def recommend(user_id: int, top_k: int = 10):
    return [f"item_{i}" for i in range(top_k)]
EOF
git add src/ && git commit -m "feat: add recommendation engine skeleton"

# 4. 关联远程仓库并推送
git remote add origin https://github.com/you/mall-ai-recsys.git
git push -u origin main

# 5. 创建开发分支继续
git checkout -b feat/collaborative-filtering
```

---

## 7. 常用命令速查表

| 操作 | 命令 |
|------|------|
| 查看状态 | `git status` |
| 添加文件 | `git add <file>` |
| 提交 | `git commit -m "msg"` |
| 推送 | `git push` |
| 拉取 | `git pull --rebase` |
| 查看日志 | `git log --oneline --graph` |
| 创建分支 | `git switch -c <branch>` |
| 合并分支 | `git merge <branch>` |
| 查看差异 | `git diff` |
| 暂存更改 | `git stash` |

---

## 下一步

掌握基本操作后，进入 [Git 进阶](02-git-advanced.md) 学习分支策略、rebase 与团队协作最佳实践。