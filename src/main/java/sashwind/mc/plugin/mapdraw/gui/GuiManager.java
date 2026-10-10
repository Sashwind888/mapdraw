package sashwind.mc.plugin.mapdraw.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.tool.PlayerDrawSession;
import sashwind.mc.plugin.mapdraw.tool.ToolManager;
import sashwind.mc.plugin.mapdraw.tool.ToolType;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;
import sashwind.mc.plugin.mapdraw.util.ColorUtil;

import java.util.*;

public class GuiManager implements Listener {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;
    private final ToolManager toolManager;

    public static final String TITLE_PALETTE = "§8[MapDraw] 16色调色板";
    public static final String TITLE_MAIN = "§8[MapDraw] 画布控制台";

    // 存储玩家正在主菜单查看的画布
    private final Map<UUID, CanvasData> openCanvasMap = new HashMap<>();
    private final Map<UUID, Long> guiProtectConfirmTimes = new HashMap<>();

    public GuiManager(Mapdraw plugin, CanvasManager canvasManager, ToolManager toolManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
        this.toolManager = toolManager;
    }

    /**
     * 打开 16 色箱子调色板
     */
    public void openPaletteGui(Player player) {
        if (!plugin.isChestGuiEnabled(player)) {
            return; // 客户端 Mod 禁用了服务端的箱子菜单
        }
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PALETTE);
        PlayerDrawSession session = toolManager.getSession(player);

        // 放置 16 色 (在第 1、2 行中心放置)
        PaletteColor[] colors = PaletteColor.values();
        int[] slots = {
            1, 2, 3, 4, 5, 6, 7,
            10, 11, 12, 13, 14, 15, 16,
            19, 25
        };

