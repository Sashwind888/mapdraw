package sashwind.mc.plugin.mapdraw.network;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.economy.EconomyManager;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;
import sashwind.mc.plugin.mapdraw.util.ImageProcessUtil;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkedUploadManager {

    private final Mapdraw plugin;
    private final CanvasManager canvasManager;
    private final EconomyManager economyManager;

    // 单个玩家最多同时 1 个上传会话
    private final Map<UUID, UploadSession> activeSessions = new ConcurrentHashMap<>();

    private static final int MAX_FILE_SIZE = 8 * 1024 * 1024; // 8MB 最大限制
    private static final long SESSION_TIMEOUT_MS = 30000L;     // 30秒无后续包自动超时释放
    private static final int MAX_IMAGE_DIMENSION = 4096;        // 防解压炸弹

    public ChunkedUploadManager(Mapdraw plugin, CanvasManager canvasManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.canvasManager = canvasManager;
        this.economyManager = economyManager;
    }

    public static class UploadSession {
        final String uploadId;
        final int totalChunks;
        final int totalBytes;
        final String algorithm;
        final int cols;
        final int rows;
        final byte[][] chunks;
        int receivedChunks = 0;
        int receivedBytes = 0;
        long lastActiveTime;

        UploadSession(String uploadId, int totalChunks, int totalBytes, String algorithm, int cols, int rows) {
            this.uploadId = uploadId;
            this.totalChunks = totalChunks;
            this.totalBytes = totalBytes;
            this.algorithm = algorithm;
            this.cols = cols;
            this.rows = rows;
            this.chunks = new byte[totalChunks][];
            this.lastActiveTime = System.currentTimeMillis();
        }
    }

    public interface UploadCallback {
        void onResult(boolean success, String message);
    }

    /**
     * 处理客户端上传的图片数据包（支持单包一次性全收，也支持大文件分包传输）
     */
    public void handleChunk(Player player, String uploadId, int chunkIndex, int totalChunks, int totalBytes,
                            String algorithm, int cols, int rows, byte[] chunkData, UploadCallback callback) {
        if (!player.hasPermission("mapdraw.upload")) {
            callback.onResult(false, plugin.getMessage("no_permission"));
            return;
        }

        // 1. 基础安全边界校验
        if (totalChunks <= 0 || totalBytes <= 0 || totalBytes > MAX_FILE_SIZE) {
            callback.onResult(false, "图片大小超出上限 (最大 8MB)");
            return;
        }
        if (chunkIndex < 0 || chunkIndex >= totalChunks || chunkData == null) {
            callback.onResult(false, "分包参数非法");
            return;
        }

        int maxW = plugin.getConfig().getInt("upload.max_width", 5);
        int maxH = plugin.getConfig().getInt("upload.max_height", 5);
        int maxTotal = plugin.getConfig().getInt("upload.max_total_maps", 25);
        int totalMaps = cols * rows;

        if (cols <= 0 || rows <= 0 || cols > maxW || rows > maxH || totalMaps > maxTotal) {
            String limitMsg = plugin.getMessage("upload_size_exceeded")
                .replace("{max_w}", String.valueOf(maxW))
                .replace("{max_h}", String.valueOf(maxH))
                .replace("{max_total}", String.valueOf(maxTotal));
            callback.onResult(false, limitMsg);
            return;
        }

        // 2. 经济余额检查
        boolean bypassMoney = player.hasPermission("mapdraw.admin.createwithnomoney");
        double costPerMap = economyManager.getUploadCost();
        double totalCost = costPerMap * totalMaps;
        if (!bypassMoney && economyManager.isEnabled() && totalCost > 0) {
            if (!economyManager.hasMoney(player, totalCost)) {
                String msg = plugin.getMessage("no_enough_money_upload")
                    .replace("{count}", String.valueOf(totalMaps))
                    .replace("{cost}", String.valueOf(totalCost));
                callback.onResult(false, msg);
                return;
            }
        }

        // 3. 会话获取或创建
        UploadSession session = activeSessions.get(player.getUniqueId());
        if (session != null && !session.uploadId.equals(uploadId)) {
            // 超时清理或替换
            if (System.currentTimeMillis() - session.lastActiveTime > SESSION_TIMEOUT_MS) {
                activeSessions.remove(player.getUniqueId());
                session = null;
            } else {
                callback.onResult(false, "尚有未完成的上传任务正在进行中");
                return;
            }
        }

        if (session == null) {
            session = new UploadSession(uploadId, totalChunks, totalBytes, algorithm, cols, rows);
            activeSessions.put(player.getUniqueId(), session);
        }

        // 存入当前分包
        if (session.chunks[chunkIndex] == null) {
            session.chunks[chunkIndex] = chunkData;
            session.receivedChunks++;
            session.receivedBytes += chunkData.length;
        }
        session.lastActiveTime = System.currentTimeMillis();

        // 4. 检查是否全部接收完毕
        if (session.receivedChunks >= session.totalChunks) {
            activeSessions.remove(player.getUniqueId());
            processCompletedUpload(player, session, totalCost, bypassMoney, callback);
        } else {
            // 部分分包确认
            callback.onResult(true, String.format("分包接收进度: %d/%d", session.receivedChunks, session.totalChunks));
        }
    }

    private void processCompletedUpload(Player player, UploadSession session, double totalCost, boolean bypassMoney, UploadCallback callback) {
        // 组装完整字节流
        byte[] completeBytes = new byte[session.receivedBytes];
        int offset = 0;
        for (int i = 0; i < session.totalChunks; i++) {
            byte[] chunk = session.chunks[i];
            if (chunk == null) {
                callback.onResult(false, "上传分包缺失，已中止");
                return;
            }
            System.arraycopy(chunk, 0, completeBytes, offset, chunk.length);
            offset += chunk.length;
        }

        // 异步执行解码与安全防炸弹检查
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 安全检查 1: 格式魔数校验
                if (!validateMagicBytes(completeBytes)) {
                    sendResult(callback, false, "无效的图片文件格式 (支持 PNG/GIF/WEBP/JPEG)");
                    return;
                }

                byte bgColor = (byte) plugin.getConfig().getInt("canvas.default_bg_color", 34);
                int maxGifFrames = plugin.getConfig().getInt("gif.max_frames", 80);

                // 解码并处理切片
                ImageProcessUtil.ProcessedImage processed =
                    ImageProcessUtil.downloadAndProcessFromBytes(completeBytes, session.algorithm, session.cols, session.rows, bgColor, maxGifFrames);

                // 回到主线程执行扣费与物品发放
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    if (!bypassMoney && economyManager.isEnabled() && totalCost > 0) {
                        if (!economyManager.withdraw(player, totalCost)) {
                            callback.onResult(false, "扣费失败");
                            return;
                        }
                        String costMsg = plugin.getMessage("upload_cost")
                            .replace("{cost}", String.valueOf(totalCost))
                            .replace("{count}", String.valueOf(session.cols * session.rows));
                        player.sendMessage(plugin.getMessage("prefix") + costMsg);
                    }

                    int baseId = (int) (Math.random() * 9000 + 1000);
                    String groupId = UUID.randomUUID().toString();
                    int configFps = plugin.getConfig().getInt("gif.fps", 8);

                    for (ImageProcessUtil.MapSlice slice : processed.slices) {
                        String sliceName = (session.cols == 1 && session.rows == 1)
                            ? ("网络画作 #" + baseId + (processed.isAnimated ? " (动图)" : ""))
                            : String.format("网络画作 #%d [%d,%d]%s", baseId, slice.col, slice.row, processed.isAnimated ? " (动图)" : "");

                        ItemStack canvasItem = canvasManager.createNewCanvas(player, sliceName, 128);
                        CanvasData canvas = canvasManager.getCanvasFromItem(canvasItem);
                        if (canvas != null) {
                            canvas.setGroupId(groupId);
                            canvas.setPixels(slice.pixels);
                            canvas.setHasPainted(true);
                            if (slice.isAnimated) {
                                canvas.setFps(configFps);
                                canvas.setAnimationFrames(slice.animationFrames);
                                canvas.setProtected(true); // GIF 动图导入自动永久锁定保护
                            }
                            if (session.cols > 1 || session.rows > 1) {
                                canvas.setDescription(String.format("拼接画作 (%dx%d) - 横第 %d 列, 纵第 %d 行%s",
                                    session.cols, session.rows, slice.col, slice.row, slice.isAnimated ? (" [共 " + processed.frameCount + " 帧，动图锁定]") : ""));
                            } else if (slice.isAnimated) {
                                canvas.setDescription("GIF 动态画作 (共 " + processed.frameCount + " 帧，" + configFps + " FPS，动图锁定)");
                            }
                            CanvasNBTUtil.applyCanvasMeta(canvasItem, canvas);
                            canvasManager.notifyCanvasUpdated(canvas);
                        }

                        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(canvasItem);
                        if (!overflow.isEmpty()) {
                            for (ItemStack item : overflow.values()) {
                                player.getWorld().dropItem(player.getLocation(), item);
                            }
                        }
                    }

                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                    callback.onResult(true, "网络图片上传生成成功！共 " + (session.cols * session.rows) + " 张地图");
                });

            } catch (Exception e) {
                sendResult(callback, false, "图片解析失败: " + e.getMessage());
            }
        });
    }

    private void sendResult(UploadCallback callback, boolean success, String msg) {
        Bukkit.getScheduler().runTask(plugin, () -> callback.onResult(success, msg));
    }

    private boolean validateMagicBytes(byte[] data) {
        if (data == null || data.length < 4) return false;
        // PNG: 89 50 4E 47
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return true;
        // GIF: 47 49 46 38 ('GIF8')
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38) return true;
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) return true;
        // WEBP: RIFF....WEBP
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' &&
            data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return true;

        return false;
    }
}
