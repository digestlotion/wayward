package com.wayward.waywardcommon;

import com.wayward.waywardcommon.network.WaywardService;
import java.io.File;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

public final class Wayward {

    //  TODO: MAIN Accept uuid AND player username
    public static final String MOD_ID = "wayward";

    public static void init() {
        Config.init();
        WaywardService.auth();
    }

    private static final Minecraft client = Minecraft.getInstance();

    public static Path getGameDir() {
        return client.gameDirectory.toPath();
    }

    public static Path getSavesDir() {
        return getGameDir().resolve("saves");
    }

    public static File getConf() {
        return getGameDir().resolve("config/wayward.json").toFile();
    }

}
