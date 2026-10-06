package sashwind.mc.plugin.mapdraw;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import sashwind.mc.plugin.mapdraw.canvas.CanvasManager;
import sashwind.mc.plugin.mapdraw.command.MapdrawCommand;
import sashwind.mc.plugin.mapdraw.economy.EconomyManager;
import sashwind.mc.plugin.mapdraw.gui.GuiManager;
import sashwind.mc.plugin.mapdraw.listener.CanvasItemInteractListener;
import sashwind.mc.plugin.mapdraw.listener.ItemFrameDrawListener;
import sashwind.mc.plugin.mapdraw.listener.MapCopyProtectionListener;
import sashwind.mc.plugin.mapdraw.listener.ToolProtectionListener;
import sashwind.mc.plugin.mapdraw.tool.ToolManager;
import sashwind.mc.plugin.mapdraw.util.CanvasNBTUtil;

public final class Mapdraw extends JavaPlugin {

    private static Mapdraw instance;
    private CanvasManager canvasManager;
    private ToolManager toolManager;
    private GuiManager guiManager;
    private EconomyManager economyManager;
    private sashwind.mc.plugin.mapdraw.canvas.AnimationManager animationManager;
    private sashwind.mc.plugin.mapdraw.api.MapDrawAPI api;
    private sashwind.mc.plugin.mapdraw.network.PlayerSettingsManager playerSettingsManager;
    private sashwind.mc.plugin.mapdraw.network.ChunkedUploadManager chunkedUploadManager;
    private sashwind.mc.plugin.mapdraw.compatibility.ProtocolLibHook protocolLibHook;

    @Override
    public void onEnable() {
        instance = this;

        // 强依赖检查：ProtocolLib 必须存在且启用，否则报错并自动卸载关闭
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null || !getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().severe("===============================================================");
            getLogger().severe(" [MapDraw 严重错误] 未检测到前置插件 ProtocolLib，插件无法启动！");
            getLogger().severe(" MapDraw 依赖 ProtocolLib 实现低延迟数据包拦截与平滑画布同步。");
            getLogger().severe(" 请先下载并安装 ProtocolLib (5.0+) 后再运行本插件！");
            getLogger().severe(" 插件正在自动关闭...");
            getLogger().severe("===============================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        // 初始化 PDC NamespacedKey
        CanvasNBTUtil.init(this);

        // 初始化子系统
        this.playerSettingsManager = new sashwind.mc.plugin.mapdraw.network.PlayerSettingsManager();
        this.canvasManager = new CanvasManager(this);
        this.toolManager = new ToolManager(this);
        this.toolManager.resetAllSessions();
        this.guiManager = new GuiManager(this, this.canvasManager, this.toolManager);
        this.economyManager = new EconomyManager(this);
        this.animationManager = new sashwind.mc.plugin.mapdraw.canvas.AnimationManager(this, this.canvasManager);
        this.chunkedUploadManager = new sashwind.mc.plugin.mapdraw.network.ChunkedUploadManager(this, this.canvasManager, this.economyManager);

        // 注册并初始化开放 API
        this.api = new sashwind.mc.plugin.mapdraw.api.MapDrawAPIImpl(this, canvasManager, toolManager, guiManager, economyManager);
        sashwind.mc.plugin.mapdraw.api.MapDrawProvider.register(this.api);

        // 加载历史画布
        this.canvasManager.loadAll();

        // 启动 GIF 动图循环播放调度器
        this.animationManager.start();

        // 注册原版客户端 Mod 专用网络数据包通道 (Plugin Messaging Channel)
        registerNetworkChannels();

        // 注册 ProtocolLib 高级包监听 (若已安装)
        this.protocolLibHook = new sashwind.mc.plugin.mapdraw.compatibility.ProtocolLibHook(this);
        this.protocolLibHook.registerListeners();

        // 注册监听器
        getServer().getPluginManager().registerEvents(new ItemFrameDrawListener(this, canvasManager, toolManager), this);
        getServer().getPluginManager().registerEvents(new ToolProtectionListener(this, toolManager), this);
        getServer().getPluginManager().registerEvents(new CanvasItemInteractListener(this, canvasManager, guiManager), this);
        getServer().getPluginManager().registerEvents(new MapCopyProtectionListener(this, canvasManager), this);
        getServer().getPluginManager().registerEvents(new sashwind.mc.plugin.mapdraw.listener.FrameEntityLoadListener(this, canvasManager), this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        // 注册主命令
        MapdrawCommand cmdExecutor = new MapdrawCommand(this, canvasManager, toolManager, guiManager, economyManager);
        PluginCommand mapdrawCmd = getCommand("mapdraw");
        if (mapdrawCmd != null) {
            mapdrawCmd.setExecutor(cmdExecutor);
            mapdrawCmd.setTabCompleter(cmdExecutor);
        }

        getLogger().info("MapDraw 地图绘制插件已成功加载！");
    }

    @Override
    public void onDisable() {
        unregisterNetworkChannels();
        sashwind.mc.plugin.mapdraw.api.MapDrawProvider.unregister();
        if (playerSettingsManager != null) {
            playerSettingsManager.clear();
        }
        if (animationManager != null) {
            animationManager.stop();
        }
        if (canvasManager != null) {
            canvasManager.shutdown();
        }
        getLogger().info("MapDraw 地图绘制插件已安全卸载并保存所有画布！");
    }

    public String getNetworkChannel() {
        return getConfig().getString("network.channel", sashwind.mc.plugin.mapdraw.network.PacketProtocol.DEFAULT_CHANNEL);
    }

    private void registerNetworkChannels() {
        if (!getConfig().getBoolean("network.enabled", true)) {
            return;
        }
        String ch = getNetworkChannel();
        getServer().getMessenger().registerIncomingPluginChannel(this, ch, new sashwind.mc.plugin.mapdraw.network.PluginMessagePacketListener(this));
        getServer().getMessenger().registerOutgoingPluginChannel(this, ch);
        getLogger().info("已注册客户端 Mod 原生网络数据包通信通道: " + ch);
    }

    private void unregisterNetworkChannels() {
        String ch = getNetworkChannel();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, ch);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, ch);
    }

    public static Mapdraw getInstance() {
        return instance;
    }

    public sashwind.mc.plugin.mapdraw.api.MapDrawAPI getAPI() {
        return api;
    }

    public sashwind.mc.plugin.mapdraw.network.PlayerSettingsManager getPlayerSettingsManager() {
        return playerSettingsManager;
    }

    public sashwind.mc.plugin.mapdraw.network.ChunkedUploadManager getChunkedUploadManager() {
        return chunkedUploadManager;
    }

    public boolean isChestGuiEnabled(org.bukkit.entity.Player player) {
        return playerSettingsManager != null && playerSettingsManager.isChestGuiEnabled(player);
    }

    public sashwind.mc.plugin.mapdraw.canvas.AnimationManager getAnimationManager() {
        return animationManager;
    }

    public CanvasManager getCanvasManager() {
        return canvasManager;
    }

    public ToolManager getToolManager() {
        return toolManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public String getMessage(String key) {
        String msg = getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
