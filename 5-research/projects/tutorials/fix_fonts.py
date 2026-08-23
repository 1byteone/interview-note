#!/usr/bin/env python3
"""批量修复 tutorials 下所有 SVG 的中文字体问题：添加 Noto Sans SC 作为 CJK fallback"""
import os
import pathlib
import re

tutorials_dir = pathlib.Path("D:/code/codeAgentDev/interview-note/projects/tutorials")

# 用 os.walk 收集 assets 目录下的所有 .svg（比 rglob 可靠）
svgs = []
for root, _dirs, files in os.walk(tutorials_dir):
    if os.path.basename(root) == "assets":
        for f in files:
            if f.endswith(".svg"):
                svgs.append(pathlib.Path(root) / f)
svgs.sort()
print(f"Found {len(svgs)} SVG files\n")

import_old = "family=Geist+Mono:wght@400;500;600&amp;"
import_new = "family=Geist+Mono:wght@400;500;600&amp;family=Noto+Sans+SC:wght@400;500;700&amp;"

def fix_family(text: str) -> str:
    # 兼容 'Geist',sans-serif 与 'Geist', sans-serif 两种写法
    text = re.sub(
        r"font-family=\"'Geist'\s*,\s*sans-serif\"",
        "font-family=\"'Geist','Noto Sans SC',sans-serif\"",
        text,
    )
    text = re.sub(
        r"font-family=\"'Geist Mono'\s*,\s*monospace\"",
        "font-family=\"'Geist Mono','Noto Sans SC',monospace\"",
        text,
    )
    return text

fixed = 0
for svg_path in svgs:
    original = svg_path.read_text("utf-8")
    text = original
    if "Noto+Sans+SC" not in text:
        text = text.replace(import_old, import_new)
    text = fix_family(text)
    if text == original:
        print(f"  [SKIP] already OK: {svg_path.name}")
        continue
    svg_path.write_text(text, "utf-8")
    fixed += 1
    print(f"  [OK] fixed: {svg_path.name}")

print(f"\nFixed {fixed}/{len(svgs)} SVG files")