package com.zznursing.ai;

import com.baidu.cloud.qianfan.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI对话会话管理服务
 *
 * 功能说明：
 * 1. 管理用户与AI的对话会话
 * 2. 维护多轮对话的上下文历史
 * 3. 会话生命周期管理（创建、查询、删除）
 * 4. 会话过期自动清理
 *
 * 会话结构：
 * - 每个会话包含唯一的会话ID
 * - 会话关联用户ID
 * - 会话保存历史对话消息
 * - 会话记录创建时间和最后活动时间
 *
 * 使用场景：
 * - 智慧养老系统中，老人与AI助手的多轮对话
 * - 保持对话上下文，提供连贯的问答体验
 *
 * @author zznursing
 * @since 1.0.0
 */
@Service
public class ChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSessionService.class);

    /**
     * AI客户端，用于调用百度千帆API
     */
    private final QianfanAIClient qianfanAIClient;

    /**
     * Prompt构建器，用于构建养老场景的系统提示词
     */
    private final ElderlyCarePromptBuilder promptBuilder;

    /**
     * 会话存储
     * Key: 会话ID
     * Value: 会话信息
     *
     * 实际项目中应存储在Redis中，支持分布式环境
     */
    private final ConcurrentHashMap<String, ChatSession> sessionStore = new ConcurrentHashMap<>();

    /**
     * 默认最大历史消息数
     * 防止历史消息过长导致token超限
     */
    private static final int DEFAULT_MAX_HISTORY_SIZE = 20;

    /**
     * 会话过期时间（秒）
     * 默认30分钟
     */
    private static final long DEFAULT_SESSION_EXPIRE_SECONDS = 1800;

    /**
     * 构造函数
     *
     * @param qianfanAIClient AI客户端
     * @param promptBuilder Prompt构建器
     */
    public ChatSessionService(QianfanAIClient qianfanAIClient,
                               ElderlyCarePromptBuilder promptBuilder) {
        this.qianfanAIClient = qianfanAIClient;
        this.promptBuilder = promptBuilder;
    }

    /**
     * 创建新的对话会话
     *
     * 功能：为用户创建新的对话会话，初始化系统提示词
     *
     * @param userId 用户ID
     * @return 会话ID
     */
    public String createSession(String userId) {
        logger.info("创建新的对话会话: userId={}", userId);

        // 生成唯一会话ID
        String sessionId = UUID.randomUUID().toString();

        // 创建会话对象
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActiveAt(LocalDateTime.now());
        session.setHistoryMessages(new ArrayList<>());
        session.setMaxHistorySize(DEFAULT_MAX_HISTORY_SIZE);

        // 添加系统提示词
        String systemPrompt = promptBuilder.buildSystemPrompt();
        Message systemMessage = Message.builder()
                .role("system")
                .content(systemPrompt)
                .build();
        session.getHistoryMessages().add(systemMessage);

        // 存储会话
        sessionStore.put(sessionId, session);

        logger.info("对话会话创建成功: sessionId={}, userId={}", sessionId, userId);
        return sessionId;
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 会话信息，如果不存在则返回null
     */
    public ChatSession getSession(String sessionId) {
        return sessionStore.get(sessionId);
    }

    /**
     * 发送消息并获取AI回复
     *
     * 功能：处理用户消息，维护上下文，获取AI回复
     *
     * @param sessionId 会话ID
     * @param userMessage 用户消息
     * @return AI回复内容
     */
    public String sendMessage(String sessionId, String userMessage) {
        logger.info("处理用户消息: sessionId={}, message={}", sessionId,
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

        // 获取会话
        ChatSession session = sessionStore.get(sessionId);
        if (session == null) {
            logger.warn("会话不存在: sessionId={}", sessionId);
            return "会话已过期，请重新开始对话。";
        }

        // 更新最后活动时间
        session.setLastActiveAt(LocalDateTime.now());

        // 创建用户消息
        Message userMsg = Message.builder()
                .role("user")
                .content(userMessage)
                .build();

        // 添加到历史消息
        session.getHistoryMessages().add(userMsg);

        // 检查历史消息数量，超过限制则删除最早的消息（保留系统提示词）
        trimHistoryIfNeeded(session);

        // 调用AI获取回复
        String aiReply = qianfanAIClient.chatWithHistory(session.getHistoryMessages());

        // 创建AI回复消息
        Message aiMsg = Message.builder()
                .role("assistant")
                .content(aiReply)
                .build();

        // 添加到历史消息
        session.getHistoryMessages().add(aiMsg);

        logger.info("AI回复生成成功: sessionId={}, replyLength={}", sessionId, aiReply.length());
        return aiReply;
    }

    /**
     * 流式发送消息
     *
     * @param sessionId 会话ID
     * @param userMessage 用户消息
     * @param callback 流式回调
     */
    public void sendMessageStream(String sessionId, String userMessage,
                                   QianfanAIClient.StreamCallback callback) {
        logger.info("处理流式用户消息: sessionId={}", sessionId);

        // 获取会话
        ChatSession session = sessionStore.get(sessionId);
        if (session == null) {
            logger.warn("会话不存在: sessionId={}", sessionId);
            callback.onStream("会话已过期，请重新开始对话。");
            callback.onComplete();
            return;
        }

        // 更新最后活动时间
        session.setLastActiveAt(LocalDateTime.now());

        // 创建用户消息
        Message userMsg = Message.builder()
                .role("user")
                .content(userMessage)
                .build();

        // 添加到历史消息
        session.getHistoryMessages().add(userMsg);

        // 检查历史消息数量
        trimHistoryIfNeeded(session);

        // 创建包装回调，用于捕获完整回复
        StringBuilder fullReply = new StringBuilder();
        QianfanAIClient.StreamCallback wrappedCallback = new QianfanAIClient.StreamCallback() {
            @Override
            public void onStream(String content) {
                fullReply.append(content);
                callback.onStream(content);
            }

            @Override
            public void onComplete() {
                // 将完整回复添加到历史消息
                Message aiMsg = Message.builder()
                        .role("assistant")
                        .content(fullReply.toString())
                        .build();
                session.getHistoryMessages().add(aiMsg);
                callback.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        };

        // 调用流式AI
        qianfanAIClient.chatStream(session.getHistoryMessages(), wrappedCallback);
    }

    /**
     * 清空会话历史
     *
     * 功能：清空会话的历史消息，只保留系统提示词
     *
     * @param sessionId 会话ID
     */
    public void clearSession(String sessionId) {
        logger.info("清空会话历史: sessionId={}", sessionId);

        ChatSession session = sessionStore.get(sessionId);
        if (session != null) {
            // 只保留系统提示词
            List<Message> newHistory = new ArrayList<>();
            if (!session.getHistoryMessages().isEmpty() &&
                "system".equals(session.getHistoryMessages().get(0).getRole())) {
                newHistory.add(session.getHistoryMessages().get(0));
            }
            session.setHistoryMessages(newHistory);
        }
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    public void deleteSession(String sessionId) {
        logger.info("删除会话: sessionId={}", sessionId);
        sessionStore.remove(sessionId);
    }

    /**
     * 获取用户的活跃会话
     *
     * @param userId 用户ID
     * @return 会话ID，如果没有活跃会话则创建新会话
     */
    public String getOrCreateSession(String userId) {
        // 查找用户的活跃会话
        for (Map.Entry<String, ChatSession> entry : sessionStore.entrySet()) {
            ChatSession session = entry.getValue();
            if (session.getUserId().equals(userId) && !isSessionExpired(session)) {
                return session.getSessionId();
            }
        }

        // 没有活跃会话，创建新会话
        return createSession(userId);
    }

    /**
     * 检查会话是否过期
     *
     * @param session 会话信息
     * @return 是否过期
     */
    private boolean isSessionExpired(ChatSession session) {
        long elapsed = java.time.Duration.between(
                session.getLastActiveAt(), LocalDateTime.now()).getSeconds();
        return elapsed > DEFAULT_SESSION_EXPIRE_SECONDS;
    }

    /**
     * 裁剪历史消息，保持在最大限制内
     *
     * @param session 会话信息
     */
    private void trimHistoryIfNeeded(ChatSession session) {
        List<Message> history = session.getHistoryMessages();
        int maxSize = session.getMaxHistorySize();

        // 如果历史消息超过限制，删除最早的消息（保留系统提示词）
        while (history.size() > maxSize) {
            // 保留第一条（系统提示词），删除第二条
            if (history.size() > 1) {
                history.remove(1);
            } else {
                break;
            }
        }
    }

    /**
     * 清理过期会话
     *
     * 功能：定期调用，清理所有过期的会话
     */
    public void cleanExpiredSessions() {
        logger.info("开始清理过期会话...");

        int removedCount = 0;
        for (String sessionId : sessionStore.keySet()) {
            ChatSession session = sessionStore.get(sessionId);
            if (session != null && isSessionExpired(session)) {
                sessionStore.remove(sessionId);
                removedCount++;
            }
        }

        logger.info("过期会话清理完成: removedCount={}, remainingCount={}",
                removedCount, sessionStore.size());
    }

    /**
     * 获取会话数量
     *
     * @return 当前活跃会话数量
     */
    public int getSessionCount() {
        return sessionStore.size();
    }

    /**
     * 会话信息类
     *
     * 功能：封装对话会话的详细信息
     */
    public static class ChatSession {
        /** 会话ID */
        private String sessionId;

        /** 用户ID */
        private String userId;

        /** 历史消息列表 */
        private List<Message> historyMessages;

        /** 最大历史消息数 */
        private int maxHistorySize;

        /** 创建时间 */
        private LocalDateTime createdAt;

        /** 最后活动时间 */
        private LocalDateTime lastActiveAt;

        // Getter和Setter方法

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public List<Message> getHistoryMessages() {
            return historyMessages;
        }

        public void setHistoryMessages(List<Message> historyMessages) {
            this.historyMessages = historyMessages;
        }

        public int getMaxHistorySize() {
            return maxHistorySize;
        }

        public void setMaxHistorySize(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getLastActiveAt() {
            return lastActiveAt;
        }

        public void setLastActiveAt(LocalDateTime lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
        }

        @Override
        public String toString() {
            return "ChatSession{" +
                    "sessionId='" + sessionId + '\'' +
                    ", userId='" + userId + '\'' +
                    ", historySize=" + (historyMessages != null ? historyMessages.size() : 0) +
                    ", createdAt=" + createdAt +
                    ", lastActiveAt=" + lastActiveAt +
                    '}';
        }
    }
}