        for (int i = 0; i < colors.length && i < slots.length; i++) {
            PaletteColor pc = colors[i];
            ItemStack item = new ItemStack(pc.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§f" + pc.getName() + " §7(" + pc.getHex() + ")");
            List<String> lore = new ArrayList<>();
            lore.add("§7点击选择该颜色作为当前画笔颜色");
            if (ColorUtil.toHexString(session.getCurrentColor()).equalsIgnoreCase(pc.getHex())) {
                lore.add("§a✔ 当前已选中此颜色");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        // 当前颜色展示
        ItemStack cur = new ItemStack(Material.NAME_TAG);
        ItemMeta curMeta = cur.getItemMeta();
        curMeta.setDisplayName("§b当前选定颜色: §f" + ColorUtil.toHexString(session.getCurrentColor()));
        cur.setItemMeta(curMeta);
        inv.setItem(22, cur);

        // 如果玩家当前已经打开了调色板，原地更新槽位，绝不关闭并重新打开窗口
        if (TITLE_PALETTE.equals(player.getOpenInventory().getTitle())
            && player.getOpenInventory().getTopInventory().getSize() == 27) {
            Inventory top = player.getOpenInventory().getTopInventory();
            for (int s = 0; s < 27; s++) {
                top.setItem(s, inv.getItem(s));
            }
            return;
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    /**
     * 打开画布控制主菜单
     */
    public void openMainMenu(Player player, CanvasData canvas) {
        if (!plugin.isChestGuiEnabled(player)) {
            return; // 客户端 Mod 禁用了服务端的箱子菜单
        }
        if (canvas == null) {
            ItemStack held = player.getInventory().getItemInMainHand();
            canvas = canvasManager.getCanvasFromItem(held);
            if (canvas == null) {
                canvas = canvasManager.getCanvasFromItem(player.getInventory().getItemInOffHand());
            }
        }

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MAIN);
        if (canvas != null) {
            openCanvasMap.put(player.getUniqueId(), canvas);
        }

        // 边框装饰
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
        inv.setItem(9, glass); inv.setItem(17, glass);
        inv.setItem(18, glass); inv.setItem(26, glass);
        inv.setItem(27, glass); inv.setItem(35, glass);
        inv.setItem(36, glass); inv.setItem(44, glass);

        // 1. 画布信息 / 新建地图按钮 (Slot 4)
        if (canvas != null) {
            ItemStack info = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = info.getItemMeta();
            meta.setDisplayName("§b§l画布信息: §f" + canvas.getTitle());
            List<String> lore = new ArrayList<>();
            lore.add("§7画布唯一ID: §8" + canvas.getId());
            lore.add("§7当前名称: §f" + canvas.getName());
            lore.add("§7画布描述: §7" + canvas.getDescription());
            lore.add("§7可用大小: §a" + canvas.getSize() + "x" + canvas.getSize());
            lore.add("§7保护状态: " + (canvas.isProtected() ? "§c已保护锁定" : "§a未保护"));
            lore.add("§7禁止拷贝: " + (canvas.isNoCopy() ? "§c开启" : "§7关闭"));
            lore.add("§7创建玩家: §e" + (canvas.getCreator() != null ? canvas.getCreator() : "未知"));
            meta.setLore(lore);
            info.setItemMeta(meta);
            inv.setItem(4, info);
        } else {
            double cost = plugin.getEconomyManager().getCreateCost();
            boolean bypass = player.hasPermission("mapdraw.admin.createwithnomoney");
            String costText = (bypass || !plugin.getEconomyManager().isEnabled() || cost <= 0) ? "§a免费" : ("§e" + cost + " 游戏币");
            inv.setItem(4, createItem(
                Material.MAP,
                "§a§l[+ 点击新建画布地图]",
                "§7当前未手持地图，点击直接创建新画布！",
                "§7默认尺寸: §e128x128",
                "§7创建费用: " + costText,
                "§e» 点击立即创建"
            ));
        }

        // 2. 工具选择 (Slot 19, 20, 21, 22)
        PlayerDrawSession session = toolManager.getSession(player);
        inv.setItem(19, createItem(Material.FEATHER, "§b获取/切换画笔", "§7点击切换当前工具为画笔", "§7当前: " + (session.getCurrentTool() == ToolType.PEN ? "§a选中" : "§8未选")));
        inv.setItem(20, createItem(Material.SHEARS, "§c获取/切换橡皮擦", "§7点击切换当前工具为橡皮擦", "§7当前: " + (session.getCurrentTool() == ToolType.ERASER ? "§a选中" : "§8未选")));
        inv.setItem(21, createItem(Material.WATER_BUCKET, "§e获取/切换油漆桶", "§7点击切换当前工具为油漆桶", "§7当前: " + (session.getCurrentTool() == ToolType.PAINTBUCKET ? "§a选中" : "§8未选")));
        inv.setItem(22, createItem(Material.STRUCTURE_VOID, "§7清空当前工具", "§7点击移除手中与选中的绘图工具"));

        // 3. 调色板入口 (Slot 24)
        inv.setItem(24, createItem(Material.PAINTING, "§6打开16色调色板", "§7当前选色: §f" + ColorUtil.toHexString(session.getCurrentColor()), "§e点击打开调色板菜单"));

        // 4. 撤销 / 重做 (Slot 29, 33)
        inv.setItem(29, createItem(Material.ARROW, "§a撤销上一步 (Undo)", "§7点击撤销一步绘图操作"));
        inv.setItem(33, createItem(Material.SPECTRAL_ARROW, "§b重做下一步 (Redo)", "§7点击重做一步绘图操作"));

        // 5. 保护模式与禁止拷贝 (Slot 38, 42)
        if (canvas != null) {
            if (canvas.isAnimated()) {
                inv.setItem(38, createItem(Material.BARRIER, "§c[GIF 动图永久锁定]", "§7该画作为 GIF 动图", "§c永久锁定保护，任何人都无法解除！"));
            } else {
                boolean isCreator = canvas.getCreator() != null && canvas.getCreator().equalsIgnoreCase(player.getName());
                boolean isAdmin = player.hasPermission("mapdraw.admin.deprotect");

                if (canvas.isProtected()) {
                    if (isCreator || isAdmin) {
                        inv.setItem(38, createItem(Material.ANVIL, "§e点击解除保护锁定", "§7你是该画布的作者或管理员", "§e点击解除锁定恢复可编辑状态"));
                    } else {
                        inv.setItem(38, createItem(Material.BEDROCK, "§c画布已被锁定保护", "§7该画布已被作者锁定", "§8(仅作者或管理员可解除)"));
                    }
                } else {
                    inv.setItem(38, createItem(Material.SHIELD, "§e点击锁定保护画布", "§c注意: 锁定后非作者或管理员将无法编辑！", "§c需2秒内点击两次以确认锁定"));
                }
            }

            inv.setItem(42, createItem(
                canvas.isNoCopy() ? Material.IRON_DOOR : Material.OAK_DOOR,
                "§6禁止拷贝设置: " + (canvas.isNoCopy() ? "§c开启" : "§a允许"),
                "§7点击切换是否允许在制图台中复制该地图",
                "§7当前状态: " + (canvas.isNoCopy() ? "§c禁止拷贝" : "§a允许拷贝")
            ));
        }

        // 6. 尺寸调节 / 未手持地图时快捷新建不同尺寸 (Slot 47, 48, 50, 51)
        if (canvas != null) {
            int curSize = canvas.getSize();
            inv.setItem(47, createItem(Material.IRON_NUGGET, "§f设置大小: 16x16", "§7当前尺寸: " + (curSize == 16 ? "§a✔ 16" : "§816"), "§e点击设置"));
            inv.setItem(48, createItem(Material.IRON_INGOT, "§f设置大小: 32x32", "§7当前尺寸: " + (curSize == 32 ? "§a✔ 32" : "§832"), "§e点击设置"));
            inv.setItem(50, createItem(Material.GOLD_INGOT, "§f设置大小: 64x64", "§7当前尺寸: " + (curSize == 64 ? "§a✔ 64" : "§864"), "§e点击设置"));
            inv.setItem(51, createItem(Material.DIAMOND, "§f设置大小: 128x128", "§7当前尺寸: " + (curSize == 128 ? "§a✔ 128" : "§8128"), "§e点击设置"));
        } else {
            inv.setItem(47, createItem(Material.IRON_NUGGET, "§a[+ 新建 16x16 画布]", "§7点击直接创建 16x16 规格的画布地图", "§e» 点击创建"));
            inv.setItem(48, createItem(Material.IRON_INGOT, "§a[+ 新建 32x32 画布]", "§7点击直接创建 32x32 规格的画布地图", "§e» 点击创建"));
            inv.setItem(50, createItem(Material.GOLD_INGOT, "§a[+ 新建 64x64 画布]", "§7点击直接创建 64x64 规格的画布地图", "§e» 点击创建"));
            inv.setItem(51, createItem(Material.DIAMOND, "§a[+ 新建 128x128 画布]", "§7点击直接创建 128x128 规格的画布地图", "§e» 点击创建"));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (TITLE_PALETTE.equals(title)) {
            // 点击外部或无效槽位直接忽略
            if (event.getRawSlot() < 0 || event.getClickedInventory() == null) {
                return;
            }
            event.setCancelled(true);

            // 仅响应顶部 27 格调色板槽位
            if (event.getRawSlot() >= 27) {
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            for (PaletteColor pc : PaletteColor.values()) {
                if (clicked.getType() == pc.getIcon()) {
                    PlayerDrawSession session = toolManager.getSession(player);
                    session.setColor(pc.getColor(), pc.getMapColorByte());
                    player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("color_set").replace("{color}", pc.getName() + " (" + pc.getHex() + ")"));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                    openPaletteGui(player); // 刷新
                    return;
                }
            }
        } else if (TITLE_MAIN.equals(title)) {
            // 点击外部丢弃物品或无效槽位直接忽略
            if (event.getRawSlot() < 0 || event.getClickedInventory() == null) {
                event.setCancelled(true);
                return;
            }

            // 仅响应顶部 54 格主菜单槽位，下半部分玩家背包槽位直接阻止取出或移动到菜单中
            int slot = event.getRawSlot();
            if (slot >= 54) {
                // 如果玩家尝试 Shift 点击将物品移入主菜单，取消事件
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            CanvasData canvas = openCanvasMap.get(player.getUniqueId());

            switch (slot) {
                case 4 -> {
                    if (canvas == null) {
                        int defaultSize = plugin.getConfig().getInt("canvas.default_size", 128);
                        handleCreateCanvasFromGui(player, defaultSize);
                    }
                }
                case 19 -> { // 画笔
                    toolManager.giveTool(player, ToolType.PEN);
                    player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("tool_given").replace("{tool}", "画笔"));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    openMainMenu(player, canvas);
                }
                case 20 -> { // 橡皮擦
                    toolManager.giveTool(player, ToolType.ERASER);
                    player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("tool_given").replace("{tool}", "橡皮擦"));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    openMainMenu(player, canvas);
                }
                case 21 -> { // 油漆桶
                    toolManager.giveTool(player, ToolType.PAINTBUCKET);
                    player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("tool_given").replace("{tool}", "油漆桶"));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    openMainMenu(player, canvas);
                }
                case 22 -> { // 清空工具
                    toolManager.clearTools(player);
                    player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("tool_cleared"));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                    openMainMenu(player, canvas);
                }
                case 24 -> { // 打开调色板
                    Bukkit.getScheduler().runTask(plugin, () -> openPaletteGui(player));
                }
                case 29 -> { // 撤销
                    if (canvas == null) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("hold_canvas_map"));
                        return;
                    }
                    if (canvas.isAnimated()) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("gif_locked"));
                        return;
                    }
                    if (canvas.isProtected()) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("canvas_protected"));
                        return;
                    }
                    if (canvas.undo()) {
                        canvasManager.notifyCanvasUpdated(canvas);
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("undo_success"));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    } else {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("undo_failed"));
                    }
                }
                case 33 -> { // 重做
                    if (canvas == null) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("hold_canvas_map"));
                        return;
                    }
                    if (canvas.isAnimated()) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("gif_locked"));
                        return;
                    }
                    if (canvas.isProtected()) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("canvas_protected"));
                        return;
                    }
                    if (canvas.redo()) {
                        canvasManager.notifyCanvasUpdated(canvas);
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("redo_success"));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    } else {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("redo_failed"));
                    }
                }
                case 38 -> { // 保护 / 解除保护
                    if (canvas == null) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("hold_canvas_map"));
                        return;
                    }
                    if (canvas.isAnimated()) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("gif_locked"));
                        return;
                    }
                    if (canvas.isProtected()) {
                        boolean isCreator = canvas.getCreator() != null && canvas.getCreator().equalsIgnoreCase(player.getName());
                        boolean isAdmin = player.hasPermission("mapdraw.admin.deprotect");

                        if (isCreator || isAdmin) {
                            canvas.setProtected(false);
                            updateHeldCanvasItemMeta(player, canvas);
                            canvasManager.notifyCanvasUpdated(canvas);
                            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("deprotected_success"));
                            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.7f, 1.2f);
                            openMainMenu(player, canvas);
                        } else {
                            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("deprotected_no_permission"));
                        }
                        return;
                    }

                    if (!player.hasPermission("mapdraw.user.protect")) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("no_permission"));
                        return;
                    }

                    boolean isAdmin = player.hasPermission("mapdraw.admin");
                    boolean isCreator = canvas.getCreator() != null && canvas.getCreator().equalsIgnoreCase(player.getName());
                    if (!isAdmin && !isCreator) {
                        player.sendMessage(plugin.getMessage("prefix") + "§c你不是该画布的作者或管理员，无法将其设置为保护模式！");
                        return;
                    }

                    long now = System.currentTimeMillis();
                    Long lastClick = guiProtectConfirmTimes.get(player.getUniqueId());
                    if (lastClick == null || (now - lastClick) > 2000L) {
                        guiProtectConfirmTimes.put(player.getUniqueId(), now);
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("protected_confirm"));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                        return;
                    }

                    guiProtectConfirmTimes.remove(player.getUniqueId());
                    canvas.setProtected(true);
                    updateHeldCanvasItemMeta(player, canvas);
                    canvasManager.notifyCanvasUpdated(canvas);
                    player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("protected_success"));
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.0f);
                    openMainMenu(player, canvas);
                }
                case 42 -> { // 禁止拷贝切换
                    if (canvas == null) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("hold_canvas_map"));
                        return;
                    }
                    if (canvas.isProtected()) {
                        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("canvas_protected"));
                        return;
                    }
                    canvas.setNoCopy(!canvas.isNoCopy());
                    updateHeldCanvasItemMeta(player, canvas);
                    canvasManager.notifyCanvasUpdated(canvas);
                    player.sendMessage(plugin.getMessage("prefix") + "§a已将禁止拷贝设置为: " + (canvas.isNoCopy() ? "§c开启" : "§a关闭"));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    openMainMenu(player, canvas);
                }
                case 47 -> {
                    if (canvas == null) {
                        handleCreateCanvasFromGui(player, 16);
                    } else {
                        changeSize(player, canvas, 16);
                    }
                }
                case 48 -> {
                    if (canvas == null) {
                        handleCreateCanvasFromGui(player, 32);
                    } else {
                        changeSize(player, canvas, 32);
                    }
                }
                case 50 -> {
                    if (canvas == null) {
                        handleCreateCanvasFromGui(player, 64);
                    } else {
                        changeSize(player, canvas, 64);
                    }
                }
                case 51 -> {
                    if (canvas == null) {
                        handleCreateCanvasFromGui(player, 128);
                    } else {
                        changeSize(player, canvas, 128);
                    }
                }
            }
        }
    }

    private void handleCreateCanvasFromGui(Player player, int size) {
        if (!player.hasPermission("mapdraw.user.create")) {
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("no_permission"));
            return;
        }

        boolean bypassMoney = player.hasPermission("mapdraw.admin.createwithnomoney");
        double cost = plugin.getEconomyManager().getCreateCost();
        if (!bypassMoney && plugin.getEconomyManager().isEnabled() && cost > 0) {
            if (!plugin.getEconomyManager().hasMoney(player, cost)) {
                String msg = plugin.getMessage("no_enough_money").replace("{cost}", String.valueOf(cost));
                player.sendMessage(plugin.getMessage("prefix") + msg);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            if (!plugin.getEconomyManager().withdraw(player, cost)) {
                player.sendMessage(plugin.getMessage("prefix") + "§c支付失败，无法创建画布！");
                return;
            }
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("map_create_cost").replace("{cost}", String.valueOf(cost)));
        }

        String name = "画布 #" + (int) (Math.random() * 9000 + 1000);
        ItemStack canvasItem = canvasManager.createNewCanvas(player, name, size);
        CanvasData newCanvas = canvasManager.getCanvasFromItem(canvasItem);

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(canvasItem);
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        }

        String createdMsg = plugin.getMessage("map_created")
            .replace("{name}", name)
            .replace("{size}", String.valueOf(size));
        player.sendMessage(plugin.getMessage("prefix") + createdMsg);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);

        // 立即展示刚创建的画布
        openMainMenu(player, newCanvas);
    }

    private void changeSize(Player player, CanvasData canvas, int targetSize) {
        if (canvas == null) {
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("hold_canvas_map"));
            return;
        }
        if (canvas.isAnimated()) {
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("gif_locked"));
            return;
        }
        if (canvas.isProtected()) {
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("canvas_protected"));
            return;
        }
        if (!player.hasPermission("mapdraw.user.data")) {
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("no_permission"));
            return;
        }
        int cur = canvas.getSize();
        if (cur == targetSize) {
            return;
        }
        if (!canvas.setSize(targetSize)) {
            String msg = plugin.getMessage("cannot_shrink_size")
                .replace("{current}", String.valueOf(cur))
                .replace("{target}", String.valueOf(targetSize));
            player.sendMessage(plugin.getMessage("prefix") + msg);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }
        updateHeldCanvasItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("size_updated").replace("{size}", String.valueOf(targetSize)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        openMainMenu(player, canvas);
    }

    public void updateHeldCanvasItemMeta(Player player, CanvasData canvas) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (CanvasNBTUtil.isCanvasMap(item)) {
            CanvasNBTUtil.applyCanvasMeta(item, canvas);
        } else {
            item = player.getInventory().getItemInOffHand();
            if (CanvasNBTUtil.isCanvasMap(item)) {
                CanvasNBTUtil.applyCanvasMeta(item, canvas);
            }
        }
    }
}
