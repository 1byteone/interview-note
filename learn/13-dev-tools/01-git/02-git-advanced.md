# Git 进阶与团队协作

> 掌握 Git 进阶技巧，提升团队协作效率与代码管理质量。

---

## 1. 分支策略

### Git Flow

适用于发布周期明确的项目，如移动 App、后端服务。

```
main         ──●─────────────●─────────────●──
                  \           / \           /
develop          ●─●─●─●─●─●─●─●─●─●─●─●─●─●
                     \           /       \   /
feature/ai-search    ●─●─●─●─●─●       ●─●─●─●
                         \           /
release/v1.1            ●─●─●─●─●─●───●
```

- **main**: 生产就绪代码，只接受 release 合并
- **develop**: 日常开发集成分支
- **feature/***: 功能开发分支，完成后合并到 develop
- **release/***: 发布准备分支，测试和修复后合并到 main 和 develop
- **hotfix/***: 紧急修复，直接从 main 创建，修复后合并到 main 和 develop

### GitHub Flow

适用于持续部署的项目，如 Web 应用、SaaS 服务。

```bash
# 单一主干，所有功能从 main 创建分支
git checkout -b feat/ai-search
# ... 开发、提交 ...
git push origin feat/ai-search
# 创建 PR → 审查 → 合并到 main → 自动部署
```

### Trunk Based Development

适用于 CI/CD 成熟、测试覆盖全面的团队。

```bash
# 所有开发者直接在主干或极短生命周期分支上工作
# 分支通常不超过几小时到一天
git checkout -b short-lived-fix
# 修改后立即合并回主干
```

### 策略选择

| 策略 | 适用场景 | 优势 | 劣势 |
|------|----------|------|------|
| Git Flow | 版本发布周期明确 | 流程严谨 | 分支复杂 |
| GitHub Flow | 持续部署 | 简单高效 | 对测试要求高 |
| Trunk Based | CI/CD 成熟团队 | 冲突少、交付快 | 需要高质量 CI |

---

## 2. Rebase 与 Merge 的选择

### Merge（合并）

```bash
git checkout main
git merge feature/login
```

- 保留完整的历史记录
- 自动创建 merge commit
- 适合合并到公共分支

### Rebase（变基）

```bash
git checkout feature/login
git rebase main
```

- 重写提交历史，使线形更清晰
- 避免不必要的 merge commit
- 适合在功能分支上整理提交

### 何时用哪个？

```bash
# 你的功能分支，整理历史用 rebase
git rebase main

# 合并到公共分支，用 merge
git checkout main && git merge feature/login

# 拉取远程代码，用 rebase 避免多余 merge commit
git pull --rebase
```

**黄金法则**：永远不要对已推送的公共分支执行 rebase。

### 交互式 Rebase

```bash
# 整理最近 3 个提交
git rebase -i HEAD~3
```

常用操作：

| 命令 | 作用 |
|------|------|
| `pick` | 保留该提交 |
| `reword` | 修改提交信息 |
| `squash` | 合并到上一个提交 |
| `fixup` | 合并但不保留提交信息 |
| `drop` | 删除该提交 |

---

## 3. Cherry-pick / Stash / Revert

### Cherry-pick（摘取提交）

将特定提交应用到当前分支，适用于需要选择性合并的场景。

```bash
# 从 feature 分支摘取一个提交到 main
git checkout main
git cherry-pick a1b2c3d
```

### Stash（暂存）

临时保存工作区修改，在不提交的情况下切换分支。

```bash
git stash                        # 暂存当前修改
git stash save "msg"             # 带信息暂存
git stash list                   # 查看暂存列表
git stash pop                    # 恢复并删除最新暂存
git stash apply stash@{1}        # 恢复指定暂存但不删除
git stash drop stash@{1}         # 删除指定暂存
```

### Revert（撤销提交）

创建一个新的提交来撤销历史提交，安全地"回退"。

```bash
git revert a1b2c3d              # 撤销指定提交
git revert HEAD                  # 撤销最新提交
```

**与 reset 的区别**：revert 创建新提交，不改变历史，适合公共分支；reset 删除历史，仅适合本地分支。

---

## 4. 子模块（Submodule）

在项目中引用其他 Git 仓库。

```bash
# 添加子模块
git submodule add https://github.com/xxx/common-lib.git libs/common

# 克隆包含子模块的项目
git clone --recurse-submodules https://github.com/xxx/mall-ai.git

# 更新子模块
git submodule update --init --recursive

# 子模块修改后提交
cd libs/common
git add . && git commit -m "update"
cd ../..
git add libs/common && git commit -m "chore: update submodule"
```

---

## 5. Git Hooks 自动化

在特定 Git 事件发生时自动执行脚本。

### 常用 Hooks

| Hook | 触发时机 | 典型用途 |
|------|----------|----------|
| `pre-commit` | 提交前 | 代码格式化、lint 检查 |
| `commit-msg` | 提交后 | 校验提交信息格式 |
| `pre-push` | 推送前 | 运行测试、构建检查 |
| `post-merge` | 合并后 | 自动更新依赖 |

### 使用 husky（Node 项目）

```bash
# .husky/pre-commit
#!/bin/sh
npx lint-staged             # 仅对暂存文件运行 lint

# .husky/commit-msg
#!/bin/sh
npx --no -- commitlint --edit $1  # 校验提交信息格式
```

### 使用 pre-commit（Python 项目）

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/psf/black
    rev: 23.9.0
    hooks:
      - id: black
  - repo: https://github.com/PyCQA/flake8
    rev: 6.1.0
    hooks:
      - id: flake8
```

---

## 6. 实战：PR 代码审查流程

```bash
# 1. 创建 PR 分支（在本地）
git checkout main && git pull
git checkout -b feat/ai-similarity

# 2. 开发并提交（遵循 Conventional Commits）
git commit -m "feat: add item similarity calculation"
git commit -m "test: add similarity unit tests"
git commit -m "docs: update API docs for similarity endpoint"

# 3. rebase 整理提交
git rebase -i HEAD~3

# 4. 推送到远程
git push origin feat/ai-similarity

# 5. 在 GitHub/GitLab 创建 PR

# 6. 审查者反馈后修改
git commit -m "fix: address review feedback - optimize similarity calc"
git push origin feat/ai-similarity

# 7. PR 通过后 Squash Merge 到 main
# （在 GitHub 上操作，或在本地）
git checkout main && git merge --squash feat/ai-similarity
git commit -m "feat: add item similarity calculation (#42)"
git push origin main

# 8. 清理本地分支
git branch -d feat/ai-similarity
```

---

## 下一步

完成进阶学习后，进入 [Git 故障排除](03-git-troubleshooting.md) 学习误操作恢复和冲突解决策略。