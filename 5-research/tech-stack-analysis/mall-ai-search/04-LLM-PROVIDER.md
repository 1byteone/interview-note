# 04 · LLM 服务商对接：阿里云通义千问 + Agnes AI

> 大语言模型是 AI 搜索的"大脑"。这一篇看项目如何通过 **OpenAI 兼容协议**统一接入不同 LLM 供应商，以及温度、思考模式等参数对业务结果的影响。
>
> **对应项目：** `src/smart_search/config/tools.py`（`_build_aliyun_llm` / `_build_agnes_llm`）

---

## 一、基础概念

### 1.1 什么是"OpenAI 兼容协议"

不同 LLM 供应商（OpenAI、阿里云、DeepSeek、智谱、Agnes...）通常暴露 **RESTful HTTP 接口**。为了让开发者一套代码接入所有供应商，各家纷纷实现了与 OpenAI Chat Completions 一致的协议：

```
POST /v1/chat/completions
{
  "model": "qwen3.7-flash-2026-07-15",
  "messages": [{"role": "user", "content": "你好"}],
  "temperature": 0.1
}
```

**OpenAI 兼容 = 接口路径、请求/响应 JSON 结构都与 OpenAI 官方一致**，只换 `base_url` 和 `api_key` 即可接入。

```
┌──────────────────────────────────────────────────────────────┐
│                    上层业务/框架代码                          │
│              (LangChain / Spring AI / 业务代码)              │
└────────────────────────────┬─────────────────────────────────┘
                             │ 统一调用
                             ▼
              ┌──────────────────────────────┐
              │   OpenAI 兼容 SDK (openai库)  │
              │   base_url 可配置，其余不变     │
              └──────┬──────────┬────────────┘
                     │          │
        ┌────────────┘          └────────────┐
        ▼                                   ▼
┌───────────────┐                  ┌───────────────┐
│  阿里云通义千问  │                  │  Agnes AI      │
│  /compatible-  │                  │  /v1/chat/     │
│  mode/v1       │                  │  completions   │
└───────────────┘                  └───────────────┘
```

### 1.2 ChatOpenAI —— LangChain 的 LLM 封装

LangChain 提供 `ChatOpenAI` 类，底层就是调用 OpenAI 兼容协议的 chat completions 接口：

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(
    base_url="https://.../compatible-mode/v1",  # 任意 OpenAI 兼容端点
    api_key="your-key",
    model="qwen3.7-flash-2026-07-15",
    temperature=0.1,
)
```

**对应 Spring 生态：** `Spring AI` 的 `ChatClient` / `OpenAiChatModel`。

---

## 二、进阶机制

### 2.1 双供应商构建工厂

```python
def _build_aliyun_llm(self, cfg: AliyunLLMConfig) -> ChatOpenAI:
    return ChatOpenAI(
        base_url=cfg.base_url,
        api_key=cfg.api_key,
        model=cfg.model,
        temperature=0.1,
        extra_body={"enable_thinking": False},  # 关闭思维链
    )

def _build_agnes_llm(self, cfg: AgnesLLMConfig) -> ChatOpenAI:
    # 代理配置
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

### 2.2 `temperature=0.1` —— 为什么推荐任务要低温

| temperature | 效果 | 适用场景 |
|------------|------|---------|
| 0~0.3 | 确定性高、输出稳定 | 推荐、结构化提取、代码生成 |
| 0.5~0.7 | 平衡创造性与稳定性 | 通用对话 |
| 0.8~1.5 | 创造性高、随机性强 | 创意写作、头脑风暴 |

**商品推荐是事实性任务**（依据向量库召回的真实数据生成推荐），必须用低温保证：
1. 不胡编乱造商品
2. 严格遵循输出格式
3. 推荐理由与召回数据一致

### 2.3 `extra_body={"enable_thinking": False}` —— 关闭思维链

部分国产模型（如通义千问系列）支持**思考模式**（thinking/reasoning 模式），即在回答前先输出内部推理过程。

```
输入："推荐5000元以下的华为手机"
┌─────────────────────────────────┐
│ 思考模式开启: 推理过程 → 最终答案   │  ← 耗时更长、token 更多
│ 思考模式关闭: 直接输出最终答案      │  ← 快速、token 少
└─────────────────────────────────┘
```

**为什么关闭？** 本项目是电商搜索，`/extract` 和 `/recommend` 都是结构化 JSON 输出：
- 开启 thinking 会混入非 JSON 的推理文本，破坏 Pydantic 解析
- 增加延迟（用户等搜索结果）
- 增加 token 成本

### 2.4 Agnes AI 的代理问题

Agnes AI 是国内服务，但直连可能被 TLS 重置。项目通过设置 `HTTP_PROXY` / `HTTPS_PROXY` 环境变量让 OpenAI SDK 底层走代理：

