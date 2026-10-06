package sashwind.mc.plugin.mapdraw.compatibility;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import sashwind.mc.plugin.mapdraw.Mapdraw;

public class ProtocolLibHook {

    private static Boolean available = null;
    private final Mapdraw plugin;

    public ProtocolLibHook(Mapdraw plugin) {
        this.plugin = plugin;
    }

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("com.comphenix.protocol.ProtocolLibrary");
                available = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    public void registerListeners() {
        if (!isAvailable()) {
            return;
        }

        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();

            // 监听客户端右键实体交互入包 (USE_ENTITY)
            // 在网络到达的第 0 个时刻立即捕获，规避事件循环排队延迟
            pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null || !player.isOnline()) return;

                    int entityId = event.getPacket().getIntegers().read(0);

                    // 必须调度回主线程执行实体查找与绘制，避免触碰 Purpur/Spigot 的 AsyncCatcher 异步安全检查
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        Entity entity = findEntityById(player, entityId);
                        if (entity instanceof ItemFrame frame) {
                            sashwind.mc.plugin.mapdraw.api.MapDrawProvider.get().drawOnFrame(player, frame);
                        }
                    });
                }
            });

            // 核心安全防护：拦截任何发往客户端的越界槽位包 (彻底根除客户端 IndexOutOfBoundsException for length 46 崩溃)
            pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.SET_SLOT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null || !player.isOnline()) return;

                    try {
                        int windowId = event.getPacket().getIntegers().read(0);
                        int slot = event.getPacket().getIntegers().read(2);

                        // -1 代表玩家光标槽位，属于合法
                        if (slot == -1) {
                            return;
                        }

                        // 如果发给玩家自身背包窗口 (windowId == 0)
                        if (windowId == 0) {
                            // 原版自身背包长度严格固定为 46 (槽位 0~45)
                            if (slot < 0 || slot >= 46) {
                                event.setCancelled(true);
                            }
                            return;
                        }

                        // 如果发给打开的箱子容器窗口
                        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
                        if (view != null && view.getTopInventory() != null) {
                            int topSize = view.getTopInventory().getSize();
                            int totalSlots = topSize + 36;
                            if (slot < 0 || slot >= totalSlots) {
                                event.setCancelled(true);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });

            plugin.getLogger().info("ProtocolLib 高级网络包优化与毫秒级入包拦截已激活！");
        } catch (Throwable t) {
            plugin.getLogger().warning("初始化 ProtocolLib 监听器失败: " + t.getMessage());
        }
    }

    private Entity findEntityById(Player player, int entityId) {
        for (Entity e : player.getNearbyEntities(6.0, 6.0, 6.0)) {
            if (e.getEntityId() == entityId) {
                return e;
            }
        }
        return null;
    }
}
