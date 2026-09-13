package com.wayward.waywardforge;

import com.mojang.logging.LogUtils;
import com.wayward.waywardcommon.Wayward;
import com.wayward.waywardcommon.WaywardEntrypoints;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Wayward.MOD_ID)
public final class WaywardForge {

    public WaywardForge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void setup(FMLClientSetupEvent event) {
        LogUtils.getLogger().info("SETUP!!!");
        Wayward.init();
    }

    private void onServerStarting(ServerStartingEvent event) {
        WaywardEntrypoints.onServerStart(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        WaywardEntrypoints.onServerStop(event.getServer());
    }
}
