package sashwind.mc.plugin.mapdraw.compatibility;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CraftEngineHook {

    private static Boolean available = null;

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
                available = Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    public static ItemStack buildItem(String id, Player player) {
        if (!isAvailable() || id == null || id.trim().isEmpty()) {
            return null;
        }

        try {
            BukkitItemDefinition definition = CraftEngineItems.byId(id.trim());
            if (definition != null) {
                if (player != null) {
                    return definition.buildBukkitItem(player);
                } else {
                    return definition.buildBukkitItem();
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[MapDraw] 从 CraftEngine 获取物品 " + id + " 失败: " + t.getMessage());
        }
        return null;
    }

    public static String getCustomItemId(ItemStack item) {
        if (!isAvailable() || item == null) {
            return null;
        }

        try {
            if (CraftEngineItems.isCustomItem(item)) {
                Key key = CraftEngineItems.getCustomItemId(item);
                if (key != null) {
                    return key.toString(); // 比如 "mapdraw:pen"
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
