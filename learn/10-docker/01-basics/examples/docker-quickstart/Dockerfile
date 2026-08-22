# ============================================
# Docker QuickStart — Python Flask + Redis
# 多阶段构建示例
# ============================================

# ---------- Stage 1: Builder ----------
FROM python:3.11-slim AS builder

WORKDIR /app

# 复制依赖文件并安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ---------- Stage 2: Runtime ----------
FROM python:3.11-slim

WORKDIR /app

# 从 builder 阶段复制已安装的 site-packages
COPY --from=builder /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
COPY --from=builder /usr/local/bin /usr/local/bin

# 复制应用代码
COPY app.py .

EXPOSE 5000

CMD ["python", "app.py"]