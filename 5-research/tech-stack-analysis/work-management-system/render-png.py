#!/usr/bin/env python3
"""Convert all work-management-system HTML diagrams to PNG using Playwright."""
import os, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HTML_FILES = sorted(ROOT.glob("*.html"))

W, H, SCALE = 1200, 900, 2  # viewport width, default height, device scale

def convert(html_path, browser):
    name = html_path.stem
    png_path = html_path.with_suffix(".png")
    raw = html_path.read_text(encoding="utf-8")

    # Extract actual height from the .canvas div via JS
    page = browser.new_page(viewport={"width": W, "height": H}, device_scale_factor=SCALE)
    try:
        page.set_content(raw, wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(2000)  # let Google Fonts load

        # Measure canvas height
        canvas_h = page.evaluate("""() => {
            const el = document.querySelector('.canvas');
            return el ? el.scrollHeight + 80 : 1200;
        }""")
        page.set_viewport_size({"width": W, "height": min(canvas_h, 3000)})
        page.wait_for_timeout(500)
        page.screenshot(path=str(png_path), full_page=True)
        size_kb = png_path.stat().st_size / 1024
        print(f"  [OK] {name}.png  ({size_kb:.0f} KB, {canvas_h}px tall)")
        return True
    except Exception as e:
        print(f"  [ERR] {name}: {e}")
        return False
    finally:
        page.close()

def main():
    print("=" * 60)
    print("  HTML -> PNG Converter  (CJK-ready, Playwright)")
    print("=" * 60)

    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("[ERR] Playwright not installed.")
        sys.exit(1)

    print(f"\n  Found {len(HTML_FILES)} HTML files\n")

    with sync_playwright() as p:
        browser = p.chromium.launch()
        ok, fail = 0, 0
        try:
            for f in HTML_FILES:
                if convert(f, browser):
                    ok += 1
                else:
                    fail += 1
        finally:
            browser.close()

    print(f"\n  Summary: {ok} OK | {fail} failed")
    print("=" * 60)

if __name__ == "__main__":
    main()