```python
os.environ.setdefault("HTTP_PROXY", proxy_url)
os.environ.setdefault("HTTPS_PROXY", proxy_url)
```

**Java 对照：**

```java
// Spring AI 中配置代理
System.setProperty("http.proxyHost", "127.0.0.1");
System.setProperty("http.proxyPort", "7890");
System.setProperty("https.proxyHost", "127.0.0.1");
System.setProperty("https.proxyPort", "7890");
```

---

## 三、项目现场

### 3.1 LLM 在两条 API 链路中的角色

```
链路1: /extract —— 条件提取
用户输入 "5000元以下续航强的华为手机"
    → LLM (通义千问, temperature=0.1)
    → 输出 {"keyword": "华为手机", "min_price": 0, "max_price": 5000}
    → PydanticOutputParser 解析为 SearchCondition

链路2: /recommend —— 商品推荐
用户输入 + 向量库召回的商品 (TOP-10)
    → Agent 调用 vector_search_tool 获取上下文
    → LLM 综合上下文生成:
       summary: "为你推荐以下华为手机..."
       product_list: [商品1, 商品2, ...]
       reason: ["价格匹配", "续航优秀", ...]
    → response_format 强制结构化
```

### 3.2 提取提示词（Prompt）设计

```python
SEARCH_EXTRACT_PROMPT = """
你是商品搜索条件提取助手，根据用户查询提取商品筛选条件。
需要提取的参数：
1. keyword: 搜索关键词
2. min_price: 最低价格,若没有提取到值，min_price=0
3. max_price: 最高价格,若没有提取到值，max_price=100000
示例：
查询："我想买一个2000-4000元的小米手机"
返回：{"keyword": "小米手机", "min_price": 2000, "max_price": 4000}
只输出JSON，不要额外文字: {format_instructions}"""
```

**好 Prompt 的关键要素：**
1. **角色设定** — "你是商品搜索条件提取助手"
2. **明确输入** — "根据用户查询提取商品筛选条件"
3. **字段说明** — 每个字段的语义、默认值
4. **示例（few-shot）** — 一个完整的输入→输出示例
5. **输出约束** — "只输出JSON，不要额外文字"

### 3.3 推荐提示词（Prompt）设计

```python
SEARCH_PROMPT = """
请严格依据上下文内真实信息回答用户问题，**严禁编造不存在的商品信息**。
规则要求：
1. 根据用户问题调用vector_search_tool工具检索商品知识库，返回的商品信息生成上下文；
2. 仅使用上下文存在的数据进行商品推荐，并给出推荐理由；
3. 若上下文没有匹配内容：summary="暂无相关信息"，product_list=[]，reason=[]；
4. 输出格式必须是纯粹标准JSON，禁止附带Markdown等任何额外文本；
5. 严格遵循输出字段结构，不随意增删字段。
"""
```

**亮点：**
- **防幻觉（anti-hallucination）**："严禁编造不存在的商品信息" + "仅使用上下文存在的数据"
- **兜底策略**："若上下文没有匹配内容：summary=暂无相关信息，product_list=[]，reason=[]"
- **格式约束**："禁止附带Markdown" + "严格遵循输出字段结构"

---

## 四、Java 对照

### 4.1 Spring AI ChatClient 对照

```java
// pom.xml 依赖 (Spring AI)
// <dependency>
//     <groupId>org.springframework.ai</groupId>
//     <artifactId>spring-ai-starter-model-openai</artifactId>
// </dependency>

@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "aliyun")
    public ChatClient aliyunChatClient(AppSettings settings) {
        var openAiApi = new OpenAiApi(
            settings.getAliyun().getBaseUrl(),
            settings.getAliyun().getApiKey(),
            settings.getAliyun().getModel()
        );
        var chatModel = new OpenAiChatModel(openAiApi, OpenAiChatOptions.builder()
            .withTemperature(0.1d)  // 对应 temperature=0.1
            .build());
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "agnes")
    public ChatClient agnesChatClient(AppSettings settings) {
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "7890");
        var openAiApi = new OpenAiApi(
            settings.getAgnes().getBaseUrl(),
            settings.getAgnes().getApiKey(),
            settings.getAgnes().getModel()
        );
        return ChatClient.builder(new OpenAiChatModel(openAiApi)).build();
    }

    @Bean
    public ChatClient activeChatClient(AppSettings settings) {
        return settings.getProvider() == Provider.ALIYUN
            ? aliyunChatClient(settings) : agnesChatClient(settings);
    }
}

// 使用
@Service
public class SearchService {
    private final ChatClient chatClient;

    public SearchCondition extractSearchCondition(String query) {
        return chatClient.prompt()
            .system(EXTRACT_SYSTEM_PROMPT)
            .user(query)
            .call()
            .entity(SearchCondition.class);  // 结构化输出
    }
}
```

