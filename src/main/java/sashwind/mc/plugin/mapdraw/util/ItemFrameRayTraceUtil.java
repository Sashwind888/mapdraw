package sashwind.mc.plugin.mapdraw.util;

import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.awt.Point;

public class ItemFrameRayTraceUtil {

    /**
     * 根据玩家视线光线追踪（RayTrace），获取与展示框平面的精确三维世界交点
     */
    public static Vector getWorldHitPosFromRayTrace(Player player, ItemFrame frame) {
        if (player == null || frame == null) {
            return null;
        }

        Vector eyePos = player.getEyeLocation().toVector();
        Vector eyeDir = player.getEyeLocation().getDirection().normalize();

        Vector frameCenter = frame.getLocation().toVector();
        BlockFace facing = frame.getFacing();
        Vector normal = facing.getDirection();

        double denom = eyeDir.dot(normal);
        if (denom >= -1e-6) {
            return null;
        }

        double t = frameCenter.clone().subtract(eyePos).dot(normal) / denom;
        if (t < 0 || t > 5.5) {
            return null;
        }

        return eyePos.clone().add(eyeDir.clone().multiply(t));
    }

    /**
     * 根据玩家视线光线追踪（RayTrace），精确计算玩家指向展示框地图的像素坐标 (0~127, 0~127)
     */
    public static Point getMapPixelFromRayTrace(Player player, ItemFrame frame) {
        Vector hitPos = getWorldHitPosFromRayTrace(player, frame);
        if (hitPos == null) return null;

        Vector offset = hitPos.subtract(frame.getLocation().toVector());
        return getMapPixelFromOffset(frame, offset);
    }

    /**
     * 获取玩家视线落在展示框物理平面的纯净视口像素坐标 (xs, ys) ∈ [0, 127]
     * 仅做展示框朝向的投影，绝对不包含任何单个地图内部的旋转变换
     */
    public static Point getSurfacePixelFromOffset(ItemFrame frame, Vector offset) {
        if (frame == null || offset == null) {
            return null;
        }

        BlockFace facing = frame.getFacing();
        Vector right = getRightVector(facing);
        Vector down = getDownVector(facing);

        double u = offset.dot(right) + 0.5;
        double v = offset.dot(down) + 0.5;

        // 地面/天花板修正
        if (facing == BlockFace.UP || facing == BlockFace.DOWN) {
            u = 1.0 - u;
            v = 1.0 - v;
        }

        u = Math.max(0.0, Math.min(0.9999, u));
        v = Math.max(0.0, Math.min(0.9999, v));

        int xs = Math.max(0, Math.min(127, (int) (u * 128)));
        int ys = Math.max(0, Math.min(127, (int) (v * 128)));

        return new Point(xs, ys);
    }

    public static Point getSurfacePixelFromRayTrace(Player player, ItemFrame frame) {
        Vector hitPos = getWorldHitPosFromRayTrace(player, frame);
        if (hitPos == null) return null;
        Vector offset = hitPos.subtract(frame.getLocation().toVector());
        return getSurfacePixelFromOffset(frame, offset);
    }

    /**
     * 将相对于展示框中心的世界位移向量 offset 转换为底层地图像素坐标 (通过 CanvasCoordinateAdapter 隔离映射)
     */
    public static Point getMapPixelFromOffset(ItemFrame frame, Vector offset) {
        Point surfacePt = getSurfacePixelFromOffset(frame, offset);
        if (surfacePt == null) return null;
        return sashwind.mc.plugin.mapdraw.canvas.CanvasCoordinateAdapter.surfaceToCanvas(surfacePt.x, surfacePt.y, frame);
    }

    public static Point getMapPixelFromOffset(ItemFrame frame, Vector offset, int rotationSteps) {
        return getMapPixelFromOffset(frame, offset);
    }

    /**
     * Bresenham 画线算法 (全向无死角 DDA 插值)，平滑连接两点之间的像素线段，杜绝连续作画断点和空隙
     */
    public static java.util.List<Point> bresenhamLine(int x0, int y0, int x1, int y1) {
        java.util.List<Point> line = new java.util.ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            line.add(new Point(x0, y0));
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
        return line;
    }

