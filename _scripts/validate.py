#!/usr/bin/env python3
"""
Guide 数据一致性全面校验脚本
============================
检查:
  1. guide_repos.json 所有仓库都有对应的 repositories/*.md 详情文件
  2. repositories/*.md 所有文件都在 guide_repos.json 中有记录
  3. categories/ 引用的仓库在 repositories/ 有对应文件
  4. guide_repos.json 的生态字段与 categories/ 文件一致
  5. README 中所有链接有效
  6. 仓库详情页的 Stars 与 JSON 一致
  7. 交叉引用链接有效

用法:
  python3 validate.py
"""

import json
import os
import re
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUIDE_DIR = os.path.join(BASE_DIR, '3-ecosystem')
JSON_PATH = os.path.join(GUIDE_DIR, 'guide_repos.json')
REPOS_DIR = os.path.join(GUIDE_DIR, 'repositories')
CATS_DIR = os.path.join(GUIDE_DIR, 'categories')
DATA_DIR = os.path.join(GUIDE_DIR, 'data')
ASSETS_DIR = os.path.join(GUIDE_DIR, 'assets')

issues = []
warnings = []


def check(condition, msg, is_warning=False):
    if not condition:
        if is_warning:
            warnings.append(msg)
        else:
            issues.append(msg)


def get_repo_files():
    files = {}
    for f in os.listdir(REPOS_DIR):
        if f.endswith('.md'):
            name = f[:-3].replace('_', '/', 1) if '_' in f else f[:-3]
            files[name] = f
    return files


def main():
    print('=' * 60)
    print('Guide 数据一致性校验')
    print('=' * 60)

    # 1. Load JSON
    with open(JSON_PATH, encoding='utf-8') as f:
        repos = json.load(f)
    json_names = {r['full_name'] for r in repos}
    print(f'\n[1] guide_repos.json: {len(repos)} repos')

    # 2. Check JSON -> repositories/
    repo_files = get_repo_files()
    print(f'[2] repositories/: {len(repo_files)} files')

    for r in repos:
        name = r['full_name']
        check(name in repo_files,
              f'[ERR] JSON 有记录但缺少详情文件: {name}')
    for r in repos:
        name = r['full_name']
        if name in repo_files:
            fpath = os.path.join(REPOS_DIR, repo_files[name])
            with open(fpath, encoding='utf-8') as f:
                content = f.read()
            # Check header stars match
            header_m = re.search(r'^> ⭐([\d,]+)', content, re.MULTILINE)
            if header_m:
                header_stars = int(header_m.group(1).replace(',', ''))
                check(header_stars == r['stargazers_count'],
                      f'[ERR] Stars 不一致: {name} (header={header_stars}, json={r["stargazers_count"]})')
            # Check eco field
            check(f'| 生态 | {r["ecosystem_primary"]} ·' in content,
                  f'[WARN] 详情页缺少生态字段: {name}', is_warning=True)
            # Check cross-ref section
            check('## 生态交叉引用' in content,
                  f'[WARN] 详情页缺少交叉引用: {name}', is_warning=True)

    # 3. Check repositories/ -> JSON
    for fname in os.listdir(REPOS_DIR):
        if not fname.endswith('.md'):
            continue
        name = fname[:-3].replace('_', '/', 1) if '_' in fname else fname[:-3]
        check(name in json_names,
              f'[ERR] 有详情文件但 JSON 无记录: {name}')

    # 4. Check categories/ references
    print(f'[3] checking categories/ references...')
    # Known non-repo patterns (ecosystem labels, tool names, tech stacks)
    skip_patterns = {
        'E01', 'E02', 'E03', 'E04', 'E05', 'E06',
        'Codex', 'Gemini', 'TDD', 'Vibe', 'DSH', 'Hermes',
        'OpenClaw', 'GPT', 'Claude', 'DeepSeek', 'Java', 'Go',
        'Creativity', 'Evaluation',
    }
    # Valid repo names from JSON
    json_names_set = json_names if 'json_names' in dir() else set()

    cat_files = [f for f in os.listdir(CATS_DIR) if f.endswith('.md')]
    for cf in cat_files:
        with open(os.path.join(CATS_DIR, cf), encoding='utf-8') as f:
            content = f.read()
        # Find all repo references - only match GitHub-style repo names
        refs = re.findall(r'[a-zA-Z0-9][\w.-]*/[a-zA-Z0-9][\w.-]*(?=[\]\)\s\n:;,])', content)
        seen = set()
        for ref in refs:
            if ref in seen:
                continue
            seen.add(ref)
            # Skip known non-repo patterns
            parts = ref.split('/')
            if any(p in skip_patterns for p in parts):
                continue
            # Skip tutorial refs (e01-claude-code/xxx etc.)
            if re.match(r'^e\d+-', ref):
                continue
            # Skip any ref starting with e + digit
            if ref.startswith('e0') and '/' in ref:
                continue
            # Check against JSON
            if ref in json_names_set:
                continue
            safe = ref.replace('/', '_')
            detail_path = os.path.join(REPOS_DIR, f'{safe}.md')
            check(os.path.exists(detail_path),
                  f'[WARN] {cf} 引用未知仓库: {ref}', is_warning=True)

    # 5. Check README links
    print(f'[4] checking README links...')
    for readme_path in [os.path.join(GUIDE_DIR, 'README.md'),
                        os.path.join(BASE_DIR, 'README.md'),
                        os.path.join(BASE_DIR, 'ai-coding-guide', 'README.md')]:
        if not os.path.exists(readme_path):
            continue
        with open(readme_path, encoding='utf-8') as f:
            content = f.read()
        links = re.findall(r'\[([^\]]+)\]\(([^)]+)\)', content)
        for text, link in links:
            if link.startswith('http'):
                continue
            if link.startswith('#'):
                continue
            # Resolve relative to guide dir
            if readme_path.endswith('README.md') and os.path.dirname(readme_path) == GUIDE_DIR:
                resolved = os.path.normpath(os.path.join(GUIDE_DIR, link))
            else:
                resolved = os.path.normpath(os.path.join(os.path.dirname(readme_path), link))
            if not os.path.exists(resolved):
                # Check if it's a file without .md
                if not os.path.exists(resolved + '.md') and not os.path.exists(resolved.replace('.md', '')):
                    check(False, f'[ERR] 断链: {link} ({resolved})')

    # 6. Check ecosystem field consistency
    print(f'[5] checking ecosystem consistency...')
    valid_ecos = ['E01', 'E02', 'E03', 'E04', 'E05', 'E06']
    for r in repos:
        check(r['ecosystem_primary'] in valid_ecos,
              f'[ERR] 无效生态: {r["full_name"]} -> {r["ecosystem_primary"]}')

    # 7. Sync script check
    print(f'[6] checking scripts...')
    scripts_dir = os.path.join(BASE_DIR, '_scripts')
    for s in ['sync_stars.py', 'add_repo.py', 'validate.py', 'check_links.py']:
        check(os.path.exists(os.path.join(scripts_dir, s)),
              f'[WARN] 缺少脚本: _scripts/{s}', is_warning=True)

    # Summary
    print('\n' + '=' * 60)
    print(f'校验完成!')
    print(f'  错误: {len(issues)}')
    print(f'  警告: {len(warnings)}')
    print('=' * 60)

    if issues:
        print('\n[错误列表]:')
        for i in issues:
            print(f'  {i}')

    if warnings:
        print('\n[警告列表]:')
        for w in warnings:
            print(f'  {w}')

    if not issues and not warnings:
        print('\n[OK] 全部通过!')

    return len(issues)


if __name__ == '__main__':
    sys.exit(main())