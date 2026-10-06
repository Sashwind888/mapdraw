package sashwind.mc.plugin.mapdraw.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.gui.GuiManager;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;

public class CanvasItemInteractListener implements Listener {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;
    private final GuiManager guiManager;

    public CanvasItemInteractListener(Mapdraw plugin, CanvasManager canvasManager, GuiManager guiManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return;
        }

        if (!CanvasNBTUtil.isCanvasMap(item)) {
            return;
        }

        CanvasData canvas = canvasManager.getCanvasFromItem(item);
        if (canvas == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("mapdraw.user")) {
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("no_permission"));
            return;
        }

        if (!plugin.isChestGuiEnabled(player)) {
            return; // 客户端 Mod 禁用了服务端的箱子菜单
        }

        event.setCancelled(true);
        guiManager.openMainMenu(player, canvas);
    }
}
