# MapDraw (地图绘制插件)

基于 Paper API 的 Minecraft 基于地图的像素画绘制插件。

通过视线追踪，玩家可以直接在物品展示框上手持笔刷、橡皮擦与油漆桶进行绘制。

> ⚠️ **运行前置要求**：本插件**强制依赖前置插件 ProtocolLib (5.0+)**！ProtocolLib 用于网络数据包拦截与平滑画布同步。

---

## 🌟 核心特性

- **展示框交互绘制**：支持垂直墙面及水平放置（地面/天花板）的展示框，通过光线投射计算三维相对交点，自动兼容展示框旋转角度，映射到 $128 \times 128$ 地图像素空间。
- **无遮挡画板**：地图画放置在展示框上后，自动去除展示框上方悬浮名称标签，彻底杜绝准星对准时的文字遮挡，还原作画纯净视线；破坏掉落时自动还原完整名称与属性卡片。
- **箱子可视化菜单与一键创建**：直接输入 `/mapdraw` 或 `/mdw` 即可呼出可视化 GUI。
- **三种绘图工具**：
  - **画笔 (PEN)**：按画布网格绘制单点与笔触。
  - **橡皮擦 (ERASER)**：按画布网格擦除为背景/透明色。
  - **油漆桶 (PAINTBUCKET)**：队列 BFS 算法泛洪填充连续闭合色彩区域。
- **全方位工具安全保护**：工具自带专属 PDC 标记，严禁用于工作台合成、铁砧修补、锻造台、熔炉烧炼与燃料、发射器发射及盛水/倒水等消耗用途；按 Q 键丢弃时直接销毁并清空当前工具选择。
- **CraftEngine 深度兼容**：
  - 软依赖挂钩 CraftEngine 自定义物品系统。
  - 在 `config.yml` 中配置 `craftengine_id` 即可赋予工具专属 3D 模型与贴图。
  - 自动识别由 CraftEngine 给予的自定义工具。
- **多样化色彩体系**：
  - 支持十六进制颜色（如 `#FF5555`）。
  - 支持英文常用颜色名（`red`、`blue`、`green`、`yellow` 等）。
  - 支持 Minecraft 颜色代码（`&a`、`&c`、`&6` 等）。
  - 不带参数执行 `/mdw draw color` 时弹出 16 色调色板箱子 GUI，直观快捷。
  - 自动映射至 Minecraft `MapPalette` 颜色表。
- **画布网格与“填色后不可缩小”机制**：
  - 支持调整画布可用网格大小（如 16、32、64、128）。
  - 画布一旦被填色（已有笔画），只允许往更大尺寸调整以防像素细节丢失，禁止缩小。
- **空间抖动与多联大图**：上传图片支持 Floyd-Steinberg 误差扩散算法（`dither`），突破原版 236 色限制实现逼真细腻的光学混色；支持指定行列尺寸将大图切分成多联画拼接。
- **GIF 动态地图画循环播放**：原生支持多帧 GIF 动图解析，自动在展示框循环播放；可在配置文件中自定义 FPS 帧率，支持服务器重启持久化恢复。
- **永久保护模式与防破坏**：
  - 输入 `/mdw protect` 锁定画布后，无法再编辑任何元数据和像素，普通玩家不可逆。
  - 展示框中的受保护画布享有防破坏保护；原作者和管理员可通过 `/mdw admin deprotect` 解除保护。
- **制图台防拷贝安全**：
  - 在制图台或工作台中复制地图时自动拦截带 `no_copy` 标记的画布地图，保护原创艺术资产。
- **撤销与重做 (Undo / Redo)**：
  - 内存维护快照历史栈，支持多步撤销与重做，修改实时推送至客户端渲染。
- **可视化箱子 GUI 控制台**：
  - 输入 `/mdw menu` 或**手持画布地图右键**即可打开 54 格可视化操作台。
- **Vault 经济系统集成**：
  - 创建画布支持配置游戏币费用；拥有指定权限节点可免除扣款。

---

## 💻 指令与语法

