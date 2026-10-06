package sashwind.mc.plugin.mapdraw.listener;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;

public class MapCopyProtectionListener implements Listener {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;

    public MapCopyProtectionListener(Mapdraw plugin, CanvasManager canvasManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
    }

    private boolean isProtectedFromCopy(ItemStack item) {
        if (!CanvasNBTUtil.isCanvasMap(item)) {
            return false;
        }
        CanvasData canvas = canvasManager.getCanvasFromItem(item);
        return canvas != null && canvas.isNoCopy();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareResult(PrepareResultEvent event) {
        Inventory inv = event.getInventory();
        if (inv instanceof CartographyInventory cartoInv) {
            ItemStack mapSlot = cartoInv.getItem(0);
            ItemStack extraSlot = cartoInv.getItem(1);

            // 如果设置了禁止拷贝，阻止制图台复制
            if (isProtectedFromCopy(mapSlot) || isProtectedFromCopy(extraSlot)) {
                event.setResult(null);
                return;
            }

            // 如果允许拷贝：确保在制图台预览时显示完整副本
            if (CanvasNBTUtil.isCanvasMap(mapSlot) && extraSlot != null && extraSlot.getType() == org.bukkit.Material.MAP) {
                CanvasData parent = canvasManager.getCanvasFromItem(mapSlot);
                if (parent != null) {
                    ItemStack preview = mapSlot.clone();
                    event.setResult(preview);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getType() == InventoryType.CARTOGRAPHY) {
            // 如果玩家试图点击取走复制结果
            if (event.getSlotType() == InventoryType.SlotType.RESULT) {
                ItemStack mapSlot = inv.getItem(0);
                if (isProtectedFromCopy(mapSlot)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("copy_forbidden"));
                    }
                    return;
                }

                // 复制出来的地图画作为新的副本，独立拥有全新 MapView 和 CanvasData，不修改母本的数据
                if (CanvasNBTUtil.isCanvasMap(mapSlot) && event.getWhoClicked() instanceof Player player) {
                    CanvasData parent = canvasManager.getCanvasFromItem(mapSlot);
                    if (parent != null) {
                        ItemStack extraSlot = inv.getItem(1);
                        if (extraSlot != null && extraSlot.getType() == org.bukkit.Material.MAP) {
                            event.setCancelled(true);

                            // 扣除制图台材料
                            extraSlot.setAmount(extraSlot.getAmount() - 1);
                            inv.setItem(1, extraSlot.getAmount() > 0 ? extraSlot : null);

                            // 生成独立的新副本
                            ItemStack clonedItem = canvasManager.createCloneCanvas(player, parent);

                            // 放入玩家光标或背包
                            if (event.getCursor() == null || event.getCursor().getType().isAir()) {
                                event.getView().setCursor(clonedItem);
                            } else {
                                player.getInventory().addItem(clonedItem);
                            }

                            player.playSound(player.getLocation(), org.bukkit.Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack canvasSource = null;
        for (ItemStack matrixItem : event.getInventory().getMatrix()) {
            if (isProtectedFromCopy(matrixItem)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("copy_forbidden"));
                }
                return;
            }
            if (CanvasNBTUtil.isCanvasMap(matrixItem)) {
                canvasSource = matrixItem;
            }
        }

        // 如果在工作台通过空白地图复制：拦截并转换为独立新副本
        if (canvasSource != null && event.getWhoClicked() instanceof Player player) {
            CanvasData parent = canvasManager.getCanvasFromItem(canvasSource);
            if (parent != null) {
                event.setCancelled(true);
                // 扣除工作台消耗并生成独立副本
                event.getInventory().clear();
                ItemStack clonedItem = canvasManager.createCloneCanvas(player, parent);
                player.getInventory().addItem(canvasSource.clone()); // 归还母本
                player.getInventory().addItem(clonedItem); // 给予独立副本
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            }
        }
    }
}
