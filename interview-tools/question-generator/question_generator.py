#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Java & AI 项目面试题自动生成器
================================
扫描项目结构 → 识别技术栈 → 生成10种类型面试题 → 输出 Markdown

用法:
    python question_generator.py --project-path ./my-java-project
    python question_generator.py --project-path ./my-java-project --output ./output
    python question_generator.py --project-path ./my-java-project --type scenario,design
    python question_generator.py --project-path ./my-java-project --difficulty senior
    python question_generator.py --project-path ./my-java-project --format md

依赖: 仅标准库 (os, re, json, pathlib, argparse, datetime)
"""

import argparse
import json
import os
import re
from pathlib import Path

# ============================================================
# 1. 技术栈识别规则
# ============================================================

# Java 技术栈识别规则 (检测关键字 → 技术名 → 面试题)
JAVA_MARKERS = [
    {
        "keywords": ["spring-boot", "spring-boot-starter"],
        "tech": "Spring Boot",
        "questions": [
            ("Spring Boot 自动配置原理是什么？", "L1"),
            ("Spring Boot 启动流程是怎样的？", "L2"),
            ("如何自定义一个 Spring Boot Starter？", "L3"),
            ("Spring Boot 如何做性能优化？", "L3"),
            ("如何实现多环境配置？", "L2"),
        ],
    },
    {
        "keywords": ["spring-cloud", "spring-cloud-starter", "spring-cloud-alibaba"],
        "tech": "Spring Cloud",
        "questions": [
            ("微服务架构有哪些优缺点？", "L1"),
            ("微服务如何拆分？有哪些原则？", "L4"),
            ("如何实现服务治理（限流/熔断/降级）？", "L3"),
            ("微服务如何实现高可用？", "L4"),
        ],
    },
    {
        "keywords": ["nacos", "nacos-client", "nacos-config", "nacos-discovery"],
        "tech": "Nacos",
        "questions": [
            ("Nacos 的核心功能有哪些？", "L1"),
            ("Nacos 配置中心的动态刷新原理是什么？", "L2"),
            ("Nacos 集群如何部署？一致性如何保证？", "L3"),
            ("Nacos 作为注册中心为什么选 AP？配置中心为什么选 CP？", "L4"),
            ("服务注册与发现的原理是什么？", "L2"),
        ],
    },
    {
        "keywords": ["gateway", "spring-cloud-gateway"],
        "tech": "Gateway",
        "questions": [
            ("API 网关的作用是什么？", "L1"),
            ("Gateway 的过滤器有哪些类型？", "L2"),
            ("如何实现 Gateway 的动态路由？", "L3"),
            ("Gateway 如何实现限流？", "L3"),
            ("Gateway 的高可用如何设计？", "L4"),
        ],
    },
    {
        "keywords": ["openfeign", "feign", "spring-cloud-starter-openfeign"],
        "tech": "OpenFeign",
        "questions": [
            ("OpenFeign 是什么？为什么要用它？", "L1"),
            ("OpenFeign 的动态代理原理是什么？", "L2"),
            ("OpenFeign 如何配置超时和重试？", "L2"),
            ("OpenFeign 如何实现服务降级？", "L3"),
            ("OpenFeign 的拦截器怎么用？", "L3"),
        ],
    },
    {
        "keywords": ["sentinel", "sentinel-core", "sentinel-spring"],
        "tech": "Sentinel",
        "questions": [
            ("Sentinel 的主要功能有哪些？", "L1"),
            ("Sentinel 限流有哪些策略？", "L2"),
            ("Sentinel 熔断降级的原理是什么？", "L2"),
            ("Sentinel 热点参数限流如何实现？", "L3"),
            ("Sentinel 规则如何持久化？", "L3"),
            ("Sentinel 与 Gateway 集成如何限流？", "L4"),
        ],
    },
    {
        "keywords": ["seata", "seata-all", "seata-spring-boot-starter", "spring-cloud-starter-alibaba-seata"],
        "tech": "Seata",
        "questions": [
            ("什么是分布式事务？为什么需要？", "L1"),
            ("Seata 的 AT 模式原理是什么？", "L2"),
            ("AT、TCC、Saga、XA 如何选择？", "L3"),
            ("TCC 模式如何实现？", "L3"),
            ("除 Seata 外还有哪些最终一致性方案？", "L4"),
        ],
    },
    {
        "keywords": ["rocketmq", "rocketmq-spring-boot-starter", "rocketmq-client"],
        "tech": "RocketMQ",
        "questions": [
            ("RocketMQ 的核心组件有哪些？", "L1"),
            ("如何保证消息不丢失？", "L2"),
            ("RocketMQ 的消息存储结构是什么？", "L2"),
            ("事务消息的实现原理是什么？", "L3"),
            ("消息重复消费如何解决？", "L3"),
            ("消息堆积如何处理？", "L4"),
        ],
    },
    {
        "keywords": ["kafka", "spring-kafka"],
        "tech": "Kafka",
        "questions": [
            ("Kafka 的核心组件有哪些？", "L1"),
            ("Kafka 为什么吞吐量高？", "L2"),
            ("消息确认机制 (Ack) 是什么？", "L2"),
            ("如何保证消息不丢失？", "L3"),
            ("如何保证消息的顺序性？", "L3"),
            ("Kafka 和 RocketMQ 如何选型？", "L4"),
        ],
    },
    {
        "keywords": ["redis", "spring-boot-starter-data-redis", "lettuce", "jedis"],
        "tech": "Redis",
        "questions": [
            ("Redis 的数据结构有哪些？", "L1"),
            ("什么是缓存穿透、击穿、雪崩？", "L1"),
            ("Redis 的持久化机制有哪些？", "L2"),
            ("Redis 分布式锁如何实现？", "L2"),
            ("Redis Cluster 原理是什么？", "L2"),
            ("如何设计 Redis 缓存架构？", "L3"),
            ("如何处理 Redis 大 Key 问题？", "L3"),
            ("Redis 延迟队列如何实现？", "L3"),
        ],
    },
    {
        "keywords": ["redisson"],
        "tech": "Redisson",
        "questions": [
            ("Redisson 是什么？为什么用它？", "L1"),
            ("Redisson 锁的 WatchDog 机制是什么？", "L2"),
            ("Redisson 分布式锁的底层实现原理？", "L3"),
            ("Redisson 公平锁、读写锁、联锁、红锁区别？", "L4"),
        ],
    },
    {
        "keywords": ["mysql", "mysql-connector", "mybatis"],
        "tech": "MySQL / MyBatis",
        "questions": [
            ("MySQL 索引的原理是什么？", "L1"),
            ("事务的隔离级别有哪些？", "L2"),
            ("MVCC 是如何实现的？", "L2"),
            ("什么是索引失效？常见场景有哪些？", "L2"),
            ("如何优化慢 SQL？", "L3"),
            ("如何设计 MySQL 高可用架构？", "L3"),
            ("百万数据如何分库分表？", "L3"),
            ("MySQL 与 Redis 双写一致性怎么保证？", "L4"),
        ],
    },
    {
        "keywords": ["elasticsearch", "elasticsearch-rest", "spring-boot-starter-data-elasticsearch"],
        "tech": "Elasticsearch",
        "questions": [
            ("什么是倒排索引？", "L1"),
            ("ES 的写入原理是什么？", "L2"),
            ("如何实现 MySQL 与 ES 的数据同步？", "L2"),
            ("ES 集群架构如何设计？", "L3"),
            ("如何优化 ES 查询性能？", "L3"),
            ("设计亿级商品搜索系统（ES 部分）？", "L4"),
        ],
    },
    {
        "keywords": ["docker", "dockerfile", "docker-compose"],
        "tech": "Docker",
        "questions": [
            ("Docker 的核心概念有哪些？", "L1"),
            ("镜像和容器的区别是什么？", "L1"),
            ("如何编写高效的 Dockerfile？", "L2"),
            ("Docker 网络模式有哪些？", "L2"),
            ("如何优化 Docker 镜像大小？", "L3"),
            ("Spring Boot 应用如何容器化部署？", "L3"),
            ("Docker Swarm 与 K8s 的区别？", "L4"),
        ],
    },
    {
        "keywords": ["nginx"],
        "tech": "Nginx",
        "questions": [
            ("Nginx 的主要作用有哪些？", "L1"),
            ("什么是正向代理和反向代理？", "L1"),
            ("Nginx 如何配置负载均衡？", "L2"),
            ("Nginx 如何实现限流？", "L3"),
            ("Nginx 如何配置 HTTPS？", "L3"),
            ("Nginx 性能优化有哪些方法？", "L4"),
        ],
    },
]

# AI 技术栈识别规则
PYTHON_MARKERS = [
    {
        "keywords": ["langchain"],
        "tech": "LangChain",
        "questions": [
            ("LangChain 的核心组件有哪些？", "L1"),
            ("LangChain 如何做 RAG？", "L2"),
            ("LangChain 与 LangGraph 的关系？", "L2"),
            ("如何实现 Agent 工具调用？", "L3"),
        ],
    },
    {
        "keywords": ["langgraph"],
        "tech": "LangGraph",
        "questions": [
            ("LangGraph 是什么？和 LangChain 什么关系？", "L1"),
            ("什么是 StateGraph？", "L1"),
            ("什么是 Agent Loop？LangGraph 如何实现？", "L2"),
            ("什么是 Conditional Edge？有什么作用？", "L2"),
            ("Multi-Agent 如何实现？", "L3"),
            ("什么是 Reflection 模式？", "L3"),
            ("Human-in-the-Loop 如何实现？", "L3"),
            ("LangGraph 的 Persistence 机制？", "L4"),
        ],
    },
    {
        "keywords": ["fastapi", "flask", "django"],
        "tech": "FastAPI / Web框架",
        "questions": [
            ("FastAPI 的核心特性有哪些？", "L1"),
            ("FastAPI 如何做异步处理？", "L2"),
            ("如何设计 RESTful API？", "L2"),
            ("FastAPI 的高并发如何优化？", "L3"),
        ],
    },
    {
        "keywords": ["pytorch", "torch"],
        "tech": "PyTorch",
        "questions": [
            ("PyTorch 的 Tensor 是什么？", "L1"),
            ("PyTorch 的自动求导原理是什么？", "L2"),
            ("如何训练一个模型？", "L2"),
            ("模型推理如何优化？", "L3"),
        ],
    },
    {
        "keywords": ["transformers", "huggingface"],
        "tech": "Transformers / HuggingFace",
        "questions": [
            ("Transformer 架构的核心？", "L1"),
            ("Pre-training 与 Fine-tuning 区别？", "L2"),
            ("LoRA 微调的原理是什么？", "L3"),
            ("大模型推理优化有哪些手段？", "L3"),
        ],
    },
    {
        "keywords": ["chromadb", "pinecone", "qdrant", "milvus", "faiss", "weaviate"],
        "tech": "向量数据库",
        "questions": [
            ("向量数据库的作用是什么？", "L1"),
            ("常见的向量索引方式有哪些？", "L2"),
            ("如何选择合适的向量数据库？", "L3"),
            ("向量检索的性能如何优化？", "L3"),
        ],
    },
    {
        "keywords": ["openai"],
        "tech": "OpenAI API",
        "questions": [
            ("OpenAI API 的核心接口有哪些？", "L1"),
            ("Function Calling 的原理是什么？", "L2"),
            ("怎么减少 OpenAI API 的 Token 消耗？", "L3"),
        ],
    },
    {
        "keywords": ["anthropic"],
        "tech": "Claude API",
        "questions": [
            ("Claude API 的核心接口有哪些？", "L1"),
            ("Tool Use 的原理是什么？", "L2"),
            ("如何用 Claude API 构建 Agent？", "L3"),
        ],
    },
]

# 通用题型模板 (用于知识题补充)
GENERIC_QUESTIONS = {
    "L1": [
        ("请描述你的项目的技术栈和整体架构。", "depth"),
        ("项目中最核心的业务模块是哪个？为什么？", "depth"),
        ("你负责的部分有哪些核心功能？", "depth"),
        ("这个项目解决了什么问题？", "depth"),
    ],
    "L2": [
        ("项目中有没有遇到过性能问题？如何定位和解决？", "scenario"),
        ("项目的数据一致性是如何保证的？", "scenario"),
        ("项目如何做容错和降级？", "scenario"),
        ("项目如何应对高并发？", "scenario"),
    ],
    "L3": [
        ("如果让你重新设计这个项目，你会做哪些改变？为什么？", "design"),
        ("项目的扩展性如何？如何支持新业务？", "design"),
        ("项目如何做监控和告警？", "scenario"),
        ("项目中最大的技术挑战是什么？怎么解决的？", "depth"),
    ],
    "L4": [
        ("这个项目的系统架构图是什么？如何画？", "design"),
        ("项目的安全设计如何？存在哪些风险？", "design"),
        ("如果项目数据量增长 100 倍，架构会如何演进？", "design"),
    ],
}

# ============================================================
# 2. 项目扫描函数
# ============================================================

def scan_project(project_path: str) -> dict:
    """扫描项目结构，返回项目分析结果"""
    root = Path(project_path)
    if not root.exists():
        raise FileNotFoundError(f"项目路径不存在: {project_path}")

    result = {
        "path": str(root),
        "name": Path(project_path).name,
        "type": "unknown",          # java / python / other
        "build_files": [],          # pom.xml / requirements.txt 等
        "config_files": [],         # application.yml / Dockerfile 等
        "tech_stack": [],           # 识别的技术栈
        "modules": [],              # 模块列表
        "source_files": [],         # 源码文件列表
        "raw_text": "",             # 拼接后的可用于匹配的文本
    }

    # 遍历项目文件
    for root_dir, dirs, files in os.walk(root):
        # 跳过隐藏目录和构建目录
        dirs[:] = [d for d in dirs if not d.startswith((".", "target", "node_modules", "dist", "build", "__pycache__"))]
        for f in files:
            full_path = os.path.join(root_dir, f)
            rel_path = os.path.relpath(full_path, project_path)
            result["source_files"].append(rel_path)

            # 收集关键文件
            lower = f.lower()
            if lower in ("pom.xml", "build.gradle", "build.gradle.kts", "requirements.txt", "pyproject.toml", "package.json"):
                result["build_files"].append(full_path)
                try:
                    result["raw_text"] += Path(full_path).read_text(encoding="utf-8", errors="ignore") + "\n"
                except Exception:
                    pass
            elif lower in ("application.yml", "application.yaml", "application.properties", "config.yml", "config.yaml", "setup.cfg"):
                result["config_files"].append(full_path)
                try:
                    result["raw_text"] += Path(full_path).read_text(encoding="utf-8", errors="ignore") + "\n"
                except Exception:
                    pass
            elif lower in ("dockerfile", "docker-compose.yml", "docker-compose.yaml", "nginx.conf", "frpc.ini", "frps.ini"):
                result["config_files"].append(full_path)
                try:
                    result["raw_text"] += Path(full_path).read_text(encoding="utf-8", errors="ignore") + "\n"
                except Exception:
                    pass
            elif lower in ("readme.md", "readme"):
                try:
                    result["raw_text"] += Path(full_path).read_text(encoding="utf-8", errors="ignore") + "\n"
                except Exception:
                    pass

    # 判断项目类型
    java_build = [f for f in result["build_files"] if f.endswith(("pom.xml", "build.gradle", "build.gradle.kts"))]
    py_build = [f for f in result["build_files"] if f.endswith(("requirements.txt", "pyproject.toml"))]
    if java_build:
        result["type"] = "java"
    elif py_build:
        result["type"] = "python"
    else:
        result["type"] = "other"

    # 识别技术栈
    result["tech_stack"] = detect_tech_stack(result["raw_text"], result["type"])

    # 扫描模块目录
    if root.is_dir():
        for d in os.listdir(root):
            if not d.startswith(".") and os.path.isdir(os.path.join(root, d)):
                result["modules"].append(d)

    return result


def detect_tech_stack(raw_text: str, project_type: str) -> list:
    """从文本中识别技术栈"""
    detected = []
    text_lower = raw_text.lower()
    markers = JAVA_MARKERS if project_type == "java" else PYTHON_MARKERS
    # Java 项目同时检测 Python 标记（混合项目）
    if project_type == "java":
        markers = markers + PYTHON_MARKERS
    elif project_type == "python":
        markers = markers + JAVA_MARKERS

    for marker in markers:
        for kw in marker["keywords"]:
            if kw.lower() in text_lower:
                detected.append({
                    "tech": marker["tech"],
                    "questions": marker["questions"],
                })
                break
    return detected


# ============================================================
# 3. 面试题生成函数
# ============================================================

def generate_questions(project: dict, question_types: list, max_count: int = 30) -> list:
    """生成面试题列表"""
    questions = []
    tech_stack = project["tech_stack"]
    tech_names = [t["tech"] for t in tech_stack]

    # 仅当指定类型包含对应题型时才生成
    want = set(question_types)

    # ① 知识题（L1/L2） - 从技术栈生成
    if want & {"choice", "short-answer"}:
        used = set()
        for t in tech_stack:
            for q, level in t["questions"]:
                if len(questions) >= max_count:
                    break
                if q in used:
                    continue
                used.add(q)
                questions.append({
                    "type": "short-answer",
                    "level": level,
                    "tech": t["tech"],
                    "question": q,
                    "answer": generate_answer_for(q, level),
                })

    # ② 项目深挖题（L3/L4）
    if want & {"deep-dive", "scenario"}:
        for level, qlist in GENERIC_QUESTIONS.items():
            for question, qtype in qlist:
                if len(questions) >= max_count:
                    break
                questions.append({
                    "type": qtype,
                    "level": level,
                    "tech": "项目经验",
                    "question": question,
                    "answer": "",
                    "followups": [
                        "能具体讲讲你在这个项目里做了什么吗？",
                        "遇到最大的坑是什么？怎么解决的？",
                        "如果重来一次，你会怎么做？",
                    ],
                })

    # ③ 架构设计题（L5）
    if want & {"design"} and len(questions) < max_count:
        questions.append({
            "type": "design",
            "level": "L5",
            "tech": "系统设计",
            "question": f"设计一个基于 {', '.join(tech_names[:5])} 的高可用系统架构",
            "answer": "架构设计要点：\n1. 分层架构（接入层/服务层/数据层）\n2. 高可用（多实例/熔断/降级）\n3. 数据一致性方案\n4. 可扩展性设计\n5. 容灾与监控",
            "followups": [
                "如果流量增长 10 倍，架构如何演进？",
                "如何保证数据最终一致性？",
                "如何做容灾备份？",
            ],
        })

    return questions


def generate_answer_for(question: str, level: str) -> str:
    """为问题生成参考答案（基于题目关键词的提示性答案）"""
    answer_templates = {
        "L1": "【参考答案】\n1. 这是 {} 的核心概念，属于面试高频基础题。\n2. 建议从概念定义、核心特性、应用场景三个维度回答。\n3. 回答时结合项目实际使用场景更能体现理解深度。",
        "L2": "【参考答案】\n1. 本题考查 {} 的实现原理。\n2. 回答时应分层阐述：底层数据结构 → 核心机制 → 实际应用。\n3. 可以结合源码细节，体现技术深度。",
        "L3": "【参考答案】\n1. 这是 {} 的场景题，考察实际解决问题的能力。\n2. 回答框架：问题分析 → 方案对比 → 方案选择 → 实施要点。\n3. 结合项目中的实际经验会更加分。",
        "L4": "【参考答案】\n1. 这是 {} 的架构/深挖题。\n2. 回答思路：当前方案 → 瓶颈分析 → 演进路径 → 分布式扩展。\n3. 展示系统设计和全局思考能力。",
    }
    return answer_templates.get(level, answer_templates["L1"]).format(question)


# ============================================================
# 4. 输出函数
# ============================================================

def to_markdown(project: dict, questions: list) -> str:
    """将生成的题目输出为 Markdown 文档"""
    lines = []
    lines.append(f"# {project['name']} 项目面试题\n")
    lines.append(f"> 自动生成时间: 2026-08-20  |  项目路径: {project['path']}\n")

    # 技术栈分析
    lines.append("## 📊 技术栈分析\n")
    tech_names = [t["tech"] for t in project["tech_stack"]]
    if tech_names:
        lines.append("| # | 技术 |")
        lines.append("|---|------|")
        for i, name in enumerate(tech_names, 1):
            lines.append(f"| {i} | {name} |")
    else:
        lines.append("> ⚠️ 未识别到明确技术栈，请手动补充。")
    lines.append("")

    lines.append(f"- 项目类型：`{project['type']}`")
    lines.append(f"- 构建文件：`{', '.join(project['build_files']) if project['build_files'] else '未发现'}`")
    lines.append(f"- 配置/部署文件：{len(project['config_files'])} 个")
    lines.append(f"- 源码文件：{len(project['source_files'])} 个")
    lines.append(f"- 模块：`{', '.join(project['modules'][:10]) if project['modules'] else '无'}`")
    lines.append("")

    # 题目分类
    levels = {"L1": [], "L2": [], "L3": [], "L4": [], "L5": []}
    for q in questions:
        levels.setdefault(q["level"], []).append(q)

    level_titles = {
        "L1": "基础题",
        "L2": "原理题",
        "L3": "场景题",
        "L4": "项目深挖",
        "L5": "架构设计",
    }

    for level_key in ["L1", "L2", "L3", "L4", "L5"]:
        qs = levels.get(level_key, [])
        if not qs:
            continue
        lines.append(f"\n## 🎯 {level_key}: {level_titles[level_key]}（共 {len(qs)} 题）\n")
        for idx, q in enumerate(qs, 1):
            lines.append(f"### 题目 {idx}：{q['question']}")
            lines.append(f"- **类型**：{q.get('type', '简答题')}")
            lines.append(f"- **技术点**：{q['tech']}")
            lines.append(f"- **难度**：{level_key}")
            if q.get("answer"):
                lines.append(f"\n{q['answer']}\n")
            if q.get("followups"):
                lines.append("**面试官追问**：")
                for fu in q["followups"]:
                    lines.append(f"- {fu}")
                lines.append("")
            lines.append("---")

    # 复习路线
    lines.append("\n## 📚 推荐复习路线\n")
    lines.append("1. **基础知识** → Java 核心 / Python 基础")
    lines.append("2. **框架原理** → Spring Boot / LangChain")
    lines.append("3. **中间件** → Redis / MySQL / RocketMQ / ES")
    lines.append("4. **项目深挖** → 技术选型理由 / 遇到的问题 / 解决方案")
    lines.append("5. **架构设计** → 高可用 / 扩展性 / 分布式")

    return "\n".join(lines)


def to_json(project: dict, questions: list) -> str:
    """输出为 JSON 格式（便于程序处理）"""
    return json.dumps({
        "project": project,
        "questions": questions,
    }, ensure_ascii=False, indent=2)


# ============================================================
# 5. CLI 入口
# ============================================================

def parse_args():
    parser = argparse.ArgumentParser(
        description="Java & AI 项目面试题自动生成器",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--project-path", "-p", required=True, help="项目路径")
    parser.add_argument("--output", "-o", help="输出目录（默认输出到项目同目录）")
    parser.add_argument(
        "--type", "-t",
        default="choice,short-answer,scenario,design,deep-dive",
        help="题型（逗号分隔）: choice,short-answer,coding,bug,scenario,design,deep-dive",
    )
    parser.add_argument(
        "--difficulty", "-d",
        default="all",
        help="难度: junior(仅L1/L2) / mid(含L3) / senior(含L4) / expert(全部) / all",
    )
    parser.add_argument("--format", "-f", default="md", choices=["md", "json"], help="输出格式")
    parser.add_argument("--max", "-m", type=int, default=50, help="最大题目数")
    return parser.parse_args()


def main():
    args = parse_args()

    print("[扫描] 正在扫描项目...")
    try:
        project = scan_project(args.project_path)
    except FileNotFoundError as e:
        print(f"[错误] {e}")
        return 1

    print(f"[完成] 项目类型: {project['type']}")
    print(f"[完成] 技术栈: {', '.join(t['tech'] for t in project['tech_stack']) or '未识别'}")

    # 题型过滤
    type_list = [t.strip() for t in args.type.split(",") if t.strip()]
    questions = generate_questions(project, type_list, args.max)

    # 难度过滤
    level_map = {
        "junior": {"L1", "L2"},
        "mid": {"L1", "L2", "L3"},
        "senior": {"L1", "L2", "L3", "L4"},
        "expert": {"L1", "L2", "L3", "L4", "L5"},
        "all": {"L1", "L2", "L3", "L4", "L5"},
    }
    allowed = level_map.get(args.difficulty, {"L1", "L2", "L3", "L4", "L5"})
    questions = [q for q in questions if q["level"] in allowed]

    print(f"[生成] {len(questions)} 道面试题")

    # 输出
    if args.output:
        out_dir = Path(args.output)
    else:
        out_dir = Path(args.project_path)
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.format == "md":
        content = to_markdown(project, questions)
        ext = "md"
    else:
        content = to_json(project, questions)
        ext = "json"

    out_file = out_dir / f"{project['name']}-interview-questions.{ext}"
    out_file.write_text(content, encoding="utf-8")
    print(f"[完成] 已输出到: {out_file}")

    return 0


if __name__ == "__main__":
    exit(main())