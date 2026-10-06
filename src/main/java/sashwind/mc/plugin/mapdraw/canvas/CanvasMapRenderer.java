package sashwind.mc.plugin.mapdraw.canvas;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CanvasMapRenderer extends MapRenderer {

    private final CanvasData canvasData;
    private final Set<UUID> renderedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int renderedVersion = -1;

    public CanvasMapRenderer(CanvasData canvasData) {
        super(false); // 全局共享画布
        this.canvasData = canvasData;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        // 清理原版指针光标
        for (int i = canvas.getCursors().size() - 1; i >= 0; i--) {
            canvas.getCursors().removeCursor(canvas.getCursors().getCursor(i));
        }

        UUID pid = player != null ? player.getUniqueId() : null;
        int currentVersion = canvasData.getVersion();

        boolean needsDataPush = false;
        // 1. 如果版本更新或标记脏，必须推送最新像素
        if (renderedVersion != currentVersion || canvasData.isDirty()) {
            needsDataPush = true;
            renderedPlayers.clear();
        }
        // 2. 如果新玩家进入视野尚未接收过当前画面，确保推送
        else if (pid != null && !renderedPlayers.contains(pid)) {
            needsDataPush = true;
        }

        if (needsDataPush) {
            byte[] pixels = canvasData.getPixels();
            // 采用高能扁平化单层位运算循环，减少乘法寻址和方法开销
            for (int i = 0; i < CanvasData.TOTAL_PIXELS; i++) {
                canvas.setPixel(i & 127, i >> 7, pixels[i]);
            }
            if (pid != null) {
                renderedPlayers.add(pid);
            }
            this.renderedVersion = currentVersion;
            canvasData.setDirty(false);
        }
    }

    public void markDirty() {
        this.renderedPlayers.clear();
        this.renderedVersion = -1;
        this.canvasData.setDirty(true);
    }
}