主指令：`/mapdraw`（别名：`/mdw`）

| 指令 | 作用说明 | 所需权限 |
| :--- | :--- | :--- |
| `/mdw` (不带参数) | 直接打开可视化控制菜单 | `mapdraw.user` |
| `/mdw create [name] [size]` | 创建一张新画布地图（可扣费） | `mapdraw.user.create` |
| `/mdw upload <图片URL> [算法] [宽] [高]` | 上传网络图片生成单张或多联巨幅画作（算法支持 dither/none，居中填充/每张扣费） | `mapdraw.upload` |
| `/mdw protect` | 锁定保护手持画布（不可撤销） | `mapdraw.user.protect` |
| `/mdw menu` | 打开可视化控制菜单（手持右键亦可） | `mapdraw.user` |
| `/mdw data get` | 聊天框打印信息并同步更新手持地图 Lore | `mapdraw.user.data` |
| `/mdw data set title <内容>` | 设置画布标题 | `mapdraw.user.data` |
| `/mdw data set description <内容>` | 设置画布描述说明 | `mapdraw.user.data` |
| `/mdw data set size <大小>` | 设置画布可用大小（16/32/64/128，填色后不可缩小） | `mapdraw.user.data` |
| `/mdw draw color [#hex/颜色名/&代码]` | 设置画笔颜色（不填参数打开 16 色菜单） | `mapdraw.user.draw` |
| `/mdw draw tool <pen/eraser/paintbucket/null>` | 领取或清空绘图工具（丢弃亦可清空） | `mapdraw.user.draw` |
| `/mdw draw undo` | 撤销上一步操作 | `mapdraw.user.draw.undo` |
| `/mdw draw redo` | 重做下一步操作 | `mapdraw.user.draw.redo` |
| `/mdw admin deprotect` | [管理员] 强制解除手持画布的保护模式 | `mapdraw.admin.deprotect` |
| `/mdw admin reload` | [管理员] 重载配置文件 | `mapdraw.admin.reload` |
| `/mdw help` | 查看帮助手册 | `mapdraw.user` |

---

## 🔑 权限节点

```yaml
mapdraw
  .user               # 普通用户基础权限（默认拥有）
    .create           # 创建画布
    .protect          # 锁定保护画布
    .data             # 查看和修改画布元数据
    .draw             # 基础绘图与选色
      .undo           # 撤销绘制
      .redo           # 重做绘制
  .admin              # 管理员权限（默认 OP 拥有）
    .deprotect        # 强制解除保护
    .reload           # 重载配置
    .createwithnomoney# 免费创建画布（绕过 Vault 费用）
```

---

## 待修复

- [ ] 当用油漆桶对连续的画布进行操作，有概率出现一些线条无法被覆盖

- [ ] 连续的画布绘制时偶发线条断开

---

## 🧩 开发者 API 调用指南

MapDraw 提供了面向外部插件的完整开放 API，所有操作接口均强制绑定发包/执行玩家进行权限、金币及保护状态鉴权。

### 获取 API 实例
```java
// 方式 1: 通过静态单例 Provider
MapDrawAPI api = MapDrawProvider.get();

// 方式 2: 通过主类插件实例
MapDrawAPI api = Mapdraw.getInstance().getAPI();
```

### 获取 CanvasData 方式
```java
// 1. 根据画布唯一 UUID / 字符串 ID 获取
CanvasData canvas = api.getCanvas("80c68d19-7993-4d59-a6a0-f452583dd9b0");

// 2. 根据原版地图的 MapView ID 获取
CanvasData canvas = api.getCanvasByMapId(mapId);

// 3. 从 ItemStack 地图物品中提取关联的 CanvasData
CanvasData canvas = api.getCanvasFromItem(player.getInventory().getItemInMainHand());

// 4. 从展示框实体 (ItemFrame) 中获取挂载的 CanvasData
CanvasData canvas = api.getCanvasFromFrame(itemFrame);

// 5. 获取全服所有已加载的画布列表
Collection<CanvasData> all = api.getAllCanvases();
```

