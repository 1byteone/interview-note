# Git 误操作恢复与冲突解决

> 人非圣贤，孰能无过。掌握 Git 的"后悔药"是每个开发者的必修课。

---

## 1. 误操作恢复

### 1.1 修改最后一次提交

```bash
# 提交后想修改提交信息
git commit --amend -m "new message"

# 漏掉了一个文件
git add forgotten-file.py
git commit --amend --no-edit

# 三个注意点：
# - 仅适用于最后一次提交
# - 如果已推送到远程，需要 force push
# - 公共分支上慎用
```

### 1.2 撤回暂存区的文件

```bash
git add wrong-file.java
git restore --staged wrong-file.java
# 或旧版写法
git reset HEAD wrong-file.java
```

### 1.3 恢复工作区的修改

```bash
# 丢弃工作区未暂存的修改
git restore wrong-file.java
git checkout -- wrong-file.java  # 旧版写法

# 丢弃所有本地修改（危险！）
git restore .
git checkout -- .  # 旧版写法
```

### 1.4 使用 Reflog 找回丢失的提交

`git reflog` 记录了所有 HEAD 移动的历史，是找回丢失提交的"救生索"。

```bash
# 场景：reset 时不小心删除了一个分支
$ git reflog
a1b2c3d HEAD@{0}: reset: moving to HEAD~2
e5f6g7h HEAD@{1}: commit: feat: add AI recommendation engine
p8q9r0s HEAD@{2}: commit: fix: update search logic
# ... 找到丢失的提交

# 恢复
git checkout -b recovered-branch e5f6g7h
```

### 1.5 撤销已推送的提交

```bash
# 方式一：revert（安全，推荐）
git revert HEAD~3..HEAD       # 撤销最近 3 个提交
git push origin main

# 方式二：reset + force push（危险，仅限个人分支）
git reset --hard HEAD~3       # 回退到 3 个提交前
git push --force-with-lease   # 比 --force 更安全
```

### 1.6 找回删除的分支

```bash
# 场景：弟弟手滑删了你本地分支
git branch -D feature/ai-search

# 拯救
git reflog
# 找到最近的 commit hash
git checkout -b feature/ai-search <commit-hash>

# 如果远程还有该分支
git checkout -b feature/ai-search origin/feature/ai-search
```

---

## 2. 冲突解决策略

### 2.1 冲突产生的原因

当两个分支修改了同一个文件的同一行时，Git 无法自动合并，需要人工介入。

### 2.2 解决冲突的基本流程

```bash
# 1. 合并时出现冲突
git merge feature/search
# 输出：CONFLICT in src/search.py

# 2. 查看冲突文件
git status
# 冲突文件在 Unmerged paths 中列出

# 3. 打开冲突文件
# <<<<<<< HEAD
# 当前分支的内容
# =======
# 对方分支的内容
# >>>>>>> feature/search

# 4. 手动编辑，保留需要的部分，删除冲突标记

# 5. 标记为已解决
git add src/search.py

# 6. 继续合并
git commit
```

### 2.3 冲突解决工具

```bash
# 使用内置的 vimdiff
git mergetool

# 使用外部工具（如 Beyond Compare、IntelliJ IDEA）
git config --global merge.tool intellij
```

### 2.4 预防冲突的技巧

```bash
# 频繁 rebase 保持分支最新
git checkout feature/search
git rebase main

# 小步提交，避免大范围修改
# 每次只改一个功能点

# 使用 .gitattributes 确保文件编码一致
echo "* text=auto" > .gitattributes
```

---

## 3. 大文件处理（Git LFS）

Git 对大型文件（大于 100MB）的处理效率很低，且会导致仓库臃肿。Git LFS（Large File Storage）是官方推荐方案。

### 安装与配置

```bash
# 安装 Git LFS
git lfs install

# 在仓库中指定要跟踪的文件类型
git lfs track "*.pkl"          # 训练好的模型文件
git lfs track "*.csv"          # 大型数据集
git lfs track "*.onnx"         # ONNX 模型
git lfs track "*.bin"          # 二进制文件

# 提交 .gitattributes
git add .gitattributes
git commit -m "chore: configure Git LFS"
```

### 日常使用

```bash
# LFS 文件的使用与普通 Git 一致
git add model/sentiment.pkl
git commit -m "feat: add sentiment analysis model"
git push origin main

# 查看 LFS 文件
git lfs ls-files

# 查看 LFS 存储空间使用
git lfs status
```

### 将已有大文件迁移到 LFS

```bash
# 如果仓库中已有大文件历史，需要迁移
git lfs migrate import --include="*.pkl,*.csv" --everything
git push --force
```

---

## 4. 面试高频 Git 问题

### Q1: `git pull` 和 `git fetch` 的区别？

`git fetch` 只从远程拉取最新数据到本地仓库，**不合并**到工作区。`git pull` = `git fetch` + `git merge`。推荐使用 `git pull --rebase` 避免多余 merge commit。

### Q2: 如何放弃本地的所有修改？

```bash
# 未暂存的修改
git checkout -- .

# 已暂存但未提交
git reset HEAD . && git checkout -- .

# 已提交但未推送
git reset --hard HEAD~1

# 已推送
git revert HEAD
```

### Q3: 如何解决冲突？

1. 找到冲突文件（`git status`）
2. 手动编辑冲突区域，保留需要的内容
3. 删除 `<<<<<<<`、`=======`、`>>>>>>>` 标记
4. `git add <file>` 标记为已解决
5. `git commit` 完成合并

### Q4: 什么是 `git rebase` 和 `git merge` 的区别？

- **merge**：保留完整历史，自动创建 merge commit，适合公共分支合并
- **rebase**：重写历史，使提交线形化，适合个人分支整理

### Q5: 如何撤销一个已推送的提交？

推荐使用 `git revert <commit>`，它会创建一个新提交来撤销指定的提交，不会修改历史。如果确认是个人分支，可以使用 `git reset --hard` + `git push --force-with-lease`。

---

## 5. 快速恢复命令速查

| 场景 | 命令 |
|------|------|
| 改最后一次提交 | `git commit --amend` |
| 撤回暂存 | `git restore --staged <file>` |
| 丢弃工作区修改 | `git restore <file>` |
| 找回丢失提交 | `git reflog` + `git checkout <hash>` |
| 撤销已推送提交 | `git revert <commit>` |
| 解决冲突 | 手动编辑 + `git add` + `git commit` |
| 管理大文件 | `git lfs track "*.pkl"` |