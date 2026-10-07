package sashwind.mc.plugin.mapdraw.api;

import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.canvas.DrawingEngine;
import sashwind.mc.plugin.mapdraw.economy.EconomyManager;
import sashwind.mc.plugin.mapdraw.gui.GuiManager;
import sashwind.mc.plugin.mapdraw.tool.PlayerDrawSession;
import sashwind.mc.plugin.mapdraw.tool.ToolManager;
import sashwind.mc.plugin.mapdraw.tool.ToolType;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;
import sashwind.mc.plugin.mapdraw.util.ItemFrameRayTraceUtil;

import java.awt.Color;
import java.awt.Point;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MapDrawAPIImpl implements MapDrawAPI {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;
    private final ToolManager toolManager;
    private final GuiManager guiManager;
    private final EconomyManager economyManager;
    private final Map<UUID, Long> protectConfirmTimes = new ConcurrentHashMap<>();

    public MapDrawAPIImpl(Mapdraw plugin, CanvasManager canvasManager, ToolManager toolManager, GuiManager guiManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
        this.toolManager = toolManager;
        this.guiManager = guiManager;
        this.economyManager = economyManager;
    }

    @Override
    public CanvasData getCanvas(String id) {
        return canvasManager.getCanvasById(id);
    }

    @Override
    public CanvasData getCanvasByMapId(int mapId) {
        return canvasManager.getCanvasByMapId(mapId);
    }

    @Override
    public CanvasData getCanvasFromItem(ItemStack item) {
        return canvasManager.getCanvasFromItem(item);
    }

    @Override
    public CanvasData getCanvasFromFrame(ItemFrame frame) {
        if (frame == null) return null;
        return canvasManager.getCanvasFromItem(frame.getItem());
    }

    @Override
    public Collection<CanvasData> getAllCanvases() {
        return canvasManager.getAllCanvases();
    }

    @Override
    public DrawResult createCanvas(Player player, String name, int size) {
        if (player == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家不可为空");
        }
        if (!player.hasPermission("mapdraw.user.create")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }

        boolean bypassMoney = player.hasPermission("mapdraw.admin.createwithnomoney");
        double cost = economyManager.getCreateCost();
        if (!bypassMoney && economyManager.isEnabled() && cost > 0) {
            if (!economyManager.hasMoney(player, cost)) {
                String msg = plugin.getMessage("no_enough_money").replace("{cost}", String.valueOf(cost));
                return DrawResult.failure(DrawResult.Status.INSUFFICIENT_FUNDS, msg);
            }
            if (!economyManager.withdraw(player, cost)) {
                return DrawResult.failure(DrawResult.Status.ERROR, "经济扣费失败");
            }
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("map_create_cost").replace("{cost}", String.valueOf(cost)));
        }

        String finalName = (name != null && !name.trim().isEmpty()) ? name : "未命名画布";
        int finalSize = size > 0 ? size : plugin.getConfig().getInt("canvas.default_size", 128);

        ItemStack canvasItem = canvasManager.createNewCanvas(player, finalName, finalSize);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(canvasItem);
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        }

        String createdMsg = plugin.getMessage("map_created")
            .replace("{name}", finalName)
            .replace("{size}", String.valueOf(finalSize));
        return DrawResult.success(createdMsg);
    }

    @Override
    public DrawResult cloneCanvas(Player player, CanvasData parent) {
        if (player == null || parent == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.create")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (parent.isNoCopy() && !player.hasPermission("mapdraw.admin")) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("copy_forbidden"));
        }

        ItemStack clonedItem = canvasManager.createCloneCanvas(player, parent);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(clonedItem);
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        }
        return DrawResult.success("副本创建成功");
    }

    @Override
    public DrawResult drawPixel(Player player, CanvasData canvas, int px, int py, ToolType tool, byte color) {
        if (player == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家不可为空");
        }
        if (canvas == null) {
            return DrawResult.failure(DrawResult.Status.CANVAS_NOT_FOUND, "未指定目标画布");
        }
        if (!player.hasPermission("mapdraw.user.draw")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        boolean success = DrawingEngine.applyDraw(canvas, px, py, tool, color);
        if (success) {
            canvasManager.notifyCanvasUpdated(canvas);
            return DrawResult.success();
        }
        return DrawResult.failure(DrawResult.Status.ERROR, "绘制坐标越界或工具无效");
    }

    @Override
    public DrawResult drawPixels(Player player, CanvasData canvas, List<Point> points, ToolType tool, byte color) {
        if (player == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家不可为空");
        }
        if (canvas == null) {
            return DrawResult.failure(DrawResult.Status.CANVAS_NOT_FOUND, "未指定目标画布");
        }
        if (points == null || points.isEmpty()) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "没有要绘制的点");
        }
        if (!player.hasPermission("mapdraw.user.draw")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        // 整批只记一个撤销快照：客户端「一笔」= 一步撤销，而不是一个点一步
        canvas.pushUndoState();

        int applied = 0;
        for (Point point : points) {
            if (point == null) {
                continue;
            }
            if (DrawingEngine.applyDrawNoUndo(canvas, point.x, point.y, tool, color)) {
                applied++;
            }
        }

        if (applied == 0) {
            return DrawResult.failure(DrawResult.Status.ERROR, "绘制坐标越界或工具无效");
        }

        // 整批只通知一次：一次地图包发送 + 一次异步存盘（否则 4096 点的包会发 4096 次）
        canvasManager.notifyCanvasUpdated(canvas);
        return DrawResult.success();
    }

    @Override
    public DrawResult drawOnFrame(Player player, ItemFrame frame) {
        if (player == null || frame == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        CanvasData canvas = getCanvasFromFrame(frame);
        if (canvas == null) {
            return DrawResult.failure(DrawResult.Status.CANVAS_NOT_FOUND, "展示框内无有效画布地图");
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        ToolType tool = toolManager.getToolType(held);
        if (tool == ToolType.NONE) {
            tool = toolManager.getToolType(player.getInventory().getItemInOffHand());
        }

        if (tool == ToolType.NONE) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家未手持绘图工具");
        }

        Point surfacePixel = ItemFrameRayTraceUtil.getSurfacePixelFromRayTrace(player, frame);
        if (surfacePixel == null) {
            return DrawResult.failure(DrawResult.Status.ERROR, "视线未命中展示框");
        }

        PlayerDrawSession session = toolManager.getSession(player);
        byte eraserColor = (byte) plugin.getConfig().getInt("canvas.default_bg_color", 34);
        byte colorToDraw = (tool == ToolType.ERASER) ? eraserColor : session.getCurrentMapColor();

        long strokeTimeoutMs = plugin.getConfig().getLong("canvas.stroke_timeout_ms", 400L);
        double maxDistance = plugin.getConfig().getDouble("canvas.max_stroke_distance", 800.0);

        Vector curHitPos = ItemFrameRayTraceUtil.getWorldHitPosFromRayTrace(player, frame);
        if (curHitPos == null) {
            curHitPos = frame.getLocation().toVector();
        }

        boolean success = sashwind.mc.plugin.mapdraw.canvas.CrossFrameDrawer.drawWithCrossFrameSupport(
            plugin, player, frame, canvas, curHitPos, surfacePixel, tool, colorToDraw, strokeTimeoutMs, maxDistance
        );

        if (success) {
            return DrawResult.success();
        }
        return DrawResult.failure(DrawResult.Status.ERROR, "绘制失败");
    }

    @Override
    public DrawResult undo(Player player, CanvasData canvas) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.draw.undo")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        if (canvas.undo()) {
            canvasManager.notifyCanvasUpdated(canvas);
            return DrawResult.success(plugin.getMessage("undo_success"));
        }
        return DrawResult.failure(DrawResult.Status.ERROR, plugin.getMessage("undo_failed"));
    }

    @Override
    public DrawResult redo(Player player, CanvasData canvas) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.draw.redo")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        if (canvas.redo()) {
            canvasManager.notifyCanvasUpdated(canvas);
            return DrawResult.success(plugin.getMessage("redo_success"));
        }
        return DrawResult.failure(DrawResult.Status.ERROR, plugin.getMessage("redo_failed"));
    }

    @Override
    public DrawResult protectCanvas(Player player, CanvasData canvas, boolean force) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.protect")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        // 鉴权：只有作者或管理员有权将画布锁定为保护模式
        boolean isAdmin = player.hasPermission("mapdraw.admin");
        boolean isCreator = canvas.getCreator() != null && canvas.getCreator().equalsIgnoreCase(player.getName());
        if (!isAdmin && !isCreator) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, "你不是该画布的作者或管理员，无法将其设置为保护模式！");
        }

        if (!force) {
            long now = System.currentTimeMillis();
            Long lastTime = protectConfirmTimes.get(player.getUniqueId());
            if (lastTime == null || (now - lastTime) > 2000L) {
                protectConfirmTimes.put(player.getUniqueId(), now);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                return DrawResult.failure(DrawResult.Status.ERROR, plugin.getMessage("protected_confirm"));
            }
            protectConfirmTimes.remove(player.getUniqueId());
        }

        canvas.setProtected(true);
        guiManager.updateHeldCanvasItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
        return DrawResult.success(plugin.getMessage("protected_success"));
    }

    @Override
    public DrawResult deprotectCanvas(Player player, CanvasData canvas) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }

        boolean isAdmin = player.hasPermission("mapdraw.admin.deprotect");
        boolean isCreator = canvas.getCreator() != null && canvas.getCreator().equalsIgnoreCase(player.getName());

        if (!isAdmin && !isCreator) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("deprotected_no_permission"));
        }

        canvas.setProtected(false);
        guiManager.updateHeldCanvasItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
        return DrawResult.success(plugin.getMessage("deprotected_success"));
    }

    @Override
    public DrawResult setTitle(Player player, CanvasData canvas, String title) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.data")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        canvas.setTitle(title != null ? title : "");
        guiManager.updateHeldCanvasItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        return DrawResult.success(plugin.getMessage("title_updated").replace("{title}", canvas.getTitle()));
    }

    @Override
    public DrawResult setDescription(Player player, CanvasData canvas, String description) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.data")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        canvas.setDescription(description != null ? description : "");
        guiManager.updateHeldCanvasItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        return DrawResult.success(plugin.getMessage("description_updated").replace("{description}", canvas.getDescription()));
    }

    @Override
    public DrawResult setSize(Player player, CanvasData canvas, int size) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.data")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isAnimated()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("gif_locked"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        int cur = canvas.getSize();
        if (cur == size) {
            return DrawResult.success();
        }

        if (!canvas.setSize(size)) {
            String msg = plugin.getMessage("cannot_shrink_size")
                .replace("{current}", String.valueOf(cur))
                .replace("{target}", String.valueOf(size));
            return DrawResult.failure(DrawResult.Status.CANNOT_SHRINK, msg);
        }

        guiManager.updateHeldCanvasItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        return DrawResult.success(plugin.getMessage("size_updated").replace("{size}", String.valueOf(size)));
    }

    @Override
    public DrawResult setNoCopy(Player player, CanvasData canvas, boolean noCopy) {
        if (player == null || canvas == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "参数不可为空");
        }
        if (!player.hasPermission("mapdraw.user.data")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }
        if (canvas.isProtected()) {
            return DrawResult.failure(DrawResult.Status.CANVAS_PROTECTED, plugin.getMessage("canvas_protected"));
        }

        canvas.setNoCopy(noCopy);
        guiManager.updateHeldCanvasItemMeta(player, canvas);
        canvasManager.notifyCanvasUpdated(canvas);
        return DrawResult.success("禁止拷贝状态已设置为: " + (noCopy ? "开启" : "关闭"));
    }

    @Override
    public DrawResult setColor(Player player, Color color, byte mapColorByte) {
        if (player == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家不可为空");
        }
        if (!player.hasPermission("mapdraw.user.draw")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }

        PlayerDrawSession session = toolManager.getSession(player);
        session.setColor(color, mapColorByte);
        return DrawResult.success();
    }

    @Override
    public DrawResult setTool(Player player, ToolType tool) {
        if (player == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家不可为空");
        }
        if (!player.hasPermission("mapdraw.user.draw")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }

        if (tool == null || tool == ToolType.NONE) {
            toolManager.clearTools(player);
            return DrawResult.success(plugin.getMessage("tool_cleared"));
        } else {
            toolManager.giveTool(player, tool);
            return DrawResult.success(plugin.getMessage("tool_given").replace("{tool}", tool.getDisplayName()));
        }
    }

    @Override
    public DrawResult openMenu(Player player, CanvasData canvas) {
        if (player == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家不可为空");
        }
        if (!player.hasPermission("mapdraw.user")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }

        guiManager.openMainMenu(player, canvas);
        return DrawResult.success();
    }

    @Override
    public DrawResult openPalette(Player player) {
        if (player == null) {
            return DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "玩家不可为空");
        }
        if (!player.hasPermission("mapdraw.user.draw")) {
            return DrawResult.failure(DrawResult.Status.NO_PERMISSION, plugin.getMessage("no_permission"));
        }

        guiManager.openPaletteGui(player);
        return DrawResult.success();
    }

    @Override
    public void setChestGuiEnabled(Player player, boolean enabled) {
        plugin.getPlayerSettingsManager().setChestGuiEnabled(player, enabled);
    }

    @Override
    public boolean isChestGuiEnabled(Player player) {
        return plugin.getPlayerSettingsManager().isChestGuiEnabled(player);
    }
}
