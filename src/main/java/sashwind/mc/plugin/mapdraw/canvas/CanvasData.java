package sashwind.mc.plugin.mapdraw.canvas;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class CanvasData implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int MAP_WIDTH = 128;
    public static final int MAP_HEIGHT = 128;
    public static final int TOTAL_PIXELS = MAP_WIDTH * MAP_HEIGHT;
    private static final int MAX_UNDO_STEPS = 30;

    public static final byte COLOR_WHITE = 34; // Minecraft MapPalette 纯白底色

    private final String id;
    private int mapId;
    private String name;
    private String title;
    private String description;
    private int size; // 16, 32, 64, 128
    private boolean isProtected;
    private boolean noCopy;
    private boolean hasPainted;
    private String creator;
    private String groupId;
    private boolean isAnimated;
    private int fps;
    private java.util.List<byte[]> animationFrames;
    private transient int currentFrameIndex;

    // 128x128 像素矩阵 (Map Palette index)
    private byte[] pixels;

    // 撤销 / 重做栈 (不序列化到持久文件以节省磁盘空间，内存维护)
    private transient Deque<byte[]> undoStack;
    private transient Deque<byte[]> redoStack;
    private transient boolean isDirty;
    private transient int version;

    public CanvasData cloneAsNew(String newId, int newMapId, String newCreator) {
        CanvasData copy = new CanvasData(newId, newMapId, this.name + " (副本)", this.size, newCreator);
        copy.setTitle(this.title);
        copy.setDescription(this.description);
        copy.setNoCopy(this.noCopy);
        copy.setProtected(false); // 副本默认不直接锁死保护，由玩家自行控制
        copy.setHasPainted(this.hasPainted);
        copy.setGroupId(this.groupId);
        copy.setAnimated(this.isAnimated);
        copy.setFps(this.fps);
        if (this.animationFrames != null) {
            java.util.List<byte[]> copyFrames = new java.util.ArrayList<>();
            for (byte[] frame : this.animationFrames) {
                copyFrames.add(Arrays.copyOf(frame, frame.length));
            }
            copy.setAnimationFrames(copyFrames);
        }
        copy.setPixels(Arrays.copyOf(this.pixels, this.pixels.length));
        return copy;
    }

    public CanvasData(String id, int mapId, String name, int size, String creator) {
        this(id, mapId, name, size, creator, COLOR_WHITE);
    }

    public CanvasData(String id, int mapId, String name, int size, String creator, byte defaultBgColor) {
        this.id = id;
        this.mapId = mapId;
        this.name = name != null ? name : "未命名画布";
        this.title = this.name;
        this.description = "无描述";
        this.size = size > 0 ? size : 128;
        this.isProtected = false;
        this.noCopy = true;
        this.hasPainted = false;
        this.creator = creator;
        this.pixels = new byte[TOTAL_PIXELS];
        Arrays.fill(this.pixels, defaultBgColor); // 默认纯白底色

        initTransient();
    }

    public void initTransient() {
        if (this.undoStack == null) {
            this.undoStack = new ArrayDeque<>();
        }
        if (this.redoStack == null) {
            this.redoStack = new ArrayDeque<>();
        }
        if (this.pixels == null || this.pixels.length != TOTAL_PIXELS) {
            this.pixels = new byte[TOTAL_PIXELS];
        }
        this.isDirty = true;
        this.version++;
    }

    public synchronized void pushUndoState() {
        if (undoStack == null) {
            undoStack = new ArrayDeque<>();
        }
        if (undoStack.size() >= MAX_UNDO_STEPS) {
            undoStack.removeLast();
        }
        undoStack.push(Arrays.copyOf(this.pixels, this.pixels.length));
        if (redoStack != null) {
            redoStack.clear();
        }
        this.isDirty = true;
        this.version++;
    }

    public synchronized boolean undo() {
        if (undoStack == null || undoStack.isEmpty()) {
            return false;
        }
        if (redoStack == null) {
            redoStack = new ArrayDeque<>();
        }
        redoStack.push(Arrays.copyOf(this.pixels, this.pixels.length));
        this.pixels = undoStack.pop();
        this.isDirty = true;
        this.version++;
        return true;
    }

    public synchronized boolean redo() {
        if (redoStack == null || redoStack.isEmpty()) {
            return false;
        }
        undoStack.push(Arrays.copyOf(this.pixels, this.pixels.length));
        this.pixels = redoStack.pop();
        this.isDirty = true;
        this.version++;
        return true;
    }

    public byte getPixel(int x, int y) {
        if (x < 0 || x >= MAP_WIDTH || y < 0 || y >= MAP_HEIGHT) {
            return 0;
        }
        return pixels[y * MAP_WIDTH + x];
    }

    public void setPixel(int x, int y, byte color) {
        if (x < 0 || x >= MAP_WIDTH || y < 0 || y >= MAP_HEIGHT) {
            return;
        }
        pixels[y * MAP_WIDTH + x] = color;
        this.hasPainted = true;
        this.isDirty = true;
        this.version++;
    }

    public String getId() {
        return id;
    }

    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
        this.isDirty = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.isDirty = true;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.isDirty = true;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.isDirty = true;
    }

    public int getSize() {
        return size;
    }

    public boolean setSize(int newSize) {
        // 如果已经填色，则只能放大不能缩小
        if (this.hasPainted && newSize < this.size) {
            return false;
        }
        this.size = newSize;
        this.isDirty = true;
        return true;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public void setProtected(boolean aProtected) {
        isProtected = aProtected;
        this.isDirty = true;
    }

    public boolean isNoCopy() {
        return noCopy;
    }

    public void setNoCopy(boolean noCopy) {
        this.noCopy = noCopy;
        this.isDirty = true;
    }

    public boolean isHasPainted() {
        return hasPainted;
    }

    public void setHasPainted(boolean hasPainted) {
        this.hasPainted = hasPainted;
        this.isDirty = true;
    }

    public String getCreator() {
        return creator;
    }

    public byte[] getPixels() {
        return pixels;
    }

    public void setPixels(byte[] pixels) {
        this.pixels = pixels;
        this.isDirty = true;
        this.version++;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public int getVersion() {
        return version;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
        this.isDirty = true;
    }

    public boolean isAnimated() {
        return isAnimated;
    }

    public void setAnimated(boolean animated) {
        isAnimated = animated;
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public java.util.List<byte[]> getAnimationFrames() {
        return animationFrames;
    }

    public void setAnimationFrames(java.util.List<byte[]> animationFrames) {
        this.animationFrames = animationFrames;
        this.isAnimated = animationFrames != null && animationFrames.size() > 1;
        this.currentFrameIndex = 0;
    }

    public synchronized boolean syncAnimationFrame(long nowMs, int defaultFps) {
        if (!isAnimated || animationFrames == null || animationFrames.size() <= 1) {
            return false;
        }
        int activeFps = this.fps > 0 ? this.fps : defaultFps;
        activeFps = Math.max(1, Math.min(20, activeFps));

        // 统一基于毫秒时间戳计算理论帧索引，保证所有拆分切片完全同步无撕裂
        int totalFrames = animationFrames.size();
        long frameDurationMs = 1000L / activeFps;
        int targetIndex = (int) ((nowMs / frameDurationMs) % totalFrames);
        if (targetIndex < 0) targetIndex = 0;

        if (targetIndex != this.currentFrameIndex) {
            this.currentFrameIndex = targetIndex;
            this.pixels = animationFrames.get(targetIndex);
            this.isDirty = true;
            this.version++;
            return true;
        }
        return false;
    }

    public synchronized boolean nextAnimationFrame() {
        if (!isAnimated || animationFrames == null || animationFrames.isEmpty()) {
            return false;
        }
        currentFrameIndex = (currentFrameIndex + 1) % animationFrames.size();
        this.pixels = animationFrames.get(currentFrameIndex);
        this.isDirty = true;
        this.version++;
        return true;
    }
}
