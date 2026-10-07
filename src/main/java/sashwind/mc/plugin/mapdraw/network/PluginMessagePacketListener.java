package sashwind.mc.plugin.mapdraw.network;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.api.DrawResult;
import sashwind.mc.plugin.mapdraw.api.MapDrawAPI;
import sashwind.mc.plugin.mapdraw.api.MapDrawProvider;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;
import sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology;
import sashwind.mc.plugin.mapdraw.tool.ToolType;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PluginMessagePacketListener implements PluginMessageListener {

    private final Mapdraw plugin;

    /**
     * 每个玩家最近一次 0x11 QUERY_CONNECTED 得到的矩阵。
     *
     * <p>0x12 DRAW_GRID_PIXEL 的全局坐标是客户端按「它收到的那份 0x84」算出来的，
     * 所以落笔时必须用<b>同一份矩阵</b>切片。以前是拿基准展示框重新搜一遍，
     * 换一个基准框可能搜出不同的包围盒（归一化原点不同）→ 坐标错位/被判超范围。</p>
     */
    private final Map<UUID, CachedMatrix> matrixCache = new ConcurrentHashMap<>();

    /** 缓存的矩阵 + 记录时间（超时后重新搜，避免世界被改动后一直用旧矩阵）。 */
    private static final class CachedMatrix {
        final ConnectedCanvasTopology.CanvasMatrix matrix;
        final long at;

        CachedMatrix(ConnectedCanvasTopology.CanvasMatrix matrix, long at) {
            this.matrix = matrix;
            this.at = at;
        }
    }

    private static final long MATRIX_CACHE_TTL_MS = 10 * 60 * 1000L;

    public PluginMessagePacketListener(Mapdraw plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(plugin.getNetworkChannel())) {
            return;
        }

        if (message == null || message.length < 1) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        byte packetId = in.readByte();

        MapDrawAPI api = MapDrawProvider.get();

        switch (packetId) {
            case PacketProtocol.C2S_DRAW_PIXEL -> handleDrawPixel(player, in, api);
            case PacketProtocol.C2S_DRAW_BATCH -> handleDrawBatch(player, in, api);
            case PacketProtocol.C2S_UNDO -> handleUndo(player, in, api);
            case PacketProtocol.C2S_REDO -> handleRedo(player, in, api);
            case PacketProtocol.C2S_PROTECT -> handleProtect(player, in, api);
            case PacketProtocol.C2S_DEPROTECT -> handleDeprotect(player, in, api);
            case PacketProtocol.C2S_SET_META -> handleSetMeta(player, in, api);
            case PacketProtocol.C2S_CREATE_CANVAS -> handleCreateCanvas(player, in, api);
            case PacketProtocol.C2S_SET_TOOL -> handleSetTool(player, in, api);
            case PacketProtocol.C2S_SET_COLOR -> handleSetColor(player, in, api);
            case PacketProtocol.C2S_OPEN_GUI -> handleOpenGui(player, in, api);
            case PacketProtocol.C2S_REQUEST_CANVAS -> handleRequestCanvas(player, in, api);
            case PacketProtocol.C2S_SET_CHEST_GUI -> handleSetChestGui(player, in, api);
            case PacketProtocol.C2S_REQUEST_CANVAS_INFO -> handleRequestCanvasInfo(player, in, api);
            case PacketProtocol.C2S_UPLOAD_CHUNK -> handleUploadChunk(player, in);
            case PacketProtocol.C2S_QUERY_CONNECTED -> handleQueryConnected(player, in);
            case PacketProtocol.C2S_DRAW_GRID_PIXEL -> handleDrawGridPixel(player, in, api);
            default -> sendResponse(player, packetId, false, "未知的数据包 ID: " + packetId);
        }
    }

    // 0x01: 画单点
    private void handleDrawPixel(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        short x = in.readShort();
        short y = in.readShort();
        byte toolByte = in.readByte();
        byte colorByte = in.readByte();

        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_DRAW_PIXEL, false, "目标画布不存在");
            return;
        }

        ToolType tool = parseTool(toolByte);
        DrawResult result = api.drawPixel(player, canvas, x, y, tool, colorByte);
        sendResponse(player, PacketProtocol.C2S_DRAW_PIXEL, result.isSuccess(), result.getMessage());
    }

    // 0x02: 连续批量画点 (Mod 平滑画笔)
    private void handleDrawBatch(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        byte toolByte = in.readByte();
        byte colorByte = in.readByte();
        short count = in.readShort();

        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_DRAW_BATCH, false, "目标画布不存在");
            return;
        }

        ToolType tool = parseTool(toolByte);
        boolean anySuccess = false;
        String lastError = null;

        for (int i = 0; i < count; i++) {
            short x = in.readShort();
            short y = in.readShort();
            DrawResult res = api.drawPixel(player, canvas, x, y, tool, colorByte);
            if (res.isSuccess()) {
                anySuccess = true;
            } else {
                lastError = res.getMessage();
            }
        }

        sendResponse(player, PacketProtocol.C2S_DRAW_BATCH, anySuccess, anySuccess ? "批量绘制成功" : lastError);
    }

    // 0x03: 撤销
    private void handleUndo(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_UNDO, false, "画布不存在");
            return;
        }
        DrawResult res = api.undo(player, canvas);
        sendResponse(player, PacketProtocol.C2S_UNDO, res.isSuccess(), res.getMessage());
    }

    // 0x04: 重做
    private void handleRedo(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_REDO, false, "画布不存在");
            return;
        }
        DrawResult res = api.redo(player, canvas);
        sendResponse(player, PacketProtocol.C2S_REDO, res.isSuccess(), res.getMessage());
    }

    // 0x05: 锁定保护
    private void handleProtect(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_PROTECT, false, "画布不存在");
            return;
        }
        DrawResult res = api.protectCanvas(player, canvas, true);
        sendResponse(player, PacketProtocol.C2S_PROTECT, res.isSuccess(), res.getMessage());
    }

    // 0x06: 解除保护
    private void handleDeprotect(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_DEPROTECT, false, "画布不存在");
            return;
        }
        DrawResult res = api.deprotectCanvas(player, canvas);
        sendResponse(player, PacketProtocol.C2S_DEPROTECT, res.isSuccess(), res.getMessage());
    }

    // 0x07: 修改属性 (标题/描述/尺寸/防拷贝)
    private void handleSetMeta(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        byte fieldType = in.readByte();
        String value = in.readUTF();

        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_SET_META, false, "画布不存在");
            return;
        }

        DrawResult res = switch (fieldType) {
            case PacketProtocol.FIELD_TITLE -> api.setTitle(player, canvas, value);
            case PacketProtocol.FIELD_DESCRIPTION -> api.setDescription(player, canvas, value);
            case PacketProtocol.FIELD_SIZE -> {
                try {
                    yield api.setSize(player, canvas, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    yield DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "尺寸必须是数字");
                }
            }
            case PacketProtocol.FIELD_NO_COPY -> api.setNoCopy(player, canvas, Boolean.parseBoolean(value));
            default -> DrawResult.failure(DrawResult.Status.INVALID_ARGUMENTS, "未知字段类型");
        };

        sendResponse(player, PacketProtocol.C2S_SET_META, res.isSuccess(), res.getMessage());
    }

    // 0x08: 申请创建画布
    private void handleCreateCanvas(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String name = in.readUTF();
        int size = in.readInt();
        DrawResult res = api.createCanvas(player, name, size);
        sendResponse(player, PacketProtocol.C2S_CREATE_CANVAS, res.isSuccess(), res.getMessage());
    }

    // 0x09: 切换工具
    private void handleSetTool(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        byte toolByte = in.readByte();
        ToolType tool = parseTool(toolByte);
        DrawResult res = api.setTool(player, tool);
        sendResponse(player, PacketProtocol.C2S_SET_TOOL, res.isSuccess(), res.getMessage());
    }

    // 0x0A: 设置颜色
    private void handleSetColor(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        int r = in.readInt() & 0xFF;
        int g = in.readInt() & 0xFF;
        int b = in.readInt() & 0xFF;
        byte mapColor = (byte) org.bukkit.map.MapPalette.matchColor(r, g, b);

        DrawResult res = api.setColor(player, new Color(r, g, b), mapColor);
        sendResponse(player, PacketProtocol.C2S_SET_COLOR, res.isSuccess(), res.getMessage());
    }

    // 0x0B: 打开 GUI
    private void handleOpenGui(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        byte guiType = in.readByte();
        String canvasId = in.readUTF();
        CanvasData canvas = canvasId.isEmpty() ? null : api.getCanvas(canvasId);

        DrawResult res = (guiType == 1) ? api.openPalette(player) : api.openMenu(player, canvas);
        sendResponse(player, PacketProtocol.C2S_OPEN_GUI, res.isSuccess(), res.getMessage());
    }

    // 0x0C: 客户端请求完整画布数据
    private void handleRequestCanvas(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_REQUEST_CANVAS, false, "画布不存在");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(PacketProtocol.S2C_SYNC_CANVAS);
        out.writeUTF(canvas.getId());
        out.writeInt(canvas.getMapId());
        out.writeUTF(canvas.getName());
        out.writeUTF(canvas.getTitle());
        out.writeUTF(canvas.getDescription());
        out.writeInt(canvas.getSize());
        out.writeBoolean(canvas.isProtected());
        out.writeBoolean(canvas.isNoCopy());
        out.writeUTF(canvas.getCreator() != null ? canvas.getCreator() : "");
        out.writeBoolean(canvas.isAnimated());
        out.writeInt(canvas.getFps());
        out.writeInt(canvas.getAnimationFrames() != null ? canvas.getAnimationFrames().size() : 0);

        byte[] pixels = canvas.getPixels();
        out.writeInt(pixels.length);
        out.write(pixels);

        player.sendPluginMessage(plugin, plugin.getNetworkChannel(), out.toByteArray());
    }

    // 0x0D: 设置是否启用服务端箱子菜单
    private void handleSetChestGui(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        boolean enabled = in.readBoolean();
        api.setChestGuiEnabled(player, enabled);
        sendResponse(player, PacketProtocol.C2S_SET_CHEST_GUI, true, enabled ? "服务端箱子菜单已启用" : "服务端箱子菜单已禁用");
    }

    // 0x0E: 请求画布元数据 (轻量查询，明确包含是不是 GIF 动图及动画属性)
    private void handleRequestCanvasInfo(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        String canvasId = in.readUTF();
        CanvasData canvas = api.getCanvas(canvasId);
        if (canvas == null) {
            sendResponse(player, PacketProtocol.C2S_REQUEST_CANVAS_INFO, false, "画布不存在");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(PacketProtocol.S2C_CANVAS_INFO);
        out.writeUTF(canvas.getId());
        out.writeInt(canvas.getMapId());
        out.writeUTF(canvas.getName());
        out.writeUTF(canvas.getTitle());
        out.writeUTF(canvas.getDescription());
        out.writeInt(canvas.getSize());
        out.writeBoolean(canvas.isProtected());
        out.writeBoolean(canvas.isNoCopy());
        out.writeUTF(canvas.getCreator() != null ? canvas.getCreator() : "");
        out.writeBoolean(canvas.isAnimated()); // 明确返回是不是 GIF 动图
        out.writeInt(canvas.getFps());
        out.writeInt(canvas.getAnimationFrames() != null ? canvas.getAnimationFrames().size() : 0);

        player.sendPluginMessage(plugin, plugin.getNetworkChannel(), out.toByteArray());
    }

    // 0x10: 处理客户端图片上传数据包 (支持分包法与单包全收，全套安全防炸弹检查)
    private void handleUploadChunk(Player player, ByteArrayDataInput in) {
        String uploadId = in.readUTF();
        int chunkIndex = in.readInt();
        int totalChunks = in.readInt();
        int totalBytes = in.readInt();
        String algorithm = in.readUTF();
        short cols = in.readShort();
        short rows = in.readShort();
        int dataLen = in.readInt();
        byte[] chunkData = new byte[dataLen];
        in.readFully(chunkData);

        plugin.getChunkedUploadManager().handleChunk(player, uploadId, chunkIndex, totalChunks, totalBytes,
            algorithm, cols, rows, chunkData, (success, message) -> {
                sendResponse(player, PacketProtocol.C2S_UPLOAD_CHUNK, success, message);
            });
    }

    // 0x11: 查询相连画布拓扑 (方案 A: 几何物理空间检测, 含有动图/锁定则不返回)
    private void handleQueryConnected(Player player, ByteArrayDataInput in) {
        int entityId = in.readInt();
        int maxRadius = in.readByte() & 0xFF;
        if (maxRadius <= 0 || maxRadius > 10) maxRadius = 5;

        org.bukkit.entity.ItemFrame startFrame = null;
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(6.0, 6.0, 6.0)) {
            if (e.getEntityId() == entityId && e instanceof org.bukkit.entity.ItemFrame frame) {
                startFrame = frame;
                break;
            }
        }

        if (startFrame == null) {
            sendResponse(player, PacketProtocol.C2S_QUERY_CONNECTED, false, "未找到目标展示框");
            return;
        }

        sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasMatrix matrix =
            sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.findConnectedMatrix(startFrame, plugin.getCanvasManager(), maxRadius);

        if (matrix == null || matrix.allNodes.isEmpty()) {
            sendResponse(player, PacketProtocol.C2S_QUERY_CONNECTED, false, "存在受保护或动图画布，无法建立多画布联动");
            return;
        }

        // 记下这份矩阵：接下来这个玩家发来的 0x12 全局坐标就是按它算的
        rememberMatrix(player, matrix);

        plugin.getLogger().fine(String.format("多画布拓扑：玩家=%s 基准框=%d 矩阵=%dx%d(%d 格)",
            player.getName(), entityId, matrix.cols, matrix.rows, matrix.allNodes.size()));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(PacketProtocol.S2C_CONNECTED_MATRIX);
        out.writeInt(matrix.cols);
        out.writeInt(matrix.rows);
        out.writeInt(matrix.cols * 128); // 总像素宽
        out.writeInt(matrix.rows * 128); // 总像素高
        out.writeInt(matrix.allNodes.size());

        for (sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasFrameNode node : matrix.allNodes) {
            out.writeShort(node.gridCol);
            out.writeShort(node.gridRow);
            out.writeInt(node.frame.getEntityId());
            out.writeUTF(node.canvas.getId());
            out.writeInt(node.canvas.getMapId());
            out.writeBoolean(node.canvas.isProtected());
            out.writeBoolean(node.canvas.isAnimated());
        }

        player.sendPluginMessage(plugin, plugin.getNetworkChannel(), out.toByteArray());
    }

    // 0x12: 大画板全局像素绘制 (自动切片坐标计算与原子落笔)
    private void handleDrawGridPixel(Player player, ByteArrayDataInput in, MapDrawAPI api) {
        int baseEntityId = in.readInt();
        int globalX = in.readInt();
        int globalY = in.readInt();
        byte toolByte = in.readByte();
        byte colorByte = in.readByte();

        org.bukkit.entity.ItemFrame startFrame = null;
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(6.0, 6.0, 6.0)) {
            if (e.getEntityId() == baseEntityId && e instanceof org.bukkit.entity.ItemFrame frame) {
                startFrame = frame;
                break;
            }
        }

        if (startFrame == null) {
            sendResponse(player, PacketProtocol.C2S_DRAW_GRID_PIXEL, false, "基准展示框未找到");
            return;
        }

        sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasMatrix matrix = cachedMatrix(player, baseEntityId);

        if (matrix == null) {
            // 缓存里没有（或基准框不在缓存矩阵里）→ 现搜一份
            matrix = sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.findConnectedMatrix(startFrame, plugin.getCanvasManager(), 5);

            if (matrix != null) {
                rememberMatrix(player, matrix);
            }
        }

        if (matrix == null) {
            sendResponse(player, PacketProtocol.C2S_DRAW_GRID_PIXEL, false, "多画布矩阵无效或受保护");
            return;
        }

        int targetCol = globalX / 128;
        int targetRow = globalY / 128;
        int localX = globalX % 128;
        int localY = globalY % 128;

        sashwind.mc.plugin.mapdraw.canvas.ConnectedCanvasTopology.CanvasFrameNode targetNode = matrix.getNode(targetCol, targetRow);
        if (targetNode == null) {
            // 把矩阵尺寸一起回给客户端，方便定位是「坐标算错」还是「矩阵不一致」
            plugin.getLogger().warning(String.format(
                "多画布落笔超出矩阵：玩家=%s 基准框=%d 全局=(%d,%d) 请求格=(%d,%d) 矩阵=%dx%d(%d 格)",
                player.getName(), baseEntityId, globalX, globalY, targetCol, targetRow,
                matrix.cols, matrix.rows, matrix.allNodes.size()));

            sendResponse(player, PacketProtocol.C2S_DRAW_GRID_PIXEL, false,
                String.format("坐标超出多画板矩阵范围 (矩阵 %dx%d，共 %d 格，请求格 %d,%d)",
                    matrix.cols, matrix.rows, matrix.allNodes.size(), targetCol, targetRow));
            return;
        }

        ToolType tool = parseTool(toolByte);
        DrawResult res = api.drawPixel(player, targetNode.canvas, localX, localY, tool, colorByte);
        sendResponse(player, PacketProtocol.C2S_DRAW_GRID_PIXEL, res.isSuccess(), res.getMessage());
    }

    // 发送通用操作响应回客户端
    private void sendResponse(Player player, byte originalPacketId, boolean success, String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(PacketProtocol.S2C_RESPONSE);
        out.writeByte(originalPacketId);
        out.writeBoolean(success);
        out.writeUTF(message != null ? message : "");
        player.sendPluginMessage(plugin, plugin.getNetworkChannel(), out.toByteArray());
    }

    // ------------------------------------------------------------------
    // 多画布矩阵缓存（让 0x12 与 0x11 用同一份矩阵）
    // ------------------------------------------------------------------

    /** 记下这个玩家最近一次 0x11 得到的矩阵，并顺手清理过期条目。 */
    private void rememberMatrix(Player player, ConnectedCanvasTopology.CanvasMatrix matrix) {
        long now = System.currentTimeMillis();
        this.matrixCache.put(player.getUniqueId(), new CachedMatrix(matrix, now));
        this.matrixCache.entrySet().removeIf(entry -> now - entry.getValue().at > MATRIX_CACHE_TTL_MS);
    }

    /**
     * 取缓存矩阵；缓存过期、或者基准展示框不在缓存矩阵里（玩家换了块画板）时返回 null，
     * 调用方会重新搜索一份。
     */
    private ConnectedCanvasTopology.CanvasMatrix cachedMatrix(Player player, int baseEntityId) {
        CachedMatrix cached = this.matrixCache.get(player.getUniqueId());

        if (cached == null || System.currentTimeMillis() - cached.at > MATRIX_CACHE_TTL_MS) {
            return null;
        }

        for (ConnectedCanvasTopology.CanvasFrameNode node : cached.matrix.allNodes) {
            if (node.frame.getEntityId() == baseEntityId) {
                return cached.matrix;
            }
        }

        return null;
    }

    private ToolType parseTool(byte toolByte) {
        return switch (toolByte) {
            case 0 -> ToolType.PEN;
            case 1 -> ToolType.ERASER;
            case 2 -> ToolType.PAINTBUCKET;
            default -> ToolType.NONE;
        };
    }
}
