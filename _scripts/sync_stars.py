#!/usr/bin/env python3
"""
guide/ Stars 同步脚本
======================
从 GitHub API 拉取 guide_repos.json 中所有仓库的最新 Stars 数据，
更新 JSON 文件和仓库详情页的 Stars 显示。

用法:
  python3 sync_stars.py          # 同步所有仓库
  python3 sync_stars.py --dry-run  # 预览变更，不实际写入
  python3 sync_stars.py --repo owner/name  # 仅同步指定仓库

依赖: gh CLI (已登录)
"""

import json
import os
import re
import subprocess
import sys
import time

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
GUIDE_DIR = os.path.join(os.path.dirname(BASE_DIR), '3-ecosystem')  # 实际数据在 3-ecosystem/
JSON_PATH = os.path.join(GUIDE_DIR, 'guide_repos.json')
REPOS_DIR = os.path.join(GUIDE_DIR, 'repositories')
RATE_LIMIT_SLEEP = 0.5  # 500ms between requests to avoid rate limiting


def get_stars(repo_name):
    """调用 gh API 获取仓库最新 Stars 数"""
    try:
        result = subprocess.run(
            ['gh', 'api', f'repos/{repo_name}', '--jq', '.stargazers_count'],
            capture_output=True, check=True, timeout=15
        )
        stdout = result.stdout.decode('utf-8', errors='replace')
        return int(stdout.strip())
    except subprocess.CalledProcessError as e:
        msg = e.stderr.decode('utf-8', errors='replace').strip() if e.stderr else 'unknown'
        print(f'  [ERR] gh api failed: {msg}')
        return None
    except Exception as e:
        print(f'  [ERR] {e}')
        return None


def update_repo_detail_file(repo_name, new_stars):
    """更新仓库详情 markdown 文件中的 Stars 显示"""
    safe_name = repo_name.replace('/', '_')
    detail_path = os.path.join(REPOS_DIR, f'{safe_name}.md')
    if not os.path.exists(detail_path):
        return False

    with open(detail_path, encoding='utf-8') as f:
        content = f.read()

    # 统一换行为 \n (处理 Windows \r\n)
    content = content.replace('\r\n', '\n')

    # 匹配 header 行: > ⭐ X,XXX | ...
    old_pattern = r'^(> [^\d]+)[\d,]+'
    stars_str = f'{new_stars:,}'
    new_content = re.sub(old_pattern, r'\g<1>' + stars_str, content, count=1, flags=re.MULTILINE)

    # 也匹配 Metadata 表中的 Stars 行
    old_table = r'(\| Stars \| )[\d,]+(\s*\|)'
    new_content = re.sub(old_table, r'\g<1>' + stars_str + r'\g<2>', new_content, count=1)

    if new_content != content:
        with open(detail_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return False


def main():
    dry_run = '--dry-run' in sys.argv
    specific_repo = None
    for arg in sys.argv[1:]:
        if arg.startswith('--repo='):
            specific_repo = arg.split('=', 1)[1]

    # 加载 JSON
    with open(JSON_PATH, encoding='utf-8') as f:
        repos = json.load(f)

    if specific_repo:
        target_repos = [r for r in repos if r['full_name'] == specific_repo]
        if not target_repos:
            print(f'[ERR] 未找到仓库: {specific_repo}')
            sys.exit(1)
        iter_repos = target_repos
    else:
        iter_repos = repos

    changed = 0
    errors = 0

    for r in iter_repos:
        name = r['full_name']
        old_stars = r['stargazers_count']

        print(f'  [{name}] current={old_stars:,}', end='', flush=True)

        new_stars = get_stars(name)
        if new_stars is None:
            print(f'  [SKIP]')
            errors += 1
            continue

        print(f' -> {new_stars:,}', end='', flush=True)

        if new_stars == old_stars:
            print(f'  [OK] 无变化')
            continue

        diff = new_stars - old_stars
        print(f'  (diff: {diff:+d})', end='', flush=True)

        if dry_run:
            print(f'  [DRY-RUN] 跳过写入')
            continue

        # 更新 JSON
        r['stargazers_count'] = new_stars

        # 更新详情页
        detail_updated = update_repo_detail_file(name, new_stars)
        if detail_updated:
            print(f'  [OK]', end='', flush=True)
        else:
            print(f'  [WARN] 详情页未更新(文件缺失或格式不符)', end='', flush=True)

        json_changed = True if new_stars != old_stars else False
        if json_changed:
            changed += 1
        print()

        # 限速控制
        time.sleep(RATE_LIMIT_SLEEP)

    # 写回 JSON
    if not dry_run and changed > 0:
        with open(JSON_PATH, 'w', encoding='utf-8') as f:
            json.dump(repos, f, ensure_ascii=False, indent=2)
        print(f'\n[OK] 已更新 {changed} 个仓库的 Stars 数据到 guide_repos.json')
    elif dry_run and changed > 0:
        print(f'\n[DRY-RUN] 将更新 {changed} 个仓库（实际未写入）')
    else:
        print(f'\n[OK] 无变更')

    if errors > 0:
        print(f'[WARN] {errors} 个仓库获取失败')
        sys.exit(1)


if __name__ == '__main__':
    main()