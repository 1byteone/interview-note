#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第一轮审查：渲染与结构问题。

检查所有 Markdown 文件：
1. 断链（相对链接指向不存在的文件）
2. 失效图片引用（![](...) 指向不存在的图片）
3. 未闭合的代码块（``` 数量为奇数）
4. 表格格式错误（表头后无分隔行、列数不一致）
5. 编码/乱码问题（无效 UTF-8）
"""
import os
import re

code_block_re = re.compile(r'^```')
link_re = re.compile(r'\[([^\]]*)\]\(([^)]+)\)')
img_re = re.compile(r'!\[([^\]]*)\]\(([^)]+)\)')

issues = {'broken_links': [], 'broken_images': [], 'unclosed_code': [], 'bad_tables': [], 'encoding': []}

def check_encoding(path):
    """检查文件是否为有效 UTF-8，且不含常见乱码标记。"""
    with open(path, 'rb') as f:
        raw = f.read()
    try:
        raw.decode('utf-8')
    except UnicodeDecodeError as e:
        issues['encoding'].append((path, f'无效 UTF-8: {e}'))
        return
    # 检查常见乱码
    text = raw.decode('utf-8', errors='ignore')
    garbled_patterns = [
        ('锘', 'UTF-8 BOM 乱码'),
        ('Ã©', 'Mojibake 双重编码'),
        ('�', '替换字符 U+FFFD'),
    ]
    for pat, desc in garbled_patterns:
        if pat in text:
            issues['encoding'].append((path, f'疑似乱码: {desc} (出现 {text.count(pat)} 次)'))
            return

for root, dirs, files in os.walk('.'):
    parts = root.replace('\\', '/').split('/')
    if '.git' in parts or '.claude' in parts:
        continue
    for f in files:
        if not f.endswith('.md'):
            continue
        path = os.path.join(root, f)
        check_encoding(path)

        with open(path, encoding='utf-8', errors='ignore') as fh:
            lines = fh.readlines()
        src_dir = os.path.dirname(path)
        in_code = False
        fence_count = 0
        table_lines = []  # 收集连续表格行

        for i, line in enumerate(lines):
            stripped = line.strip()

            # 代码块 fenc 统计
            if code_block_re.match(stripped):
                in_code = not in_code
                fence_count += 1
                continue

            if in_code:
                continue

            # 链接检查（排除代码块内）
            for m in link_re.finditer(line):
                # 跳过图片（单独处理）
                if line[max(0, m.start()-1)] == '!':
                    continue
                link = m.group(2)
                if link.startswith(('http', '#', 'mailto')) or link.startswith('!'):
                    continue
                target = link.split('#')[0]
                if not target:
                    continue
                resolved = os.path.normpath(os.path.join(src_dir, target))
                if not os.path.exists(resolved):
                    issues['broken_links'].append((path, i+1, link))

            # 图片检查
            for m in img_re.finditer(line):
                src = m.group(2)
                if src.startswith(('http', 'data:')):
                    continue
                target = src.split('#')[0]
                if not target:
                    continue
                resolved = os.path.normpath(os.path.join(src_dir, target))
                if not os.path.exists(resolved):
                    issues['broken_images'].append((path, i+1, src))

        # 代码块闭合检查（跳过代码块内的 ```，我们只统计行首）
        if fence_count % 2 != 0:
            issues['unclosed_code'].append((path, f'代码块未闭合（出现 {fence_count} 个 ```）'))

# 表格检查：扫描连续表格行，验证列数一致性
# 表格：| a | b | c |
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
        in_table = False
        table_cols = 0
        for i, line in enumerate(lines):
            stripped = line.strip()
            if code_block_re.match(stripped):
                in_code = not in_code
                continue
            if in_code:
                continue
            is_table_row = stripped.startswith('|') and stripped.endswith('|')
            if is_table_row:
                cols = len([c for c in stripped.split('|')[1:-1]])
                if not in_table:
                    in_table = True
                    table_cols = cols
                elif stripped.replace('|', '').replace('-', '').replace(':', '').replace(' ', '').strip() == '':
                    # 分隔行，保留 table_cols
                    continue
                elif cols != table_cols:
                    issues['bad_tables'].append((path, i+1, f'表头 {table_cols} 列，此行 {cols} 列'))
            else:
                in_table = False

# 输出报告
print('=' * 60)
print(f'第一轮审查：渲染与结构问题')
print('=' * 60)

for category, items in issues.items():
    names = {
        'broken_links': '断链',
        'broken_images': '失效图片引用',
        'unclosed_code': '未闭合代码块',
        'bad_tables': '表格列数不一致',
        'encoding': '编码/乱码问题',
    }
    print(f'\n📌 {names[category]}：{len(items)}')
    seen = set()
    for item in items:
        key = (item[0], item[1]) if len(item) >= 2 else item[0]
        if key in seen:
            continue
        seen.add(key)
        if category == 'breaking_links' and len(item) >= 3:
            print(f'  {item[0]}:{item[1]} → {item[2]}')
        elif len(item) >= 3:
            print(f'  {item[0]}:{item[1]} → {item[2]}')
        elif len(item) == 2:
            print(f'  {item[0]} → {item[1]}')