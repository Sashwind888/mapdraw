package sashwind.mc.plugin.mapdraw.canvas;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.tool.PlayerDrawSession;
import sashwind.mc.plugin.mapdraw.tool.ToolType;
import sashwind.mc.plugin.mapdraw.util.ItemFrameRayTraceUtil;

import java.awt.Point;
import java.util.*;

public class CrossFrameDrawer {

    /**
     * 跨展示框三维平滑连续绘制（完全解耦架构）：
     * 上层所有笔迹追踪、平滑曲线、跨界采样均在纯净的视口物理平面 (Surface Space) 进行，
     * 仅在写入底层像素时通过 CanvasCoordinateAdapter 进行硬件旋转映射，
     * 彻底从数学和几何上消灭坐标旋转污染与对勾折线！
     */
    public static boolean drawWithCrossFrameSupport(
            Mapdraw plugin,
            Player player,
            ItemFrame currentFrame,
            CanvasData currentCanvas,
            Vector curWorldHitPos,
            Point curSurfacePt,
            ToolType tool,
            byte colorToDraw,
            long timeoutMs,
            double maxDistancePixels) {

        PlayerDrawSession session = plugin.getToolManager().getSession(player);
        CanvasManager canvasManager = plugin.getCanvasManager();

        Vector lastHit = session.getLastWorldHit(currentFrame.getFacing(), timeoutMs);
        List<Point> history = session.getStrokePoints(currentCanvas.getId(), timeoutMs);
        Point lastSurfacePt = history.isEmpty() ? null : history.get(history.size() - 1);

        boolean drawnAny = false;

        // 油漆桶工具：优先进行多画布相连矩阵全局泛洪填充
        if (tool == ToolType.PAINTBUCKET) {
            ConnectedCanvasTopology.CanvasMatrix matrix =
                ConnectedCanvasTopology.findConnectedMatrix(currentFrame, canvasManager, 5);

            if (matrix != null && matrix.allNodes.size() > 1) {
                ConnectedCanvasTopology.CanvasFrameNode startNode = null;
                for (ConnectedCanvasTopology.CanvasFrameNode node : matrix.allNodes) {
                    if (node.frame.equals(currentFrame)) {
                        startNode = node;
                        break;
                    }
                }
                if (startNode != null) {
                    drawnAny = MatrixFloodFiller.applyMatrixFloodFill(matrix, startNode, curSurfacePt.x, curSurfacePt.y, colorToDraw);
                    if (drawnAny) {
                        for (ConnectedCanvasTopology.CanvasFrameNode node : matrix.allNodes) {
                            canvasManager.notifyCanvasUpdated(node.canvas, node.frame.getLocation());
                        }
                    }
                }
            }

            if (!drawnAny) {
                Point canvasPt = CanvasCoordinateAdapter.surfaceToCanvas(curSurfacePt.x, curSurfacePt.y, currentFrame);
                drawnAny = DrawingEngine.applyDraw(currentCanvas, canvasPt.x, canvasPt.y, tool, colorToDraw);
            }
        }
        // 情况 1: 上次落笔也在同一张画布上，且在纯净视口空间中距离在阈值内 -> 使用方向感知样条插值
        else if (lastSurfacePt != null && (lastSurfacePt.x != curSurfacePt.x || lastSurfacePt.y != curSurfacePt.y) && lastSurfacePt.distance(curSurfacePt) <= maxDistancePixels) {
            List<Point> surfaceCurvePoints;
            if (history.size() >= 2) {
                // p1 严格为起点 lastSurfacePt，p2 严格为当前落笔终点 curSurfacePt
                Point p0 = history.get(history.size() - 2);
                Point p1 = lastSurfacePt;
                Point p2 = curSurfacePt;
                Point p3 = new Point(2 * curSurfacePt.x - lastSurfacePt.x, 2 * curSurfacePt.y - lastSurfacePt.y);
                surfaceCurvePoints = ItemFrameRayTraceUtil.smoothSplineCurve(p0, p1, p2, p3);
            } else {
                surfaceCurvePoints = ItemFrameRayTraceUtil.bresenhamLine(lastSurfacePt.x, lastSurfacePt.y, curSurfacePt.x, curSurfacePt.y);
            }

            // 视口坐标转换为底层画布像素坐标
            List<Point> canvasPoints = new ArrayList<>();
            for (Point sp : surfaceCurvePoints) {
                canvasPoints.add(CanvasCoordinateAdapter.surfaceToCanvas(sp.x, sp.y, currentFrame));
            }
            drawnAny = DrawingEngine.applyDrawLine(currentCanvas, canvasPoints, tool, colorToDraw);
        }
        // 情况 2: 跨越展示框边界！从相邻地图长按划入当前地图 (纯净三维空间线段密集采样，自动映射各画布)
        else if (lastHit != null && curWorldHitPos != null && lastHit.distance(curWorldHitPos) <= 5.0 && lastHit.distance(curWorldHitPos) > 0.0001) {
            drawnAny = drawCrossBorderSegment(plugin, currentFrame.getWorld(), lastHit, curWorldHitPos, currentFrame.getFacing(), tool, colorToDraw);
            if (!drawnAny) {
                Point canvasPt = CanvasCoordinateAdapter.surfaceToCanvas(curSurfacePt.x, curSurfacePt.y, currentFrame);
                drawnAny = DrawingEngine.applyDraw(currentCanvas, canvasPt.x, canvasPt.y, tool, colorToDraw);
            }
        }
        // 情况 3: 普通单点绘制
        else {
            Point canvasPt = CanvasCoordinateAdapter.surfaceToCanvas(curSurfacePt.x, curSurfacePt.y, currentFrame);
            drawnAny = DrawingEngine.applyDraw(currentCanvas, canvasPt.x, canvasPt.y, tool, colorToDraw);
        }

        if (drawnAny) {
            session.recordPoint(currentCanvas.getId(), curSurfacePt, timeoutMs);
            session.recordWorldHit(curWorldHitPos, currentFrame.getFacing(), timeoutMs);
            canvasManager.notifyCanvasUpdated(currentCanvas, currentFrame.getLocation());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.8f);
            return true;
        }

