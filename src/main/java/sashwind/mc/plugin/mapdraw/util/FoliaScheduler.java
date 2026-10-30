package sashwind.mc.plugin.mapdraw.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * Folia / Paper / Spigot 跨平台统一调度器工具层
 */
public final class FoliaScheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        IS_FOLIA = folia;
    }

    private FoliaScheduler() {
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * 统一任务取消接口封装
     */
    public interface TaskWrapper {
        void cancel();
        boolean isCancelled();
    }

    private static class ScheduledTaskWrapper implements TaskWrapper {
        private final io.papermc.paper.threadedregions.scheduler.ScheduledTask task;

        ScheduledTaskWrapper(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return task != null && task.isCancelled();
        }
    }

    private static class BukkitTaskWrapper implements TaskWrapper {
        private final BukkitTask task;

        BukkitTaskWrapper(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return task != null && task.isCancelled();
        }
    }

    /**
     * 异步立即执行任务 (AsyncScheduler)
     */
    public static TaskWrapper runAsync(Plugin plugin, Runnable runnable) {
        if (IS_FOLIA) {
            return new ScheduledTaskWrapper(Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run()));
        } else {
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
        }
    }

    /**
     * 异步延迟执行任务
     */
    public static TaskWrapper runAsyncLater(Plugin plugin, Runnable runnable, long delay, TimeUnit unit) {
        if (IS_FOLIA) {
            return new ScheduledTaskWrapper(Bukkit.getAsyncScheduler().runDelayed(plugin, task -> runnable.run(), delay, unit));
        } else {
            long ticks = Math.max(1L, unit.toMillis(delay) / 50L);
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, ticks));
        }
    }

    /**
     * 异步定时重复执行任务 (用于动画高频定时运算)
     */
    public static TaskWrapper runTimerAsync(Plugin plugin, Runnable runnable, long initialDelayMs, long periodMs) {
        if (IS_FOLIA) {
            return new ScheduledTaskWrapper(Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> runnable.run(),
                initialDelayMs,
                periodMs,
                TimeUnit.MILLISECONDS
            ));
        } else {
            long initTicks = Math.max(1L, initialDelayMs / 50L);
            long periodTicks = Math.max(1L, periodMs / 50L);
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                runnable,
                initTicks,
                periodTicks
            ));
        }
    }

    /**
     * 在实体所属的 EntityScheduler 上运行任务 (适用于玩家、展示框等实体状态更新)
     */
    public static TaskWrapper runAtEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (entity == null || !entity.isValid()) {
            return null;
        }
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st =
                entity.getScheduler().run(plugin, task -> runnable.run(), null);
            return st != null ? new ScheduledTaskWrapper(st) : null;
        } else {
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTask(plugin, runnable));
        }
    }

    /**
     * 在实体所属的 EntityScheduler 上延迟运行任务 (delayTicks 单位: tick)
     */
    public static TaskWrapper runAtEntityLater(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || !entity.isValid()) {
            return null;
        }
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st =
                entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, Math.max(1L, delayTicks));
            return st != null ? new ScheduledTaskWrapper(st) : null;
        } else {
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1L, delayTicks)));
        }
    }

    /**
     * 在指定区块/位置所属的 RegionScheduler 上执行任务
     */
    public static TaskWrapper runAtLocation(Plugin plugin, Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().execute(plugin, location, runnable);
            return null;
        } else {
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTask(plugin, runnable));
        }
    }

    /**
     * 在指定区块/位置所属的 RegionScheduler 上延迟执行任务 (delayTicks 单位: tick)
     */
    public static TaskWrapper runAtLocationLater(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st =
                Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> runnable.run(), Math.max(1L, delayTicks));
            return st != null ? new ScheduledTaskWrapper(st) : null;
        } else {
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1L, delayTicks)));
        }
    }

    /**
     * 在全局区域调度器 (GlobalRegionScheduler) 上执行任务
     */
    public static TaskWrapper runGlobal(Plugin plugin, Runnable runnable) {
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st =
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run());
            return st != null ? new ScheduledTaskWrapper(st) : null;
        } else {
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTask(plugin, runnable));
        }
    }

    /**
     * 在全局区域调度器 (GlobalRegionScheduler) 上延迟执行任务 (delayTicks 单位: tick)
     */
    public static TaskWrapper runGlobalLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st =
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), Math.max(1L, delayTicks));
            return st != null ? new ScheduledTaskWrapper(st) : null;
        } else {
            return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1L, delayTicks)));
        }
    }
}
