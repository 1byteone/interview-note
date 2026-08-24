# Guide Stars 自动同步说明

## 本地同步（推荐）

```bash
# 同步全部 27 个仓库
python3 _scripts/sync_stars.py

# 预览变更（不写入）
python3 _scripts/sync_stars.py --dry-run

# 仅同步指定仓库
python3 _scripts/sync_stars.py --repo=owner/name
```

脚本会自动：
1. 调用 GitHub API 获取每个仓库的最新 Stars 数
2. 更新 `3-ecosystem/guide_repos.json`
3. 更新 `3-ecosystem/repositories/{name}.md` 的 header 和 Metadata 表
4. 每请求间隔 500ms 避免限流

## 自动同步（CI）

已配置 GitHub Actions workflow：`.github/workflows/sync-guide-stars.yml`

- **触发**：每周一 09:07（UTC+8）自动运行 + 手动触发（workflow_dispatch）
- **行为**：同步 Stars → 若有变更自动 commit + push
- **自动更新**：guide_repos.json + 详情页

## 注意事项

- 需要 `gh` CLI 已登录（本地），CI 使用内置 `GITHUB_TOKEN`
- GitHub REST API 未认证限额 60 次/小时，认证后 5000 次/小时；脚本 27 个请求远低于限额
- 若同时修改了 `guide_repos.json` 的字段结构，请先运行 `--dry-run` 预览