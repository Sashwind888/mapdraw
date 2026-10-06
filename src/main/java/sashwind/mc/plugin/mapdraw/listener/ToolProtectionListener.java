package sashwind.mc.plugin.mapdraw.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.tool.ToolManager;

public class ToolProtectionListener implements Listener {

    private final Mapdraw plugin;
    private final ToolManager toolManager;

    public ToolProtectionListener(Mapdraw plugin, ToolManager toolManager) {
        this.plugin = plugin;
        this.toolManager = toolManager;
    }

    // 1. 禁止用于合成
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (toolManager.isDrawingTool(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (toolManager.isDrawingTool(item)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(plugin.getMessage("prefix") + "§c绘图工具不能用于合成！");
                }
                return;
            }
        }
    }

    // 2. 禁止铁砧与锻造台
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        if (toolManager.isDrawingTool(event.getInventory().getItem(0)) ||
            toolManager.isDrawingTool(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (toolManager.isDrawingTool(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    // 3. 禁止熔炉烧炼与燃料
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (toolManager.isDrawingTool(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (toolManager.isDrawingTool(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    // 4. 阻止油漆桶倒水/装水
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (toolManager.isDrawingTool(event.getPlayer().getInventory().getItemInMainHand()) ||
            toolManager.isDrawingTool(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (toolManager.isDrawingTool(event.getPlayer().getInventory().getItemInMainHand()) ||
            toolManager.isDrawingTool(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    // 5. 阻止发射器发射
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (toolManager.isDrawingTool(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // 6. 丢弃清空：玩家丢出工具时直接销毁并提示已清空当前工具
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (toolManager.isDrawingTool(dropped.getItemStack())) {
            dropped.remove(); // 销毁掉落物
            toolManager.getSession(event.getPlayer()).setCurrentTool(null);
            event.getPlayer().sendMessage(plugin.getMessage("prefix") + plugin.getMessage("tool_cleared"));
        }
    }

    // 7. 工具耐久保护
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (toolManager.isDrawingTool(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
