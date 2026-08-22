# 03 · 多 Provider 配置体系：pydantic-settings + 策略模式

> 配置层是整个 AI 搜索系统的"开关面板"——通过两个枚举变量 `EMBED_PROVIDER` 和 `LLM_PROVIDER`，就可以切换整条链路的 AI 供应商，无需修改一行业务代码。
>
> **对应项目：** `src/smart_search/config/settings.py` + `tools.py`

---

## 一、基础概念

### 1.1 配置管理在 AI 应用中的特殊挑战

电商智能搜索涉及多个 AI 供应商：

| 供应商 | 服务类型 | 角色 |
|--------|---------|------|
| **SiliconFlow** | Embedding 模型 | 文本向量化（BGE-M3） |
| **OpenRouter** | Embedding 模型 | 文本向量化（备选） |
| **阿里云通义千问** | 大语言模型 | 条件提取 + 推荐生成 |
| **Agnes AI** | 大语言模型 | 大语言模型（备选） |

**核心挑战：**
1. API Key 不能写死在代码中（安全风险）
2. 不同供应商有不同的 base_url、model 名、额外参数
3. 供应商之间要能快速切换（开发/测试/生产不同环境可能用不同供应商）
4. 配置项多达 20+，管理容易混乱

### 1.2 pydantic-settings 是什么

