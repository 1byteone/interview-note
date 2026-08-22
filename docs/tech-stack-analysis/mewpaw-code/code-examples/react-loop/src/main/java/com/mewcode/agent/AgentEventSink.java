package com.mewcode.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Agent 事件发射器 — 负责将 {@link AgentEvent} 广播给所有已注册的监听者。
 * <p>
 * 线程安全：内部使用 {@link CopyOnWriteArrayList}，适合读多写少场景。
 * 典型消费者：日志记录器、UI 面板、指标采集器。
 */
public class AgentEventSink {

    /** 已注册的事件监听者列表（线程安全副本写入） */
    private final List<Consumer<AgentEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 注册一个事件监听者。
     *
     * @param listener 接收所有 AgentEvent 的回调
     */
    public void addListener(Consumer<AgentEvent> listener) {
        this.listeners.add(listener);
    }

    /**
     * 移除一个已注册的事件监听者。
     *
     * @param listener 之前通过 {@link #addListener} 注册的回调
     */
    public void removeListener(Consumer<AgentEvent> listener) {
        this.listeners.remove(listener);
    }

    /**
     * 向所有监听者广播一个事件。
     * <p>
     * 任何一个监听者抛出异常都不会影响其他监听者的接收（异常被捕获并打印到 stderr）。
     *
     * @param event 要广播的 AgentEvent
     */
    public void emit(AgentEvent event) {
        for (Consumer<AgentEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                System.err.println("[AgentEventSink] 监听者异常: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
