package com.zznursing.iot;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * MQTT客户端配置类
 *
 * 功能说明：
 * 1. 配置MQTT连接参数（服务器地址、认证信息等）
 * 2. 创建MQTT客户端工厂
 * 3. 配置消息生产者（发布消息）
 * 4. 配置消息消费者（订阅消息）
 *
 * 使用场景：
 * - 智慧养老系统中，IoT设备通过MQTT协议上报数据
 * - 系统通过MQTT向设备下发控制指令
 *
 * @author zznursing
 * @since 1.0.0
 */
@Configuration
public class MqttClientConfig {

    /**
     * MQTT连接配置属性
     * 从application.yml中读取配置
     */
    private final MqttProperties mqttProperties;

    /**
     * 构造函数注入配置属性
     *
     * @param mqttProperties MQTT配置属性对象
     */
    public MqttClientConfig(MqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    /**
     * 创建MQTT客户端工厂
     *
     * 功能：初始化MQTT客户端连接工厂，配置连接参数
     * 包括：服务器地址、客户端ID、用户名、密码等
     *
     * @return MqttPahoClientFactory MQTT客户端工厂实例
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        // 创建默认的MQTT客户端工厂
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        // 创建连接选项配置
        MqttConnectOptions options = new MqttConnectOptions();

        // 设置MQTT服务器地址列表（支持多个服务器，用于负载均衡和故障转移）
        options.setServerURIs(mqttProperties.getUrls());

        // 设置连接超时时间（秒）
        options.setConnectionTimeout(mqttProperties.getConnectionTimeout());

        // 设置心跳间隔（秒），用于保持连接活性
        options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());

        // 设置是否自动重连
        options.setAutomaticReconnect(mqttProperties.isAutoReconnect());

        // 设置清理会话标志
        // true: 每次连接都是新会话，不保留订阅和消息
        // false: 保留会话状态，重连时可以接收离线消息
        options.setCleanSession(mqttProperties.isCleanSession());

        // 设置最大重连次数
        options.setMaxReconnectDelay(mqttProperties.getMaxReconnectDelay());

        // 如果配置了用户名和密码，则设置认证信息
        if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isEmpty()) {
            options.setUserName(mqttProperties.getUsername());
        }

        if (mqttProperties.getPassword() != null && !mqttProperties.getPassword().isEmpty()) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }

        // 将连接选项设置到工厂
        factory.setConnectionOptions(options);

        return factory;
    }

    /**
     * 消息输出通道
     *
     * 功能：定义MQTT消息的输出通道，用于将接收到的消息传递给后续处理组件
     * 使用DirectChannel实现同步消息传递
     *
     * @return MessageChannel 消息通道实例
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * 消息输入通道
     *
     * 功能：定义MQTT消息的输入通道，用于将要发送的消息传递给MQTT发送适配器
     *
     * @return MessageChannel 消息通道实例
     */
    @Bean
    public MessageChannel mqttOutputChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT消息入站适配器（消费者）
     *
     * 功能：
     * 1. 连接到MQTT服务器并订阅指定主题
     * 2. 接收设备上报的消息
     * 3. 将消息转换后发送到mqttInputChannel通道
     *
     * 配置说明：
     * - clientId: 客户端唯一标识，用于区分不同连接
     * - topic: 订阅的主题，支持通配符（+表示单层，#表示多层）
     * - qos: 服务质量等级（0: 最多一次, 1: 至少一次, 2: 恰好一次）
     *
     * @param factory MQTT客户端工厂
     * @return MessageProducer 消息生产者实例
     */
    @Bean
    public MessageProducer inbound() {
        // 创建MQTT入站通道适配器
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttProperties.getClientId() + "-inbound",  // 客户端ID
                        mqttClientFactory(),                          // 客户端工厂
                        mqttProperties.getTopic()                     // 订阅主题
                );

        // 设置输出通道，接收到的消息会发送到此通道
        adapter.setOutputChannel(mqttInputChannel());

        // 设置消息转换器
        adapter.setMessageConverter(new DefaultPahoMessageConverter());

        // 设置QoS服务质量等级
        adapter.setQos(mqttProperties.getQos());

        return adapter;
    }

    /**
     * MQTT消息出站处理器（生产者）
     *
     * 功能：
     * 1. 将系统生成的消息发送到MQTT服务器
     * 2. 用于向IoT设备下发控制指令
     *
     * @param factory MQTT客户端工厂
     * @return MessageHandler 消息处理器实例
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttOutputChannel")
    public MessageHandler outbound(MqttPahoClientFactory factory) {
        // 创建MQTT出站消息处理器
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                mqttProperties.getClientId() + "-outbound",  // 客户端ID
                factory                                       // 客户端工厂
        );

        // 设置默认主题，如果没有指定主题则使用此默认主题
        handler.setDefaultTopic(mqttProperties.getDefaultTopic());

        // 设置默认QoS
        handler.setDefaultQos(mqttProperties.getQos());

        return handler;
    }

    /**
     * MQTT配置属性类
     *
     * 功能：封装MQTT连接的所有配置参数
     * 对应application.yml中的zznursing.mqtt配置项
     *
     * @author zznursing
     * @since 1.0.0
     */
    public static class MqttProperties {

        /** MQTT服务器地址列表 */
        private String[] urls;

        /** 客户端ID */
        private String clientId;

        /** 订阅主题 */
        private String topic;

        /** 默认发布主题 */
        private String defaultTopic;

        /** 用户名 */
        private String username;

        /** 密码 */
        private String password;

        /** 服务质量等级 (0, 1, 2) */
        private int qos = 1;

        /** 连接超时时间（秒） */
        private int connectionTimeout = 30;

        /** 心跳间隔（秒） */
        private int keepAliveInterval = 60;

        /** 是否自动重连 */
        private boolean autoReconnect = true;

        /** 是否清理会话 */
        private boolean cleanSession = true;

        /** 最大重连延迟（秒） */
        private int maxReconnectDelay = 30;

        // Getter和Setter方法

        public String[] getUrls() {
            return urls;
        }

        public void setUrls(String[] urls) {
            this.urls = urls;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getDefaultTopic() {
            return defaultTopic;
        }

        public void setDefaultTopic(String defaultTopic) {
            this.defaultTopic = defaultTopic;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getQos() {
            return qos;
        }

        public void setQos(int qos) {
            this.qos = qos;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getKeepAliveInterval() {
            return keepAliveInterval;
        }

        public void setKeepAliveInterval(int keepAliveInterval) {
            this.keepAliveInterval = keepAliveInterval;
        }

        public boolean isAutoReconnect() {
            return autoReconnect;
        }

        public void setAutoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
        }

        public boolean isCleanSession() {
            return cleanSession;
        }

        public void setCleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
        }

        public int getMaxReconnectDelay() {
            return maxReconnectDelay;
        }

        public void setMaxReconnectDelay(int maxReconnectDelay) {
            this.maxReconnectDelay = maxReconnectDelay;
        }
    }
}
