package com.wayward.common;

import java.io.File;
import java.nio.file.Path;

import com.wayward.common.network.WaywardService;

import net.minecraft.client.Minecraft;

public final class Wayward {

    //  TODO: MAIN Accept uuid AND player username
    public static final String MOD_ID = "wayward";

    public static void init() {
        Config.init();
        WaywardService.auth();
    }

    private static final Minecraft minecraft = Minecraft.getInstance();

    public static Path getGameDir() {
        return minecraft.gameDirectory.toPath();
    }

    public static Path getSavesDir() {
        return getGameDir().resolve("saves");
    }

    public static File getConf() {
        return getGameDir().resolve("config/wayward.json").toFile();
    }

    public static String getUuid() {
        return minecraft.getUser().getProfileId().toString();
    }

}
