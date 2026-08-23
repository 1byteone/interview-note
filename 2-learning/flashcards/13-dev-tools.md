# 开发工具 — 面试抽认卡

> 来源：`learn/13-dev-tools/05-interview/`

---

### Card 1: Git 分支策略
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Git Flow 和 GitHub Flow 分支策略有什么区别？**

**A:** Git Flow：`master`（生产） + `develop`（开发） + `feature/*`（功能） + `release/*`（发布） + `hotfix/*`（热修复）。流程复杂，适合版本发布周期固定的项目。GitHub Flow：只有 `main` 分支 + 功能分支，所有功能分支合并到 `main` 后立即部署，适合持续部署的 Web 项目。Trunk-Based：所有开发者在 `main` 上开发，短生命周期分支（<1 天），高频合并，适合 CI/CD 成熟团队。选择：需要版本管理选 Git Flow，持续部署选 GitHub Flow，高频迭代选 Trunk-Based。

---

### Card 2: rebase vs merge
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: git rebase 和 git merge 的区别是什么？什么场景下用 rebase？**

**A:** `git merge` 创建 merge commit，保留完整分支历史，但历史图可能杂乱。`git rebase` 将当前分支的提交"移植"到目标分支的顶部，历史线性整洁，但会改写提交哈希。推荐：推送前用 rebase 整理提交（`git rebase -i` 合并/squash 小提交），公共分支用 merge（不 rewrite 历史）。`git pull --rebase` 替代 `git pull`（避免多余 merge commit）。黄金法则：**不要对已推送的公共分支执行 rebase**。

---

### Card 3: Git LFS
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Git LFS 解决了什么问题？大文件管理如何工作？**

**A:** Git LFS（Large File Storage）将大文件替换为文本指针（指针文件指向远程存储服务器），仓库中不存储大文件本身。适用：二进制文件（图片、视频、模型文件、编译产物）。工作流程：`git lfs track "*.psd"` 跟踪大文件，`git add .gitattributes` 提交跟踪规则，文件上传到 LFS 服务器。优势：仓库克隆速度快（只拉指针），存储空间节省。`git lfs migrate` 将已有仓库中的大文件迁移到 LFS。

---

### Card 4: merge 冲突解决
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Git 合并冲突如何产生？如何解决？**

**A:** 冲突产生：两个分支修改了同一文件的同一行，或一个分支删除文件另一个分支修改了它。解决步骤：① `git status` 找到冲突文件（`both modified`）；② 打开文件找到 `<<<<<<< HEAD`（当前分支）→ `=======`（分隔）→ `>>>>>>> branch-name`（合并分支）；③ 手动编辑保留需要的代码，删除冲突标记；④ `git add` 标记为已解决；⑤ `git commit` 完成合并。工具：`git mergetool`（配置 vimdiff/kdiff3/VS Code），`git diff --check` 检查残留冲突标记。

---

### Card 5: reflog 恢复数据
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: git reflog 是什么？如何用它恢复误删的提交？**

**A:** `git reflog` 记录所有 HEAD 移动操作（包括 reset、rebase、cherry-pick），即使在本地仓库中"丢失"的提交也能找回。`git reflog` 显示所有操作历史，每行含 `HEAD@{index}` 和操作描述。恢复：`git reset --hard HEAD@{n}` 回到指定位置，或 `git cherry-pick <commit>` 将丢失的提交复制到当前分支。reflog 只在本地仓库，不会推送到远程。默认保留 90 天。`git fsck --lost-found` 找回未被引用的对象。

---

### Card 6: Conda 环境隔离
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Conda 环境隔离的原理是什么？如何迁移环境？**

**A:** Conda 在 `envs/` 目录下创建独立的环境目录，每个环境包含独立的 Python 解释器、包和依赖。`conda create -n myenv python=3.10` 创建环境，`conda activate myenv` 激活。环境导出：`conda env export > environment.yml`（跨平台，推荐）或 `conda list --export > requirements.txt`（仅 pip 包）。环境导入：`conda env create -f environment.yml`。`conda` vs `pip`：conda 管理二进制包（非 Python 依赖也可管理，如 CUDA），pip 仅管理 Python 包。`mamba` 是 conda 的 C++ 重写，安装速度快 10 倍+。

