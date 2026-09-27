package com.wayward.waywardfabric;

import com.wayward.waywardcommon.Wayward;
import com.wayward.waywardcommon.gui.WaywardWidget;
import com.wayward.waywardcommon.mixin.ScreenInvoker;
import com.wayward.waywardcommon.network.WaywardService;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.gui.screens.TitleScreen;

public final class WaywardFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Wayward.init();
        ServerLifecycleEvents.SERVER_STARTING.register(WaywardService::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(WaywardService::onServerStop);
        ScreenEvents.AFTER_INIT.register((c, s, w, h) -> {
            if (s instanceof TitleScreen ts) {
                WaywardWidget widget = new WaywardWidget(c, (int) (ts.width * 0.7), (int) (ts.height * 0.3), (int) (ts.width * 0.25), (int) (ts.height * 0.2));
                ((ScreenInvoker) s).invoker$addRenderableWidget(widget);
            }
        });
    }
}
