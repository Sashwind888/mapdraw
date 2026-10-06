package sashwind.mc.plugin.mapdraw.network;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSettingsManager {

    // 记录玩家是否启用服务端的箱子菜单 GUI (默认 true)
    private final Map<UUID, Boolean> chestGuiEnabledMap = new ConcurrentHashMap<>();

    public boolean isChestGuiEnabled(Player player) {
        if (player == null) return true;
        return chestGuiEnabledMap.getOrDefault(player.getUniqueId(), true);
    }

    public void setChestGuiEnabled(Player player, boolean enabled) {
        if (player == null) return;
        chestGuiEnabledMap.put(player.getUniqueId(), enabled);
    }

    public void resetPlayer(Player player) {
        if (player == null) return;
        // 每次玩家重新连接进入服务器时，自动重置为默认启用 (true)
        chestGuiEnabledMap.remove(player.getUniqueId());
    }

    public void clear() {
        chestGuiEnabledMap.clear();
    }
}
