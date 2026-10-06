package sashwind.mc.plugin.mapdraw.canvas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CanvasManager {

    private final Mapdraw plugin;
    private final File dataFolder;
    private final Map<String, CanvasData> canvasById = new ConcurrentHashMap<>();
    private final Map<Integer, CanvasData> canvasByMapId = new ConcurrentHashMap<>();
    private final Map<String, CanvasMapRenderer> renderers = new ConcurrentHashMap<>();

    // 多线程异步 IO 保存线程池，彻底解除主线程磁盘阻塞
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MapDraw-IO-Worker");
        t.setDaemon(true);
        return t;
    });

    public CanvasManager(Mapdraw plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "canvases");
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
    }

    public void loadAll() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            CanvasData canvas = loadFromFile(file);
            if (canvas != null) {
                registerCanvas(canvas);
                attachRenderer(canvas);
            }
        }

        // 延迟 1 tick 再次确保所有世界与 MapView 完全初始化完毕后重新绑定，防止服务端冷重启时丢失
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (CanvasData canvas : canvasById.values()) {
                attachRenderer(canvas);
            }
            // 全服全量同步已放置的展示框地图：修复所有名称和描述不显示的问题
            syncAllLoadedFrames();
        });
        plugin.getLogger().info("成功加载 " + canvasById.size() + " 个画布数据！");
    }

    /**
     * 全服全量扫描所有世界中已加载的展示框，自动同步并修复所有名称和描述
     */
    public void syncAllLoadedFrames() {
        int repairedCount = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.ItemFrame frame : world.getEntitiesByClass(org.bukkit.entity.ItemFrame.class)) {
                ItemStack item = frame.getItem();
                if (CanvasNBTUtil.isCanvasMap(item)) {
                    CanvasData canvas = getCanvasFromItem(item);
                    if (canvas != null) {
                        CanvasNBTUtil.applyCanvasMeta(item, canvas);
                        frame.setCustomNameVisible(false);
                        frame.customName(null);
                        frame.setItem(item, false);
                        if (item.getItemMeta() instanceof MapMeta meta && meta.hasMapView()) {
                            checkAndAttachRenderer(meta.getMapView(), canvas);
                        }
                        repairedCount++;
                    }
                }
            }
        }
        if (repairedCount > 0) {
            plugin.getLogger().info("已全量同步并修复 " + repairedCount + " 个展示框画作的名称与描述数据！");
        }
    }

    public void saveAll() {
        for (CanvasData canvas : canvasById.values()) {
            saveToFile(canvas);
        }
    }

    public void shutdown() {
        saveAll();
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
        }
    }

    public void registerCanvas(CanvasData canvas) {
        canvas.initTransient();
        canvasById.put(canvas.getId(), canvas);
        canvasByMapId.put(canvas.getMapId(), canvas);
    }

    public CanvasData getCanvasById(String id) {
        if (id == null) return null;
        return canvasById.get(id);
    }

    public CanvasData getCanvasByMapId(int mapId) {
        return canvasByMapId.get(mapId);
    }

    public Collection<CanvasData> getAllCanvases() {
        return canvasById.values();
    }

    public CanvasData getCanvasFromItem(ItemStack item) {
        if (item == null) {
            return null;
        }

        String id = CanvasNBTUtil.getCanvasId(item);
        CanvasData canvas = null;
        if (id != null && canvasById.containsKey(id)) {
            canvas = canvasById.get(id);
        }

        if (canvas == null && item.getItemMeta() instanceof MapMeta mapMeta && mapMeta.hasMapView()) {
            MapView view = mapMeta.getMapView();
            if (view != null) {
                canvas = canvasByMapId.get(view.getId());
            }
        }

        if (canvas != null) {
            // 自动检查并修复可能缺失的 Lore 或错误隐藏的 Tooltip，保证物品栏信息框正常可见
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null && (meta.isHideTooltip() || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty())) {
                CanvasNBTUtil.applyCanvasMeta(item, canvas);
            }

            if (item.getItemMeta() instanceof MapMeta mapMeta && mapMeta.hasMapView()) {
                checkAndAttachRenderer(mapMeta.getMapView(), canvas);
            }
        }
        return canvas;
    }

    public ItemStack createCloneCanvas(Player player, CanvasData parent) {
        World world = player.getWorld();
        MapView mapView = Bukkit.createMap(world);

        String newId = UUID.randomUUID().toString();
        CanvasData clone = parent.cloneAsNew(newId, mapView.getId(), player.getName());

        registerCanvas(clone);
        attachRenderer(clone);
        saveToFile(clone);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(mapView);
        mapItem.setItemMeta(meta);

        CanvasNBTUtil.applyCanvasMeta(mapItem, clone);
        return mapItem;
    }

    public ItemStack createNewCanvas(Player player, String name, int size) {
        World world = player.getWorld();
        MapView mapView = Bukkit.createMap(world);

        byte bgColor = (byte) plugin.getConfig().getInt("canvas.default_bg_color", 34);
        String canvasId = UUID.randomUUID().toString();
        CanvasData canvas = new CanvasData(canvasId, mapView.getId(), name, size, player.getName(), bgColor);

        registerCanvas(canvas);
        attachRenderer(canvas);
        saveToFile(canvas);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(mapView);
        mapItem.setItemMeta(meta);

        CanvasNBTUtil.applyCanvasMeta(mapItem, canvas);
        return mapItem;
    }

    public void attachRenderer(CanvasData canvas) {
        MapView view = Bukkit.getMap(canvas.getMapId());
        if (view == null) {
            return;
        }

        // 彻底清空包括原版世界地图在内的所有渲染器
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(r);
        }

        // 移除原版光标（例如绿色玩家标记、指南针图标等）
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);

        CanvasMapRenderer renderer = new CanvasMapRenderer(canvas);
        view.addRenderer(renderer);
        renderers.put(canvas.getId(), renderer);
        renderer.markDirty();
    }

    public void checkAndAttachRenderer(MapView view, CanvasData canvas) {
        if (view == null || canvas == null) return;
        boolean hasCustom = false;
        for (MapRenderer r : view.getRenderers()) {
            if (r instanceof CanvasMapRenderer) {
                hasCustom = true;
                break;
            }
        }
        if (!hasCustom) {
            attachRenderer(canvas);
        }
    }

    public void notifyCanvasRenderOnly(CanvasData canvas) {
        CanvasMapRenderer renderer = renderers.get(canvas.getId());
        if (renderer != null) {
            renderer.markDirty();
        }
    }

    public void notifyCanvasUpdated(CanvasData canvas) {
        notifyCanvasUpdated(canvas, null);
    }

    public void notifyCanvasUpdated(CanvasData canvas, org.bukkit.Location loc) {
        CanvasMapRenderer renderer = renderers.get(canvas.getId());
        if (renderer != null) {
            renderer.markDirty();
        }

        // 规避原版展示框低频轮询机制，主动向附近玩家直发原生地图数据包，实现 0 延迟实时绘制
        sashwind.mc.plugin.mapdraw.network.FastMapPacketSender.sendDirectMapPacket(canvas, loc);

        // 多线程后台异步保存，永不阻塞主线程
        saveToFileAsync(canvas);
    }

    public void saveToFileAsync(CanvasData canvas) {
        if (canvas == null) return;
        ioExecutor.submit(() -> saveToFile(canvas));
    }

    private static final int MAGIC_HEADER = 0x4D445257; // "MDRW"
    private static final int FILE_VERSION = 2;

    public void saveToFile(CanvasData canvas) {
        File file = new File(dataFolder, canvas.getId() + ".dat");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC_HEADER);
            out.writeInt(FILE_VERSION);

            out.writeUTF(canvas.getId());
            out.writeInt(canvas.getMapId());
            out.writeUTF(canvas.getName() != null ? canvas.getName() : "");
            out.writeUTF(canvas.getTitle() != null ? canvas.getTitle() : "");
            out.writeUTF(canvas.getDescription() != null ? canvas.getDescription() : "");
            out.writeInt(canvas.getSize());
            out.writeBoolean(canvas.isProtected());
            out.writeBoolean(canvas.isNoCopy());
            out.writeBoolean(canvas.isHasPainted());
            out.writeUTF(canvas.getCreator() != null ? canvas.getCreator() : "");
            out.writeUTF(canvas.getGroupId() != null ? canvas.getGroupId() : "");

            byte[] pixels = canvas.getPixels();
            out.writeInt(pixels.length);
            out.write(pixels);

            // 保存动画数据
            out.writeBoolean(canvas.isAnimated());
            if (canvas.isAnimated()) {
                out.writeInt(canvas.getFps());
                java.util.List<byte[]> frames = canvas.getAnimationFrames();
                if (frames != null) {
                    out.writeInt(frames.size());
                    for (byte[] frame : frames) {
                        out.writeInt(frame.length);
                        out.write(frame);
                    }
                } else {
                    out.writeInt(0);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("保存画布 " + canvas.getId() + " 失败: " + e.getMessage());
        }
    }

    private CanvasData loadFromFile(File file) {
        try {
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            if (fileBytes.length < 16) {
                return null;
            }

            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(fileBytes))) {
                int magic = in.readInt();
                if (magic != MAGIC_HEADER) {
                    // 没有魔数，说明是历史 V1 格式文件，转由兼容解析器加载
                    return loadV1File(fileBytes, file.getName());
                }

                int version = in.readInt();
                String id = in.readUTF();
                int mapId = in.readInt();
                String name = in.readUTF();
                String title = in.readUTF();
                String description = in.readUTF();
                int size = in.readInt();
                boolean isProtected = in.readBoolean();
                boolean noCopy = in.readBoolean();
                boolean hasPainted = in.readBoolean();
                String creator = in.readUTF();
                String groupId = in.readUTF();

                int pixelLen = in.readInt();
                byte[] pixels = new byte[pixelLen];
                in.readFully(pixels);

                CanvasData canvas = new CanvasData(id, mapId, name, size, creator);
                canvas.setGroupId(groupId.isEmpty() ? null : groupId);
                canvas.setTitle(title);
                canvas.setDescription(description);
                canvas.setProtected(isProtected);
                canvas.setNoCopy(noCopy);
                canvas.setHasPainted(hasPainted);
                canvas.setPixels(pixels);

                if (in.available() > 0) {
                    boolean isAnimated = in.readBoolean();
                    if (isAnimated && in.available() > 0) {
                        int fps = in.readInt();
                        int frameCount = in.readInt();
                        java.util.List<byte[]> frames = new java.util.ArrayList<>();
                        for (int i = 0; i < frameCount; i++) {
                            int fLen = in.readInt();
                            byte[] fBytes = new byte[fLen];
                            in.readFully(fBytes);
                            frames.add(fBytes);
                        }
                        canvas.setFps(fps);
                        canvas.setAnimationFrames(frames);
                    }
                }
                return canvas;
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("读取画布文件 " + file.getName() + " 失败: " + e.getMessage());
            return null;
        }
    }

    private CanvasData loadV1File(byte[] fileBytes, String fileName) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(fileBytes))) {
            String id = in.readUTF();
            int mapId = in.readInt();
            String name = in.readUTF();
            String title = in.readUTF();
            String description = in.readUTF();
            int size = in.readInt();
            boolean isProtected = in.readBoolean();
            boolean noCopy = in.readBoolean();
            boolean hasPainted = in.readBoolean();
            String creator = in.readUTF();

            // 历史 V1 版本直接是 pixelLen (16384)，无 groupId
            int pixelLen = in.readInt();
            byte[] pixels = new byte[pixelLen];
            in.readFully(pixels);

            CanvasData canvas = new CanvasData(id, mapId, name, size, creator);
            canvas.setTitle(title);
            canvas.setDescription(description);
            canvas.setProtected(isProtected);
            canvas.setNoCopy(noCopy);
            canvas.setHasPainted(hasPainted);
            canvas.setPixels(pixels);

            if (in.available() > 0) {
                boolean isAnimated = in.readBoolean();
                if (isAnimated && in.available() > 0) {
                    int fps = in.readInt();
                    int frameCount = in.readInt();
                    java.util.List<byte[]> frames = new java.util.ArrayList<>();
                    for (int i = 0; i < frameCount; i++) {
                        int fLen = in.readInt();
                        byte[] fBytes = new byte[fLen];
                        in.readFully(fBytes);
                        frames.add(fBytes);
                    }
                    canvas.setFps(fps);
                    canvas.setAnimationFrames(frames);
                }
            }
            return canvas;
        } catch (Throwable e) {
            plugin.getLogger().warning("解析历史 V1 画布文件 " + fileName + " 失败: " + e.getMessage());
            return null;
        }
    }
}
