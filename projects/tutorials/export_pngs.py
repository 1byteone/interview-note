#!/usr/bin/env python3
"""导出 diagram-design HTML 为 PNG 并嵌入到 Markdown 教程中"""
import sys, pathlib, subprocess

def export_png(html_path: str, scale: int = 2):
    """用 Playwright 导出 HTML 中的 SVG 为 PNG"""
    src = pathlib.Path(html_path).resolve()
    out = src.with_suffix(".png")

    script = f"""
from playwright.sync_api import sync_playwright
import sys, pathlib
src, out = sys.argv[1], sys.argv[2]
scale = int(sys.argv[3]) if len(sys.argv) > 3 else 2
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(device_scale_factor=scale)
    page.goto(f"file://{{pathlib.Path(src).resolve()}}")
    page.wait_for_load_state("networkidle")
    page.locator("svg").first.screenshot(path=out, omit_background=True)
    browser.close()
"""
    subprocess.run(["python", "-c", script, str(src), str(out), str(scale)], check=True)
    print(f"  ✅ PNG: {out}")
    return out

if __name__ == "__main__":
    # 导出所有教程的 HTML 配图
    tutorials_dir = pathlib.Path("/d/code/codeAgentDev/interview-note/projects/tutorials")
    htmls = sorted(tutorials_dir.rglob("assets/*.html"))
    print(f"找到 {len(htmls)} 个 HTML 配图文件")
    for h in htmls:
        print(f"  ⏳ 导出: {h.name}")
        try:
            export_png(str(h))
        except Exception as e:
            print(f"  ❌ 失败: {e}")