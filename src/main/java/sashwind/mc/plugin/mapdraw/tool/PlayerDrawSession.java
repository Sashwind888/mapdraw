package sashwind.mc.plugin.mapdraw.tool;

import sashwind.mc.plugin.mapdraw.util.ColorUtil;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class PlayerDrawSession {

    private ToolType currentTool = ToolType.NONE;
    private java.awt.Color currentColor = java.awt.Color.BLACK;
    private byte currentMapColor = (byte) 118; // 默认黑色基准色

    // 连续作画多点历史轨迹队列 (用于 Catmull-Rom 样条方向平滑曲线拟合)
    private String lastCanvasId;
    private final Deque<Point> strokeHistory = new ArrayDeque<>();
    private long lastDrawTime;

    // 跨展示框三维空间连续作画轨迹
    private org.bukkit.util.Vector lastWorldHitPos;
    private org.bukkit.block.BlockFace lastFrameFacing;

    public ToolType getCurrentTool() {
        return currentTool;
    }

    public void setCurrentTool(ToolType currentTool) {
        this.currentTool = currentTool != null ? currentTool : ToolType.NONE;
    }

    public java.awt.Color getCurrentColor() {
        return currentColor;
    }

    public byte getCurrentMapColor() {
        return currentMapColor;
    }

    public void setCurrentColor(java.awt.Color color) {
        this.currentColor = color != null ? color : java.awt.Color.BLACK;
        this.currentMapColor = ColorUtil.toMapColor(this.currentColor);
    }

    public void setColor(java.awt.Color color, byte mapColorByte) {
        this.currentColor = color != null ? color : java.awt.Color.BLACK;
        this.currentMapColor = mapColorByte;
    }

    public synchronized void recordPoint(String canvasId, Point point, long timeoutMs) {
        long now = System.currentTimeMillis();
        // 关键修复：一旦超时抬笔，或者跨越到了新的画布地图，必须彻底清空局部像素历史，杜绝把上一张地图的坐标混入新画布拉出对勾折线！
        if (lastCanvasId == null || !lastCanvasId.equals(canvasId) || (now - lastDrawTime > timeoutMs)) {
            strokeHistory.clear();
        }
        this.lastCanvasId = canvasId;
        this.lastDrawTime = now;

        Point last = strokeHistory.peekLast();
        if (last == null || last.x != point.x || last.y != point.y) {
            strokeHistory.addLast(new Point(point.x, point.y));
            while (strokeHistory.size() > 4) {
                strokeHistory.removeFirst();
            }
        }
    }

    public synchronized List<Point> getStrokePoints(String canvasId, long timeoutMs) {
        long now = System.currentTimeMillis();
        if (canvasId != null && canvasId.equals(this.lastCanvasId) && (now - lastDrawTime <= timeoutMs)) {
            return new ArrayList<>(strokeHistory);
        }
        return new ArrayList<>();
    }

    public synchronized Point getLastPoint(String canvasId, long timeoutMs) {
        long now = System.currentTimeMillis();
        if (canvasId != null && canvasId.equals(this.lastCanvasId) && (now - lastDrawTime <= timeoutMs)) {
            return strokeHistory.peekLast();
        }
        return null;
    }

    public synchronized void recordWorldHit(org.bukkit.util.Vector worldPos, org.bukkit.block.BlockFace facing, long timeoutMs) {
        this.lastWorldHitPos = worldPos;
        this.lastFrameFacing = facing;
        this.lastDrawTime = System.currentTimeMillis();
    }

    public synchronized org.bukkit.util.Vector getLastWorldHit(org.bukkit.block.BlockFace facing, long timeoutMs) {
        long now = System.currentTimeMillis();
        if (this.lastWorldHitPos != null && this.lastFrameFacing == facing && (now - lastDrawTime <= timeoutMs)) {
            return this.lastWorldHitPos;
        }
        return null;
    }

    public synchronized void resetStroke() {
        this.strokeHistory.clear();
        this.lastCanvasId = null;
        this.lastWorldHitPos = null;
        this.lastFrameFacing = null;
    }
}
