package sashwind.mc.plugin.mapdraw.tool;

public enum ToolType {
    PEN("画笔", "pen"),
    ERASER("橡皮擦", "eraser"),
    PAINTBUCKET("油漆桶", "paintbucket"),
    NONE("无", "null");

    private final String displayName;
    private final String key;

    ToolType(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKey() {
        return key;
    }

    public static ToolType fromString(String str) {
        if (str == null || str.isEmpty() || str.equalsIgnoreCase("null") || str.equalsIgnoreCase("none")) {
            return NONE;
        }
        for (ToolType type : values()) {
            if (type.name().equalsIgnoreCase(str) || type.key.equalsIgnoreCase(str)) {
                return type;
            }
        }
        return null;
    }
}
