package com.dracave.title.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 统一调度器工具，兼容 Paper 和 Folia。
 * Paper 1.19.4+ 的 API 已包含 GlobalRegionScheduler / AsyncScheduler，
 * 在标准 Paper 上这些调度器委托到主线程，在 Folia 上使用区域化调度。
 */
public final class SchedulerUtil {

    private SchedulerUtil() {
    }

    /**
     * 可取消的任务句柄，统一包装 BukkitTask 和 Folia ScheduledTask。
     */
    public interface Task {
        void cancel();
    }

    /**
     * 在主线程（Paper）或全局区域线程（Folia）上立即执行。
     */
    public static void runTask(Plugin plugin, Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    /**
     * 在实体（玩家）所属区域线程上执行，Folia 下玩家实体操作必须走这里。
     */
    public static void runTaskEntity(Player player, Plugin plugin, Runnable runnable) {
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    /**
     * 在异步线程上立即执行。
     */
    public static void runTaskAsynchronously(Plugin plugin, Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    /**
     * 在主线程上延迟执行。
     */
    public static void runTaskLater(Plugin plugin, Runnable runnable, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delayTicks);
    }

    /**
     * 在主线程上定时执行，返回可取消的任务句柄。
     */
    public static Task runTaskTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        var scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), delayTicks, periodTicks);
        return scheduled::cancel;
    }

    /**
     * 在异步线程上定时执行，返回可取消的任务句柄。
     */
    public static Task runTaskTimerAsynchronously(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        var scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(), delayMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        return scheduled::cancel;
    }
}
