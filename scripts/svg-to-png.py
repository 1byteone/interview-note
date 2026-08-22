#!/usr/bin/env python3
"""
SVG to PNG converter with CJK font support.
Uses Playwright for browser-based rendering.

Usage:
    pip install -r requirements-render.txt
    playwright install chromium
    python scripts/svg-to-png.py
"""

import os
import re
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
ROOT = Path(__file__).resolve().parent.parent
LEARN_DIR = ROOT / "learn"

# The 12 target SVGs (relative to ROOT)
TARGET_SVGS = [
    "learn/02-java/02-core/examples/assets/jvm-memory-architecture.svg",
    "learn/03-spring-boot/02-core/examples/assets/spring-boot-startup-flow.svg",
    "learn/06-mysql/02-core/examples/assets/b-plus-tree-index.svg",
    "learn/07-redis/02-core/examples/assets/redis-cluster-architecture.svg",
    "learn/08-rocketmq/02-core/examples/assets/rocketmq-transaction-message.svg",
    "learn/09-elasticsearch/02-core/examples/assets/es-inverted-index.svg",
    "learn/10-docker/02-core/examples/assets/docker-multi-stage-build.svg",
    "learn/14-langchain/02-core/examples/assets/langchain-agent-flow.svg",
    "learn/15-rag/02-core/examples/assets/rag-pipeline.svg",
    "learn/16-openai/02-core/examples/assets/openai-function-calling.svg",
    "learn/12-infrastructure/02-core/examples/assets/microservice-architecture.svg",
    "learn/projects/ai-mall/assets/ai-mall-architecture.svg",
]

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _inject_cjk_font(svg: str) -> str:
    """Prepend 'Noto Sans SC' to every font-family value that lacks it.

    The SVGs already use Microsoft YaHei / PingFang SC (fine on Windows), but
    adding Noto Sans SC guarantees correct CJK rendering on Linux/macOS too.
    """
    def _replacer(m):
        val = m.group(1)
        if "Noto Sans SC" not in val:
            # Insert before the generic fallback (sans-serif, serif, monospace)
            parts = val.rsplit(",", 1)
            if len(parts) == 2 and parts[1].strip().startswith(
                ("sans-serif", "serif", "monospace")
            ):
                val = parts[0] + ", 'Noto Sans SC', " + parts[1].strip()
            else:
                val = "'Noto Sans SC', " + val
        return f'font-family="{val}"'

    return re.sub(r'font-family="([^"]+)"', _replacer, svg)


def _parse_viewbox(svg: str):
    """Return (width, height) from SVG viewBox attribute."""
    m = re.search(r'viewBox="(\d+)\s+(\d+)\s+(\d+)\s+(\d+)"', svg)
    if m:
        return int(m.group(3)), int(m.group(4))
    return None, None


def _has_local_image_refs(svg: str) -> bool:
    """Return True if the SVG references external image files (not URLs)."""
    # Check for <image> tags
    if re.search(r'<image\s', svg, re.IGNORECASE):
        return True
    # Check for href/src pointing to local files
    pattern = r'(?:href|src)\s*=\s*"(?!(?:https?://|data:|#))[^"]+\.(?:png|jpg|jpeg|gif|svg)"'
    if re.search(pattern, svg, re.IGNORECASE):
        return True
    return False


def _build_html(svg_content: str, w: int, h: int) -> str:
    """Wrap SVG in an HTML page with Google Fonts CJK support."""
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet"
      href="https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;700&display=swap">
<style>
  * {{ margin: 0; padding: 0; box-sizing: border-box; }}
  body {{ background: white; }}
  svg {{ width: {w}px; height: {h}px; display: block; }}
</style>
</head>
<body>
{svg_content}
</body>
</html>"""


# ---------------------------------------------------------------------------
# Conversion
# ---------------------------------------------------------------------------

def convert_svg(svg_path: str, browser) -> tuple:
    """Convert a single SVG file to PNG.

    Returns (success: bool, message: str).
    """
    path = Path(svg_path)
    if not path.exists():
        return False, "File not found"

    raw = path.read_text(encoding="utf-8")

    # Skip SVGs that reference external local images
    if _has_local_image_refs(raw):
        return False, "Contains local image references, skipping"

    # Inject CJK font fallback into font-family attributes
    raw = _inject_cjk_font(raw)

    w, h = _parse_viewbox(raw)
    if not w or not h:
        return False, "Could not parse viewBox"

    html = _build_html(raw, w, h)
    png_path = path.with_suffix(".png")

    # Create a page sized to the SVG's viewBox, at 2x device scale for retina crispness
    page = browser.new_page(
        viewport={"width": w, "height": h},
        device_scale_factor=2,
    )
    try:
        page.set_content(html, wait_until="load", timeout=30000)
        # Allow extra time for Google Fonts to load & render
        page.wait_for_timeout(2000)
        page.screenshot(path=str(png_path), full_page=False)
        size_kb = png_path.stat().st_size / 1024
        return True, f"{png_path.name}  ({w}x{h} -> {w*2}x{h*2} @2x, {size_kb:.0f} KB)"
    except Exception as e:
        return False, str(e)
    finally:
        page.close()


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    print("=" * 60)
    print("  SVG -> PNG Converter  (CJK-ready, Playwright)")
    print("=" * 60)
    print()

    # Verify Playwright availability
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("[ERR] Playwright is not installed.")
        print("      Run:  pip install -r requirements-render.txt")
        print("      Then: playwright install chromium")
        sys.exit(1)

    # Collect existing targets
    targets: list[tuple[str, Path]] = []
    missing = 0
    for rel in TARGET_SVGS:
        abs_path = ROOT / rel
        if abs_path.exists():
            targets.append((rel, abs_path))
        else:
            print(f"  [SKIP] {rel}  (file not found)")
            missing += 1

    if not targets:
        print("[..]  No SVG files to convert.")
        sys.exit(0)

    print(f"  Found {len(targets)} SVGs  |  {missing} missing (of {len(TARGET_SVGS)} total)")
    print()

    converted = 0
    failed = 0

    with sync_playwright() as p:
        browser = p.chromium.launch()
        try:
            for idx, (rel, abs_path) in enumerate(targets, 1):
                label = f"[{idx:2d}/{len(targets)}]"
                print(f"  {label} {rel} ...", end="", flush=True)

                success, msg = convert_svg(str(abs_path), browser)
                if success:
                    print(f" [OK]")
                    print(f"          {msg}")
                    converted += 1
                else:
                    print(f" [ERR]")
                    print(f"          {msg}")
                    failed += 1
        finally:
            browser.close()

    print()
    print("-" * 60)
    print(f"  Summary: {converted} converted  |  {failed} failed  |  {missing} skipped")
    print("-" * 60)


if __name__ == "__main__":
    main()