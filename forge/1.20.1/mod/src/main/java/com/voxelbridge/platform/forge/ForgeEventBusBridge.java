package com.voxelbridge.platform.forge;

import net.minecraftforge.eventbus.api.IEventBus;

public final class ForgeEventBusBridge {
    private ForgeEventBusBridge() {
    }

    public static IEventBus cast(Object bus) {
        return bus instanceof IEventBus eventBus ? eventBus : null;
    }
}
