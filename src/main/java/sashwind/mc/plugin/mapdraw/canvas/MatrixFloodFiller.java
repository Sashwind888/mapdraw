package sashwind.mc.plugin.mapdraw.canvas;

import sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasFrameNode;
import sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasMatrix;

import java.awt.Point;
import java.util.*;

public class MatrixFloodFiller {

    /**
     * 在相连画板矩阵组成的全局视口大画板上执行稳健的跨画布泛洪填充 (油漆桶多画布联动)
     * 采用标准的图形学 4 连通泛洪 + 入队即着色策略，彻底杜绝孤立白线残留与死角
     */
    public static boolean applyMatrixFloodFill(CanvasMatrix matrix, CanvasFrameNode startNode, int startSurfaceX, int startSurfaceY, byte newColor) {
        if (matrix == null || startNode == null) return false;

        CanvasData startCanvas = startNode.canvas;
        if (startCanvas.isProtected() || startCanvas.isAnimated()) return false;

        int globalW = matrix.cols * 128;
        int globalH = matrix.rows * 128;

        int startGlobalX = startNode.gridCol * 128 + startSurfaceX;
        int startGlobalY = startNode.gridRow * 128 + startSurfaceY;

        Point startCanvasPt = CanvasCoordinateAdapter.surfaceToCanvas(startSurfaceX, startSurfaceY, startNode.frame);
        byte targetColor = startCanvas.getPixel(startCanvasPt.x, startCanvasPt.y);
        if (targetColor == newColor) {
            return false;
        }

        boolean[][] visited = new boolean[globalH][globalW];
        Queue<Point> queue = new ArrayDeque<>();

        // 入队即标记已访问
        queue.add(new Point(startGlobalX, startGlobalY));
        visited[startGlobalY][startGlobalX] = true;

        Set<CanvasData> modifiedCanvases = new HashSet<>();
        for (CanvasFrameNode node : matrix.allNodes) {
            if (!node.canvas.isProtected() && !node.canvas.isAnimated()) {
                node.canvas.pushUndoState();
            }
        }

        // 标准正交 4-连通方向，防止对角跳跃破坏连续区域
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            int gX = p.x;
            int gY = p.y;

            int col = gX / 128;
            int row = gY / 128;
            int localX = gX % 128;
            int localY = gY % 128;

            CanvasFrameNode node = matrix.getNode(col, row);
            if (node == null || node.canvas.isProtected() || node.canvas.isAnimated()) {
                continue;
            }

            // 着色
            Point cPt = CanvasCoordinateAdapter.surfaceToCanvas(localX, localY, node.frame);
            node.canvas.setPixel(cPt.x, cPt.y, newColor);
            modifiedCanvases.add(node.canvas);

            // 扩展四邻
            for (int i = 0; i < 4; i++) {
                int nx = gX + dx[i];
                int ny = gY + dy[i];

                if (nx >= 0 && nx < globalW && ny >= 0 && ny < globalH && !visited[ny][nx]) {
                    int nCol = nx / 128;
                    int nRow = ny / 128;
                    CanvasFrameNode nNode = matrix.getNode(nCol, nRow);
                    if (nNode != null && !nNode.canvas.isProtected() && !nNode.canvas.isAnimated()) {
                        int nLocalX = nx % 128;
                        int nLocalY = ny % 128;
                        Point checkPt = CanvasCoordinateAdapter.surfaceToCanvas(nLocalX, nLocalY, nNode.frame);
                        byte cColor = nNode.canvas.getPixel(checkPt.x, checkPt.y);
                        if (cColor == targetColor) {
                            visited[ny][nx] = true; // 入队立刻锁定
                            queue.add(new Point(nx, ny));
                        }
                    }
                }
            }
        }

        return !modifiedCanvases.isEmpty();
    }
}
