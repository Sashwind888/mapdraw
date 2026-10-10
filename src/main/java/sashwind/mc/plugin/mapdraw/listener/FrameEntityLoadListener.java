package sashwind.mc.plugin.mapdraw.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;

public class FrameEntityLoadListener implements Listener {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;

    public FrameEntityLoadListener(Mapdraw plugin, CanvasManager canvasManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            // 延后到下一个 tick 执行，避开 Paper/Purpur (Moonrise) 区块切片状态更新期间禁止修改展示框物品/边界框的限制
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!frame.isValid()) {
                    return;
                }
                ItemStack item = frame.getItem();
                if (CanvasNBTUtil.isCanvasMap(item)) {
                    CanvasData canvas = canvasManager.getCanvasFromItem(item);
                    if (canvas != null) {
                        CanvasNBTUtil.applyCanvasMeta(item, canvas);
                        // 仅关闭展示框实体的悬浮名牌
                        frame.setCustomNameVisible(false);
                        frame.customName(null);
                        frame.setItem(item, false);

                        if (item.getItemMeta() instanceof MapMeta meta && meta.hasMapView()) {
                            canvasManager.checkAndAttachRenderer(meta.getMapView(), canvas);
                        }
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 每次玩家重新连接进入服务器时，自动重置箱子菜单状态为默认开启 (true)
        plugin.getPlayerSettingsManager().resetPlayer(event.getPlayer());
    }
}
