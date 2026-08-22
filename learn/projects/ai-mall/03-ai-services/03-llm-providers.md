# 03 · 多 LLM Provider 配置与管理

> AI 商城的智能服务依赖多个 LLM 和 Embedding 供应商。本文展示如何通过策略模式 + 工厂模式 + 环境变量驱动，实现多供应商的无缝切换，以及成本与延迟的优化策略。

---

## 一、多 Provider 架构总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                        .env 配置文件                                  │
│  EMBED_PROVIDER=siliconflow    LLM_PROVIDER=aliyun                   │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Settings 对象（Pydantic BaseSettings）                               │
│                                                                      │
│  ┌──────────────────────────────┐  ┌──────────────────────────────┐  │
│  │ Embedding Provider 开关      │  │ LLM Provider 开关             │  │
│  │                             │  │                              │  │
│  │ active_embedding_config     │  │ active_llm_config            │  │
│  │   → SiliconFlow (默认)      │  │   → 阿里云通义千问 (默认)     │  │
│  │   → OpenRouter (备选)       │  │   → Agnes AI (备选)          │  │
│  └──────────────────────────────┘  └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Tools 工厂方法（get_embeddings / get_model）                        │
│                                                                      │
│  get_embeddings() → 读取 active_embedding_config → 构建实例 → 缓存  │
│  get_model()       → 读取 active_llm_config       → 构建实例 → 缓存  │
└──────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  业务消费方（SearchService / RAGService）                             │
│                                                                      │
│  # 只关心"当前激活的实例"，不关心具体是哪个供应商                       │
│  self.llm = tools.get_model()                                        │
│  self.embeddings = tools.get_embeddings()                            │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 二、配置层设计

### 2.1 枚举定义

```python
from enum import Enum

class EmbeddingProvider(str, Enum):
    SILICONFLOW = "siliconflow"   # 默认，BGE-M3 模型
    OPENROUTER = "openrouter"      # 备选，免费模型

class LLMProvider(str, Enum):
    ALIYUN = "aliyun"             # 默认，通义千问
    AGNES = "agnes"               # 备选，Agnes AI
```

### 2.2 供应商配置类

```python
from pydantic import BaseModel

class SiliconFlowEmbeddingConfig(BaseModel):
    base_url: str = "https://api.siliconflow.cn/v1"
    api_key: str = ""
    model: str = "BAAI/bge-m3"          # 中文 Embedding 标杆

class OpenRouterEmbeddingConfig(BaseModel):
    base_url: str = "https://openrouter.ai/api/v1"
    api_key: str = ""
    model: str = "liquid/lfm-2.5-embedding-350m:free"  # 免费模型
    http_referer: str = "https://mall-ai.example.com"
    openrouter_title: str = "Mall-AI Search"

class AliyunLLMConfig(BaseModel):
    base_url: str = "https://llm-xxx.compatible-mode/v1"
    api_key: str = ""
    model: str = "qwen3.7-flash-2026-07-15"
    temperature: float = 0.1

class AgnesLLMConfig(BaseModel):
    base_url: str = "https://api.agnes-ai.cn"
    api_key: str = ""
    model: str = "agnes-2.5-flash"
    proxy_host: str = ""
    proxy_port: int = 0
```

### 2.3 主配置类

```python
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    # 供应商开关
    EMBED_PROVIDER: EmbeddingProvider = EmbeddingProvider.SILICONFLOW
    LLM_PROVIDER: LLMProvider = LLMProvider.ALIYUN

    # 嵌套配置（每个供应商一个配置类）
    siliconflow_embedding: SiliconFlowEmbeddingConfig = SiliconFlowEmbeddingConfig()
    openrouter_embedding: OpenRouterEmbeddingConfig = OpenRouterEmbeddingConfig()
    aliyun_llm: AliyunLLMConfig = AliyunLLMConfig()
    agnes_llm: AgnesLLMConfig = AgnesLLMConfig()

    @property
    def active_embedding_config(self) -> SiliconFlowEmbeddingConfig | OpenRouterEmbeddingConfig:
        """策略模式：根据当前开关返回对应配置"""
        if self.EMBED_PROVIDER == EmbeddingProvider.OPENROUTER:
            return self.openrouter_embedding
        return self.siliconflow_embedding

    @property
    def active_llm_config(self) -> AliyunLLMConfig | AgnesLLMConfig:
        """策略模式：根据当前开关返回对应配置"""
        if self.LLM_PROVIDER == LLMProvider.AGNES:
            return self.agnes_llm
        return self.aliyun_llm
```

---

## 三、工厂层设计

### 3.1 Embedding 工厂

```python
from langchain_openai import OpenAIEmbeddings

class Tools:
    def __init__(self):
        self._embedding_cache = None
        self._llm_cache = None

    def get_embeddings(self) -> OpenAIEmbeddings:
        """获取 Embedding 实例（缓存单例）"""
        if self._embedding_cache is not None:
            return self._embedding_cache

        provider = settings.EMBED_PROVIDER
        cfg = settings.active_embedding_config

        if provider == EmbeddingProvider.OPENROUTER:
            self._embedding_cache = self._build_openrouter_embedding(cfg)
        else:
            self._embedding_cache = self._build_siliconflow_embedding(cfg)

        return self._embedding_cache

    def _build_siliconflow_embedding(self, cfg):
        return OpenAIEmbeddings(
            base_url=cfg.base_url,
            api_key=cfg.api_key,
            model=cfg.model,
        )

    def _build_openrouter_embedding(self, cfg):
        return OpenAIEmbeddings(
            base_url=cfg.base_url,
            api_key=cfg.api_key,
            model=cfg.model,
            check_embedding_ctx_length=False,  # OpenRouter 只接受原始文本
            default_headers={
                "HTTP-Referer": cfg.http_referer,
                "X-OpenRouter-Title": cfg.openrouter_title,
            },
        )
```