### 核心功能调用示例 (操作均绑定玩家进行鉴权)
```java
// 1. 玩家绘制像素点 (自动校验 mapdraw.user.draw 与保护状态)
DrawResult result = api.drawPixel(player, canvas, 64, 64, ToolType.PEN, (byte) 114);
if (!result.isSuccess()) {
    player.sendMessage("绘制失败: " + result.getMessage());
}

// 2. 玩家创建新画布地图 (校验权限与经济扣款)
DrawResult createRes = api.createCanvas(player, "我的画作", 128);

// 3. 撤销 / 重做
api.undo(player, canvas);
api.redo(player, canvas);

// 4. 锁定保护画布 / 解除保护 (动图不可解，普通图仅作者或管理员可解)
api.protectCanvas(player, canvas, false);
api.deprotectCanvas(player, canvas);

// 5. 修改标题、描述与画布尺寸
api.setTitle(player, canvas, "新标题");
api.setSize(player, canvas, 64);
```

---

## 📡 客户端 Mod 原生网络数据包 API (Plugin Messaging Channel)

针对 Forge / Fabric / NeoForge 等客户端 Mod 开发者，MapDraw 提供了原生网络数据包通道：`mapdraw:main`。客户端发包天然绑定发包玩家连接，服务端直接以发包玩家身份进行权限、金币及保护状态鉴权。

### 客户端发包协议 (Client -> Server)
第一个字节为 `PacketID`：

| PacketID | 标识 | 载荷字段结构 (DataInputStream) | 说明 |
| :--- | :--- | :--- | :--- |
| `0x01` | **DRAW_PIXEL** | `String canvasId`, `short x`, `short y`, `byte tool(0:笔,1:橡皮,2:桶)`, `byte color` | 单点绘制，校验发包玩家权限与保护模式 |
| `0x02` | **DRAW_BATCH** | `String canvasId`, `byte tool`, `byte color`, `short count`, 循环 `count` 次: (`short x`, `short y`) | 批量平滑绘制（Mod 拖拽画线优化） |
| `0x03` | **UNDO** | `String canvasId` | 撤销上一步操作 |
| `0x04` | **REDO** | `String canvasId` | 重做下一步操作 |
| `0x05` | **PROTECT** | `String canvasId` | 锁定保护画布 |
| `0x06` | **DEPROTECT** | `String canvasId` | 解除保护（仅限作者或管理员） |
| `0x07` | **SET_META** | `String canvasId`, `byte field(0:标题,1:描述,2:尺寸,3:防拷贝)`, `String value` | 修改画布属性 |
| `0x08` | **CREATE_CANVAS** | `String name`, `int size` | 申请创建新画布（扣除费用） |
| `0x09` | **SET_TOOL** | `byte tool(0:笔, 1:橡皮, 2:桶, 3:无)` | 切换玩家手持绘图工具 |
| `0x0A` | **SET_COLOR** | `int r`, `int g`, `int b` | 设置玩家画笔颜色 |
| `0x0B` | **OPEN_GUI** | `byte guiType(0:菜单, 1:调色板)`, `String canvasId` | 在游戏内呼出对应界面 |
| `0x0C` | **REQUEST_CANVAS** | `String canvasId` | 请求完整画布元数据、动图参数与 16384 像素 |
| `0x0D` | **SET_CHEST_GUI** | `boolean enabled` | 启用/禁用服务端的箱子菜单（玩家重进自动重置为启用） |
| `0x0E` | **REQUEST_CANVAS_INFO** | `String canvasId` | 轻量查询画布属性（明确返回是否为 GIF 动图及帧率帧数） |
| `0x10` | **UPLOAD_CHUNK** | `String uploadId`, `int chunkIndex`, `int totalChunks`, `int totalBytes`, `String algorithm`, `short cols`, `short rows`, `int dataLen`, `byte[] data` | 上传图片数据包（支持单包全收/多包分片，全套安全防炸弹检查） |
| `0x11` | **QUERY_CONNECTED** | `int entityId`, `byte maxRadius(默认5)` | **多画布联动检测 (方案A)**：以该展示框为基准进行空间拓扑搜索，若含有动图或锁定画布则直接拒绝并返回空，保证连贯可编辑性 |
| `0x12` | **DRAW_GRID_PIXEL** | `int baseEntityId`, `int globalX`, `int globalY`, `byte tool`, `byte color` | **大画布全局像素绘制**：客户端直接在全局大画板上打点，服务端底层自动计算切片与局部像素原子落笔 |