    /**
     * Catmull-Rom 样条曲线平滑连接：根据玩家运动历史和切线方向，在 p1 到 p2 之间生成自然圆润的曲线点阵
     */
    public static java.util.List<Point> smoothSplineCurve(Point p0, Point p1, Point p2, Point p3) {
        if (p0 == null) p0 = p1;
        if (p3 == null) p3 = p2;

        java.util.List<Point> result = new java.util.ArrayList<>();
        double dist = p1.distance(p2);
        int steps = Math.max(2, (int) Math.ceil(dist * 2.0)); // 细分步数保证无断点

        Point lastAdded = null;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double t2 = t * t;
            double t3 = t2 * t;

            // Catmull-Rom 矩阵插值计算
            double x = 0.5 * ((2 * p1.x) +
                (-p0.x + p2.x) * t +
                (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);

            double y = 0.5 * ((2 * p1.y) +
                (-p0.y + p2.y) * t +
                (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);

            int px = Math.max(0, Math.min(127, (int) Math.round(x)));
            int py = Math.max(0, Math.min(127, (int) Math.round(y)));

            Point cur = new Point(px, py);
            if (lastAdded != null && (lastAdded.x != cur.x || lastAdded.y != cur.y)) {
                // 连接两个相近浮点步进之间的离散像素
                java.util.List<Point> sub = bresenhamLine(lastAdded.x, lastAdded.y, cur.x, cur.y);
                result.addAll(sub);
            } else if (lastAdded == null) {
                result.add(cur);
            }
            lastAdded = cur;
        }
        return result;
    }

    /**
     * 地图在展示框中的实际渲染旋转（每次物理旋转45度，地图实际渲染旋转90度）
     * 对应序列：
     * 初始 CLOCKWISE (第1次摆下) -> 0°
     * CLOCKWISE_135 (点击第1次)   -> 90°
     * FLIPPED (点击第2次)         -> 180°
     * FLIPPED_45 (点击第3次)      -> 270°
     * COUNTER_CLOCKWISE (点击第4次)-> 360° (0°)
     * COUNTER_CLOCKWISE_45 (第5次)-> 90°
     * NONE (点击第6次)            -> 180°
     * CLOCKWISE_45 (点击第7次)    -> 270°
     * CLOCKWISE (点击第8次回到初始)-> 0°
     */
    private static double getMapRenderRotationAngle(Rotation rot, BlockFace facing) {
        if (rot == null) return 0.0;
        int stepIndex = switch (rot) {
            case CLOCKWISE -> 0;
            case CLOCKWISE_135 -> 1;
            case FLIPPED -> 2;
            case FLIPPED_45 -> 3;
            case COUNTER_CLOCKWISE -> 4;
            case COUNTER_CLOCKWISE_45 -> 5;
            case NONE -> 6;
            case CLOCKWISE_45 -> 7;
        };

        // 垂直墙面初始为 NONE，若在墙面上则以 NONE 为 0 步
        if (facing != BlockFace.UP && facing != BlockFace.DOWN) {
            stepIndex = switch (rot) {
                case NONE -> 0;
                case CLOCKWISE_45 -> 1;
                case CLOCKWISE -> 2;
                case CLOCKWISE_135 -> 3;
                case FLIPPED -> 4;
                case FLIPPED_45 -> 5;
                case COUNTER_CLOCKWISE -> 6;
                case COUNTER_CLOCKWISE_45 -> 7;
            };
        }

        // 核心规则：每次点击（虽然展示框内部+45°），地图实际画面旋转 90° (Math.PI / 2)！
        int rotQuarters = (stepIndex % 4); // 4次点击即完成一整圈 360° 视觉旋转
        return rotQuarters * (Math.PI / 2.0);
    }

    /**
     * 获取展示框正方形平面的水平向右单位向量 (Right Vector)
     */
    private static Vector getRightVector(BlockFace facing) {
        return switch (facing) {
            case SOUTH -> new Vector(1, 0, 0);   // 面向南(+Z)，观察者面朝北，右侧是东(+X)
            case NORTH -> new Vector(-1, 0, 0);  // 面向北(-Z)，观察者面朝南，右侧是西(-X)
            case WEST  -> new Vector(0, 0, 1);   // 面向西(-X)，观察者面朝东，右侧是南(+Z)
            case EAST  -> new Vector(0, 0, -1);  // 面向东(+X)，观察者面朝西，右侧是北(-Z)
            case UP    -> new Vector(1, 0, 0);   // 朝上放置在地面，默认顶部是北(-Z)，右侧是东(+X)
            case DOWN  -> new Vector(1, 0, 0);   // 朝下放置在天花板，右侧是东(+X)
            default    -> new Vector(1, 0, 0);
        };
    }

    /**
     * 获取展示框正方形平面的垂直向下单位向量 (Down Vector)
     */
    private static Vector getDownVector(BlockFace facing) {
        return switch (facing) {
            case SOUTH, NORTH, WEST, EAST -> new Vector(0, -1, 0); // 垂直墙面，向下是 -Y
            case UP   -> new Vector(0, 0, 1);  // 朝上放置在地面，向下是南(+Z)
            case DOWN -> new Vector(0, 0, -1); // 朝下放置在天花板，向下是北(-Z)
            default   -> new Vector(0, -1, 0);
        };
    }
}