### 3.2 LLM 工厂

```python
from langchain_openai import ChatOpenAI

class Tools:
    def get_model(self) -> ChatOpenAI:
        """获取 LLM 实例（缓存单例）"""
        if self._llm_cache is not None:
            return self._llm_cache

        provider = settings.LLM_PROVIDER
        cfg = settings.active_llm_config

        if provider == LLMProvider.AGNES:
            self._llm_cache = self._build_agnes_llm(cfg)
        else:
            self._llm_cache = self._build_aliyun_llm(cfg)

        return self._llm_cache

    def _build_aliyun_llm(self, cfg):
        return ChatOpenAI(
            base_url=cfg.base_url,
            api_key=cfg.api_key,
            model=cfg.model,
            temperature=0.1,
            extra_body={"enable_thinking": False},  # 关闭思考模式
        )

    def _build_agnes_llm(self, cfg):
        if cfg.proxy_host and cfg.proxy_port > 0:
            proxy_url = f"http://{cfg.proxy_host}:{cfg.proxy_port}"
            os.environ.setdefault("HTTP_PROXY", proxy_url)
            os.environ.setdefault("HTTPS_PROXY", proxy_url)

        return ChatOpenAI(
            base_url=cfg.base_url,
            api_key=cfg.api_key,
            model=cfg.model,
            temperature=0.1,
        )
```

---

## 四、Spring Boot 对照

### 4.1 Java 配置等价实现

```java
// 枚举
public enum EmbeddingProvider { SILICONFLOW, OPENROUTER }
public enum LLMProvider { ALIYUN, AGNES }

// 主配置类
@Component
@ConfigurationProperties(prefix = "app")
public class AppSettings {
    private EmbeddingProvider embedProvider = EmbeddingProvider.SILICONFLOW;
    private LLMProvider llmProvider = LLMProvider.ALIYUN;

    private SiliconFlowConfig siliconflow = new SiliconFlowConfig();
    private OpenRouterConfig openrouter = new OpenRouterConfig();
    private AliyunConfig aliyun = new AliyunConfig();
    private AgnesConfig agnes = new AgnesConfig();

    public EmbeddingConfig getActiveEmbeddingConfig() {
        return embedProvider == EmbeddingProvider.OPENROUTER
            ? openrouter : siliconflow;
    }

    public LLMConfig getActiveLlmConfig() {
        return llmProvider == LLMProvider.AGNES
            ? agnes : aliyun;
    }
}

// 条件装配
@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "aliyun")
    public ChatClient aliyunChatClient(AppSettings settings) {
        var api = new OpenAiApi(
            settings.getAliyun().getBaseUrl(),
            settings.getAliyun().getApiKey()
        );
        return ChatClient.builder(new OpenAiChatModel(api)).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.llm-provider", havingValue = "agnes")
    public ChatClient agnesChatClient(AppSettings settings) {
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "7890");
        var api = new OpenAiApi(
            settings.getAgnes().getBaseUrl(),
            settings.getAgnes().getApiKey()
        );
        return ChatClient.builder(new OpenAiChatModel(api)).build();
    }
}
```

---

## 五、成本与延迟优化

### 5.1 供应商成本对比

| 供应商 | 模型 | 价格 | 延迟 | 质量 | 推荐场景 |
|--------|------|------|------|------|---------|
| 阿里云通义千问 | qwen3.7-flash | 低 | 低 | 高 | 默认推荐 |
| Agnes AI | agnes-2.5-flash | 中 | 中 | 高 | 备选/容灾 |
| SiliconFlow | BGE-M3 (Embedding) | 低 | 低 | 极高 | 默认 Embedding |
| OpenRouter | lfm-2.5-embedding | 免费 | 高 | 中 | 备选/降级 |

### 5.2 优化策略

```
成本优化:
  1. 缓存 Embedding 结果（相同文本不重复调用）
  2. 选择 Flash 模型（速度更快，成本更低）
  3. 控制上下文长度（只保留必要的历史消息）
  4. 设置 token 上限（max_tokens 限制）

延迟优化:
  1. 低温 (temperature=0.1) 减少随机采样时间
  2. 关闭 thinking 模式（结构化任务不需要）
  3. 流式输出 (SSE) 降低首 token 感知延迟
  4. Embedding 连接池复用（避免 TCP 握手）

降级策略:
  1. 主供应商超时 → 自动切换到备选供应商
  2. 所有供应商不可用 → 降级到传统 ES 搜索
  3. LLM 离线 → 只返回向量检索结果，不加 AI 生成
```

---

> **下一篇：** [../04-infrastructure/01-docker-deploy.md](../04-infrastructure/01-docker-deploy.md) — Docker 容器化部署