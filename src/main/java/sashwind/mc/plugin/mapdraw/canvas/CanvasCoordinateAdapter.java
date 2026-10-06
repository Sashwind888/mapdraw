package sashwind.mc.plugin.mapdraw.canvas;

import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import sashwind.mc.plugin.mapdraw.Mapdraw;

import java.awt.Point;

/**
 * 视口操作空间 (Surface/Screen) 与 地图底层像素空间 (Canvas/Storage) 之间的正交坐标隔离适配器
 * 让所有笔迹追踪、平滑插值、多图跨界在纯净的视口空间中进行，仅在底层写入像素时进行映射
 */
public class CanvasCoordinateAdapter {

    /**
     * 将视口坐标 (xs, ys) -> 地图底层像素存储坐标 (cx, cy)
     *
     * @param xs 视口水平坐标 (0-127, 左至右)
     * @param ys 视口垂直坐标 (0-127, 上至下)
     * @param frame 目标展示框
     * @return 底层 Canvas 像素坐标
     */
    public static Point surfaceToCanvas(int xs, int ys, ItemFrame frame) {
        if (frame == null) {
            return new Point(xs, ys);
        }

        // 1. 视口坐标归一化 (0.0 ~ 1.0)
        double u = (double) xs / 128.0;
        double v = (double) ys / 128.0;

        // 2. 应用配置文件中的基础全局偏置 (0: 0°, 1: 90°, 2: 180°, 3: 270°)
        int configRotation = Mapdraw.getInstance().getConfig().getInt("canvas.rotation_offset", 0);
        int steps = (configRotation % 4 + 4) % 4;
        for (int i = 0; i < steps; i++) {
            double nextU = v;
            double nextV = 1.0 - u;
            u = nextU;
            v = nextV;
        }

        // 3. 顺应展示框当前物理物品旋转 (每次旋转 45° 阶梯，地图实际旋转 90°)
        double angle = getMapRenderRotationAngle(frame.getRotation(), frame.getFacing());
        if (angle != 0.0) {
            double cu = u - 0.5;
            double cv = v - 0.5;
            double cos = Math.cos(-angle);
            double sin = Math.sin(-angle);
            u = cu * cos - cv * sin + 0.5;
            v = cu * sin + cv * cos + 0.5;
        }

        u = Math.max(0.0, Math.min(0.9999, u));
        v = Math.max(0.0, Math.min(0.9999, v));

        int cx = Math.max(0, Math.min(127, (int) (u * 128)));
        int cy = Math.max(0, Math.min(127, (int) (v * 128)));

        return new Point(cx, cy);
    }

    /**
     * 地图在展示框中的实际渲染旋转角
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

        return (stepIndex % 4) * (Math.PI / 2.0);
    }
}
