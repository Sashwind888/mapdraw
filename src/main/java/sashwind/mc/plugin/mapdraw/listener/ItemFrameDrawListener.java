package sashwind.mc.plugin.mapdraw.listener;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasCoordinateAdapter;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.canvas.DrawingEngine;
import sashwind.mc.plugin.mapdraw.tool.PlayerDrawSession;
import sashwind.mc.plugin.mapdraw.tool.ToolManager;
import sashwind.mc.plugin.mapdraw.tool.ToolType;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;
import sashwind.mc.plugin.mapdraw.util.ItemFrameRayTraceUtil;

import java.awt.Point;

public class ItemFrameDrawListener implements Listener {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;
    private final ToolManager toolManager;

    public ItemFrameDrawListener(Mapdraw plugin, CanvasManager canvasManager, ToolManager toolManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
        this.toolManager = toolManager;
    }

    private void cleanFrameDisplayName(ItemFrame frame) {
        if (frame == null || !frame.isValid()) return;
        frame.setCustomNameVisible(false);
        frame.customName(null);
        ItemStack item = frame.getItem();
        if (CanvasNBTUtil.isCanvasMap(item)) {
            CanvasData canvas = canvasManager.getCanvasFromItem(item);
            if (canvas != null && item.getItemMeta() != null && (!item.getItemMeta().hasDisplayName() || !item.getItemMeta().hasLore())) {
                CanvasNBTUtil.applyCanvasMeta(item, canvas);
                frame.setItem(item, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtFrame(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }

        ItemStack itemInFrame = frame.getItem();
        CanvasData canvas = canvasManager.getCanvasFromItem(itemInFrame);
        if (canvas == null) {
            return;
        }

        cleanFrameDisplayName(frame);

        Player player = event.getPlayer();
        ToolType tool = toolManager.getToolType(player.getInventory().getItemInMainHand());
        if (tool == ToolType.NONE) {
            tool = toolManager.getToolType(player.getInventory().getItemInOffHand());
        }

        // 手持绘图工具：作画
        if (tool != ToolType.NONE) {
            event.setCancelled(true);
            handleDrawOnFrame(player, frame, canvas, event.getClickedPosition());
            return;
        }

        // 未持工具：潜行（蹲下）右键打开控制菜单
        if (player.isSneaking()) {
            event.setCancelled(true);
            if (!plugin.isChestGuiEnabled(player)) {
                return; // 客户端 Mod 禁用了箱子菜单
            }
            if (!canvas.isProtected()) {
                plugin.getGuiManager().openMainMenu(player, canvas);
            } else {
                player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("canvas_protected"));
            }
        }
        // 未持工具且未潜行：允许正常触发展示框旋转
    }

    private void handleDrawOnFrame(Player player, ItemFrame frame, CanvasData canvas, Vector clickOffset) {
        ItemStack held = player.getInventory().getItemInMainHand();

        ToolType tool = toolManager.getToolType(held);
        if (tool == ToolType.NONE) {
            tool = toolManager.getToolType(player.getInventory().getItemInOffHand());
        }

        // 必须切实手持绘图工具，未手持工具时禁止作画
        if (tool == ToolType.NONE) {
            return;
        }

        PlayerDrawSession session = toolManager.getSession(player);
        session.setCurrentTool(tool);
        ToolType activeTool = tool;

        if (!player.hasPermission("mapdraw.user.draw")) {
            player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("no_permission"));
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

        // 统一提取纯净视口像素坐标 (Surface Space)
        Point raySurfacePixel = ItemFrameRayTraceUtil.getSurfacePixelFromRayTrace(player, frame);
        Point clickSurfacePixel = clickOffset != null ? ItemFrameRayTraceUtil.getSurfacePixelFromOffset(frame, clickOffset) : null;
        Point surfacePixel = raySurfacePixel != null ? raySurfacePixel : clickSurfacePixel;

        if (surfacePixel == null) {
            return;
        }

        // 控制台与动作栏详细调试输出
        if (plugin.getConfig().getBoolean("canvas.debug", false)) {
            Point debugCanvasPt = CanvasCoordinateAdapter.surfaceToCanvas(surfacePixel.x, surfacePixel.y, frame);
            String rayStr = raySurfacePixel != null ? String.format("(%d, %d)", raySurfacePixel.x, raySurfacePixel.y) : "未命中";
            String clickStr = clickSurfacePixel != null ? String.format("(%d, %d)", clickSurfacePixel.x, clickSurfacePixel.y) : "无";
            String logMsg = String.format("[MapDraw Debug] 玩家: %s | 朝向: %s | 旋转: %s | 视线视口像素: %s | 点击视口像素: %s | 最终底层画布: (%d, %d)",
                player.getName(), frame.getFacing(), frame.getRotation(), rayStr, clickStr, debugCanvasPt.x, debugCanvasPt.y);
            plugin.getLogger().info(logMsg);

            String actionMsg = String.format("§6[Debug] §f朝向:§e%s §f旋转:§b%s §f视口:§a%s §f底层:§c(%d,%d)",
                frame.getFacing(), frame.getRotation(), rayStr, debugCanvasPt.x, debugCanvasPt.y);
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(actionMsg));
        }

        byte eraserColor = (byte) plugin.getConfig().getInt("canvas.default_bg_color", 34);
        byte colorToDraw = (activeTool == ToolType.ERASER) ? eraserColor : session.getCurrentMapColor();

        long strokeTimeoutMs = plugin.getConfig().getLong("canvas.stroke_timeout_ms", 400L);
        double maxDistance = plugin.getConfig().getDouble("canvas.max_stroke_distance", 800.0);

        Vector curHitPos = ItemFrameRayTraceUtil.getWorldHitPosFromRayTrace(player, frame);
        if (curHitPos == null) {
            curHitPos = (clickOffset != null) ? frame.getLocation().toVector().add(clickOffset) : frame.getLocation().toVector();
        }

        sashwind.mc.plugin.mapdraw.canvas.CrossFrameDrawer.drawWithCrossFrameSupport(
            plugin, player, frame, canvas, curHitPos, surfacePixel, activeTool, colorToDraw, strokeTimeoutMs, maxDistance
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }

        ItemStack itemInFrame = frame.getItem();
        CanvasData canvas = canvasManager.getCanvasFromItem(itemInFrame);

        // 如果展示框为空，且玩家正准备放置地图
        if (itemInFrame.getType().isAir() || canvas == null) {
            ItemStack main = event.getPlayer().getInventory().getItemInMainHand();
            ItemStack off = event.getPlayer().getInventory().getItemInOffHand();
            if (CanvasNBTUtil.isCanvasMap(main) || CanvasNBTUtil.isCanvasMap(off)) {
                // 延迟 1 tick 消除放入展示框后的悬浮文字，防止遮挡视线
                Bukkit.getScheduler().runTask(plugin, () -> cleanFrameDisplayName(frame));
            }
            return;
        }

        // 展示框已有地图，消除悬浮文字
        cleanFrameDisplayName(frame);

        Player player = event.getPlayer();
        ToolType tool = toolManager.getToolType(player.getInventory().getItemInMainHand());
        if (tool == ToolType.NONE) {
            tool = toolManager.getToolType(player.getInventory().getItemInOffHand());
        }

        // 手持绘图工具：阻止展示框旋转，触发绘画
        if (tool != ToolType.NONE) {
            event.setCancelled(true);
            handleDrawOnFrame(player, frame, canvas, null);
            return;
        }

        // 未持工具：潜行（蹲下）右键打开控制菜单
        if (player.isSneaking()) {
            event.setCancelled(true);
            if (!canvas.isProtected()) {
                plugin.getGuiManager().openMainMenu(player, canvas);
            } else {
                player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("canvas_protected"));
            }
        }
        // 未持工具且未潜行：允许正常触发展示框旋转
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (event.getEntity() instanceof ItemFrame) {
            ItemStack dropped = event.getItemDrop().getItemStack();
            if (CanvasNBTUtil.isCanvasMap(dropped)) {
                CanvasData canvas = canvasManager.getCanvasFromItem(dropped);
                if (canvas != null) {
                    CanvasNBTUtil.applyCanvasMeta(dropped, canvas);
                    event.getItemDrop().setItemStack(dropped);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageFrame(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        ItemStack itemInFrame = frame.getItem();
        CanvasData canvas = canvasManager.getCanvasFromItem(itemInFrame);
        if (canvas != null && canvas.isProtected()) {
            if (event.getDamager() instanceof Player player && !player.hasPermission("mapdraw.admin.deprotect")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage("prefix") + "§c该展示框中的画布已被保护锁定，无法破坏！");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAir(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ToolType tool = toolManager.getToolType(player.getInventory().getItemInMainHand());
        if (tool == ToolType.NONE) {
            tool = toolManager.getToolType(player.getInventory().getItemInOffHand());
        }

        // 必须切实手持绘图工具
        if (tool == ToolType.NONE) {
            return;
        }

        PlayerDrawSession session = toolManager.getSession(player);
        session.setCurrentTool(tool);
        ToolType activeTool = tool;

        // 1. 优先光线追踪 5.5 格内的展示框实体
        RayTraceResult result = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            5.5,
            0.25,
            e -> e instanceof ItemFrame
        );

        ItemFrame targetFrame = null;
        Vector hitVector = null;

        if (result != null && result.getHitEntity() instanceof ItemFrame frame) {
            targetFrame = frame;
            hitVector = result.getHitPosition();
        } else {
            // 2. 若准星正好划过展示框木框接缝处(实体射线漏检)，追踪背后的墙面方块表面！
            RayTraceResult blockHit = player.rayTraceBlocks(5.5);
            if (blockHit != null && blockHit.getHitBlock() != null && blockHit.getHitBlockFace() != null) {
                targetFrame = findAttachedFrame(blockHit.getHitBlock().getLocation(), blockHit.getHitBlockFace());
                if (targetFrame != null) {
                    hitVector = blockHit.getHitPosition();
                }
            }
        }

        if (targetFrame != null) {
            ItemStack itemInFrame = targetFrame.getItem();
            CanvasData canvas = canvasManager.getCanvasFromItem(itemInFrame);
            if (canvas == null) return;

            event.setCancelled(true);
            cleanFrameDisplayName(targetFrame);

            if (!player.hasPermission("mapdraw.user.draw")) {
                player.sendMessage(plugin.getMessage("prefix") + plugin.getMessage("no_permission"));
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

            Point pixel = ItemFrameRayTraceUtil.getMapPixelFromRayTrace(player, targetFrame);
            if (pixel == null && hitVector != null) {
                Vector offset = hitVector.clone().subtract(targetFrame.getLocation().toVector());
                pixel = ItemFrameRayTraceUtil.getMapPixelFromOffset(targetFrame, offset);
            }
            if (pixel == null) return;

            byte eraserColor = (byte) plugin.getConfig().getInt("canvas.default_bg_color", 34);
            byte colorToDraw = (activeTool == ToolType.ERASER) ? eraserColor : session.getCurrentMapColor();

            long strokeTimeoutMs = plugin.getConfig().getLong("canvas.stroke_timeout_ms", 200L);
            double maxDistance = plugin.getConfig().getDouble("canvas.max_stroke_distance", 110.0);

            Vector curHit = hitVector != null ? hitVector : targetFrame.getLocation().toVector();
            sashwind.mc.plugin.mapdraw.canvas.CrossFrameDrawer.drawWithCrossFrameSupport(
                plugin, player, targetFrame, canvas, curHit, pixel, activeTool, colorToDraw, strokeTimeoutMs, maxDistance
            );
        }
    }

    private ItemFrame findAttachedFrame(org.bukkit.Location blockLoc, BlockFace face) {
        if (blockLoc.getWorld() == null) return null;
        org.bukkit.Location frontLoc = blockLoc.clone().add(face.getDirection().multiply(0.5));
        for (ItemFrame frame : blockLoc.getWorld().getNearbyEntitiesByType(ItemFrame.class, frontLoc, 0.9)) {
            if (frame.getFacing() == face) {
                return frame;
            }
        }
        return null;
    }
}
