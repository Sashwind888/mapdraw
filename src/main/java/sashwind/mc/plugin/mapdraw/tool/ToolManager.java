package sashwind.mc.plugin.mapdraw.tool;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import sashwind.mc.plugin.mapdraw.Mapdraw;
import sashwind.mc.plugin.mapdraw.compatibility.CraftEngineHook;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolManager {

    private final Mapdraw plugin;
    private final NamespacedKey toolKey;
    private final Map<UUID, PlayerDrawSession> sessions = new ConcurrentHashMap<>();

    public ToolManager(Mapdraw plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "tool_type");
    }

    public PlayerDrawSession getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), k -> new PlayerDrawSession());
    }

    public boolean isDrawingTool(ItemStack item) {
        return getToolType(item) != ToolType.NONE;
    }

    public ToolType getToolType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return ToolType.NONE;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(toolKey, PersistentDataType.STRING)) {
            String str = pdc.get(toolKey, PersistentDataType.STRING);
            ToolType type = ToolType.fromString(str);
            if (type != null && type != ToolType.NONE) {
                return type;
            }
        }

        // CraftEngine 自定义物品兼容检查
        if (CraftEngineHook.isAvailable()) {
            String customId = CraftEngineHook.getCustomItemId(item);
            if (customId != null) {
                for (ToolType type : ToolType.values()) {
                    if (type == ToolType.NONE) continue;
                    String configId = plugin.getConfig().getString("tools." + type.getKey() + ".craftengine_id");
                    if (configId != null && !configId.trim().isEmpty()) {
                        if (customId.equalsIgnoreCase(configId.trim()) ||
                            customId.replace("minecraft:", "").equalsIgnoreCase(configId.trim())) {
                            return type;
                        }
                    }
                }
            }
        }

        return ToolType.NONE;
    }

    public ItemStack createToolItem(ToolType type, Player player) {
        if (type == null || type == ToolType.NONE) {
            return null;
        }

        String path = "tools." + type.getKey();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);

        ItemStack item = null;

        // 1. 尝试从 CraftEngine 生成物品
        if (sec != null && CraftEngineHook.isAvailable()) {
            String ceId = sec.getString("craftengine_id");
            if (ceId != null && !ceId.trim().isEmpty()) {
                item = CraftEngineHook.buildItem(ceId, player);
            }
        }

        // 2. 回退机制：使用原版材质与配置
        if (item == null) {
            String matName = sec != null ? sec.getString("material", "STICK") : "STICK";
            Material mat = Material.matchMaterial(matName != null ? matName : "STICK");
            if (mat == null) {
                mat = switch (type) {
                    case PEN -> Material.FEATHER;
                    case ERASER -> Material.SHEARS;
                    case PAINTBUCKET -> Material.WATER_BUCKET;
                    default -> Material.STICK;
                };
            }
            item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            String displayName = sec != null ? sec.getString("name", "§bMapDraw " + type.getDisplayName()) : "§bMapDraw " + type.getDisplayName();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

            List<String> lore = new ArrayList<>();
            if (sec != null && sec.contains("lore")) {
                for (String l : sec.getStringList("lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', l));
                }
            } else {
                lore.add("§7MapDraw 专用绘图工具");
                lore.add("§8(该物品禁止合成或其它用途)");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        // 3. 注入 MapDraw 工具标记
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(toolKey, PersistentDataType.STRING, type.getKey());
            item.setItemMeta(meta);
        }

        return item;
    }

    public void giveTool(Player player, ToolType type) {
        PlayerDrawSession session = getSession(player);
        session.setCurrentTool(type);

        if (type == ToolType.NONE) {
            clearTools(player);
            return;
        }

        clearTools(player);

        ItemStack toolItem = createToolItem(type, player);
        if (toolItem != null) {
            player.getInventory().addItem(toolItem);
        }
    }

    public void clearTools(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (isDrawingTool(it)) {
                player.getInventory().setItem(i, null);
            }
        }
        getSession(player).setCurrentTool(ToolType.NONE);
    }

    public void resetAllSessions() {
        sessions.clear();
    }
}
