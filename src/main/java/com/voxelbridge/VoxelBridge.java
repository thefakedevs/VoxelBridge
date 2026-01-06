/*
 * VoxelBridge mod entry point.
 */
package com.voxelbridge;

import com.voxelbridge.platform.NeoForgePlatformBootstrap;
import com.voxelbridge.platform.PlatformBootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;


@Mod(VoxelBridge.MODID)
public class VoxelBridge {
    public static final String MODID = "voxelbridge";
    private static final PlatformBootstrap PLATFORM = new NeoForgePlatformBootstrap();

    public VoxelBridge(IEventBus modBus, ModContainer container, Dist dist) {
        PLATFORM.register(dist, modBus);
    }
}
