package com.wayward.fabric;

import com.wayward.Wayward;

import net.fabricmc.api.ClientModInitializer;

public final class WaywardFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Wayward.init();
    }
}
