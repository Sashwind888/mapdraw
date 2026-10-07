package sashwind.mc.plugin.mapdraw.canvas;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.util.Vector;
import sashwind.mc.plugin.mapdraw.Mapdraw;

import java.awt.Point;
import java.util.*;

public class ConnectedCanvasTopology {

    /** 联动搜索的总帧数上限（兜底，防止畸形布局把 BFS 拖死）。 */
    private static final int MAX_MATRIX_NODES = 512;

    public static class CanvasFrameNode {
        public final ItemFrame frame;
        public final CanvasData canvas;
        public final int gridCol; // 0-based, 从最左侧算起
        public final int gridRow; // 0-based, 从最顶部算起

        public CanvasFrameNode(ItemFrame frame, CanvasData canvas, int gridCol, int gridRow) {
            this.frame = frame;
            this.canvas = canvas;
            this.gridCol = gridCol;
            this.gridRow = gridRow;
        }
    }

    public static class CanvasMatrix {
        public final int cols;
        public final int rows;
        public final BlockFace facing;
        public final CanvasFrameNode[][] matrix;
        public final List<CanvasFrameNode> allNodes;

        public CanvasMatrix(int cols, int rows, BlockFace facing, CanvasFrameNode[][] matrix, List<CanvasFrameNode> allNodes) {
            this.cols = cols;
            this.rows = rows;
            this.facing = facing;
            this.matrix = matrix;
            this.allNodes = allNodes;
        }

        public CanvasFrameNode getNode(int col, int row) {
            if (col < 0 || col >= cols || row < 0 || row >= rows) return null;
            return matrix[row][col];
        }
    }

