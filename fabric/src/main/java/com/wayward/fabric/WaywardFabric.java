package com.wayward.fabric;

import com.wayward.common.Wayward;
import com.wayward.common.gui.WaywardWidget;
import com.wayward.common.mixin.ScreenInvoker;
import com.wayward.common.network.WaywardService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

public final class WaywardFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Wayward.init();
        ServerLifecycleEvents.SERVER_STARTING.register(WaywardService::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(WaywardService::onServerStop);
        ScreenEvents.AFTER_INIT.register((c, s, w, h) -> {
            if (s instanceof SelectWorldScreen ts) {
                WaywardWidget widget = new WaywardWidget(
                    c,
                    (int) (ts.width * 0.72),
                    (int) (ts.height * 0.3),
                    (int) (ts.width * 0.25),
                    (int) (ts.height * 0.2)
                );
                ((ScreenInvoker) s).invoker$addRenderableWidget(widget);
            }
        });
    }
}