### 服务端回包协议 (Server -> Client)
- `0x80` **PACKET_RESPONSE**：`byte originalPacketId`, `boolean success`, `String message`（操作成功/失败反馈及提示语）
- `0x81` **CANVAS_DATA_SYNC**：`String id`, `int mapId`, `String name`, `String title`, `String desc`, `int size`, `boolean protected`, `boolean noCopy`, `String creator`, `boolean animated`, `int fps`, `int frameCount`, `int pixelLen`, `byte[] 像素数据`
- `0x83` **CANVAS_INFO**：`String id`, `int mapId`, `String name`, `String title`, `String desc`, `int size`, `boolean protected`, `boolean noCopy`, `String creator`, `boolean animated`, `int fps`, `int frameCount`（轻量元数据同步，不含巨型像素数据）
- `0x84` **CONNECTED_MATRIX**：`int cols`, `int rows`, `int totalWidthPixels`, `int totalHeightPixels`, `int nodeCount`, 循环 `nodeCount` 次: (`short gridCol`, `short gridRow`, `int entityId`, `String canvasId`, `int mapId`, `boolean protected`, `boolean animated`)（返回空间拓扑相连矩阵元数据）

---

### 网络数据包构造与解析示例 (Fabric / Forge / 服务端)

#### 1. 客户端画点发包示例 (Client -> Server)
```java
// 构造 0x01 DRAW_PIXEL 数据包
FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
buf.writeByte(0x01);                 // PacketID: 0x01
buf.writeUtf("画布唯一UUID");         // canvasId
buf.writeShort(64);                 // x (0-127)
buf.writeShort(64);                 // y (0-127)
buf.writeByte(0);                   // tool: 0=PEN, 1=ERASER, 2=PAINTBUCKET
buf.writeByte(114);                 // color: MapColor 字节 (如 114=红)

// Fabric 客户端发送:
ClientPlayNetworking.send(new CustomPacketPayload() { ... });
// 或 Forge / NeoForge 客户端发送:
PacketDistributor.sendToServer(new ServerboundCustomPayload(new ResourceLocation("mapdraw:main"), buf));
```

#### 2. 服务端数据包解析示例 (Server 端实现)
```java
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!channel.equals("mapdraw:main")) return;

    ByteArrayDataInput in = ByteStreams.newDataInput(message);
    byte packetId = in.readByte();

    if (packetId == 0x01) { // DRAW_PIXEL
        String canvasId = in.readUTF();
        short x = in.readShort();
        short y = in.readShort();
        byte toolByte = in.readByte();
        byte colorByte = in.readByte();

        // 自动基于发包玩家 player 鉴权权限与保护状态并实时更新展示框
        CanvasData canvas = MapDrawProvider.get().getCanvas(canvasId);
        ToolType tool = toolByte == 0 ? ToolType.PEN : (toolByte == 1 ? ToolType.ERASER : ToolType.PAINTBUCKET);

        DrawResult result = MapDrawProvider.get().drawPixel(player, canvas, x, y, tool, colorByte);

        // 回包反馈给客户端 Mod
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(0x80);                  // S2C_RESPONSE
        out.writeByte(0x01);                  // 原包 ID
        out.writeBoolean(result.isSuccess());  // 成功/失败
        out.writeUTF(result.getMessage() != null ? result.getMessage() : "");
        player.sendPluginMessage(plugin, "mapdraw:main", out.toByteArray());
    }
}
```