---

### Card 7: Jupyter Magic 命令
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Jupyter Notebook 的 % 和 %% Magic 命令有哪些常用？**

**A:** `%`（行 Magic，作用于单行）：`%timeit`（计时，`%timeit sum(range(1000))`），`%run`（运行脚本），`%load`（加载代码），`%who`（列出变量），`%matplotlib inline`（嵌入图表）。`%%`（单元格 Magic，作用于整个单元格）：`%%time`（单元格耗时），`%%bash`（运行 Shell 命令），`%%writefile`（写入文件），`%%capture`（捕获输出），`%%html`（渲染 HTML）。`%env` 设置环境变量。`%lsmagic` 列出所有 Magic 命令。

---

### Card 8: nbconvert 导出
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Jupyter Notebook 如何导出为其他格式？**

**A:** `jupyter nbconvert --to html notebook.ipynb`（HTML 格式，保留图表）。`--to markdown`（Markdown，适合文档）。`--to script`（Python 脚本，去除 Markdown 单元格）。`--to pdf`（需要 LaTeX 环境）。`--to slides`（reveal.js 幻灯片）。`--to webpdf`（无头浏览器 PDF）。`--no-input` 排除代码单元格（只显示输出和 Markdown）。`--template` 自定义模板（如 `nbconvert --to html --template classic`）。`--execute` 先执行再导出（配合 `--ExecutePreprocessor.timeout=600`）。

---

### Card 9: Git Hooks
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Git Hooks 的作用是什么？pre-commit 和 pre-push 分别做什么？**

**A:** Git Hooks 是 Git 事件触发的本地脚本（`.git/hooks/` 目录）。`pre-commit`：提交前执行代码检查（ESLint、Prettier、单元测试），失败则阻止提交。`pre-push`：推送前运行集成测试或构建检查。`commit-msg`：检查提交信息格式（是否符合 Angular 规范）。`prepare-commit-msg`：自动生成提交信息。Hooks 不会被 Git 跟踪（`git init` 复制）。团队共享：`git config core.hooksPath .githooks` 或使用 `husky`（Node.js 生态）配置 `.husky/` 目录，支持通过 `git add` 跟踪。

---

### Card 10: PR 审查流程
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: 良好的 PR 审查流程应该包含哪些要素？**

**A:** ① PR 标题规范（`feat: 添加用户注册功能`，遵循 Conventional Commits）；② 描述清晰（动机、改动点、影响范围、测试方式）；③ 代码审查清单（功能正确性、代码风格、异常处理、边界情况、性能影响）；④ 自动化检查（CI 流水线：构建+测试+代码扫描+覆盖率）；⑤ 审查者（至少 1 人，关键模块 2 人）；⑥ 合并方式（Squash Merge 保持主分支历史整洁）；⑦ 反馈文化（对事不对人，用"建议"而非"要求"语气）。

---

### Card 11: stash 用法
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: git stash 的常用命令和使用场景是什么？**

**A:** `git stash` 临时保存工作区修改，还原干净的工作区（切换分支前保存未提交的修改）。`git stash pop` 恢复最近一次 stash 并删除它。`git stash apply` 恢复但不删除 stash。`git stash list` 查看所有 stash。`git stash save "msg"` 带描述保存。`git stash drop stash@{n}` 删除指定 stash。`git stash branch <branch>` 从 stash 创建新分支。场景：正在开发功能 A，突然需要修复紧急 BUG，stash 后切分支修 BUG，修完回来 `stash pop` 继续开发。

---

### Card 12: cherry-pick
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: git cherry-pick 的用途是什么？什么场景下使用？**

**A:** `git cherry-pick <commit>` 将指定提交的变更应用到当前分支（复制提交，不是移动）。场景：① 修复分支上的 BUG 补丁应用到发布分支；② 在 release 分支上选择特定功能提交（不要全部合并）；③ 误操作后恢复丢失的提交。`git cherry-pick -n` 只应用变更不自动提交（手动审查）。`git cherry-pick <commitA>..<commitB>` 批量 cherry-pick 多个提交。注意：cherry-pick 会生成新的 commit hash，多次 cherry-pick 相同提交可能导致冲突。