package com.wayward.neoforge;

import com.wayward.common.Wayward;
import com.wayward.common.gui.WaywardWidget;
import com.wayward.common.network.WaywardService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(Wayward.MOD_ID)
public final class WaywardNeoForge {

    // TODO:
    public WaywardNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onScreenInit);
    }

    private void setup(FMLClientSetupEvent event) {
        Wayward.init();
    }

    private void onServerStarting(ServerAboutToStartEvent event) {
        WaywardService.onServerStart(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        WaywardService.onServerStop(event.getServer());
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof SelectWorldScreen ts) {
            WaywardWidget widget = new WaywardWidget(Minecraft.getInstance(), (int) (ts.width * 0.7), (int) (ts.height * 0.3), (int) (ts.width * 0.25), (int) (ts.height * 0.2));
            event.addListener(widget);
        }
    }
}
