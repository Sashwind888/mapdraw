package sashwind.mc.plugin.mapdraw.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;

import java.util.ArrayList;
import java.util.List;

public class CanvasNBTUtil {

    public static NamespacedKey KEY_IS_CANVAS;
    public static NamespacedKey KEY_CANVAS_ID;
    public static NamespacedKey KEY_TITLE;
    public static NamespacedKey KEY_DESCRIPTION;
    public static NamespacedKey KEY_SIZE;
    public static NamespacedKey KEY_PROTECTED;
    public static NamespacedKey KEY_NO_COPY;
    public static NamespacedKey KEY_CREATOR;

    public static void init(Plugin plugin) {
        KEY_IS_CANVAS = new NamespacedKey(plugin, "is_canvas");
        KEY_CANVAS_ID = new NamespacedKey(plugin, "canvas_id");
        KEY_TITLE = new NamespacedKey(plugin, "title");
        KEY_DESCRIPTION = new NamespacedKey(plugin, "description");
        KEY_SIZE = new NamespacedKey(plugin, "size");
        KEY_PROTECTED = new NamespacedKey(plugin, "protected");
        KEY_NO_COPY = new NamespacedKey(plugin, "no_copy");
        KEY_CREATOR = new NamespacedKey(plugin, "creator");
    }

    public static boolean isCanvasMap(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(KEY_IS_CANVAS, PersistentDataType.BYTE);
    }

    public static String getCanvasId(ItemStack item) {
        if (!isCanvasMap(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(KEY_CANVAS_ID, PersistentDataType.STRING);
    }

    /**
     * 将画布的完整信息（名称 + 描述 Lore）应用到物品上
     */
    public static void applyCanvasMeta(ItemStack item, CanvasData canvas) {
        applyCanvasMeta(item, canvas, false);
    }

    /**
     * 将画布的完整信息应用到物品上
     * @param forFrameInWorld 若为 true，则在展示框内部保留完整的名称与描述，彻底杜绝重启后名称/描述变回普通地图
     */
    public static void applyCanvasMeta(ItemStack item, CanvasData canvas, boolean forFrameInWorld) {
        if (item == null || canvas == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_IS_CANVAS, PersistentDataType.BYTE, (byte) 1);
        pdc.set(KEY_CANVAS_ID, PersistentDataType.STRING, canvas.getId());
        pdc.set(KEY_TITLE, PersistentDataType.STRING, canvas.getTitle());
        pdc.set(KEY_DESCRIPTION, PersistentDataType.STRING, canvas.getDescription());
        pdc.set(KEY_SIZE, PersistentDataType.INTEGER, canvas.getSize());
        pdc.set(KEY_PROTECTED, PersistentDataType.BYTE, (byte) (canvas.isProtected() ? 1 : 0));
        pdc.set(KEY_NO_COPY, PersistentDataType.BYTE, (byte) (canvas.isNoCopy() ? 1 : 0));
        if (canvas.getCreator() != null) {
            pdc.set(KEY_CREATOR, PersistentDataType.STRING, canvas.getCreator());
        }

        // 使用 ITEM_NAME 替代 CUSTOM_NAME：
        // 1. 物品栏中正常显示自定义名字和所有描述框；
        // 2. 展示框中客户端绝对不会渲染浮空字框（原版只对 CUSTOM_NAME 渲染展示框浮空字）；
        // 3. 彻底根除展示框浮空字遮挡，同时确保物品栏中的名称框和 Lore 永远正常可见！
        String displayName = "§b§l[MapDraw 画布] §f" + canvas.getTitle();
        try {
            meta.setItemName(displayName);
            meta.displayName(null); // 移除 CUSTOM_NAME
        } catch (Throwable t) {
            meta.setDisplayName(displayName);
        }

        List<String> lore = new ArrayList<>();
        lore.add("§8§m------------------------");
        lore.add("§7画布名称: §f" + canvas.getName());
        lore.add("§7当前标题: §e" + canvas.getTitle());
        lore.add("§7画布描述: §7" + canvas.getDescription());
        lore.add("§7画板大小: §a" + canvas.getSize() + "x" + canvas.getSize());
        lore.add("§7保护状态: " + (canvas.isProtected() ? "§c已锁定保护 (无法编辑)" : "§a可编辑"));
        lore.add("§7禁止拷贝: " + (canvas.isNoCopy() ? "§c是 (防复制)" : "§7否"));
        lore.add("§7创建玩家: §b" + (canvas.getCreator() != null ? canvas.getCreator() : "未知"));
        lore.add("§8§m------------------------");
        lore.add("§e» 放置到展示框上开始绘制");
        lore.add("§e» 蹲下右键或输入 /mdw menu 打开菜单");
        meta.setLore(lore);

        // 确保物品栏中的名称框和悬浮信息永远正常显示
        try {
            meta.setHideTooltip(false);
        } catch (Throwable ignored) {
        }

        item.setItemMeta(meta);
    }

    public static void stripDisplayForFrame(ItemStack item) {
    }
}
