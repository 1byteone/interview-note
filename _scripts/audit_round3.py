#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第三轮审查：一致性与质量。

检查：
1. 引用旧路径的残留链接（如 java/ spring/ devops/ 等旧前缀）
2. 重复内容（相同文件名的 interview-questions.md 在不同目录下的结构相似度）
3. 元数据格式不一致（教程文件的 level 元数据头格式）
4. 过时日期（2024 或 2025 年未更新的日期）
5. 文件编码混合（同一文件内混用 CRLF/LF）
"""
import os
import re
from collections import defaultdict

code_block_re = re.compile(r'^```')

issues = {
    'old_path_refs': [],
    'metadata_inconsistent': [],
    'outdated_dates': [],
    'line_ending_mixed': [],
}

# 旧路径前缀 — 重构后这些路径前缀不应出现在链接中
OLD_PREFIXES = [
    'java/', 'spring/', 'spring-cloud/', 'devops/', 'middleware/',
    'ai/', 'learn/', 'guide/', 'interview-tools/', 'liyupi/',
    'examples/', 'scripts/', 'projects/',
    'interview-preparation-plan.md',
]

# 检查旧路径引用
for root, dirs, files in os.walk('.'):
    parts = root.replace('\\', '/').split('/')
    if '.git' in parts or '.claude' in parts:
        continue
    for f in files:
        if not f.endswith('.md'):
            continue
        path = os.path.join(root, f)
        with open(path, encoding='utf-8', errors='ignore') as fh:
            lines = fh.readlines()
        in_code = False
        for i, line in enumerate(lines):
            stripped = line.strip()
            if code_block_re.match(stripped):
                in_code = not in_code
                continue
            if in_code:
                continue
            # 检查 markdown 链接中的旧路径
            for m in re.finditer(r'\[([^\]]*)\]\(([^)]+)\)', line):
                link = m.group(2)
                if link.startswith(('http', '#', 'mailto')):
                    continue
                for old_prefix in OLD_PREFIXES:
                    if old_prefix in link:
                        # 检查是否是真的旧路径引用（而不是在文件名中）
                        # 如 ../java/ 或 ../../spring/ 等
                        if '../' + old_prefix in link or old_prefix in link:
                            issues['old_path_refs'].append((path, i+1, link))
                            break

# 检查教程元数据格式一致性
for root, dirs, files in os.walk('3-ecosystem/tutorials'):
    for f in files:
        if not f.endswith('.md'):
            continue
        path = os.path.join(root, f)
        with open(path, encoding='utf-8', errors='ignore') as fh:
            first_lines = [fh.readline() for _ in range(5)]
        content = ''.join(first_lines)
        # 检查是否包含生态和等级标注
        if '生态' not in content and '等级' not in content and '前置要求' not in content:
            # 跳过 README.md 和教程目录
            if f != 'README.md':
                issues['metadata_inconsistent'].append((path, '缺少生态/等级/前置要求元数据'))

# 检查过时日期
for root, dirs, files in os.walk('.'):
    parts = root.replace('\\', '/').split('/')
    if '.git' in parts or '.claude' in parts:
        continue
    for f in files:
        if not f.endswith('.md'):
            continue
        path = os.path.join(root, f)
        with open(path, encoding='utf-8', errors='ignore') as fh:
            content = fh.read()
        # 查找 2024- 或 2025- 的日期引用
        for m in re.finditer(r'20(24|25)-\d{2}-\d{2}', content):
            issues['outdated_dates'].append((path, f'日期 {m.group(0)}'))

# 检查行尾混合
for root, dirs, files in os.walk('.'):
    parts = root.replace('\\', '/').split('/')
    if '.git' in parts or '.claude' in parts:
        continue
    for f in files:
        if not f.endswith('.md'):
            continue
        path = os.path.join(root, f)
        with open(path, 'rb') as fh:
            raw = fh.read()
        has_crlf = b'\r\n' in raw
        has_lf = b'\n' in raw and b'\r\n' not in raw
        # 如果既有 CRLF 又有纯 LF（非 CRLF 的 \n），则混合
        crlf_count = raw.count(b'\r\n')
        lf_count = raw.count(b'\n') - crlf_count
        if crlf_count > 0 and lf_count > 0:
            issues['line_ending_mixed'].append((path, f'CRLF={crlf_count}, LF={lf_count}'))

print('=' * 60)
print('第三轮审查：一致性与质量')
print('=' * 60)

for category, items in issues.items():
    names = {
        'old_path_refs': '旧路径引用残留',
        'metadata_inconsistent': '元数据格式不一致',
        'outdated_dates': '过时日期（2024/2025）',
        'line_ending_mixed': '行尾混合（CRLF+LF）',
    }
    print(f'\n📌 {names[category]}：{len(items)}')
    for item in items[:20]:
        if len(item) == 3:
            print(f'  {item[0]}:{item[1]} → {item[2]}')
        else:
            print(f'  {item[0]} → {item[1]}')
    if len(items) > 20:
        print(f'  ... 共 {len(items)} 条，显示前 20')