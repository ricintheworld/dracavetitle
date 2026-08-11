package com.dracave.title.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * GUI 动画槽位差异刷新缓存。
 * 记录每个槽位上次渲染的 MiniMessage 字符串，只在内容变化时才触发 inventory.setItem，
 * 避免每 2 tick 全量发送物品更新包。
 */
public final class GuiRefreshCache {
    private final Map<Integer, String> lastRendered = new HashMap<>();

    /**
     * 判断指定槽位的渲染内容是否发生变化。
     * 若变化则更新缓存并返回 true，否则返回 false。
     */
    public boolean checkAndUpdate(int slot, String currentRender) {
        String last = lastRendered.get(slot);
        if (last == null || !last.equals(currentRender)) {
            lastRendered.put(slot, currentRender);
            return true;
        }
        return false;
    }

    /**
     * 在页面切换或重新渲染时清空缓存，使所有槽位在下次刷新时强制更新。
     */
    public void clear() {
        lastRendered.clear();
    }
}
