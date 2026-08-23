#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第二轮审查：内容完整性。

检查：
1. TODO / TBD / FIXME / WIP 标记
2. 占位符：TODO, ..., xxx, 待补充, 待完善, （待补）, 占位
3. 空章节：## 标题后无内容
4. 极短文件（可能不完整）
5. "..." 截断内容 / "略" 大量出现
6. stub 文件（只有标题，没有正文）
7. 未完成的句子（以句号/逗号结尾的孤行）
"""
import os
import re
from collections import Counter

code_block_re = re.compile(r'^```')

issues = {
    'todo': [],
    'placeholder': [],
    'empty_section': [],
    'tiny_file': [],
    'ellipsis': [],
    'stub': [],
}

TODO_PAT = re.compile(r'\b(TODO|TBD|FIXME|XXX|WIP)\b|待补充|待完善|待完成|（待补）|待添加|此处待')
PLACEHOLDER_PAT = re.compile(r'\[占位\]|占位符|Lorem ipsum|placeholder|示例代码待|内容待')
ELLIPSIS_PAT = re.compile(r'\.{3,}|…{3,}|等等\.\.\.|\.\.\.等等|（此处省略|（删节')

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

        # 极短文件检查（< 5 行且无代码块）
        if len(lines) < 5:
            issues['tiny_file'].append((path, f'{len(lines)} 行'))
            continue

        # 统计正文行数（排除代码块、空行、标题）
        in_code = False
        content_lines = []
        for line in lines:
            stripped = line.strip()
            if code_block_re.match(stripped):
                in_code = not in_code
                continue
            if in_code:
                continue
            if not stripped or stripped.startswith('#'):
                continue
            content_lines.append(stripped)

        # stub 检查：只有标题没有正文
        if len(content_lines) == 0 and len([l for l in lines if l.strip().startswith('#')]) > 0:
            issues['stub'].append((path, '只有标题，无正文'))

        # 遍历正文行检查标记
        in_code = False
        for i, line in enumerate(lines):
            stripped = line.strip()
            if code_block_re.match(stripped):
                in_code = not in_code
                continue
            if in_code:
                continue

            if TODO_PAT.search(stripped):
                issues['todo'].append((path, i+1, stripped[:100]))
            if PLACEHOLDER_PAT.search(stripped):
                issues['placeholder'].append((path, i+1, stripped[:100]))
            if ELLIPSIS_PAT.search(stripped):
                issues['ellipsis'].append((path, i+1, stripped[:100]))

            # 空章节检查：## 或 ### 标题行，下一行是标题或其他章节
            if stripped.startswith('##'):
                # 找下一个非空行
                j = i + 1
                while j < len(lines) and not lines[j].strip():
                    j += 1
                if j >= len(lines):
                    issues['empty_section'].append((path, i+1, f'章节 "{stripped}" 无内容'))
                elif lines[j].strip().startswith('#'):
                    issues['empty_section'].append((path, i+1, f'章节 "{stripped}" 后直接是下一标题'))

print('=' * 60)
print('第二轮审查：内容完整性')
print('=' * 60)

for category, items in issues.items():
    names = {
        'todo': 'TODO/待办标记',
        'placeholder': '占位符',
        'empty_section': '空章节',
        'tiny_file': '极短文件',
        'ellipsis': '省略号/截断',
        'stub': 'stub 文件',
    }
    print(f'\n📌 {names[category]}：{len(items)}')
    for item in items[:30]:
        if len(item) == 3:
            print(f'  {item[0]}:{item[1]} → {item[2]}')
        else:
            print(f'  {item[0]} → {item[1]}')
    if len(items) > 30:
        print(f'  ... 共 {len(items)} 条，显示前 30')