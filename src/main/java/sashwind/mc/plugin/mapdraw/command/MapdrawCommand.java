package sashwind.mc.plugin.mapdraw.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.economy.EconomyManager;
import sashwind.mc.plugin.mapdraw.gui.GuiManager;
import sashwind.mc.plugin.mapdraw.tool.PlayerDrawSession;
import sashwind.mc.plugin.mapdraw.tool.ToolManager;
import sashwind.mc.plugin.mapdraw.tool.ToolType;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;
import sashwind.mc.plugin.mapdraw.util.ColorUtil;

import java.awt.Color;
import java.util.*;

public class MapdrawCommand implements CommandExecutor, TabCompleter {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;
    private final ToolManager toolManager;
    private final GuiManager guiManager;
    private final EconomyManager economyManager;

    private final Map<UUID, Long> protectConfirmTimes = new HashMap<>();

    public MapdrawCommand(Mapdraw plugin, CanvasManager canvasManager, ToolManager toolManager, GuiManager guiManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
        this.toolManager = toolManager;
        this.guiManager = guiManager;
        this.economyManager = economyManager;
    }

    private String getPrefix() {
        return plugin.getMessage("prefix");
    }

    private CanvasData getHeldCanvas(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        CanvasData canvas = canvasManager.getCanvasFromItem(held);
        if (canvas == null) {
            held = player.getInventory().getItemInOffHand();
            canvas = canvasManager.getCanvasFromItem(held);
        }
        return canvas;
    }

