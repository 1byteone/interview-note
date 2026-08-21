#!/usr/bin/env python3
"""导出 tutorials 下所有 SVG 为 PNG（Playwright 直接渲染，无需 subprocess，避免 Python 环境不一致）"""
import os, pathlib
from playwright.sync_api import sync_playwright

tutorials_dir = pathlib.Path("D:/code/codeAgentDev/interview-note/projects/tutorials")

svgs = []
for root, _dirs, files in os.walk(tutorials_dir):
    if os.path.basename(root) == "assets":
        for f in files:
            if f.endswith(".svg"):
                svgs.append(pathlib.Path(root) / f)
svgs.sort()

print(f"Found {len(svgs)} SVG files to export")
ok = 0
with sync_playwright() as p:
    browser = p.chromium.launch()
    for svg in svgs:
        out = svg.with_suffix(".png")
        print(f"  [..] exporting: {svg.name}")
        try:
            page = browser.new_page(device_scale_factor=2)
            page.goto(f"file://{svg.resolve()}")
            page.wait_for_load_state("networkidle")
            page.locator("svg").first.screenshot(path=str(out), omit_background=True)
            page.close()
            ok += 1
            print(f"        -> {out.name}")
        except Exception as e:
            print(f"  [ERR] failed: {e}")
    browser.close()
print(f"\nExported {ok}/{len(svgs)} PNG files")