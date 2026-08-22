# Git 面试高频题速记

> 面向后端和 AI 岗位面试的 Git 高频问题速查，涵盖概念、操作和实战场景。

---

## 一、基础概念

### 1. Git 和 SVN 的区别？

| 对比维度 | Git（分布式） | SVN（集中式） |
|----------|---------------|---------------|
| 仓库存储 | 本地有完整仓库 | 仅服务器有完整仓库 |
| 离线工作 | 完全支持 | 依赖网络 |
| 分支操作 | 轻量、快速 | 创建分支是拷贝目录 |
| 性能 | 快（本地操作） | 慢（网络操作） |

### 2. Git 工作区、暂存区、仓库的关系？

```
工作区（Working Directory） → git add → 暂存区（Staging Area） → git commit → 本地仓库（Local Repo） → git push → 远程仓库（Remote Repo）
```

### 3. 什么是 HEAD？

HEAD 是指向当前分支最新提交的指针。`HEAD~1` 表示上一个提交，`HEAD~2` 表示上两个提交。

---

## 二、高频操作题

### 4. `git pull` 和 `git fetch` 的区别？

- **fetch**：只拉取远程数据，不合并，需要手动 `git merge`
- **pull**：fetch + merge（或 rebase）一步完成
- 推荐：`git pull --rebase` 避免多余 merge commit

### 5. 如何撤销修改？

```bash
# 未 add
git checkout -- <file>
git restore <file>

# 已 add 未 commit
git reset HEAD <file>
git restore --staged <file>

# 已 commit 未 push
git reset --soft HEAD~1   # 保留改动
git reset --hard HEAD~1   # 彻底丢弃

# 已 push
git revert <commit>       # 安全，推荐
```

### 6. 如何解决冲突？

1. `git status` 找到冲突文件
2. 打开文件，找到 `<<<<<<<`、`=======`、`>>>>>>>` 标记
3. 手动编辑，保留需要的代码，删除冲突标记
4. `git add <file>` 标记为已解决
5. `git commit` 完成合并

### 7. 如何合并多个 commit 为一个？

```bash
git rebase -i HEAD~3
# 将后两个 commit 的 pick 改为 squash
```

### 8. `git merge` 和 `git rebase` 的区别？

| 操作 | 特点 | 适用场景 |
|------|------|----------|
| merge | 保留完整历史，创建 merge commit | 公共分支合并 |
| rebase | 线性化历史，无 merge commit | 个人分支整理 |

### 9. 已经 push 了，如何修改 commit message？

```bash
git commit --amend -m "new message"
git push --force-with-lease
```

### 10. 如何找回误删的分支？

```bash
git reflog               # 找到分支最后的 commit hash
git checkout -b <branch-name> <commit-hash>
```

---

## 三、团队协作题

### 11. 什么是 Git Flow？

一种分支管理策略，包含 main、develop、feature、release、hotfix 五种分支类型，适合有版本发布周期的项目。

### 12. 什么是 GitHub Flow？

一种更简洁的分支策略，只有 main 和 feature 分支，feature 分支通过 PR 合并到 main 后立即部署，适合持续部署项目。

### 13. PR 的代码审查流程？

1. 开发者从 main 创建 feature 分支
2. 开发完成后 push 到远程
3. 在 GitHub/GitLab 创建 PR
4. 审查者 review 代码，提意见
5. 开发者修改后重新 push
6. 审查通过后 Squash Merge 到 main
7. 删除 feature 分支

### 14. 如何处理大文件？

使用 Git LFS（Large File Storage）：

```bash
git lfs track "*.pkl"
git add .gitattributes
git commit -m "chore: track .pkl files with LFS"
```

---

## 四、场景题

### 场景 1：提交信息写错了

```bash
# 最后一次提交
git commit --amend -m "correct message"

# 历史提交
git rebase -i HEAD~3
# 将对应 commit 的 pick 改为 reword
```

### 场景 2：在错误的分支上开发了

```bash
# 方法一：cherry-pick（推荐）
git log --oneline          # 复制错误分支上的 commit hash
git checkout correct-branch
git cherry-pick <hash1> <hash2>

# 方法二：stash
git stash
git checkout correct-branch
git stash pop
```

### 场景 3：需要紧急修复，但当前分支有未完成的修改

```bash
git stash                         # 暂存当前修改
git checkout main
git checkout -b hotfix/critical-bug
# 修复
git commit -m "fix: critical bug"
git push origin hotfix/critical-bug
git checkout feature/xxx
git stash pop                     # 恢复之前的工作
```

### 场景 4：误加了不该跟踪的文件

```bash
# 添加到 .gitignore 后移除跟踪
echo "*.log" >> .gitignore
git rm --cached *.log
git commit -m "chore: stop tracking log files"
```

### 场景 5：需要从另一个分支取一个文件

```bash
git checkout feature-branch -- path/to/file.java
```

---

## 五、速记口诀

```
git add 暂存，commit 保存
push 推送，pull 拉取
branch 分支，switch 切换
merge 合并，rebase 变基
stash 暂存，pop 恢复
log 查历史，diff 看差异
reset 回退，revert 撤销
reflog 救急，cherry-pick 摘取
```

---

## 六、面试回答模板

> **Q: 描述一次你用 Git 解决的复杂问题**

可以这样回答：
1. 描述场景（如：分支合并冲突、误删分支、提交历史混乱）
2. 分析问题（如：冲突原因、丢失的 commit 位置）
3. 解决方案（如：手动解决冲突 + 验证 / reflog 找回 / rebase 整理）
4. 预防措施（如：更频繁的 rebase、更小的提交粒度、pre-commit hook）

---

## 推荐复习思路

1. 创建几个测试仓库，模拟上述所有场景
2. 用 `git reflog` 练习恢复误操作
3. 熟悉 `git log --graph --oneline --all` 可视化提交历史
4. 理解 rebase 和 merge 的本质区别，敢于在个人分支上使用 rebase