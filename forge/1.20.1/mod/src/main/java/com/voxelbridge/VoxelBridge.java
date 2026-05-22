package com.voxelbridge;

import com.voxelbridge.adapter.Adapters;
import com.voxelbridge.adapter.ForgeBlockEntityRenderBridge;
import com.voxelbridge.adapter.ForgeDynamicTextureReader;
import com.voxelbridge.adapter.ForgeEntityRenderBridge;
import com.voxelbridge.adapter.ForgeFluidSpriteResolver;
import com.voxelbridge.adapter.ForgePlatformModelHelper;
import com.voxelbridge.adapter.ForgePlatformRenderHelper;
import com.voxelbridge.adapter.ForgePlatformTextureHelper;
import com.voxelbridge.adapter.ForgeRenderAdapter;
import com.voxelbridge.adapter.ForgeSelectionRenderBridge;
import com.voxelbridge.adapter.ForgeWorldAdapter;
import com.voxelbridge.adapter.NoopModHandlerBridge;
import com.voxelbridge.platform.ForgePlatformBootstrap;
import com.voxelbridge.platform.PlatformBootstrap;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.platform.client.MinecraftClientAccess;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(VoxelBridge.MODID)
public final class VoxelBridge {
    public static final String MODID = ModConstants.MOD_ID;

    public VoxelBridge() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientOnly::init);
    }

    private static final class ClientOnly {
        private static void init() {
            ClientAccessHolder.set(new MinecraftClientAccess());
            Adapters.init(
                    new ForgeWorldAdapter(),
                    new ForgeRenderAdapter(),
                    new ForgeEntityRenderBridge(),
                    new ForgeBlockEntityRenderBridge(),
                    new ForgeSelectionRenderBridge(),
                    new ForgeFluidSpriteResolver(),
                    new NoopModHandlerBridge(),
                    new ForgePlatformRenderHelper(),
                    new ForgePlatformTextureHelper(),
                    new ForgePlatformModelHelper());
            ForgeDynamicTextureReader.touch();
            PlatformBootstrap platform = new ForgePlatformBootstrap(FMLJavaModLoadingContext.get().getModEventBus());
            platform.init();
        }
    }
}
