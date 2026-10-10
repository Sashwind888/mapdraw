package sashwind.mc.plugin.mapdraw.canvas;

import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import sashwind.mc.plugin.mapdraw.Mapdraw;

import java.awt.Point;

/**
 * 视口操作空间 (Surface/Screen) 与 地图底层像素空间 (Canvas/Storage) 之间的正交坐标隔离适配器
 * 采用严格的离散整数双射 (Discrete Bijections)，彻底消除浮点数截断造成的 0号/127号边缘整列像素缺失！
 */
public class CanvasCoordinateAdapter {

    /**
     * 将视口坐标 (xs, ys) -> 地图底层像素存储坐标 (cx, cy)
     */
    public static Point surfaceToCanvas(int xs, int ys, ItemFrame frame) {
        if (frame == null) {
            return new Point(xs, ys);
        }

        // 约束在 [0, 127]
        int curX = Math.max(0, Math.min(127, xs));
        int curY = Math.max(0, Math.min(127, ys));

        // 1. 应用配置中的基础旋转步数 (0: 0°, 1: 90°, 2: 180°, 3: 270°)
        int configRotation = Mapdraw.getInstance().getConfig().getInt("canvas.rotation_offset", 0);
        int totalQuarterSteps = (configRotation % 4 + 4) % 4;

        // 2. 累加展示框当前物理物品的旋转步数 (每点击 1 次转 90°)
        int itemQuarterSteps = getMapRenderQuarterSteps(frame.getRotation(), frame.getFacing());
        totalQuarterSteps = (totalQuarterSteps + itemQuarterSteps) % 4;

        // 3. 严格的离散整数逆时针正交旋转映射，彻底杜绝任何浮点截断漏行漏列
        return switch (totalQuarterSteps) {
            case 1 -> new Point(curY, 127 - curX);         // 逆时针 90°
            case 2 -> new Point(127 - curX, 127 - curY);   // 逆时针 180°
            case 3 -> new Point(127 - curY, curX);         // 逆时针 270°
            default -> new Point(curX, curY);              // 0° (不旋转)
        };
    }

    /**
     * 地图在展示框中的实际渲染旋转步数 (0, 1, 2, 3，对应 0°, 90°, 180°, 270°)
     */
    private static int getMapRenderQuarterSteps(Rotation rot, BlockFace facing) {
        if (rot == null) return 0;
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

        return (stepIndex % 4);
    }
}
