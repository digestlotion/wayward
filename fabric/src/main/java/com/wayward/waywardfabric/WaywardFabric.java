package com.wayward.waywardfabric;

import com.wayward.waywardcommon.Wayward;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class WaywardFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Wayward.init();
        ServerLifecycleEvents.SERVER_STARTING.register(Wayward::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(Wayward::onServerStop);
    }
}