    private void updateHeldItemMeta(Player player, CanvasData canvas) {
        guiManager.updateHeldCanvasItemMeta(player, canvas);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleMenu(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "upload" -> handleUpload(sender, args);
            case "protect" -> handleProtect(sender);
            case "menu" -> handleMenu(sender);
            case "data" -> handleData(sender, args);
            case "draw" -> handleDraw(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> sender.sendMessage(getPrefix() + plugin.getMessage("invalid_args"));
        }
        return true;
    }

    // 1. /mdw create [name] [size]
    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getPrefix() + plugin.getMessage("only_player"));
            return;
        }
        if (!player.hasPermission("mapdraw.user.create")) {
            player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
            return;
        }

        String name = "未命名画布";
        int size = plugin.getConfig().getInt("canvas.default_size", 128);

        if (args.length >= 2) {
            name = args[1];
        }
        if (args.length >= 3) {
            try {
                size = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }

        // 经济收费判定
        boolean bypassMoney = player.hasPermission("mapdraw.admin.createwithnomoney");
        double cost = economyManager.getCreateCost();
        if (!bypassMoney && economyManager.isEnabled() && cost > 0) {
            if (!economyManager.hasMoney(player, cost)) {
                String msg = plugin.getMessage("no_enough_money").replace("{cost}", String.valueOf(cost));
                player.sendMessage(getPrefix() + msg);
                return;
            }
            if (!economyManager.withdraw(player, cost)) {
                player.sendMessage(getPrefix() + "§c支付失败，无法创建画布！");
                return;
            }
            player.sendMessage(getPrefix() + plugin.getMessage("map_create_cost").replace("{cost}", String.valueOf(cost)));
        }

        ItemStack canvasItem = canvasManager.createNewCanvas(player, name, size);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(canvasItem);
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        }

        String createdMsg = plugin.getMessage("map_created")
            .replace("{name}", name)
            .replace("{size}", String.valueOf(size));
        player.sendMessage(getPrefix() + createdMsg);
    }

    // 新增: /mdw upload <URL> [dither/none] [宽] [高]
    private void handleUpload(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getPrefix() + plugin.getMessage("only_player"));
            return;
        }
        if (!player.hasPermission("mapdraw.upload")) {
            player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(getPrefix() + "§c用法: /mdw upload <图片URL> [算法: dither/none] [宽] [高]");
            return;
        }

        String url = args[1].trim();
        String algorithm = "dither";
        int width = 1;
        int height = 1;

        if (args.length >= 3) {
            String arg2 = args[2].toLowerCase();
            if (arg2.equals("dither") || arg2.equals("none") || arg2.equals("floyd") || arg2.equals("fs") || arg2.equals("nearest")) {
                algorithm = arg2;
                if (args.length >= 4) {
                    try { width = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                }
                if (args.length >= 5) {
                    try { height = Integer.parseInt(args[4]); } catch (NumberFormatException ignored) {}
                }
            } else {
                try { width = Integer.parseInt(arg2); } catch (NumberFormatException ignored) {}
                if (args.length >= 4) {
                    try { height = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 校验规格限制
        int maxW = plugin.getConfig().getInt("upload.max_width", 5);
        int maxH = plugin.getConfig().getInt("upload.max_height", 5);
        int maxTotal = plugin.getConfig().getInt("upload.max_total_maps", 25);
        int totalMaps = width * height;

        if (width <= 0 || height <= 0 || width > maxW || height > maxH || totalMaps > maxTotal) {
            String limitMsg = plugin.getMessage("upload_size_exceeded")
                .replace("{max_w}", String.valueOf(maxW))
                .replace("{max_h}", String.valueOf(maxH))
                .replace("{max_total}", String.valueOf(maxTotal));
            player.sendMessage(getPrefix() + limitMsg);
            return;
        }

        // 经济收费判定 (按每张地图单价累加扣费)
        boolean bypassMoney = player.hasPermission("mapdraw.admin.createwithnomoney");
        double costPerMap = economyManager.getUploadCost();
        double totalCost = costPerMap * totalMaps;

        if (!bypassMoney && economyManager.isEnabled() && totalCost > 0) {
            if (!economyManager.hasMoney(player, totalCost)) {
                String msg = plugin.getMessage("no_enough_money_upload")
                    .replace("{count}", String.valueOf(totalMaps))
                    .replace("{cost}", String.valueOf(totalCost));
                player.sendMessage(getPrefix() + msg);
                return;
            }
        }

        player.sendMessage(getPrefix() + plugin.getMessage("uploading"));

        byte bgColor = (byte) plugin.getConfig().getInt("canvas.default_bg_color", 34);
        int maxFrames = plugin.getConfig().getInt("gif.max_frames", 80);
        int configFps = plugin.getConfig().getInt("gif.fps", 8);

        final String finalAlgo = algorithm;
        final int finalW = width;
        final int finalH = height;

        // 异步下载和转换像素，防止阻塞服务器主线程
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sashwind.mc.plugin.mapdraw.util.ImageProcessUtil.ProcessedImage processed =
                    sashwind.mc.plugin.mapdraw.util.ImageProcessUtil.downloadAndProcess(url, finalAlgo, finalW, finalH, bgColor, maxFrames);

                // 回到主线程创建并给予地图物品
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    // 扣款
                    if (!bypassMoney && economyManager.isEnabled() && totalCost > 0) {
                        if (!economyManager.withdraw(player, totalCost)) {
                            player.sendMessage(getPrefix() + "§c支付失败，无法生成画布！");
                            return;
                        }
                        String costMsg = plugin.getMessage("upload_cost")
                            .replace("{cost}", String.valueOf(totalCost))
                            .replace("{count}", String.valueOf(totalMaps));
                        player.sendMessage(getPrefix() + costMsg);
                    }

                    int baseId = (int) (Math.random() * 9000 + 1000);
                    String groupId = UUID.randomUUID().toString();

                    // 按行和列生成地图切片
                    for (sashwind.mc.plugin.mapdraw.util.ImageProcessUtil.MapSlice slice : processed.slices) {
                        String sliceName = (finalW == 1 && finalH == 1)
                            ? ("画作 #" + baseId + (processed.isAnimated ? " (动图)" : ""))
                            : String.format("画作 #%d [%d,%d]%s", baseId, slice.col, slice.row, processed.isAnimated ? " (动图)" : "");

                        ItemStack canvasItem = canvasManager.createNewCanvas(player, sliceName, 128);
                        CanvasData canvas = canvasManager.getCanvasFromItem(canvasItem);
                        if (canvas != null) {
                            canvas.setGroupId(groupId);
                            canvas.setPixels(slice.pixels);
                            canvas.setHasPainted(true);
                            if (slice.isAnimated) {
                                canvas.setFps(configFps);
                                canvas.setAnimationFrames(slice.animationFrames);
                                canvas.setProtected(true); // GIF 动图导入自动永久锁定保护，无法修改
                            }
                            if (finalW > 1 || finalH > 1) {
                                canvas.setDescription(String.format("拼接画作 (%dx%d) - 横第 %d 列, 纵第 %d 行%s",
                                    finalW, finalH, slice.col, slice.row, slice.isAnimated ? (" [共 " + processed.frameCount + " 帧，动图锁定]") : ""));
                            } else if (slice.isAnimated) {
                                canvas.setDescription("GIF 动态画作 (共 " + processed.frameCount + " 帧，" + configFps + " FPS，动图锁定)");
                            }
                            CanvasNBTUtil.applyCanvasMeta(canvasItem, canvas);
                            canvasManager.notifyCanvasUpdated(canvas);
                        }

                        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(canvasItem);
                        if (!overflow.isEmpty()) {
                            for (ItemStack item : overflow.values()) {
                                player.getWorld().dropItem(player.getLocation(), item);
                            }
                        }
                    }

                    String successMsg = plugin.getMessage("upload_success")
                        .replace("{count}", String.valueOf(totalMaps))
                        .replace("{width}", String.valueOf(finalW))
                        .replace("{height}", String.valueOf(finalH));
                    if (processed.isAnimated) {
                        successMsg += " §b[动态GIF: " + processed.frameCount + " 帧, " + configFps + " FPS]";
                    }
                    player.sendMessage(getPrefix() + successMsg);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                });
            } catch (Exception e) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        String errMsg = plugin.getMessage("upload_failed").replace("{error}", e.getMessage() != null ? e.getMessage() : "未知错误");
                        player.sendMessage(getPrefix() + errMsg);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                    }
                });
            }
        });
    }

    // 2. /mdw protect
    private void handleProtect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getPrefix() + plugin.getMessage("only_player"));
            return;
        }
        if (!player.hasPermission("mapdraw.user.protect")) {
            player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
            return;
        }

        CanvasData canvas = getHeldCanvas(player);
        if (canvas == null) {
            player.sendMessage(getPrefix() + plugin.getMessage("hold_canvas_map"));
            return;
        }

        if (canvas.isAnimated()) {
            player.sendMessage(getPrefix() + plugin.getMessage("gif_locked"));
            return;
        }

        if (canvas.isProtected()) {
            player.sendMessage(getPrefix() + plugin.getMessage("canvas_protected"));
            return;
        }

        boolean isAdmin = player.hasPermission("mapdraw.admin");
        boolean isCreator = canvas.getCreator() != null && canvas.getCreator().equalsIgnoreCase(player.getName());
        if (!isAdmin && !isCreator) {
            player.sendMessage(getPrefix() + "§c你不是该画布的作者或管理员，无法将其设置为保护模式！");
            return;
        }

        long now = System.currentTimeMillis();
        Long lastTime = protectConfirmTimes.get(player.getUniqueId());
        if (lastTime == null || (now - lastTime) > 2000L) {
            protectConfirmTimes.put(player.getUniqueId(), now);
            player.sendMessage(getPrefix() + plugin.getMessage("protected_confirm"));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            return;
        }

        protectConfirmTimes.remove(player.getUniqueId());
        canvas.setProtected(true);
        updateHeldItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        player.sendMessage(getPrefix() + plugin.getMessage("protected_success"));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
    }

    // 3. /mdw menu
    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getPrefix() + plugin.getMessage("only_player"));
            return;
        }
        if (!player.hasPermission("mapdraw.user")) {
            player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
            return;
        }

        CanvasData canvas = getHeldCanvas(player);
        guiManager.openMainMenu(player, canvas);
    }

    // 4. /mdw data [get/set]
    private void handleData(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getPrefix() + plugin.getMessage("only_player"));
            return;
        }
        if (!player.hasPermission("mapdraw.user.data")) {
            player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
            return;
        }

        CanvasData canvas = getHeldCanvas(player);
        if (canvas == null) {
            player.sendMessage(getPrefix() + plugin.getMessage("hold_canvas_map"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(getPrefix() + "§c用法: /mdw data <get/set ...>");
            return;
        }

        String action = args[1].toLowerCase();
        if (action.equals("get")) {
            // 聊天框打印信息，同时更新手持地图物品描述
            updateHeldItemMeta(player, canvas);
            player.sendMessage("§8§m-----------------§b [画布信息] §8§m-----------------");
            player.sendMessage("§7画布唯一ID: §8" + canvas.getId());
            player.sendMessage("§7画布名称: §f" + canvas.getName());
            player.sendMessage("§7当前标题: §e" + canvas.getTitle());
            player.sendMessage("§7画布描述: §7" + canvas.getDescription());
            player.sendMessage("§7画板大小: §a" + canvas.getSize() + "x" + canvas.getSize());
            player.sendMessage("§7保护状态: " + (canvas.isProtected() ? "§c已锁定保护 (无法编辑)" : "§a可编辑"));
            player.sendMessage("§7禁止拷贝: " + (canvas.isNoCopy() ? "§c是 (防复制)" : "§7否"));
            player.sendMessage("§7创建玩家: §b" + (canvas.getCreator() != null ? canvas.getCreator() : "未知"));
            player.sendMessage("§8§m------------------------------------------------");
        } else if (action.equals("set")) {
            if (canvas.isAnimated()) {
                player.sendMessage(getPrefix() + plugin.getMessage("gif_locked"));
                return;
            }
            if (canvas.isProtected()) {
                player.sendMessage(getPrefix() + plugin.getMessage("canvas_protected"));
                return;
            }
            if (args.length < 4) {
                player.sendMessage(getPrefix() + "§c用法: /mdw data set <title/description/size> <内容>");
                return;
            }
            String target = args[2].toLowerCase();
            String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            switch (target) {
                case "title" -> {
                    String title = ChatColor.translateAlternateColorCodes('&', value);
                    canvas.setTitle(title);
                    updateHeldItemMeta(player, canvas);
                    canvasManager.notifyCanvasUpdated(canvas);
                    player.sendMessage(getPrefix() + plugin.getMessage("title_updated").replace("{title}", title));
                }
                case "description" -> {
                    String desc = ChatColor.translateAlternateColorCodes('&', value);
                    canvas.setDescription(desc);
                    updateHeldItemMeta(player, canvas);
                    canvasManager.notifyCanvasUpdated(canvas);
                    player.sendMessage(getPrefix() + plugin.getMessage("description_updated").replace("{description}", desc));
                }
                case "size" -> {
                    try {
                        int newSize = Integer.parseInt(value);
                        int cur = canvas.getSize();
                        if (!canvas.setSize(newSize)) {
                            String msg = plugin.getMessage("cannot_shrink_size")
                                .replace("{current}", String.valueOf(cur))
                                .replace("{target}", String.valueOf(newSize));
                            player.sendMessage(getPrefix() + msg);
                            return;
                        }
                        updateHeldItemMeta(player, canvas);
                        canvasManager.notifyCanvasUpdated(canvas);
                        player.sendMessage(getPrefix() + plugin.getMessage("size_updated").replace("{size}", String.valueOf(newSize)));
                    } catch (NumberFormatException e) {
                        player.sendMessage(getPrefix() + "§c无效的尺寸数字！");
                    }
                }
                default -> player.sendMessage(getPrefix() + "§c未知的设置项，支持: title, description, size");
            }
        }
    }

    // 5. /mdw draw <color/tool/undo/redo>
    private void handleDraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getPrefix() + plugin.getMessage("only_player"));
            return;
        }
        if (!player.hasPermission("mapdraw.user.draw")) {
            player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(getPrefix() + "§c用法: /mdw draw <color/tool/undo/redo>");
            return;
        }

        String sub = args[1].toLowerCase();
        PlayerDrawSession session = toolManager.getSession(player);

        switch (sub) {
            case "color" -> {
                if (args.length < 3) {
                    // 不填参数弹箱子菜单，16色
                    guiManager.openPaletteGui(player);
                } else {
                    String colStr = args[2];

                    sashwind.mc.plugin.mapdraw.gui.PaletteColor matchedPalette = null;
                    for (sashwind.mc.plugin.mapdraw.gui.PaletteColor pc : sashwind.mc.plugin.mapdraw.gui.PaletteColor.values()) {
                        if (pc.name().equalsIgnoreCase(colStr) || pc.getName().equalsIgnoreCase(colStr) || pc.getHex().equalsIgnoreCase(colStr)) {
                            matchedPalette = pc;
                            break;
                        }
                    }

                    if (matchedPalette != null) {
                        session.setColor(matchedPalette.getColor(), matchedPalette.getMapColorByte());
                        player.sendMessage(getPrefix() + plugin.getMessage("color_set").replace("{color}", matchedPalette.getName() + " (" + matchedPalette.getHex() + ")"));
                        return;
                    }

                    Color color = ColorUtil.parseColor(colStr);
                    if (color == null) {
                        player.sendMessage(getPrefix() + plugin.getMessage("color_invalid"));
                        return;
                    }
                    session.setCurrentColor(color);
                    player.sendMessage(getPrefix() + plugin.getMessage("color_set").replace("{color}", ColorUtil.toHexString(color)));
                }
            }
            case "tool" -> {
                if (args.length < 3) {
                    toolManager.clearTools(player);
                    player.sendMessage(getPrefix() + plugin.getMessage("tool_cleared"));
                    return;
                }
                String tStr = args[2];
                ToolType tool = ToolType.fromString(tStr);
                if (tool == null || tool == ToolType.NONE) {
                    toolManager.clearTools(player);
                    player.sendMessage(getPrefix() + plugin.getMessage("tool_cleared"));
                } else {
                    toolManager.giveTool(player, tool);
                    player.sendMessage(getPrefix() + plugin.getMessage("tool_given").replace("{tool}", tool.getDisplayName()));
                }
            }
            case "undo" -> {
                if (!player.hasPermission("mapdraw.user.draw.undo")) {
                    player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
                    return;
                }
                CanvasData canvas = getHeldCanvas(player);
                if (canvas == null) {
                    player.sendMessage(getPrefix() + plugin.getMessage("hold_canvas_map"));
                    return;
                }
                if (canvas.isAnimated()) {
                    player.sendMessage(getPrefix() + plugin.getMessage("gif_locked"));
                    return;
                }
                if (canvas.isProtected()) {
                    player.sendMessage(getPrefix() + plugin.getMessage("canvas_protected"));
                    return;
                }
                if (canvas.undo()) {
                    canvasManager.notifyCanvasUpdated(canvas);
                    player.sendMessage(getPrefix() + plugin.getMessage("undo_success"));
                } else {
                    player.sendMessage(getPrefix() + plugin.getMessage("undo_failed"));
                }
            }
            case "redo" -> {
                if (!player.hasPermission("mapdraw.user.draw.redo")) {
                    player.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
                    return;
                }
                CanvasData canvas = getHeldCanvas(player);
                if (canvas == null) {
                    player.sendMessage(getPrefix() + plugin.getMessage("hold_canvas_map"));
                    return;
                }
                if (canvas.isAnimated()) {
                    player.sendMessage(getPrefix() + plugin.getMessage("gif_locked"));
                    return;
                }
                if (canvas.isProtected()) {
                    player.sendMessage(getPrefix() + plugin.getMessage("canvas_protected"));
                    return;
                }
                if (canvas.redo()) {
                    canvasManager.notifyCanvasUpdated(canvas);
                    player.sendMessage(getPrefix() + plugin.getMessage("redo_success"));
                } else {
                    player.sendMessage(getPrefix() + plugin.getMessage("redo_failed"));
                }
            }
            default -> player.sendMessage(getPrefix() + "§c未知绘图指令，支持: color, tool, undo, redo");
        }
    }

    // 6. /mdw admin <deprotect/reload>
    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getPrefix() + "§c用法: /mdw admin <deprotect/reload>");
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "deprotect" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(getPrefix() + plugin.getMessage("only_player"));
                    return;
                }
                CanvasData canvas = getHeldCanvas(player);
                if (canvas == null) {
                    player.sendMessage(getPrefix() + plugin.getMessage("hold_canvas_map"));
                    return;
                }

                if (canvas.isAnimated()) {
                    player.sendMessage(getPrefix() + plugin.getMessage("gif_locked"));
                    return;
                }

                // 被锁定的地图画只能被作者或管理员解锁
                boolean isAdmin = player.hasPermission("mapdraw.admin.deprotect");
                boolean isCreator = canvas.getCreator() != null && canvas.getCreator().equalsIgnoreCase(player.getName());

                if (!isAdmin && !isCreator) {
                    player.sendMessage(getPrefix() + plugin.getMessage("deprotected_no_permission"));
                    return;
                }

                canvas.setProtected(false);
                updateHeldItemMeta(player, canvas);
                canvasManager.notifyCanvasUpdated(canvas);
                player.sendMessage(getPrefix() + plugin.getMessage("deprotected_success"));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
            }
            case "reload" -> {
                if (!sender.hasPermission("mapdraw.admin.reload")) {
                    sender.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
                    return;
                }
                plugin.reloadConfig();
                if (plugin.getAnimationManager() != null) {
                    plugin.getAnimationManager().start();
                }
                canvasManager.syncAllLoadedFrames();
                sender.sendMessage(getPrefix() + plugin.getMessage("config_reloaded"));
            }
            case "debug" -> {
                if (!sender.hasPermission("mapdraw.admin")) {
                    sender.sendMessage(getPrefix() + plugin.getMessage("no_permission"));
                    return;
                }
                boolean current = plugin.getConfig().getBoolean("canvas.debug", false);
                plugin.getConfig().set("canvas.debug", !current);
                plugin.saveConfig();
                sender.sendMessage(getPrefix() + "§a调试模式已" + (!current ? "§e开启 §7(每次点击展示框将在动作栏显示朝向和像素坐标)" : "§c关闭"));
            }
            default -> sender.sendMessage(getPrefix() + "§c未知管理指令，支持: deprotect, reload, debug");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        String header = plugin.getConfig().getString("help.header", "&8§m-----------------&b [MapDraw 帮助手册] &8§m-----------------");
        String footer = plugin.getConfig().getString("help.footer", "&8§m-------------------------------------------------------");
        List<String> userLines = plugin.getConfig().getStringList("help.user_lines");
        List<String> adminLines = plugin.getConfig().getStringList("help.admin_lines");

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', header.replace("{label}", label)));

        for (String line : userLines) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line.replace("{label}", label)));
        }

        if (sender.hasPermission("mapdraw.admin")) {
            for (String line : adminLines) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line.replace("{label}", label)));
            }
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', footer.replace("{label}", label)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            String[] subs = {"create", "upload", "protect", "menu", "data", "draw", "admin", "help"};
            for (String s : subs) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "data" -> {
                    for (String s : new String[]{"get", "set"}) {
                        if (s.startsWith(args[1].toLowerCase())) list.add(s);
                    }
                }
                case "draw" -> {
                    for (String s : new String[]{"color", "tool", "undo", "redo"}) {
                        if (s.startsWith(args[1].toLowerCase())) list.add(s);
                    }
                }
                case "admin" -> {
                    for (String s : new String[]{"deprotect", "reload", "debug"}) {
                        if (s.startsWith(args[1].toLowerCase())) list.add(s);
                    }
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String sub2 = args[1].toLowerCase();
            if (sub.equals("data") && sub2.equals("set")) {
                for (String s : new String[]{"title", "description", "size"}) {
                    if (s.startsWith(args[2].toLowerCase())) list.add(s);
                }
            } else if (sub.equals("draw") && sub2.equals("tool")) {
                for (String s : new String[]{"pen", "eraser", "paintbucket", "null"}) {
                    if (s.startsWith(args[2].toLowerCase())) list.add(s);
                }
            } else if (sub.equals("draw") && sub2.equals("color")) {
                list.addAll(Arrays.asList("#FF0000", "#00FF00", "#0000FF", "#FFFF00", "&a", "&c", "&b", "red", "blue", "green", "black", "white"));
            } else if (sub.equals("create")) {
                list.addAll(Arrays.asList("16", "32", "64", "128"));
            } else if (sub.equals("upload")) {
                list.addAll(Arrays.asList("dither", "none", "1", "2", "3"));
            }
        } else if (args.length == 4) {
            String sub = args[0].toLowerCase();
            String sub2 = args[1].toLowerCase();
            String sub3 = args[2].toLowerCase();
            if (sub.equals("data") && sub2.equals("set") && sub3.equals("size")) {
                for (String s : new String[]{"16", "32", "64", "128"}) {
                    if (s.startsWith(args[3].toLowerCase())) list.add(s);
                }
            } else if (sub.equals("upload")) {
                list.addAll(Arrays.asList("1", "2", "3", "4"));
            }
        } else if (args.length == 5) {
            String sub = args[0].toLowerCase();
            if (sub.equals("upload")) {
                list.addAll(Arrays.asList("1", "2", "3", "4"));
            }
        }
        return list;
    }
}
