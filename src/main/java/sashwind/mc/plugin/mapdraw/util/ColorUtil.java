package sashwind.mc.plugin.mapdraw.util;

import org.bukkit.ChatColor;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6})$");
    private static final Map<String, Color> NAMED_COLORS = new HashMap<>();
    private static final Map<Character, Color> CHAT_CODE_COLORS = new HashMap<>();
    private static final Color[] PALETTE_COLORS = new Color[256];

    static {
        for (int i = 0; i < 256; i++) {
            try {
                PALETTE_COLORS[i] = MapPalette.getColor((byte) i);
            } catch (Throwable t) {
                PALETTE_COLORS[i] = new Color(0, 0, 0);
            }
        }
        // 基础英文命名颜色
        NAMED_COLORS.put("black", new Color(0, 0, 0));
        NAMED_COLORS.put("white", new Color(255, 255, 255));
        NAMED_COLORS.put("red", new Color(255, 0, 0));
        NAMED_COLORS.put("green", new Color(0, 128, 0));
        NAMED_COLORS.put("blue", new Color(0, 0, 255));
        NAMED_COLORS.put("yellow", new Color(255, 255, 0));
        NAMED_COLORS.put("cyan", new Color(0, 255, 255));
        NAMED_COLORS.put("magenta", new Color(255, 0, 255));
        NAMED_COLORS.put("purple", new Color(128, 0, 128));
        NAMED_COLORS.put("orange", new Color(255, 165, 0));
        NAMED_COLORS.put("gray", new Color(128, 128, 128));
        NAMED_COLORS.put("grey", new Color(128, 128, 128));
        NAMED_COLORS.put("dark_gray", new Color(64, 64, 64));
        NAMED_COLORS.put("light_gray", new Color(153, 153, 153));
        NAMED_COLORS.put("pink", new Color(242, 127, 165));
        NAMED_COLORS.put("brown", new Color(102, 76, 51));
        NAMED_COLORS.put("lime", new Color(127, 204, 25));
        NAMED_COLORS.put("light_blue", new Color(102, 153, 216));

        // Minecraft 颜色代码 &0 - &f 对应 RGB 映射
        CHAT_CODE_COLORS.put('0', new Color(0, 0, 0));       // Black
        CHAT_CODE_COLORS.put('1', new Color(0, 0, 170));     // Dark Blue
        CHAT_CODE_COLORS.put('2', new Color(0, 170, 0));     // Dark Green
        CHAT_CODE_COLORS.put('3', new Color(0, 170, 170));   // Dark Aqua
        CHAT_CODE_COLORS.put('4', new Color(170, 0, 0));     // Dark Red
        CHAT_CODE_COLORS.put('5', new Color(170, 0, 170));   // Dark Purple
        CHAT_CODE_COLORS.put('6', new Color(255, 170, 0));   // Gold
        CHAT_CODE_COLORS.put('7', new Color(170, 170, 170)); // Gray
        CHAT_CODE_COLORS.put('8', new Color(85, 85, 85));    // Dark Gray
        CHAT_CODE_COLORS.put('9', new Color(85, 85, 255));   // Blue
        CHAT_CODE_COLORS.put('a', new Color(85, 255, 85));   // Green
        CHAT_CODE_COLORS.put('b', new Color(85, 255, 255));  // Aqua
        CHAT_CODE_COLORS.put('c', new Color(255, 85, 85));   // Red
        CHAT_CODE_COLORS.put('d', new Color(255, 85, 255));  // Light Purple
        CHAT_CODE_COLORS.put('e', new Color(255, 255, 85));  // Yellow
        CHAT_CODE_COLORS.put('f', new Color(255, 255, 255)); // White
    }

    public static Color parseColor(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        input = input.trim();

        // 1. #RRGGBB
        Matcher matcher = HEX_PATTERN.matcher(input);
        if (matcher.matches()) {
            try {
                return Color.decode(input);
            } catch (NumberFormatException ignored) {
            }
        }

        // 2. &<code> 或 §<code>
        if (input.length() == 2 && (input.charAt(0) == '&' || input.charAt(0) == '§')) {
            char code = Character.toLowerCase(input.charAt(1));
            if (CHAT_CODE_COLORS.containsKey(code)) {
                return CHAT_CODE_COLORS.get(code);
            }
        }

        // 3. Named color
        String lower = input.toLowerCase().replace('-', '_').replace(' ', '_');
        if (NAMED_COLORS.containsKey(lower)) {
            return NAMED_COLORS.get(lower);
        }

        return null;
    }

    public static byte toMapColor(Color color) {
        if (color == null) {
            return 0;
        }
        return MapPalette.matchColor(color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String toHexString(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Map<String, Color> getNamedColors() {
        return Collections.unmodifiableMap(NAMED_COLORS);
    }

    public static Map<Character, Color> getChatCodeColors() {
        return Collections.unmodifiableMap(CHAT_CODE_COLORS);
    }

    public static Color getPaletteColor(byte b) {
        int idx = b & 0xFF;
        Color c = PALETTE_COLORS[idx];
        return c != null ? c : Color.WHITE;
    }
}
