#!/usr/bin/env python3
"""
Guide 仓库收录工作流脚本
========================
输入一个 GitHub 仓库地址，自动完成收录流程：

  1. 调用 gh API 抓取仓库 metadata（Stars、语言、Topics、描述）
  2. 根据 Topics 自动匹配生态归属，可手动指定
  3. 生成 repositories/{owner}_{repo}.md 详情页模板
  4. 追加到 guide_repos.json
  5. 提示后续更新 ecosystem-index.md

用法:
  python3 add_repo.py https://github.com/owner/repo
  python3 add_repo.py owner/repo --eco E06
  python3 add_repo.py owner/repo --dry-run

依赖: gh CLI (已登录)
"""

import json
import os
import re
import subprocess
import sys
import time

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
GUIDE_DIR = os.path.dirname(BASE_DIR)
JSON_PATH = os.path.join(GUIDE_DIR, 'guide', 'guide_repos.json')
REPOS_DIR = os.path.join(GUIDE_DIR, 'guide', 'repositories')
CATEGORIES_DIR = os.path.join(GUIDE_DIR, 'guide', 'categories')

ECOSYSTEMS = {
    'E01': {'name': 'Claude Code 生态', 'file': '01-ecosystem-claude-code.md', 'keywords': ['claude', 'claude-code', 'anthropic']},
    'E02': {'name': 'Codex 生态', 'file': '02-ecosystem-codex.md', 'keywords': ['codex', 'openai-agents']},
    'E03': {'name': 'DSH/Harness 生态', 'file': '03-ecosystem-dsh-harness.md', 'keywords': ['deepseek-harness', 'dsh', 'harness', 'cordis']},
    'E04': {'name': 'Hermes/OpenClaw 生态', 'file': '04-ecosystem-hermes-openclaw.md', 'keywords': ['hermes', 'openclaw', 'claw']},
    'E05': {'name': 'MCP 协议生态', 'file': '05-ecosystem-mcp.md', 'keywords': ['mcp', 'model-context-protocol', 'modelcontextprotocol']},
    'E06': {'name': '通识与基础', 'file': '06-ecosystem-general-agent.md', 'keywords': []},
}


def parse_input(arg):
    """解析输入，支持 URL 或 owner/repo 格式"""
    arg = arg.strip()
    if arg.startswith('http'):
        m = re.search(r'github\.com/([\w.-]+/[\w.-]+)', arg)
        if m:
            return m.group(1)
    if '/' in arg:
        return arg
    return None


def fetch_repo(repo_name):
    """调用 gh API 获取仓库信息"""
    result = subprocess.run(
        ['gh', 'api', f'repos/{repo_name}',
         '--jq', '{full_name, html_url, description, stargazers_count, language, topics, pushed_at, created_at}'],
        capture_output=True, check=True, timeout=15
    )
    stdout = result.stdout.decode('utf-8', errors='replace')
    return json.loads(stdout)


def guess_ecosystem(topics, description=''):
    """根据 topics 猜测生态归属"""
    text = ' '.join(topics).lower() + ' ' + (description or '').lower()
    # 按关键词权重匹配
    for eco_id, eco in ECOSYSTEMS.items():
        for kw in eco['keywords']:
            if kw.lower() in text:
                return eco_id
    return 'E06'  # 默认通识


def generate_detail_file(repo, ecosystem):
    """生成 repositories/{owner}_{repo}.md 详情页"""
    safe_name = repo['full_name'].replace('/', '_')
    detail_path = os.path.join(REPOS_DIR, f'{safe_name}.md')

    eco_name = ECOSYSTEMS[ecosystem]['name']
    topics_str = ', '.join(repo.get('topics', []) or []) or '无'
    stars = repo['stargazers_count']
    lang = repo['language'] or 'N/A'
    desc = repo.get('description') or '暂无描述'

    # Build cross-ref section (from existing JSON config if present)
    cross_section = ''
    with open(JSON_PATH, encoding='utf-8') as f:
        all_repos = json.load(f)
    existing = next((r for r in all_repos if r['full_name'] == repo['full_name']), None)
    if existing and existing.get('ecosystem_cross'):
        cross_section = f'''
## 生态交叉引用

- **主生态**: {ecosystem} · {eco_name}
- **交叉引用**:
'''
        for e in existing['ecosystem_cross']:
            related = [r['full_name'] for r in all_repos
                       if r['ecosystem_primary'] == e and r['full_name'] != repo['full_name']]
            if related:
                links = ', '.join([f'[{x}](../repositories/{x.replace("/", "_")}.md)' for x in related[:2]])
                cross_section += f'  - **{e}**: {links}\n'
        cross_section += '\n> 📖 完整矩阵见 [data/cross-reference.md](../data/cross-reference.md)'

    content = f"""# {repo['full_name']}

> ⭐ {stars:,} | 🗣 {lang} | [GitHub]({repo['html_url']}) | 收录: 2026-08-22

---

## Metadata

| 字段 | 值 |
|------|-----|
| Stars | {stars:,} |
| 语言 | {lang} |
| Topics | {topics_str} |
| 生态 | {ecosystem} · {eco_name} |

## 内容分析

### 核心定位

{desc}

_（待补充：请阅读 README 后完善内容分析）_

## 阅读建议

- **适合人群**:
- **前置要求**:
- **预计耗时**:
- **配合阅读**:
{cross_section}
"""
    with open(detail_path, 'w', encoding='utf-8') as f:
        f.write(content)
    return detail_path


