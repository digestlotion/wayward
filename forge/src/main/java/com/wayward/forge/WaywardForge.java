package com.wayward.forge;

import com.wayward.Wayward;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(Wayward.MOD_ID)
public final class WaywardForge {

    public WaywardForge() {
        MinecraftForge.EVENT_BUS.addListener(this::setup);
    }

    private void setup(FMLClientSetupEvent event) {
        Wayward.init();
    }
}
