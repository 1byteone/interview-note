#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描所有 Markdown 文件的相对链接，报告失效链接（排除代码块）。"""
import os
import re

broken = []
total_links = 0
code_block_re = re.compile(r'^```')

for root, dirs, files in os.walk('.'):
    parts = root.replace('\\', '/').split('/')
    if '.git' in parts or '.claude' in parts:
        continue
    for f in files:
        if not f.endswith('.md'):
            continue
        path = os.path.join(root, f)
        with open(path, encoding='utf-8') as fh:
            lines = fh.readlines()
        in_code = False
        for line in lines:
            if code_block_re.match(line.strip()):
                in_code = not in_code
                continue
            if in_code:
                continue
            for m in re.finditer(r'\[[^\]]*\]\(([^)]+)\)', line):
                link = m.group(1)
                if link.startswith(('http', '#', 'mailto')) or link.startswith('!'):
                    continue
                target = link.split('#')[0]
                if not target:
                    continue
                total_links += 1
                resolved = os.path.normpath(os.path.join(os.path.dirname(path), target))
                if not os.path.exists(resolved):
                    broken.append((path, link))

print(f'链接总数: {total_links}, 断链: {len(broken)}')
for p, l in broken[:60]:
    print(f'  [{p}] -> {l}')