        return false;
    }

    /**
     * 跨越展示框接缝的高精度边缘缝合算法
     */
    private static boolean drawCrossBorderSegment(
            Mapdraw plugin,
            org.bukkit.World world,
            Vector pStart,
            Vector pEnd,
            BlockFace facing,
            ToolType tool,
            byte colorToDraw) {

        double dist = pStart.distance(pEnd);
        int samples = Math.max(10, (int) Math.ceil(dist / 0.0025));
        Vector dir = pEnd.clone().subtract(pStart).multiply(1.0 / samples);

        CanvasManager canvasManager = plugin.getCanvasManager();
        Map<CanvasData, List<Point>> mapPoints = new HashMap<>();
        Map<CanvasData, Location> mapLocations = new HashMap<>();

        for (int i = 0; i <= samples; i++) {
            Vector sampleWorld = pStart.clone().add(dir.clone().multiply(i));
            Location sampleLoc = sampleWorld.toLocation(world);

            ItemFrame frame = findFrameNear(world, sampleLoc, facing);
            if (frame != null) {
                CanvasData canvas = canvasManager.getCanvasFromItem(frame.getItem());
                if (canvas != null && !canvas.isProtected() && !canvas.isAnimated()) {
                    Vector offset = sampleWorld.subtract(frame.getLocation().toVector());
                    Point surfacePt = ItemFrameRayTraceUtil.getSurfacePixelFromOffset(frame, offset);
                    if (surfacePt != null) {
                        Point canvasPt = CanvasCoordinateAdapter.surfaceToCanvas(surfacePt.x, surfacePt.y, frame);
                        List<Point> list = mapPoints.computeIfAbsent(canvas, k -> new ArrayList<>());

                        if (!list.isEmpty()) {
                            Point prev = list.get(list.size() - 1);
                            if (prev.x != canvasPt.x || prev.y != canvasPt.y) {
                                double d = prev.distance(canvasPt);
                                if (d > 1.5 && d <= 30.0) {
                                    list.addAll(ItemFrameRayTraceUtil.bresenhamLine(prev.x, prev.y, canvasPt.x, canvasPt.y));
                                } else if (d <= 1.5) {
                                    list.add(canvasPt);
                                }
                            }
                        } else {
                            list.add(canvasPt);
                        }
                        mapLocations.putIfAbsent(canvas, frame.getLocation());
                    }
                }
            }
        }

        boolean anySuccess = false;
        for (Map.Entry<CanvasData, List<Point>> entry : mapPoints.entrySet()) {
            CanvasData canvas = entry.getKey();
            List<Point> points = entry.getValue();
            if (DrawingEngine.applyDrawLine(canvas, points, tool, colorToDraw)) {
                anySuccess = true;
                canvasManager.notifyCanvasUpdated(canvas, mapLocations.get(canvas));
            }
        }
        return anySuccess;
    }

    private static ItemFrame findFrameNear(org.bukkit.World world, Location loc, BlockFace facing) {
        ItemFrame closest = null;
        double minD2 = 0.95;

        for (ItemFrame frame : world.getNearbyEntitiesByType(ItemFrame.class, loc, 1.0)) {
            if (frame.getFacing() == facing) {
                double d2 = frame.getLocation().distanceSquared(loc);
                if (d2 < minD2) {
                    minD2 = d2;
                    closest = frame;
                }
            }
        }
        return closest;
    }
}
