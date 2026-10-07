package sashwind.mc.plugin.mapdraw.api;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.tool.ToolType;

import java.awt.Color;
import java.util.Collection;

public interface MapDrawAPI {

    // ==================== 画布数据获取 ====================

    /**
     * 根据画布唯一 ID 获取画布数据
     */
    CanvasData getCanvas(String id);

    /**
     * 根据 Minecraft Map ID 获取画布数据
     */
    CanvasData getCanvasByMapId(int mapId);

    /**
     * 从物品中获取关联的画布数据
     */
    CanvasData getCanvasFromItem(ItemStack item);

    /**
     * 从展示框实体中获取关联的画布数据
     */
    CanvasData getCanvasFromFrame(ItemFrame frame);

    /**
     * 获取所有已加载的画布
     */
    Collection<CanvasData> getAllCanvases();

    // ==================== 画布创建 ====================

    /**
     * 玩家创建新画布（校验 mapdraw.user.create 权限与经济费用）
     */
    DrawResult createCanvas(Player player, String name, int size);

    /**
     * 玩家复制已有画布作为新副本（校验经济与防拷贝规则）
     */
    DrawResult cloneCanvas(Player player, CanvasData parent);

    // ==================== 像素点绘制 API ====================

    /**
     * 玩家在画布指定坐标执行绘制（画点/橡皮擦/油漆桶）
     * 自动校验玩家的 mapdraw.user.draw 权限及画布锁定保护状态
     *
     * @param player 执行操作的发包玩家
     * @param canvas 目标画布
     * @param px     X 坐标 (0-127)
     * @param py     Y 坐标 (0-127)
     * @param tool   工具类型 (PEN, ERASER, PAINTBUCKET)
     * @param color  地图颜色字节
     */
    DrawResult drawPixel(Player player, CanvasData canvas, int px, int py, ToolType tool, byte color);

    /**
     * 玩家在画布上一次落笔很多个点（批量绘制）。
     *
     * <p>与 {@link #drawPixel} 相比：鉴权/保护状态只校验一次，整批只记<b>一个</b>撤销快照，
     * 全部落笔后只通知<b>一次</b>画布更新（一次地图包 + 一次存盘）。
     * 客户端一个 {@code 0x02 DRAW_BATCH} 或 {@code 0x12 DRAW_GRID_PIXEL}（带批量尾巴）就是一个批次。</p>
     *
     * @param points 画布局部坐标（0-127），按顺序落笔；空或全部越界会返回失败
     */
    DrawResult drawPixels(Player player, CanvasData canvas, java.util.List<java.awt.Point> points, ToolType tool, byte color);

    /**
     * 玩家手持工具在展示框上执行点击绘制
     * 自动完成光线追踪/点击投影计算、鉴权及绘制同步
     */
    DrawResult drawOnFrame(Player player, ItemFrame frame);

    // ==================== 撤销与重做 ====================

    /**
     * 玩家撤销上一步操作（校验 mapdraw.user.draw.undo 权限及保护状态）
     */
    DrawResult undo(Player player, CanvasData canvas);

    /**
     * 玩家重做下一步操作（校验 mapdraw.user.draw.redo 权限及保护状态）
     */
    DrawResult redo(Player player, CanvasData canvas);

    // ==================== 保护与解除保护 ====================

    /**
     * 玩家锁定保护画布（校验 mapdraw.user.protect 权限）
     * @param force 若为 true，跳过 2 秒双击确认防误触检测
     */
    DrawResult protectCanvas(Player player, CanvasData canvas, boolean force);

    /**
     * 解除画布保护（仅允许原作者或拥有 mapdraw.admin.deprotect 的管理员解除；动图不可解除）
     */
    DrawResult deprotectCanvas(Player player, CanvasData canvas);

    // ==================== 元数据设置 ====================

    /**
     * 修改画布标题（校验 mapdraw.user.data 权限及保护状态）
     */
    DrawResult setTitle(Player player, CanvasData canvas, String title);

    /**
     * 修改画布描述（校验 mapdraw.user.data 权限及保护状态）
     */
    DrawResult setDescription(Player player, CanvasData canvas, String description);

    /**
     * 修改画布可用大小（校验 mapdraw.user.data 权限、保护状态及填色后不可缩小规则）
     */
    DrawResult setSize(Player player, CanvasData canvas, int size);

    /**
     * 切换防拷贝状态（校验 mapdraw.user.data 权限及保护状态）
     */
    DrawResult setNoCopy(Player player, CanvasData canvas, boolean noCopy);

    // ==================== 工具与颜色会话 ====================

    /**
     * 为玩家设定画笔颜色（校验 mapdraw.user.draw 权限）
     */
    DrawResult setColor(Player player, Color color, byte mapColorByte);

    /**
     * 为玩家分发或清空绘图工具（校验 mapdraw.user.draw 权限）
     */
    DrawResult setTool(Player player, ToolType tool);

    /**
     * 打开画布管理 GUI 菜单（校验 mapdraw.user 权限）
     */
    DrawResult openMenu(Player player, CanvasData canvas);

    /**
     * 打开 16 色调色板 GUI 菜单（校验 mapdraw.user.draw 权限）
     */
    DrawResult openPalette(Player player);

    // ==================== 客户端 Mod / 玩家设置 ====================

    /**
     * 设置玩家是否启用服务端的箱子菜单 GUI (每次玩家重新登录时会自动重置为 true)
     */
    void setChestGuiEnabled(Player player, boolean enabled);

    /**
     * 检查玩家是否启用了服务端的箱子菜单 GUI
     */
    boolean isChestGuiEnabled(Player player);
}
