package sashwind.mc.plugin.mapdraw.canvas;

import sashwind.mc.plugin.mapdraw.tool.ToolType;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Queue;

public class DrawingEngine {

    /**
     * 在指定像素点执行绘图操作
     */
    public static boolean applyDraw(CanvasData canvas, int px, int py, ToolType tool, byte color) {
        if (canvas.isProtected() || canvas.isAnimated()) {
            return false;
        }

        if (!inBounds(canvas, px, py)) {
            return false;
        }

        // 保存撤销历史（单个点 = 一步撤销）
        canvas.pushUndoState();

        return applyDrawNoUndo(canvas, px, py, tool, color);
    }

    /**
     * 在指定像素点执行绘图操作，<b>但不记撤销历史</b>。
     *
     * <p>批量落笔（一个包画很多点）用：整批只 push 一次撤销快照、只通知一次画布更新，
     * 否则一个 4096 点的包会产生 4096 个撤销快照 + 4096 次地图包发送（服务端直接被打爆）。</p>
     *
     * <p>调用方需自行校验保护状态与权限。</p>
     */
    public static boolean applyDrawNoUndo(CanvasData canvas, int px, int py, ToolType tool, byte color) {
        int size = canvas.getSize();
        if (size <= 0) size = 128;
        int scale = Math.max(1, 128 / size);

        int gridX = px / scale;
        int gridY = py / scale;
        int maxGrid = 128 / scale;

        if (gridX < 0 || gridX >= maxGrid || gridY < 0 || gridY >= maxGrid) {
            return false;
        }

        if (tool == ToolType.PEN) {
            fillGridBlock(canvas, gridX, gridY, scale, color);
            return true;
        } else if (tool == ToolType.ERASER) {
            fillGridBlock(canvas, gridX, gridY, scale, (byte) 0);
            return true;
        } else if (tool == ToolType.PAINTBUCKET) {
            floodFill(canvas, gridX, gridY, scale, maxGrid, color);
            return true;
        }

        return false;
    }

    /** 坐标是否落在画布的可画区内（0..127，按逻辑格对齐后的范围）。 */
    public static boolean inBounds(CanvasData canvas, int px, int py) {
        int size = canvas.getSize();
        if (size <= 0) size = 128;
        int scale = Math.max(1, 128 / size);
        int maxGrid = 128 / scale;

        int gridX = px / scale;
        int gridY = py / scale;
        return gridX >= 0 && gridX < maxGrid && gridY >= 0 && gridY < maxGrid;
    }

    /**
     * 在指定像素路径线段执行连续平滑绘图操作 (保存单次撤销快照)
     */
    public static boolean applyDrawLine(CanvasData canvas, java.util.List<Point> points, ToolType tool, byte color) {
        if (canvas.isProtected() || canvas.isAnimated() || points == null || points.isEmpty()) {
            return false;
        }

        int size = canvas.getSize();
        if (size <= 0) size = 128;
        int scale = Math.max(1, 128 / size);
        int maxGrid = 128 / scale;

        // 一笔连续线条仅压入 1 次撤销快照
        canvas.pushUndoState();

        byte targetFillColor = (tool == ToolType.ERASER) ? (byte) 0 : color;

        boolean drawn = false;
        for (Point p : points) {
            int gridX = p.x / scale;
            int gridY = p.y / scale;
            if (gridX >= 0 && gridX < maxGrid && gridY >= 0 && gridY < maxGrid) {
                fillGridBlock(canvas, gridX, gridY, scale, targetFillColor);
                drawn = true;
            }
        }
        return drawn;
    }

    private static void fillGridBlock(CanvasData canvas, int gridX, int gridY, int scale, byte color) {
        int startX = gridX * scale;
        int startY = gridY * scale;
        for (int y = startY; y < startY + scale && y < 128; y++) {
            for (int x = startX; x < startX + scale && x < 128; x++) {
                canvas.setPixel(x, y, color);
            }
        }
    }

    private static byte getGridBlockColor(CanvasData canvas, int gridX, int gridY, int scale) {
        int startX = gridX * scale;
        int startY = gridY * scale;
        return canvas.getPixel(startX, startY);
    }

    private static void floodFill(CanvasData canvas, int startGridX, int startGridY, int scale, int maxGrid, byte newColor) {
        byte targetColor = getGridBlockColor(canvas, startGridX, startGridY, scale);
        if (targetColor == newColor) {
            return;
        }

        boolean[][] visited = new boolean[maxGrid][maxGrid];
        Queue<Point> queue = new ArrayDeque<>();
        queue.add(new Point(startGridX, startGridY));
        visited[startGridX][startGridY] = true;

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            fillGridBlock(canvas, p.x, p.y, scale, newColor);

            for (int i = 0; i < 4; i++) {
                int nx = p.x + dx[i];
                int ny = p.y + dy[i];

                if (nx >= 0 && nx < maxGrid && ny >= 0 && ny < maxGrid && !visited[nx][ny]) {
                    if (getGridBlockColor(canvas, nx, ny, scale) == targetColor) {
                        visited[nx][ny] = true;
                        queue.add(new Point(nx, ny));
                    }
                }
            }
        }
    }
}
