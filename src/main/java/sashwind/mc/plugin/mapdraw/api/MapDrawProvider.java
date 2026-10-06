package sashwind.mc.plugin.mapdraw.api;

public final class MapDrawProvider {

    private static MapDrawAPI api;

    public static MapDrawAPI get() {
        if (api == null) {
            throw new IllegalStateException("MapDraw 插件尚未就绪，无法获取 MapDrawAPI！");
        }
        return api;
    }

    public static void register(MapDrawAPI apiInstance) {
        api = apiInstance;
    }

    public static void unregister() {
        api = null;
    }
}