### 4.2 对照总结

| 维度 | Python (LangChain ChatOpenAI) | Java (Spring AI ChatClient) |
|------|------------------------------|----------------------------|
| 核心类 | `ChatOpenAI` | `ChatClient` / `OpenAiChatModel` |
| 参数 | `base_url` / `api_key` / `model` | `OpenAiApi` + `OpenAiChatOptions` |
| 温度 | `temperature=0.1` | `.withTemperature(0.1d)` |
| 扩展字段 | `extra_body={...}` | `OpenAiChatOptions.builder()` |
| 代理 | 环境变量 `HTTPS_PROXY` | 系统属性 `https.proxyHost` |

---

## 五、最小可复现示例

### 5.1 Python：多 LLM 供应商切换

```python
# 需要: pip install langchain-openai python-dotenv
import os
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI

load_dotenv()

def create_llm(provider: str = "aliyun") -> ChatOpenAI:
    """根据供应商名创建 LLM 实例"""
    configs = {
        "aliyun": {
            "base_url": os.getenv("ALIYUN_BASE_URL"),
            "api_key": os.getenv("ALIYUN_API_KEY"),
            "model": os.getenv("ALIYUN_MODEL", "qwen-plus"),
        },
        "openai": {
            "base_url": "https://api.openai.com/v1",
            "api_key": os.getenv("OPENAI_API_KEY"),
            "model": "gpt-4o-mini",
        },
    }
    cfg = configs[provider]
    return ChatOpenAI(
        base_url=cfg["base_url"],
        api_key=cfg["api_key"],
        model=cfg["model"],
        temperature=0.1,
        extra_body={"enable_thinking": False},  # 关闭思考模式保证 JSON 输出
    )

# 使用
llm = create_llm("aliyun")
resp = llm.invoke("苹果手机8000元以内有什么推荐？")
print(resp.content)
```

### 5.2 测试不同供应商的响应

```python
# test_providers.py
def test_switch_provider():
    """验证切换供应商不影响接口行为"""
    import pytest

    @pytest.mark.parametrize("provider", ["aliyun", "openai"])
    def test_llm_responds(provider):
        llm = create_llm(provider)
        resp = llm.invoke("说'你好'")
        assert resp.content  # 有响应

        # 结构化输出
        from langchain_core.output_parsers import JsonOutputParser
        parser = JsonOutputParser()
        structured = parser.invoke('{"msg":"你好"}')
        assert structured["msg"] == "你好"
```

---

## 六、面试要点

### Q1: 什么是 OpenAI 兼容协议？为什么 AI 行业都在用？

**回答思路：** OpenAI 制定了 chat completions API 的事实标准（端点、请求体、响应体）。各厂商实现同一接口，开发者换 `base_url` + `api_key` 即可切换供应商，省去 SDK 适配成本。这是"标准接口 + 多实现"的接口隔离思想。

### Q2: 为什么推荐任务用低温（temperature=0.1）？

**回答思路：** LLM 采样温度控制随机性。推荐是事实性任务，温度过高会引入幻觉、导致输出不确定；低温让模型输出更稳定、更贴近训练分布，特别适合需要精确 JSON 结构化的场景。

### Q3: 为什么要关闭 thinking 模式？

**回答思路：** 思考模式会在最终答案前输出内部推理文本，可能混入 JSON 破坏解析，同时增加延迟和 token 成本。结构化任务直接要答案，不需要让模型"思考"。

### Q4: 在 Java 生态（Spring AI）中如何实现同样功能？

**回答思路：** 用 `ChatClient` + `OpenAiChatModel`，`OpenAiChatOptions.builder().withTemperature(0.1).build()`，`.entity(SearchCondition.class)` 做结构化输出。多供应商用 `@ConditionalOnProperty` 按配置装配不同 bean。

### Q5: Prompt 设计中怎么防幻觉（编造商品）？

**回答思路：** 四层防护：
1. **系统提示词**明确"严禁编造不存在的商品信息"+"仅使用上下文存在的数据"
2. **上下文约束**：只把向量库召回的 TOP-10 商品作为上下文传入
3. **低温度**：减少随机创作空间
4. **结构约束**：response_format/Pydantic 强制字段结构，空数据有兜底

---

> **下一篇：** [05-EMBEDDING.md —— Embedding 向量化：BGE-M3 + SiliconFlow + OpenRouter](./05-EMBEDDING.md)
>
> LLM 理解语义的前提是先看懂文本。看 Embedding 如何把"5000元以下华为手机"变成一组向量。