#### 3. 客户端请求并加载整张画布数据 (0x0C & 0x81)
```java
// 客户端发起请求:
FriendlyByteBuf req = new FriendlyByteBuf(Unpooled.buffer());
req.writeByte(0x0C);                 // PacketID: 0x0C REQUEST_CANVAS
req.writeUtf(canvasId);              // 请求的画布唯一 ID
// 发送到服务端...

// 客户端接收服务端 0x81 SYNC_CANVAS 数据包:
if (packetId == (byte) 0x81) {
    String canvasId = in.readUtf();
    int mapId = in.readInt();
    String name = in.readUtf();
    String title = in.readUtf();
    String description = in.readUtf();
    int size = in.readInt();
    boolean isProtected = in.readBoolean();
    boolean noCopy = in.readBoolean();
    String creator = in.readUtf();
    boolean isAnimated = in.readBoolean();

    int pixelLength = in.readInt(); // 16384
    byte[] pixels = new byte[pixelLength];
    in.readBytes(pixels);

    // 客户端 Mod 可以在自定义 GUI / 画板上直接绘制 pixels 图像！
}
```

#### 4. 批量连续绘制 (0x02 DRAW_BATCH，鼠标拖拽画线平滑优化)
```java
FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
buf.writeByte(0x02);               // PacketID: 0x02 DRAW_BATCH
buf.writeUtf(canvasId);
buf.writeByte(0);                  // tool: PEN
buf.writeByte(70);                 // color: 浅蓝色
buf.writeShort(pathPoints.size()); // 连续点数量 N

for (Point p : pathPoints) {
    buf.writeShort((short) p.x);
    buf.writeShort((short) p.y);
}
// 单包发送，服务端毫秒内批量完成落笔并推送展示框更新，无任何网络延迟！
```

#### 5. 客户端原生上传图片数据包 (0x10 UPLOAD_CHUNK，支持小图单包/大图分包)
```java
// 若图片小于 30KB，可直接作为 1 个包发送 (totalChunks = 1, chunkIndex = 0)
// 若图片较大，切分成每块 16KB ~ 30KB 依次发送：
String uploadId = UUID.randomUUID().toString();
byte[] fullImageBytes = Files.readAllBytes(myImageFile.toPath());
int chunkSize = 24576; // 24KB
int totalChunks = (int) Math.ceil((double) fullImageBytes.length / chunkSize);

for (int i = 0; i < totalChunks; i++) {
    int start = i * chunkSize;
    int end = Math.min(fullImageBytes.length, start + chunkSize);
    byte[] chunk = Arrays.copyOfRange(fullImageBytes, start, end);

    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(0x10);                 // PacketID: 0x10 UPLOAD_CHUNK
    buf.writeUtf(uploadId);              // 唯一上传 ID
    buf.writeInt(i);                     // chunkIndex (从 0 开始)
    buf.writeInt(totalChunks);           // totalChunks
    buf.writeInt(fullImageBytes.length); // totalBytes
    buf.writeUtf("dither");              // 算法 ("dither" 或 "none")
    buf.writeShort(1);                   // 横向张数 cols (如 1x1)
    buf.writeShort(1);                   // 纵向张数 rows
    buf.writeInt(chunk.length);
    buf.writeBytes(chunk);

    // 发送到服务端，服务端会自动完成格式魔数校验、反序列化炸弹防御、经济扣费并分发地图！
}
```

#### 6. 禁用服务端箱子菜单（使用客户端 Mod 专用 GUI 时）
```java
FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
buf.writeByte(0x0D);                 // PacketID: 0x0D SET_CHEST_GUI
buf.writeBoolean(false);             // 禁用服务端的箱子菜单弹窗
// 发送后，玩家蹲下右键时不会再弹出原版箱子菜单，可直接呼出 Mod 本地界面！每次重新连接自动重置为开启。
```

---

## 🛠️ 构建与编译

本项目使用 Gradle 构建：

```bash
# 编译并生成 Jar 包
./gradlew build
```

编译输出目录：`build/libs/`

---

## 📄 开源许可证

本项目基于 **GNU Affero General Public License v3.0 (AGPL-3.0)** 许可证开源。
