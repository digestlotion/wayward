package com.wayward.waywardcommon;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

public class WaywardEntrypoints {

    public static void onServerStart(MinecraftServer server) {
        WaywardService.pull(getWorldPath(server), server.getHostProfile().getId().toString());
    }

    public static void onServerStop(MinecraftServer server) {
        new Thread(() -> WaywardService.push(getWorldPath(server), server.getHostProfile().getId().toString())).start();
    }

    private static Path getWorldPath(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).getParent();
    }
}
