/*
 * VoxelBridge mod entry point.
 */
package com.voxelbridge;

import com.voxelbridge.platform.NeoForgePlatformBootstrap;
import com.voxelbridge.platform.PlatformBootstrap;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.platform.client.MinecraftClientAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;


@Mod(VoxelBridge.MODID)
public class VoxelBridge {
    public static final String MODID = "voxelbridge";

    public VoxelBridge(IEventBus modBus, ModContainer container, Dist dist) {
        ClientAccessHolder.set(new MinecraftClientAccess());
        com.voxelbridge.adapter.Adapters.init(
            new com.voxelbridge.adapter.NeoForgeWorldAdapter(),
            new com.voxelbridge.adapter.NeoForgeRenderAdapter()
        );
        PlatformBootstrap platform = new NeoForgePlatformBootstrap(dist, modBus);
        platform.init();
    }
}
