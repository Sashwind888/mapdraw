package sashwind.mc.plugin.mapdraw.canvas;

import sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasFrameNode;
import sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasMatrix;

import java.awt.Point;
import java.util.*;

public class MatrixFloodFiller {

    /**
     * 在相连画板矩阵组成的全局视口大画板上执行跨画布泛洪填充 (油漆桶多画布联动)
     * 支持 8 连通（含对角线）全向无死角蔓延，彻底覆盖所有相接线条与边缘
     */
    public static boolean applyMatrixFloodFill(CanvasMatrix matrix, CanvasFrameNode startNode, int startSurfaceX, int startSurfaceY, byte newColor) {
        if (matrix == null || startNode == null) return false;

        CanvasData startCanvas = startNode.canvas;
        if (startCanvas.isProtected() || startCanvas.isAnimated()) return false;

        // 全局大画板总像素尺寸 (每个展示框 128x128 像素)
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
        queue.add(new Point(startGlobalX, startGlobalY));
        visited[startGlobalY][startGlobalX] = true;

        Set<CanvasData> modifiedCanvases = new HashSet<>();
        for (CanvasFrameNode node : matrix.allNodes) {
            if (!node.canvas.isProtected() && !node.canvas.isAnimated()) {
                node.canvas.pushUndoState();
            }
        }

        // 8 连通方向向量 (上下左右 + 4 个对角线，彻底覆盖斜向线条与狭缝)
        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};

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

            // 向八个方向蔓延
            for (int i = 0; i < 8; i++) {
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
                            visited[ny][nx] = true;
                            queue.add(new Point(nx, ny));
                        }
                    }
                }
            }
        }

        return !modifiedCanvases.isEmpty();
    }
}