    /**
     * 以指定的展示框为基准，在相同物理平面上向周围（上下左右）搜索所有紧密相连的未锁定画布展示框
     * 规则：
     * 1. 只要其中包含任何一个已锁定或 GIF 动图画布，立即拒绝联动并返回 null；
     * 2. 搜索到的所有画布必须朝向完全一致，形成连续的画板拓扑。
     *
     * <p><b>注意（重要）：</b>搜索结果必须与「从哪个展示框开始搜」无关 —— 归一化原点取的是
     * 连通区域自己的最左上角（minU/minV），只要搜出来的集合一样，网格坐标就一样。
     * 之前这里用的是「相对起始展示框 ±maxRadius 就停止扩散」，同一个连通区域从不同展示框出发
     * 会搜出<b>不同的子集</b>，包围盒跟着变，客户端用 0x84 算出来的全局坐标在 0x12 里就会错位
     * （报「坐标超出多画板矩阵范围」或落到别的格子上）。
     * 现在改成：把整块连通区域搜完，只用「总帧数上限」兜底（上限由 maxRadius 换算而来，
     * 保留「别搜太远」的意图，但不让结果依赖起始展示框）。</p>
     */
    public static CanvasMatrix findConnectedMatrix(ItemFrame startFrame, CanvasManager canvasManager, int maxRadius) {
        if (startFrame == null || !startFrame.isValid()) return null;

        CanvasData startCanvas = canvasManager.getCanvasFromItem(startFrame.getItem());
        if (startCanvas == null || startCanvas.isProtected() || startCanvas.isAnimated()) {
            return null; // 存在锁定或动图，不建立联动
        }

        BlockFace facing = startFrame.getFacing();
        Vector rightStep = getRightStepVector(facing);
        Vector downStep = getDownStepVector(facing);

        // 使用相对坐标 (u, v) -> Node 进行空间广度优先搜索 (BFS)
        Map<Point, CanvasFrameNode> visited = new HashMap<>();
        Queue<Point> queue = new ArrayDeque<>();

        Point startCoord = new Point(0, 0);
        visited.put(startCoord, new CanvasFrameNode(startFrame, startCanvas, 0, 0));
        queue.add(startCoord);

        int minU = 0, maxU = 0;
        int minV = 0, maxV = 0;

        int[] du = {1, -1, 0, 0};
        int[] dv = {0, 0, 1, -1};

        Location baseLoc = startFrame.getLocation();

        // 总帧数上限：maxRadius=5 时是 100 帧，足够覆盖 10x10 的大板，同时防止畸形布局把搜索拖死
        int maxNodes = Math.max(16, Math.min(MAX_MATRIX_NODES, Math.max(1, maxRadius) * Math.max(1, maxRadius) * 4));

        while (!queue.isEmpty()) {
            Point cur = queue.poll();
            CanvasFrameNode curNode = visited.get(cur);

            minU = Math.min(minU, cur.x);
            maxU = Math.max(maxU, cur.x);
            minV = Math.min(minV, cur.y);
            maxV = Math.max(maxV, cur.y);

            // 检查上下左右四个相邻展示框
            for (int i = 0; i < 4; i++) {
                int nextU = cur.x + du[i];
                int nextV = cur.y + dv[i];
                Point nextPt = new Point(nextU, nextV);

                if (visited.containsKey(nextPt)) continue;

                // 用总帧数兜底，而不是「相对起始展示框 ±maxRadius」（那样结果会依赖起始框）
                if (visited.size() >= maxNodes) break;

                // 计算相邻格子的空间坐标
                Location expectedLoc = baseLoc.clone()
                    .add(rightStep.clone().multiply(nextU))
                    .add(downStep.clone().multiply(nextV));

                ItemFrame neighborFrame = findFrameAt(expectedLoc, facing);
                if (neighborFrame != null) {
                    CanvasData neighborCanvas = canvasManager.getCanvasFromItem(neighborFrame.getItem());
                    // 核心规则：如果有动图或被锁定，则不能联动
                    if (neighborCanvas == null || neighborCanvas.isProtected() || neighborCanvas.isAnimated()) {
                        return null; // 只要连着的含有动图或锁定，整组不可联动
                    }
                    CanvasFrameNode nextNode = new CanvasFrameNode(neighborFrame, neighborCanvas, 0, 0);
                    visited.put(nextPt, nextNode);
                    queue.add(nextPt);
                }
            }
        }

        int cols = (maxU - minU) + 1;
        int rows = (maxV - minV) + 1;

        CanvasFrameNode[][] matrix = new CanvasFrameNode[rows][cols];
        List<CanvasFrameNode> allNodes = new ArrayList<>();

        for (Map.Entry<Point, CanvasFrameNode> entry : visited.entrySet()) {
            Point pt = entry.getKey();
            int c = pt.x - minU;
            int r = pt.y - minV;
            CanvasFrameNode n = entry.getValue();
            CanvasFrameNode normalized = new CanvasFrameNode(n.frame, n.canvas, c, r);
            matrix[r][c] = normalized;
            allNodes.add(normalized);
        }

        return new CanvasMatrix(cols, rows, facing, matrix, allNodes);
    }

    private static ItemFrame findFrameAt(Location targetLoc, BlockFace facing) {
        if (targetLoc.getWorld() == null) return null;
        for (ItemFrame frame : targetLoc.getWorld().getNearbyEntitiesByType(ItemFrame.class, targetLoc, 0.7)) {
            if (frame.getFacing() == facing && frame.getLocation().distanceSquared(targetLoc) <= 0.4) {
                return frame;
            }
        }
        return null;
    }

    private static Vector getRightStepVector(BlockFace facing) {
        return switch (facing) {
            case SOUTH -> new Vector(1, 0, 0);
            case NORTH -> new Vector(-1, 0, 0);
            case WEST  -> new Vector(0, 0, 1);
            case EAST  -> new Vector(0, 0, -1);
            case UP    -> new Vector(1, 0, 0);
            case DOWN  -> new Vector(1, 0, 0);
            default    -> new Vector(1, 0, 0);
        };
    }

    private static Vector getDownStepVector(BlockFace facing) {
        return switch (facing) {
            case SOUTH, NORTH, WEST, EAST -> new Vector(0, -1, 0);
            case UP   -> new Vector(0, 0, 1);
            case DOWN -> new Vector(0, 0, -1);
            default   -> new Vector(0, -1, 0);
        };
    }
}
