package com.voxelbridge.platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

/**
 * Platform-specific bootstrap for event registration.
 */
public interface PlatformBootstrap {
    void register(Dist dist, IEventBus modBus);
}
