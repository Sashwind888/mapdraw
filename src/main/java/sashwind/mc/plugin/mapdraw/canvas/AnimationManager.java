package sashwind.mc.plugin.mapdraw.canvas;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import sashwind.mc.plugin.mapdraw.Mapdraw;

import java.util.*;

public class AnimationManager {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;
    private BukkitTask asyncTickerTask;

    public AnimationManager(Mapdraw plugin, CanvasManager canvasManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("gif.enabled", true)) {
            return;
        }

        stop();

        // 采用后台异步多线程定时调度，彻底从服务端主线程中剥离高频计算
        asyncTickerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // 若无玩家在线，自动进入完全休眠状态，零开销
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }

            long nowMs = System.currentTimeMillis();
            int defaultFps = plugin.getConfig().getInt("gif.fps", 8);

            // 1. 按 groupId 将动画切片分组归类（同一个多联拼接大图拥有相同 groupId）
            Map<String, List<CanvasData>> groupMap = new HashMap<>();
            List<CanvasData> standaloneList = new ArrayList<>();

            for (CanvasData canvas : canvasManager.getAllCanvases()) {
                if (!canvas.isAnimated()) {
                    continue;
                }
                String gid = canvas.getGroupId();
                if (gid != null && !gid.isEmpty()) {
                    groupMap.computeIfAbsent(gid, k -> new ArrayList<>()).add(canvas);
                } else {
                    standaloneList.add(canvas);
                }
            }

            List<CanvasData> updatedCanvases = new ArrayList<>();

            // 2. 同组多切片原子级同步更新：统一基准时间戳，所有切片在同一时刻绝对同频推进
            for (List<CanvasData> group : groupMap.values()) {
                boolean anyAdvanced = false;
                for (CanvasData slice : group) {
                    if (slice.syncAnimationFrame(nowMs, defaultFps)) {
                        anyAdvanced = true;
                    }
                }
                if (anyAdvanced) {
                    updatedCanvases.addAll(group);
                }
            }

            // 3. 独立单张动图同步更新
            for (CanvasData canvas : standaloneList) {
                if (canvas.syncAnimationFrame(nowMs, defaultFps)) {
                    updatedCanvases.add(canvas);
                }
            }

            // 4. 将需要更新的切片批量提交至主线程渲染器标记脏（纯内存更新，绝不触碰磁盘写文件）
            if (!updatedCanvases.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (CanvasData c : updatedCanvases) {
                        canvasManager.notifyCanvasRenderOnly(c);
                    }
                });
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (asyncTickerTask != null) {
            asyncTickerTask.cancel();
            asyncTickerTask = null;
        }
    }
}
