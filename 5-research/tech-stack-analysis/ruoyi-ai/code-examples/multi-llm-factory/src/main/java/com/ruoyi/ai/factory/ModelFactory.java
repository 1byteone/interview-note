package com.ruoyi.ai.factory;

/**
 * ModelFactory - 模型工厂接口
 *
 * 定义统一的模型创建契约，遵循工厂模式：
 * 1. 每个模型厂商实现此接口
 * 2. 通过 ModelFactoryRegistry 统一管理和分发请求
 * 3. 运行时动态选择模型，实现模型热切换
 *
 * 设计原则：
 * - 开闭原则：新增模型只需新增实现类，无需修改现有代码
 * - 依赖倒置：上层依赖此接口而非具体实现
 */
public interface ModelFactory {

    /**
     * 获取当前模型工厂对应的厂商名称标识
     *
     * 返回值用于：
     * - 注册到 ModelFactoryRegistry 的 Map key
     * - 配置文件中 model.provider 的匹配值
     *
     * @return 厂商名称，如 "openai"、"deepseek"、"qwen"
     */
    String getProviderName();

    /**
     * 根据传入的配置对象创建聊天模型实例
     *
     * 每个实现类负责：
     * 1. 读取配置中的 API Key、Base URL 等参数
     * 2. 初始化对应厂商的 LangChain4j ChatLanguageModel
     * 3. 返回配置好参数的模型实例
     *
     * @param config 模型配置对象，包含 apiKey、modelName、temperature 等
     * @return ChatLanguageModel 实例，可直接用于生成对话
     */
    dev.langchain4j.model.chat.ChatLanguageModel createModel(ModelConfig config);
}