def add_to_json(repo, ecosystem):
    """将仓库追加到 guide_repos.json"""
    with open(JSON_PATH, encoding='utf-8') as f:
        repos = json.load(f)

    # 检查是否已存在
    for r in repos:
        if r['full_name'] == repo['full_name']:
            print(f'  [SKIP] 已存在于 JSON: {repo["full_name"]}')
            return False

    entry = {
        'full_name': repo['full_name'],
        'html_url': repo['html_url'],
        'description': repo['description'],
        'stargazers_count': repo['stargazers_count'],
        'language': repo['language'],
        'topics': repo['topics'],
        'pushed_at': repo['pushed_at'],
        'ecosystem_primary': ecosystem,
        'ecosystem_primary_name': ECOSYSTEMS[ecosystem]['name'],
        'ecosystem_cross': [],
        'ecosystem_cross_names': [],
    }
    repos.append(entry)
    with open(JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump(repos, f, ensure_ascii=False, indent=2)
    return True


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    # 过滤参数
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    dry_run = '--dry-run' in sys.argv
    forced_eco = None
    for a in sys.argv[1:]:
        if a.startswith('--eco='):
            forced_eco = a.split('=', 1)[1].upper()

    if not args:
        print(__doc__)
        sys.exit(1)

    arg = args[0]

    repo_name = parse_input(arg)
    if not repo_name:
        print(f'[ERR] 无法解析仓库地址: {arg}')
        print('  支持格式: https://github.com/owner/repo 或 owner/repo')
        sys.exit(1)

    print(f'[1/4] 抓取仓库信息: {repo_name}')
    repo = fetch_repo(repo_name)
    print(f'      -> {repo["full_name"]} ({repo["stargazers_count"]:,} stars, {repo["language"] or "N/A"})')

    print(f'[2/4] 匹配生态归属')
    if forced_eco:
        if forced_eco not in ECOSYSTEMS:
            print(f'      [ERR] 无效生态: {forced_eco}. 可选: {", ".join(ECOSYSTEMS.keys())}')
            sys.exit(1)
        eco = forced_eco
        print(f'      -> 手动指定: {eco} · {ECOSYSTEMS[eco]["name"]}')
    else:
        eco = guess_ecosystem(repo.get('topics', []), repo.get('description', ''))
        print(f'      -> 自动匹配: {eco} · {ECOSYSTEMS[eco]["name"]}')

    if dry_run:
        print(f'[3/4] [DRY-RUN] 跳过详情页生成')
        print(f'[4/4] [DRY-RUN] 跳过 JSON 更新')
        print(f'\n[DRY-RUN] 完成。实际执行将写入:')
        print(f'  - guide/repositories/{repo["full_name"].replace("/", "_")}.md')
        print(f'  - guide/guide_repos.json (+1 repo)')
        sys.exit(0)

    print(f'[3/4] 生成详情页: repositories/{repo["full_name"].replace("/", "_")}.md')
    detail_path = generate_detail_file(repo, eco)
    print(f'      -> {detail_path}')

    print(f'[4/4] 更新 guide_repos.json')
    added = add_to_json(repo, eco)
    if added:
        print(f'      -> 已添加: {repo["full_name"]}')
    else:
        # 详情页已建但 JSON 已存在时的数据同步
        print(f'      -> JSON 已存在，跳过')
        sys.exit(1)

    print(f'''
[OK] 收录完成!
  - 详情页: {detail_path}
  - JSON: {JSON_PATH}

下一步建议:
  1. 编辑详情页,完善「内容分析」和「阅读建议」
  2. 更新对应的分类文件: guide/categories/{ECOSYSTEMS[eco]["file"]}
  3. 更新 guide/ecosystem-index.md（按生态/Stars 表）
  4. 运行 python3 scripts/sync_stars.py 同步 Stars
''')


if __name__ == '__main__':
    main()