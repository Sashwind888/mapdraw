package sashwind.mc.plugin.mapdraw.network;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import sashwind.mc.plugin.mapdraw.canvas.CanvasData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;

public class FastMapPacketSender {

    private static Boolean initialized = null;
    private static Constructor<?> mapIdConstructor;
    private static Constructor<?> mapPatchConstructor;
    private static Constructor<?> packetConstructor;
    private static Method getHandleMethod;
    private static Method sendPacketMethod;
    private static java.lang.reflect.Field connectionField;

    private static void initReflection() {
        if (initialized != null) return;
        try {
            // NMS 类加载 (支持现代 Mojang 映射服务端)
            Class<?> mapIdClass = Class.forName("net.minecraft.world.level.saveddata.maps.MapId");
            Class<?> mapPatchClass = Class.forName("net.minecraft.world.level.saveddata.maps.MapItemSavedData$MapPatch");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMapItemDataPacket");
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");

            mapIdConstructor = mapIdClass.getConstructor(int.class);
            mapPatchConstructor = mapPatchClass.getConstructor(int.class, int.class, int.class, int.class, byte[].class);

            // ClientboundMapItemDataPacket(MapId, byte, boolean, Optional, Optional)
            for (Constructor<?> c : packetClass.getConstructors()) {
                if (c.getParameterCount() == 5) {
                    packetConstructor = c;
                    break;
                }
            }

            getHandleMethod = craftPlayerClass.getMethod("getHandle");
            connectionField = serverPlayerClass.getField("connection");

            for (Method m : connectionClass.getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(packetInterface)) {
                    sendPacketMethod = m;
                    break;
                }
            }

            initialized = (packetConstructor != null && sendPacketMethod != null);
        } catch (Throwable t) {
            initialized = false;
        }
    }

    /**
     * 发送原生低级数据包直发推送，无缝降级为 sendMap
     */
    public static void sendDirectMapPacket(CanvasData canvas, Location loc) {
        if (canvas == null) return;

        initReflection();

        int mapIdInt = canvas.getMapId();
        byte[] pixels = canvas.getPixels();

        if (Boolean.TRUE.equals(initialized)) {
            try {
                Object nmsMapId = mapIdConstructor.newInstance(mapIdInt);
                Object patch = mapPatchConstructor.newInstance(0, 0, 128, 128, pixels);
                Object nmsPacket = packetConstructor.newInstance(
                    nmsMapId,
                    (byte) 0,
                    false,
                    Optional.empty(),
                    Optional.of(patch)
                );

                if (loc != null && loc.getWorld() != null) {
                    for (Player p : loc.getWorld().getNearbyPlayers(loc, 48)) {
                        if (p.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                            continue;
                        }
                        sendToPlayer(p, nmsPacket);
                    }
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                            continue;
                        }
                        sendToPlayer(p, nmsPacket);
                    }
                }
                return;
            } catch (Throwable ignored) {
            }
        }

        // 环境不支持时，使用 Bukkit 标准 sendMap 降级推送
        MapView view = Bukkit.getMap(mapIdInt);
        if (view != null) {
            if (loc != null && loc.getWorld() != null) {
                for (Player p : loc.getWorld().getNearbyPlayers(loc, 48)) {
                    // 若玩家当前打开了任何箱子/工作台容器界面，不进行跨界面强制同步，防客户端越界
                    if (p.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                        continue;
                    }
                    p.sendMap(view);
                }
            } else {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                        continue;
                    }
                    p.sendMap(view);
                }
            }
        }
    }

    private static void sendToPlayer(Player player, Object nmsPacket) {
        try {
            Object serverPlayer = getHandleMethod.invoke(player);
            Object connection = connectionField.get(serverPlayer);
            sendPacketMethod.invoke(connection, nmsPacket);
        } catch (Throwable ignored) {
        }
    }
}
