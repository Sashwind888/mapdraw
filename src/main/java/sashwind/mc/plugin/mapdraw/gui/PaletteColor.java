package sashwind.mc.plugin.mapdraw.gui;

import org.bukkit.Material;

import java.awt.Color;

public enum PaletteColor {
    WHITE("白色", "#FFFFFF", Material.WHITE_CONCRETE, new Color(255, 255, 255), (byte) 34),
    ORANGE("橙色", "#D87F33", Material.ORANGE_CONCRETE, new Color(216, 127, 51), (byte) 62),
    MAGENTA("品红", "#B24CD8", Material.MAGENTA_CONCRETE, new Color(178, 76, 216), (byte) 66),
    LIGHT_BLUE("浅蓝", "#6699D8", Material.LIGHT_BLUE_CONCRETE, new Color(102, 153, 216), (byte) 70),
    YELLOW("黄色", "#E5E533", Material.YELLOW_CONCRETE, new Color(229, 229, 51), (byte) 74),
    LIME("黄绿", "#7FCC19", Material.LIME_CONCRETE, new Color(127, 204, 25), (byte) 78),
    PINK("粉红", "#F27FA5", Material.PINK_CONCRETE, new Color(242, 127, 165), (byte) 82),
    GRAY("灰色", "#4C4C4C", Material.GRAY_CONCRETE, new Color(76, 76, 76), (byte) 86),
    LIGHT_GRAY("浅灰", "#999999", Material.LIGHT_GRAY_CONCRETE, new Color(153, 153, 153), (byte) 90),
    CYAN("青色", "#4C7F99", Material.CYAN_CONCRETE, new Color(76, 127, 153), (byte) 94),
    PURPLE("紫色", "#7F3FB2", Material.PURPLE_CONCRETE, new Color(127, 63, 178), (byte) 98),
    BLUE("蓝色", "#334CB2", Material.BLUE_CONCRETE, new Color(51, 76, 178), (byte) 102),
    BROWN("棕色", "#664C33", Material.BROWN_CONCRETE, new Color(102, 76, 51), (byte) 106),
    GREEN("绿色", "#667F33", Material.GREEN_CONCRETE, new Color(102, 127, 51), (byte) 110),
    RED("红色", "#993333", Material.RED_CONCRETE, new Color(153, 51, 51), (byte) 114),
    BLACK("黑色", "#191919", Material.BLACK_CONCRETE, new Color(25, 25, 25), (byte) 118);

    private final String name;
    private final String hex;
    private final Material icon;
    private final Color color;
    private final byte mapColorByte;

    PaletteColor(String name, String hex, Material icon, Color color, byte mapColorByte) {
        this.name = name;
        this.hex = hex;
        this.icon = icon;
        this.color = color;
        this.mapColorByte = mapColorByte;
    }

    public String getName() {
        return name;
    }

    public String getHex() {
        return hex;
    }

    public Material getIcon() {
        return icon;
    }

    public Color getColor() {
        return color;
    }

    public byte getMapColorByte() {
        return mapColorByte;
    }
}
