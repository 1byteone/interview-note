package com.mewcode.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工具注册表 — 统一管理所有已注册的 {@link Tool} 实例。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>使用 LinkedHashMap 保持插入顺序（首次出现顺序即为 Tool 列表展示顺序）</li>
 *   <li>支持按名称查找、列出全部、按名称移除</li>
 *   <li>线程安全：读操作返回不可变视图，写操作同步加锁</li>
 * </ul>
 */
public class ToolRegistry {

    /** name -> Tool 实例映射（有序） */
    private final Map<String, Tool> tools = Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * 注册一个工具。若同名工具已存在，则覆盖旧实例。
     *
     * @param tool 要注册的工具
     */
    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * 按名称查找工具。
     *
     * @param name 工具名
     * @return Optional 包裹的 Tool 实例
     */
    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 列出所有已注册的工具（按注册顺序）。
     *
     * @return 不可变的工具列表
     */
    public List<Tool> listAll() {
        return List.copyOf(tools.values());
    }

    /**
     * 按名称移除一个工具。
     *
     * @param name 要移除的工具名
     * @return 被移除的 Tool（若不存在则返回 null）
     */
    public Tool unregister(String name) {
        return tools.remove(name);
    }

    /**
     * 获取已注册工具的总数。
     *
     * @return 工具数量
     */
    public int size() {
        return tools.size();
    }
}