[pydantic-settings](https://docs.pydantic.dev/latest/concepts/pydantic_settings/) 是 Pydantic v2 官方推荐的配置管理库，从环境变量和 `.env` 文件自动加载配置，并在加载时做类型校验。

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "my-app"
    DEBUG: bool = False

    model_config = SettingsConfigDict(env_file=".env")
```

**对比 Spring Boot：**

| Python | Java (Spring Boot) |
|--------|-------------------|
| `BaseSettings` | `@ConfigurationProperties` |
| `env_file=".env"` | `application.yml` |
| `model_config` | `@ConfigurationPropertiesScan` |
| 字段类型注解自动校验 | `@Validated` + `@NotNull` |

---

## 二、进阶机制

### 2.1 策略模式 —— Provider 开关设计

项目定义了两个枚举来控制供应商选择：

```python
class EmbeddingProvider(str, Enum):
    SILICONFLOW = "siliconflow"
    OPENROUTER = "openrouter"

class LLMProvider(str, Enum):
    ALIYUN = "aliyun"
    AGNES = "agnes"
```

然后在 `Settings` 中通过 `active_embedding_config` 和 `active_llm_config` 两个属性实现"一键切换"：

```python
class Settings(BaseSettings):
    EMBED_PROVIDER: EmbeddingProvider = EmbeddingProvider.SILICONFLOW
    LLM_PROVIDER: LLMProvider = LLMProvider.ALIYUN
    ...

    @property
    def active_embedding_config(self) -> SiliconFlowEmbeddingConfig | OpenRouterEmbeddingConfig:
        if self.EMBED_PROVIDER == EmbeddingProvider.OPENROUTER:
            return self.openrouter_embedding
        return self.siliconflow_embedding

    @property
    def active_llm_config(self) -> AliyunLLMConfig | AgnesLLMConfig:
        if self.LLM_PROVIDER == LLMProvider.AGNES:
            return self.agnes_llm
        return self.aliyun_llm
```

**这就是策略模式（Strategy Pattern）的 AI 工程化实践：**

```
┌──────────────────────────────────────────────┐
│                  Settings                     │
│  EMBED_PROVIDER = "siliconflow"              │
│  LLM_PROVIDER = "aliyun"                     │
├──────────────────────────────────────────────┤
│  active_embedding_config  ──→  SiliconFlow   │
│  active_llm_config        ──→  阿里云通义千问  │
└──────────────────────────────────────────────┘
```

**业务代码调用时完全解耦：**

```python
# tools.py —— 工厂类
def get_embeddings(self):
    provider = settings.EMBED_PROVIDER
    cfg = settings.active_embedding_config  # 只关心"当前激活的配置"
    if provider == EmbeddingProvider.OPENROUTER:
        return self._build_openrouter_embedding(cfg)
    return self._build_siliconflow_embedding(cfg)

def get_model(self):
    provider = settings.LLM_PROVIDER
    cfg = settings.active_llm_config  # 只关心"当前激活的配置"
    if provider == LLMProvider.AGNES:
        return self._build_agnes_llm(cfg)
    return self._build_aliyun_llm(cfg)
```

**切换供应商只需改 `.env` 文件：**

```ini
# 从 SiliconFlow 切换到 OpenRouter
EMBED_PROVIDER=openrouter
OPENROUTER_API_KEY=sk-or-...

# 从阿里云切换到 Agnes AI
LLM_PROVIDER=agnes
AGNES_API_KEY=ag-...
```

### 2.2 嵌套配置 + 向后兼容

项目定义了 4 个独立的配置类，每个供应商一个：

```python
class SiliconFlowEmbeddingConfig(BaseModel):
    base_url: str = "https://api.siliconflow.cn/v1"
    api_key: str = ""
    model: str = "BAAI/bge-m3"

class OpenRouterEmbeddingConfig(BaseModel):
    base_url: str = "https://openrouter.ai/api/v1"
    api_key: str = ""
    model: str = "liquid/lfm-2.5-embedding-350m:free"
    http_referer: str = "https://mall-ai.example.com"
    openrouter_title: str = "Mall-AI Search"

class AliyunLLMConfig(BaseModel):
    base_url: str = "https://llm-7ydhlbtu5e3ldxca.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
    api_key: str = ""
    model: str = "qwen3.7-flash-2026-07-15"

class AgnesLLMConfig(BaseModel):
    base_url: str = "https://api.agnes-ai.cn"
    api_key: str = ""
    model: string = "agnes-2.5-flash"
    proxy_host: str = ""
    proxy_port: int = 0
```

**嵌套+回填的配置加载流程：**

```python
class Settings(BaseSettings):
    # 嵌套配置
    siliconflow_embedding: SiliconFlowEmbeddingConfig = SiliconFlowEmbeddingConfig()
    openrouter_embedding: OpenRouterEmbeddingConfig = OpenRouterEmbeddingConfig()
    aliyun_llm: AliyunLLMConfig = AliyunLLMConfig()
    agnes_llm: AgnesLLMConfig = AgnesLLMConfig()

    @model_validator(mode="before")
    @classmethod
    def _populate_provider_configs(cls, data: dict) -> dict:
        """回填优先级：.env 已有值 > 系统环境变量 > 默认值"""
        # 1. 供应商开关以 .env 为准（不被系统环境变量覆盖）
        # 2. API Key 优先从系统环境变量读取（避免把 key 写进仓库）
        # 3. 向后兼容旧字段名（EMBED_MODEL → siliconflow_embedding.model）
        ...
```

### 2.3 增强的配置加载流程图

```
.env 文件                   系统环境变量              默认值
  │                            │                      │
  ▼                            ▼                      ▼
┌──────────────────────────────────────────────────────────┐
│  pydantic-settings 自动加载                                 │
│  SettingsConfigDict(env_file=".env", env_nested_delimiter="__") │
├──────────────────────────────────────────────────────────┤
│  @model_validator(mode="before")                          │
│  1. 供应商开关以 .env 为准（不被系统环境变量覆盖）            │
│  2. API Key 优先从系统环境变量读取                           │
│  3. 旧字段名兼容（EMBED_MODEL → siliconflow_embedding.model）│
├──────────────────────────────────────────────────────────┤
│  类型校验：int 必须是数字，bool 必须是布尔值，str 必须是字符串  │
│  嵌套校验：每个子 Config 也做类型校验                        │
└──────────────────────────────────────────────────────────┘
```

---

## 三、项目现场

### 3.1 关键设计决策

**决策 1：为什么供应商开关从 `.env` 读，而不是系统环境变量？**

```python
_dotenv = dotenv_values(".env")
for _switch in ("EMBED_PROVIDER", "LLM_PROVIDER"):
    _wanted = (_dotenv or {}).get(_switch)
    if _wanted:
        data[_switch] = _wanted
```

因为系统环境变量可能残留进程级设置（如 `LLM_PROVIDER=aliyun`），会静默覆盖 `.env` 中配置的 `agnes`。显式从 `.env` 读回开关，保证项目能自控选用哪个供应商。

**决策 2：API Key 为什么从系统环境变量读？**

```python
def _fill(container: dict, key: str, env_name: str = "", default: str = "") -> None:
    val = container.get(key)
    if not val or "YOUR_" in str(val):
        container[key] = env.get(env_name, default) if env_name else default
```

`.env` 文件可能提交到 Git（虽然不应该），用 `YOUR_OPENAI_API_KEY` 占位。系统环境变量才是真正的密钥来源——安全且不泄漏。

**决策 3：为什么需要向后兼容旧字段名？**

项目演进过程中，配置字段名可能变化（如从 `EMBED_MODEL` 改为 `siliconflow_embedding.model`）。`@model_validator` 做兼容映射，保证旧 `.env` 文件仍然可用。

### 3.2 整个配置体系的全貌

```
┌───────────────────────────────────────────────────────────────┐
│                        .env 文件                               │
│  MYSQL_HOST=localhost    MYSQL_PORT=3306                       │
│  REDIS_URL=redis://...   INDEX_NAME=sku_idx                   │
│  EMBED_PROVIDER=siliconflow  LLM_PROVIDER=aliyun               │
│  OPENROUTER_API_KEY=...   AGNES_API_KEY=...                    │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────┐
│  Settings 对象（全局单例，模块加载时初始化）                     │
├───────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌─────────────────────────────────┐ │
│  │ 基础设施配置          │  │ AI 供应商配置                    │ │
│  │  MYSQL_HOST          │  │  ┌───────────────────────────┐  │ │
│  │  MYSQL_PORT          │  │  │ Embedding 供应商          │  │ │
│  │  REDIS_URL           │  │  │  SiliconFlow / OpenRouter │  │ │
│  │  INDEX_NAME          │  │  └───────────────────────────┘  │ │
│  └─────────────────────┘  │  ┌───────────────────────────┐  │ │
│                            │  │ LLM 供应商                │  │ │
│                            │  │  阿里云 / Agnes AI        │  │ │
│                            │  └───────────────────────────┘  │ │
│                            └─────────────────────────────────┘ │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────┐
│  消费方（tools.py 工厂方法）                                    │
│  get_embeddings() → active_embedding_config                     │
│  get_model()       → active_llm_config                          │
│  get_mysql_conn()  → MYSQL_*                                    │
│  get_redis_conn()  → REDIS_*                                    │
│  get_vector_store() → INDEX_NAME + embeddings                   │
└───────────────────────────────────────────────────────────────┘
```

---

## 四、Java 对照

### 4.1 完整 Java 等价实现

```java
// 1. 枚举定义
public enum EmbeddingProvider {
    SILICONFLOW, OPENROUTER
}

public enum LLMProvider {
    ALIYUN, AGNES
}

// 2. 供应商配置类
@Data
@ConfigurationProperties(prefix = "siliconflow.embedding")
public class SiliconFlowEmbeddingConfig {
    private String baseUrl = "https://api.siliconflow.cn/v1";
    private String apiKey = "";
    private String model = "BAAI/bge-m3";
}

// 3. 主配置类
@Component
@ConfigurationProperties(prefix = "app")
public class AppSettings {
    private EmbeddingProvider embedProvider = EmbeddingProvider.SILICONFLOW;
    private LLMProvider llmProvider = LLMProvider.ALIYUN;

    private SiliconFlowEmbeddingConfig siliconflowEmbedding = new SiliconFlowEmbeddingConfig();
    private OpenRouterEmbeddingConfig openrouterEmbedding = new OpenRouterEmbeddingConfig();
    private AliyunLLMConfig aliyunLlm = new AliyunLLMConfig();
    private AgnesLLMConfig agnesLlm = new AgnesLLMConfig();

    // 策略属性
    public EmbeddingConfig getActiveEmbeddingConfig() {
        return embedProvider == EmbeddingProvider.OPENROUTER
            ? openrouterEmbedding : siliconflowEmbedding;
    }

    public LLMConfig getActiveLlmConfig() {
        return llmProvider == LLMProvider.AGNES
            ? agnesLlm : aliyunLlm;
    }
}

// 4. application.yml
// app:
//   embed-provider: siliconflow
//   llm-provider: aliyun
//   siliconflow.embedding:
//     base-url: https://api.siliconflow.cn/v1
//     model: BAAI/bge-m3
```

### 4.2 差异对比

| 维度 | Python (pydantic-settings) | Java (Spring Boot) |
|------|---------------------------|-------------------|
| 配置来源 | `.env` + 系统环境变量 | `application.yml` + 系统环境变量 |
| 类型校验 | 声明时自动校验 | `@Validated` 注解 |
| 嵌套配置 | 嵌套 `BaseModel` | `@NestedConfigurationProperty` |
| 默认值 | 字段赋值 | 字段赋值 / `@Value` |
| 条件逻辑 | `@model_validator` | `@ConditionalOnProperty` |

---

## 五、最小可复现示例

### 5.1 Python 多 Provider 配置模板

```python
# config.py
from enum import Enum
from pydantic import BaseModel
from pydantic_settings import BaseSettings, SettingsConfigDict

class Provider(str, Enum):
    ALIYUN = "aliyun"
    OPENAI = "openai"

class AliyunConfig(BaseModel):
    api_key: str = ""
    model: str = "qwen-plus"

class OpenAIConfig(BaseModel):
    api_key: str = ""
    model: str = "gpt-4o-mini"

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    provider: Provider = Provider.ALIYUN
    aliyun: AliyunConfig = AliyunConfig()
    openai: OpenAIConfig = OpenAIConfig()

    @property
    def active_config(self):
        if self.provider == Provider.OPENAI:
            return self.openai
        return self.aliyun

settings = Settings()

# 使用
print(settings.active_config.model)  # 根据 provider 动态切换
```

### 5.2 Java 对照

```java
// application.yml
// ai:
//   provider: aliyun
//   aliyun:
//     api-key: ${ALIYUN_API_KEY:}
//     model: qwen-plus
//   openai:
//     api-key: ${OPENAI_API_KEY:}
//     model: gpt-4o-mini

@Component
@ConfigurationProperties(prefix = "ai")
public class AiSettings {
    private Provider provider = Provider.ALIYUN;
    private AliyunConfig aliyun = new AliyunConfig();
    private OpenAIConfig openai = new OpenAIConfig();

    public BaseConfig getActiveConfig() {
        return provider == Provider.OPENAI ? openai : aliyun;
    }
}
```

---

## 六、面试要点

### Q1: 为什么需要多 Provider 架构？

**回答思路：** 防止供应商锁定、容灾切换（A 供应商宕机切 B）、成本优化（不同场景用不同模型 A/B 测试）、不同供应商的模型擅长不同领域。

### Q2: 项目中的配置回填策略是什么？

**回答思路：** 三层回填：`.env` 有空值或占位符 → 系统环境变量 → 代码默认值。供应商开关以 `.env` 为准（避免系统环境变量静默覆盖），API Key 以系统环境变量为准（避免提交到 Git）。

### Q3: 这个配置方案和 Spring Boot 的 `@ConfigurationProperties` 在思想上有何异同？

**回答思路：** 同：都支持嵌套配置、类型校验、多环境切换。异：Python 用 `@model_validator` 做自定义回填逻辑，Spring Boot 用 `@ConditionalOnProperty` 做条件加载；Python 的枚举更灵活（支持字符串值），Java 的枚举更严格（编译时确定）。

### Q4: 如果要新增一个供应商（如百度文心一言），需要改哪些地方？

**回答思路：** 三步：
1. 枚举中加 `BAIDU = "baidu"`
2. 新建 `BaiduLLMConfig` 配置类
3. `Settings` 中加嵌套配置，`active_llm_config` 加 `baidu` 分支
4. `tools.py` 工厂方法加 `_build_baidu_llm()` 分支

**不需要改任何业务代码**——这是策略模式的核心价值。

---

> **下一篇：** [04-LLM-PROVIDER.md —— LLM 服务商对接：阿里云通义千问 + Agnes AI](./04-LLM-PROVIDER.md)
>
> 从配置层进入 AI 核心，看大语言模型如何通过 OpenAI 兼容协议统一接入，以及双供应商的实战差异。