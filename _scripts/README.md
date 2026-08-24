# Scripts

辅助脚本工具集，用于项目维护、内容生成和自动化任务。

## 目录

| 脚本 | 说明 |
|------|------|
| `svg-to-png.py` | 将 SVG 架构图批量转换为 PNG 图片（支持中文渲染） |
| `sync_stars.py` | 同步 3-ecosystem 仓库 Stars 数据（JSON + 详情页 + 索引） |
| `check_links.py` | 检查文档中的链接是否有效 |
| `validate.py` | 校验 3-ecosystem 数据一致性（JSON ↔ 详情页 ↔ 索引） |
| `requirements-render.txt` | 渲染脚本的 Python 依赖 |

---

## SVG → PNG 转换

使用 [Playwright](https://playwright.dev/python/) 将技术栈架构图从 SVG 格式转换为高分辨率 PNG 图片，确保中文文本正确渲染。

### 适用场景

项目中的 `.svg` 架构图（如 JVM 内存结构、Spring Boot 启动流程、B+ 树索引等）在 Markdown 预览和某些文档系统中无法直接显示。转换为 PNG 后可嵌入到 README 或文档中。

### 前置条件

```bash
# 安装 Python 依赖
pip install -r requirements-render.txt

# 安装 Chromium 浏览器（Playwright 需要）
playwright install chromium
```

> **注意**：`playwright install chromium` 会在系统上安装专用的 Chromium 浏览器，大约 300MB。如果您已经安装了 Chrome/Edge，Playwright 仍需其自带的 Chromium 版本以保证兼容性。

### 使用方法

```bash
# 从项目根目录运行
python scripts/svg-to-png.py
```

### 输出说明

- 每个 SVG 文件会在**同目录**下生成对应的 PNG 文件
- 输出分辨率：原 SVG viewBox 尺寸的 **2x**（Retina 清晰度）
- 字体：自动注入 Noto Sans SC 字体回退，确保中文正确渲染
- 不支持：包含本地图片引用的 SVG（跳过）

### 示例输出

```
============================================================
  SVG -> PNG Converter  (CJK-ready, Playwright)
============================================================

  Found 12 SVGs  |  0 missing (of 12 total)

  [ 1/12] learn/02-java/02-core/examples/assets/jvm-memory-architecture.svg ... [OK]
          jvm-memory-architecture.png  (960x780 -> 1920x1560 @2x, 245 KB)
  ...
  [12/12] learn/projects/ai-mall/assets/ai-mall-architecture.svg ... [OK]
          ai-mall-architecture.png  (960x900 -> 1920x1800 @2x, 310 KB)

------------------------------------------------------------
  Summary: 12 converted  |  0 failed  |  0 skipped
------------------------------------------------------------
```

### 转换的 SVG 列表

| # | 技术栈 | 架构图 | 尺寸 |
|---|--------|--------|------|
| 1 | Java | JVM 内存结构 | 960×780 |
| 2 | Spring Boot | 启动流程 | 800×1200 |
| 3 | MySQL | B+ 树索引结构 | 960×800 |
| 4 | Redis | 集群架构 | 960×780 |
| 5 | RocketMQ | 事务消息流程 | 960×820 |
| 6 | Elasticsearch | 倒排索引结构 | 960×760 |
| 7 | Docker | 多阶段构建 | 960×800 |
| 8 | LangChain | Agent 工作流程 | 960×760 |
| 9 | RAG | 检索增强生成流水线 | 960×820 |
| 10 | OpenAI | 函数调用流程 | 960×780 |
| 11 | 基础设施 | 微服务架构总览 | 960×780 |
| 12 | AI 商城 | 总体架构 | 960×900 |

### 常见问题

**Q: 中文显示为方块（tofu）？**
A: 脚本会自动注入 Noto Sans SC 字体回退。如果仍然显示方块，请检查网络连接（Google Fonts 需要外网访问），或确认系统安装了 Noto Sans SC 字体。

**Q: 输出 PNG 太大？**
A: 脚本使用 2x 设备像素比 (deviceScaleFactor=2) 以获得 Retina 清晰度。如需缩小文件体积，可修改脚本中的 `device_scale_factor` 参数为 1。

**Q: 如何处理自定义 SVG？**
A: 将 SVG 文件放入 `learn/**/assets/` 目录后，运行脚本即可自动扫描并转换（脚本当前使用预定义列表，如需自动扫描所有 SVG 可修改 `TARGET_SVGS` 为 `os.walk` 遍历）。