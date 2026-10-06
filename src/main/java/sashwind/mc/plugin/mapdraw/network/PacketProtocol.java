package sashwind.mc.plugin.mapdraw.network;

public final class PacketProtocol {

    // 通道标识
    public static final String DEFAULT_CHANNEL = "mapdraw:main";

    // 客户端 -> 服务端 (C2S)
    public static final byte C2S_DRAW_PIXEL         = 0x01; // 单点绘制
    public static final byte C2S_DRAW_BATCH         = 0x02; // 连续批量绘制 (Mod 平滑画笔)
    public static final byte C2S_UNDO               = 0x03; // 撤销
    public static final byte C2S_REDO               = 0x04; // 重做
    public static final byte C2S_PROTECT            = 0x05; // 锁定保护
    public static final byte C2S_DEPROTECT          = 0x06; // 解除保护 (作者/管理员)
    public static final byte C2S_SET_META           = 0x07; // 修改元数据 (标题/描述/尺寸/防拷贝)
    public static final byte C2S_CREATE_CANVAS      = 0x08; // 申请创建新画布
    public static final byte C2S_SET_TOOL           = 0x09; // 切换玩家绘图工具
    public static final byte C2S_SET_COLOR          = 0x0A; // 设置玩家当前颜色
    public static final byte C2S_OPEN_GUI           = 0x0B; // 打开箱子菜单/调色板
    public static final byte C2S_REQUEST_CANVAS     = 0x0C; // 请求画布完整数据
    public static final byte C2S_SET_CHEST_GUI      = 0x0D; // 设置是否启用服务端箱子菜单 (重进重置)
    public static final byte C2S_REQUEST_CANVAS_INFO= 0x0E; // 请求画布元数据 (轻量查询是不是GIF动图等)
    public static final byte C2S_UPLOAD_CHUNK       = 0x10; // 上传图片分包 (安全检查,支持单包全收/多包分片)
    public static final byte C2S_QUERY_CONNECTED    = 0x11; // 方案A: 几何拓扑检测所有相连画布
    public static final byte C2S_DRAW_GRID_PIXEL    = 0x12; // 大画板全局像素绘制 (自动切片落笔)

    // 服务端 -> 客户端 (S2C)
    public static final byte S2C_RESPONSE           = (byte) 0x80; // 操作响应结果
    public static final byte S2C_SYNC_CANVAS        = (byte) 0x81; // 同步画布元数据与像素
    public static final byte S2C_PIXEL_UPDATE       = (byte) 0x82; // 增量单点更新广播
    public static final byte S2C_CANVAS_INFO        = (byte) 0x83; // 返回画布属性 (含是否为GIF动图等)
    public static final byte S2C_CONNECTED_MATRIX   = (byte) 0x84; // 返回相连画布矩阵结构 (无动图且可编辑)

    // SET_META 字段类型定义
    public static final byte FIELD_TITLE       = 0x00;
    public static final byte FIELD_DESCRIPTION = 0x01;
    public static final byte FIELD_SIZE        = 0x02;
    public static final byte FIELD_NO_COPY     = 0x03;

    private PacketProtocol() {}
}
